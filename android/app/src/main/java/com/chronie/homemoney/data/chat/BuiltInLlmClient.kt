package com.chronie.homemoney.data.chat

import com.chronie.homemoney.data.vlm.MnnVlmEngine
import com.chronie.homemoney.data.vlm.NativeChatTurn
import com.chronie.homemoney.data.vlm.OnDeviceModelManager
import com.chronie.homemoney.domain.model.chat.AiProviderConfig
import com.chronie.homemoney.domain.model.chat.ChatRole
import com.chronie.homemoney.domain.model.chat.LlmRequest
import com.chronie.homemoney.domain.model.chat.LlmTurn
import com.chronie.homemoney.domain.model.chat.StreamEvent
import com.google.gson.Gson
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.catch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Built-in on-device backend (MNN / Qwen3-VL 8B).
 *
 * History is handed to MNN as role-tagged [NativeChatTurn]s so the runtime
 * applies the model's real chat template. The earlier implementation flattened
 * everything into one `[User]/[Assistant]` labelled string, which made the
 * model *continue the transcript* with invented dialogue and garbage dates.
 *
 * Tool calls and reasoning use plain-text markers (see [TextProtocolParser]):
 * `<think>` is a Qwen3 special token and MNN's template already injects an
 * empty `<think>\n\n</think>\n\n` assistant prefix, so special-token markers
 * never reach the stream. A short output budget keeps battery use bounded.
 */
@Singleton
class BuiltInLlmClient @Inject constructor(
    private val engine: MnnVlmEngine,
    private val modelManager: OnDeviceModelManager,
    private val codec: ChatAttachmentCodec,
    private val gson: Gson
) : LlmClient {

    override fun stream(
        request: LlmRequest,
        config: AiProviderConfig,
        apiKey: String
    ): Flow<StreamEvent> = channelFlow {
        if (!modelManager.isModelReady()) {
            trySend(StreamEvent.Failed("On-device model is not downloaded yet"))
            return@channelFlow
        }
        val loaded = engine.ensureLoaded(modelManager.modelDir())
        loaded.exceptionOrNull()?.let {
            trySend(StreamEvent.Failed(it.message ?: "Failed to load on-device model"))
            return@channelFlow
        }

        val turns = buildNativeTurns(request)
        val imagePaths = request.turns
            .flatMap { it.attachments }
            .filter { it.isImage && File(it.path).exists() }
            .map { it.path }

        val parser = TextProtocolParser(gson)
        var reasoningActive = false

        /** Applies one incremental emission on the native callback thread. */
        fun dispatch(result: TextEmission) {
            if (result.reasoningDelta.isNotEmpty()) {
                reasoningActive = true
                trySend(StreamEvent.ReasoningDelta(result.reasoningDelta))
            }
            // Reasoning closes either on an explicit marker or on the first
            // visible answer token; whichever arrives first wins.
            if (reasoningActive && (result.reasoningEnded || result.textDelta.isNotEmpty())) {
                reasoningActive = false
                trySend(StreamEvent.ReasoningEnded())
            }
            if (result.textDelta.isNotEmpty()) {
                trySend(StreamEvent.TextDelta(result.textDelta))
            }
            result.tools.forEach { call -> trySend(StreamEvent.ToolCallCompleted(call)) }
        }

        // channelFlow is required: the JNI TokenListener runs on a native
        // generation thread and calls back through runBlocking, so a plain
        // flow {} builder would violate Flow's single-coroutine emission rule.
        val result = engine.generateTurns(
            turns = turns,
            imagePaths = imagePaths,
            endWith = "[User]",
            maxNewTokens = MAX_NEW_TOKENS
        ) { token -> dispatch(parser.feed(token)) }
        result.exceptionOrNull()?.let {
            trySend(StreamEvent.Failed(it.message ?: "On-device generation failed"))
            return@channelFlow
        }

        dispatch(parser.finish())
        if (reasoningActive) trySend(StreamEvent.ReasoningEnded())
        trySend(StreamEvent.Completed)
    }.buffer(Channel.UNLIMITED).catch { e ->
        emit(StreamEvent.Failed(e.message ?: "On-device generation failed"))
    }

    override suspend fun probe(config: AiProviderConfig, apiKey: String): Result<String> =
        if (modelManager.isModelReady()) {
            Result.success("On-device model ready")
        } else {
            Result.failure(IllegalStateException("On-device model is not downloaded"))
        }

    /**
     * Maps provider-neutral turns onto chat-template roles. Tool results are
     * merged into a user turn (Qwen3 has no tool role), and consecutive turns
     * with the same role are collapsed so the template stays valid.
     */
    private fun buildNativeTurns(request: LlmRequest): List<NativeChatTurn> {
        val recent = request.turns.takeLast(MAX_HISTORY_TURNS)
        val mapped = mutableListOf<NativeChatTurn>()
        mapped.add(NativeChatTurn("system", request.systemPrompt))

        recent.forEach { turn ->
            val (role, content) = when (turn.role) {
                ChatRole.USER -> "user" to renderUser(turn)
                ChatRole.ASSISTANT -> "assistant" to renderAssistant(turn)
                ChatRole.TOOL -> "user" to
                    "[Tool result: ${turn.toolName ?: "tool"}]\n${turn.toolResult ?: "{}"}"
            }
            val last = mapped.lastOrNull()
            if (last != null && last.role == role && role != "system") {
                mapped[mapped.lastIndex] = NativeChatTurn(role, last.content + "\n\n" + content)
            } else {
                mapped.add(NativeChatTurn(role, content))
            }
        }
        // A trailing assistant turn would make the model continue itself; end
        // on a user turn so the template opens a fresh assistant response.
        if (mapped.last().role != "user") {
            mapped.add(NativeChatTurn("user", "请现在给出唯一一次回答。"))
        }
        return mapped
    }

    private fun renderUser(turn: LlmTurn): String = buildString {
        append(turn.content)
        turn.attachments.forEach { attachment ->
            if (attachment.isImage) {
                append("\n[图片附件: ${attachment.displayName}]")
            } else {
                append("\n[文件附件: ${attachment.displayName}]\n")
                append(codec.readText(attachment, 2_000))
            }
        }
    }

    private fun renderAssistant(turn: LlmTurn): String = buildString {
        append(turn.content)
        turn.toolCalls.forEach { call ->
            append("\n")
            append(TextProtocolMarkers.TOOL_START)
            append("""{"name":"${call.name}","arguments":${call.argumentsJson}}""")
            append(TextProtocolMarkers.TOOL_END)
        }
    }

    private companion object {
        /** Longer histories cost a full prefill on every turn (and battery). */
        const val MAX_HISTORY_TURNS = 10
        const val MAX_NEW_TOKENS = 512
    }
}

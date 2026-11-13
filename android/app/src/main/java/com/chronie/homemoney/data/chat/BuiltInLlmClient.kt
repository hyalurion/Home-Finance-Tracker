package com.chronie.homemoney.data.chat

import com.chronie.homemoney.data.vlm.MnnVlmEngine
import com.chronie.homemoney.data.vlm.OnDeviceModelManager
import com.chronie.homemoney.domain.model.chat.AiProviderConfig
import com.chronie.homemoney.domain.model.chat.ChatRole
import com.chronie.homemoney.domain.model.chat.LlmRequest
import com.chronie.homemoney.domain.model.chat.PendingToolCall
import com.chronie.homemoney.domain.model.chat.StreamEvent
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.catch
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Built-in on-device backend (MNN / Qwen3-VL 8B).
 *
 * The existing `MnnVlmEngine.generate(onToken)` already exposes token-level
 * streaming via its JNI TokenListener — this client is the first consumer of
 * that callback. The native bridge only accepts a flat prompt, so conversation
 * history is rendered into labelled turns, images are passed as file paths,
 * and tool calls use a strict text marker protocol instead of native function
 * calling. Markers are stripped from the visible answer as tokens arrive.
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

        val imagePaths = request.turns
            .flatMap { it.attachments }
            .filter { it.isImage && File(it.path).exists() }
            .map { it.path }

        val prompt = buildFlatPrompt(request)
        val raw = StringBuilder()
        var visibleLength = 0

        // channelFlow is required here: the JNI TokenListener is invoked from
        // a native/Dispatchers.Default thread through runBlocking, so a plain
        // flow {} builder would violate Flow's single-coroutine emission rule.
        val result = engine.generate(prompt, imagePaths) { token ->
            raw.append(token)
            val visible = visiblePrefix(raw.toString())
            if (visible.length > visibleLength) {
                trySend(StreamEvent.TextDelta(visible.substring(visibleLength)))
                visibleLength = visible.length
            }
        }
        result.exceptionOrNull()?.let {
            trySend(StreamEvent.Failed(it.message ?: "On-device generation failed"))
            return@channelFlow
        }

        parseToolCalls(raw.toString()).forEach { trySend(StreamEvent.ToolCallCompleted(it)) }
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

    /** Flattens provider-neutral turns into the single-prompt format MNN expects. */
    private fun buildFlatPrompt(request: LlmRequest): String {
        val history = request.turns.joinToString("\n\n") { turn ->
            when (turn.role) {
                ChatRole.USER -> buildString {
                    append("[User]\n").append(turn.content)
                    turn.attachments.forEach { attachment ->
                        if (attachment.isImage) {
                            append("\n[Image attached: ${attachment.displayName}]")
                        } else {
                            append("\n[File attached: ${attachment.displayName}]\n")
                            append(codec.readText(attachment, 4_000))
                        }
                    }
                }

                ChatRole.ASSISTANT -> buildString {
                    append("[Assistant]\n").append(turn.content)
                    turn.toolCalls.forEach {
                        append("\n[Requested tool ${it.name} with ${it.argumentsJson}]")
                    }
                }

                ChatRole.TOOL -> "[Tool result (${turn.toolName ?: "tool"})]\n${turn.toolResult ?: "{}"}"
            }
        }
        return "${request.systemPrompt}\n\n### Conversation\n$history\n\n[Assistant]\n"
    }

    /** Hides complete tool markers and any partial marker currently streaming in. */
    private fun visiblePrefix(raw: String): String {
        val marker = ChatSystemPrompt.TOOL_MARKER_START
        val fullIndex = raw.indexOf(marker)
        if (fullIndex >= 0) return raw.substring(0, fullIndex)

        val maxProbe = minOf(marker.length - 1, raw.length)
        for (length in maxProbe downTo 1) {
            if (raw.endsWith(marker.take(length))) {
                return raw.substring(0, raw.length - length)
            }
        }
        return raw
    }

    private fun parseToolCalls(raw: String): List<PendingToolCall> {
        val cleaned = raw.replace("```json", "").replace("```", "")
        val regex = Regex(
            Regex.escape(ChatSystemPrompt.TOOL_MARKER_START) +
                "([\\s\\S]*?)" +
                Regex.escape(ChatSystemPrompt.TOOL_MARKER_END)
        )
        return regex.findAll(cleaned).mapNotNull { match ->
            val payload = match.groupValues[1].trim()
            val obj = runCatching { gson.fromJson(payload, JsonObject::class.java) }.getOrNull()
                ?: return@mapNotNull null
            val name = obj.getAsJsonPrimitive("name")?.asString ?: return@mapNotNull null
            val arguments = obj.get("arguments")?.toString() ?: "{}"
            PendingToolCall(UUID.randomUUID().toString(), name, arguments)
        }.toList()
    }
}

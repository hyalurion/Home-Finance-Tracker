package com.chronie.homemoney.data.chat

import com.chronie.homemoney.domain.model.chat.AiProviderConfig
import com.chronie.homemoney.domain.model.chat.ChatRole
import com.chronie.homemoney.domain.model.chat.LlmRequest
import com.chronie.homemoney.domain.model.chat.LlmTurn
import com.chronie.homemoney.domain.model.chat.PendingToolCall
import com.chronie.homemoney.domain.model.chat.StreamEvent
import com.chronie.homemoney.domain.model.chat.ToolProtocol
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Anthropic-compatible `/v1/messages` streaming client.
 *
 * Implements the Messages SSE dialect: `content_block_start` opens a
 * `tool_use` block, `content_block_delta` carries `text_delta` or
 * `input_json_delta`, and `content_block_stop` closes it. Tool results are
 * returned as `tool_result` blocks inside a user message.
 */
@Singleton
class AnthropicLlmClient @Inject constructor(
    @Named("AiStreamClient") client: OkHttpClient,
    gson: Gson,
    private val codec: ChatAttachmentCodec
) : SseLlmClient(client, gson) {

    override fun stream(
        request: LlmRequest,
        config: AiProviderConfig,
        apiKey: String
    ): Flow<StreamEvent> = flow {
        val httpRequest = buildRequest(request, config, apiKey, streaming = true)
        var activeTool: MutableAnthropicCall? = null
        var reasoningActive = false
        val signature = StringBuilder()
        val textParser = if (request.toolProtocol == ToolProtocol.TEXT) {
            TextProtocolParser(gson)
        } else {
            null
        }

        suspend fun dispatchText(parsed: TextEmission) {
            if (parsed.reasoningDelta.isNotEmpty()) {
                reasoningActive = true
                emit(StreamEvent.ReasoningDelta(parsed.reasoningDelta))
            }
            if (reasoningActive && (parsed.reasoningEnded || parsed.textDelta.isNotEmpty())) {
                reasoningActive = false
                emit(StreamEvent.ReasoningEnded(signature.toString()))
            }
            if (parsed.textDelta.isNotEmpty()) emit(StreamEvent.TextDelta(parsed.textDelta))
            parsed.tools.forEach { emit(StreamEvent.ToolCallCompleted(it)) }
        }

        sseData(httpRequest).collect { data ->
            val frame = parseObject(data) ?: return@collect
            objectField(frame, "error")?.let { err ->
                error(stringField(err, "message") ?: "Model returned an error")
            }
            when (stringField(frame, "type")) {
                "content_block_start" -> {
                    val block = objectField(frame, "content_block") ?: return@collect
                    when (stringField(block, "type")) {
                        "tool_use" -> activeTool = MutableAnthropicCall(
                            id = stringField(block, "id")
                                ?: java.util.UUID.randomUUID().toString(),
                            name = stringField(block, "name").orEmpty(),
                            initialInput = objectField(block, "input")?.toString().orEmpty()
                        )

                        // Extended thinking blocks carry their text in
                        // thinking_delta frames; `redacted_thinking` is skipped.
                        "thinking" -> {
                            reasoningActive = true
                            stringField(block, "thinking")
                                ?.takeIf { it.isNotEmpty() }
                                ?.let { emit(StreamEvent.ReasoningDelta(it)) }
                        }
                    }
                }

                "content_block_delta" -> {
                    val delta = objectField(frame, "delta") ?: return@collect
                    // Anthropic-format gateways sometimes reuse the OpenAI field name.
                    stringField(delta, "reasoning_content", "reasoning")
                        ?.takeIf { it.isNotEmpty() }
                        ?.let {
                            reasoningActive = true
                            emit(StreamEvent.ReasoningDelta(it))
                        }
                    when (stringField(delta, "type")) {
                        "thinking_delta" -> {
                            reasoningActive = true
                            stringField(delta, "thinking")
                                ?.takeIf { it.isNotEmpty() }
                                ?.let { emit(StreamEvent.ReasoningDelta(it)) }
                        }

                        "signature_delta" -> stringField(delta, "signature")
                            ?.let { signature.append(it) }

                        "text_delta" -> stringField(delta, "text")
                            ?.takeIf { it.isNotEmpty() }
                            ?.let { text ->
                                if (textParser != null) {
                                    dispatchText(textParser.feed(text))
                                } else {
                                    if (reasoningActive) {
                                        reasoningActive = false
                                        emit(StreamEvent.ReasoningEnded(signature.toString()))
                                    }
                                    emit(StreamEvent.TextDelta(text))
                                }
                            }

                        "input_json_delta" -> stringField(delta, "partial_json")
                            ?.let { activeTool?.arguments?.append(it) }
                    }
                }

                "content_block_stop" -> {
                    if (reasoningActive) {
                        reasoningActive = false
                        emit(StreamEvent.ReasoningEnded(signature.toString()))
                    }
                    activeTool?.let { slot ->
                        if (slot.name.isNotBlank()) {
                            val args = slot.arguments.toString().ifBlank {
                                slot.initialInput.ifBlank { "{}" }
                            }
                            emit(
                                StreamEvent.ToolCallCompleted(
                                    PendingToolCall(slot.id, slot.name, args)
                                )
                            )
                        }
                    }
                    activeTool = null
                }

                "error" -> error(
                    objectField(frame, "error")?.let { stringField(it, "message") }
                        ?: "Anthropic stream error"
                )
            }
        }

        if (textParser != null) dispatchText(textParser.finish())
        if (reasoningActive) {
            reasoningActive = false
            emit(StreamEvent.ReasoningEnded(signature.toString()))
        }
        activeTool?.let { slot ->
            if (slot.name.isNotBlank()) {
                emit(
                    StreamEvent.ToolCallCompleted(
                        PendingToolCall(
                            slot.id,
                            slot.name,
                            slot.arguments.toString().ifBlank { slot.initialInput.ifBlank { "{}" } }
                        )
                    )
                )
            }
        }
        emit(StreamEvent.Completed)
    }.catch { e ->
        emit(StreamEvent.Failed(e.message ?: "Anthropic-compatible stream failed"))
    }

    private fun buildRequest(
        request: LlmRequest,
        config: AiProviderConfig,
        apiKey: String,
        streaming: Boolean
    ): Request {
        val body = JsonObject().apply {
            addProperty("model", config.model)
            addProperty("max_tokens", if (streaming) request.maxTokens else 1)
            addProperty("stream", streaming)
            addProperty("system", request.systemPrompt)
            add("messages", buildMessages(request))
            if (streaming && request.toolProtocol == ToolProtocol.NATIVE &&
                request.tools.isNotEmpty()
            ) {
                add("tools", buildTools(request))
            }
        }

        return Request.Builder()
            .url(endpoint(config, "v1/messages"))
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .header("Accept", if (streaming) "text/event-stream" else "application/json")
            .post(jsonRequest(body))
            .build()
    }

    private fun buildMessages(request: LlmRequest): JsonArray {
        val messages = JsonArray()
        val native = request.toolProtocol == ToolProtocol.NATIVE
        var pendingToolResults: JsonArray? = null

        fun flushToolResults() {
            pendingToolResults?.let { blocks ->
                messages.add(JsonObject().apply {
                    addProperty("role", "user")
                    add("content", blocks)
                })
                pendingToolResults = null
            }
        }

        request.turns.forEach { turn ->
            when (turn.role) {
                ChatRole.USER -> {
                    flushToolResults()
                    messages.add(JsonObject().apply {
                        addProperty("role", "user")
                        add("content", userContent(turn))
                    })
                }

                ChatRole.ASSISTANT -> {
                    flushToolResults()
                    messages.add(JsonObject().apply {
                        addProperty("role", "assistant")
                        add("content", assistantContent(turn, native))
                    })
                }

                ChatRole.TOOL -> {
                    if (pendingToolResults == null) pendingToolResults = JsonArray()
                    pendingToolResults!!.add(
                        if (native) {
                            JsonObject().apply {
                                addProperty("type", "tool_result")
                                addProperty("tool_use_id", turn.toolCallId.orEmpty())
                                addProperty("content", turn.toolResult ?: "{}")
                            }
                        } else {
                            // `tool_result` without a matching `tool_use` is a
                            // 400 on strict Anthropic-compatible endpoints.
                            JsonObject().apply {
                                addProperty("type", "text")
                                addProperty(
                                    "text",
                                    TextProtocolMarkers.renderToolResult(
                                        turn.toolName, turn.toolResult
                                    )
                                )
                            }
                        }
                    )
                }
            }
        }
        flushToolResults()
        return messages
    }

    private fun buildTools(request: LlmRequest): JsonArray = JsonArray().apply {
        request.tools.forEach { spec ->
            add(JsonObject().apply {
                addProperty("name", spec.name)
                addProperty("description", spec.description)
                add("input_schema", gson.fromJson(spec.parametersJson, JsonObject::class.java))
            })
        }
    }

    private fun userContent(turn: LlmTurn): JsonArray = JsonArray().apply {
        val text = buildString {
            append(turn.content)
            turn.attachments.filterNot { it.isImage }.forEach { attachment ->
                append("\n\n[Attached file: ${attachment.displayName}]\n")
                append(codec.readText(attachment, 6_000))
            }
        }
        add(JsonObject().apply {
            addProperty("type", "text")
            addProperty("text", text)
        })
        turn.attachments.filter { it.isImage }.forEach { attachment ->
            val encoded = codec.encodeImage(attachment) ?: return@forEach
            add(JsonObject().apply {
                addProperty("type", "image")
                add("source", JsonObject().apply {
                    addProperty("type", "base64")
                    addProperty("media_type", encoded.mediaType)
                    addProperty("data", encoded.base64)
                })
            })
        }
    }

    private fun assistantContent(turn: LlmTurn, native: Boolean): JsonArray = JsonArray().apply {
        // Anthropic requires a signed thinking block to be replayed verbatim
        // ahead of any tool_use block in the same assistant turn. Gateways that
        // do not emit a signature get a plain text turn instead.
        if (native && turn.reasoningContent.isNotBlank() && turn.reasoningSignature.isNotBlank()) {
            add(JsonObject().apply {
                addProperty("type", "thinking")
                addProperty("thinking", turn.reasoningContent)
                addProperty("signature", turn.reasoningSignature)
            })
        }
        val body = buildString {
            append(turn.content)
            if (!native) {
                turn.toolCalls.forEach { call ->
                    append("\n")
                    append(TextProtocolMarkers.renderToolCall(call.name, call.argumentsJson))
                }
            }
        }
        if (body.isNotBlank()) {
            add(JsonObject().apply {
                addProperty("type", "text")
                addProperty("text", body)
            })
        }
        if (native) {
            turn.toolCalls.forEach { call ->
                val input = runCatching {
                    gson.fromJson(call.argumentsJson, JsonObject::class.java)
                }.getOrNull() ?: JsonObject()
                add(JsonObject().apply {
                    addProperty("type", "tool_use")
                    addProperty("id", call.id)
                    addProperty("name", call.name)
                    add("input", input)
                })
            }
        }
        if (size() == 0) {
            add(JsonObject().apply {
                addProperty("type", "text")
                addProperty("text", "")
            })
        }
    }

    override suspend fun probe(config: AiProviderConfig, apiKey: String): Result<String> {
        val minimal = LlmRequest(
            systemPrompt = "Reply with the single word OK.",
            turns = listOf(LlmTurn(role = ChatRole.USER, content = "ping")),
            tools = emptyList(),
            maxTokens = 1
        )
        return try {
            executeProbe(buildRequest(minimal, config, apiKey, streaming = false))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private class MutableAnthropicCall(
        val id: String,
        val name: String,
        val initialInput: String,
        val arguments: StringBuilder = StringBuilder()
    )

    private companion object {
        const val ANTHROPIC_VERSION = "2023-06-01"
    }
}

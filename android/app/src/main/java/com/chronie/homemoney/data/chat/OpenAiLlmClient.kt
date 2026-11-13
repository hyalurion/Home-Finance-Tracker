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
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * OpenAI-compatible `/chat/completions` streaming client.
 *
 * Follows the public chat-completions contract (SSE `data:` frames ending in
 * `[DONE]`, incremental `delta.tool_calls[*].function.arguments`), which is
 * also implemented by most OpenAI-format gateways and local servers.
 */
@Singleton
class OpenAiLlmClient @Inject constructor(
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
        // OpenAI streams tool call arguments in fragments keyed by array index.
        val pending = linkedMapOf<Int, MutableOpenAiCall>()
        var reasoningActive = false
        // Providers without native function calling emit tool calls and a
        // short reasoning block as plain text inside `content`.
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
                emit(StreamEvent.ReasoningEnded())
            }
            if (parsed.textDelta.isNotEmpty()) emit(StreamEvent.TextDelta(parsed.textDelta))
            parsed.tools.forEach { emit(StreamEvent.ToolCallCompleted(it)) }
        }

        sseData(httpRequest).collect { data ->
            if (data.trim() == DONE_MARKER) return@collect
            val frame = parseObject(data) ?: return@collect
            objectField(frame, "error")?.let { err ->
                error(stringField(err, "message") ?: "Model returned an error")
            }
            val choice = frame.getAsJsonArray("choices")
                ?.firstOrNull()?.asJsonObject ?: return@collect
            // A few gateways deliver the final chunk under "message" instead of "delta".
            val delta = objectField(choice, "delta")
                ?: objectField(choice, "message")
                ?: return@collect

            // Reasoning models (DeepSeek-R1, Qwen3, o-series through gateways,
            // vLLM, OpenRouter...) expose the chain of thought under one of
            // these aliases while "content" stays null. Never treat a null
            // member as a string: JsonNull.getAsJsonPrimitive throws.
            stringField(
                delta,
                "reasoning_content", "reasoning", "thinking", "thinking_content"
            )?.takeIf { it.isNotEmpty() }?.let {
                reasoningActive = true
                emit(StreamEvent.ReasoningDelta(it))
            }

            stringField(delta, "content")?.takeIf { it.isNotEmpty() }?.let { content ->
                if (textParser != null) {
                    dispatchText(textParser.feed(content))
                } else {
                    if (reasoningActive) {
                        reasoningActive = false
                        emit(StreamEvent.ReasoningEnded())
                    }
                    emit(StreamEvent.TextDelta(content))
                }
            }

            delta.getAsJsonArray("tool_calls")?.forEachIndexed { fallbackIndex, element ->
                val call = element.asJsonObject
                val index = call.get("index")
                    ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
                    ?.asInt ?: fallbackIndex
                val function = objectField(call, "function")
                val slot = pending.getOrPut(index) {
                    MutableOpenAiCall(
                        id = stringField(call, "id").orEmpty(),
                        name = function?.let { stringField(it, "name") }.orEmpty()
                    )
                }
                stringField(call, "id")?.takeIf { it.isNotBlank() }?.let { slot.id = it }
                function?.let { stringField(it, "name") }
                    ?.takeIf { it.isNotBlank() }?.let { slot.name = it }
                function?.let { stringField(it, "arguments") }?.let { slot.arguments.append(it) }
            }

            val finish = choice.get("finish_reason")
            if (finish != null && !finish.isJsonNull) {
                if (reasoningActive) {
                    reasoningActive = false
                    emit(StreamEvent.ReasoningEnded())
                }
                drainPending(pending).forEach { emit(StreamEvent.ToolCallCompleted(it)) }
            }
        }

        if (textParser != null) dispatchText(textParser.finish())
        if (reasoningActive) {
            reasoningActive = false
            emit(StreamEvent.ReasoningEnded())
        }
        drainPending(pending).forEach { emit(StreamEvent.ToolCallCompleted(it)) }
        emit(StreamEvent.Completed)
    }.catch { e ->
        emit(StreamEvent.Failed(e.message ?: "OpenAI-compatible stream failed"))
    }

    /** Converts accumulated argument fragments into complete tool calls. */
    private fun drainPending(
        pending: LinkedHashMap<Int, MutableOpenAiCall>
    ): List<PendingToolCall> {
        val result = pending.values.mapNotNull { slot ->
            if (slot.name.isBlank()) return@mapNotNull null
            PendingToolCall(
                id = slot.id.ifBlank { java.util.UUID.randomUUID().toString() },
                name = slot.name,
                argumentsJson = slot.arguments.toString().ifBlank { "{}" }
            )
        }
        pending.clear()
        return result
    }

    private fun buildRequest(
        request: LlmRequest,
        config: AiProviderConfig,
        apiKey: String,
        streaming: Boolean
    ): Request {
        val body = JsonObject().apply {
            addProperty("model", config.model)
            addProperty(tokenLimitKey(config.model), if (streaming) request.maxTokens else 1)
            addProperty("stream", streaming)
            add("messages", buildMessages(request))
            // Only NATIVE may send `tools`. Gateways such as code 20037 reject
            // the member outright with HTTP 400 before any SSE frame arrives.
            if (streaming && request.toolProtocol == ToolProtocol.NATIVE &&
                request.tools.isNotEmpty()
            ) {
                add("tools", buildTools(request))
                addProperty("tool_choice", "auto")
            }
        }

        return Request.Builder()
            .url(endpoint(config, "chat/completions"))
            .apply { if (apiKey.isNotBlank()) header("Authorization", "Bearer $apiKey") }
            .header("Accept", if (streaming) "text/event-stream" else "application/json")
            .post(jsonRequest(body))
            .build()
    }

    private fun buildMessages(request: LlmRequest): JsonArray = JsonArray().apply {
        add(JsonObject().apply {
            addProperty("role", "system")
            addProperty("content", request.systemPrompt)
        })

        val native = request.toolProtocol == ToolProtocol.NATIVE
        request.turns.forEach { turn ->
            when (turn.role) {
                ChatRole.USER -> add(JsonObject().apply {
                    addProperty("role", "user")
                    add("content", userContent(turn))
                })

                ChatRole.ASSISTANT -> add(JsonObject().apply {
                    addProperty("role", "assistant")
                    if (native) {
                        addProperty("content", turn.content)
                        if (turn.toolCalls.isNotEmpty()) {
                            add("tool_calls", JsonArray().apply {
                                turn.toolCalls.forEach { call ->
                                    add(JsonObject().apply {
                                        addProperty("id", call.id)
                                        addProperty("type", "function")
                                        add("function", JsonObject().apply {
                                            addProperty("name", call.name)
                                            addProperty("arguments", call.argumentsJson)
                                        })
                                    })
                                }
                            })
                        }
                    } else {
                        // A `tool_calls` member without a matching `tools`
                        // request is rejected by strict gateways.
                        addProperty("content", buildString {
                            append(turn.content)
                            turn.toolCalls.forEach { call ->
                                append("\n")
                                append(
                                    TextProtocolMarkers.renderToolCall(
                                        call.name, call.argumentsJson
                                    )
                                )
                            }
                        })
                    }
                })

                ChatRole.TOOL -> add(
                    if (native) {
                        JsonObject().apply {
                            addProperty("role", "tool")
                            addProperty("tool_call_id", turn.toolCallId.orEmpty())
                            addProperty("content", turn.toolResult ?: "{}")
                        }
                    } else {
                        JsonObject().apply {
                            addProperty("role", "user")
                            addProperty(
                                "content",
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

    private fun buildTools(request: LlmRequest): JsonArray = JsonArray().apply {
        request.tools.forEach { spec ->
            add(JsonObject().apply {
                addProperty("type", "function")
                add("function", JsonObject().apply {
                    addProperty("name", spec.name)
                    addProperty("description", spec.description)
                    add("parameters", gson.fromJson(spec.parametersJson, JsonObject::class.java))
                })
            })
        }
    }

    /** Text parts carry file contents; images become base64 data URLs. */
    private fun userContent(turn: LlmTurn): JsonElement {
        if (turn.attachments.isEmpty()) return JsonPrimitive(turn.content)

        val parts = JsonArray()
        val text = buildString {
            append(turn.content)
            turn.attachments.filterNot { it.isImage }.forEach { attachment ->
                append("\n\n[Attached file: ${attachment.displayName}]\n")
                append(codec.readText(attachment, 6_000))
            }
        }
        parts.add(JsonObject().apply {
            addProperty("type", "text")
            addProperty("text", text)
        })
        turn.attachments.filter { it.isImage }.forEach { attachment ->
            val encoded = codec.encodeImage(attachment) ?: return@forEach
            parts.add(JsonObject().apply {
                addProperty("type", "image_url")
                add("image_url", JsonObject().apply {
                    addProperty("url", "data:${encoded.mediaType};base64,${encoded.base64}")
                })
            })
        }
        return parts
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

    /** Reasoning-first models reject the legacy `max_tokens` parameter. */
    private fun tokenLimitKey(model: String): String {
        val normalized = model.lowercase()
        return if (normalized.startsWith("o1") || normalized.startsWith("o3") ||
            normalized.startsWith("o4") || normalized.startsWith("gpt-5")
        ) {
            "max_completion_tokens"
        } else {
            "max_tokens"
        }
    }

    private class MutableOpenAiCall(
        var id: String,
        var name: String,
        val arguments: StringBuilder = StringBuilder()
    )

    private companion object {
        const val DONE_MARKER = "[DONE]"
    }
}

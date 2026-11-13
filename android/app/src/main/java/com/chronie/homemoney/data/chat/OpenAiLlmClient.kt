package com.chronie.homemoney.data.chat

import com.chronie.homemoney.domain.model.chat.AiProviderConfig
import com.chronie.homemoney.domain.model.chat.ChatRole
import com.chronie.homemoney.domain.model.chat.LlmRequest
import com.chronie.homemoney.domain.model.chat.LlmTurn
import com.chronie.homemoney.domain.model.chat.PendingToolCall
import com.chronie.homemoney.domain.model.chat.StreamEvent
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

        sseData(httpRequest).collect { data ->
            if (data.trim() == DONE_MARKER) return@collect
            val frame = parseObject(data) ?: return@collect
            frame.getAsJsonObject("error")?.let { err ->
                error(err.getAsJsonPrimitive("message")?.asString ?: "Model returned an error")
            }
            val choice = frame.getAsJsonArray("choices")
                ?.firstOrNull()?.asJsonObject ?: return@collect
            val delta = choice.getAsJsonObject("delta") ?: return@collect

            delta.getAsJsonPrimitive("content")?.asString
                ?.takeIf { it.isNotEmpty() }
                ?.let { emit(StreamEvent.TextDelta(it)) }

            delta.getAsJsonArray("tool_calls")?.forEachIndexed { fallbackIndex, element ->
                val call = element.asJsonObject
                val index = call.getAsJsonPrimitive("index")?.asInt ?: fallbackIndex
                val function = call.getAsJsonObject("function")
                val slot = pending.getOrPut(index) {
                    MutableOpenAiCall(
                        id = call.getAsJsonPrimitive("id")?.asString.orEmpty(),
                        name = function?.getAsJsonPrimitive("name")?.asString.orEmpty()
                    )
                }
                call.getAsJsonPrimitive("id")?.asString?.takeIf { it.isNotBlank() }
                    ?.let { slot.id = it }
                function?.getAsJsonPrimitive("name")?.asString?.takeIf { it.isNotBlank() }
                    ?.let { slot.name = it }
                function?.getAsJsonPrimitive("arguments")?.asString
                    ?.let { slot.arguments.append(it) }
            }

            val finish = choice.get("finish_reason")
            if (finish != null && !finish.isJsonNull) {
                drainPending(pending).forEach { emit(StreamEvent.ToolCallCompleted(it)) }
            }
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
            addProperty("max_tokens", if (streaming) request.maxTokens else 1)
            addProperty("stream", streaming)
            add("messages", buildMessages(request))
            if (streaming && request.tools.isNotEmpty()) {
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

        request.turns.forEach { turn ->
            when (turn.role) {
                ChatRole.USER -> add(JsonObject().apply {
                    addProperty("role", "user")
                    add("content", userContent(turn))
                })

                ChatRole.ASSISTANT -> add(JsonObject().apply {
                    addProperty("role", "assistant")
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
                })

                ChatRole.TOOL -> add(JsonObject().apply {
                    addProperty("role", "tool")
                    addProperty("tool_call_id", turn.toolCallId.orEmpty())
                    addProperty("content", turn.toolResult ?: "{}")
                })
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

    private class MutableOpenAiCall(
        var id: String,
        var name: String,
        val arguments: StringBuilder = StringBuilder()
    )

    private companion object {
        const val DONE_MARKER = "[DONE]"
    }
}

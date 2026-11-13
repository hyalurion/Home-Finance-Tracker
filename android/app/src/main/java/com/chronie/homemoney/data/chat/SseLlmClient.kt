package com.chronie.homemoney.data.chat

import com.chronie.homemoney.domain.model.chat.AiProviderConfig
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import javax.inject.Named

/**
 * Shared plumbing for OpenAI- and Anthropic-compatible streaming endpoints.
 *
 * Uses OkHttp's official `okhttp-sse` extension (Apache-2.0) rather than a
 * hand-rolled line parser: EventSources handles `data:` framing, comment
 * heartbeats, multi-line events and call-timeout cancellation for us.
 * The [callbackFlow] bridges its thread-pool callbacks into a cold Flow and
 * cancels the HTTP call on coroutine cancellation (e.g. "Stop generating").
 */
abstract class SseLlmClient(
    @param:Named("AiStreamClient") protected val client: OkHttpClient,
    protected val gson: Gson
) : LlmClient {

    /**
     * Emits the `data:` payload of every SSE event; throws on transport failure.
     * An unlimited channel prevents OkHttp's thread pool from dropping frames
     * when the collector is briefly busy rendering a delta.
     */
    protected fun sseData(httpRequest: Request): Flow<String> =
        channelFlow {
        val listener = object : EventSourceListener() {
            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String
            ) {
                if (data.isNotBlank()) trySend(data)
            }

            override fun onFailure(
                eventSource: EventSource,
                t: Throwable?,
                response: Response?
            ) {
                val bodyHint = response?.let { r ->
                    runCatching { r.body.string().take(300) }.getOrNull()
                }
                val detail = when {
                    response != null -> "HTTP ${response.code} ${response.message}" +
                        (bodyHint?.let { ": $it" } ?: "")
                    t != null -> t.message ?: t.javaClass.simpleName
                    else -> "SSE stream closed unexpectedly"
                }
                close(IllegalStateException(detail))
            }

            override fun onClosed(eventSource: EventSource) {
                close()
            }
        }

        val factory = EventSources.createFactory(client)
        val source = factory.newEventSource(httpRequest, listener)
        awaitClose { source.cancel() }
    }.buffer(Channel.UNLIMITED)

    /** Joins a relative API path onto a user-supplied base URL. */
    protected fun endpoint(config: AiProviderConfig, path: String): okhttp3.HttpUrl {
        val base = config.baseUrl.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid base URL: ${config.baseUrl}")
        // Avoid producing /v1/v1/... when the user already entered a versioned base.
        val effective = if (base.encodedPath.contains("/v1") && path.startsWith("v1/")) {
            path.removePrefix("v1/")
        } else {
            path
        }
        return base.newBuilder().addEncodedPathSegments(effective).build()
    }

    protected val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    protected fun jsonRequest(body: JsonObject): okhttp3.RequestBody =
        body.toString().toRequestBody(jsonMediaType)

    protected fun parseObject(data: String): JsonObject? = runCatching {
        gson.fromJson(data, JsonObject::class.java)
    }.getOrNull()

    /** Non-streaming one-shot call used by the "Test connection" button. */
    protected suspend fun executeProbe(httpRequest: Request): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(httpRequest).execute().use { response ->
                    val body = response.body.string()
                    if (!response.isSuccessful) {
                        error("HTTP ${response.code}: ${body.take(300)}")
                    }
                    "OK (${response.code})"
                }
            }
        }
}

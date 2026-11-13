package com.chronie.homemoney.domain.model.chat

import java.util.UUID

/**
 * Wire protocol the app knows how to speak. The built-in engine has no
 * network API; OpenAI/Anthropic-compatible endpoints are the two supported
 * cloud dialects (both streaming over SSE).
 */
enum class ProviderFormat {
    BUILT_IN,
    OPENAI,
    ANTHROPIC;

    companion object {
        fun fromStorage(value: String): ProviderFormat =
            entries.firstOrNull { it.name == value } ?: OPENAI
    }
}

/**
 * How a provider wants tools to be delivered.
 *
 * Many OpenAI-compatible gateways (including some hosted Chinese models)
 * reject a `tools` member with `HTTP 400 Function call is not supported for
 * this model`. AUTO first tries native function calling and transparently
 * falls back to a plain-text marker protocol; TEXT skips the failing request
 * entirely; OFF disables tools for that provider.
 */
enum class ProviderToolMode {
    AUTO,
    TEXT,
    OFF;

    companion object {
        /** Null/blank covers preferences written before this field existed. */
        fun fromStorage(value: String?): ProviderToolMode =
            entries.firstOrNull { it.name == value } ?: AUTO
    }
}

/**
 * Configuration of one model source. API keys are intentionally NOT stored
 * here: [com.chronie.homemoney.data.local.AiProviderConfigManager] keeps them
 * in EncryptedSharedPreferences and only exposes [hasApiKey] for display.
 *
 * [toolMode] is persisted as a string rather than an enum so Gson can read
 * configs saved by older app versions without failing on a missing member.
 */
data class AiProviderConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val format: ProviderFormat = ProviderFormat.OPENAI,
    val baseUrl: String = "",
    val model: String = "",
    val hasApiKey: Boolean = false,
    val toolMode: String? = null
) {
    val isBuiltIn: Boolean get() = format == ProviderFormat.BUILT_IN
    val resolvedToolMode: ProviderToolMode get() = ProviderToolMode.fromStorage(toolMode)
}

/** Effective tool protocol for one request, after resolving provider capability. */
enum class ToolProtocol {
    /** Native `tools` / `tool_use` schema in the HTTP request. */
    NATIVE,

    /** Tool calls embedded as plain-text markers in the message body. */
    TEXT,

    /** No tools at all; the model may only answer from the conversation. */
    NONE
}

/** JSON-Schema description of a callable tool, shared by all providers. */
data class ToolSpec(
    val name: String,
    val description: String,
    /** A JSON Schema object literal (type/properties/required). */
    val parametersJson: String
)

/** A tool call requested by the model, fully assembled from streamed deltas. */
data class PendingToolCall(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val argumentsJson: String
)

/**
 * Provider-neutral conversation turn. Each client maps this into its own
 * wire format (OpenAI messages / Anthropic content blocks / a flat prompt).
 */
data class LlmTurn(
    val role: ChatRole,
    val content: String = "",
    val attachments: List<ChatAttachment> = emptyList(),
    val toolCalls: List<PendingToolCall> = emptyList(),
    /** Set on TOOL turns; references the assistant tool call being answered. */
    val toolCallId: String? = null,
    val toolName: String? = null,
    /** Set on TOOL turns; the JSON result returned to the model. */
    val toolResult: String? = null,
    /** Reasoning text of an ASSISTANT turn; only Anthropic replays it. */
    val reasoningContent: String = "",
    /** Signature paired with [reasoningContent] for Anthropic thinking blocks. */
    val reasoningSignature: String = ""
)

data class LlmRequest(
    val systemPrompt: String,
    val turns: List<LlmTurn>,
    val tools: List<ToolSpec>,
    val maxTokens: Int = 2048,
    /** NATIVE adds the tools schema; TEXT/NONE omit it from the HTTP body. */
    val toolProtocol: ToolProtocol = ToolProtocol.NATIVE
)

/** Incremental events emitted while a model response is being generated. */
sealed interface StreamEvent {
    /** A fragment of the model's chain of thought, shown in a distinct block. */
    data class ReasoningDelta(val text: String) : StreamEvent

    /**
     * Marks the end of the reasoning phase. [signature] is the Anthropic
     * extended-thinking signature accumulated from `signature_delta` frames.
     */
    data class ReasoningEnded(val signature: String = "") : StreamEvent

    data class TextDelta(val text: String) : StreamEvent
    data class ToolCallCompleted(val call: PendingToolCall) : StreamEvent
    data object Completed : StreamEvent
    data class Failed(val message: String) : StreamEvent
}

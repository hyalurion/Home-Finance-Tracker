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
 * Configuration of one model source. API keys are intentionally NOT stored
 * here: [com.chronie.homemoney.data.local.AiProviderConfigManager] keeps them
 * in EncryptedSharedPreferences and only exposes [hasApiKey] for display.
 */
data class AiProviderConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val format: ProviderFormat = ProviderFormat.OPENAI,
    val baseUrl: String = "",
    val model: String = "",
    val hasApiKey: Boolean = false
) {
    val isBuiltIn: Boolean get() = format == ProviderFormat.BUILT_IN
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
    val toolResult: String? = null
)

data class LlmRequest(
    val systemPrompt: String,
    val turns: List<LlmTurn>,
    val tools: List<ToolSpec>,
    val maxTokens: Int = 2048
)

/** Incremental events emitted while a model response is being generated. */
sealed interface StreamEvent {
    data class TextDelta(val text: String) : StreamEvent
    data class ToolCallCompleted(val call: PendingToolCall) : StreamEvent
    data object Completed : StreamEvent
    data class Failed(val message: String) : StreamEvent
}

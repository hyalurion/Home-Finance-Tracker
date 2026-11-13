package com.chronie.homemoney.domain.model.chat

import java.util.UUID

/** Who produced a chat message. */
enum class ChatRole {
    USER,
    ASSISTANT,
    TOOL;

    companion object {
        fun fromStorage(value: String): ChatRole = when (value.uppercase()) {
            "USER" -> USER
            "TOOL" -> TOOL
            else -> ASSISTANT
        }
    }
}

/** Lifecycle of a single message, used to drive streaming and error UI. */
enum class ChatMessageStatus {
    SENDING,
    STREAMING,
    COMPLETE,
    FAILED,
    CANCELLED;

    companion object {
        fun fromStorage(value: String): ChatMessageStatus =
            entries.firstOrNull { it.name == value } ?: COMPLETE
    }
}

/** State of one tool invocation attached to an assistant message. */
enum class ToolCallStatus {
    RUNNING,
    /** Waiting for the user to approve a write (when auto-write is disabled). */
    PENDING_CONFIRM,
    SUCCESS,
    FAILED,
    DENIED;

    companion object {
        fun fromStorage(value: String): ToolCallStatus =
            entries.firstOrNull { it.name == value } ?: RUNNING
    }
}

/**
 * A user-supplied file copied into app-private storage before being sent.
 * Keeping a private copy means a conversation can be replayed after the
 * original content:// URI permission has expired.
 */
data class ChatAttachment(
    val id: String = UUID.randomUUID().toString(),
    val path: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long = 0L
) {
    val isImage: Boolean get() = mimeType.startsWith("image/", ignoreCase = true)
    val isText: Boolean
        get() = mimeType.startsWith("text/") ||
            mimeType in setOf(
                "application/json",
                "application/xml",
                "application/csv",
                "text/csv",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            )
}

/**
 * A single tool call and its outcome. Rendered as a dedicated card inside
 * the assistant bubble so users can audit every write/query the AI performs.
 */
data class ToolCallRecord(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val argumentsJson: String,
    val resultJson: String? = null,
    val status: ToolCallStatus = ToolCallStatus.RUNNING,
    val summary: String = ""
)

/** A message in a conversation. Collections are stored as JSON in Room. */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val conversationId: String,
    val role: ChatRole,
    val content: String = "",
    val attachments: List<ChatAttachment> = emptyList(),
    val toolCalls: List<ToolCallRecord> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val status: ChatMessageStatus = ChatMessageStatus.COMPLETE,
    val providerLabel: String? = null,
    /** Chain-of-thought text rendered separately from the final answer. */
    val reasoningContent: String = "",
    /** Wall-clock time spent in the reasoning phase, in milliseconds. */
    val reasoningDurationMs: Long = 0L,
    /** Anthropic extended-thinking signature, needed to replay a thinking block. */
    val reasoningSignature: String = ""
)

/** A resumable chat session. */
data class ChatConversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val providerId: String,
    val messageCount: Int = 0
)

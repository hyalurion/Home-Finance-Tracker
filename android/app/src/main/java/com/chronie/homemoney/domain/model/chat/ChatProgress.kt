package com.chronie.homemoney.domain.model.chat

/** Incremental updates emitted while the repository generates an answer. */
sealed interface ChatProgress {
    data class AssistantStarted(val message: ChatMessage) : ChatProgress
    data class AssistantDelta(val text: String) : ChatProgress
    data class ToolUpdated(val call: ToolCallRecord) : ChatProgress
    data object Finished : ChatProgress
    data class Failed(val message: String) : ChatProgress
}

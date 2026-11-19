package com.chronie.homemoney.domain.repository

import com.chronie.homemoney.domain.model.chat.AiProviderConfig
import com.chronie.homemoney.domain.model.chat.ChatAttachment
import com.chronie.homemoney.domain.model.chat.ChatConversation
import com.chronie.homemoney.domain.model.chat.ChatMessage
import com.chronie.homemoney.domain.model.chat.ChatProgress
import kotlinx.coroutines.flow.Flow

/** Persistence + orchestration contract for the AI chat feature. */
interface ChatRepository {

    fun observeConversations(): Flow<List<ChatConversation>>

    fun observeMessages(conversationId: String): Flow<List<ChatMessage>>

    fun observeProviders(): Flow<List<AiProviderConfig>>

    fun activeProvider(): AiProviderConfig

    suspend fun createConversation(providerId: String): ChatConversation

    /** Returns the existing session or creates one when the id is unknown. */
    suspend fun ensureConversation(conversationId: String?, providerId: String): ChatConversation

    suspend fun deleteConversation(conversationId: String)

    suspend fun clearAllHistory()

    /** Persists whatever was generated before the user pressed "Stop". */
    suspend fun savePartialMessage(message: ChatMessage)

    fun sendMessage(
        conversationId: String,
        text: String,
        attachments: List<ChatAttachment>
    ): Flow<ChatProgress>

    /**
     * Edits a previously sent user message: drops it and every later turn,
     * reuses its attachments, then regenerates the assistant answer.
     */
    fun editAndResend(
        conversationId: String,
        messageId: String,
        text: String
    ): Flow<ChatProgress>

    /** Approves a write that was held back by the auto-write consent switch. */
    fun confirmToolCall(
        conversationId: String,
        messageId: String,
        toolCallId: String
    ): Flow<ChatProgress>

    suspend fun probeProvider(config: AiProviderConfig, apiKey: String): Result<String>
}

package com.chronie.homemoney.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.chronie.homemoney.data.local.entity.ChatConversationEntity
import com.chronie.homemoney.data.local.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

/** Persistence for AI chat sessions and their messages. */
@Dao
interface ChatDao {

    @Query("SELECT * FROM chat_conversations ORDER BY updated_at DESC")
    fun observeConversations(): Flow<List<ChatConversationEntity>>

    @Query("SELECT * FROM chat_conversations")
    suspend fun getConversationsOnce(): List<ChatConversationEntity>

    @Query("SELECT * FROM chat_messages WHERE conversation_id = :conversationId ORDER BY created_at ASC")
    fun observeMessages(conversationId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE conversation_id = :conversationId ORDER BY created_at ASC")
    suspend fun getMessages(conversationId: String): List<ChatMessageEntity>

    @Query("SELECT COUNT(*) FROM chat_messages WHERE conversation_id = :conversationId")
    suspend fun countMessages(conversationId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ChatConversationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessageEntity>)

    @Query(
        "UPDATE chat_conversations SET title = :title, " +
            "updated_at = :updatedAt, provider_id = :providerId WHERE id = :conversationId"
    )
    suspend fun updateConversation(
        conversationId: String,
        title: String,
        updatedAt: Long,
        providerId: String
    )

    @Query(
        "UPDATE chat_conversations SET updated_at = :updatedAt, " +
            "provider_id = :providerId WHERE id = :conversationId"
    )
    suspend fun touchConversation(
        conversationId: String,
        updatedAt: Long,
        providerId: String
    )

    /**
     * Removes an exact set of messages by id. Used when a user message is
     * edited: that message and every later turn are dropped before the new
     * text is sent, so the transcript never contains the stale branch.
     */
    @Query(
        "DELETE FROM chat_messages WHERE conversation_id = :conversationId " +
            "AND id IN (:messageIds)"
    )
    suspend fun deleteMessagesByIds(conversationId: String, messageIds: List<String>)

    @Query("DELETE FROM chat_messages WHERE conversation_id = :conversationId")
    suspend fun deleteMessages(conversationId: String)

    @Query("DELETE FROM chat_conversations WHERE id = :conversationId")
    suspend fun deleteConversation(conversationId: String)

    /** Clears a session atomically. No foreign key is declared, hence the explicit delete. */
    @Transaction
    suspend fun deleteConversationWithMessages(conversationId: String) {
        deleteMessages(conversationId)
        deleteConversation(conversationId)
    }

    @Query("DELETE FROM chat_messages")
    suspend fun clearAllMessages()

    @Query("DELETE FROM chat_conversations")
    suspend fun clearAllConversations()
}

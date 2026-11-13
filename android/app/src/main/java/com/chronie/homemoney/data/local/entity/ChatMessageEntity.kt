package com.chronie.homemoney.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room row for one chat message. Attachments and tool calls are stored as
 * JSON strings to avoid extra tables; they are always decoded by the
 * repository before reaching the UI layer.
 */
@Entity(
    tableName = "chat_messages",
    indices = [Index(value = ["conversation_id"])]
)
data class ChatMessageEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "conversation_id")
    val conversationId: String,

    /** USER / ASSISTANT / TOOL, see ChatRole. */
    @ColumnInfo(name = "role")
    val role: String,

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "attachments_json")
    val attachmentsJson: String,

    @ColumnInfo(name = "tool_calls_json")
    val toolCallsJson: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "provider_label")
    val providerLabel: String?
)

package com.chronie.homemoney.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room row for one AI chat session.
 *
 * Sessions are deliberately kept local: conversations may contain personal
 * financial details, so they are not part of the cloud sync protocol.
 */
@Entity(tableName = "chat_conversations")
data class ChatConversationEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    /** Provider id used for the last turn, so a resumed chat keeps its model. */
    @ColumnInfo(name = "provider_id")
    val providerId: String
)

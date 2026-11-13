package com.chronie.homemoney.data.mapper

import com.chronie.homemoney.data.local.entity.ChatConversationEntity
import com.chronie.homemoney.data.local.entity.ChatMessageEntity
import com.chronie.homemoney.domain.model.chat.ChatAttachment
import com.chronie.homemoney.domain.model.chat.ChatConversation
import com.chronie.homemoney.domain.model.chat.ChatMessage
import com.chronie.homemoney.domain.model.chat.ChatMessageStatus
import com.chronie.homemoney.domain.model.chat.ChatRole
import com.chronie.homemoney.domain.model.chat.ToolCallRecord
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** Converts chat rows to/from domain models; lists are stored as JSON text. */
object ChatMapper {

    private val attachmentListType = object : TypeToken<List<ChatAttachment>>() {}.type
    private val toolCallListType = object : TypeToken<List<ToolCallRecord>>() {}.type

    fun conversationToDomain(entity: ChatConversationEntity): ChatConversation =
        ChatConversation(
            id = entity.id,
            title = entity.title,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            providerId = entity.providerId
        )

    fun conversationToEntity(model: ChatConversation): ChatConversationEntity =
        ChatConversationEntity(
            id = model.id,
            title = model.title,
            createdAt = model.createdAt,
            updatedAt = model.updatedAt,
            providerId = model.providerId
        )

    fun messageToDomain(entity: ChatMessageEntity, gson: Gson): ChatMessage = ChatMessage(
        id = entity.id,
        conversationId = entity.conversationId,
        role = ChatRole.fromStorage(entity.role),
        content = entity.content,
        attachments = decode(entity.attachmentsJson, gson),
        toolCalls = decodeToolCalls(entity.toolCallsJson, gson),
        createdAt = entity.createdAt,
        status = ChatMessageStatus.fromStorage(entity.status),
        providerLabel = entity.providerLabel,
        reasoningContent = entity.reasoningContent,
        reasoningDurationMs = entity.reasoningDurationMs,
        reasoningSignature = entity.reasoningSignature
    )

    fun messageToEntity(model: ChatMessage, gson: Gson): ChatMessageEntity = ChatMessageEntity(
        id = model.id,
        conversationId = model.conversationId,
        role = model.role.name,
        content = model.content,
        attachmentsJson = gson.toJson(model.attachments),
        toolCallsJson = gson.toJson(model.toolCalls),
        createdAt = model.createdAt,
        status = model.status.name,
        providerLabel = model.providerLabel,
        reasoningContent = model.reasoningContent,
        reasoningDurationMs = model.reasoningDurationMs,
        reasoningSignature = model.reasoningSignature
    )

    private fun decode(json: String, gson: Gson): List<ChatAttachment> = runCatching {
        gson.fromJson<List<ChatAttachment>>(json, attachmentListType) ?: emptyList()
    }.getOrDefault(emptyList())

    private fun decodeToolCalls(json: String, gson: Gson): List<ToolCallRecord> = runCatching {
        gson.fromJson<List<ToolCallRecord>>(json, toolCallListType) ?: emptyList()
    }.getOrDefault(emptyList())
}

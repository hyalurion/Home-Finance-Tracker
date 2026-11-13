package com.chronie.homemoney.data.repository

import android.util.Log
import com.chronie.homemoney.data.chat.AgentToolExecutor
import com.chronie.homemoney.data.chat.AgentToolSpecs
import com.chronie.homemoney.data.chat.ChatAttachmentCodec
import com.chronie.homemoney.data.chat.ChatSystemPrompt
import com.chronie.homemoney.data.chat.LlmClient
import com.chronie.homemoney.data.chat.LlmClientFactory
import com.chronie.homemoney.data.local.AiProviderConfigManager
import com.chronie.homemoney.data.local.dao.ChatDao
import com.chronie.homemoney.data.mapper.ChatMapper
import com.chronie.homemoney.domain.model.chat.AiProviderConfig
import com.chronie.homemoney.domain.model.chat.ChatAttachment
import com.chronie.homemoney.domain.model.chat.ChatConversation
import com.chronie.homemoney.domain.model.chat.ChatMessage
import com.chronie.homemoney.domain.model.chat.ChatMessageStatus
import com.chronie.homemoney.domain.model.chat.ChatProgress
import com.chronie.homemoney.domain.model.chat.ChatRole
import com.chronie.homemoney.domain.model.chat.LlmRequest
import com.chronie.homemoney.domain.model.chat.LlmTurn
import com.chronie.homemoney.domain.model.chat.PendingToolCall
import com.chronie.homemoney.domain.model.chat.ProviderToolMode
import com.chronie.homemoney.domain.model.chat.StreamEvent
import com.chronie.homemoney.domain.model.chat.ToolCallRecord
import com.chronie.homemoney.domain.model.chat.ToolCallStatus
import com.chronie.homemoney.domain.model.chat.ToolProtocol
import com.chronie.homemoney.domain.model.chat.ToolSpec
import com.chronie.homemoney.domain.repository.ChatRepository
import com.google.gson.Gson
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates conversations: persistence, streaming generation, and the
 * tool-call loop (model -> tool result -> model) up to [MAX_TOOL_ROUNDS].
 *
 * Tool delivery is per-provider. Many OpenAI-compatible gateways reject a
 * `tools` member with `HTTP 400 Function call is not supported for this
 * model`; AUTO mode retries the same turn with a plain-text tool protocol and
 * remembers the capability so later turns never repeat the failed request.
 */
@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val chatDao: ChatDao,
    private val gson: Gson,
    private val configManager: AiProviderConfigManager,
    private val clientFactory: LlmClientFactory,
    private val toolExecutor: AgentToolExecutor,
    private val codec: ChatAttachmentCodec
) : ChatRepository {

    override fun observeConversations(): Flow<List<ChatConversation>> =
        chatDao.observeConversations().map { rows ->
            rows.map(ChatMapper::conversationToDomain)
        }

    override fun observeMessages(conversationId: String): Flow<List<ChatMessage>> =
        chatDao.observeMessages(conversationId).map { rows ->
            rows.map { ChatMapper.messageToDomain(it, gson) }
        }

    override fun observeProviders(): Flow<List<AiProviderConfig>> = configManager.providers

    override fun activeProvider(): AiProviderConfig = configManager.activeProvider()

    override suspend fun createConversation(providerId: String): ChatConversation {
        val now = System.currentTimeMillis()
        val conversation = ChatConversation(
            title = "",
            createdAt = now,
            updatedAt = now,
            providerId = providerId
        )
        chatDao.insertConversation(ChatMapper.conversationToEntity(conversation))
        return conversation
    }

    override suspend fun ensureConversation(
        conversationId: String?,
        providerId: String
    ): ChatConversation {
        if (conversationId.isNullOrBlank()) return createConversation(providerId)
        // A direct lookup avoids subscribing to a Flow for a one-shot read.
        val existing = chatDao.getConversationsOnce().firstOrNull { it.id == conversationId }
        return if (existing == null) {
            createConversation(providerId)
        } else {
            chatDao.touchConversation(
                conversationId = conversationId,
                updatedAt = System.currentTimeMillis(),
                providerId = providerId
            )
            ChatMapper.conversationToDomain(existing).copy(providerId = providerId)
        }
    }

    override suspend fun deleteConversation(conversationId: String) {
        val attachments = chatDao.getMessages(conversationId).flatMap {
            ChatMapper.messageToDomain(it, gson).attachments
        }
        codec.deleteFiles(attachments)
        chatDao.deleteConversationWithMessages(conversationId)
    }

    override suspend fun clearAllHistory() {
        chatDao.clearAllMessages()
        chatDao.clearAllConversations()
    }

    override suspend fun savePartialMessage(message: ChatMessage) {
        persist(message.copy(status = ChatMessageStatus.CANCELLED))
    }

    override fun sendMessage(
        conversationId: String,
        text: String,
        attachments: List<ChatAttachment>
    ): Flow<ChatProgress> = channelFlow {
        val userMessage = ChatMessage(
            conversationId = conversationId,
            role = ChatRole.USER,
            content = text,
            attachments = attachments,
            status = ChatMessageStatus.COMPLETE
        )
        generate(conversationId, userMessage)
    }.buffer(Channel.UNLIMITED)

    override fun confirmToolCall(
        conversationId: String,
        messageId: String,
        toolCallId: String
    ): Flow<ChatProgress> = channelFlow {
        val entity = chatDao.getMessages(conversationId).firstOrNull { it.id == messageId }
        if (entity == null) {
            trySend(ChatProgress.Failed("Message not found"))
            return@channelFlow
        }
        val message = ChatMapper.messageToDomain(entity, gson)
        val target = message.toolCalls.firstOrNull {
            it.id == toolCallId && it.status == ToolCallStatus.PENDING_CONFIRM
        }
        if (target == null) {
            trySend(ChatProgress.Failed("Tool call is no longer pending"))
            return@channelFlow
        }

        val execution = toolExecutor.execute(
            PendingToolCall(target.id, target.name, target.argumentsJson),
            forceWrite = true
        )
        val updated = message.copy(
            toolCalls = message.toolCalls.map { if (it.id == toolCallId) execution.record else it }
        )
        persist(updated)
        trySend(ChatProgress.ToolUpdated(execution.record))
        // Rebuild the transcript from Room so the confirmed result is included.
        generate(conversationId, null)
    }.buffer(Channel.UNLIMITED)

    override suspend fun probeProvider(
        config: AiProviderConfig,
        apiKey: String
    ): Result<String> = clientFactory.create(config).probe(config, apiKey)

    // ------------------------------------------------------------------
    // Generation loop
    // ------------------------------------------------------------------

    private suspend fun ProducerScope<ChatProgress>.generate(
        conversationId: String,
        userMessage: ChatMessage?
    ) {
        if (userMessage != null) {
            persist(userMessage)
            val existing = chatDao.getConversationsOnce().firstOrNull { it.id == conversationId }
            if (existing?.title.isNullOrBlank()) {
                val title = userMessage.content.trim().takeIf { it.isNotBlank() }
                    ?: userMessage.attachments.firstOrNull()?.displayName
                    ?: "New chat"
                chatDao.updateConversation(
                    conversationId = conversationId,
                    title = title.take(40),
                    updatedAt = System.currentTimeMillis(),
                    providerId = configManager.activeProviderId.value
                )
            }
        }

        val config = configManager.activeProvider()
        val apiKey = configManager.apiKeyFor(config.id)
        val client = clientFactory.create(config)

        val tools: List<ToolSpec> = if (config.resolvedToolMode == ProviderToolMode.OFF) {
            emptyList()
        } else {
            AgentToolSpecs.available(configManager.isQueryAuthorized())
        }

        var protocol = resolveProtocol(config, tools)
        var turns = loadTurns(conversationId)
        val maxTokens = if (config.isBuiltIn) ON_DEVICE_OUTPUT_TOKENS else MAX_OUTPUT_TOKENS
        var fallbackUsed = false

        repeat(MAX_TOOL_ROUNDS) {
            val assistant = ChatMessage(
                conversationId = conversationId,
                role = ChatRole.ASSISTANT,
                status = ChatMessageStatus.STREAMING,
                providerLabel = config.name
            )
            trySend(ChatProgress.AssistantStarted(assistant))

            var outcome = runAttempt(
                client, config, apiKey, turns, tools, protocol, maxTokens
            )

            // A native tool request can be rejected before the first SSE frame
            // (HTTP 400 / code 20037). Retry the same turn with text markers;
            // the partial answer is empty in that case, so no UI text doubles.
            val firstFailure = outcome.failure
            if (firstFailure != null && !fallbackUsed &&
                protocol == ToolProtocol.NATIVE && !outcome.emittedDeltas &&
                (isToolUnsupportedError(firstFailure) || isBareHttpRejection(firstFailure))
            ) {
                Log.i(TAG, "Provider rejected native tools; switching to text protocol")
                configManager.rememberTextToolFallback(config.id)
                protocol = ToolProtocol.TEXT
                fallbackUsed = true
                outcome = runAttempt(
                    client, config, apiKey, turns, tools, protocol, maxTokens
                )
            }

            val failure = outcome.failure
            if (failure != null) {
                persist(
                    assistant.copy(
                        content = outcome.answer,
                        reasoningContent = outcome.reasoning,
                        reasoningDurationMs = outcome.reasoningDurationMs,
                        reasoningSignature = outcome.reasoningSignature,
                        status = ChatMessageStatus.FAILED
                    )
                )
                trySend(ChatProgress.Failed(failure))
                return
            }

            if (outcome.requested.isEmpty()) {
                persist(
                    assistant.copy(
                        content = outcome.answer,
                        reasoningContent = outcome.reasoning,
                        reasoningDurationMs = outcome.reasoningDurationMs,
                        reasoningSignature = outcome.reasoningSignature,
                        status = ChatMessageStatus.COMPLETE
                    )
                )
                trySend(ChatProgress.Finished)
                return
            }

            // Persist the assistant turn with pending tool cards, then execute
            // each tool and update the same message as results arrive.
            var toolRecords: List<ToolCallRecord> = outcome.requested.map { call ->
                ToolCallRecord(
                    id = call.id,
                    name = call.name,
                    argumentsJson = call.argumentsJson,
                    status = ToolCallStatus.RUNNING
                )
            }
            var stored = assistant.copy(
                content = outcome.answer,
                reasoningContent = outcome.reasoning,
                reasoningDurationMs = outcome.reasoningDurationMs,
                reasoningSignature = outcome.reasoningSignature,
                toolCalls = toolRecords,
                status = ChatMessageStatus.COMPLETE
            )
            persist(stored)

            val toolTurns = mutableListOf<LlmTurn>()
            var awaitingConfirmation = false
            outcome.requested.forEach { call ->
                val execution = toolExecutor.execute(call)
                toolRecords = toolRecords.map {
                    if (it.id == execution.record.id) execution.record else it
                }
                stored = stored.copy(toolCalls = toolRecords)
                persist(stored)
                trySend(ChatProgress.ToolUpdated(execution.record))
                toolTurns.add(
                    LlmTurn(
                        role = ChatRole.TOOL,
                        toolCallId = call.id,
                        toolName = call.name,
                        toolResult = execution.resultForModel
                    )
                )
                if (execution.record.status == ToolCallStatus.PENDING_CONFIRM) {
                    awaitingConfirmation = true
                }
            }

            if (awaitingConfirmation) {
                trySend(ChatProgress.Finished)
                return
            }

            turns = turns +
                LlmTurn(
                    role = ChatRole.ASSISTANT,
                    content = outcome.answer,
                    toolCalls = outcome.requested,
                    reasoningContent = outcome.reasoning,
                    reasoningSignature = outcome.reasoningSignature
                ) +
                toolTurns
        }

        Log.w(TAG, "Tool loop reached its round limit")
        trySend(ChatProgress.Finished)
    }

    /** Resolves how tools reach the model for this provider and permission set. */
    private fun resolveProtocol(
        config: AiProviderConfig,
        tools: List<ToolSpec>
    ): ToolProtocol = when {
        tools.isEmpty() -> ToolProtocol.NONE
        config.isBuiltIn -> ToolProtocol.TEXT
        config.resolvedToolMode == ProviderToolMode.TEXT -> ToolProtocol.TEXT
        else -> ToolProtocol.NATIVE
    }

    /**
     * Runs one model turn, forwarding live deltas to the UI and collecting the
     * final state. Kept separate from the tool loop so a capability fallback
     * can re-run the same turn with a different protocol without duplicating
     * persisted or rendered content.
     */
    private suspend fun ProducerScope<ChatProgress>.runAttempt(
        client: LlmClient,
        config: AiProviderConfig,
        apiKey: String,
        turns: List<LlmTurn>,
        tools: List<ToolSpec>,
        protocol: ToolProtocol,
        maxTokens: Int
    ): AttemptOutcome {
        val answer = StringBuilder()
        val reasoning = StringBuilder()
        val requested = mutableListOf<PendingToolCall>()
        var failure: String? = null
        var reasoningStartedAt: Long? = null
        var reasoningDurationMs = 0L
        var reasoningSignature = ""
        var reasoningActive = false
        var emittedDeltas = false

        fun endReasoning(signature: String = "") {
            if (!reasoningActive) return
            reasoningActive = false
            if (signature.isNotBlank()) reasoningSignature = signature
            reasoningDurationMs = reasoningStartedAt
                ?.let { System.currentTimeMillis() - it } ?: 0L
            trySend(ChatProgress.ReasoningEnded(reasoningDurationMs))
        }

        val systemPrompt = ChatSystemPrompt.build(tools, protocol, config.isBuiltIn)
        client.stream(
            LlmRequest(
                systemPrompt = systemPrompt,
                turns = turns,
                tools = tools,
                maxTokens = maxTokens,
                toolProtocol = protocol
            ),
            config,
            apiKey
        ).collect { event ->
            when (event) {
                is StreamEvent.ReasoningDelta -> {
                    if (reasoningStartedAt == null) {
                        reasoningStartedAt = System.currentTimeMillis()
                    }
                    reasoningActive = true
                    reasoning.append(event.text)
                    emittedDeltas = true
                    trySend(ChatProgress.ReasoningDelta(event.text))
                }

                is StreamEvent.ReasoningEnded -> endReasoning(event.signature)

                is StreamEvent.TextDelta -> {
                    // The first visible token also closes the thinking phase
                    // for gateways that never emit an explicit end marker.
                    endReasoning()
                    answer.append(event.text)
                    emittedDeltas = true
                    trySend(ChatProgress.AssistantDelta(event.text))
                }

                is StreamEvent.ToolCallCompleted -> {
                    requested.add(event.call)
                    emittedDeltas = true
                }

                is StreamEvent.Failed -> failure = event.message
                StreamEvent.Completed -> Unit
            }
        }
        endReasoning()

        return AttemptOutcome(
            answer = answer.toString(),
            reasoning = reasoning.toString(),
            reasoningDurationMs = reasoningDurationMs,
            reasoningSignature = reasoningSignature,
            requested = requested,
            failure = failure,
            emittedDeltas = emittedDeltas
        )
    }

    /**
     * A bare 4xx/5xx on the very first frame of a request carrying `tools`
     * is almost always a schema rejection — some gateways close the body
     * before we can read the explanatory JSON. Retry with text tools anyway.
     */
    private fun isBareHttpRejection(message: String): Boolean =
        Regex("HTTP\\s+(400|404|405|415|422|501)", RegexOption.IGNORE_CASE)
            .containsMatchIn(message)

    /** Detects "this model does not support function calling" errors. */
    private fun isToolUnsupportedError(message: String): Boolean {
        val lower = message.lowercase()
        val negative = listOf(
            "not supported", "unsupported", "not support", "does not support",
            "doesn't support", "not available", "20037"
        ).any { lower.contains(it) }
        val toolRelated = listOf(
            "function call", "function_call", "tool call", "tool_call",
            "tool_use", "tools", "function"
        ).any { lower.contains(it) }
        return negative && toolRelated
    }

    /** Rebuilds a provider-neutral transcript from persisted messages. */
    private suspend fun loadTurns(conversationId: String): List<LlmTurn> {
        val result = mutableListOf<LlmTurn>()
        chatDao.getMessages(conversationId).forEach { entity ->
            val message = ChatMapper.messageToDomain(entity, gson)
            if (message.status == ChatMessageStatus.FAILED ||
                message.status == ChatMessageStatus.CANCELLED
            ) return@forEach

            when (message.role) {
                ChatRole.USER -> result.add(
                    LlmTurn(
                        role = ChatRole.USER,
                        content = message.content,
                        attachments = message.attachments
                    )
                )

                ChatRole.ASSISTANT -> {
                    result.add(
                        LlmTurn(
                            role = ChatRole.ASSISTANT,
                            content = message.content,
                            toolCalls = message.toolCalls.map {
                                PendingToolCall(it.id, it.name, it.argumentsJson)
                            },
                            reasoningContent = message.reasoningContent,
                            reasoningSignature = message.reasoningSignature
                        )
                    )
                    message.toolCalls.filter { it.resultJson != null }.forEach { tool ->
                        result.add(
                            LlmTurn(
                                role = ChatRole.TOOL,
                                toolCallId = tool.id,
                                toolName = tool.name,
                                toolResult = tool.resultJson
                            )
                        )
                    }
                }

                ChatRole.TOOL -> Unit
            }
        }
        return result
    }

    private suspend fun persist(message: ChatMessage) {
        chatDao.insertMessage(ChatMapper.messageToEntity(message, gson))
        chatDao.touchConversation(
            conversationId = message.conversationId,
            updatedAt = System.currentTimeMillis(),
            providerId = configManager.activeProviderId.value
        )
    }

    private data class AttemptOutcome(
        val answer: String,
        val reasoning: String,
        val reasoningDurationMs: Long,
        val reasoningSignature: String,
        val requested: List<PendingToolCall>,
        val failure: String?,
        val emittedDeltas: Boolean
    )

    private companion object {
        const val TAG = "ChatRepositoryImpl"
        const val MAX_TOOL_ROUNDS = 4
        // Reasoning models consume part of the output budget on chain of thought.
        const val MAX_OUTPUT_TOKENS = 4096
        // The on-device 8B model is slow and power hungry; keep replies short.
        const val ON_DEVICE_OUTPUT_TOKENS = 512
    }
}

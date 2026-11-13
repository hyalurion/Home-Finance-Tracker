package com.chronie.homemoney.ui.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chronie.homemoney.data.chat.AgentToolSpecs
import com.chronie.homemoney.data.chat.ChatAttachmentCodec
import com.chronie.homemoney.data.local.AiProviderConfigManager
import com.chronie.homemoney.domain.model.chat.AiProviderConfig
import com.chronie.homemoney.domain.model.chat.ChatAttachment
import com.chronie.homemoney.domain.model.chat.ChatConversation
import com.chronie.homemoney.domain.model.chat.ChatMessage
import com.chronie.homemoney.domain.model.chat.ChatMessageStatus
import com.chronie.homemoney.domain.model.chat.ChatProgress
import com.chronie.homemoney.domain.model.chat.ToolCallStatus
import com.chronie.homemoney.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** UI state for the streaming AI chat screen. */
data class AIChatUiState(
    val conversationId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val conversations: List<ChatConversation> = emptyList(),
    val providers: List<AiProviderConfig> = emptyList(),
    val activeProviderId: String = "",
    val activeProviderName: String = "",
    val input: String = "",
    val pendingAttachments: List<ChatAttachment> = emptyList(),
    val isGenerating: Boolean = false,
    val error: String? = null,
    val showHistory: Boolean = false,
    val showProviderPicker: Boolean = false,
    val queryAuthorized: Boolean = false,
    /** Incremented whenever the AI writes expense records, so the list can refresh. */
    val recordsChangedTick: Int = 0
)

/**
 * Bridges the Room-backed chat history with the live token stream.
 *
 * Room is only written when a turn finishes; while generating, a single
 * in-memory [streaming] message is overlaid on the database snapshot. This
 * avoids tens of encrypted DB writes per second during token streaming.
 */
@HiltViewModel
class AIChatViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: ChatRepository,
    private val configManager: AiProviderConfigManager,
    private val codec: ChatAttachmentCodec
) : ViewModel() {

    private val _uiState = MutableStateFlow(AIChatUiState())
    val uiState: StateFlow<AIChatUiState> = _uiState.asStateFlow()

    private var dbMessages: List<ChatMessage> = emptyList()
    private var streaming: ChatMessage? = null
    private var generationJob: Job? = null
    private var messagesJob: Job? = null

    init {
        // Resume the most recent conversation on launch; the history sheet
        // lets the user switch to any older session or start a new one.
        repository.observeConversations().onEach { conversations ->
            _uiState.update { it.copy(conversations = conversations) }
            if (_uiState.value.conversationId == null) {
                val latest = conversations.firstOrNull()?.id
                if (latest != null) openConversation(latest) else newConversation()
            }
        }.launchIn(viewModelScope)

        combine(configManager.activeProviderId, configManager.providers) { id, list ->
            val name = list.firstOrNull { it.id == id }?.name
                ?: configManager.builtInProvider.name
            Pair(name, list)
        }.onEach { (name, list) ->
            _uiState.update {
                it.copy(
                    activeProviderName = name,
                    providers = list,
                    activeProviderId = configManager.activeProviderId.value
                )
            }
        }.launchIn(viewModelScope)

        configManager.queryAuthorized.onEach { granted ->
            _uiState.update { it.copy(queryAuthorized = granted) }
        }.launchIn(viewModelScope)
    }

    // ------------------------------------------------------------------
    // Conversation management
    // ------------------------------------------------------------------

    fun newConversation() {
        viewModelScope.launch {
            val conversation = repository.createConversation(configManager.activeProviderId.value)
            openConversation(conversation.id)
            _uiState.update { it.copy(showHistory = false) }
        }
    }

    fun openConversation(id: String) {
        if (_uiState.value.conversationId == id && messagesJob?.isActive == true) return
        generationJob?.cancel()
        generationJob = null
        messagesJob?.cancel()
        streaming = null
        dbMessages = emptyList()
        _uiState.update {
            it.copy(conversationId = id, messages = emptyList(), error = null, isGenerating = false)
        }
        messagesJob = repository.observeMessages(id).onEach { rows ->
            dbMessages = rows
            recompute()
        }.launchIn(viewModelScope)
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            repository.deleteConversation(id)
            if (_uiState.value.conversationId == id) {
                _uiState.update { it.copy(conversationId = null) }
                newConversation()
            }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAllHistory()
            _uiState.update { it.copy(conversationId = null, showHistory = false) }
            newConversation()
        }
    }

    fun setShowHistory(show: Boolean) {
        _uiState.update { it.copy(showHistory = show) }
    }

    fun setShowProviderPicker(show: Boolean) {
        _uiState.update { it.copy(showProviderPicker = show) }
    }

    fun selectProvider(id: String) {
        configManager.setActiveProvider(id)
        _uiState.update { it.copy(showProviderPicker = false) }
    }

    // ------------------------------------------------------------------
    // Composer
    // ------------------------------------------------------------------

    fun updateInput(text: String) {
        _uiState.update { it.copy(input = text) }
    }

    fun attachFiles(uris: List<Uri>) {
        val copied = uris.mapNotNull(codec::copyToPrivate)
        if (copied.isEmpty()) {
            _uiState.update { it.copy(error = context.getString(com.chronie.homemoney.R.string.chat_attachment_failed)) }
            return
        }
        _uiState.update { it.copy(pendingAttachments = it.pendingAttachments + copied) }
    }

    fun removeAttachment(id: String) {
        val removed = _uiState.value.pendingAttachments.firstOrNull { it.id == id }
        _uiState.update {
            it.copy(pendingAttachments = it.pendingAttachments.filterNot { item -> item.id == id })
        }
        removed?.let { codec.deleteFiles(listOf(it)) }
    }

    fun sendMessage() {
        val state = _uiState.value
        val text = state.input.trim()
        if (state.isGenerating) return
        if (text.isBlank() && state.pendingAttachments.isEmpty()) return
        val conversationId = state.conversationId ?: return
        val attachments = state.pendingAttachments

        _uiState.update {
            it.copy(input = "", pendingAttachments = emptyList(), error = null, isGenerating = true)
        }
        collectGeneration(repository.sendMessage(conversationId, text, attachments))
    }

    fun confirmToolCall(messageId: String, toolCallId: String) {
        val conversationId = _uiState.value.conversationId ?: return
        if (_uiState.value.isGenerating) return
        _uiState.update { it.copy(isGenerating = true, error = null) }
        collectGeneration(repository.confirmToolCall(conversationId, messageId, toolCallId))
    }

    fun stopGeneration() {
        generationJob?.cancel()
        generationJob = null
        val partial = streaming
        streaming = null
        _uiState.update { it.copy(isGenerating = false) }
        recompute()
        if (partial != null) {
            viewModelScope.launch {
                repository.savePartialMessage(partial.copy(status = ChatMessageStatus.CANCELLED))
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // ------------------------------------------------------------------
    // Stream handling
    // ------------------------------------------------------------------

    private fun collectGeneration(flow: kotlinx.coroutines.flow.Flow<ChatProgress>) {
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            try {
                flow.collect { progress -> handleProgress(progress) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Expected when the user stops generation.
            } catch (e: Exception) {
                _uiState.update { it.copy(isGenerating = false, error = e.message) }
            } finally {
                if (_uiState.value.isGenerating) {
                    _uiState.update { it.copy(isGenerating = false) }
                }
            }
        }
    }

    private fun handleProgress(progress: ChatProgress) {
        when (progress) {
            is ChatProgress.AssistantStarted -> {
                streaming = progress.message
                recompute()
            }

            is ChatProgress.AssistantDelta -> {
                streaming = streaming?.let { it.copy(content = it.content + progress.text) }
                recompute()
            }

            is ChatProgress.ToolUpdated -> {
                streaming = streaming?.let { message ->
                    // Later tool rounds start from an empty in-memory bubble,
                    // so a card may need to be appended rather than replaced.
                    val known = message.toolCalls.any { it.id == progress.call.id }
                    message.copy(
                        toolCalls = if (known) {
                            message.toolCalls.map {
                                if (it.id == progress.call.id) progress.call else it
                            }
                        } else {
                            message.toolCalls + progress.call
                        }
                    )
                }
                recompute()
                if (progress.call.name == AgentToolSpecs.ADD_EXPENSE &&
                    progress.call.status == ToolCallStatus.SUCCESS
                ) {
                    _uiState.update { it.copy(recordsChangedTick = it.recordsChangedTick + 1) }
                }
            }

            ChatProgress.Finished -> {
                streaming = null
                _uiState.update { it.copy(isGenerating = false) }
                recompute()
            }

            is ChatProgress.Failed -> {
                streaming = null
                _uiState.update { it.copy(isGenerating = false, error = progress.message) }
                recompute()
            }
        }
    }

    private fun recompute() {
        val live = streaming
        val merged = if (live == null) {
            dbMessages
        } else {
            dbMessages.filterNot { it.id == live.id } + live
        }
        _uiState.update { it.copy(messages = merged.sortedBy { message -> message.createdAt }) }
    }
}

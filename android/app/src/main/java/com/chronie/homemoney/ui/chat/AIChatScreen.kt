package com.chronie.homemoney.ui.chat

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.chronie.homemoney.R
import com.chronie.homemoney.ui.components.CircularIconButton
import com.chronie.homemoney.ui.scroll.RegisterScrollToTop
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Full AI chat screen. Replaces the old one-shot "Smart Scan" form:
 * messages stream token-by-token, tool calls are auditable cards, files can
 * be attached, and sessions can be resumed from the history sheet.
 *
 * @param onRecordsChanged signals the expense list to refresh after the AI
 *   successfully writes records through the add_expense tool.
 */
@Composable
fun AIChatScreen(
    context: Context,
    onNavigateBack: () -> Unit,
    onRecordsChanged: () -> Unit,
    viewModel: AIChatViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    RegisterScrollToTop(listState)

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.attachFiles(uris)
    }

    // A successful add_expense tool call must refresh the ledger list.
    LaunchedEffect(state.recordsChangedTick) {
        if (state.recordsChangedTick > 0) onRecordsChanged()
    }

    // Scroll on new messages; during streaming only every ~80 characters to
    // avoid an animation per token.
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }
    LaunchedEffect(state.messages.lastOrNull()?.content?.length?.div(80)) {
        if (state.isGenerating && state.messages.isNotEmpty()) {
            listState.scrollToItem(state.messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.ai_chat_title),
                navigationIcon = {
                    CircularIconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                },
                color = MiuixTheme.colorScheme.surface
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
        ) {
            ChatProviderBar(
                providerName = state.activeProviderName,
                queryAuthorized = state.queryAuthorized,
                onProviderClick = { viewModel.setShowProviderPicker(true) },
                onNewChat = viewModel::newConversation,
                onHistory = { viewModel.setShowHistory(true) }
            )

            if (!state.queryAuthorized) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.chat_query_permission_hint),
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
            }

            state.error?.let { error ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = error,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = stringResource(R.string.chat_dismiss),
                        onClick = viewModel::clearError
                    )
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (state.messages.isEmpty()) {
                        item { ChatEmptyHint() }
                    }
                    items(state.messages, key = { it.id }) { message ->
                        ChatMessageItem(
                            message = message,
                            onConfirmTool = { toolCallId ->
                                viewModel.confirmToolCall(message.id, toolCallId)
                            }
                        )
                    }
                }
            }

            ChatComposer(
                input = state.input,
                onInputChange = viewModel::updateInput,
                attachments = state.pendingAttachments,
                onAttach = { filePicker.launch(arrayOf("*/*")) },
                onRemoveAttachment = viewModel::removeAttachment,
                isGenerating = state.isGenerating,
                onSend = viewModel::sendMessage,
                onStop = viewModel::stopGeneration
            )
        }
    }

    ChatHistorySheet(
        show = state.showHistory,
        conversations = state.conversations,
        activeId = state.conversationId,
        onSelect = viewModel::openConversation,
        onDelete = viewModel::deleteConversation,
        onNew = viewModel::newConversation,
        onClearAll = viewModel::clearAllHistory,
        onDismiss = { viewModel.setShowHistory(false) }
    )

    ChatProviderPickerSheet(
        show = state.showProviderPicker,
        providers = state.providers,
        activeId = state.activeProviderId,
        onSelect = viewModel::selectProvider,
        onDismiss = { viewModel.setShowProviderPicker(false) }
    )
}

package com.chronie.homemoney.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chronie.homemoney.R
import com.chronie.homemoney.domain.model.chat.AiProviderConfig
import com.chronie.homemoney.domain.model.chat.ChatConversation
import com.chronie.homemoney.ui.components.CircularIconButton
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowDialog
import java.text.DateFormat
import java.util.Date

/** Conversation switcher: resume, start fresh, delete, or clear everything. */
@Composable
fun ChatHistorySheet(
    show: Boolean,
    conversations: List<ChatConversation>,
    activeId: String?,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onNew: () -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    var confirmClearAll by remember { mutableStateOf(false) }

    WindowBottomSheet(
        show = show,
        title = stringResource(R.string.chat_history),
        startAction = {
            CircularIconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.cancel),
                    tint = MiuixTheme.colorScheme.onBackground
                )
            }
        },
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Button(onClick = { onNew(); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringResource(R.string.chat_new_conversation))
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (conversations.isEmpty()) {
                Text(
                    text = stringResource(R.string.chat_history_empty),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                    items(conversations, key = { it.id }) { conversation ->
                        HistoryRow(
                            conversation = conversation,
                            isActive = conversation.id == activeId,
                            onClick = { onSelect(conversation.id); onDismiss() },
                            onDelete = { pendingDeleteId = conversation.id }
                        )
                        HorizontalDivider()
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(
                    text = stringResource(R.string.chat_clear_history),
                    onClick = { confirmClearAll = true }
                )
            }
        }
    }

    WindowDialog(
        show = pendingDeleteId != null,
        title = stringResource(R.string.chat_delete_conversation_title),
        summary = stringResource(R.string.chat_delete_conversation_message),
        onDismissRequest = { pendingDeleteId = null }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularIconButton(onClick = { pendingDeleteId = null }) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.cancel),
                    tint = MiuixTheme.colorScheme.onBackground
                )
            }
            CircularIconButton(
                onClick = {
                    pendingDeleteId?.let(onDelete)
                    pendingDeleteId = null
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.confirm),
                    tint = MiuixTheme.colorScheme.error
                )
            }
        }
    }

    WindowDialog(
        show = confirmClearAll,
        title = stringResource(R.string.chat_clear_history),
        summary = stringResource(R.string.chat_delete_conversation_message),
        onDismissRequest = { confirmClearAll = false }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularIconButton(onClick = { confirmClearAll = false }) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.cancel),
                    tint = MiuixTheme.colorScheme.onBackground
                )
            }
            CircularIconButton(
                onClick = {
                    onClearAll()
                    confirmClearAll = false
                    onDismiss()
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.confirm),
                    tint = MiuixTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(
    conversation: ChatConversation,
    isActive: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = conversation.title.ifBlank { stringResource(R.string.chat_new_conversation) },
                style = MiuixTheme.textStyles.body2,
                color = if (isActive) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface
            )
            Text(
                text = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(Date(conversation.updatedAt)),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
        }
        if (isActive) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        CircularIconButton(onClick = onDelete, size = 32.dp) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.chat_delete_conversation),
                tint = MiuixTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/** Quick switch between the built-in engine and user-added API providers. */
@Composable
fun ChatProviderPickerSheet(
    show: Boolean,
    providers: List<AiProviderConfig>,
    activeId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    WindowBottomSheet(
        show = show,
        title = stringResource(R.string.chat_provider_picker_title),
        startAction = {
            CircularIconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.cancel),
                    tint = MiuixTheme.colorScheme.onBackground
                )
            }
        },
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            providers.forEach { provider ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            onSelect(provider.id)
                            onDismiss()
                        }
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = provider.name,
                            style = MiuixTheme.textStyles.body2
                        )
                        Text(
                            text = if (provider.isBuiltIn) {
                                stringResource(R.string.settings_ai_provider_builtin_desc)
                            } else {
                                "${provider.format.name} · ${provider.model}"
                            },
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                    }
                    if (provider.id == activeId) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                HorizontalDivider()
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.chat_provider_picker_hint),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
        }
    }
}

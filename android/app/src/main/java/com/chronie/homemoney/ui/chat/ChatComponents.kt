package com.chronie.homemoney.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.chronie.homemoney.R
import com.chronie.homemoney.domain.model.chat.ChatAttachment
import com.chronie.homemoney.domain.model.chat.ChatMessage
import com.chronie.homemoney.domain.model.chat.ChatMessageStatus
import com.chronie.homemoney.domain.model.chat.ChatRole
import com.chronie.homemoney.domain.model.chat.ToolCallRecord
import com.chronie.homemoney.domain.model.chat.ToolCallStatus
import com.chronie.homemoney.ui.components.ExpressiveLoadingIndicator
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowListPopup
import java.io.File
import java.text.DateFormat
import java.util.Date

/** Top strip: active model, new-chat and history actions. */
@Composable
fun ChatProviderBar(
    providerName: String,
    queryAuthorized: Boolean,
    onProviderClick: () -> Unit,
    onNewChat: () -> Unit,
    onHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val modelFallback = stringResource(R.string.chat_model_label)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MiuixTheme.colorScheme.primaryContainer,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onProviderClick)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = providerName.ifBlank { modelFallback },
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                if (!queryAuthorized) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(4.dp))
        IconButton(onClick = onNewChat) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(R.string.chat_new_conversation),
                tint = MiuixTheme.colorScheme.onBackground
            )
        }
        IconButton(onClick = onHistory) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = stringResource(R.string.chat_history),
                tint = MiuixTheme.colorScheme.onBackground
            )
        }
    }
}

/**
 * One chat bubble with attachments, Markdown answer, reasoning and tool cards.
 *
 * Copy/edit are exposed twice on purpose: a long press opens the anchored
 * context menu, and the small footer icons keep the actions discoverable on
 * devices where long press is hard to reach.
 */
@Composable
fun ChatMessageItem(
    message: ChatMessage,
    onConfirmTool: (toolCallId: String) -> Unit,
    onCopy: (ChatMessage) -> Unit,
    onEdit: (ChatMessage) -> Unit,
    isThinking: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == ChatRole.USER
    val streaming = message.status == ChatMessageStatus.STREAMING
    val bubbleTextColor = if (isUser) {
        MiuixTheme.colorScheme.onPrimaryContainer
    } else {
        MiuixTheme.colorScheme.onSurface
    }
    val subtleColor = MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.72f)

    var showMenu by rememberSaveable(message.id) { mutableStateOf(false) }
    val copyLabel = stringResource(R.string.chat_copy)
    val editLabel = stringResource(R.string.chat_edit)
    val menuItems = buildList<Pair<String, () -> Unit>> {
        add(copyLabel to { onCopy(message) })
        if (isUser) add(editLabel to { onEdit(message) })
    }

    // Scale with the screen instead of a hard-coded 320.dp, and keep a
    // comfortable reading measure on tablets / landscape.
    val maxBubbleWidth = (LocalConfiguration.current.screenWidthDp * 0.80f).dp
        .coerceAtMost(460.dp)
    val bubbleShape = if (isUser) {
        RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp)
    } else {
        RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Top
        ) {
            if (!isUser) {
                Surface(
                    shape = CircleShape,
                    color = MiuixTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .padding(top = 2.dp, end = 8.dp)
                        .size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .padding(5.dp)
                            .size(18.dp)
                    )
                }
            }

            Box {
                Surface(
                    shape = bubbleShape,
                    color = if (isUser) {
                        MiuixTheme.colorScheme.primaryContainer
                    } else {
                        MiuixTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier
                        .widthIn(max = maxBubbleWidth)
                        .pointerInput(Unit) {
                            detectTapGestures(onLongPress = { showMenu = true })
                        }
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                        if (message.attachments.isNotEmpty()) {
                            AttachmentRow(message.attachments, removable = false, onRemove = {})
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        if (!isUser && (message.reasoningContent.isNotBlank() || isThinking)) {
                            ReasoningBlock(
                                messageId = message.id,
                                content = message.reasoningContent,
                                durationMs = message.reasoningDurationMs,
                                isThinking = isThinking,
                                textColor = bubbleTextColor
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        if (message.content.isNotBlank()) {
                            // No fillMaxWidth here: the bubble should hug short
                            // answers instead of stretching to the max width.
                            ChatMarkdownText(
                                content = message.content,
                                isStreaming = streaming,
                                textColor = bubbleTextColor
                            )
                        } else if (streaming && message.reasoningContent.isBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ExpressiveLoadingIndicator(containerVisible = false)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.chat_streaming),
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                                )
                            }
                        }

                        message.toolCalls.forEach { call ->
                            Spacer(modifier = Modifier.height(8.dp))
                            ToolCallCard(record = call, onConfirm = { onConfirmTool(call.id) })
                        }

                        if (message.status == ChatMessageStatus.FAILED) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.chat_message_failed),
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.error
                            )
                        }
                    }
                }

                WindowListPopup(
                    show = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    val dismiss = LocalDismissState.current
                    ListPopupColumn {
                        menuItems.forEachIndexed { index, (label, action) ->
                            DropdownImpl(
                                text = label,
                                optionSize = menuItems.size,
                                isSelected = false,
                                index = index,
                                onSelectedIndexChange = {
                                    action()
                                    showMenu = false
                                    dismiss?.invoke()
                                }
                            )
                        }
                    }
                }
            }
        }

        // Meta row: timestamp, model name and quick actions. Kept outside the
        // bubble so long Markdown answers stay visually clean.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = if (isUser) 0.dp else 36.dp,
                    end = if (isUser) 4.dp else 0.dp
                ),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val meta = buildString {
                append(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.createdAt)))
                if (!isUser && !message.providerLabel.isNullOrBlank()) {
                    append(" · ")
                    append(message.providerLabel)
                }
            }
            Text(
                text = meta,
                style = MiuixTheme.textStyles.footnote2,
                color = subtleColor,
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = { onCopy(message) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = copyLabel,
                    tint = subtleColor,
                    modifier = Modifier.size(13.dp)
                )
            }
            if (isUser) {
                IconButton(
                    onClick = { onEdit(message) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = editLabel,
                        tint = subtleColor,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

/**
 * Chain-of-thought panel. A thin vertical rule on the left and a dimmed body
 * visually separate reasoning from the authoritative answer without hiding it.
 * Expanded by default; the header collapses it once generation has finished.
 */
@Composable
private fun ReasoningBlock(
    messageId: String,
    content: String,
    durationMs: Long,
    isThinking: Boolean,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable(messageId) { mutableStateOf(true) }
    // Deliberately lighter than the answer so the two streams read differently
    // in both light and dark themes.
    val reasoningColor = textColor.copy(alpha = 0.62f)
    val barColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.45f)
    val showContent = expanded || isThinking
    val label = if (isThinking) {
        stringResource(R.string.chat_reasoning_thinking)
    } else {
        stringResource(
            R.string.chat_reasoning_done,
            (durationMs / 1000L).coerceAtLeast(1L).toString()
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = reasoningColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MiuixTheme.textStyles.footnote1,
                color = reasoningColor,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (showContent) {
                    Icons.Default.ExpandLess
                } else {
                    Icons.Default.ExpandMore
                },
                contentDescription = stringResource(
                    if (showContent) {
                        R.string.chat_reasoning_hide
                    } else {
                        R.string.chat_reasoning_show
                    }
                ),
                tint = reasoningColor,
                modifier = Modifier.size(16.dp)
            )
        }

        if (showContent && content.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(barColor)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (isThinking) content + " ▍" else content.trim(),
                    style = MiuixTheme.textStyles.body2,
                    color = reasoningColor,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** Compact attachment previews; used in bubbles and in the composer. */
@Composable
fun AttachmentRow(
    attachments: List<ChatAttachment>,
    removable: Boolean,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(attachments, key = { it.id }) { attachment ->
            Box(modifier = Modifier.size(72.dp)) {
                if (attachment.isImage) {
                    AsyncImage(
                        model = File(attachment.path),
                        contentDescription = attachment.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MiuixTheme.colorScheme.surface)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MiuixTheme.colorScheme.surface)
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = attachment.displayName.take(10),
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                    }
                }
                if (removable) {
                    IconButton(
                        onClick = { onRemove(attachment.id) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.chat_remove_attachment),
                            tint = MiuixTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Auditable tool invocation card, with an approve button for held writes. */
@Composable
fun ToolCallCard(
    record: ToolCallRecord,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val statusColor = when (record.status) {
        ToolCallStatus.SUCCESS -> MiuixTheme.colorScheme.primary
        ToolCallStatus.FAILED, ToolCallStatus.DENIED -> MiuixTheme.colorScheme.error
        else -> MiuixTheme.colorScheme.onSurfaceSecondary
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MiuixTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = toolName(record.name),
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                if (record.status == ToolCallStatus.RUNNING) {
                    Text(
                        text = "…",
                        style = MiuixTheme.textStyles.footnote2,
                        color = statusColor
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = toolStatus(record.status),
                    style = MiuixTheme.textStyles.footnote2,
                    color = statusColor
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.chat_tool_arguments),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )
                Text(
                    text = record.argumentsJson.take(600),
                    style = MiuixTheme.textStyles.footnote2
                )
                record.resultJson?.let { result ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.chat_tool_result),
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                    Text(
                        text = result.take(600),
                        style = MiuixTheme.textStyles.footnote2
                    )
                }
            }

            if (record.status == ToolCallStatus.PENDING_CONFIRM) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.chat_tool_confirm))
                }
            }
        }
    }
}

/** Rounded input card with attachment picker and a circular send/stop control. */
@Composable
fun ChatComposer(
    input: String,
    onInputChange: (String) -> Unit,
    attachments: List<ChatAttachment>,
    onAttach: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    isGenerating: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MiuixTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (attachments.isNotEmpty()) {
                AttachmentRow(attachments, removable = true, onRemove = onRemoveAttachment)
                Spacer(modifier = Modifier.height(8.dp))
            }
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MiuixTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(start = 2.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    IconButton(onClick = onAttach) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = stringResource(R.string.chat_attach),
                            tint = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                    }
                    TextField(
                        value = input,
                        onValueChange = onInputChange,
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.chat_input_hint),
                        useLabelAsPlaceholder = true,
                        singleLine = false,
                        maxLines = 5
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = CircleShape,
                        color = if (isGenerating) {
                            MiuixTheme.colorScheme.error
                        } else {
                            MiuixTheme.colorScheme.primary
                        },
                        modifier = Modifier
                            .padding(bottom = 6.dp)
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable(onClick = if (isGenerating) onStop else onSend)
                    ) {
                        Icon(
                            imageVector = if (isGenerating) {
                                Icons.Default.Stop
                            } else {
                                Icons.AutoMirrored.Filled.Send
                            },
                            contentDescription = stringResource(
                                if (isGenerating) R.string.chat_stop else R.string.chat_send
                            ),
                            tint = MiuixTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .padding(10.dp)
                                .size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Shown when a conversation has no messages yet. */
@Composable
fun ChatEmptyHint(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.chat_empty_hint),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceSecondary
        )
    }
}

@Composable
private fun toolName(name: String): String = stringResource(
    when (name) {
        "add_expense" -> R.string.chat_tool_add_expense
        "query_expenses" -> R.string.chat_tool_query_expenses
        "get_spending_summary" -> R.string.chat_tool_spending_summary
        "get_current_date" -> R.string.chat_tool_current_date
        else -> R.string.chat_tool_unknown
    }
)

@Composable
private fun toolStatus(status: ToolCallStatus): String = stringResource(
    when (status) {
        ToolCallStatus.RUNNING -> R.string.chat_tool_status_running
        ToolCallStatus.PENDING_CONFIRM -> R.string.chat_tool_status_pending
        ToolCallStatus.SUCCESS -> R.string.chat_tool_status_success
        ToolCallStatus.FAILED -> R.string.chat_tool_status_failed
        ToolCallStatus.DENIED -> R.string.chat_tool_status_denied
    }
)

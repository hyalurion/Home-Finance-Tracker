package com.chronie.homemoney.ui.recyclebin

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.chronie.homemoney.R
import com.chronie.homemoney.core.common.rememberCurrencySymbol
import com.chronie.homemoney.domain.model.Expense
import com.chronie.homemoney.ui.components.CircularIconButton
import com.chronie.homemoney.ui.expense.formatDateByLocale
import com.chronie.homemoney.ui.scroll.RegisterScrollToTop
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** Days a soft-deleted expense survives in the bin before the worker purges it. */
private const val PURGE_AGE_DAYS = 30L

/**
 * Recycle Bin screen content.
 *
 * Renders soft-deleted expenses grouped by deletion date, with support for
 * multi-select operations, restore-all, and delete-all via the top-right
 * dropdown menu. Each row shows the deletion date, days remaining before
 * auto-purge, and offers single-item restore/delete.
 */
@Composable
fun RecycleBinScreen(
    context: Context,
    viewModel: RecycleBinViewModel = hiltViewModel(),
    paddingValues: PaddingValues = PaddingValues()
) {
    val deletedExpenses by viewModel.deletedExpenses.collectAsState(initial = emptyList())
    val selectedIds by viewModel.selectedIds.collectAsState()
    val isSelectMode by viewModel.isSelectMode.collectAsState()
    val pendingBatchAction by viewModel.pendingBatchAction.collectAsState()
    var pendingPermanentDelete by remember { mutableStateOf<Expense?>(null) }

    val locale = remember(context) {
        context.resources.configuration.locales[0].toLanguageTag()
    }

    if (deletedExpenses.isEmpty()) {
        EmptyRecycleBin(context = context, paddingValues = paddingValues)
    } else {
        val listState = rememberLazyListState()
        RegisterScrollToTop(listState)

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = paddingValues.calculateTopPadding(),
                end = 16.dp,
                bottom = 24.dp
            )
        ) {
            // Selection bar (animated slide-in/out)
            item(key = "selection_bar") {
                AnimatedVisibility(
                    visible = isSelectMode,
                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
                ) {
                    SelectionBar(
                        context = context,
                        selectedCount = selectedIds.size,
                        totalCount = deletedExpenses.size,
                        onExitSelectMode = { viewModel.exitSelectMode() },
                        onSelectAll = { viewModel.selectAll(deletedExpenses.map { it.id }) },
                        onClearSelection = { viewModel.clearSelection() }
                    )
                }
            }

            // Group by deletion date (YYYY-MM-DD)
            deletedExpenses
                .groupBy { expense -> deletedAtToDateString(expense.deletedAt, locale) }
                .forEach { (dateLabel, items) ->
                    item(key = "date_$dateLabel") {
                        DateHeader(dateLabel = dateLabel, count = items.size)
                    }
                    items(
                        items = items,
                        key = { it.id },
                        contentType = { "expense" }
                    ) { expense ->
                        RecycleBinItem(
                            context = context,
                            expense = expense,
                            isSelected = expense.id in selectedIds,
                            isSelectMode = isSelectMode,
                            onToggleSelect = { viewModel.toggleSelection(expense.id) },
                            onLongPress = {
                                if (!isSelectMode) {
                                    viewModel.enterSelectMode()
                                    viewModel.toggleSelection(expense.id)
                                }
                            },
                            onRestore = { viewModel.restoreExpense(expense.id) },
                            onDelete = { pendingPermanentDelete = expense }
                        )
                    }
                }
        }
    }

    // Single-item permanent delete confirmation
    pendingPermanentDelete?.let { expense ->
        PermanentDeleteDialog(
            context = context,
            expense = expense,
            onConfirm = {
                viewModel.permanentDeleteExpense(expense.id)
                pendingPermanentDelete = null
            },
            onDismiss = { pendingPermanentDelete = null }
        )
    }

    // Batch action confirmation dialogs
    pendingBatchAction?.let { action ->
        BatchConfirmDialog(
            context = context,
            action = action,
            count = when (action) {
                BatchAction.RESTORE_SELECTED, BatchAction.DELETE_SELECTED -> selectedIds.size
                else -> deletedExpenses.size
            },
            onConfirm = {
                when (action) {
                    BatchAction.RESTORE_ALL -> viewModel.restoreAll()
                    BatchAction.DELETE_ALL -> viewModel.permanentDeleteAll()
                    BatchAction.RESTORE_SELECTED -> viewModel.restoreSelected()
                    BatchAction.DELETE_SELECTED -> viewModel.permanentDeleteSelected()
                }
                viewModel.dismissBatchAction()
            },
            onDismiss = { viewModel.dismissBatchAction() }
        )
    }
}

@Composable
private fun SelectionBar(
    context: Context,
    selectedCount: Int,
    totalCount: Int,
    onExitSelectMode: () -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        color = MiuixTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                text = context.getString(R.string.cancel),
                onClick = onExitSelectMode
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = context.getString(R.string.recycle_bin_selected_count, selectedCount),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(
                text = if (selectedCount == totalCount) {
                    context.getString(R.string.recycle_bin_deselect_all)
                } else {
                    context.getString(R.string.recycle_bin_select_all)
                },
                onClick = {
                    if (selectedCount == totalCount) onClearSelection()
                    else onSelectAll()
                }
            )
        }
    }
}

@Composable
private fun DateHeader(dateLabel: String, count: Int) {
    Text(
        text = "$dateLabel · $count",
        style = MiuixTheme.textStyles.body2,
        fontWeight = FontWeight.Bold,
        color = MiuixTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecycleBinItem(
    context: Context,
    expense: Expense,
    isSelected: Boolean,
    isSelectMode: Boolean,
    onToggleSelect: () -> Unit,
    onLongPress: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    val daysLeft = remember(expense.deletedAt) { daysUntilPurge(expense.deletedAt) }
    val remark = expense.remark
    val deletedTimeLabel = remember(expense.deletedAt) { deletedAtToTimeString(expense.deletedAt) }
    val currencySymbol = rememberCurrencySymbol(context)

    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) {
            MiuixTheme.colorScheme.primary.copy(alpha = 0.08f)
        } else {
            MiuixTheme.colorScheme.surface
        },
        animationSpec = tween(durationMillis = 250),
        label = "bgColor"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .combinedClickable(
                onClick = {
                    if (isSelectMode) onToggleSelect()
                },
                onLongClick = onLongPress
            ),
        color = backgroundColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(end = 16.dp, top = 12.dp, bottom = 12.dp)
                .animateContentSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox with animated slide-in/out
            AnimatedVisibility(
                visible = isSelectMode,
                enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
                exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut()
            ) {
                Row {
                    Spacer(modifier = Modifier.width(12.dp))
                    Checkbox(
                        state = if (isSelected) ToggleableState.On else ToggleableState.Off,
                        onClick = { onToggleSelect() }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }

            if (!isSelectMode) {
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "-" + context.getString(
                            R.string.currency_format,
                            currencySymbol,
                            expense.amount
                        ),
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    // Action buttons animated fade-out in select mode
                    AnimatedVisibility(
                        visible = !isSelectMode,
                        enter = fadeIn(animationSpec = tween(200)),
                        exit = fadeOut(animationSpec = tween(150))
                    ) {
                        Row {
                            CircularIconButton(onClick = onRestore) {
                                Icon(
                                    Icons.Default.Restore,
                                    contentDescription = context.getString(R.string.recycle_bin_restore),
                                    tint = MiuixTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            CircularIconButton(onClick = onDelete) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = context.getString(R.string.delete),
                                    tint = MiuixTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (remark.isNullOrBlank()) {
                        expense.date
                    } else {
                        "${expense.date} · $remark"
                    },
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )

                Spacer(modifier = Modifier.height(4.dp))

                val purgeText = when (daysLeft) {
                    null -> context.getString(R.string.recycle_bin_deleted) + " $deletedTimeLabel"
                    else -> context.getString(R.string.recycle_bin_expires_in, daysLeft)
                }
                Text(
                    text = purgeText,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )
            }
        }
    }
}

@Composable
private fun EmptyRecycleBin(context: Context, paddingValues: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.RestoreFromTrash,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MiuixTheme.colorScheme.onSurfaceSecondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = context.getString(R.string.recycle_bin_empty),
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
        }
    }
}

@Composable
private fun PermanentDeleteDialog(
    context: Context,
    expense: Expense,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    WindowDialog(
        show = true,
        title = context.getString(R.string.recycle_bin_permanent_delete_title),
        onDismissRequest = onDismiss
    ) {
        Column {
            Text(
                text = context.getString(R.string.recycle_bin_permanent_delete_message),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    text = context.getString(R.string.cancel),
                    onClick = onDismiss
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    text = context.getString(R.string.delete),
                    onClick = onConfirm
                )
            }
        }
    }
}

@Composable
private fun BatchConfirmDialog(
    context: Context,
    action: BatchAction,
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val (title, message) = when (action) {
        BatchAction.RESTORE_ALL -> {
            context.getString(R.string.recycle_bin_restore_all_title) to
                    context.getString(R.string.recycle_bin_restore_all_message, count)
        }
        BatchAction.DELETE_ALL -> {
            context.getString(R.string.recycle_bin_delete_all_title) to
                    context.getString(R.string.recycle_bin_delete_all_message, count)
        }
        BatchAction.RESTORE_SELECTED -> {
            context.getString(R.string.recycle_bin_restore_selected_title) to
                    context.getString(R.string.recycle_bin_restore_selected_message, count)
        }
        BatchAction.DELETE_SELECTED -> {
            context.getString(R.string.recycle_bin_delete_selected_title) to
                    context.getString(R.string.recycle_bin_delete_selected_message, count)
        }
    }

    WindowDialog(
        show = true,
        title = title,
        onDismissRequest = onDismiss
    ) {
        Column {
            Text(
                text = message,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    text = context.getString(R.string.cancel),
                    onClick = onDismiss
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    text = context.getString(R.string.confirm),
                    onClick = onConfirm
                )
            }
        }
    }
}

/**
 * Days remaining before the 30-day auto-purge deletes [deletedAt].
 * Returns null when the deletion timestamp is missing.
 */
private fun daysUntilPurge(deletedAt: Long?): Int? {
    if (deletedAt == null) return null
    val deadline = deletedAt + TimeUnit.DAYS.toMillis(PURGE_AGE_DAYS)
    val remaining = deadline - System.currentTimeMillis()
    return maxOf(0, (remaining / TimeUnit.DAYS.toMillis(1)).toInt())
}

/** Converts epoch millis to a localized date string using the project's DateFormatter. */
private fun deletedAtToDateString(deletedAt: Long?, locale: String): String {
    if (deletedAt == null) return "Unknown"
    return try {
        val dateString = Instant.ofEpochMilli(deletedAt)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(DateTimeFormatter.ISO_LOCAL_DATE)
        formatDateByLocale(dateString, locale)
    } catch (_: Exception) {
        "Unknown"
    }
}

/** Converts epoch millis to a "HH:mm" time string. */
private fun deletedAtToTimeString(deletedAt: Long?): String {
    if (deletedAt == null) return ""
    return try {
        val localTime = Instant.ofEpochMilli(deletedAt)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
        localTime.format(DateTimeFormatter.ofPattern("HH:mm"))
    } catch (_: Exception) {
        ""
    }
}
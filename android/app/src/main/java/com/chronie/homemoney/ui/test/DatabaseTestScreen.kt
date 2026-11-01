package com.chronie.homemoney.ui.test

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.chronie.homemoney.R
import com.chronie.homemoney.core.common.rememberCurrencySymbol
import com.chronie.homemoney.data.local.entity.ExpenseEntity
import com.chronie.homemoney.ui.components.CircularIconButton
import com.chronie.homemoney.ui.scroll.RegisterScrollToTop
import com.chronie.homemoney.ui.expense.formatDateByLocale
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.menu.WindowIconDropdownMenu
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * A diagnostic screen that lists all expense records stored in the local database.
 * Displays summary statistics (count and total amount) and provides actions for
 * adding test data or clearing the entire dataset.
 */
@Composable
fun DatabaseTestScreen(
    context: android.content.Context,
    onNavigateBack: () -> Unit,
    viewModel: DatabaseTestViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = context.getString(R.string.database_test),
                largeTitle = context.getString(R.string.database_test),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    CircularIconButton(onClick = onNavigateBack, modifier = Modifier.padding(start = 8.dp, end = 4.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = context.getString(R.string.back))
                    }
                },
                actions = {
                    val moreMenuEntries = listOf(
                        DropdownEntry(
                            items = listOf(
                                DropdownItem(
                                    text = context.getString(R.string.add_test_data),
                                    icon = { modifier ->
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            modifier = modifier
                                        )
                                    },
                                    onClick = { viewModel.addTestExpense() }
                                )
                            )
                        ),
                        DropdownEntry(
                            items = listOf(
                                DropdownItem(
                                    text = "",
                                    icon = { modifier ->
                                        Row(
                                            modifier = modifier,
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = null,
                                                tint = MiuixTheme.colorScheme.error
                                            )
                                            Text(
                                                text = context.getString(R.string.clear_data),
                                                color = MiuixTheme.colorScheme.error
                                            )
                                        }
                                    },
                                    onClick = { viewModel.clearAllExpenses() }
                                )
                            )
                        )
                    )
                    WindowIconDropdownMenu(
                        entries = moreMenuEntries,
                        dropdownColors = DropdownDefaults.dropdownColors(
                            contentColor = MiuixTheme.colorScheme.onSurface,
                            summaryColor = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = context.getString(R.string.common_more_functions))
                    }
                }
            )
        }
    ) { paddingValues ->
        val listState = rememberLazyListState()
        RegisterScrollToTop(listState)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(top = paddingValues.calculateTopPadding() + 16.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = context.getString(R.string.database_statistics),
                            style = MiuixTheme.textStyles.body1
                        )
                        Text(context.getString(R.string.record_count, uiState.expenseCount))
                        Text(context.getString(R.string.total_amount_database, uiState.totalAmount))
                        Text(
                            text = context.getString(R.string.recycle_bin_deleted) + "：" + uiState.deletedCount,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.error
                        )
                    }
                }
            }
            
            if (!uiState.message.isNullOrEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.defaultColors(
                            color = if (uiState.isError) {
                                MiuixTheme.colorScheme.errorContainer
                            } else {
                                MiuixTheme.colorScheme.primaryContainer
                            }
                        )
                    ) {
                        Text(
                            text = uiState.message ?: "",
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
            
            item {
                Text(
                    text = context.getString(R.string.expense_records),
                    style = MiuixTheme.textStyles.body1
                )
            }
            
            if (uiState.expenses.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(context.getString(R.string.no_data))
                    }
                }
            } else {
                items(uiState.expenses) { expense ->
                    ExpenseItem(
                        context = context,
                        expense = expense.toUiModel()
                    )
                }
            }
        }
    }
}

/**
 * Renders a single expense record as a [Card] showing its type, amount, remark,
 * date, and sync status.
 */
@Composable
fun ExpenseItem(
    context: android.content.Context,
    expense: ExpenseItemUiModel
) {
    val isDeleted = expense.deletedAt != null
    val currencySymbol = rememberCurrencySymbol(context)
    Card(
        modifier = Modifier.fillMaxWidth().alpha(if (isDeleted) 0.55f else 1f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = expense.type,
                        style = MiuixTheme.textStyles.body1
                    )
                    if (isDeleted) {
                        Text(
                            text = context.getString(R.string.recycle_bin_deleted),
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.error
                        )
                    }
                }
                Text(
                        text = context.getString(R.string.currency_format, currencySymbol, expense.amount),
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.error
                    )
            }
            
            if (expense.remark.isNotEmpty()) {
                Text(
                    text = expense.remark,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )
            }
            
            Text(
                text = formatDateByLocale(expense.timeFormatted, context.resources.configuration.locales[0].toLanguageTag()),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = context.getString(if (expense.isSynced) R.string.synced else R.string.not_synced),
                    style = MiuixTheme.textStyles.footnote2,
                    color = if (expense.isSynced) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.error
                    }
                )
                
                Text(
                    text = "ID: ${expense.id}",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )
            }
        }
    }
}

private fun ExpenseEntity.toUiModel(): ExpenseItemUiModel {
    return ExpenseItemUiModel(
        id = id,
        type = type,
        remark = remark ?: "",
        amount = amount,
        timeFormatted = date,
        isSynced = isSynced,
        deletedAt = deletedAt
    )
}

/**
 * Lightweight UI model for the expense list item, decoupled from the Room entity.
 */
data class ExpenseItemUiModel(
    val id: String,
    val type: String,
    val remark: String,
    val amount: Double,
    val timeFormatted: String,
    val isSynced: Boolean,
    val deletedAt: Long? = null
)

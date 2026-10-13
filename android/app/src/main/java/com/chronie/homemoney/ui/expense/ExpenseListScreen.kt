package com.chronie.homemoney.ui.expense

import android.annotation.SuppressLint
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FilterListOff
import androidx.compose.material.icons.filled.Translate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.chronie.homemoney.R
import com.chronie.homemoney.domain.model.Expense
import com.chronie.homemoney.domain.model.SortOption
import com.chronie.homemoney.ui.budget.BudgetCard
import com.chronie.homemoney.ui.components.CircularIconButton
import com.chronie.homemoney.ui.components.ExpressiveLoadingIndicator
import com.chronie.homemoney.ui.scroll.RegisterScrollToTop
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.menu.WindowIconCascadingDropdownMenu
import top.yukonga.miuix.kmp.menu.WindowIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowDialog
import kotlin.time.Duration.Companion.milliseconds

/**
 * Expense List Screen
 */
@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun ExpenseListScreen(
    context: android.content.Context,
    viewModel: ExpenseListViewModel = hiltViewModel(),
    shouldRefresh: Boolean = false,
    onRefreshHandled: () -> Unit = {},
    onNavigateToAddExpense: () -> Unit = {},
    onNavigateToAIExpense: () -> Unit = {},
    onNavigateToEditExpense: (expenseId: String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val isAiModelDownloaded by viewModel.isModelDownloaded.collectAsState()
    val remarkTranslations by viewModel.remarkTranslations.collectAsState()
    var showFilterDialog by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var budgetRefreshTrigger by remember { mutableIntStateOf(0) }

    // Use screen width to decide table layout
    val configuration = LocalConfiguration.current
    val useTableLayout = configuration.screenWidthDp.dp >= 600.dp

    // Handle refresh requests from parent scope
    LaunchedEffect(shouldRefresh) {
        if (shouldRefresh) {
            viewModel.refresh()
            budgetRefreshTrigger++
            onRefreshHandled()
        }
    }

    // Refresh function
    val onRefresh: () -> Unit = {
        isRefreshing = true
        viewModel.refresh()
        budgetRefreshTrigger++
    }

    // Handle refresh state reset after refresh
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            delay(1000.milliseconds)
            isRefreshing = false
        }
    }
    
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = context.getString(R.string.expense_list_title),
                largeTitle = context.getString(R.string.expense_list_title),
                scrollBehavior = scrollBehavior,
                actions = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val addMenuEntries = listOf(
                            DropdownEntry(
                                items = listOf(
                                    DropdownItem(
                                        text = context.getString(R.string.add_expense_title),
                                        icon = { modifier ->
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = null,
                                                modifier = modifier
                                            )
                                        },
                                        onClick = onNavigateToAddExpense
                                    ),
                                    DropdownItem(
                                        text = context.getString(R.string.ai_expense_title),
                                        icon = { modifier ->
                                            Icon(
                                                imageVector = Icons.Default.AutoAwesome,
                                                contentDescription = null,
                                                modifier = modifier
                                            )
                                        },
                                        onClick = onNavigateToAIExpense
                                    )
                                )
                            )
                        )

                        WindowIconDropdownMenu(entries = addMenuEntries) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = context.getString(R.string.add_expense_title)
                            )
                        }

                        val filterMenuEntry = DropdownEntry(
                            items = listOf(
                                DropdownItem(
                                    text = context.getString(R.string.expense_list_sort),
                                    children = listOf(
                                        DropdownItem(
                                            text = context.getString(R.string.expense_list_sort_date_desc),
                                            onClick = { viewModel.updateSortOption(SortOption.DATE_DESC) }
                                        ),
                                        DropdownItem(
                                            text = context.getString(R.string.expense_list_sort_date_asc),
                                            onClick = { viewModel.updateSortOption(SortOption.DATE_ASC) }
                                        ),
                                        DropdownItem(
                                            text = context.getString(R.string.expense_list_sort_amount_desc),
                                            onClick = { viewModel.updateSortOption(SortOption.AMOUNT_DESC) }
                                        ),
                                        DropdownItem(
                                            text = context.getString(R.string.expense_list_sort_amount_asc),
                                            onClick = { viewModel.updateSortOption(SortOption.AMOUNT_ASC) }
                                        )
                                    )
                                ),
                                DropdownItem(
                                    text = context.getString(R.string.common_filter),
                                    icon = { modifier ->
                                        Icon(
                                            imageVector = Icons.Default.FilterList,
                                            contentDescription = null,
                                            modifier = modifier
                                        )
                                    },
                                    onClick = { showFilterDialog = true }
                                ),
                                DropdownItem(
                                    text = context.getString(R.string.expense_list_clear_filters),
                                    icon = { modifier ->
                                        Icon(
                                            imageVector = Icons.Default.FilterListOff,
                                            contentDescription = null,
                                            modifier = modifier
                                        )
                                    },
                                    onClick = { viewModel.resetFilters() }
                                )
                            )
                        )

                        WindowIconCascadingDropdownMenu(entry = filterMenuEntry) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = context.getString(R.string.common_filter)
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        // Content Area - Below toolbar, can scroll
        Column(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoading && uiState.expenses.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        ExpressiveLoadingIndicator(containerVisible = true)
                    }
                }
                uiState.error != null && uiState.expenses.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = uiState.error ?: context.getString(R.string.common_error),
                                style = MiuixTheme.textStyles.body1
                            )
                            Button(onClick = { viewModel.refresh() }, colors = ButtonDefaults.buttonColorsPrimary()) {
                                Text(context.getString(R.string.common_retry))
                            }
                        }
                    }
                }
                uiState.expenses.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = context.getString(R.string.expense_list_empty),
                                style = MiuixTheme.textStyles.body1
                            )
                            Text(
                                text = context.getString(R.string.expense_list_empty_description),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                        }
                    }
                }
                else -> {
                    val listState = rememberLazyListState()
                    RegisterScrollToTop(listState)
                    val groupedExpenses = uiState.groupedExpenses
                    var lastLoadTime by remember { mutableLongStateOf(0L) }
                    
                    // Improved scroll detection: only load more when actually near bottom
                    LaunchedEffect(listState, uiState.hasMore, uiState.isLoading) {
                        snapshotFlow { 
                            val layoutInfo = listState.layoutInfo
                            val totalItemsCount = layoutInfo.totalItemsCount
                            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index
                            Triple(totalItemsCount, lastVisibleIndex, layoutInfo.visibleItemsInfo.firstOrNull()?.index)
                        }.collect { (totalItemsCount, lastVisibleIndex, firstVisibleIndex) ->
                            val currentTime = System.currentTimeMillis()
                            if (totalItemsCount > 0 && 
                                lastVisibleIndex != null && 
                                firstVisibleIndex != null &&
                                lastVisibleIndex >= totalItemsCount - 1 && // Only trigger when last item is visible
                                uiState.hasMore && 
                                !uiState.isLoading &&
                                currentTime - lastLoadTime > 1000) { // Debounce, at least 1 second interval
                                lastLoadTime = currentTime
                                viewModel.loadMore()
                            }
                        }
                    }
                    
                    PullToRefresh(
                        isRefreshing = isRefreshing,
                        onRefresh = onRefresh,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(scrollBehavior.nestedScrollConnection),
                            contentPadding = PaddingValues(top = paddingValues.calculateTopPadding(), bottom = 80.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Budget Management Card
                            item(key = "budget_card") {
                                BudgetCard(
                                    context = context,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    refreshTrigger = budgetRefreshTrigger
                                )
                            }
                            
                            // Expense Statistics Card
                            item(key = "stats_card") {
                                ExpenseStatisticsCard(
                                    statistics = uiState.statistics,
                                    context = context,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    useSingleRow = useTableLayout
                                )
                            }
                            
                            // Expense List Items
                            groupedExpenses.forEach { (date, expenses) ->
                                // Date Header
                                item(key = "header_$date") {
                                    if (useTableLayout) {
                                        ExpenseTableDateHeader(
                                            date = date,
                                            count = expenses.size,
                                            totalAmount = expenses.sumOf { it.amount },
                                            context = context,
                                            locale = context.resources.configuration.locales[0].toLanguageTag(),
                                            modifier = Modifier.padding(horizontal = 16.dp)
                                        )
                                    } else {
                                        ExpenseDateHeader(
                                            date = date,
                                            count = expenses.size,
                                            totalAmount = expenses.sumOf { it.amount },
                                            context = context,
                                            locale = context.resources.configuration.locales[0].toLanguageTag(),
                                            modifier = Modifier.padding(horizontal = 16.dp)
                                        )
                                    }
                                }
                                
                                // Expense Items for this Date
                                if (useTableLayout) {
                                    item(key = "table_$date") {
                                        ExpenseTableItems(
                                            expenses = expenses,
                                            context = context,
                                            onEdit = { expense -> onNavigateToEditExpense(expense.id) },
                                            onDelete = { expense -> viewModel.deleteExpense(expense) },
                                            isAiModelDownloaded = isAiModelDownloaded,
                                            remarkTranslations = remarkTranslations,
                                            onTranslateRemark = { expense -> viewModel.translateRemark(expense) },
                                            modifier = Modifier.padding(horizontal = 16.dp)
                                        )
                                    }
                                } else {
                                    items(
                                        items = expenses,
                                        key = { expense -> "expense_${expense.id}" }
                                    ) { expense ->
                                        LongPressExpenseItem(
                                            expense = expense,
                                            context = context,
                                            onEdit = { onNavigateToEditExpense(expense.id) },
                                            onDelete = { viewModel.deleteExpense(expense) },
                                            isAiModelDownloaded = isAiModelDownloaded,
                                            remarkTranslationState = remarkTranslations[expense.id],
                                            onTranslateRemark = { viewModel.translateRemark(expense) },
                                            modifier = Modifier.padding(horizontal = 16.dp)
                                        )
                                    }
                                }
                            }
                            
                            // Load More Indicator
                            if (uiState.hasMore) {
                                item(key = "load_more") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (uiState.isLoading) {
                                            ExpressiveLoadingIndicator(containerVisible = true)
                                        } else {
                                            Button(onClick = { viewModel.loadMore() }, colors = ButtonDefaults.buttonColorsPrimary()) {
                                                Text(context.getString(R.string.common_loading))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Always composed so WindowBottomSheet can play its exit animation.
        ExpenseFilterDialog(
            context = context,
            show = showFilterDialog,
            currentFilters = uiState.filters,
            onDismiss = { showFilterDialog = false },
            onApplyFilters = { filters ->
                viewModel.updateFilters(filters)
            }
        )
    }
}

/**
 * Date Header
 */
@Composable
fun ExpenseDateHeader(
    date: String,
    count: Int,
    totalAmount: Double,
    context: android.content.Context,
    locale: String? = null,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    val displayDate = formatRelativeDate(date, context, locale)
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = displayDate,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface
            )
            Text(
                text = context.getString(R.string.expense_stats_count) + ": $count",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
        }
        Text(
            text = "-" + context.getString(R.string.currency_format, context.getString(R.string.currency_symbol), totalAmount),
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.error
        )
    }
}

/**
 * Statistics Card
 */
@Composable
fun ExpenseStatisticsCard(
    statistics: com.chronie.homemoney.domain.model.ExpenseStatistics,
    context: android.content.Context,
    modifier: Modifier = Modifier,
    useSingleRow: Boolean = false
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.primaryContainer
        )
    ) {
        if (useSingleRow) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatisticItem(
                    label = context.getString(R.string.expense_stats_count),
                    value = statistics.count.toString()
                )
                StatisticItem(
                    label = context.getString(R.string.expense_stats_total),
                    value = context.getString(R.string.currency_format, context.getString(R.string.currency_symbol), statistics.totalAmount)
                )
                StatisticItem(
                    label = context.getString(R.string.expense_stats_average),
                    value = context.getString(R.string.currency_format, context.getString(R.string.currency_symbol), statistics.averageAmount)
                )
                StatisticItem(
                    label = context.getString(R.string.expense_stats_median),
                    value = context.getString(R.string.currency_format, context.getString(R.string.currency_symbol), statistics.medianAmount)
                )
            }
        } else {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatisticItem(
                        label = context.getString(R.string.expense_stats_count),
                        value = statistics.count.toString()
                    )
                    StatisticItem(
                        label = context.getString(R.string.expense_stats_total),
                        value = context.getString(R.string.currency_format, context.getString(R.string.currency_symbol), statistics.totalAmount)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatisticItem(
                        label = context.getString(R.string.expense_stats_average),
                        value = context.getString(R.string.currency_format, context.getString(R.string.currency_symbol), statistics.averageAmount)
                    )
                    StatisticItem(
                        label = context.getString(R.string.expense_stats_median),
                        value = context.getString(R.string.currency_format, context.getString(R.string.currency_symbol), statistics.medianAmount)
                    )
                }
            }
        }
    }
}

/**
 * Statistics Item
 */
@Composable
fun StatisticItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onPrimaryContainer
        )
    }
}

/**
 * Long Press Expense Item
 */
@Composable
fun LongPressExpenseItem(
    expense: Expense,
    context: android.content.Context,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    isAiModelDownloaded: Boolean = false,
    remarkTranslationState: RemarkTranslationState? = null,
    onTranslateRemark: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Bottom Sheet Menu Display State
    val showBottomSheetMenu = remember { mutableStateOf(false) }

    // Dialog State - First Confirm Dialog
    val showFirstConfirmDialog = remember { mutableStateOf(false) }
    // Dialog State - Second Confirm Dialog
    val showSecondConfirmDialog = remember { mutableStateOf(false) }

    // Handle First Confirmation
    fun handleFirstConfirm() {
        showFirstConfirmDialog.value = false
        showSecondConfirmDialog.value = true
    }

    // Handle Second Confirmation
    fun handleSecondConfirm() {
        showSecondConfirmDialog.value = false
        showBottomSheetMenu.value = false
        onDelete()
    }

    // Cancel Delete
    fun cancelDelete() {
        showFirstConfirmDialog.value = false
        showSecondConfirmDialog.value = false
    }

    // Show Delete Confirmation dialog
    fun showDeleteConfirm() {
        showBottomSheetMenu.value = false
        showFirstConfirmDialog.value = true
    }

    // Localized Expense Type Name
    val typeDisplayName = ExpenseTypeLocalizer.getLocalizedName(context, expense.type)

    // Remark currently shown: the AI translation when it is active, else the original.
    val sheetRemark = (remarkTranslationState as? RemarkTranslationState.Success)
        ?.takeIf { it.showingTranslated }
        ?.translatedText
        ?: expense.remark

    Box(modifier = modifier.fillMaxWidth()) {
        // Expense List Item
        ExpenseListItem(
            expense = expense,
            context = context,
            isAiModelDownloaded = isAiModelDownloaded,
            remarkTranslationState = remarkTranslationState,
            onTranslateRemark = onTranslateRemark,
            modifier = Modifier
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            showBottomSheetMenu.value = true
                        }
                    )
                }
        )

        // Bottom Sheet Menu (always composed, show-driven)
        WindowBottomSheet(
            show = showBottomSheetMenu.value,
            title = typeDisplayName,
            onDismissRequest = { showBottomSheetMenu.value = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Selected Record Details
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!sheetRemark.isNullOrBlank()) {
                        Text(
                            text = sheetRemark,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatDateByLocale(expense.date, context.resources.configuration.locales[0].toLanguageTag()),
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                        Text(
                            text = "-" + context.getString(R.string.currency_format, context.getString(R.string.currency_symbol), expense.amount),
                            style = MiuixTheme.textStyles.title3,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.error
                        )
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Edit Button
                    Button(
                        onClick = {
                            showBottomSheetMenu.value = false
                            onEdit()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.primaryContainer,
                            contentColor = MiuixTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edit",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = context.getString(R.string.edit))
                    }

                    // Delete Button
                    Button(
                        onClick = {
                            showDeleteConfirm()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.errorContainer,
                            contentColor = MiuixTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = context.getString(R.string.delete))
                    }
                }
            }
        }

        // First Confirmation Dialog (always composed, show-driven)
        WindowDialog(
            show = showFirstConfirmDialog.value,
            title = context.getString(R.string.delete_confirm_title),
            summary = context.getString(R.string.delete_confirm_message),
            onDismissRequest = { cancelDelete() }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularIconButton(onClick = { cancelDelete() }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = context.getString(R.string.cancel),
                        tint = MiuixTheme.colorScheme.onBackground
                    )
                }
                CircularIconButton(
                    onClick = { handleFirstConfirm() }
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = context.getString(R.string.confirm),
                        tint = MiuixTheme.colorScheme.primary
                    )
                }
            }
        }

        // Second Confirmation Dialog (always composed, show-driven)
        WindowDialog(
            show = showSecondConfirmDialog.value,
            title = context.getString(R.string.delete_second_confirm_title),
            summary = context.getString(R.string.delete_second_confirm_message),
            onDismissRequest = { cancelDelete() }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularIconButton(onClick = { cancelDelete() }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = context.getString(R.string.cancel),
                        tint = MiuixTheme.colorScheme.onBackground
                    )
                }
                CircularIconButton(
                    onClick = { handleSecondConfirm() }
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = context.getString(R.string.delete),
                        tint = MiuixTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/**
 * Expense List Item
 */
@Composable
fun ExpenseListItem(
    expense: Expense,
    context: android.content.Context,
    isAiModelDownloaded: Boolean = false,
    remarkTranslationState: RemarkTranslationState? = null,
    onTranslateRemark: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Localized Expense Type Name Display
    val typeDisplayName = ExpenseTypeLocalizer.getLocalizedName(context, expense.type)
    
    
    Card(
        modifier = modifier.fillMaxWidth(),
        pressFeedbackType = PressFeedbackType.Sink
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = typeDisplayName,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Medium
                )
                if (!expense.remark.isNullOrBlank()) {
                    val translationSuccess = remarkTranslationState as? RemarkTranslationState.Success
                    val showTranslation = translationSuccess != null && translationSuccess.showingTranslated
                    val displayRemark = translationSuccess
                        ?.takeIf { it.showingTranslated }
                        ?.translatedText
                        ?: expense.remark

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = displayRemark,
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                            if (showTranslation) {
                                // Caption distinguishes a real translation from
                                // the "already in your language" passthrough.
                                Text(
                                    text = context.getString(
                                        if (translationSuccess.unchanged)
                                            R.string.remark_translate_already_current_language
                                        else
                                            R.string.remark_translate_ai_result
                                    ),
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.primary
                                )
                            }
                            if (remarkTranslationState is RemarkTranslationState.Error) {
                                Text(
                                    text = context.getString(R.string.remark_translate_failed),
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.error
                                )
                            }
                        }
                        // Only offered when the on-device model package is verified.
                        if (isAiModelDownloaded) {
                            RemarkTranslateButton(
                                state = remarkTranslationState,
                                onClick = onTranslateRemark,
                                context = context
                            )
                        }
                    }
                }
                Text(
                    text = formatDateByLocale(expense.date, context.resources.configuration.locales[0].toLanguageTag()),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )
            }
            Text(
                text = "-" + context.getString(R.string.currency_format, context.getString(R.string.currency_symbol), expense.amount),
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.error
            )
        }
    }
}

/**
 * Compact icon button shown right after a remark text that translates
 * the remark with the on-device AI model.
 *
 * While inference runs, a small spinner replaces the icon. Once a
 * translation exists, the icon turns primary-colored to signal that
 * tapping it toggles back to the original remark.
 */
@Composable
fun RemarkTranslateButton(
    state: RemarkTranslationState?,
    onClick: () -> Unit,
    context: android.content.Context,
    modifier: Modifier = Modifier
) {
    if (state is RemarkTranslationState.Translating) {
        Box(
            modifier = modifier.size(28.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp))
        }
    } else {
        val isActive = state is RemarkTranslationState.Success && state.showingTranslated
        IconButton(
            onClick = onClick,
            modifier = modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Translate,
                contentDescription = context.getString(R.string.remark_translate_button),
                tint = if (isActive)
                    MiuixTheme.colorScheme.primary
                else
                    MiuixTheme.colorScheme.onSurfaceSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Table date header for wide screens
 */
@Composable
fun ExpenseTableDateHeader(
    date: String,
    count: Int,
    totalAmount: Double,
    context: android.content.Context,
    locale: String? = null,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    val displayDate = formatRelativeDate(date, context, locale)
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MiuixTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = displayDate,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${context.getString(R.string.expense_stats_count)}: $count",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )
                Text(
                    text = "-" + context.getString(R.string.currency_format, context.getString(R.string.currency_symbol), totalAmount),
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * Table layout for expense items on wide screens
 */
@Composable
fun ExpenseTableItems(
    expenses: List<Expense>,
    context: android.content.Context,
    onEdit: (Expense) -> Unit,
    onDelete: (Expense) -> Unit,
    isAiModelDownloaded: Boolean = false,
    remarkTranslations: Map<String, RemarkTranslationState> = emptyMap(),
    onTranslateRemark: (Expense) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var pendingDeleteExpense by remember { mutableStateOf<Expense?>(null) }
    val showFirstConfirm = remember { mutableStateOf(false) }
    val showSecondConfirm = remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Table header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MiuixTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = context.getString(R.string.expense_type),
                        style = MiuixTheme.textStyles.body2,
                        modifier = Modifier.weight(1f),
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                    Text(
                        text = context.getString(R.string.expense_remark),
                        style = MiuixTheme.textStyles.body2,
                        modifier = Modifier.weight(1.5f),
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                    Text(
                        text = context.getString(R.string.expense_amount),
                        style = MiuixTheme.textStyles.body2,
                        modifier = Modifier.width(120.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                    Text(
                        text = context.getString(R.string.common_actions),
                        style = MiuixTheme.textStyles.body2,
                        modifier = Modifier.width(100.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
            }
            
            HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
            
            // Table rows
            expenses.forEachIndexed { index, expense ->
                val typeDisplayName = ExpenseTypeLocalizer.getLocalizedName(context, expense.type)
                val remarkTranslation = remarkTranslations[expense.id]
                val remarkSuccess = remarkTranslation as? RemarkTranslationState.Success
                val showRemarkTranslation = remarkSuccess != null && remarkSuccess.showingTranslated
                val displayRemark = remarkSuccess
                    ?.takeIf { it.showingTranslated }
                    ?.translatedText
                    ?: expense.remark.orEmpty()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = typeDisplayName,
                        style = MiuixTheme.textStyles.body1,
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.Medium
                    )
                    if (expense.remark.isNullOrBlank()) {
                        Text(
                            text = "-",
                            style = MiuixTheme.textStyles.body2,
                            modifier = Modifier.weight(1.5f),
                            color = MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.5f),
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    } else {
                        Row(
                            modifier = Modifier.weight(1.5f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = displayRemark,
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                if (showRemarkTranslation) {
                                    Text(
                                        text = context.getString(
                                            if (remarkSuccess.unchanged)
                                                R.string.remark_translate_already_current_language
                                            else
                                                R.string.remark_translate_ai_result
                                        ),
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.primary
                                    )
                                }
                                if (remarkTranslation is RemarkTranslationState.Error) {
                                    Text(
                                        text = context.getString(R.string.remark_translate_failed),
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.error
                                    )
                                }
                            }
                            // Only offered when the on-device model package is verified.
                            if (isAiModelDownloaded) {
                                RemarkTranslateButton(
                                    state = remarkTranslation,
                                    onClick = { onTranslateRemark(expense) },
                                    context = context
                                )
                            }
                        }
                    }
                    Text(
                        text = "-" + context.getString(R.string.currency_format, context.getString(R.string.currency_symbol), expense.amount),
                        style = MiuixTheme.textStyles.body1,
                        modifier = Modifier.width(120.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.error
                    )
                    Row(
                        modifier = Modifier.width(100.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        IconButton(
                            onClick = { onEdit(expense) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = context.getString(R.string.edit),
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                pendingDeleteExpense = expense
                                showFirstConfirm.value = true
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = context.getString(R.string.delete),
                                tint = MiuixTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                
                if (index < expenses.size - 1) {
                    HorizontalDivider(
                        color = MiuixTheme.colorScheme.dividerLine.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }

    // Delete Confirm Dialogs (same as LongPressExpenseItem)
    // First Confirmation Dialog
    WindowDialog(
        show = showFirstConfirm.value,
        title = context.getString(R.string.delete_confirm_title),
        summary = context.getString(R.string.delete_confirm_message),
        onDismissRequest = {
            showFirstConfirm.value = false
            pendingDeleteExpense = null
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularIconButton(
                onClick = {
                    showFirstConfirm.value = false
                    pendingDeleteExpense = null
                }
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = context.getString(R.string.cancel),
                    tint = MiuixTheme.colorScheme.onBackground
                )
            }
            CircularIconButton(
                onClick = {
                    showFirstConfirm.value = false
                    showSecondConfirm.value = true
                }
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = context.getString(R.string.confirm),
                    tint = MiuixTheme.colorScheme.primary
                )
            }
        }
    }

    // Second Confirmation Dialog
    WindowDialog(
        show = showSecondConfirm.value,
        title = context.getString(R.string.delete_second_confirm_title),
        summary = context.getString(R.string.delete_second_confirm_message),
        onDismissRequest = {
            showSecondConfirm.value = false
            pendingDeleteExpense = null
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularIconButton(
                onClick = {
                    showSecondConfirm.value = false
                    pendingDeleteExpense = null
                }
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = context.getString(R.string.cancel),
                    tint = MiuixTheme.colorScheme.onBackground
                )
            }
            CircularIconButton(
                onClick = {
                    showSecondConfirm.value = false
                    pendingDeleteExpense?.let { onDelete(it) }
                    pendingDeleteExpense = null
                }
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = context.getString(R.string.delete),
                    tint = MiuixTheme.colorScheme.error
                )
            }
        }
    }
}

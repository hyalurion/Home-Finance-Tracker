package com.chronie.homemoney.ui.expense

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chronie.homemoney.R
import com.chronie.homemoney.domain.model.Expense
import com.chronie.homemoney.ui.budget.BudgetCard
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * 支出列表界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseListScreen(
    context: android.content.Context,
    viewModel: ExpenseListViewModel = hiltViewModel(),
    shouldRefresh: Boolean = false,
    onRefreshHandled: () -> Unit = {},
    onNavigateToMoreFunctions: () -> Unit = {},
    onNavigateToAddExpense: () -> Unit = {},
    onNavigateToEditExpense: (expenseId: String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var showFilterDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    
    // 处理刷新请求
    LaunchedEffect(shouldRefresh) {
        if (shouldRefresh) {
            viewModel.refresh()
            onRefreshHandled()
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 顶部工具栏
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = context.getString(R.string.expense_list_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    )
                    
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = context.getString(R.string.common_refresh)
                        )
                    }
                    
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = context.getString(R.string.common_more_functions)
                            )
                        }
                        
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(context.getString(R.string.common_filter)) },
                                onClick = {
                                    showMoreMenu = false
                                    showFilterDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(context.getString(R.string.expense_list_clear_filters)) },
                                onClick = {
                                    showMoreMenu = false
                                    viewModel.resetFilters()
                                }
                            )
                        }
                    }
                }
            }
            
            // 预算管理卡片 - 显示在标题栏下方
            BudgetCard(
                context = context,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
            
            // 统计信息卡片
            ExpenseStatisticsCard(
                statistics = uiState.statistics,
                context = context,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
            
            // 支出列表
            when {
                uiState.isLoading && uiState.expenses.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
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
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Button(onClick = { viewModel.refresh() }) {
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
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = context.getString(R.string.expense_list_empty_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                else -> {
                    val listState = rememberLazyListState()
                    val groupedExpenses = uiState.groupedExpenses
                    
                    // 计算总项数（日期标题 + 支出项）
                    val totalItems = groupedExpenses.size + uiState.expenses.size
                    
                    // 检测是否滚动到底部
                    LaunchedEffect(listState) {
                        snapshotFlow { 
                            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index 
                        }.collect { lastVisibleIndex ->
                            if (lastVisibleIndex != null && 
                                lastVisibleIndex >= totalItems - 3 && 
                                uiState.hasMore && 
                                !uiState.isLoading) {
                                viewModel.loadMore()
                            }
                        }
                    }
                    
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        groupedExpenses.forEach { (date, expenses) ->
                            // 日期标题
                            item(key = "header_$date") {
                                ExpenseDateHeader(
                                    date = date,
                                    count = expenses.size,
                                    totalAmount = expenses.sumOf { it.amount },
                                    context = context
                                )
                            }
                            
                            // 该日期下的支出项
                            items(
                                items = expenses,
                                key = { expense -> "expense_${expense.id}" }
                            ) { expense ->
                                SwipeableExpenseItem(
                                    expense = expense,
                                    context = context,
                                    onEdit = { onNavigateToEditExpense(expense.id) },
                                    onDelete = { viewModel.deleteExpense(expense) }
                                )
                            }
                        }
                        
                        // 加载更多指示器
                        if (uiState.hasMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (uiState.isLoading) {
                                        CircularProgressIndicator()
                                    } else {
                                        Button(onClick = { viewModel.loadMore() }) {
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
        
        // 浮动按钮
        FloatingActionButton(
            onClick = onNavigateToAddExpense,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = context.getString(R.string.add_expense_title))
        }
        
        // 筛选对话框
        if (showFilterDialog) {
            ExpenseFilterDialog(
                context = context,
                currentFilters = uiState.filters,
                onDismiss = { showFilterDialog = false },
                onApplyFilters = { filters ->
                    viewModel.updateFilters(filters)
                }
            )
        }
    }
}

/**
 * 日期标题
 */
@Composable
fun ExpenseDateHeader(
    date: String,
    count: Int,
    totalAmount: Double,
    context: android.content.Context,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = date,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = context.getString(R.string.expense_stats_count) + ": $count",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = String.format(Locale.getDefault(), "-¥%.2f", totalAmount),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )
    }
}

/**
 * 统计信息卡片
 */
@Composable
fun ExpenseStatisticsCard(
    statistics: com.chronie.homemoney.domain.model.ExpenseStatistics,
    context: android.content.Context,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
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
                    value = String.format(Locale.getDefault(), "¥%.2f", statistics.totalAmount)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatisticItem(
                    label = context.getString(R.string.expense_stats_average),
                    value = String.format(Locale.getDefault(), "¥%.2f", statistics.averageAmount)
                )
                StatisticItem(
                    label = context.getString(R.string.expense_stats_median),
                    value = String.format(Locale.getDefault(), "¥%.2f", statistics.medianAmount)
                )
            }
        }
    }
}

/**
 * 统计项
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
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

/**
 * 可滑动的支出列表项
 */
@Composable
fun SwipeableExpenseItem(
    expense: Expense,
    context: android.content.Context,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 滑动偏移量
    val swipeOffset = remember { mutableStateOf(0f) }
    // 动画偏移量
    val animatedOffset by animateDpAsState(
        targetValue = swipeOffset.value.dp,
        label = "Swipe offset"
    )
    // 滑动阈值 - 增大值使滑动更容易被检测
    val swipeThreshold = 100.dp
    
    // 检查是否正在执行操作 - 提前定义，确保在handleSwipeEnd中可用
    val isPerformingAction = remember { mutableStateOf(false) }
    
    // 弹窗状态 - 第一次确认
    val showFirstConfirmDialog = remember { mutableStateOf(false) }
    // 弹窗状态 - 第二次确认
    val showSecondConfirmDialog = remember { mutableStateOf(false) }
    
    // 重置滑动状态
    fun resetSwipe() {
        swipeOffset.value = 0f
    }
    
    // 处理滑动结束 - 符合MD3规范，直接触发操作
    fun handleSwipeEnd() {
        when {
            // 调整阈值比较，使操作更容易触发
            swipeOffset.value > swipeThreshold.value / 3 -> {
                // 从左向右滑动超过阈值，直接触发编辑操作
                onEdit()
                isPerformingAction.value = true
                resetSwipe()
            }
            swipeOffset.value < -swipeThreshold.value / 3 -> {
                // 从右向左滑动超过阈值，显示第一次确认弹窗
                isPerformingAction.value = true
                resetSwipe()
                showFirstConfirmDialog.value = true
            }
            else -> {
                // 未超过阈值，重置
                resetSwipe()
            }
        }
    }
    
    // 处理第一次确认
    fun handleFirstConfirm() {
        showFirstConfirmDialog.value = false
        showSecondConfirmDialog.value = true
    }
    
    // 处理第二次确认
    fun handleSecondConfirm() {
        showSecondConfirmDialog.value = false
        onDelete()
    }
    
    // 取消删除
    fun cancelDelete() {
        showFirstConfirmDialog.value = false
        showSecondConfirmDialog.value = false
    }
    
    Box(modifier = modifier.fillMaxWidth()) {
        // 背景操作按钮 - 只有在滑动时显示
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 左侧编辑按钮背景 - 只在向右滑动时显示
            if (swipeOffset.value > 0) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        IconButton(
                            onClick = {
                                onEdit()
                                resetSwipe()
                            },
                            modifier = Modifier
                                .padding(start = 16.dp)
                                .align(Alignment.CenterStart)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Edit",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
            
            // 右侧删除按钮背景 - 只在向左滑动时显示
            if (swipeOffset.value < 0) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        IconButton(
                            onClick = {
                                onDelete()
                                resetSwipe()
                            },
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .align(Alignment.CenterEnd)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
        
        // 可滑动的主要内容
        ExpenseListItem(
            expense = expense,
            context = context,
            modifier = Modifier
                .offset(x = animatedOffset)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            if (isPerformingAction.value) return@detectHorizontalDragGestures
                            
                            // 更新偏移量，但限制在阈值范围内
                            val newOffset = swipeOffset.value + dragAmount
                            swipeOffset.value = when {
                                newOffset > swipeThreshold.value -> swipeThreshold.value
                                newOffset < -swipeThreshold.value -> -swipeThreshold.value
                                else -> newOffset
                            }
                            change.consume()
                        },
                        onDragEnd = {
                            if (!isPerformingAction.value) {
                                handleSwipeEnd()
                            }
                            isPerformingAction.value = false
                        }
                    )
                }
        )
        
        // 第一次确认弹窗
        if (showFirstConfirmDialog.value) {
            AlertDialog(
                onDismissRequest = { cancelDelete() },
                title = { Text(text = context.getString(R.string.delete_confirm_title)) },
                text = { Text(text = context.getString(R.string.delete_confirm_message)) },
                confirmButton = {
                    Button(onClick = { handleFirstConfirm() }) {
                        Text(text = context.getString(R.string.confirm))
                    }
                },
                dismissButton = {
                    Button(onClick = { cancelDelete() }) {
                        Text(text = context.getString(R.string.cancel))
                    }
                }
            )
        }
        
        // 第二次确认弹窗
        if (showSecondConfirmDialog.value) {
            AlertDialog(
                onDismissRequest = { cancelDelete() },
                title = { Text(text = context.getString(R.string.delete_second_confirm_title)) },
                text = { Text(text = context.getString(R.string.delete_second_confirm_message)) },
                confirmButton = {
                    Button(
                        onClick = { handleSecondConfirm() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Text(text = context.getString(R.string.delete))
                    }
                },
                dismissButton = {
                    Button(onClick = { cancelDelete() }) {
                        Text(text = context.getString(R.string.cancel))
                    }
                }
            )
        }
    }
}

/**
 * 支出列表项
 */
@Composable
fun ExpenseListItem(
    expense: Expense,
    context: android.content.Context,
    modifier: Modifier = Modifier
) {
    // 使用传递的context来获取本地化字符串
    val typeDisplayName = ExpenseTypeLocalizer.getLocalizedName(context, expense.type)
    
    
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation()
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
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                if (!expense.remark.isNullOrBlank()) {
                    Text(
                        text = expense.remark,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = expense.date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = String.format(Locale.getDefault(), "-¥%.2f", expense.amount),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

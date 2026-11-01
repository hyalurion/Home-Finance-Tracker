package com.chronie.homemoney.ui.charts

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.chronie.homemoney.R
import com.chronie.homemoney.core.common.rememberCurrencyNumberFormat
import com.chronie.homemoney.domain.model.TimeRange
import com.chronie.homemoney.ui.components.CircularIconButton
import com.chronie.homemoney.ui.components.ExpressiveLinearProgressIndicator
import com.chronie.homemoney.ui.components.ExpressiveLoadingIndicator
import com.chronie.homemoney.ui.components.MiuixDatePickerSheet
import com.chronie.homemoney.ui.expense.ExpenseTypeLocalizer
import com.chronie.homemoney.ui.expense.formatDateByLocale
import com.chronie.homemoney.ui.scroll.RegisterScrollToTop
import com.himanshoe.charty.color.ChartyColor
import com.himanshoe.charty.common.config.ChartScaffoldConfig
import com.himanshoe.charty.line.LineChart
import com.himanshoe.charty.line.config.LineChartConfig
import com.himanshoe.charty.line.data.LineData
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowListPopup
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

enum class ChartType {
    TREND, CATEGORY, WEEKDAY
}

/**
 * Renders expense statistics and visual charts for the selected time range.
 *
 * Connects to [ChartsViewModel] and supports three chart types via [ChartType]:
 * - **Trend** — a smooth line chart of daily spending over the selected period.
 * - **Category** — an animated bar chart and progress list breaking down spending by category.
 * - **Weekday** — a radar/bar representation of spending distribution across days of the week.
 *
 * Includes a time range selector (this week, month, quarter, year, or custom date range)
 * and a statistics summary card showing total, count, average, and median amounts.
 *
 * @param context Android [Context] for string resources and locale formatting.
 * @param viewModel the Hilt-provided [ChartsViewModel].
 * @param onNavigateToWeekdayDetail callback when a weekday bar is tapped, carrying summary data.
 */
@Composable
fun ChartsScreen(
    context: Context,
    viewModel: ChartsViewModel = hiltViewModel(),
    onNavigateToWeekdayDetail: (dayOfWeek: Int, amount: Double, count: Int, percentage: Float, startDate: String, endDate: String) -> Unit = { _, _, _, _, _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedTimeRange by viewModel.selectedTimeRange.collectAsState()   
    var showTimeRangePopup by remember { mutableStateOf(false) }
    var selectedChartType by remember { mutableStateOf(ChartType.TREND) }
    var showChartTypePopup by remember { mutableStateOf(false) }
    
    // Custom range state
    val customStartDate by viewModel.customStartDate.collectAsState()
    val customEndDate by viewModel.customEndDate.collectAsState()
    var showCustomRangeBottomSheet by remember { mutableStateOf(false) }
    
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = context.getString(R.string.charts_title),
                largeTitle = context.getString(R.string.charts_title),
                scrollBehavior = scrollBehavior,
                actions = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Chart Type Switcher
                        Box {
                            Row(
                                modifier = Modifier
                                    .clickable { showChartTypePopup = true }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = getChartTypeText(context, selectedChartType),
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.primary
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            
                            WindowListPopup(
                                show = showChartTypePopup,
                                onDismissRequest = { showChartTypePopup = false }
                            ) {
                                val dismiss = LocalDismissState.current
                                ListPopupColumn {
                                    ChartType.entries.forEachIndexed { index, type ->
                                        DropdownImpl(
                                            text = getChartTypeText(context, type),
                                            optionSize = ChartType.entries.size,
                                            isSelected = selectedChartType == type,
                                            index = index,
                                            onSelectedIndexChange = {
                                                selectedChartType = type
                                                dismiss?.invoke()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        
                        // Time Range Selector
                        Box {
                            IconButton(onClick = { showTimeRangePopup = true }) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = "Select time range"
                                )
                            }
                            
                            WindowListPopup(
                                show = showTimeRangePopup,
                                onDismissRequest = { showTimeRangePopup = false }
                            ) {
                                val dismiss = LocalDismissState.current
                                val timeRanges = listOf(
                                    TimeRange.THIS_WEEK,
                                    TimeRange.THIS_MONTH,
                                    TimeRange.LAST_MONTH,
                                    TimeRange.THIS_QUARTER,
                                    TimeRange.THIS_YEAR,
                                    TimeRange.CUSTOM
                                )
                                ListPopupColumn {
                                    timeRanges.forEachIndexed { index, timeRange ->
                                        DropdownImpl(
                                            text = getTimeRangeText(context, timeRange),
                                            optionSize = timeRanges.size,
                                            isSelected = selectedTimeRange == timeRange,
                                            index = index,
                                            onSelectedIndexChange = {
                                                if (timeRange == TimeRange.CUSTOM) {
                                                    showCustomRangeBottomSheet = true
                                                    dismiss?.invoke()
                                                } else {
                                                    viewModel.selectTimeRange(timeRange)
                                                    dismiss?.invoke()
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        // Content area
        when (val state = uiState) {
            is ChartsUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    ExpressiveLoadingIndicator()
                }
            }
            is ChartsUiState.Success -> {
                ChartsContent(
                    context = context,
                    state = state,
                    selectedTimeRange = selectedTimeRange,
                    selectedChartType = selectedChartType,
                    scrollBehavior = scrollBehavior,
                    paddingValues = paddingValues,
                    onNavigateToWeekdayDetail = onNavigateToWeekdayDetail
                )
            }
            is ChartsUiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = state.message,
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.refresh() }, colors = ButtonDefaults.buttonColorsPrimary()) {
                        Text(context.getString(R.string.retry))
                    }
                }
            }
        }

        CustomRangeBottomSheet(
            show = showCustomRangeBottomSheet,
            context = context,
            initialStartDate = customStartDate ?: LocalDate.now().minusMonths(1),
            initialEndDate = customEndDate ?: LocalDate.now(),
            onDismiss = { showCustomRangeBottomSheet = false },
            onConfirm = { startDate, endDate ->
                viewModel.setCustomDateRange(startDate, endDate)
                viewModel.selectTimeRange(TimeRange.CUSTOM)
            }
        )
    }
}

@Composable
private fun ChartsContent(
    context: Context,
    state: ChartsUiState.Success,
    selectedTimeRange: TimeRange,
    selectedChartType: ChartType,
    scrollBehavior: ScrollBehavior,
    paddingValues: PaddingValues,
    onNavigateToWeekdayDetail: (dayOfWeek: Int, amount: Double, count: Int, percentage: Float, startDate: String, endDate: String) -> Unit = { _, _, _, _, _, _ -> }
) {
    val listState = rememberLazyListState()
    RegisterScrollToTop(listState)
    val currencyFormat = rememberCurrencyNumberFormat(context)
    
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(top = paddingValues.calculateTopPadding() + 16.dp, start = 16.dp, end = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Time range display
            TimeRangeCard(context, selectedTimeRange, state)
        }
        
        item {
            // Statistics summary (Always show)
            StatisticsSummaryCard(context, state.statistics, currencyFormat)
        }
        
        item {
            when (selectedChartType) {
                ChartType.TREND -> {
                    // Trend line chart
                    TrendLineChartCard(context, state.dailyData, currencyFormat)
                }
                ChartType.CATEGORY -> {
                    // Category breakdown
                    CategoryBreakdownCard(context, state.categoryData, currencyFormat)
                }
                ChartType.WEEKDAY -> {
                    // Weekday analysis
                    WeekdayRadarChartCard(
                        context = context,
                        weekdayData = state.weekdayData,
                        currencyFormat = currencyFormat,
                        startDate = state.startDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                        endDate = state.endDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                        onNavigateToWeekdayDetail = onNavigateToWeekdayDetail
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeRangeCard(
    context: Context,
    selectedTimeRange: TimeRange,
    state: ChartsUiState.Success
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = getTimeRangeText(context, selectedTimeRange),
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${formatDateByLocale(state.startDate.format(DateTimeFormatter.ISO_LOCAL_DATE), context.resources.configuration.locales[0].toLanguageTag())} - ${formatDateByLocale(state.endDate.format(DateTimeFormatter.ISO_LOCAL_DATE), context.resources.configuration.locales[0].toLanguageTag())}",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
        }
    }
}

@Composable
private fun StatisticsSummaryCard(
    context: Context,
    statistics: com.chronie.homemoney.domain.model.ExpenseStatistics,
    currencyFormat: NumberFormat
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        // Wrapping the layout body in a BoxWithConstraints lets us react
        // to the actual card width at composition time. On tablets /
        // desktop windows >= 600dp we lay the four metrics out in a
        // single evenly-split row to use the horizontal real estate;
        // phones keep the original 2x2 grid for readability.
        //
        // NOTE: BoxWithConstraints has Box semantics, so all layout
        // children must live inside a single wrapper Column to avoid
        // overlaps – the same pitfall we hit in LoginForm's first pass.
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val useSingleRow = maxWidth >= 600.dp

            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = context.getString(R.string.statistics_summary),
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (useSingleRow) {
                    // ---- 600dp+: four metrics in a single balanced row ----
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatisticItem(
                            label = context.getString(R.string.total_amount),
                            value = currencyFormat.format(statistics.totalAmount),
                            modifier = Modifier.weight(1f)
                        )
                        StatisticItem(
                            label = context.getString(R.string.count),
                            value = "${statistics.count}",
                            modifier = Modifier.weight(1f)
                        )
                        StatisticItem(
                            label = context.getString(R.string.average_amount),
                            value = currencyFormat.format(statistics.averageAmount),
                            modifier = Modifier.weight(1f)
                        )
                        StatisticItem(
                            label = context.getString(R.string.median_amount),
                            value = currencyFormat.format(statistics.medianAmount),
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    // ---- <600dp: original two-row / two-column layout ----
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatisticItem(
                            label = context.getString(R.string.total_amount),
                            value = currencyFormat.format(statistics.totalAmount)
                        )
                        StatisticItem(
                            label = context.getString(R.string.count),
                            value = "${statistics.count}"
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatisticItem(
                            label = context.getString(R.string.average_amount),
                            value = currencyFormat.format(statistics.averageAmount)
                        )
                        StatisticItem(
                            label = context.getString(R.string.median_amount),
                            value = currencyFormat.format(statistics.medianAmount)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatisticItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceSecondary
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TrendLineChartCard(
    context: Context,
    dailyData: List<DailyChartData>,
    currencyFormat: NumberFormat
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = context.getString(R.string.trend_chart),
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (dailyData.isEmpty()) {
                Text(
                    text = context.getString(R.string.no_data),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier.padding(vertical = 32.dp)
                )
            } else {
                HighQualityLineChart(
                    data = dailyData,
                    currencyFormat = currencyFormat,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                )
            }
        }
    }
}

@Composable
private fun HighQualityLineChart(
    data: List<DailyChartData>,
    currencyFormat: NumberFormat,
    modifier: Modifier = Modifier
) {
    val primaryColor = MiuixTheme.colorScheme.primary
    
    val labelStep = maxOf(1, data.size / 7)
    val lineData = data.mapIndexed { index, dailyData ->
        val label = if (index % labelStep == 0 || index == data.size - 1) {
            "${dailyData.date.monthValue}/${dailyData.date.dayOfMonth}"
        } else {
            ""
        }
        LineData(
            label = label,
            value = dailyData.amount.toFloat()
        )
    }
    
    Box(modifier = modifier.padding(start = 8.dp, end = 8.dp)) {
        LineChart(
            data = { lineData },
            modifier = Modifier.fillMaxSize(),
            color = ChartyColor.Solid(primaryColor),
            lineConfig = LineChartConfig(
                lineWidth = 4f,
                showPoints = true,
                pointRadius = 6f,
                smoothCurve = true
            ),
            scaffoldConfig = ChartScaffoldConfig(
                gridColor = ChartyColor.Solid(MiuixTheme.colorScheme.dividerLine),
                axisColor = ChartyColor.Solid(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f)),
                labelTextStyle = MiuixTheme.textStyles.footnote1.copy(color = MiuixTheme.colorScheme.onSurface)
            )
        )
    }
}

@Composable
private fun CategoryBreakdownCard(
    context: Context,
    categoryData: List<CategoryChartData>,
    currencyFormat: NumberFormat
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = context.getString(R.string.category_breakdown),
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (categoryData.isEmpty()) {
                Text(
                    text = context.getString(R.string.no_data),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                val maxAmount = categoryData.maxOf { it.amount }
                val scrollState = rememberScrollState()
                RegisterScrollToTop(scrollState)
                var isAnimated by remember { mutableStateOf(false) }
                
                LaunchedEffect(categoryData) {
                    kotlinx.coroutines.delay(150.milliseconds)
                    isAnimated = true
                }
                
                val chartHeight = 220.dp
                val labelHeight = 40.dp
                val yAxisWidth = 50.dp
                val barWidth = 24.dp
                val itemWidth = 50.dp
                val spacing = 16.dp
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(chartHeight + labelHeight)
                ) {
                    Column(
                        modifier = Modifier.fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        repeat(5) { i ->
                            val value = maxAmount * (4 - i) / 4
                            Row(
                                modifier = Modifier.height((chartHeight / 4)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = currencyFormat.format(value),
                                    style = MiuixTheme.textStyles.footnote2.copy(
                                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                        fontSize = 9.sp
                                    ),
                                    modifier = Modifier.width(yAxisWidth)
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(0.5.dp)
                                        .background(MiuixTheme.colorScheme.dividerLine.copy(alpha = 0.3f))
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.height(labelHeight),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "0",
                                style = MiuixTheme.textStyles.footnote2.copy(
                                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                    fontSize = 9.sp
                                ),
                                modifier = Modifier.width(yAxisWidth)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .padding(start = yAxisWidth)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(chartHeight + labelHeight)
                                .horizontalScroll(scrollState),
                            horizontalArrangement = Arrangement.spacedBy(spacing),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            categoryData.forEachIndexed { index, category ->
                                val barColor = getColorForExpenseType(category.type)
                                val targetBarHeight = if (maxAmount > 0) {
                                    (category.amount / maxAmount * chartHeight.value).dp
                                } else {
                                    0.dp
                                }
                                
                                val animatedProgress = animateFloatAsState(
                                    targetValue = if (isAnimated) 1f else 0f,
                                    animationSpec = tween(
                                        durationMillis = 700,
                                        delayMillis = index * 40,
                                        easing = androidx.compose.animation.core.EaseOutQuart
                                    )
                                )
                                val animatedBarHeight = (targetBarHeight.value * animatedProgress.value).dp

                                Box(
                                    modifier = Modifier.width(itemWidth)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Bottom
                                    ) {
                                        Text(
                                            text = "${String.format(Locale.US, "%.1f", category.percentage)}%",
                                            style = MiuixTheme.textStyles.footnote1.copy(
                                                color = barColor,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp
                                            ),
                                            modifier = Modifier.padding(bottom = 4.dp)
                                        )

                                        Box(
                                            modifier = Modifier
                                                .width(barWidth)
                                                .height(animatedBarHeight)
                                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(
                                                    topStart = 6.dp,
                                                    topEnd = 6.dp,
                                                    bottomStart = 3.dp,
                                                    bottomEnd = 3.dp
                                                ))
                                                .background(
                                                    color = barColor,
                                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(
                                                        topStart = 6.dp,
                                                        topEnd = 6.dp,
                                                        bottomStart = 3.dp,
                                                        bottomEnd = 3.dp
                                                    )
                                                )
                                        )
                                        
                                        Spacer(modifier = Modifier.height(8.dp))
                                        
                                        Text(
                                            text = ExpenseTypeLocalizer.getLocalizedTypeName(context, category.type),
                                            style = MiuixTheme.textStyles.footnote2.copy(
                                                color = MiuixTheme.colorScheme.onSurface,
                                                fontSize = 9.sp
                                            ),
                                            maxLines = 1,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                categoryData.forEach { category ->
                    CategoryItem(context, category, currencyFormat)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
private fun CategoryItem(
    context: Context,
    category: CategoryChartData,
    currencyFormat: NumberFormat
) {
    val categoryColor = getColorForExpenseType(category.type)
    
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(10.dp)
                        .height(10.dp)
                        .background(
                            color = categoryColor,
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                )
                Text(
                    text = ExpenseTypeLocalizer.getLocalizedTypeName(context, category.type),
                    style = MiuixTheme.textStyles.body2
                )
            }
            Text(
                text = "${String.format("%.1f", category.percentage)}%",
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.Bold,
                color = categoryColor
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        ExpressiveLinearProgressIndicator(
            progress = category.percentage / 100f,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = categoryColor,
            trackColor = MiuixTheme.colorScheme.surfaceVariant
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = "${currencyFormat.format(category.amount)} (${category.count} ${context.getString(R.string.records)})",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceSecondary
        )
    }
}

@Composable
private fun CustomRangeBottomSheet(
    show: Boolean,
    context: Context,
    initialStartDate: LocalDate,
    initialEndDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit
) {
    var startDate by remember(show) { mutableStateOf(initialStartDate) }
    var endDate by remember(show) { mutableStateOf(initialEndDate) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    WindowBottomSheet(
        show = show,
        title = context.getString(R.string.custom_range),
        onDismissRequest = onDismiss,
        startAction = {
            CircularIconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = context.getString(R.string.cancel),
                    tint = MiuixTheme.colorScheme.onBackground
                )
            }
        },
        endAction = {
            CircularIconButton(
                onClick = {
                    onConfirm(startDate, endDate)
                },
                enabled = !startDate.isAfter(endDate)
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = context.getString(R.string.confirm),
                    tint = MiuixTheme.colorScheme.primary
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                onClick = { showStartDatePicker = true },
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                color = MiuixTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = context.getString(R.string.expense_list_filter_start_date),
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                        Text(
                            text = startDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                            style = MiuixTheme.textStyles.body1
                        )
                    }
                    Icon(
                        Icons.Default.DateRange,
                        contentDescription = context.getString(R.string.expense_list_filter_start_date),
                        tint = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
            }

            Surface(
                onClick = { showEndDatePicker = true },
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                color = MiuixTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = context.getString(R.string.expense_list_filter_end_date),
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                        Text(
                            text = endDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                            style = MiuixTheme.textStyles.body1
                        )
                    }
                    Icon(
                        Icons.Default.DateRange,
                        contentDescription = context.getString(R.string.expense_list_filter_end_date),
                        tint = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
            }
        }
    }

    MiuixDatePickerSheet(
        context = context,
        show = showStartDatePicker,
        initialDate = startDate,
        onDismiss = { showStartDatePicker = false },
        onDateSelected = { date -> startDate = date },
        title = context.getString(R.string.expense_list_filter_start_date)
    )

    MiuixDatePickerSheet(
        context = context,
        show = showEndDatePicker,
        initialDate = endDate,
        onDismiss = { showEndDatePicker = false },
        onDateSelected = { date -> endDate = date },
        title = context.getString(R.string.expense_list_filter_end_date)
    )
}

@Composable
private fun TimeRangeOption(
    context: Context,
    timeRange: TimeRange,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = isSelected, onClick = onClick)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = getTimeRangeText(context, timeRange),
            style = MiuixTheme.textStyles.body1
        )
    }
}

private fun getChartTypeText(context: Context, type: ChartType): String {
    return when (type) {
        ChartType.TREND -> context.getString(R.string.trend_chart)
        ChartType.CATEGORY -> context.getString(R.string.category_breakdown)
        ChartType.WEEKDAY -> context.getString(R.string.weekday_analysis)
    }
}

private fun getTimeRangeText(context: Context, timeRange: TimeRange): String {
    return when (timeRange) {
        TimeRange.THIS_WEEK -> context.getString(R.string.this_week)
        TimeRange.THIS_MONTH -> context.getString(R.string.this_month)
        TimeRange.LAST_MONTH -> context.getString(R.string.last_month)
        TimeRange.THIS_QUARTER -> context.getString(R.string.this_quarter)
        TimeRange.THIS_YEAR -> context.getString(R.string.this_year)
        TimeRange.CUSTOM -> context.getString(R.string.custom_range)
    }
}

private val expenseTypeColors = mapOf(
    "DAILY_GOODS" to androidx.compose.ui.graphics.Color(0xFFEF4444),
    "LUXURY" to androidx.compose.ui.graphics.Color(0xFF8B5CF6),
    "COMMUNICATION" to androidx.compose.ui.graphics.Color(0xFF3B82F6),
    "FOOD" to androidx.compose.ui.graphics.Color(0xFF10B981),
    "SNACKS" to androidx.compose.ui.graphics.Color(0xFFF59E0B),
    "COLD_DRINKS" to androidx.compose.ui.graphics.Color(0xFF06B6D4),
    "CONVENIENCE_FOOD" to androidx.compose.ui.graphics.Color(0xFF84CC16),
    "TEXTILES" to androidx.compose.ui.graphics.Color(0xFFF97316),
    "BEVERAGES" to androidx.compose.ui.graphics.Color(0xFFEC4899),
    "CONDIMENTS" to androidx.compose.ui.graphics.Color(0xFF14B8A6),
    "TRANSPORTATION" to androidx.compose.ui.graphics.Color(0xFF6366F1),
    "DINING" to androidx.compose.ui.graphics.Color(0xFFF43F5E),
    "MEDICAL" to androidx.compose.ui.graphics.Color(0xFF0EA5E9),
    "FRUITS" to androidx.compose.ui.graphics.Color(0xFF22C55E),
    "OTHER" to androidx.compose.ui.graphics.Color(0xFF6B7280),
    "SEAFOOD" to androidx.compose.ui.graphics.Color(0xFF0891B2),
    "DAIRY" to androidx.compose.ui.graphics.Color(0xFFA855F7),
    "GIFTS" to androidx.compose.ui.graphics.Color(0xFFD946EF),
    "TRAVEL" to androidx.compose.ui.graphics.Color(0xFF0284C7),
    "GOVERNMENT" to androidx.compose.ui.graphics.Color(0xFF7C3AED),
    "UTILITIES" to androidx.compose.ui.graphics.Color(0xFFF97316),
    "BEAUTY" to androidx.compose.ui.graphics.Color(0xFFF472B6),
    "BEAN_PRODUCTS" to androidx.compose.ui.graphics.Color(0xFF84CC16),
    "COSMETICS" to androidx.compose.ui.graphics.Color(0xFFEC4899),
    "ELECTRONICS" to androidx.compose.ui.graphics.Color(0xFF3B82F6),
    "HOUSEHOLD_APPLIANCES" to androidx.compose.ui.graphics.Color(0xFF10B981),
    "HARDWARE" to androidx.compose.ui.graphics.Color(0xFF6B7280),
    "CLOTHING" to androidx.compose.ui.graphics.Color(0xFFF59E0B)
)

private fun getColorForExpenseType(type: String): androidx.compose.ui.graphics.Color {
    return expenseTypeColors[type] ?: androidx.compose.ui.graphics.Color(0xFF6B7280)
}

private val colorPalette = listOf(
    androidx.compose.ui.graphics.Color(0xFFEF4444),
    androidx.compose.ui.graphics.Color(0xFFF97316),
    androidx.compose.ui.graphics.Color(0xFFF59E0B),
    androidx.compose.ui.graphics.Color(0xFF84CC16),
    androidx.compose.ui.graphics.Color(0xFF22C55E),
    androidx.compose.ui.graphics.Color(0xFF10B981),
    androidx.compose.ui.graphics.Color(0xFF14B8A6),
    androidx.compose.ui.graphics.Color(0xFF06B6D4),
    androidx.compose.ui.graphics.Color(0xFF0EA5E9),
    androidx.compose.ui.graphics.Color(0xFF3B82F6),
    androidx.compose.ui.graphics.Color(0xFF6366F1),
    androidx.compose.ui.graphics.Color(0xFF8B5CF6),
    androidx.compose.ui.graphics.Color(0xFFA855F7),
    androidx.compose.ui.graphics.Color(0xFFD946EF),
    androidx.compose.ui.graphics.Color(0xFFEC4899),
    androidx.compose.ui.graphics.Color(0xFFF472B6),
    androidx.compose.ui.graphics.Color(0xFFF43F5E),
    androidx.compose.ui.graphics.Color(0xFF0891B2),
    androidx.compose.ui.graphics.Color(0xFF0284C7),
    androidx.compose.ui.graphics.Color(0xFF7C3AED),
    androidx.compose.ui.graphics.Color(0xFF6B7280)
)

private fun getColorForIndex(index: Int): androidx.compose.ui.graphics.Color {
    return colorPalette[index % colorPalette.size]
}

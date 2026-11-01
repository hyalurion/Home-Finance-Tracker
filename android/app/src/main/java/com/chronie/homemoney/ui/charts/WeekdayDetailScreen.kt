package com.chronie.homemoney.ui.charts

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.chronie.homemoney.R
import com.chronie.homemoney.core.common.rememberCurrencyNumberFormat
import com.chronie.homemoney.ui.expense.ExpenseTypeLocalizer
import com.chronie.homemoney.ui.components.CircularIconButton
import com.chronie.homemoney.ui.components.ExpressiveLinearProgressIndicator
import com.chronie.homemoney.ui.components.ExpressiveLoadingIndicator
import com.chronie.homemoney.ui.scroll.RegisterScrollToTop
import java.text.NumberFormat
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Displays a detailed breakdown of expenses for a specific day of the week.
 *
 * Shows a summary card with total amount, record count, and percentage of weekly spending,
 * followed by a scrollable list of category breakdowns with progress bars.
 * Connects to [WeekdayDetailViewModel] to load per-category data filtered by the selected
 * weekday and the time range carried from the charts screen.
 *
 * @param context Android [Context] for string resources and locale formatting.
 * @param dayOfWeek the day index (0 = Sunday … 6 = Saturday) being analyzed.
 * @param amount the total expense amount for this weekday in the selected range.
 * @param count the total number of expense records for this weekday.
 * @param percentage the share of total weekly spending represented by this day.
 * @param onNavigateBack callback to pop the back stack.
 * @param viewModel the Hilt-provided [WeekdayDetailViewModel].
 */
@SuppressLint("DefaultStringFormat", "DefaultLocale")
@Composable
fun WeekdayDetailScreen(
    context: Context,
    dayOfWeek: Int,
    amount: Double,
    count: Int,
    percentage: Float,
    onNavigateBack: () -> Unit,
    viewModel: WeekdayDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currencyFormat = rememberCurrencyNumberFormat(context)
    
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = getWeekdayName(context, dayOfWeek),
                subtitle = context.getString(R.string.expense_details),
                navigationIcon = {
                    CircularIconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.padding(start = 8.dp, end = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = context.getString(R.string.back)
                        )
                    }
                },
                color = MiuixTheme.colorScheme.surface
            )
        }
    ) { paddingValues ->
        val scrollState = rememberScrollState()
        RegisterScrollToTop(scrollState)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            // Total amount and percentage
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = context.getString(R.string.total_amount),
                            style = MiuixTheme.textStyles.body2
                        )
                        Text(
                            text = currencyFormat.format(amount),
                            style = MiuixTheme.textStyles.body1,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.primary
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = context.getString(R.string.count),
                            style = MiuixTheme.textStyles.body2
                        )
                        Text(
                            text = "$count ${context.getString(R.string.records)}",
                            style = MiuixTheme.textStyles.body2,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = context.getString(R.string.percentage_of_total),
                            style = MiuixTheme.textStyles.body2
                        )
                        Text(
                            text = "${String.format("%.1f", percentage)}%",
                            style = MiuixTheme.textStyles.body2,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Category breakdown for this weekday
            when (val state = uiState) {
                is WeekdayDetailUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        ExpressiveLoadingIndicator(
                            containerVisible = false
                        )
                    }
                }
                is WeekdayDetailUiState.Success -> {
                    if (state.categoryBreakdown.isNotEmpty()) {
                        Text(
                            text = context.getString(R.string.category_breakdown),
                            style = MiuixTheme.textStyles.body1,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        state.categoryBreakdown.forEach { category ->
                            CategoryDetailItem(context, category, currencyFormat)
                            Spacer(modifier = Modifier.height(20.dp))
                        }
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = context.getString(R.string.no_data),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                modifier = Modifier.padding(vertical = 32.dp, horizontal = 16.dp)
                            )
                        }
                    }
                }
                is WeekdayDetailUiState.Error -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.defaultColors(
                            color = MiuixTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = state.message,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(vertical = 32.dp, horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
private fun CategoryDetailItem(
    context: Context,
    category: CategoryChartData,
    currencyFormat: NumberFormat
) {
    val categoryColor = getColorForExpenseType(category.type)
    
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
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
            
            Spacer(modifier = Modifier.height(8.dp))
            
            ExpressiveLinearProgressIndicator(
                progress = category.percentage / 100f,
                modifier = Modifier.fillMaxWidth(),
                color = categoryColor,
                trackColor = MiuixTheme.colorScheme.surfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${category.count} ${context.getString(R.string.records)}",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )
                Text(
                    text = currencyFormat.format(category.amount),
                    style = MiuixTheme.textStyles.footnote1,
                    color = categoryColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
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

private fun getWeekdayName(context: Context, dayOfWeek: Int): String {
    return when (dayOfWeek) {
        0 -> context.getString(R.string.sunday)
        1 -> context.getString(R.string.monday)
        2 -> context.getString(R.string.tuesday)
        3 -> context.getString(R.string.wednesday)
        4 -> context.getString(R.string.thursday)
        5 -> context.getString(R.string.friday)
        6 -> context.getString(R.string.saturday)
        else -> ""
    }
}

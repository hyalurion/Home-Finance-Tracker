package com.chronie.homemoney.ui.budget

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.chronie.homemoney.R
import com.chronie.homemoney.core.common.rememberCurrencySymbol
import com.chronie.homemoney.domain.model.BudgetStatus
import com.chronie.homemoney.ui.components.ExpressiveLoadingIndicator
import com.chronie.homemoney.ui.expense.formatMonthLabelByLocale
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Budget Management Card
 * Displays budget usage and settings in the expense list screen
 */
@Composable
fun BudgetCard(
    context: android.content.Context,
    viewModel: BudgetViewModel = hiltViewModel(),
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
    refreshTrigger: Int = 0
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    
    // Listen for refresh trigger
    LaunchedEffect(refreshTrigger) {
        viewModel.refresh()
    }
    
    // If budget feature is not enabled, show enable prompt
    if (uiState.budget?.isEnabled != true) {
        BudgetEnablePrompt(
            context = context,
            onEnableClick = {
                showSettings = true
            },
            modifier = modifier
        )
    } else {
        // Display budget usage
        val usage = uiState.budgetUsage
        if (usage != null) {
            BudgetUsageCard(
                context = context,
                usage = usage,
                onSettingsClick = { showSettings = true },
                modifier = modifier
            )
        } else {
            // Loading state while fetching budget usage
            Card(
                modifier = modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.surfaceVariant
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ExpressiveLoadingIndicator()
                }
            }
        }
    }
    
    // Budget Settings Dialog
    BudgetSettingsDialog(
        show = showSettings,
        context = context,
        currentBudget = uiState.budget,
        onDismiss = { showSettings = false },
        onSave = { limit, threshold, enabled ->
            viewModel.saveBudget(limit, threshold, enabled)
            showSettings = false
        }
    )
}

/**
 * Budget Enable Prompt Card
 * Displays a prompt to enable budget feature
 */
@Composable
fun BudgetEnablePrompt(
    context: android.content.Context,
    onEnableClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AccountBalance,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MiuixTheme.colorScheme.onSecondaryContainer
            )
            
            Text(
                text = context.getString(R.string.budget_enable_title),
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = context.getString(R.string.budget_enable_description),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )
            
            Button(
                onClick = onEnableClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary()
            ) {
                Text(context.getString(R.string.budget_enable_button))
            }
        }
    }
}

/**
 * Budget Usage Card — adaptive layout for phone + tablet.
 *
 * Large-screen (≥600dp): uses a two-column layout with the spending
 * amount + progress bar on the left and detail stats on the right,
 * plus an overflow-aware progress indicator that renders an
 * "overflow segment" beyond 100% when spending exceeds the budget.
 *
 * Phone (<600dp): the original single-column vertical layout.
 */
@Composable
fun BudgetUsageCard(
    context: android.content.Context,
    usage: com.chronie.homemoney.domain.model.BudgetUsage,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    val status = when {
        usage.isOverLimit -> BudgetStatus.OVER_LIMIT
        usage.isNearLimit -> BudgetStatus.WARNING
        else -> BudgetStatus.NORMAL
    }

    val progressColor = when (status) {
        BudgetStatus.OVER_LIMIT -> MiuixTheme.colorScheme.error
        BudgetStatus.WARNING -> MiuixTheme.colorScheme.primaryVariant
        BudgetStatus.NORMAL -> MiuixTheme.colorScheme.primary
    }

    val currencySymbol = rememberCurrencySymbol(context)
    val overAmount = (usage.currentSpending - usage.monthlyLimit).coerceAtLeast(0.0)

    Card(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            val isLargeScreen = maxWidth >= 600.dp

            if (isLargeScreen) {
                // ------- Large screen: two-column layout -------
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Title row
                    BudgetCardTitleRow(
                        context = context,
                        usage = usage,
                        isExpanded = isExpanded,
                        progressColor = progressColor,
                        onToggleExpanded = { isExpanded = !isExpanded },
                        onSettingsClick = onSettingsClick,
                        compact = true
                    )

                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = expandVertically(
                            animationSpec = tween(300, easing = FastOutSlowInEasing)
                        ),
                        exit = shrinkVertically(
                            animationSpec = tween(300, easing = FastOutSlowInEasing)
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            // ---- LEFT column: spending + progress ----
                            Column(
                                modifier = Modifier.weight(1.3f),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Amount row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    Text(
                                        text = context.getString(
                                            R.string.currency_format,
                                            currencySymbol,
                                            usage.currentSpending
                                        ),
                                        style = MiuixTheme.textStyles.title2,
                                        fontWeight = FontWeight.Bold,
                                        color = progressColor
                                    )
                                    Text(
                                        text = "/ " + context.getString(
                                            R.string.currency_format,
                                            currencySymbol,
                                            usage.monthlyLimit
                                        ),
                                        style = MiuixTheme.textStyles.body1,
                                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                                    )
                                }

                                // Overflow-aware progress indicator
                                OverflowProgressIndicator(
                                    percentage = usage.spendingPercentage,
                                    color = progressColor,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // Status alert (compact inline for large screens)
                                CompactStatusAlert(
                                    status = status,
                                    context = context,
                                    usage = usage,
                                    overAmount = overAmount
                                )
                            }

                            // ---- RIGHT column: detail stats ----
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                DetailItem(
                                    label = context.getString(R.string.budget_daily_average),
                                    value = context.getString(
                                        R.string.currency_format,
                                        currencySymbol,
                                        usage.dailyAverage
                                    )
                                )

                                DetailItem(
                                    label = context.getString(R.string.budget_recommended_daily),
                                    value = context.getString(
                                        R.string.currency_format,
                                        currencySymbol,
                                        usage.recommendedDaily
                                    ),
                                    valueColor = when {
                                        usage.recommendedDaily <= 0 ->
                                            MiuixTheme.colorScheme.error
                                        usage.recommendedDaily < usage.dailyAverage * 0.8 ->
                                            MiuixTheme.colorScheme.primaryVariant
                                        else ->
                                            MiuixTheme.colorScheme.primary
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                // ------- Phone: single-column layout -------
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Title row
                    BudgetCardTitleRow(
                        context = context,
                        usage = usage,
                        isExpanded = isExpanded,
                        progressColor = progressColor,
                        onToggleExpanded = { isExpanded = !isExpanded },
                        onSettingsClick = onSettingsClick,
                        compact = false
                    )

                    // Expanded state content
                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = expandVertically(
                            animationSpec = tween(300, easing = FastOutSlowInEasing)
                        ),
                        exit = shrinkVertically(
                            animationSpec = tween(300, easing = FastOutSlowInEasing)
                        )
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Amount row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Text(
                                    text = context.getString(
                                        R.string.currency_format,
                                        currencySymbol,
                                        usage.currentSpending
                                    ),
                                    style = MiuixTheme.textStyles.title2,
                                    fontWeight = FontWeight.Bold,
                                    color = progressColor
                                )
                                Text(
                                    text = "/ " + context.getString(
                                        R.string.currency_format,
                                        currencySymbol,
                                        usage.monthlyLimit
                                    ),
                                    style = MiuixTheme.textStyles.body1,
                                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                                )
                                Text(
                                    text = "(" + String.format(
                                        LocalLocale.current.platformLocale,
                                        "%.0f",
                                        usage.spendingPercentage
                                    ) + "%)",
                                    style = MiuixTheme.textStyles.body2,
                                    fontWeight = FontWeight.Medium,
                                    color = progressColor
                                )
                            }

                            // Progress bar (overflow-aware)
                            OverflowProgressIndicator(
                                percentage = usage.spendingPercentage,
                                color = progressColor,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Status alert
                            when (status) {
                                BudgetStatus.OVER_LIMIT -> {
                                    AlertCard(
                                        title = context.getString(R.string.budget_alert_over_title),
                                        message = context.getString(
                                            R.string.budget_alert_over_message,
                                            context.getString(
                                                R.string.currency_format,
                                                currencySymbol,
                                                overAmount
                                            )
                                        ),
                                        containerColor = MiuixTheme.colorScheme.errorContainer,
                                        contentColor = MiuixTheme.colorScheme.onErrorContainer
                                    )
                                }
                                BudgetStatus.WARNING -> {
                                    AlertCard(
                                        title = context.getString(R.string.budget_alert_warning_title),
                                        message = context.getString(
                                            R.string.budget_alert_warning_message,
                                            context.getString(
                                                R.string.currency_format,
                                                currencySymbol,
                                                usage.remainingAmount
                                            ),
                                            usage.spendingPercentage
                                        ),
                                        containerColor = MiuixTheme.colorScheme.tertiaryContainer,
                                        contentColor = MiuixTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                                BudgetStatus.NORMAL -> {
                                    AlertCard(
                                        title = context.getString(R.string.budget_alert_normal_title),
                                        message = context.getString(
                                            R.string.budget_alert_normal_message,
                                            context.getString(
                                                R.string.currency_format,
                                                currencySymbol,
                                                usage.remainingAmount
                                            ),
                                            100 - usage.spendingPercentage
                                        ),
                                        containerColor = MiuixTheme.colorScheme.primaryContainer,
                                        contentColor = MiuixTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            // Detail items row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                DetailItem(
                                    label = context.getString(R.string.budget_daily_average),
                                    value = context.getString(
                                        R.string.currency_format,
                                        currencySymbol,
                                        usage.dailyAverage
                                    )
                                )

                                DetailItem(
                                    label = context.getString(R.string.budget_recommended_daily),
                                    value = context.getString(
                                        R.string.currency_format,
                                        currencySymbol,
                                        usage.recommendedDaily
                                    ),
                                    valueColor = when {
                                        usage.recommendedDaily <= 0 ->
                                            MiuixTheme.colorScheme.error
                                        usage.recommendedDaily < usage.dailyAverage * 0.8 ->
                                            MiuixTheme.colorScheme.primaryVariant
                                        else ->
                                            MiuixTheme.colorScheme.primary
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Title row shared by both phone and large-screen layouts.
 *
 * @param compact when true, shows only month + icon buttons (no inline amount summary).
 */
@Composable
private fun BudgetCardTitleRow(
    context: android.content.Context,
    usage: com.chronie.homemoney.domain.model.BudgetUsage,
    isExpanded: Boolean,
    progressColor: Color,
    onToggleExpanded: () -> Unit,
    onSettingsClick: () -> Unit,
    compact: Boolean
) {
    val currencySymbol = rememberCurrencySymbol(context)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!compact && !isExpanded) {
            // Phone collapsed: month on left, amount summary right-aligned
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatMonthLabelByLocale(
                        usage.currentMonth + "-01",
                        context.resources.configuration.locales[0].toLanguageTag()
                    ),
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = context.getString(
                            R.string.currency_format_no_decimal,
                            currencySymbol,
                            usage.currentSpending
                        ),
                        style = MiuixTheme.textStyles.body2,
                        fontWeight = FontWeight.Bold,
                        color = progressColor
                    )
                    Text(
                        text = "/ " + context.getString(
                            R.string.currency_format_no_decimal,
                            currencySymbol,
                            usage.monthlyLimit
                        ),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                    Text(
                        text = "(" + String.format(
                            LocalLocale.current.platformLocale,
                            "%.0f",
                            usage.spendingPercentage
                        ) + "%)",
                        style = MiuixTheme.textStyles.footnote1,
                        color = progressColor
                    )
                }
            }
        } else if (compact && !isExpanded) {
            // Large-screen collapsed: month on left, summary amount on right
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = context.getString(R.string.budget_monthly_progress),
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = formatMonthLabelByLocale(
                            usage.currentMonth + "-01",
                            context.resources.configuration.locales[0].toLanguageTag()
                        ),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
                // Summary on the right side of the title row on large screens
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = context.getString(
                            R.string.currency_format_no_decimal,
                            currencySymbol,
                            usage.currentSpending
                        ),
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.Bold,
                        color = progressColor
                    )
                    Text(
                        text = "/ " + context.getString(
                            R.string.currency_format_no_decimal,
                            currencySymbol,
                            usage.monthlyLimit
                        ),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                    Text(
                        text = "(" + String.format(
                            LocalLocale.current.platformLocale,
                            "%.0f",
                            usage.spendingPercentage
                        ) + "%)",
                        style = MiuixTheme.textStyles.footnote1,
                        fontWeight = FontWeight.Medium,
                        color = progressColor
                    )
                }
            }
        } else {
            // Expanded state (phone + large-screen): title + month
            Column {
                Text(
                    text = context.getString(R.string.budget_monthly_progress),
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = formatMonthLabelByLocale(
                        usage.currentMonth + "-01",
                        context.resources.configuration.locales[0].toLanguageTag()
                    ),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )
            }
        }

        // Expand/Collapse + Settings buttons
        Row {
            IconButton(onClick = onToggleExpanded) {
                Icon(
                    imageVector = if (isExpanded)
                        Icons.Default.KeyboardArrowUp
                    else
                        Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded)
                        context.getString(R.string.budget_collapse)
                    else
                        context.getString(R.string.budget_expand)
                )
            }
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = context.getString(R.string.budget_settings)
                )
            }
        }
    }
}

/**
 * Overflow-aware progress indicator.
 *
 * When the percentage ≤ 100%, renders a single filled bar (same as
 * the original LinearProgressIndicator but with our styling).
 *
 * When the percentage > 100%, renders:
 *   1. The main bar filled to 100% with the status color.
 *   2. A second "overflow segment" below showing the extra percentage
 *      in a semi-transparent overlay of the same color, giving the
 *      user a clear visual of how far past the limit they are.
 */
@Composable
private fun OverflowProgressIndicator(
    percentage: Double,
    color: Color,
    modifier: Modifier = Modifier
) {
    val isOverBudget = percentage > 100.0
    val safeProgress = (percentage / 100.0).coerceIn(0.0, 1.0).toFloat()
    val overflowFraction = if (isOverBudget) {
        ((percentage - 100.0) / 100.0).coerceAtMost(2.0).toFloat()
    } else {
        0f
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (isOverBudget) 6.dp else 0.dp)
    ) {
        // Main bar (0–100%)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MiuixTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(safeProgress)
                    .fillMaxHeight()
                    .background(color)
            )
        }

        // Overflow segment (only when >100%)
        if (isOverBudget) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color.copy(alpha = 0.25f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(overflowFraction.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(color.copy(alpha = 0.6f))
                    )
                }
                Text(
                    text = "+${String.format(LocalLocale.current.platformLocale, "%.0f%%", percentage - 100.0)} overflow",
                    style = MiuixTheme.textStyles.footnote1,
                    color = color,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Compact inline status alert for large-screen layouts.
 * Uses a single-line row instead of the full AlertCard to conserve
 * vertical space on tablets.
 */
@Composable
private fun CompactStatusAlert(
    status: BudgetStatus,
    context: android.content.Context,
    usage: com.chronie.homemoney.domain.model.BudgetUsage,
    overAmount: Double
) {
    val currencySymbol = rememberCurrencySymbol(context)

    when (status) {
        BudgetStatus.OVER_LIMIT -> {
            val containerColor = MiuixTheme.colorScheme.errorContainer
            val contentColor = MiuixTheme.colorScheme.onErrorContainer
            Surface(
                color = containerColor,
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = context.getString(R.string.budget_alert_over_title),
                        style = MiuixTheme.textStyles.body2,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                    Text(
                        text = context.getString(
                            R.string.budget_alert_over_message,
                            context.getString(
                                R.string.currency_format,
                                currencySymbol,
                                overAmount
                            )
                        ),
                        style = MiuixTheme.textStyles.footnote1,
                        color = contentColor.copy(alpha = 0.8f),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        BudgetStatus.WARNING -> {
            val containerColor = MiuixTheme.colorScheme.tertiaryContainer
            val contentColor = MiuixTheme.colorScheme.onTertiaryContainer
            Surface(
                color = containerColor,
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = context.getString(R.string.budget_alert_warning_title),
                        style = MiuixTheme.textStyles.body2,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                    Text(
                        text = context.getString(
                            R.string.budget_alert_warning_message,
                            context.getString(
                                R.string.currency_format,
                                currencySymbol,
                                usage.remainingAmount
                            ),
                            usage.spendingPercentage
                        ),
                        style = MiuixTheme.textStyles.footnote1,
                        color = contentColor.copy(alpha = 0.8f),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        BudgetStatus.NORMAL -> {
            val containerColor = MiuixTheme.colorScheme.primaryContainer
            val contentColor = MiuixTheme.colorScheme.onPrimaryContainer
            Surface(
                color = containerColor,
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = context.getString(R.string.budget_alert_normal_title),
                        style = MiuixTheme.textStyles.body2,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                    Text(
                        text = context.getString(
                            R.string.budget_alert_normal_message,
                            context.getString(
                                R.string.currency_format,
                                currencySymbol,
                                usage.remainingAmount
                            ),
                            100 - usage.spendingPercentage
                        ),
                        style = MiuixTheme.textStyles.footnote1,
                        color = contentColor.copy(alpha = 0.8f),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Alert Card (full-width vertical layout for phone)
 */
@Composable
fun AlertCard(
    title: String,
    message: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = containerColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
            Text(
                text = message,
                style = MiuixTheme.textStyles.footnote1,
                color = contentColor.copy(alpha = 0.8f)
            )
        }
    }
}

/**
 * Detail Item
 */
@Composable
fun DetailItem(
    label: String,
    value: String,
    valueColor: Color = MiuixTheme.colorScheme.onSurface,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceSecondary
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.body2,
            fontWeight = FontWeight.Medium,
            color = valueColor
        )
    }
}

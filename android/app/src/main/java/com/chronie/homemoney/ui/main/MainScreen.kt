package com.chronie.homemoney.ui.main

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chronie.homemoney.R
import com.chronie.homemoney.ui.expense.ExpenseListScreen
import com.chronie.homemoney.ui.settings.SettingsScreen
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.InsertChart
import androidx.compose.material.icons.filled.Settings

/**
 * The main tabbed screen of the app, hosting the three primary sections:
 * expense list, charts/statistics, and settings.
 *
 * Uses [AnimatedContent] for cross-fade transitions between tabs and renders
 * a [FloatingBottomBar] with LiquidGlass mode for tab switching.
 *
 * @param context Android [Context] for string resources and callbacks.
 * @param shouldRefreshExpenses flag that triggers a data reload in the expense list tab.
 * @param onRefreshHandled callback invoked once the refresh has been consumed.
 * @param selectedTab the currently active tab index (0 = expenses, 1 = charts, 2 = settings).
 * @param onTabChange callback when the user switches tabs via the bottom bar.
 * @param onNavigateToDatabaseTest navigates to the database debug screen.
 * @param onNavigateToAddExpense navigates to the add-expense form.
 * @param onNavigateToAIChat navigates to the streaming AI chat screen.
 * @param onNavigateToEditExpense navigates to the edit-expense form for a given [expenseId].
 * @param onNavigateToWeekdayDetail navigates to the weekday detail breakdown screen.
 * @param onNavigateToLanSync navigates to the LAN sync screen.
 * @param onNavigateToOpenSourceLicenses navigates to the open-source licenses screen.
 * @param onRequireLogin callback when the user is no longer authenticated and must log in again.
 */
@Composable
fun MainScreen(
    context: Context,
    shouldRefreshExpenses: Boolean = false,
    onRefreshHandled: () -> Unit = {},
    selectedTab: Int = 0,
    onTabChange: (Int) -> Unit = {},
    onNavigateToDatabaseTest: () -> Unit = {},
    onNavigateToAddExpense: () -> Unit = {},
    onNavigateToAIChat: () -> Unit = {},
    onNavigateToEditExpense: (expenseId: String) -> Unit = {},
    onNavigateToWeekdayDetail: (dayOfWeek: Int, amount: Double, count: Int, percentage: Float, startDate: String, endDate: String) -> Unit = { _, _, _, _, _, _ -> },
    onNavigateToLanSync: () -> Unit = {},
    onNavigateToOpenSourceLicenses: () -> Unit = {},
    onRequireLogin: () -> Unit = {}
) {
    val backdrop = rememberLayerBackdrop()
    Box(modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
        ) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    scaleIn(
                        initialScale = 0.9f,
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing)) togetherWith
                            scaleOut(
                                targetScale = 0.9f,
                                animationSpec = tween(300, easing = FastOutSlowInEasing)
                            ) + fadeOut(animationSpec = tween(300, easing = FastOutSlowInEasing))
                },
                contentAlignment = Alignment.Center
            ) { tab ->
                when (tab) {
                    0 -> {
                        ExpenseListScreen(
                            context = context,
                            shouldRefresh = shouldRefreshExpenses,
                            onRefreshHandled = onRefreshHandled,
                            onNavigateToAddExpense = onNavigateToAddExpense,
                            onNavigateToAIChat = onNavigateToAIChat,
                            onNavigateToEditExpense = onNavigateToEditExpense
                        )
                    }
                    1 -> {
                        com.chronie.homemoney.ui.charts.ChartsScreen(
                            context = context,
                            onNavigateToWeekdayDetail = onNavigateToWeekdayDetail
                        )
                    }
                    2 -> {
                        SettingsScreen(
                            context = context,
                            onNavigateToDatabaseTest = onNavigateToDatabaseTest,
                            onNavigateToLanSync = onNavigateToLanSync,
                            onNavigateToOpenSourceLicenses = onNavigateToOpenSourceLicenses,
                            onLogout = {
                                android.util.Log.d("MainScreen", "Received onLogout callback")
                                onRequireLogin()
                            },
                            onRequireLogin = onRequireLogin
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
        ) {
            FloatingBottomBar(
                selectedIndex = { selectedTab },
                onSelected = onTabChange,
                backdrop = backdrop,
                tabsCount = 3,
                mode = FloatingBottomBarMode.LiquidGlass,
                colors = FloatingBottomBarDefaults.colors(
                    containerColor = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.4f),
                    indicatorColor = MiuixTheme.colorScheme.primary,
                    contentColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    activeContentColor = MiuixTheme.colorScheme.primary
                )
            ) {
                FloatingBottomBarItem(onClick = { onTabChange(0) }) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = context.getString(R.string.expense_list_title),
                        style = MiuixTheme.textStyles.footnote2,
                        color = LocalFloatingBottomBarContentColor.current
                    )
                }
                FloatingBottomBarItem(onClick = { onTabChange(1) }) {
                    Icon(
                        imageVector = Icons.Default.InsertChart,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = context.getString(R.string.charts_title),
                        style = MiuixTheme.textStyles.footnote2,
                        color = LocalFloatingBottomBarContentColor.current
                    )
                }
                FloatingBottomBarItem(onClick = { onTabChange(2) }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = context.getString(R.string.settings),
                        style = MiuixTheme.textStyles.footnote2,
                        color = LocalFloatingBottomBarContentColor.current
                    )
                }
            }
        }
    }
}
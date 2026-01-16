package com.chronie.homemoney.ui.main

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.InsertChart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chronie.homemoney.R
import com.chronie.homemoney.ui.expense.ExpenseListScreen
import com.chronie.homemoney.ui.settings.SettingsScreen
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding

@Composable
fun MainScreen(
    context: Context,
    shouldRefreshExpenses: Boolean = false,
    onRefreshHandled: () -> Unit = {},
    onNavigateToSettings: () -> Unit,
    onNavigateToDatabaseTest: () -> Unit = {},
    onNavigateToAddExpense: () -> Unit = {},
    onNavigateToEditExpense: (expenseId: String) -> Unit = {},
    onNavigateToMoreFunctions: () -> Unit = {},
    onRequireLogin: () -> Unit = {},
    onRequireMembership: () -> Unit = {},
    viewModel: MainViewModel = hiltViewModel()
) {
    val isDeveloperMode by viewModel.isDeveloperMode.collectAsState(initial = false)
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val isMember by viewModel.isMember.collectAsState()
    val shouldShowExpiryWarning by viewModel.shouldShowExpiryWarning.collectAsState()
    val daysUntilExpiry by viewModel.daysUntilExpiry.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    
    // 检查登录状态，未登录则跳转到欢迎页
    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn) {
            onRequireLogin()
        }
    }
    
    // 检查会员状态，非会员则跳转到会员购买页面
    LaunchedEffect(isLoggedIn, isMember) {
        if (isLoggedIn && !isMember) {
            onRequireMembership()
        }
    }
    
    // 原生界面（带底部 Tab 栏）
    Scaffold(
            topBar = {
                // 显示会员过期提醒
                if (shouldShowExpiryWarning) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = context.getString(R.string.membership_expiring_soon, daysUntilExpiry),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = onRequireMembership) {
                                    Text(
                                        text = context.getString(R.string.renew_now),
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                
                                IconButton(onClick = { viewModel.dismissExpiryWarning() }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = context.getString(R.string.remind_later),
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }
            },
            bottomBar = {
                BottomNavigationBar(
                    context = context,
                    selectedTab = selectedTab,
                    onTabChange = { selectedTab = it }
                )
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues)) {
                when (selectedTab) {
                    0 -> {
                        // 支出记录界面
                        ExpenseListScreen(
                            context = context,
                            shouldRefresh = shouldRefreshExpenses,
                            onRefreshHandled = onRefreshHandled,
                            onNavigateToMoreFunctions = {},
                            onNavigateToAddExpense = onNavigateToAddExpense,
                            onNavigateToEditExpense = onNavigateToEditExpense
                        )
                    }
                    1 -> {
                        // 图表界面
                        com.chronie.homemoney.ui.charts.ChartsScreen(
                            context = context,
                            onRequireLogin = onRequireLogin,
                            onRequireMembership = onRequireMembership
                        )
                    }
                    2 -> {
                        // 设置界面
                        SettingsScreen(
                            context = context,
                            onNavigateToDatabaseTest = onNavigateToDatabaseTest,
                            onNavigateToMembership = {
                                android.util.Log.d("MainScreen", "收到 onNavigateToMembership 回调")
                                onRequireMembership()
                            },
                            onLogout = {
                                android.util.Log.d("MainScreen", "收到 onLogout 回调")
                                onRequireLogin()
                            },
                            onRequireLogin = onRequireLogin,
                            onRequireMembership = onRequireMembership
                        )
                    }
                }
            }
        }
}

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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    context: Context,
    shouldRefreshExpenses: Boolean = false,
    onRefreshHandled: () -> Unit = {},
    selectedTab: Int = 0,
    onTabChange: (Int) -> Unit = {},
    onNavigateToSettings: () -> Unit,
    onNavigateToDatabaseTest: () -> Unit = {},
    onNavigateToAddExpense: () -> Unit = {},
    onNavigateToEditExpense: (expenseId: String) -> Unit = {},
    onNavigateToMoreFunctions: () -> Unit = {},
    onNavigateToWeekdayDetail: (dayOfWeek: Int, amount: Double, count: Int, percentage: Float, startDate: String, endDate: String) -> Unit = { _, _, _, _, _, _ -> },
    onRequireLogin: () -> Unit = {},
    onRequireMembership: () -> Unit = {},
    viewModel: MainViewModel = hiltViewModel()
) {
    val isDeveloperMode by viewModel.isDeveloperMode.collectAsState(initial = false)
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val isMember by viewModel.isMember.collectAsState()
    val shouldShowExpiryWarning by viewModel.shouldShowExpiryWarning.collectAsState()
    val daysUntilExpiry by viewModel.daysUntilExpiry.collectAsState()
    var showExpiryBottomSheet by remember { mutableStateOf(false) }
    val bottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    
    LaunchedEffect(shouldShowExpiryWarning) {
        if (shouldShowExpiryWarning) {
            showExpiryBottomSheet = true
        }
    }
    
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
            },
            bottomBar = {
                BottomNavigationBar(
                    context = context,
                    selectedTab = selectedTab,
                    onTabChange = onTabChange
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
                            onRequireMembership = onRequireMembership,
                            onNavigateToWeekdayDetail = onNavigateToWeekdayDetail
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
    
    // 会员即将过期提醒BottomSheet
    if (showExpiryBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { 
                showExpiryBottomSheet = false
                viewModel.dismissExpiryWarning()
            },
            sheetState = bottomSheetState,
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = context.getString(R.string.membership_expiring_soon, daysUntilExpiry),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                
                Text(
                    text = context.getString(R.string.membership_expiring_message),
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            showExpiryBottomSheet = false
                            onRequireMembership()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(context.getString(R.string.renew_now))
                    }
                    
                    TextButton(
                        onClick = {
                            showExpiryBottomSheet = false
                            viewModel.dismissExpiryWarning()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(context.getString(R.string.remind_later))
                    }
                }
            }
        }
    }
}

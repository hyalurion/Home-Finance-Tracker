package com.chronie.homemoney

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chronie.homemoney.core.common.LanguageManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.chronie.homemoney.ui.expense.AddExpenseScreen
import com.chronie.homemoney.ui.expense.AIExpenseScreen
import com.chronie.homemoney.ui.main.MainScreen
import com.chronie.homemoney.ui.settings.SettingsScreen
import com.chronie.homemoney.ui.test.DatabaseTestScreen
import com.chronie.homemoney.ui.theme.HomeMoneyTheme
import com.chronie.homemoney.ui.welcome.WelcomeScreen
import com.chronie.homemoney.ui.membership.MembershipPurchaseScreen
import com.chronie.homemoney.domain.usecase.CheckLoginStatusUseCase
import com.chronie.homemoney.domain.usecase.CheckMembershipUseCase
import com.chronie.homemoney.service.HealthCheckService
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject

val LocalLanguageManager = staticCompositionLocalOf<LanguageManager> {
    error("No LanguageManager provided")
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var languageManager: LanguageManager
    
    @Inject
    lateinit var syncScheduler: com.chronie.homemoney.data.sync.SyncScheduler
    
    @Inject
    lateinit var checkLoginStatusUseCase: CheckLoginStatusUseCase
    
    @Inject
    lateinit var checkMembershipUseCase: CheckMembershipUseCase
    
    @Inject
    lateinit var healthCheckService: HealthCheckService
    
    private var membershipCheckJob: kotlinx.coroutines.Job? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化同步调度器
        syncScheduler.initialize()
        
        // 应用启动时触发云同步尝试（允许失败）
        lifecycleScope.launch {
            try {
                syncScheduler.triggerImmediateSync()
            } catch (e: Exception) {
                // 同步失败不影响应用启动
                android.util.Log.w("MainActivity", "Failed to trigger sync on app start", e)
            }
        }
        
        // 立即切换到正常主题，避免启动图背景影响 Popup 窗口
        setTheme(R.style.AppTheme_NoActionBar)
        
        // 清除启动图背景，设置为透明背景
        window.setBackgroundDrawableResource(android.R.color.transparent)
        
        // Enable edge-to-edge display
        enableEdgeToEdge()
        
        // Make sure the window draws behind system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // 启动定期会员状态检查
        startPeriodicMembershipCheck()
        
        // 启动健康检查服务
        healthCheckService.start()
        
        setContent {
            val currentLanguage by languageManager.currentLanguage.collectAsState()
            
            // Update configuration when language changes
            val context = LocalContext.current
            val locale = currentLanguage.locale
            Locale.setDefault(locale)
            val configuration = Configuration(context.resources.configuration)
            configuration.setLocale(locale)
            val localizedContext = context.createConfigurationContext(configuration)
            
            CompositionLocalProvider(
                LocalLanguageManager provides languageManager
            ) {
                HomeMoneyTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        HomeMoneyApp(
                            context = localizedContext,
                            checkLoginStatusUseCase = checkLoginStatusUseCase,
                            checkMembershipUseCase = checkMembershipUseCase
                        )
                    }
                }
            }
        }
    }
    
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        membershipCheckJob?.cancel()
        healthCheckService.stop()
    }
    
    private fun startPeriodicMembershipCheck() {
        membershipCheckJob = lifecycleScope.launch {
            while (true) {
                delay(30 * 60 * 1000L) // 每30分钟检查一次
                
                val isLoggedIn = checkLoginStatusUseCase()
                val isMember = checkMembershipUseCase()
                
                // 如果已登录但不是会员，触发重新检查
                if (isLoggedIn && !isMember) {
                    // 这里可以触发导航或显示提示
                    // 由于我们在 MainScreen 中已经有 LaunchedEffect 监听，
                    // 这里只需要确保状态被更新即可
                }
            }
        }
    }
}

@Composable
fun HomeMoneyApp(
    context: Context,
    checkLoginStatusUseCase: CheckLoginStatusUseCase,
    checkMembershipUseCase: CheckMembershipUseCase
) {
    val navController = rememberNavController()
    var shouldRefreshExpenses by remember { mutableStateOf(false) }
    
    // 确定初始路由
    val startDestination = remember {
        val isLoggedIn = checkLoginStatusUseCase()
        val isMember = checkMembershipUseCase()
        
        when {
            !isLoggedIn -> "welcome"
            !isMember -> "membership_purchase"
            else -> "main"
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("welcome") {
            WelcomeScreen(
                context = context,
                onSettingsClick = {
                    navController.navigate("settings")
                },
                onGetStartedClick = {
                    navController.navigate("main") {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToMembership = {
                    navController.navigate("membership_purchase") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable("membership_purchase") {
            MembershipPurchaseScreen(
                context = context,
                onNavigateToMain = {
                    navController.navigate("main") {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToWelcome = {
                    navController.navigate("welcome") {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToHistory = {
                    navController.navigate("subscription_history")
                }
            )
        }
        
        composable("subscription_history") {
            com.chronie.homemoney.ui.membership.SubscriptionHistoryScreen(
                context = context,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable("settings") {
            SettingsScreen(
                context = context,
                onNavigateToDatabaseTest = {
                    navController.navigate("database_test")
                },
                onNavigateToMembership = {
                    navController.navigate("membership_purchase")
                },
                onLogout = {
                    // 退出登录后，清空整个导航栈并返回欢迎页
                    navController.navigate("welcome") {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onRequireLogin = {
                    navController.navigate("welcome") {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onRequireMembership = {
                    navController.navigate("membership_purchase") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        
        composable("main") {
            MainScreen(
                context = context,
                shouldRefreshExpenses = shouldRefreshExpenses,
                onRefreshHandled = { shouldRefreshExpenses = false },
                onNavigateToSettings = {
                    navController.navigate("settings")
                },
                onNavigateToDatabaseTest = {
                    navController.navigate("database_test")
                },
                onNavigateToAddExpense = {
                    navController.navigate("add_expense")
                },
                onNavigateToEditExpense = { expenseId ->
                        navController.navigate(
                            route = "add_expense?expenseId=$expenseId"
                        )
                    },
                onRequireLogin = {
                    // 未登录时，清空导航栈并返回欢迎页
                    navController.navigate("welcome") {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onRequireMembership = {
                    // 非会员时，清空导航栈并跳转到会员购买页面
                    navController.navigate("membership_purchase") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        
        composable(
            "add_expense?expenseId={expenseId}",
            arguments = listOf(
                navArgument("expenseId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val expenseId = backStackEntry.arguments?.getString("expenseId")
            AddExpenseScreen(
                context = context,
                expenseId = expenseId,
                onNavigateBack = {
                    shouldRefreshExpenses = true
                    navController.popBackStack()
                },
                onNavigateToAI = {
                    navController.navigate("ai_expense")
                },
                onRequireLogin = {
                    navController.navigate("welcome") {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onRequireMembership = {
                    navController.navigate("membership_purchase") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        
        composable("ai_expense") {
            AIExpenseScreen(
                context = context,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onRecordsSaved = {
                    shouldRefreshExpenses = true
                    navController.popBackStack()
                }
            )
        }
        
        composable("database_test") {
            DatabaseTestScreen(
                context = context,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
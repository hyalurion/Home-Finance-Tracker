package com.chronie.homemoney

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.chronie.homemoney.core.common.LanguageManager
import com.chronie.homemoney.data.sync.DeviceSyncManagerFactory
import com.chronie.homemoney.data.sync.LocalNetworkPermission
import com.chronie.homemoney.data.sync.SyncRequestBus
import com.chronie.homemoney.domain.usecase.CheckLoginStatusUseCase
import com.chronie.homemoney.service.HealthCheckService
import com.chronie.homemoney.ui.chat.AIChatScreen
import com.chronie.homemoney.ui.expense.AddExpenseScreen
import com.chronie.homemoney.ui.main.MainScreen
import com.chronie.homemoney.ui.membership.MembershipScreen
import com.chronie.homemoney.ui.navigation.AppRoute
import com.chronie.homemoney.ui.scroll.ScrollToTopRegistry
import com.chronie.homemoney.ui.settings.OpenSourceLicensesScreen
import com.chronie.homemoney.ui.settings.SettingsScreen
import com.chronie.homemoney.ui.sync.IncomingSyncRequestDialog
import com.chronie.homemoney.ui.sync.LanSyncScreen
import com.chronie.homemoney.ui.test.DatabaseTestScreen
import com.chronie.homemoney.ui.theme.HomeMoneyTheme
import com.chronie.homemoney.ui.welcome.WelcomeScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs

val LocalLanguageManager = staticCompositionLocalOf<LanguageManager> {
    error("No LanguageManager provided")
}

/**
 * The single-Activity entry point for the Home Finance Tracker app.
 *
 * This Activity:
 * - Initializes the cloud sync scheduler and triggers an immediate sync on launch.
 * - Configures edge-to-edge rendering with automatic light/dark status bar icons.
 * - Starts the [HealthCheckService] for periodic background system checks.
 * - Observes the current language from [LanguageManager] and applies it to the
 *   [Configuration] so the entire Compose tree uses the correct locale resources.
 * - Sets [HomeMoneyApp] as the Compose content root, which hosts the
 *   [androidx.navigation3.ui.NavDisplay] for all screen navigation (welcome, main, settings, charts, etc.).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var languageManager: LanguageManager

    @Inject
    lateinit var syncScheduler: com.chronie.homemoney.data.sync.SyncScheduler

    @Inject
    lateinit var checkLoginStatusUseCase: CheckLoginStatusUseCase

    @Inject
    lateinit var healthCheckService: HealthCheckService

    @Inject
    lateinit var deviceSyncManagerFactory: DeviceSyncManagerFactory

    // Android 17 (API 37) Local Network Protection (LNP):
    // the inbound LAN sync server (started via DeviceSyncManagerFactory) and any outbound LAN
    // traffic require ACCESS_LOCAL_NETWORK. The Application class cannot show a runtime dialog,
    // so we request it here at launch and (re)start the sync server only after it is granted.
    // If already granted (e.g. from a previous launch) the launcher simply reports granted and
    // the server starts immediately.
    private val lanPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startLanSyncServer()
        } else {
            // User denied (or "don't ask again"): LAN features stay disabled. The save-server-URL
            // and device-search screens will re-prompt and surface an explicit hint on denial,
            // so we do not need a blocking dialog here.
            Log.w("MainActivity", "ACCESS_LOCAL_NETWORK denied; LAN sync/discovery disabled until granted")
        }
    }

    // ========================================================================
    // TAP-THE-TOP-TO-SCROLL (Activity-level dispatchTouchEvent implementation)
    // ========================================================================
    //
    // IMPORTANT – Android system limitation:
    //   The REAL status-bar pixel row (time, signal, battery icons) is owned
    //   by the SystemUI window that sits ABOVE our app window. NO Android app
    //   can ever receive touches there – it is a hard OS-level wall.
    //
    //   As a UX substitute we detect taps on the ENTIRE top chrome of the app
    //   (status-bar visual band + the TopAppBar underneath). That covers the
    //   user's muscle memory: "tap anywhere near the top to go back up".
    //
    // Detection band size (chosen empirically after debug measurements):
    //   The user's typical tap on a 2400px-tall screen lands at rawY ≈ 514 in
    //   the TopAppBar-title area. With 20% (= 480px) that was just OUTSIDE the
    //   band (inBand=false), so we use 28% + 80px safety margin. That gives
    //   ≈ 750px on a 2400px screen, comfortably covering:
    //       status bar visual band + large-title TopAppBar + budget-card header.
    //
    // Debug flags (disable for release):
    private val showDebugToasts       = false // confirmed-tap feedback
    private val showForceAllTouches   = false // EVERY touch diagnostic (VERY noisy)

    // ---- Gesture state ----
    private var tapTrackingActive = false
    private var tapDownX        = 0f
    private var tapDownY        = 0f
    private var touchSlopPx     = 0
    private var detectionBandPx = 0   // pixels; top N rows of the screen

    private fun refreshTapBand() {
        val dm = resources.displayMetrics
        // 28% of screen height + 80px absolute margin.
        // On a 2400px FHD+ phone this equals ≈ 752 px (≈ 210 dp).
        // Minimum 500 px so small / landscape / foldable states don't collapse.
        detectionBandPx = ((dm.heightPixels * 0.28f).toInt() + 80).coerceAtLeast(500)
        touchSlopPx     = ViewConfiguration.get(this).scaledTouchSlop
        android.util.Log.d(
            "StatusBarTap",
            "band=$detectionBandPx (28% of ${dm.heightPixels} + 80), slop=$touchSlopPx"
        )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) refreshTapBand()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // Refresh immediately on the very first touch of a session if needed.
        // onWindowFocusChanged is unreliable on some OEM ROMs (MIUI / ColorOS),
        // so we treat "first touch ever received" as another fallback sync point.
        if (detectionBandPx <= 0) refreshTapBand()

        val y     = ev.y        // relative to decorView (our Window's content area)
        val rawY  = ev.rawY     // absolute screen coordinate
        val x     = ev.x

        // -------------------- FORCE DIAGNOSTIC ------------------------------
        // Show a Toast for EVERY ACTION_DOWN so we can prove:
        //   a) dispatchTouchEvent is actually being called,
        //   b) what y/rawY values really are vs detectionBandPx,
        //   c) whether the comparison ever lands inside the band.
        if (showForceAllTouches && ev.actionMasked == MotionEvent.ACTION_DOWN) {
            val inBand = (y <= detectionBandPx.toFloat())
            Toast.makeText(
                this,
                "ACTION_DOWN  y=%.0f  rawY=%.0f  band=%d  inBand=%s  screenH=%d"
                    .format(y, rawY, detectionBandPx, inBand,
                            resources.displayMetrics.heightPixels),
                Toast.LENGTH_SHORT
            ).show()
            android.util.Log.i(
                "StatusBarTap",
                "DOWN y=$y rawY=$rawY band=$detectionBandPx inBand=$inBand"
            )
        }
        // -------------------------------------------------------------------

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                tapTrackingActive = (y <= detectionBandPx.toFloat())
                if (tapTrackingActive) {
                    tapDownX = x
                    tapDownY = y
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (tapTrackingActive) {
                    val dx = abs(x - tapDownX)
                    val dy = abs(y - tapDownY)
                    if (dx >= touchSlopPx || dy >= touchSlopPx) {
                        tapTrackingActive = false
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (tapTrackingActive) {
                    val dx           = abs(x - tapDownX)
                    val dy           = abs(y - tapDownY)
                    val tapConfirmed = dx < touchSlopPx && dy < touchSlopPx
                    if (tapConfirmed) {
                        val handlerExists = ScrollToTopRegistry.tryTrigger()
                        if (showDebugToasts) {
                            val msg = if (handlerExists) {
                                "✓ 顶部点击 → 已触发回顶"
                            } else {
                                "✓ 顶部点击检测到，但当前页面未注册回顶 handler"
                            }
                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                    tapTrackingActive = false
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                tapTrackingActive = false
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    @SuppressLint("LocalContextConfigurationRead")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize sync scheduler
        syncScheduler.initialize()

        // Trigger immediate sync on app start (allow failure)
        lifecycleScope.launch {
            try {
                syncScheduler.triggerImmediateSync()
            } catch (e: Exception) {
                // Sync failure does not affect app startup
                android.util.Log.w("MainActivity", "Failed to trigger sync on app start", e)
            }
        }

        // Switch to normal theme immediately to avoid splash screen background affecting Popup windows
        setTheme(R.style.AppTheme_NoActionBar)

        // Clear splash screen background, set to transparent background
        window.setBackgroundDrawableResource(android.R.color.transparent)

        // Edge-to-edge with proper status bar icon colors based on theme.
        // detectDarkMode ensures light mode → dark icons, dark mode → light icons.
        // NOTE: enableEdgeToEdge() internally calls setDecorFitsSystemWindows(false),
        // so we don't need a second call (duplicate calls can confuse the
        // insets-dispatch pipeline and break coordinate consistency).
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
                detectDarkMode = { resources.configuration.isNightModeActive }
            ),
            navigationBarStyle = androidx.activity.SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
                detectDarkMode = { resources.configuration.isNightModeActive }
            )
        )

        // Start health check service
        healthCheckService.start()

        // Android 17 LNP: request ACCESS_LOCAL_NETWORK before the LAN sync server is brought up.
        // If already granted, this immediately starts the server; otherwise it shows the runtime
        // dialog and starts the server in the launcher callback. See lanPermissionLauncher above
        // and HomeMoneyApplication's guarded start.
        ensureLanPermissionAndStartServer()

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
                        color = MiuixTheme.colorScheme.background
                    ) {
                        HomeMoneyApp(
                            context = localizedContext,
                            checkLoginStatusUseCase = checkLoginStatusUseCase
                        )
                    }
                }
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        if (::languageManager.isInitialized) {
            languageManager.checkAndApplySystemLanguage()
        }
    }

    override fun onResume() {
        super.onResume()
        languageManager.checkAndApplySystemLanguage()
    }

    // ---------------------------------------------------------------------------------
    // Android 17 (API 37) Local Network Protection entry point.
    // ---------------------------------------------------------------------------------

    /**
     * Ensures the ACCESS_LOCAL_NETWORK permission is held, then brings up the LAN sync server.
     *
     * If the permission is already granted this starts the server immediately. Otherwise it
     * launches the runtime dialog; the actual start happens in [lanPermissionLauncher]'s
     * callback once the user accepts. Calling startLanSyncServer() more than once is safe -
     * DeviceSyncManagerFactory.createDeviceSyncManager() returns a singleton and
     * LanDeviceSyncManager.startSyncServer() is idempotent.
     */
    private fun ensureLanPermissionAndStartServer() {
        if (LocalNetworkPermission.isGranted(this)) {
            startLanSyncServer()
        } else {
            lanPermissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
        }
    }

    /** Brings up the inbound LAN sync server (TCP listener + UDP discovery responder). */
    private fun startLanSyncServer() {
        try {
            deviceSyncManagerFactory.createDeviceSyncManager()
            Log.d("MainActivity", "LAN sync server started after permission grant")
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to start LAN sync server", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        healthCheckService.stop()
    }
}

/**
 * Root composable that owns the app-wide [androidx.navigation3.ui.NavDisplay] and top-level state.
 *
 * Determines the start destination based on login status (welcome vs. main).
 * Manages cross-screen state such as [shouldRefreshExpenses] and [selectedTab].
 * Listens for incoming LAN sync requests via [SyncRequestBus] and shows
 * [IncomingSyncRequestDialog] regardless of which screen is currently displayed.
 *
 * NOTE: The "tap status bar to scroll to top" gesture is implemented at the
 * Activity level in [MainActivity.dispatchTouchEvent], NOT inside Compose. That
 * avoids the SystemUI-interception and WindowInsets-read-0 pitfalls that made
 * every Compose-overlay approach unreliable for real devices.
 *
 * @param context the Android [Context] used for navigation callbacks and dialogs.
 * @param checkLoginStatusUseCase use case to determine whether the user is logged in.
 */
@Composable
fun HomeMoneyApp(
    context: Context,
    checkLoginStatusUseCase: CheckLoginStatusUseCase
) {
    val startRoute = remember {
        if (checkLoginStatusUseCase()) AppRoute.Main else AppRoute.Welcome
    }
    val backStack = rememberNavBackStack(startRoute)
    var shouldRefreshExpenses by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }

    Box(modifier = Modifier.fillMaxSize()) {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator()
            ),
            entryProvider = entryProvider {
                entry<AppRoute.Welcome> {
                    Box(Modifier.fillMaxSize()) {
                        WelcomeScreen(
                            context = context,
                            onGetStartedClick = {
                                backStack.clear()
                                backStack.add(AppRoute.Main)
                            }
                        )
                    }
                }

                entry<AppRoute.Membership> {
                    Box(Modifier.fillMaxSize()) {
                        MembershipScreen(
                            context = context,
                            onNavigateBack = { backStack.removeLastOrNull() },
                            onLogout = {
                                backStack.clear()
                                backStack.add(AppRoute.Welcome)
                            }
                        )
                    }
                }

                entry<AppRoute.Settings> {
                    Box(Modifier.fillMaxSize()) {
                        SettingsScreen(
                            context = context,
                            onNavigateToDatabaseTest = { backStack.add(AppRoute.DatabaseTest) },
                            onNavigateToLanSync = { backStack.add(AppRoute.LanSync) },
                            onNavigateToOpenSourceLicenses = { backStack.add(AppRoute.OpenSourceLicenses) },
                            onLogout = {
                                backStack.clear()
                                backStack.add(AppRoute.Welcome)
                            },
                            onRequireLogin = {
                                backStack.clear()
                                backStack.add(AppRoute.Welcome)
                            }
                        )
                    }
                }

                entry<AppRoute.LanSync> {
                    Box(Modifier.fillMaxSize()) {
                        LanSyncScreen(
                            context = context,
                            onNavigateBack = { backStack.removeLastOrNull() }
                        )
                    }
                }

                entry<AppRoute.Main> {
                    Box(Modifier.fillMaxSize()) {
                        MainScreen(
                            context = context,
                            shouldRefreshExpenses = shouldRefreshExpenses,
                            onRefreshHandled = { shouldRefreshExpenses = false },
                            selectedTab = selectedTab,
                            onTabChange = { selectedTab = it },
                            onNavigateToDatabaseTest = {
                                selectedTab = 2
                                backStack.add(AppRoute.DatabaseTest)
                            },
                            onNavigateToAddExpense = {
                                selectedTab = 0
                                backStack.add(AppRoute.AddExpense())
                            },
                            onNavigateToAIChat = {
                                selectedTab = 0
                                backStack.add(AppRoute.AIChat)
                            },
                            onNavigateToEditExpense = { expenseId ->
                                selectedTab = 0
                                backStack.add(AppRoute.AddExpense(expenseId))
                            },
                            onNavigateToWeekdayDetail = { dayOfWeek, amount, count, percentage, startDate, endDate ->
                                selectedTab = 1
                                backStack.add(
                                    AppRoute.WeekdayDetail(
                                        dayOfWeek = dayOfWeek,
                                        amount = amount,
                                        count = count,
                                        percentage = percentage,
                                        startDate = startDate,
                                        endDate = endDate
                                    )
                                )
                            },
                            onNavigateToLanSync = {
                                selectedTab = 2
                                backStack.add(AppRoute.LanSync)
                            },
                            onNavigateToOpenSourceLicenses = {
                                selectedTab = 2
                                backStack.add(AppRoute.OpenSourceLicenses)
                            },
                            onRequireLogin = {
                                backStack.clear()
                                backStack.add(AppRoute.Welcome)
                            }
                        )
                    }
                }

                entry<AppRoute.AddExpense> { key ->
                    Box(Modifier.fillMaxSize()) {
                        AddExpenseScreen(
                            context = context,
                            expenseId = key.expenseId,
                            onNavigateBack = {
                                shouldRefreshExpenses = true
                                backStack.removeLastOrNull()
                            }
                        )
                    }
                }

                entry<AppRoute.AIChat> {
                    Box(Modifier.fillMaxSize()) {
                        AIChatScreen(
                            context = context,
                            onNavigateBack = { backStack.removeLastOrNull() },
                            // Tool-written records must be reflected in the ledger list.
                            onRecordsChanged = { shouldRefreshExpenses = true }
                        )
                    }
                }

                entry<AppRoute.DatabaseTest> {
                    Box(Modifier.fillMaxSize()) {
                        DatabaseTestScreen(
                            context = context,
                            onNavigateBack = { backStack.removeLastOrNull() }
                        )
                    }
                }

                entry<AppRoute.OpenSourceLicenses> {
                    Box(Modifier.fillMaxSize()) {
                        OpenSourceLicensesScreen(
                            context = context,
                            onNavigateBack = { backStack.removeLastOrNull() }
                        )
                    }
                }

                entry<AppRoute.WeekdayDetail> { key ->
                    Box(Modifier.fillMaxSize()) {
                        com.chronie.homemoney.ui.charts.WeekdayDetailScreen(
                            context = context,
                            dayOfWeek = key.dayOfWeek,
                            amount = key.amount,
                            count = key.count,
                            percentage = key.percentage,
                            onNavigateBack = { backStack.removeLastOrNull() }
                        )
                    }
                }
            },
            transitionSpec = {
                slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it } togetherWith
                    slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { -it }
            },
            popTransitionSpec = {
                slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { -it / 4 } togetherWith
                    slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { it }
            },
            predictivePopTransitionSpec = {
                slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { -it / 4 } togetherWith
                    slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { it }
            }
        )

        // App-wide incoming LAN sync confirmation.
        // The responder runs on a native worker thread and, when no screen-bound callback is
        // installed, posts the request to SyncRequestBus. Observing it here (at the always-mounted
        // app root) means a sync request arriving on ANY screen still prompts the user instead of
        // being silently rejected - the original "B does nothing, A shows 100% failed" symptom.
        val busRequest by SyncRequestBus.request.collectAsState()
        if (busRequest != null) {
            IncomingSyncRequestDialog(
                context = context,
                requestInfo = busRequest!!,
                onAccept = { SyncRequestBus.resolve(true) },
                onReject = { SyncRequestBus.resolve(false) }
            )
        }
    }
}

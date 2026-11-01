package com.chronie.homemoney.ui.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import com.chronie.homemoney.ui.permissions.rememberLocalNetworkPermissionRequester
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import coil3.compose.AsyncImage
import com.chronie.homemoney.R
import com.chronie.homemoney.core.common.rememberCurrencySymbol
import com.chronie.homemoney.ui.components.CircularIconButton
import com.chronie.homemoney.ui.components.ColorPickerBottomSheet
import com.chronie.homemoney.ui.components.ExpressiveLoadingIndicator
import com.chronie.homemoney.ui.components.ExpressiveSwitch
import com.chronie.homemoney.ui.components.LanguageSelectorBottomSheet
import com.chronie.homemoney.ui.components.MiuixDatePickerSheet
import com.chronie.homemoney.ui.components.imageeditor.CropShape
import com.chronie.homemoney.ui.components.imageeditor.ImageEditorDialog
import com.chronie.homemoney.ui.components.imageeditor.compressBitmapToBytes
import com.chronie.homemoney.ui.expense.formatDateByLocale
import com.chronie.homemoney.ui.recyclebin.BatchAction
import com.chronie.homemoney.ui.recyclebin.RecycleBinScreen
import com.chronie.homemoney.ui.recyclebin.RecycleBinViewModel
import com.chronie.homemoney.ui.scroll.RegisterScrollToTop
import com.chronie.homemoney.ui.theme.LocalThemeSettings
import com.chronie.homemoney.ui.theme.ThemeSettings
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.menu.WindowIconDropdownMenu
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import java.time.LocalDate
import kotlin.time.Duration.Companion.milliseconds

@Serializable
enum class SettingsPage : NavKey {
    MAIN, ACCOUNT, APPEARANCE, FEATURES, DATA_SYNC, ABOUT, RECYCLE_BIN
}

/**
 * The top-level settings hub that delegates to sub-pages via an internal [androidx.navigation3.ui.NavDisplay].
 *
 * Connects to [SettingsViewModel] and renders the following sections:
 * - **Main** — profile card, theme, features, sync, and about entry points.
 * - **Account** — avatar upload, username display, logout/login.
 * - **Appearance** — language selection, dynamic color toggle, manual color picker.
 * - **Features** — on-device AI model management and monthly budget settings.
 * - **Data Sync** — cloud/LAN sync status, manual trigger, data export/import.
 * - **About** — feedback link, open-source licenses, developer options, version info.
 *
 * @param context Android [Context] for string resources and external intents.
 * @param viewModel the Hilt-provided [SettingsViewModel].
 * @param onNavigateToDatabaseTest navigates to the database debug screen (developer mode only).
 * @param onNavigateToLanSync navigates to the LAN sync management screen.
 * @param onNavigateToOpenSourceLicenses navigates to the OSS licenses list.
 * @param onLogout callback invoked after a successful logout.
 * @param onRequireLogin callback when authentication is required (e.g., for logged-out users).
 */
@SuppressLint("NoCollectCallFound")
@Composable
fun SettingsScreen(
    context: Context,
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToDatabaseTest: () -> Unit = {},
    onNavigateToLanSync: () -> Unit = {},
    onNavigateToOpenSourceLicenses: () -> Unit = {},
    onLogout: () -> Unit = {},
    onRequireLogin: () -> Unit = {}
) {
    // ============================================================
    // Two-pane layout support (tablets / desktop windows >= 600dp).
    // ============================================================
    //
    // Phones stay pixel-identical to the previous NavDisplay stack
    // (MAIN → sub-page slides in). Larger screens switch to a static
    // Row:
    //   LEFT  pane (weight 1)   = MainSettingsPage – always visible.
    //   RIGHT pane (weight 1.4) = selected sub-page content.
    //
    // The selected page is hoisted into `selectedPane` so the two
    // panes can share state. On small screens we hand the full
    // routing off to the existing NavDisplay.
    var selectedPane by rememberSaveable { mutableStateOf<SettingsPage?>(null) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useTwoPane = maxWidth >= 600.dp

        if (useTwoPane) {
            // ------- Large screen: Left list + Right detail --------
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // --- LEFT: master list, ~42% of the screen ---
                // Wrapped in a Surface so the pane split has a subtle
                // visual divider feel (matches Miuix's preference for
                // tinted sections).
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ) {
                    MainSettingsPage(
                        viewModel = viewModel,
                        context = context,
                        onNavigate = { page ->
                            // On the master pane we do NOT push onto the
                            // back stack – that would animate-slide the
                            // whole screen and break the two-pane layout.
                            // Instead, we simply update `selectedPane` so
                            // the right panel recomposes.
                            selectedPane = page
                        }
                    )
                }

                // Vertical hairline divider between the two panes.
                HorizontalDivider(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                )

                // --- RIGHT: detail panel, ~58% of the screen ---
                // weight(1.4) gives the detail page noticeably more
                // breathing room, which matches the typical Android
                // Settings + Info master/detail convention.
                //
                // Wrapped in a Surface so the layer-backdrop sampler
                // (used by FloatingBottomBar glass effects) always
                // sees solid content behind the bar. Without this
                // opaque surface the AnimatedContent/Scaffold stack
                // is transparent to the backdrop, which makes the
                // nav bar think the right pane is empty → muddy
                // gray (#5F5F61) refraction color.
                Surface(
                    modifier = Modifier
                        .weight(1.4f)
                        .fillMaxHeight(),
                    color = MiuixTheme.colorScheme.surface
                ) {
                    AnimatedContent(
                        targetState = selectedPane,
                        transitionSpec = {
                            // Mirror the horizontal slide animation used by
                            // the phone-path NavDisplay exactly:
                            //   * Forward / open  (anything → non-null page):
                            //       - new page slides IN from the right edge
                            //       - previous content slides OUT to the LEFT
                            //   * Backward / close (a page → null empty state):
                            //       - page slides OUT to the RIGHT
                            //       - empty state slides IN from the LEFT
                            //
                            // Using the same 320 ms + FastOutSlowInEasing
                            // constants ensures phone + tablet feel identical.
                            val openForward = targetState != null &&
                                (initialState == null ||
                                    (targetState != null &&
                                        initialState != null &&
                                        initialState != targetState))

                            if (openForward) {
                                slideInHorizontally(
                                    animationSpec = tween(
                                        durationMillis = 320,
                                        easing = FastOutSlowInEasing
                                    ),
                                    initialOffsetX = { it }
                                ) togetherWith slideOutHorizontally(
                                    animationSpec = tween(
                                        durationMillis = 320,
                                        easing = FastOutSlowInEasing
                                    ),
                                    targetOffsetX = { -it }
                                )
                            } else {
                                slideInHorizontally(
                                    animationSpec = tween(
                                        durationMillis = 320,
                                        easing = FastOutSlowInEasing
                                    ),
                                    initialOffsetX = { -it / 4 }
                                ) togetherWith slideOutHorizontally(
                                    animationSpec = tween(
                                        durationMillis = 320,
                                        easing = FastOutSlowInEasing
                                    ),
                                    targetOffsetX = { it }
                                )
                            }
                        },
                        contentAlignment = Alignment.TopStart,
                        modifier = Modifier.fillMaxSize()
                ) { targetPane ->
                    if (targetPane == null) {
                        EmptyDetailPane()
                    } else {
                        SettingsPaneContent(
                            selectedPage = targetPane,
                            context = context,
                            viewModel = viewModel,
                            embedMode = true,
                            onClosePane = { selectedPane = null },
                            onNavigateToDatabaseTest = onNavigateToDatabaseTest,
                            onNavigateToLanSync = onNavigateToLanSync,
                            onNavigateToOpenSourceLicenses = onNavigateToOpenSourceLicenses,
                            onLogout = onLogout,
                            onRequireLogin = onRequireLogin
                        )
                    }
                }
                }
            }
        } else {
            // ------- Small screen: original stack navigation --------
            val backStack = rememberNavBackStack(SettingsPage.MAIN)
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                modifier = Modifier.fillMaxSize(),
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator()
                ),
                entryProvider = entryProvider {
                    entry<SettingsPage> { key ->
                        SettingsPaneContent(
                            selectedPage = key,
                            context = context,
                            viewModel = viewModel,
                            embedMode = false,
                            onClosePane = { backStack.removeLastOrNull() },
                            onNavigateToDatabaseTest = onNavigateToDatabaseTest,
                            onNavigateToLanSync = onNavigateToLanSync,
                            onNavigateToOpenSourceLicenses = onNavigateToOpenSourceLicenses,
                            onLogout = onLogout,
                            onRequireLogin = onRequireLogin,
                            onNavigateSubPage = { page -> backStack.add(page) }
                        )
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
        }
    }
}

/**
 * Centralizes rendering of every [SettingsPage] so both the single-pane
 * (phone) NavDisplay path and the two-pane (tablet) Row path can share
 * one set of page bodies.
 *
 * @param selectedPage which page to render. MAIN is special – phones
 *   render [MainSettingsPage] as a full screen while tablet mode never
 *   passes MAIN here (it lives permanently on the left pane).
 * @param embedMode
 *   * `true`  → tablet / right-pane rendering. Callers MUST have already
 *               provided an outer Scaffold / top-bar for the overall
 *               Settings screen; this function shows a lighter-weight
 *               panel header rather than a second TopAppBar + ← arrow.
 *               (Nested scaffold would double-inset the status bar.)
 *   * `false` → phone / full-screen rendering. Each page paints its own
 *               full [SettingsSubPage] chrome with back button as before.
 * @param onClosePane invoked when the user requests close for the
 *   current panel: on phones this pops the Nav stack; on tablets it
 *   clears the right-pane selection back to the empty state.
 * @param onNavigateSubPage forwarded onward to [MainSettingsPage] so
 *   that the phone-path MAIN entry still knows how to push new pages.
 */
@Composable
private fun SettingsPaneContent(
    selectedPage: SettingsPage,
    context: Context,
    viewModel: SettingsViewModel,
    embedMode: Boolean,
    onClosePane: () -> Unit,
    onNavigateToDatabaseTest: () -> Unit,
    onNavigateToLanSync: () -> Unit,
    onNavigateToOpenSourceLicenses: () -> Unit,
    onLogout: () -> Unit,
    onRequireLogin: () -> Unit,
    onNavigateSubPage: (SettingsPage) -> Unit = {}
) {
    when (selectedPage) {
        SettingsPage.MAIN -> MainSettingsPage(
            viewModel = viewModel,
            context = context,
            onNavigate = onNavigateSubPage
        )

        SettingsPage.ACCOUNT -> DecoratedSettingsPane(
            embedMode = embedMode,
            title = context.getString(R.string.auth_account_info),
            onClosePane = onClosePane
        ) { paddingValues ->
            AccountSettingsPage(
                viewModel = viewModel,
                context = context,
                onLogout = onLogout,
                onRequireLogin = onRequireLogin,
                paddingValues = paddingValues
            )
        }

        SettingsPage.APPEARANCE -> DecoratedSettingsPane(
            embedMode = embedMode,
            title = context.getString(R.string.theme_settings),
            onClosePane = onClosePane
        ) { paddingValues ->
            AppearanceSettingsPage(
                viewModel = viewModel,
                context = context,
                paddingValues = paddingValues
            )
        }

        SettingsPage.FEATURES -> DecoratedSettingsPane(
            embedMode = embedMode,
            title = context.getString(R.string.budget_settings),
            onClosePane = onClosePane
        ) { paddingValues ->
            FeaturesSettingsPage(
                viewModel = viewModel,
                context = context,
                paddingValues = paddingValues
            )
        }

        SettingsPage.DATA_SYNC -> DecoratedSettingsPane(
            embedMode = embedMode,
            title = context.getString(R.string.sync_title),
            onClosePane = onClosePane
        ) { paddingValues ->
            DataSyncSettingsPage(
                viewModel = viewModel,
                context = context,
                onNavigateToLanSync = onNavigateToLanSync,
                paddingValues = paddingValues
            )
        }

        SettingsPage.ABOUT -> DecoratedSettingsPane(
            embedMode = embedMode,
            title = context.getString(R.string.common_more_functions),
            onClosePane = onClosePane
        ) { paddingValues ->
            AboutSettingsPage(
                viewModel = viewModel,
                context = context,
                onNavigateToOpenSourceLicenses = onNavigateToOpenSourceLicenses,
                onNavigateToDatabaseTest = onNavigateToDatabaseTest,
                paddingValues = paddingValues
            )
        }

        SettingsPage.RECYCLE_BIN -> {
            val recycleBinViewModel: RecycleBinViewModel = hiltViewModel()
            val isSelectMode by recycleBinViewModel.isSelectMode.collectAsState()

            DecoratedSettingsPane(
                embedMode = embedMode,
                title = context.getString(R.string.recycle_bin),
                onClosePane = onClosePane,
                actions = {
                    if (isSelectMode) {
                        WindowIconDropdownMenu(
                            entries = listOf(
                                DropdownEntry(
                                    items = listOf(
                                        DropdownItem(
                                            text = context.getString(R.string.recycle_bin_restore_selected),
                                            icon = { modifier ->
                                                Icon(
                                                    Icons.Default.Restore,
                                                    contentDescription = null,
                                                    modifier = modifier
                                                )
                                            },
                                            onClick = {
                                                recycleBinViewModel.requestBatchAction(
                                                    BatchAction.RESTORE_SELECTED
                                                )
                                            }
                                        )
                                    )
                                ),
                                DropdownEntry(
                                    items = listOf(
                                        DropdownItem(
                                            text = "",
                                            icon = { modifier ->
                                                Row(
                                                    modifier = modifier,
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = null,
                                                        tint = MiuixTheme.colorScheme.error
                                                    )
                                                    Text(
                                                        text = context.getString(R.string.recycle_bin_delete_selected),
                                                        color = MiuixTheme.colorScheme.error
                                                    )
                                                }
                                            },
                                            onClick = {
                                                recycleBinViewModel.requestBatchAction(
                                                    BatchAction.DELETE_SELECTED
                                                )
                                            }
                                        )
                                    )
                                )
                            ),
                            dropdownColors = DropdownDefaults.dropdownColors(
                                contentColor = MiuixTheme.colorScheme.onSurface,
                                summaryColor = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = context.getString(R.string.common_more_functions)
                            )
                        }
                    } else {
                        WindowIconDropdownMenu(
                            entries = listOf(
                                DropdownEntry(
                                    items = listOf(
                                        DropdownItem(
                                            text = context.getString(R.string.recycle_bin_select),
                                            icon = { modifier ->
                                                Icon(
                                                    Icons.Default.Checklist,
                                                    contentDescription = null,
                                                    modifier = modifier
                                                )
                                            },
                                            onClick = { recycleBinViewModel.enterSelectMode() }
                                        )
                                    )
                                ),
                                DropdownEntry(
                                    items = listOf(
                                        DropdownItem(
                                            text = context.getString(R.string.recycle_bin_restore_all),
                                            icon = { modifier ->
                                                Icon(
                                                    Icons.Default.Restore,
                                                    contentDescription = null,
                                                    modifier = modifier
                                                )
                                            },
                                            onClick = {
                                                recycleBinViewModel.requestBatchAction(
                                                    BatchAction.RESTORE_ALL
                                                )
                                            }
                                        )
                                    )
                                ),
                                DropdownEntry(
                                    items = listOf(
                                        DropdownItem(
                                            text = "",
                                            icon = { modifier ->
                                                Row(
                                                    modifier = modifier,
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = null,
                                                        tint = MiuixTheme.colorScheme.error
                                                    )
                                                    Text(
                                                        text = context.getString(R.string.recycle_bin_delete_all),
                                                        color = MiuixTheme.colorScheme.error
                                                    )
                                                }
                                            },
                                            onClick = {
                                                recycleBinViewModel.requestBatchAction(
                                                    BatchAction.DELETE_ALL
                                                )
                                            }
                                        )
                                    )
                                )
                            ),
                            dropdownColors = DropdownDefaults.dropdownColors(
                                contentColor = MiuixTheme.colorScheme.onSurface,
                                summaryColor = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = context.getString(R.string.common_more_functions)
                            )
                        }
                    }
                }
            ) { paddingValues ->
                RecycleBinScreen(
                    context = context,
                    viewModel = recycleBinViewModel,
                    paddingValues = paddingValues
                )
            }
        }
    }
}

/**
 * Switches between the phone-native [SettingsSubPage] (full Scaffold +
 * back arrow) and the tablet in-panel variant (just a Card title +
 * close action, without adding a second status-bar chrome).
 */
@Composable
private fun DecoratedSettingsPane(
    embedMode: Boolean,
    title: String,
    onClosePane: () -> Unit,
    actions: @Composable (RowScope.() -> Unit) = {},
    content: @Composable (PaddingValues) -> Unit
) {
    if (embedMode) {
        EmbeddedSettingsPane(
            title = title,
            onClosePane = onClosePane,
            actions = actions,
            content = content
        )
    } else {
        SettingsSubPage(
            title = title,
            onBack = onClosePane,
            actions = actions,
            content = content
        )
    }
}

/**
 * Tablet-mode right-panel chrome: a large-title collapsing header +
 * standard Miuix TopAppBar arrow-back that matches the phone path's
 * [SettingsSubPage] pixel-for-pixel, but keeps the header constrained
 * inside the bounds of the right panel rather than spanning the whole
 * screen.
 *
 * Replacing the previous "Close (×) + fixed 56.dp row" with a full
 * Miuix large-title TopAppBar + scrollBehavior gives:
 *   - Correct navigation icon (←, not ✕, matches phone behavior).
 *   - Large title that shrinks into a compact title as the body scrolls
 *     (exactly the same "适配大小滚动标题" UX as every other page).
 *   - Same nested-scroll / elevation / divider behavior, so users see
 *     a consistent app bar whether they tap a row on phone or tablet.
 */
@Composable
private fun EmbeddedSettingsPane(
    title: String,
    onClosePane: () -> Unit,
    actions: @Composable (RowScope.() -> Unit) = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = title,
                largeTitle = title,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    // Arrow-back rather than a close (×) to be
                    // consistent with the phone / full-screen sub-pages
                    // ("back to main list" is the same gesture as
                    // "back in the nav stack" on phones).
                    CircularIconButton(
                        onClick = onClosePane,
                        modifier = Modifier.padding(start = 8.dp, end = 4.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    actions()
                    Box(modifier = Modifier.padding(end = 8.dp))
                }
            )
        },
        content = { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
            ) {
                // Pass the scaffold's PaddingValues straight through to
                // the page body so calculateTopPadding() correctly
                // accounts for the collapsed/expanded large title.
                content(paddingValues)
            }
        }
    )
}

/**
 * Tablet-mode right-pane placeholder drawn when the user hasn't tapped
 * anything on the left yet. Renders the app's launcher icon at very low
 * opacity, dead-center, as a minimal "no selection" chrome.
 *
 * NOTE: We intentionally use `AsyncImage` (Coil3) here instead of
 * `painterResource(R.mipmap.ic_launcher)`, because `painterResource`
 * only supports VectorDrawable + raster (PNG/JPG/WEBP) drawables and
 * will throw `IllegalArgumentException` for modern adaptive launcher
 * icons defined in `mipmap-anydpi-v26` via `<adaptive-icon>`. Coil3's
 * built-in `ResourceFetcher` walks through `Resources.getDrawable()`
 * which correctly resolves both adaptive layers and raster fallbacks.
 */
@Composable
private fun EmptyDetailPane() {
    // Alpha chosen so the watermark is clearly visible but never
    // distracts from real detail content once a selection is made.
    val watermarkAlpha = 0.22f
    val watermarkSizeDp = 220.dp

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = R.mipmap.ic_launcher,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(watermarkSizeDp)
                .alpha(watermarkAlpha)
        )
    }
}

@Composable
private fun MainSettingsPage(
    viewModel: SettingsViewModel,
    context: Context,
    onNavigate: (SettingsPage) -> Unit
) {
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = context.getString(R.string.settings),
                largeTitle = context.getString(R.string.settings),
                scrollBehavior = scrollBehavior
            )
        },
        content = { paddingValues ->
            val settingsListState = rememberLazyListState()
            RegisterScrollToTop(settingsListState)
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                LazyColumn(
                    state = settingsListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = paddingValues.calculateTopPadding(),
                        end = 16.dp
                    )
                ) {
                    item {
                        val currentUsername by viewModel.currentUsername.collectAsState()
                        val avatar by viewModel.avatar.collectAsState()

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                                .clickable { onNavigate(SettingsPage.ACCOUNT) },
                            color = MiuixTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(60.dp)) {
                                    if (avatar != null) {
                                        AsyncImage(
                                            model = avatar,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Surface(
                                            modifier = Modifier.fillMaxSize(),
                                            color = MiuixTheme.colorScheme.primaryContainer,
                                            shape = CircleShape
                                        ) {
                                            Icon(
                                                Icons.Default.Person,
                                                contentDescription = null,
                                                modifier = Modifier.padding(12.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentUsername ?: context.getString(R.string.auth_not_logged_in),
                                        style = MiuixTheme.textStyles.body1,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = context.getString(R.string.auth_account_info),
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                                    )
                                }

                                Icon(Icons.Default.ChevronRight, contentDescription = null)
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(24.dp)) }

                    item {
                        ArrowPreference(
                            title = context.getString(R.string.theme_settings),
                            summary = context.getString(R.string.language_settings),
                            startAction = {
                                Row {
                                    Surface(
                                        modifier = Modifier.size(40.dp),
                                        color = MiuixTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        shape = CircleShape
                                    ) {
                                        Icon(
                                            Icons.Default.Palette,
                                            contentDescription = null,
                                            modifier = Modifier.padding(8.dp),
                                            tint = MiuixTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                }
                            },
                            onClick = { onNavigate(SettingsPage.APPEARANCE) }
                        )
                    }

                    item {
                        ArrowPreference(
                            title = context.getString(R.string.features_settings),
                            summary = context.getString(R.string.settings_ai_title) + ", " + context.getString(R.string.budget_settings),
                            startAction = {
                                Row {
                                    Surface(
                                        modifier = Modifier.size(40.dp),
                                        color = MiuixTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        shape = CircleShape
                                    ) {
                                        Icon(
                                            Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            modifier = Modifier.padding(8.dp),
                                            tint = MiuixTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                }
                            },
                            onClick = { onNavigate(SettingsPage.FEATURES) }
                        )
                    }

                    item {
                        ArrowPreference(
                            title = context.getString(R.string.sync_title),
                            summary = context.getString(R.string.data_import_export),
                            startAction = {
                                Row {
                                    Surface(
                                        modifier = Modifier.size(40.dp),
                                        color = MiuixTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        shape = CircleShape
                                    ) {
                                        Icon(
                                            Icons.Default.Sync,
                                            contentDescription = null,
                                            modifier = Modifier.padding(8.dp),
                                            tint = MiuixTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                }
                            },
                            onClick = { onNavigate(SettingsPage.DATA_SYNC) }
                        )
                    }

                    item {
                        ArrowPreference(
                            title = context.getString(R.string.recycle_bin),
                            summary = context.getString(R.string.recycle_bin_entry_summary),
                            startAction = {
                                Row {
                                    Surface(
                                        modifier = Modifier.size(40.dp),
                                        color = MiuixTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        shape = CircleShape
                                    ) {
                                        Icon(
                                            Icons.Default.RestoreFromTrash,
                                            contentDescription = null,
                                            modifier = Modifier.padding(8.dp),
                                            tint = MiuixTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                }
                            },
                            onClick = { onNavigate(SettingsPage.RECYCLE_BIN) }
                        )
                    }

                    item {
                        ArrowPreference(
                            title = context.getString(R.string.common_more_functions),
                            summary = context.getString(R.string.feedback_title) + ", " + context.getString(R.string.open_source_licenses),
                            startAction = {
                                Row {
                                    Surface(
                                        modifier = Modifier.size(40.dp),
                                        color = MiuixTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        shape = CircleShape
                                    ) {
                                        Icon(
                                            Icons.Default.Info,
                                            contentDescription = null,
                                            modifier = Modifier.padding(8.dp),
                                            tint = MiuixTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                }
                            },
                            onClick = { onNavigate(SettingsPage.ABOUT) }
                        )
                    }
                }
            }
        }
    )
}

/**
 * Shared chrome for every settings sub-page.
 *
 * Uses the collapsible [TopAppBar] (rather than [SmallTopAppBar]) so the title renders as a large
 * heading when the page is at rest and shrinks into the compact bar as the user scrolls down.
 *
 * The [MiuixScrollBehavior] is created here and its nested-scroll connection is attached to the
 * content [Box]. Compose dispatches scroll events from any descendant scrollable to the nearest
 * ancestor nested-scroll node, so each sub-page's own `LazyColumn` drives the collapse without
 * needing to know about the scroll behavior.
 *
 * @param title text shown both as the large heading and the collapsed bar title.
 * @param onBack invoked when the navigation icon is tapped.
 * @param content the page body; receives the [PaddingValues] reported by the [Scaffold].
 */
@Composable
private fun SettingsSubPage(
    title: String,
    onBack: () -> Unit,
    actions: @Composable (RowScope.() -> Unit) = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = title,
                largeTitle = title,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    CircularIconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(start = 8.dp, end = 4.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    actions()
                    Box(modifier = Modifier.padding(end = 8.dp))
                }
            )
        },
        content = { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
            ) {
                content(paddingValues)
            }
        }
    )
}

@Composable
fun AccountSettingsPage(
    viewModel: SettingsViewModel,
    context: Context,
    onLogout: () -> Unit,
    onRequireLogin: () -> Unit,
    paddingValues: PaddingValues
) {
    val listState = rememberLazyListState()
    RegisterScrollToTop(listState)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(
            top = paddingValues.calculateTopPadding() + 16.dp,
            bottom = 16.dp
        )
    ) {
        item {
            AccountSection(
                viewModel = viewModel,
                context = context,
                onLogout = onLogout,
                onRequireLogin = onRequireLogin
            )
        }
    }
}

@Composable
fun AppearanceSettingsPage(
    viewModel: SettingsViewModel,
    context: Context,
    paddingValues: PaddingValues
) {
    val currentLanguage by viewModel.currentLanguage.collectAsState()
    val selectedCurrency by viewModel.selectedCurrency.collectAsState()
    val useDynamicColor by viewModel.useDynamicColor.collectAsState()
    var showLanguageBottomSheet by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }

    // Index 0 = follow language; the rest map to CurrencyOption.entries.
    val currencyItems = listOf(context.getString(R.string.currency_follow_language)) +
            com.chronie.homemoney.core.common.CurrencyManager.CurrencyOption.entries
                .map { it.label }
    val themeSettings = LocalThemeSettings.current

    val listState = rememberLazyListState()
    RegisterScrollToTop(listState)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(
            top = paddingValues.calculateTopPadding() + 16.dp,
            bottom = 16.dp
        )
    ) {
        item {
            Text(
                text = context.getString(R.string.language_settings),
                style = MiuixTheme.textStyles.body1,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            ArrowPreference(
                title = context.getString(R.string.select_language),
                summary = currentLanguage.localName,
                onClick = {
                    showLanguageBottomSheet = true
                }
            )

            WindowDropdownPreference(
                items = currencyItems,
                selectedIndex = (selectedCurrency?.ordinal ?: -1) + 1,
                title = context.getString(R.string.settings_currency),
                maxHeight = 480.dp,
                onSelectedIndexChange = { index ->
                    viewModel.setCurrency(
                        if (index == 0) null
                        else com.chronie.homemoney.core.common.CurrencyManager
                            .CurrencyOption.entries[index - 1]
                    )
                }
            )
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = context.getString(R.string.theme_settings),
                style = MiuixTheme.textStyles.body1,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = context.getString(R.string.dynamic_color), style = MiuixTheme.textStyles.body1)
                        Text(
                            text = context.getString(R.string.dynamic_color_description),
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                    }
                    ExpressiveSwitch(
                        checked = useDynamicColor,
                        onCheckedChange = { enabled ->
                            themeSettings.value = ThemeSettings(
                                useDynamicColor = enabled,
                                primaryColor = themeSettings.value.primaryColor,
                                paletteStyle = themeSettings.value.paletteStyle
                            )
                            viewModel.toggleDynamicColor(enabled)
                        }
                    )
                }
            }
        }

        item {
            if (!useDynamicColor) {
                Spacer(modifier = Modifier.height(12.dp))
                ArrowPreference(
                    title = context.getString(R.string.manual_color_selection),
                    summary = context.getString(R.string.current_theme_color),
                    endActions = {
                        Surface(
                            shape = CircleShape,
                            color = if (themeSettings.value.primaryColor == 0) Color(0xFF4E84F7) else Color(themeSettings.value.primaryColor),
                            modifier = Modifier.size(24.dp),
                            border = BorderStroke(1.dp, MiuixTheme.colorScheme.outline)
                        ) {}
                    },
                    onClick = { showColorPicker = true }
                )
            }
        }

        item {
            LanguageSelectorBottomSheet(
                show = showLanguageBottomSheet,
                currentLanguage = currentLanguage,
                onLanguageSelected = { viewModel.setLanguage(it) },
                onDismiss = { showLanguageBottomSheet = false },
                context = context
            )

            ColorPickerBottomSheet(
                show = showColorPicker,
                currentColor = themeSettings.value.primaryColor,
                onColorSelected = { color ->
                    themeSettings.value = ThemeSettings(
                        useDynamicColor = false,
                        primaryColor = color,
                        paletteStyle = themeSettings.value.paletteStyle
                    )
                    viewModel.setPrimaryColor(color)
                },
                onDismiss = { showColorPicker = false },
                context = context
            )
        }
    }
}

@Composable
fun FeaturesSettingsPage(
    viewModel: SettingsViewModel,
    context: Context,
    paddingValues: PaddingValues
) {
    val listState = rememberLazyListState()
    RegisterScrollToTop(listState)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(
            top = paddingValues.calculateTopPadding() + 16.dp,
            bottom = 16.dp
        )
    ) {
        item {
            AISettingsSection(viewModel = viewModel, context = context)
            Spacer(modifier = Modifier.height(24.dp))
            BudgetSettingsSection(context = context)
        }
    }
}

@Composable
fun DataSyncSettingsPage(
    viewModel: SettingsViewModel,
    context: Context,
    onNavigateToLanSync: () -> Unit,
    paddingValues: PaddingValues
) {
    val listState = rememberLazyListState()
    RegisterScrollToTop(listState)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(
            top = paddingValues.calculateTopPadding() + 16.dp,
            bottom = 16.dp
        )
    ) {
        item {
            ServerConfigSection(viewModel = viewModel, context = context)
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))
            SyncSection(
                viewModel = viewModel,
                context = context,
                onNavigateToLanSync = onNavigateToLanSync
            )
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))
            DataImportExportSection(viewModel = viewModel, context = context)
        }
    }
}

@Composable
fun AboutSettingsPage(
    viewModel: SettingsViewModel,
    context: Context,
    onNavigateToOpenSourceLicenses: () -> Unit,
    onNavigateToDatabaseTest: () -> Unit,
    paddingValues: PaddingValues
) {
    val isDeveloperMode by viewModel.isDeveloperMode.collectAsState(initial = false)

    val listState = rememberLazyListState()
    RegisterScrollToTop(listState)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(
            top = paddingValues.calculateTopPadding() + 16.dp,
            bottom = 100.dp
        )
    ) {
        item {
            Text(
                text = context.getString(R.string.feedback_title),
                style = MiuixTheme.textStyles.body1,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            ArrowPreference(
                title = context.getString(R.string.feedback_title),
                summary = context.getString(R.string.feedback_description),
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW,
                            "https://wj.qq.com/s2/24109109/3572/".toUri())
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Browser Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = context.getString(R.string.open_source_licenses),
                style = MiuixTheme.textStyles.body1,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            ArrowPreference(
                title = context.getString(R.string.open_source_licenses),
                summary = context.getString(R.string.open_source_licenses_description),
                onClick = onNavigateToOpenSourceLicenses
            )
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = context.getString(R.string.developer_options),
                    style = MiuixTheme.textStyles.body1
                )
                ExpressiveSwitch(
                    checked = isDeveloperMode,
                    onCheckedChange = { viewModel.toggleDeveloperMode() }
                )
            }
        }

        item {
            if (isDeveloperMode) {
                Spacer(modifier = Modifier.height(8.dp))
                ArrowPreference(
                    title = context.getString(R.string.database_test),
                    summary = "Local database debugging",
                    onClick = onNavigateToDatabaseTest
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(48.dp))
            AppVersionInfo(context = context)
        }
    }
}

@Composable
fun AppVersionInfo(context: Context) {
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    val versionName = packageInfo.versionName
    val versionCode =
        packageInfo.longVersionCode

    Text(
        text = "Version $versionName ($versionCode)",
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onSurfaceSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp)
            .wrapContentWidth(Alignment.CenterHorizontally)
    )
}

fun openSystemAppLanguageSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        openAppInfoSettings(context)
    }
}

fun openAppInfoSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "Failed to open settings", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun AISettingsSection(
    viewModel: SettingsViewModel,
    context: Context
) {
    val modelState by viewModel.aiModelState.collectAsState()
    val sourcePref by viewModel.aiModelSourcePreference.collectAsState()
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Dropdown options, ordered to match SourcePreference.entries.
    val sourceItems = listOf(
        context.getString(R.string.settings_ai_model_source_auto),
        context.getString(R.string.settings_ai_model_source_modelscope),
        context.getString(R.string.settings_ai_model_source_huggingface)
    )

    Column {
        Text(
            text = context.getString(R.string.settings_ai_title),
            style = MiuixTheme.textStyles.body1,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        when (val state = modelState) {
            is com.chronie.homemoney.data.vlm.OnDeviceModelManager.ModelState.Ready -> {
                ArrowPreference(
                    title = context.getString(R.string.settings_ai_model),
                    summary = context.getString(
                        R.string.settings_ai_model_status_ready,
                        formatGigabytes(state.totalBytes)
                    ),
                    onClick = { showDeleteConfirmDialog = true }
                )
                Text(
                    text = context.getString(R.string.settings_ai_model_delete_hint),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                )
            }

            is com.chronie.homemoney.data.vlm.OnDeviceModelManager.ModelState.Downloading -> {
                ArrowPreference(
                    title = context.getString(R.string.settings_ai_model),
                    summary = context.getString(
                        R.string.settings_ai_model_status_downloading,
                        (state.progress * 100).toInt(),
                        formatGigabytes(state.downloadedBytes),
                        formatGigabytes(state.totalBytes)
                    ),
                    onClick = { viewModel.cancelAiModelDownload() }
                )
                Text(
                    text = context.getString(R.string.settings_ai_model_cancel_hint),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                )
            }

            is com.chronie.homemoney.data.vlm.OnDeviceModelManager.ModelState.Failed -> {
                ArrowPreference(
                    title = context.getString(R.string.settings_ai_model),
                    summary = context.getString(
                        R.string.settings_ai_model_status_failed,
                        state.reason
                    ),
                    onClick = { viewModel.downloadAiModel() }
                )
                WindowDropdownPreference(
                    items = sourceItems,
                    selectedIndex = sourcePref.ordinal,
                    title = context.getString(R.string.settings_ai_model_source),
                    onSelectedIndexChange = { index ->
                        viewModel.setAiModelSourcePreference(
                            com.chronie.homemoney.data.vlm.OnDeviceModelManager
                                .SourcePreference.entries[index]
                        )
                    }
                )
            }

            else -> {
                // NotDownloaded (or transient states): tap to start the download.
                ArrowPreference(
                    title = context.getString(R.string.settings_ai_model),
                    summary = context.getString(R.string.settings_ai_model_status_not_downloaded),
                    onClick = { viewModel.downloadAiModel() }
                )
                WindowDropdownPreference(
                    items = sourceItems,
                    selectedIndex = sourcePref.ordinal,
                    title = context.getString(R.string.settings_ai_model_source),
                    onSelectedIndexChange = { index ->
                        viewModel.setAiModelSourcePreference(
                            com.chronie.homemoney.data.vlm.OnDeviceModelManager
                                .SourcePreference.entries[index]
                        )
                    }
                )
                Text(
                    text = context.getString(R.string.settings_ai_model_description),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                )
            }
        }
    }

    // Delete confirmation — model files are gigabytes, so deletion is
    // gated behind an explicit confirm instead of a single tap.
    WindowDialog(
        show = showDeleteConfirmDialog,
        title = context.getString(R.string.settings_ai_model_delete_confirm_title),
        summary = context.getString(R.string.settings_ai_model_delete_confirm_message),
        onDismissRequest = { showDeleteConfirmDialog = false }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularIconButton(onClick = { showDeleteConfirmDialog = false }) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = context.getString(R.string.cancel),
                    tint = MiuixTheme.colorScheme.onBackground
                )
            }
            CircularIconButton(
                onClick = {
                    showDeleteConfirmDialog = false
                    viewModel.deleteAiModel()
                }
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = context.getString(R.string.confirm),
                    tint = MiuixTheme.colorScheme.error
                )
            }
        }
    }

}

/** Formats a byte count as a compact GB figure for settings summaries. */
private fun formatGigabytes(bytes: Long): String {
    return String.format(java.util.Locale.US, "%.1f GB", bytes / (1024f * 1024f * 1024f))
}

@Composable
fun BudgetSettingsSection(
    context: Context,
    budgetViewModel: com.chronie.homemoney.ui.budget.BudgetViewModel = hiltViewModel()
) {
    val uiState by budgetViewModel.uiState.collectAsState()
    var showBudgetDialog by remember { mutableStateOf(false) }
    val currencySymbol = rememberCurrencySymbol(context)

    Column {
        Text(
            text = context.getString(R.string.budget_settings),
            style = MiuixTheme.textStyles.body1,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        ArrowPreference(
            title = context.getString(R.string.budget_monthly_limit),
            summary = if (uiState.budget?.isEnabled == true) {
                "${context.getString(R.string.budget_enable_feature)}: " + context.getString(R.string.currency_format, currencySymbol, uiState.budget?.monthlyLimit ?: 0.0)
            } else {
                context.getString(R.string.budget_enable_title)
            },
            onClick = { showBudgetDialog = true }
        )
    }

    com.chronie.homemoney.ui.budget.BudgetSettingsDialog(
        show = showBudgetDialog,
        context = context,
        currentBudget = uiState.budget,
        onDismiss = { showBudgetDialog = false },
        onSave = { limit, threshold, enabled ->
            budgetViewModel.saveBudget(limit, threshold, enabled)
            showBudgetDialog = false
        }
    )
}

@Composable
fun DataImportExportSection(
    viewModel: SettingsViewModel,
    context: Context
) {
    val exportInProgress by viewModel.exportInProgress.collectAsState()
    val importInProgress by viewModel.importInProgress.collectAsState()
    var showExportDialog by remember { mutableStateOf(false) }
    var showDateRangeDialog by remember { mutableStateOf(false) }
    var startDate by remember { mutableStateOf<LocalDate?>(null) }
    var endDate by remember { mutableStateOf<LocalDate?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // On Android 14+ the user may grant partial photo access
        // (READ_MEDIA_VISUAL_USER_SELECTED) instead of full READ_MEDIA_IMAGES;
        // either grant is sufficient.
        if (permissions.values.none { it }) {
            Toast.makeText(context, context.getString(R.string.permission_storage_required), Toast.LENGTH_LONG).show()
        }
    }

    fun checkAndRequestPermissions(onGranted: () -> Unit) {
        val permissions = arrayOf(
            android.Manifest.permission.READ_MEDIA_IMAGES,
            android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
        val hasAccess = permissions.any { androidx.core.content.ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED }
        if (hasAccess) onGranted() else permissionLauncher.launch(permissions)
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { viewModel.importExpenses(it) } }

    Column {
        Text(
            text = context.getString(R.string.data_import_export),
            style = MiuixTheme.textStyles.body1,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Button(
            onClick = { checkAndRequestPermissions { showExportDialog = true } },
            modifier = Modifier.fillMaxWidth(),
            enabled = !exportInProgress && !importInProgress,
            colors = ButtonDefaults.buttonColorsPrimary()
        ) {
            if (exportInProgress) {
                ExpressiveLoadingIndicator(containerVisible = false)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(text = if (exportInProgress) context.getString(R.string.export_in_progress) else context.getString(R.string.export_data))
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { checkAndRequestPermissions { filePickerLauncher.launch("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") } },
            modifier = Modifier.fillMaxWidth(),
            enabled = !exportInProgress && !importInProgress,
            colors = ButtonDefaults.buttonColorsPrimary()
        ) {
            if (importInProgress) {
                ExpressiveLoadingIndicator(containerVisible = false)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(text = if (importInProgress) context.getString(R.string.import_in_progress) else context.getString(R.string.import_data))
        }
    }

    WindowDialog(
        show = showExportDialog,
        title = context.getString(R.string.export_data),
        onDismissRequest = { showExportDialog = false }
    ) {
        Column {
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { showExportDialog = false; viewModel.exportExpenses(null, null) },
                color = MiuixTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            ) { Text(text = context.getString(R.string.export_all_data), modifier = Modifier.padding(16.dp)) }
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { showExportDialog = false; showDateRangeDialog = true },
                color = MiuixTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            ) { Text(text = context.getString(R.string.export_date_range), modifier = Modifier.padding(16.dp)) }
        }
    }

    var showStartDatePicker by remember(showDateRangeDialog) { mutableStateOf(false) }
    var showEndDatePicker by remember(showDateRangeDialog) { mutableStateOf(false) }
    WindowDialog(
        show = showDateRangeDialog,
        title = context.getString(R.string.export_select_range),
        onDismissRequest = { showDateRangeDialog = false }
    ) {
        Column {
            Text(text = context.getString(R.string.export_start_date), modifier = Modifier.padding(bottom = 4.dp))
            Surface(modifier = Modifier.fillMaxWidth().clickable { showStartDatePicker = true }, color = MiuixTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
                Text(text = startDate?.let { formatDateByLocale(it.toString(), context.resources.configuration.locales[0].toLanguageTag()) } ?: context.getString(R.string.export_start_date), modifier = Modifier.padding(16.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = context.getString(R.string.export_end_date), modifier = Modifier.padding(bottom = 4.dp))
            Surface(modifier = Modifier.fillMaxWidth().clickable { showEndDatePicker = true }, color = MiuixTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
                Text(text = endDate?.let { formatDateByLocale(it.toString(), context.resources.configuration.locales[0].toLanguageTag()) } ?: context.getString(R.string.export_end_date), modifier = Modifier.padding(16.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Top header with X (cancel) and Check (export) icons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularIconButton(onClick = { showDateRangeDialog = false }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = context.getString(R.string.cancel),
                        tint = MiuixTheme.colorScheme.onBackground
                    )
                }
                CircularIconButton(
                    onClick = { showDateRangeDialog = false; viewModel.exportExpenses(startDate, endDate) },
                    enabled = startDate != null && endDate != null
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = context.getString(R.string.export_data),
                        tint = MiuixTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    MiuixDatePickerSheet(
        context = context,
        show = showStartDatePicker,
        initialDate = startDate ?: LocalDate.now(),
        onDismiss = { showStartDatePicker = false },
        onDateSelected = { startDate = it; showStartDatePicker = false },
        title = context.getString(R.string.export_start_date)
    )

    MiuixDatePickerSheet(
        context = context,
        show = showEndDatePicker,
        initialDate = endDate ?: LocalDate.now(),
        onDismiss = { showEndDatePicker = false },
        onDateSelected = { endDate = it; showEndDatePicker = false },
        title = context.getString(R.string.export_end_date)
    )
}

@Composable
fun ServerConfigSection(
    viewModel: SettingsViewModel,
    context: Context
) {
    val serverBaseUrl by viewModel.serverBaseUrl.collectAsState()
    val isCustom by viewModel.isUsingCustomServer.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    Column {
        Text(
            text = context.getString(R.string.server_settings),
            style = MiuixTheme.textStyles.body1,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        ArrowPreference(
            title = context.getString(R.string.server_address),
            summary = serverBaseUrl,
            onClick = { showDialog = true }
        )

        if (isCustom) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = context.getString(R.string.server_custom_hint),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
        }
    }

    ServerConfigDialog(
        show = showDialog,
        viewModel = viewModel,
        context = context,
        currentUrl = serverBaseUrl,
        onDismiss = { showDialog = false }
    )
}

@Composable
fun ServerConfigDialog(
    show: Boolean,
    viewModel: SettingsViewModel,
    context: Context,
    currentUrl: String,
    onDismiss: () -> Unit
) {
    var input by remember(show, currentUrl) { mutableStateOf(currentUrl) }
    val testState by viewModel.serverTestState.collectAsState()

    // Android 17 LNP guard: ACCESS_LOCAL_NETWORK is required before any LAN
    // access. If the user refuses we show an explicit hint rather than failing
    // with a silent socket timeout.
    val lanPermissionRequester = rememberLocalNetworkPermissionRequester(
        onDenied = {
            Toast.makeText(context, R.string.lan_permission_denied, Toast.LENGTH_LONG).show()
        }
    )

    WindowDialog(
        show = show,
        title = context.getString(R.string.server_address),
        onDismissRequest = {
            viewModel.clearServerTestState()
            onDismiss()
        }
    ) {
        Column {
            Text(
                text = context.getString(R.string.server_address_hint),
                style = MiuixTheme.textStyles.body2,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            TextField(
                value = input,
                onValueChange = {
                    input = it
                    if (testState !is ServerTestUiState.Idle) viewModel.clearServerTestState()
                },
                modifier = Modifier.fillMaxWidth(),
                label = context.getString(R.string.server_address_hint),
                useLabelAsPlaceholder = true,
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { viewModel.testServerConnection(input) },
                modifier = Modifier.fillMaxWidth(),
                enabled = testState !is ServerTestUiState.Testing && input.isNotBlank(),
                colors = ButtonDefaults.buttonColorsPrimary()
            ) {
                if (testState is ServerTestUiState.Testing) {
                    ExpressiveLoadingIndicator(containerVisible = false)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(text = context.getString(R.string.server_test_connection))
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (val state = testState) {
                is ServerTestUiState.Reachable -> Text(
                    text = context.getString(R.string.server_test_reachable, state.latencyMs),
                    color = MiuixTheme.colorScheme.primary,
                    style = MiuixTheme.textStyles.body2
                )
                is ServerTestUiState.Failed -> Text(
                    text = state.message,
                    color = MiuixTheme.colorScheme.error,
                    style = MiuixTheme.textStyles.body2
                )
                else -> {}
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    text = context.getString(R.string.server_reset_default),
                    onClick = {
                        viewModel.resetServerUrl()
                        viewModel.clearServerTestState()
                        onDismiss()
                    }
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularIconButton(
                        onClick = {
                            viewModel.clearServerTestState()
                            onDismiss()
                        }
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = context.getString(R.string.cancel),
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    CircularIconButton(
                        onClick = {
                            lanPermissionRequester.ensure {
                                if (viewModel.saveServerUrl(input)) {
                                    viewModel.clearServerTestState()
                                    onDismiss()
                                }
                            }
                        },
                        enabled = input.isNotBlank()
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = context.getString(R.string.save),
                            tint = MiuixTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SyncSection(
    viewModel: SettingsViewModel,
    context: Context,
    onNavigateToLanSync: () -> Unit = {}
) {
    val syncStatus by viewModel.syncStatus.collectAsState()
    val lastSyncTime by viewModel.lastSyncTime.collectAsState()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()
    var showSyncMethodDialog by remember { mutableStateOf(false) }

    syncMessage?.let { message ->
        LaunchedEffect(message) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            kotlinx.coroutines.delay(3000.milliseconds)
            viewModel.clearSyncMessage()
        }
    }

    Column {
        Text(text = context.getString(R.string.sync_title), style = MiuixTheme.textStyles.body1, modifier = Modifier.padding(bottom = 8.dp))
        Surface(modifier = Modifier.fillMaxWidth(), color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = context.getString(R.string.sync_status))
                    Text(
                        text = when (syncStatus) {
                            com.chronie.homemoney.domain.model.SyncStatus.IDLE -> context.getString(R.string.sync_status_idle)
                            com.chronie.homemoney.domain.model.SyncStatus.SYNCING -> context.getString(R.string.sync_status_syncing)
                            com.chronie.homemoney.domain.model.SyncStatus.SUCCESS -> context.getString(R.string.sync_status_success)
                            com.chronie.homemoney.domain.model.SyncStatus.FAILED -> context.getString(R.string.sync_status_failed)
                            com.chronie.homemoney.domain.model.SyncStatus.CONFLICT -> context.getString(R.string.sync_status_conflict)
                        },
                        color = when (syncStatus) {
                            com.chronie.homemoney.domain.model.SyncStatus.SUCCESS -> MiuixTheme.colorScheme.primary
                            com.chronie.homemoney.domain.model.SyncStatus.FAILED, com.chronie.homemoney.domain.model.SyncStatus.CONFLICT -> MiuixTheme.colorScheme.error
                            else -> MiuixTheme.colorScheme.onSurfaceSecondary
                        }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = context.getString(R.string.sync_last_time))
                    Text(text = lastSyncTime ?: context.getString(R.string.sync_never), style = MiuixTheme.textStyles.footnote1)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = context.getString(R.string.sync_pending_count))
                    Text(
                        text = pendingSyncCount.toString(),
                        color = if (pendingSyncCount > 0) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { showSyncMethodDialog = true }, modifier = Modifier.fillMaxWidth(), enabled = syncStatus != com.chronie.homemoney.domain.model.SyncStatus.SYNCING, colors = ButtonDefaults.buttonColorsPrimary()) {
                    if (syncStatus == com.chronie.homemoney.domain.model.SyncStatus.SYNCING) {
                        ExpressiveLoadingIndicator(containerVisible = false)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(text = if (syncStatus == com.chronie.homemoney.domain.model.SyncStatus.SYNCING) context.getString(R.string.sync_syncing) else context.getString(R.string.sync_manual_trigger))
                }
            }
        }
    }

    WindowDialog(
        show = showSyncMethodDialog,
        title = context.getString(R.string.sync_select_method),
        onDismissRequest = { showSyncMethodDialog = false }
    ) {
        Column {
            Surface(modifier = Modifier.fillMaxWidth().clickable { showSyncMethodDialog = false; viewModel.manualSync() }, color = MiuixTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
                Text(text = context.getString(R.string.sync_cloud), modifier = Modifier.padding(16.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Surface(modifier = Modifier.fillMaxWidth().clickable { showSyncMethodDialog = false; onNavigateToLanSync() }, color = MiuixTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
                Text(text = context.getString(R.string.sync_lan), modifier = Modifier.padding(16.dp))
            }
        }
    }
}

@Composable
fun AccountSection(
    viewModel: SettingsViewModel,
    context: Context,
    onLogout: () -> Unit,
    onRequireLogin: () -> Unit = {}
) {
    val currentUsername by viewModel.currentUsername.collectAsState()
    val avatar by viewModel.avatar.collectAsState()
    val avatarLoading by viewModel.avatarLoading.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.logoutEvent.collect { onLogout() } }

    var avatarEditUri by remember { mutableStateOf<Uri?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) avatarEditUri = uri
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.padding(bottom = 20.dp), contentAlignment = Alignment.Center) {
                Surface(modifier = Modifier.size(140.dp), shape = CircleShape, color = MiuixTheme.colorScheme.surfaceVariant, shadowElevation = 4.dp) {}
                Surface(modifier = Modifier.size(132.dp), shape = CircleShape, color = MiuixTheme.colorScheme.surface, border = BorderStroke(3.dp, MiuixTheme.colorScheme.outline)) {}
                Box(modifier = Modifier.size(120.dp).clickable { imagePickerLauncher.launch("image/*") }) {
                    if (avatarLoading) ExpressiveLoadingIndicator(containerVisible = false)
                    else if (avatar != null) {
                        AsyncImage(model = avatar, contentDescription = null, modifier = Modifier.size(120.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                    } else {
                        Surface(modifier = Modifier.size(120.dp), color = MiuixTheme.colorScheme.primaryContainer, shape = CircleShape) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(48.dp), tint = MiuixTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
            Text(text = currentUsername ?: context.getString(R.string.auth_not_logged_in), style = MiuixTheme.textStyles.title3, fontWeight = FontWeight.Medium)
            Text(text = context.getString(R.string.auth_current_user), style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceSecondary, modifier = Modifier.padding(top = 4.dp))
            Spacer(modifier = Modifier.height(20.dp))
            if (currentUsername != null) {
                Button(onClick = { showLogoutDialog = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.errorContainer, contentColor = MiuixTheme.colorScheme.onErrorContainer)) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(context.getString(R.string.auth_logout_button))
                }
            } else {
                Button(onClick = { viewModel.clearSkippedLogin(); onRequireLogin() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColorsPrimary()) {
                    Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(context.getString(R.string.auth_login_button))
                }
            }
        }
    }

    WindowDialog(
        show = showLogoutDialog,
        title = context.getString(R.string.auth_logout_confirm_title),
        summary = context.getString(R.string.auth_logout_confirm_message),
        onDismissRequest = { showLogoutDialog = false }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularIconButton(onClick = { showLogoutDialog = false }) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = context.getString(R.string.cancel),
                    tint = MiuixTheme.colorScheme.onBackground
                )
            }
            CircularIconButton(onClick = { viewModel.logout(); showLogoutDialog = false }) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = context.getString(R.string.confirm),
                    tint = MiuixTheme.colorScheme.error
                )
            }
        }
    }

    ImageEditorDialog(
        uri = avatarEditUri,
        cropShape = CropShape.CIRCLE,
        enableEraser = false,
        maxResultSize = 256,
        onDismiss = { avatarEditUri = null },
        onConfirm = { bmp ->
            try {
                val bytes = compressBitmapToBytes(bmp, 10 * 1024 * 1024, android.graphics.Bitmap.CompressFormat.PNG, 100)
                val base64String = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
                viewModel.updateAvatar("data:image/png;base64,$base64String")
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.crop_image_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
            }
            avatarEditUri = null
        }
    )
}
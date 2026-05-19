// allow-bypass-D5: defensive Pager animateScrollToPage wrap; failure is a no-op tap
package com.tmuxes.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.tmuxes.ui.components.app.AppBottomBar
import com.tmuxes.ui.components.app.AppNavEntry
import com.tmuxes.ui.components.app.AppNavigationDrawer
import com.tmuxes.ui.components.app.AppNavigationRail
import com.tmuxes.ui.design.LocalNavigationStyle
import com.tmuxes.ui.design.appTokens
import com.tmuxes.ui.screens.servers.ServerListScreen
import com.tmuxes.ui.screens.sessions.SessionPickerScreen
import com.tmuxes.ui.screens.settings.SettingsScreen
import kotlinx.coroutines.launch

/**
 * Host for the three top-level tabs (Servers / Sessions / Settings).
 *
 * Three navigation modes (driven by `app.navigation_style`):
 * - **bottom_bar** (default) — bottom NavigationBar
 * - **rail** — vertical NavigationRail on the left
 * - **drawer** — ModalNavigationDrawer
 *
 * **NOT a Scaffold.** HomeScreen is a pure layout container. Each tab's
 * inner screen (`ServerListScreen` / `SessionPickerScreen` /
 * `SettingsScreen`) owns its own `AppScaffold` — that's the single,
 * sole Scaffold per screen tree, and the sole owner of system-bars
 * insets. `AppBottomBar` separately owns its `WindowInsets.navigationBars`.
 *
 * The drill-down stack (server detail, sessions, settings sub-pages,
 * etc.) is on the outer NavHost — not affected by which mode is in use.
 */
@Composable
fun HomeScreen(
    initialPage: Int = 0,
    onAddServer: () -> Unit,
    onServerClick: (Long) -> Unit,
    onEditServer: (Long) -> Unit,
    onOpenTerminal: (Long, String) -> Unit,
    onNavigateToAppAppearance: () -> Unit,
    onNavigateToTerminalAppearance: () -> Unit,
    onNavigateToTerminalInput: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToSshConnection: () -> Unit,
    onNavigateToShellSession: () -> Unit,
    onNavigateToSecurityKeys: () -> Unit,
    onNavigateToSnippets: () -> Unit,
    onNavigateToYamlEditor: () -> Unit,
    onNavigateToDebugLog: () -> Unit = {}
) {
    var savedPage by rememberSaveable {
        mutableIntStateOf(initialPage.coerceIn(0, 2))
    }
    val pagerState = rememberPagerState(
        initialPage = savedPage,
        pageCount = { 3 }
    )
    val scope = rememberCoroutineScope()
    val navigationStyle = LocalNavigationStyle.current
    val tokens = MaterialTheme.appTokens

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            savedPage = page
        }
    }

    val selectedKey = TAB_KEYS[pagerState.currentPage]
    val entries = TAB_KEYS.mapIndexed { index, key ->
        AppNavEntry(
            key = key,
            label = TAB_LABELS[index],
            icon = TAB_ICONS[index],
            onSelect = {
                scope.launch {
                    try { pagerState.animateScrollToPage(index) } catch (_: Throwable) {}
                }
            }
        )
    }

    val pagerBody: @Composable BoxScope.() -> Unit = {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 0
        ) { page ->
            when (page) {
                0 -> ServerListScreen(
                    onAddServer = onAddServer,
                    onServerClick = onServerClick,
                    onEditServer = onEditServer
                )
                1 -> SessionPickerScreen(
                    serverId = null,
                    onNavigateBack = { /* unused in tab mode */ },
                    onOpenTerminal = onOpenTerminal,
                    onNavigateToServerDetail = onServerClick
                )
                2 -> SettingsScreen(
                    onNavigateToAppAppearance = onNavigateToAppAppearance,
                    onNavigateToTerminalAppearance = onNavigateToTerminalAppearance,
                    onNavigateToTerminalInput = onNavigateToTerminalInput,
                    onNavigateToNotifications = onNavigateToNotifications,
                    onNavigateToSshConnection = onNavigateToSshConnection,
                    onNavigateToShellSession = onNavigateToShellSession,
                    onNavigateToSecurityKeys = onNavigateToSecurityKeys,
                    onNavigateToSnippets = onNavigateToSnippets,
                    onNavigateToYamlEditor = onNavigateToYamlEditor,
                    onNavigateToDebugLog = onNavigateToDebugLog
                )
            }
        }
    }

    when (navigationStyle) {
        "drawer" -> {
            val drawerState = rememberDrawerState(DrawerValue.Closed)
            AppNavigationDrawer(
                entries = entries,
                selectedKey = selectedKey,
                drawerState = drawerState
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(tokens.colors.background),
                    content = pagerBody
                )
            }
        }
        "rail" -> {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(tokens.colors.background)
            ) {
                AppNavigationRail(entries = entries, selectedKey = selectedKey)
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    content = pagerBody
                )
            }
        }
        else -> {
            // bottom_bar (default)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(tokens.colors.background)
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    content = pagerBody
                )
                AppBottomBar(entries = entries, selectedKey = selectedKey)
            }
        }
    }
}

private val TAB_KEYS = listOf("servers", "sessions", "settings")
private val TAB_LABELS = listOf("Servers", "Sessions", "Settings")
private val TAB_ICONS = listOf(Icons.Filled.Dns, Icons.Filled.Terminal, Icons.Filled.Settings)

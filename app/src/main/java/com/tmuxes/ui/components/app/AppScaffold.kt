package com.tmuxes.ui.components.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.tmuxes.ui.design.appTokens

/**
 * Universal screen scaffold. The single Scaffold per screen tree —
 * everything above it (NavHost, HomeScreen tab host, drawer/rail)
 * is a non-Scaffold layout that does NOT consume insets. AppScaffold
 * is the sole owner of `WindowInsets.systemBars` for the screen.
 *
 * `topBar` is an optional escape hatch — pass non-null to fully
 * replace the default [AppTopBar] (used by SnippetsScreen /
 * LibraryDetailScreen for multi-select mode). When null, the default
 * AppTopBar is built from [title] / [subtitle] / [onBack] / [actions].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    titleIcon: ImageVector? = null,
    titleMeta: String? = null,
    subtitle: String? = null,
    actions: @Composable () -> Unit = {},
    fab: @Composable () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    topBar: (@Composable () -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val tokens = MaterialTheme.appTokens
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = tokens.colors.background,
        contentColor = tokens.colors.onBackground,
        topBar = topBar ?: {
            AppTopBar(
                title = title,
                titleIcon = titleIcon,
                titleMeta = titleMeta,
                subtitle = subtitle,
                navigation = if (onBack != null) AppTopBarNav.Back else AppTopBarNav.None,
                onNavClick = onBack,
                actions = actions
            )
        },
        bottomBar = bottomBar,
        floatingActionButton = fab,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding -> content(padding) }
}

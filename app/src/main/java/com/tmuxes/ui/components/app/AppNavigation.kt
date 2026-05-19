package com.tmuxes.ui.components.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.tmuxes.i18n.t
import com.tmuxes.ui.design.appTokens
import kotlinx.coroutines.launch

/**
 * Navigation entry common to bottom-bar / rail / drawer renderings.
 */
data class AppNavEntry(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val onSelect: () -> Unit
)

/**
 * Bottom navigation bar — token-driven colors.
 *
 * Total height = 64dp content + the device's navigationBars inset
 * (e.g., 0dp on gesture-nav devices, ~24dp on 3-button devices).
 * The M3 default of 80dp content was too thick at this density;
 * 64dp keeps the indicator pill (32dp) + icon + label readable
 * with normal Material padding.
 *
 * Explicit `windowInsets = WindowInsets.navigationBars` makes this
 * bar the sole owner of the bottom system inset — the surrounding
 * layout (HomeScreen) does NOT apply navigation-bar padding so the
 * inset is consumed exactly once.
 */
@Composable
fun AppBottomBar(
    entries: List<AppNavEntry>,
    selectedKey: String,
    modifier: Modifier = Modifier
) {
    val tokens = MaterialTheme.appTokens
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    NavigationBar(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp + navInset),
        containerColor = tokens.colors.surface,
        contentColor = tokens.colors.onSurface,
        windowInsets = WindowInsets.navigationBars,
        tonalElevation = 0.dp
    ) {
        entries.forEach { entry ->
            val label = t(entry.label)
            NavigationBarItem(
                selected = entry.key == selectedKey,
                onClick = entry.onSelect,
                icon = { Icon(entry.icon, contentDescription = label) },
                label = { Text(label, style = tokens.type.labelMedium) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = tokens.colors.onPrimaryContainer,
                    selectedTextColor = tokens.colors.primary,
                    indicatorColor = tokens.colors.primaryContainer,
                    unselectedIconColor = tokens.colors.onSurfaceVariant,
                    unselectedTextColor = tokens.colors.onSurfaceVariant
                )
            )
        }
    }
}

/**
 * Vertical navigation rail (tablets / large phones in landscape).
 * Caller is responsible for the `Row { rail; body }` outer layout.
 */
@Composable
fun AppNavigationRail(
    entries: List<AppNavEntry>,
    selectedKey: String,
    modifier: Modifier = Modifier
) {
    val tokens = MaterialTheme.appTokens
    NavigationRail(
        modifier = modifier.fillMaxHeight(),
        containerColor = tokens.colors.surface,
        contentColor = tokens.colors.onSurface
    ) {
        entries.forEach { entry ->
            val label = t(entry.label)
            NavigationRailItem(
                selected = entry.key == selectedKey,
                onClick = entry.onSelect,
                icon = { Icon(entry.icon, contentDescription = label) },
                label = { Text(label, style = tokens.type.labelMedium) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = tokens.colors.onPrimaryContainer,
                    selectedTextColor = tokens.colors.primary,
                    indicatorColor = tokens.colors.primaryContainer,
                    unselectedIconColor = tokens.colors.onSurfaceVariant,
                    unselectedTextColor = tokens.colors.onSurfaceVariant
                )
            )
        }
    }
}

/**
 * Modal navigation drawer — finally a real implementation. Caller wraps
 * the screen body in [content], plus passes drawer entries. The drawer
 * itself opens on swipe-from-edge or via the [drawerState] handle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigationDrawer(
    entries: List<AppNavEntry>,
    selectedKey: String,
    modifier: Modifier = Modifier,
    drawerState: androidx.compose.material3.DrawerState = rememberDrawerState(DrawerValue.Closed),
    headerTitle: String = "tmuxes",
    content: @Composable () -> Unit
) {
    val tokens = MaterialTheme.appTokens
    val scope = rememberCoroutineScope()
    ModalNavigationDrawer(
        drawerState = drawerState,
        modifier = modifier,
        scrimColor = tokens.colors.dimmer,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = tokens.colors.surfaceContainer,
                drawerContentColor = tokens.colors.onSurface,
                drawerShape = tokens.shape.lg
            ) {
                Column(modifier = Modifier.fillMaxHeight().padding(tokens.space.lg)) {
                    Text(
                        text = headerTitle,
                        style = tokens.type.titleLarge,
                        color = tokens.colors.primary,
                        modifier = Modifier.padding(bottom = tokens.space.xl)
                    )
                    entries.forEach { entry ->
                        val label = t(entry.label)
                        NavigationDrawerItem(
                            selected = entry.key == selectedKey,
                            onClick = {
                                entry.onSelect()
                                scope.launch { drawerState.close() }
                            },
                            icon = { Icon(entry.icon, contentDescription = null) },
                            label = { Text(label, style = tokens.type.labelLarge) },
                            shape = tokens.shape.md,
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = tokens.colors.primaryContainer,
                                unselectedContainerColor = tokens.colors.surfaceContainer,
                                selectedIconColor = tokens.colors.onPrimaryContainer,
                                unselectedIconColor = tokens.colors.onSurfaceVariant,
                                selectedTextColor = tokens.colors.onPrimaryContainer,
                                unselectedTextColor = tokens.colors.onSurfaceVariant
                            )
                        )
                        Spacer(Modifier.height(tokens.space.xs))
                    }
                }
            }
        },
        content = content
    )
}

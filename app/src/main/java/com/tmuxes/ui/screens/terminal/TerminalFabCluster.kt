package com.tmuxes.ui.screens.terminal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tmuxes.ui.components.app.AppFab
import com.tmuxes.ui.components.app.AppFabBubble
import com.tmuxes.ui.components.app.AppFabBubbleController
import com.tmuxes.ui.components.app.AppFabSize
import com.tmuxes.ui.design.appTokens

/** Bubble id for the Command Panel — the only bubble in the terminal cluster. */
const val TERMINAL_BUBBLE_COMMAND_PANEL = "terminal.commandPanel"

/**
 * The Terminal screen's bottom-end FAB cluster: optional Configure-Widget
 * FAB (only when this session is bound to a home-screen widget) and a
 * Command Panel FAB whose bubble hosts snippets / clipboards / history.
 *
 * Drop into a Box (typically the screen-level Box that also hosts the
 * terminal grid). Renders both the FAB column and the bubble overlay.
 *
 * The Command Panel bubble is **always composed** so its inner state
 * (search text, scroll position, expanded library) survives open/close.
 * Visibility is gated by [controller], which is hoisted so callers
 * (e.g. the tab strip's "+ add session" button) can programmatically
 * open the panel via `controller.open(TERMINAL_BUBBLE_COMMAND_PANEL)`.
 *
 * Same pattern as `EditorFabCluster` — both terminal and editor share
 * the [AppFabBubble] visual contract (translucent surface, bottom-end
 * aligned with the FAB column, content-driven height, multi-tap safe).
 *
 * Configure-Widget is intentionally NOT a bubble — it's a direct action
 * (launch [com.tmuxes.widget.WidgetConfigActivity]).
 */
@Composable
fun BoxScope.TerminalFabCluster(
    controller: AppFabBubbleController,
    widgetId: Int?,
    onConfigureWidget: (Int) -> Unit,
    isCopyModeActive: Boolean,
    onToggleCopyMode: () -> Unit,
    modifier: Modifier = Modifier,
    commandPanel: @Composable () -> Unit
) {
    val tokens = MaterialTheme.appTokens

    // Bubble — always composed; visibility-gated by controller.
    AppFabBubble(
        visible = controller.isOpen(TERMINAL_BUBBLE_COMMAND_PANEL),
        onDismiss = controller::close,
        maxWidth = 520.dp,
        maxHeight = 600.dp
    ) {
        commandPanel()
    }

    // FAB column — bottom-end of parent Box.
    Column(
        modifier = modifier
            .align(Alignment.BottomEnd)
            .padding(tokens.space.md),
        verticalArrangement = Arrangement.spacedBy(tokens.space.sm)
    ) {
        if (widgetId != null) {
            AppFab(
                icon = Icons.Filled.Settings,
                onClick = {
                    try { onConfigureWidget(widgetId) } catch (_: Throwable) {} // allow-bypass-D5: caller lambda
                },
                contentDescription = "Configure Widget",
                size = AppFabSize.Small
            )
        }
        AppFab(
            icon = Icons.Filled.Code,
            onClick = { controller.toggle(TERMINAL_BUBBLE_COMMAND_PANEL) },
            contentDescription = "Command Panel",
            selected = controller.isOpen(TERMINAL_BUBBLE_COMMAND_PANEL),
            size = AppFabSize.Small
        )
        // Edit mode (tmux copy-mode): toggle FAB. Sends `prefix [` on enter,
        // ESC on exit. State drift is recoverable — one extra tap resyncs.
        AppFab(
            icon = Icons.Filled.Visibility,
            onClick = onToggleCopyMode,
            contentDescription = if (isCopyModeActive) "Exit Edit Mode" else "Enter Edit Mode",
            selected = isCopyModeActive,
            size = AppFabSize.Small
        )
    }
}

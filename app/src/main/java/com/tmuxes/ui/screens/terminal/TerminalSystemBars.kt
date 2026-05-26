package com.tmuxes.ui.screens.terminal

import android.app.Activity
import android.view.View
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

internal enum class TerminalStatusBarAreaBehavior(val key: String) {
    Reserve("reserve"),
    DrawBehind("draw_behind");

    companion object {
        fun fromKey(key: String): TerminalStatusBarAreaBehavior =
            entries.firstOrNull { it.key == key } ?: Reserve
    }
}

internal fun terminalStatusBarReservesSafeArea(behaviorKey: String): Boolean =
    TerminalStatusBarAreaBehavior.fromKey(behaviorKey) == TerminalStatusBarAreaBehavior.Reserve

@Composable
internal fun Modifier.terminalStatusBarAreaPadding(behaviorKey: String): Modifier =
    if (terminalStatusBarReservesSafeArea(behaviorKey)) {
        windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
    } else {
        this
    }

@Composable
internal fun TerminalSystemStatusBarEffect(
    showStatusBar: Boolean,
    view: View
) {
    DisposableEffect(showStatusBar, view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        try {
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (showStatusBar) {
                controller?.show(WindowInsetsCompat.Type.statusBars())
            } else {
                controller?.hide(WindowInsetsCompat.Type.statusBars())
            }
        } catch (_: Throwable) { // allow-bypass-D5: system-bar control is best-effort and must not crash composition
        }
        onDispose {
            try {
                controller?.show(WindowInsetsCompat.Type.statusBars())
            } catch (_: Throwable) { // allow-bypass-D5: cleanup restore is best-effort during activity teardown
            }
        }
    }
}

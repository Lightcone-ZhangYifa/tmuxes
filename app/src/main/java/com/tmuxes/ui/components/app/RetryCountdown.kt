package com.tmuxes.ui.components.app

import android.os.SystemClock
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.tmuxes.i18n.t
import com.tmuxes.ui.design.appTokens
import kotlinx.coroutines.delay

/**
 * Live-ticking remaining-seconds for a backoff timer.
 *
 * - `seconds == null` — no scheduled retry (`nextRetryAt` was null)
 * - `seconds == 0`    — deadline passed, the supervisor will fire any moment
 * - `seconds  > 0`    — recomposes once per second; stops at 0 (zero idle wakes)
 *
 * `peakSeconds` is kept for non-UI diagnostics/tests. The user-facing
 * presentation is an unobtrusive status chip, not a progress bar.
 *
 * The internal ticker restarts whenever [nextRetryAt] changes (each new
 * backoff round). Compose cancels the `LaunchedEffect` automatically when
 * the host composable leaves composition — no leaks, no idle CPU.
 */
data class RetryCountdownState(val seconds: Int?, val peakSeconds: Int)

@Composable
fun rememberRetryCountdown(nextRetryAt: Long?): RetryCountdownState {
    if (nextRetryAt == null) return RetryCountdownState(null, 0)
    val initial = remainingSeconds(nextRetryAt)
    var seconds by remember(nextRetryAt) { mutableIntStateOf(initial) }
    val peak = remember(nextRetryAt) { initial }
    LaunchedEffect(nextRetryAt) {
        while (seconds > 0) {
            delay(1_000L)
            seconds = remainingSeconds(nextRetryAt)
        }
    }
    return RetryCountdownState(seconds, peak)
}

internal fun remainingSeconds(nextRetryAt: Long): Int {
    val remaining = (nextRetryAt - SystemClock.elapsedRealtime()) / 1000
    return remaining.toInt().coerceAtLeast(0)
}

/**
 * Unified retry-countdown label used everywhere a scheduled retry hint
 * surfaces (SessionPicker row, TerminalScreen ConnectionLostBanner, Widget
 * config error state). Always pair with `nextRetryAt` from
 * [com.tmuxes.ssh.ServerConnectionState] so all retry surfaces share the same
 * monotonic clock source.
 *
 * @param verbose when true, formats as
 *   `"Reconnect #N · Ms"` (used in TerminalScreen banner);
 *   when false, formats as `"Auto retry · Ns"` (used in row hints).
 */
@Composable
fun RetryCountdownLabel(
    nextRetryAt: Long?,
    retryCount: Int = 0,
    verbose: Boolean = false,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.appTokens.type.labelSmall,
    color: Color = MaterialTheme.appTokens.colors.onSurfaceVariant
) {
    val tokens = MaterialTheme.appTokens
    val state = rememberRetryCountdown(nextRetryAt)
    val secs = state.seconds
    val text = when {
        secs == null || secs == 0 -> t(if (verbose) "Connection lost · reconnecting…" else "Reconnecting…")
        verbose && retryCount > 0 -> t(
            "Reconnect #{count} · {seconds}s",
            "count" to retryCount,
            "seconds" to secs
        )
        verbose -> t("Reconnect · {seconds}s", "seconds" to secs)
        else -> t("Auto retry · {seconds}s", "seconds" to secs)
    }
    Surface(
        modifier = modifier,
        shape = tokens.shape.pill,
        color = tokens.colors.surfaceContainerHigh,
        contentColor = color
    ) {
        Row(
            modifier = Modifier.padding(horizontal = tokens.space.sm, vertical = tokens.space.xs),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(tokens.space.md)
            )
            Spacer(modifier = Modifier.width(tokens.space.xs))
            Text(
                text = text,
                style = style,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

internal fun formatCountdownText(
    seconds: Int?,
    retryCount: Int,
    verbose: Boolean
): String = when {
    seconds == null -> if (verbose) "Connection lost · reconnecting…" else "Reconnecting…"
    seconds == 0 -> if (verbose) "Connection lost · reconnecting…" else "Reconnecting…"
    verbose && retryCount > 0 -> "Reconnect #$retryCount · ${seconds}s"
    verbose -> "Reconnect · ${seconds}s"
    else -> "Auto retry · ${seconds}s"
}

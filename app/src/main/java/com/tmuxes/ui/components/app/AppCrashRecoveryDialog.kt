// allow-bypass-D5: crash-recovery UI; logging from inside crash UI risks recursion (CrashLogWriter is the logger)
package com.tmuxes.ui.components.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import com.tmuxes.i18n.t
import com.tmuxes.ui.design.appTokens
import com.tmuxes.util.CrashLogWriter

/**
 * Shown once on app launch if a crash from a previous session was
 * detected via [CrashLogWriter.hasUnacknowledgedCrashes]. The user
 * can Share / Copy the trace then dismiss, or just Dismiss.
 *
 * Only the most recent crash record is shown to avoid overwhelming the
 * user. The full log is reachable from Settings > About > Crash Log.
 *
 * Distinguishes Java crashes from OS-initiated kills (PROCESS_EXIT
 * entries recorded by ProcessExitReporter) and adjusts the wording
 * accordingly, because for OS kills sharing a stack trace doesn't help —
 * the user has to exempt the app from battery optimization.
 */
@Composable
fun AppCrashRecoveryDialog() {
    val context = LocalContext.current
    val tokens = MaterialTheme.appTokens
    var show by remember { mutableStateOf(false) }
    var crashTail by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            if (CrashLogWriter.hasUnacknowledgedCrashes()) {
                val full = CrashLogWriter.readCrashLog()
                crashTail = extractMostRecentRecord(full)
                show = true
            }
        } catch (_: Throwable) {}
    }

    if (!show) return

    val isOsKill = crashTail.contains("PROCESS_EXIT")
    val isJavaCrash = !isOsKill || crashTail.contains("CRASH (crash)") ||
        crashTail.contains("at com.tmuxes")
    val titleText = when {
        isJavaCrash -> "Crash detected last session"
        else -> "Previous session ended unexpectedly"
    }
    val bodyText = when {
        isJavaCrash ->
            "tmuxes crashed in the previous session. If you can share " +
                "the trace below with the developer it helps fix the " +
                "underlying bug.\n"
        else ->
            "Android terminated tmuxes between sessions. The reason " +
                "below explains why — usually it's the OS reclaiming " +
                "memory or applying strict background battery limits. " +
                "The fix is usually to exempt tmuxes from battery " +
                "optimization in system Settings.\n"
    }
    val shareCrashLogTitle = t("Share crash log")
    val acknowledge = {
        try { CrashLogWriter.acknowledgeCrashLog() } catch (_: Throwable) {}
        show = false
    }
    val shareLog = {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "tmuxes crash log")
                putExtra(Intent.EXTRA_TEXT, crashTail)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(
                Intent.createChooser(shareIntent, shareCrashLogTitle)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Throwable) {}
        acknowledge()
    }
    val copyLog = {
        try {
            val clipboard = context.getSystemService(
                Context.CLIPBOARD_SERVICE
            ) as? ClipboardManager
            clipboard?.setPrimaryClip(
                ClipData.newPlainText("tmuxes crash log", crashTail)
            )
        } catch (_: Throwable) {}
        acknowledge()
    }

    AppDialog(
        title = titleText,
        onDismiss = {
            // Force the user to choose Share / Copy / Dismiss explicitly
            // so they don't accidentally lose the trace via outside-tap.
        },
        confirmLabel = "Share",
        onConfirm = shareLog,
        dismissLabel = "Dismiss",
        onDismissAction = acknowledge,
        neutralLabel = "Copy",
        onNeutral = copyLog,
        neutralStyle = AppButtonStyle.Outlined,
        contentScrollable = false,
        content = {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dpUnit())
                    .appElasticVerticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(tokens.space.md)
            ) {
                Text(
                    text = t(bodyText),
                    style = tokens.type.bodyMedium,
                    color = tokens.colors.onSurface
                )
                Text(
                    text = crashTail,
                    style = tokens.type.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    color = tokens.colors.onSurfaceVariant
                )
            }
        }
    )
}

private fun extractMostRecentRecord(full: String): String {
    return try {
        val sep = "=========================================="
        val lastEndIdx = full.lastIndexOf(sep)
        if (lastEndIdx < 0) return full
        val beforeLastEnd = full.substring(0, lastEndIdx).trimEnd()
        val lastStartIdx = beforeLastEnd.lastIndexOf(sep)
        if (lastStartIdx < 0) return full
        full.substring(lastStartIdx, lastEndIdx + sep.length)
    } catch (_: Throwable) {
        full
    }
}

private fun Int.dpUnit() = androidx.compose.ui.unit.Dp(this.toFloat())

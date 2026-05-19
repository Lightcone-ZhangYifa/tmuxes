package com.tmuxes.util

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Queries Android's historical process-exit API to surface WHY the
 * process last died, and writes the reason to the crash log file
 * via [CrashLogWriter] so the user can see it alongside actual crashes.
 *
 * **Why this matters**: many user-perceived "crashes" are not crashes
 * at all — the OS killed the process for one of several reasons:
 *
 * - `REASON_LOW_MEMORY`: system OOM killer reclaimed memory
 * - `REASON_FREEZER`: Android 12+ froze the process for too long
 * - `REASON_USER_REQUESTED`: user swiped from Recents (intentional)
 * - `REASON_USER_STOPPED`: Settings > Force stop, battery saver
 * - `REASON_DEPENDENCY_DIED`: a bound service the process depended on died
 * - `REASON_PERMISSION_CHANGE`: user revoked a permission
 * - `REASON_PACKAGE_STATE_CHANGE`: app updated / uninstalled / replaced
 * - `REASON_CRASH`: actual Java exception (already covered by CrashLogWriter)
 * - `REASON_CRASH_NATIVE`: native (JNI / ndk) crash (NOT covered by us)
 * - `REASON_ANR`: Application Not Responding (NOT covered by us)
 *
 * Some Android builds aggressively stop background apps for battery
 * reasons, which the user experiences as "crashes everywhere" because
 * the SSH connections drop silently. Without this reporter we have no
 * way to distinguish "the app crashed" from "the OS killed the app for
 * being in the background".
 *
 * Available since API 30 (Android 11). On older devices this class is
 * a no-op.
 */
object ProcessExitReporter {

    /**
     * Inspect the previous process-exit reasons and append a record to
     * the crash log if any non-trivial exit happened (i.e., not
     * USER_REQUESTED or EXIT_SELF). Called from TmuxesApp.onCreate
     * after [CrashLogWriter.install].
     */
    fun reportLastExit(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            AppLogger.t(AppLogger.Category.LIFECYCLE) { "exitReporter skip — pre-API 30" }
            return
        }
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return
            // pid=0 → all processes for our package; maxNum=5 → up to 5 most recent.
            // We only care about the most recent one for the dialog, but pull a
            // small history so we can also detect repeated kills.
            val records = am.getHistoricalProcessExitReasons(
                /* packageName = */ context.packageName,
                /* pid = */ 0,
                /* maxNum = */ 5
            )
            if (records.isEmpty()) {
                AppLogger.t(AppLogger.Category.LIFECYCLE) { "exitReporter ← no historical exits" }
                return
            }
            AppLogger.d(AppLogger.Category.LIFECYCLE) {
                "exitReporter ← history=${records.size} reasons=${records.joinToString(",") { reasonToName(it.reason) }}"
            }

            // Find the most recent reason that isn't a normal exit. We
            // never want to surface "user closed the app from Recents"
            // as a problem — only abnormal terminations.
            val notable = records.firstOrNull { isNotable(it) } ?: run {
                AppLogger.t(AppLogger.Category.LIFECYCLE) { "exitReporter no notable exits" }
                return
            }
            AppLogger.w(AppLogger.Category.LIFECYCLE) {
                "exitReporter notable reason=${reasonToName(notable.reason)} desc='${notable.description ?: ""}' " +
                    "importance=${importanceToName(notable.importance)} pss=${notable.pss}KB"
            }

            val text = formatExit(notable)
            CrashLogWriter.recordExitReason(text)
        } catch (t: Throwable) {
            AppLogger.w(AppLogger.Category.LIFECYCLE) {
                "exitReporter ✗ cause='${t.message}' — never propagated; runs on main thread in Application.onCreate"
            }
        }
    }

    private fun isNotable(info: ApplicationExitInfo): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        return when (info.reason) {
            // Normal terminations — never notable. Same hardcoded-int
            // rationale as reasonToName.
            10, // REASON_USER_REQUESTED
            11, // REASON_USER_STOPPED
            1   // REASON_EXIT_SELF
            -> false
            // Anything else is potentially user-visible as a "crash".
            else -> true
        }
    }

    private fun formatExit(info: ApplicationExitInfo): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return ""

        val reasonName = reasonToName(info.reason)
        val importanceName = importanceToName(info.importance)
        val timestamp = try {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                .format(Date(info.timestamp))
        } catch (_: Throwable) {
            info.timestamp.toString()
        }
        val description = info.description ?: ""
        val pss = info.pss
        val rss = info.rss
        val processName = info.processName

        return buildString {
            append("[$timestamp] PROCESS_EXIT pid=${info.pid} process=$processName\n")
            append("reason=$reasonName")
            if (description.isNotBlank()) append(" ($description)")
            append(" importance=$importanceName pss=${pss}KB rss=${rss}KB\n")
            append(reasonExplanation(info.reason))
            append("\n")
        }
    }

    private fun reasonToName(reason: Int): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return "?"
        // Use raw integer values rather than ApplicationExitInfo
        // constants because some constants were added at later API
        // levels (REASON_PACKAGE_STATE_CHANGE in API 31, REASON_FREEZER
        // in API 31) and the Kotlin `when` over symbolic constants
        // doesn't always inline cleanly across the SDK boundary —
        // observed in the field as "REASON_16" being printed instead
        // of "PACKAGE_STATE_CHANGE". The integer values are stable
        // across SDK versions per the AOSP documentation.
        return when (reason) {
            0 -> "UNKNOWN"
            1 -> "EXIT_SELF"
            2 -> "SIGNALED"
            3 -> "LOW_MEMORY"
            4 -> "CRASH"
            5 -> "CRASH_NATIVE"
            6 -> "ANR"
            7 -> "INITIALIZATION_FAILURE"
            8 -> "PERMISSION_CHANGE"
            9 -> "EXCESSIVE_RESOURCE_USAGE"
            10 -> "USER_REQUESTED"
            11 -> "USER_STOPPED"
            12 -> "DEPENDENCY_DIED"
            13 -> "OTHER"
            14 -> "FREEZER"
            15 -> "PACKAGE_UPDATED"
            16 -> "PACKAGE_STATE_CHANGE"
            else -> "REASON_$reason"
        }
    }

    private fun importanceToName(importance: Int): String {
        return when (importance) {
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "FOREGROUND"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE -> "FG_SERVICE"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING -> "TOP_SLEEPING"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "VISIBLE"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE -> "PERCEPTIBLE"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "SERVICE"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED -> "CACHED"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE -> "GONE"
            else -> "IMP_$importance"
        }
    }

    private fun reasonExplanation(reason: Int): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return ""
        // Same hardcoded-int rationale as reasonToName above.
        return when (reason) {
            3 -> // LOW_MEMORY
                "→ The OS killed tmuxes to reclaim memory. This is NOT a tmuxes bug; the device was under memory pressure."
            14 -> // FREEZER
                "→ Android 12+ froze tmuxes for too long while in the background and then killed it. " +
                "This is common on devices with strict background battery limits. " +
                "To prevent: allow unrestricted battery usage for tmuxes in system Settings."
            12 -> // DEPENDENCY_DIED
                "→ A bound service that tmuxes depended on died. This is rarely a tmuxes bug."
            9 -> // EXCESSIVE_RESOURCE_USAGE
                "→ The OS killed tmuxes for using too many resources (CPU/memory/network). " +
                "This usually indicates a leak or runaway loop in tmuxes — please share this log."
            8 -> // PERMISSION_CHANGE
                "→ A permission was revoked while tmuxes was running. Re-grant permissions in Settings."
            16, 15 -> // PACKAGE_STATE_CHANGE / PACKAGE_UPDATED
                "→ tmuxes was updated, uninstalled, or replaced while running. Normal after an app update."
            4 -> // CRASH
                "→ Java exception. See the stack trace recorded by CrashLogWriter."
            5 -> // CRASH_NATIVE
                "→ Native (NDK / JNI) crash. tmuxes does not use native code, so this should not happen — please share."
            6 -> // ANR
                "→ Application Not Responding — the main thread blocked for too long. " +
                "This is a tmuxes bug; please share this log."
            7 -> // INITIALIZATION_FAILURE
                "→ tmuxes failed to initialize. Probably a corrupted install or incompatible Android version."
            2 -> // SIGNALED
                "→ The process received an OS signal (e.g. SIGKILL). Often the OOM killer or an Android background restriction."
            else -> ""
        }
    }
}

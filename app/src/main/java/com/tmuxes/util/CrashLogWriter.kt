// allow-bypass-D5: this IS the logger; logging from inside crash-write would recurse
package com.tmuxes.util

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes uncaught exceptions to a local crash log file that the user can
 * share with the developer for diagnosis.
 *
 * **Why this exists**: the main complaint from real users is "crashes
 * everywhere" with no concrete stack trace. Traditional Android crash
 * dialogs vanish the moment the user dismisses them, and `adb logcat -b
 * crash -d` requires a connected developer machine. This writer persists
 * the last ~20 crashes to `filesDir/crash_log.txt` so the Settings > About
 * screen can surface them and the user can copy/export them.
 *
 * Design choices:
 *
 * - **File-based, not system API**: [Thread.setDefaultUncaughtExceptionHandler]
 *   fires on the thread where the crash happened, and we can't block the
 *   main thread for a database write or a network round trip. Writing to a
 *   flat text file via `File.appendText` is fast enough that we can do it
 *   synchronously before the process dies.
 *
 * - **Append-only with size cap**: we append new crashes to the end of
 *   the file. When the file grows beyond [MAX_CRASH_LOG_BYTES], we
 *   atomically truncate from the front so the most recent crashes survive.
 *   We never lock the file because a crash-during-crash-write is still a
 *   crash — partial writes are acceptable.
 *
 * - **Synchronous write, not a worker**: by the time the default uncaught
 *   handler fires, the process is already doomed on the main thread. A
 *   `CoroutineScope.launch` would not complete before the process dies.
 *   Direct file I/O is the only reliable option.
 *
 * - **Never throw from here**: every operation wrapped in try/catch. A
 *   throw from the crash writer inside the uncaught handler would
 *   recurse and eventually stack-overflow.
 */
object CrashLogWriter {

    private const val FILE_NAME = "crash_log.txt"
    private const val ACK_FILE_NAME = "crash_log.ack"
    private const val MAX_CRASH_LOG_BYTES = 256 * 1024  // 256 KB

    /** Application context for file access. Set once from TmuxesApp.onCreate. */
    @Volatile
    private var appContext: Context? = null

    fun install(context: Context) {
        appContext = context.applicationContext
    }

    fun getCrashLogFile(): File? {
        val ctx = appContext ?: return null
        return try {
            File(ctx.filesDir, FILE_NAME)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Append one crash record to the log file. Format:
     *
     *     ==========================================
     *     [yyyy-MM-dd HH:mm:ss.SSS] thread=<name> pid=<pid>
     *     <stack trace>
     *     ==========================================
     *
     * Safe to call from any thread including the UncaughtExceptionHandler.
     * Never throws.
     */
    fun recordCrash(thread: Thread, throwable: Throwable) {
        val ctx = appContext ?: return
        try {
            val file = File(ctx.filesDir, FILE_NAME)
            // Truncate if we are over the cap. Read tail, write it back.
            val existing: String = try {
                if (file.exists() && file.length() > MAX_CRASH_LOG_BYTES) {
                    val text = file.readText()
                    // Keep the last 75% of the cap so we have headroom for
                    // the next few crashes before another truncate.
                    val keepFromIdx = (text.length - (MAX_CRASH_LOG_BYTES * 3 / 4))
                        .coerceAtLeast(0)
                    // Snap to the next `==========` block boundary so we
                    // don't truncate mid-record.
                    val boundary = text.indexOf("==========", keepFromIdx)
                        .let { if (it < 0) keepFromIdx else it }
                    text.substring(boundary)
                } else if (file.exists()) {
                    file.readText()
                } else {
                    ""
                }
            } catch (_: Throwable) {
                ""
            }

            val sw = StringWriter()
            val pw = PrintWriter(sw)
            throwable.printStackTrace(pw)
            pw.flush()

            val timestamp = try {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            } catch (_: Throwable) {
                System.currentTimeMillis().toString()
            }

            val pid = try { android.os.Process.myPid() } catch (_: Throwable) { -1 }

            // Device + app context — every field is guarded because
            // reading Build fields on a stripped ROM or an Xposed-hooked
            // device can throw (rare but real).
            val context = buildString {
                append("device=")
                append(safe { "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}" })
                append(" android=")
                append(safe { android.os.Build.VERSION.RELEASE })
                append(" sdk=")
                append(safe { android.os.Build.VERSION.SDK_INT.toString() })
                append(" app=")
                append(safe { getAppVersion(ctx) })
                append(" build=")
                append(safe { getBuildType(ctx) })
            }

            val crumbs = try {
                AppLogger.snapshotBreadcrumbs()
            } catch (_: Throwable) {
                emptyList()
            }

            val record = buildString {
                append("==========================================\n")
                append("[$timestamp] thread=${thread.name} pid=$pid\n")
                append(context)
                append('\n')
                if (crumbs.isNotEmpty()) {
                    append("--- last ${crumbs.size} log lines before crash ---\n")
                    for (line in crumbs) append(line).append('\n')
                    append("--- end log ---\n")
                }
                append(sw.toString())
                if (!sw.toString().endsWith("\n")) append("\n")
                append("==========================================\n\n")
            }

            try {
                file.parentFile?.mkdirs()
                file.writeText(existing + record)
            } catch (_: Throwable) {
                // If the atomic write fails, try a simple append as a
                // fallback — better to have a corrupted tail than no log
                // at all.
                try { file.appendText(record) } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {
            // Never propagate — the uncaught handler is the last line
            // of defense and cannot itself throw.
        }
    }

    /**
     * Return the full crash log as a string, or `""` if unavailable.
     * Called from the About screen to display the log to the user.
     */
    fun readCrashLog(): String {
        val ctx = appContext ?: return ""
        return try {
            val file = File(ctx.filesDir, FILE_NAME)
            if (file.exists()) file.readText() else ""
        } catch (_: Throwable) {
            ""
        }
    }

    /**
     * Clear the crash log. Called from the About screen's "Clear crash
     * log" button. Also clears the acknowledgement marker so the next
     * crash will trigger a fresh notification.
     */
    fun clearCrashLog() {
        val ctx = appContext ?: return
        try {
            File(ctx.filesDir, FILE_NAME).delete()
        } catch (_: Throwable) {}
        try {
            File(ctx.filesDir, ACK_FILE_NAME).delete()
        } catch (_: Throwable) {}
    }

    /**
     * Returns true if the crash log file is longer than what the user
     * has acknowledged — meaning a new crash happened since the last
     * time they saw the dialog.
     *
     * Used by MainActivity to show a one-time "crash detected last
     * session" dialog on app launch. The user can then tap "View" to
     * open Settings > About > Crash Log, or "Dismiss" to mark the
     * current log as acknowledged.
     *
     * Storage: a tiny `crash_log.ack` file containing the acknowledged
     * byte length. We compare against the current crash log file
     * length. This is robust to mid-session crashes (the flag is
     * per-byte-count, not per-session).
     */
    fun hasUnacknowledgedCrashes(): Boolean {
        val ctx = appContext ?: return false
        return try {
            val crashFile = File(ctx.filesDir, FILE_NAME)
            if (!crashFile.exists() || crashFile.length() == 0L) return false
            val ackFile = File(ctx.filesDir, ACK_FILE_NAME)
            val acked = if (ackFile.exists()) {
                ackFile.readText().trim().toLongOrNull() ?: 0L
            } else {
                0L
            }
            crashFile.length() > acked
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Mark the current crash log as acknowledged. After calling this,
     * [hasUnacknowledgedCrashes] returns false until a new crash is
     * appended to the file.
     */
    fun acknowledgeCrashLog() {
        val ctx = appContext ?: return
        try {
            val crashFile = File(ctx.filesDir, FILE_NAME)
            val length = if (crashFile.exists()) crashFile.length() else 0L
            File(ctx.filesDir, ACK_FILE_NAME).writeText(length.toString())
        } catch (_: Throwable) {}
    }

    /**
     * Append a non-crash record to the log file. Used by
     * [ProcessExitReporter] to record an OS-initiated kill (low memory,
     * freezer, background battery limits, etc.) so the user sees it
     * alongside actual crashes in the same dialog.
     *
     * The format is the same as [recordCrash] but with the caller's
     * preformatted text instead of a stack trace, and no thread/pid
     * line (since the process that died is not the current one).
     */
    fun recordExitReason(text: String) {
        val ctx = appContext ?: return
        try {
            val file = File(ctx.filesDir, FILE_NAME)
            val record = buildString {
                append("==========================================\n")
                append(text)
                if (!text.endsWith("\n")) append("\n")
                append("==========================================\n\n")
            }
            file.parentFile?.mkdirs()
            try {
                file.appendText(record)
            } catch (_: Throwable) {}
        } catch (_: Throwable) {}
    }

    // -------------------------------------------------------------------------
    // Device + app context helpers
    // -------------------------------------------------------------------------

    /** Run [block] and return "?" on any throw — used for defensive
     *  stringification of device fields that might throw on exotic ROMs. */
    private inline fun safe(block: () -> String): String {
        return try {
            block()
        } catch (_: Throwable) {
            "?"
        }
    }

    private fun getAppVersion(ctx: Context): String {
        return try {
            val pm = ctx.packageManager
            val info = pm.getPackageInfo(ctx.packageName, 0)
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            "${info.versionName} ($versionCode)"
        } catch (_: Throwable) {
            "?"
        }
    }

    private fun getBuildType(ctx: Context): String {
        // `debug` variant has packageName com.tmuxes.debug, release has
        // com.tmuxes. Use that to infer the build type without pulling
        // in BuildConfig which is per-variant generated.
        return try {
            if (ctx.packageName.endsWith(".debug")) "debug" else "release"
        } catch (_: Throwable) {
            "?"
        }
    }
}

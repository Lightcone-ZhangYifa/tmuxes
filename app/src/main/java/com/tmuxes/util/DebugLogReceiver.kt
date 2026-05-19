package com.tmuxes.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.tmuxes.BuildConfig

/**
 * Debug-build-only receiver that lets developers retune AppLogger from
 * `adb shell` without rebuilding or restarting the app.
 *
 * ## Usage
 *
 * Crank one category to TRACE:
 *
 * ```bash
 * adb shell am broadcast \
 *   -a com.tmuxes.debug.SET_LOG_LEVEL \
 *   -p com.tmuxes.debug \
 *   --es category SESSION --es level trace
 * ```
 *
 * Set every category at once:
 *
 * ```bash
 * adb shell am broadcast \
 *   -a com.tmuxes.debug.SET_LOG_LEVEL \
 *   -p com.tmuxes.debug \
 *   --es category ALL --es level debug
 * ```
 *
 * The receiver is registered programmatically by [TmuxesApp] only when
 * `BuildConfig.DEBUG` is true. In release builds [register] is a no-op
 * so this attack-surface-shaped string-keyed Intent vector simply
 * doesn't exist.
 */
object DebugLogReceiver {

    const val ACTION = "com.tmuxes.debug.SET_LOG_LEVEL"
    private const val EXTRA_CATEGORY = "category"
    private const val EXTRA_LEVEL = "level"

    @Volatile private var registered: BroadcastReceiver? = null

    fun register(context: Context) {
        if (!BuildConfig.DEBUG) return
        if (registered != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                handle(intent)
            }
        }
        val filter = IntentFilter(ACTION)
        try {
            // RECEIVER_EXPORTED is intentional in debug builds: `adb shell
            // am broadcast` runs as shell UID, which is NOT same-UID with
            // the app, so RECEIVER_NOT_EXPORTED would block delivery.
            // Action string + `-p com.tmuxes.debug` filter still scope
            // to our package, and the receiver is only registered when
            // BuildConfig.DEBUG, so release builds are not affected.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    receiver,
                    filter,
                    Context.RECEIVER_EXPORTED
                )
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(receiver, filter)
            }
            registered = receiver
            AppLogger.i(AppLogger.Category.LIFECYCLE) {
                "debug.receiver registered action=$ACTION"
            }
        } catch (t: Throwable) {
            AppLogger.w(AppLogger.Category.LIFECYCLE) {
                "debug.receiver register ✗ cause='${t.message}'"
            }
        }
    }

    fun unregister(context: Context) {
        val r = registered ?: return
        registered = null
        try { context.unregisterReceiver(r) } catch (_: Throwable) {}
    }

    private fun handle(intent: Intent) {
        val rawCategory = intent.getStringExtra(EXTRA_CATEGORY)?.uppercase() ?: ""
        val rawLevel = intent.getStringExtra(EXTRA_LEVEL)?.uppercase() ?: ""
        val level = try {
            AppLogger.Level.valueOf(rawLevel)
        } catch (_: Throwable) {
            AppLogger.w(AppLogger.Category.LIFECYCLE) {
                "debug.receiver unknown level='$rawLevel' (valid: TRACE|DEBUG|INFO|WARN|ERROR)"
            }
            return
        }
        if (rawCategory == "ALL" || rawCategory == "*") {
            AppLogger.setLevelForAll(level)
            return
        }
        val category = try {
            AppLogger.Category.valueOf(rawCategory) // allow-bypass-D6: debug ADB broadcast translates user-typed string
        } catch (_: Throwable) {
            AppLogger.w(AppLogger.Category.LIFECYCLE) {
                "debug.receiver unknown category='$rawCategory' (valid: ${AppLogger.Category.values().joinToString(",") { it.name }} or ALL)"
            }
            return
        }
        AppLogger.setLevel(category, level)
    }
}

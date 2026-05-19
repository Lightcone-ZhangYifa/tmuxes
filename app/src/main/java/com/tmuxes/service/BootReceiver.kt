package com.tmuxes.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tmuxes.TmuxesApp
import com.tmuxes.util.AppLogger
import com.tmuxes.util.AppLogger.Category
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Starts the foreground service on device boot so the
 * [com.tmuxes.ssh.ConnectionSupervisor] can auto-connect all enabled servers.
 *
 * The supervisor runs inside TmuxesApp.onCreate() and handles the actual
 * connection work. This receiver just ensures the process and service start.
 *
 * This receiver runs on the main thread and is one of the first pieces of
 * our code to execute on device boot. An uncaught exception here kills the
 * receiving process — the user sees it as "the app keeps stopping right
 * after I unlock the device". Every code path is therefore wrapped in
 * try/catch with a fail-safe to release the goAsync() result.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Wrap the entire body — onReceive runs on the main thread and
        // an uncaught exception kills the receiving process.
        val pendingResult = try { goAsync() } catch (_: Throwable) { null }
        try {
            if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
                pendingResult?.finish()
                return
            }

            // Start the foreground service immediately to stay within the
            // background start exemption window from BOOT_COMPLETED.
            try {
                SshForegroundService.start(context)
            } catch (e: Throwable) {
                AppLogger.e(Category.BOOT, e) { "BootReceiver: failed to start foreground service" }
            }

            // SupervisorJob + CoroutineExceptionHandler so the server
            // enumeration cannot crash the BootReceiver process.
            val handler = CoroutineExceptionHandler { _, e ->
                AppLogger.e(Category.BOOT, e) { "BootReceiver: scope exception (swallowed)" }
                try { pendingResult?.finish() } catch (_: Throwable) {}
            }
            CoroutineScope(Dispatchers.IO + SupervisorJob() + handler).launch {
                try {
                    val app = context.applicationContext as? TmuxesApp ?: run {
                        pendingResult?.finish()
                        return@launch
                    }
                    val servers = app.serverRepository.getAllDecryptedOnce().filter { it.isEnabled }
                    if (servers.isEmpty()) {
                        // No enabled servers — stop the service we just started
                        try { SshForegroundService.stop(context) } catch (_: Throwable) {} // allow-bypass-D5: best-effort post-boot cleanup when no servers enabled
                    }
                    // Otherwise the ConnectionSupervisor (started in TmuxesApp.onCreate())
                    // will detect these servers and auto-connect them.
                } catch (e: Throwable) {
                    AppLogger.e(Category.BOOT, e) { "BootReceiver: error checking enabled servers" }
                } finally {
                    try { pendingResult?.finish() } catch (_: Throwable) {}
                }
            }
        } catch (e: Throwable) {
            // Last resort — onReceive must never throw
            try {
                AppLogger.e(Category.BOOT, e) { "BootReceiver: top-level exception (swallowed)" }
            } catch (_: Throwable) {}
            try { pendingResult?.finish() } catch (_: Throwable) {}
        }
    }
}

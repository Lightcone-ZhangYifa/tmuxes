package com.tmuxes.widget

import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.tmuxes.R
import com.tmuxes.TmuxesApp
import com.tmuxes.ssh.ServerStatus
import com.tmuxes.util.AppLogger
import com.tmuxes.util.safeLaunch
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first

/**
 * Quick Settings tile for one-tap connect/disconnect to the default
 * (most recently used) server.
 *
 * - Displays "Connected" or "Disconnected" state with matching icon.
 * - On click: connects to the default server and attaches to the default
 *   tmux session, or disconnects if already connected.
 */
class QuickConnectTile : TileService() {

    // SupervisorJob + handler so a coroutine failure does NOT kill the
    // tile service process. The previous plain Job + `!!` on Job lookup
    // could NPE on lifecycle edge cases.
    private val tileExceptionHandler = CoroutineExceptionHandler { _, e ->
        AppLogger.e(AppLogger.Category.SVC, e) { "QuickConnectTile: scope exception (swallowed)" }
    }
    private var scope = newScope()

    private fun newScope(): CoroutineScope =
        CoroutineScope(Dispatchers.IO + SupervisorJob() + tileExceptionHandler)

    override fun onStartListening() {
        super.onStartListening()
        try {
            // Re-create scope if it was previously cancelled. Null-safe job
            // lookup: `get(Job)` may theoretically return null even though
            // in practice a CoroutineScope always carries one — the old
            // `!!` bang was a latent NPE.
            val jobActive = scope.coroutineContext[Job]?.isActive ?: false
            if (!jobActive) scope = newScope()
            updateTileState()
        } catch (e: Exception) {
            AppLogger.w(AppLogger.Category.SVC) { "QuickConnectTile.onStartListening failed: ${e.message}" }
        }
    }

    override fun onClick() {
        super.onClick()

        scope.safeLaunch(tag = "QuickConnectTile.onClick") {
            val app = applicationContext as? TmuxesApp ?: return@safeLaunch
            val supervisor = app.connectionSupervisor
            val hasConnected = supervisor.serverStates.value.values
                .any { it.status == ServerStatus.CONNECTED }

            try {
                if (hasConnected) {
                    // Disconnect all active connections
                    supervisor.disconnectAll()
                } else {
                    // Connect to the most recently used server
                    connectToDefault(app)
                }
            } catch (e: Exception) {
                AppLogger.w(AppLogger.Category.SVC) { "QuickConnectTile.onClick action failed: ${e.message}" }
            }

            // Update tile on the main thread — also guarded because
            // withContext can be cancelled if the service has been destroyed.
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    updateTileState()
                }
            } catch (_: Exception) {
                // Tile may no longer be listening
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { scope.cancel() } catch (_: Exception) {}
    }

    /**
     * Connect to the default (last connected or first auto-connect) server.
     */
    private suspend fun connectToDefault(app: TmuxesApp) {
        try {
            // Quick Settings tile = explicit user-driven retry. Routed through
            // ConnectionTrigger for telemetry consistency with other refresh
            // surfaces (top-bar Sessions refresh, pull-to-refresh).
            app.connectionTrigger.userRefresh()
        } catch (_: Exception) {
            // Best-effort
        }
    }

    /**
     * Read current connection state and update the Quick Settings tile.
     */
    private fun updateTileState() {
        try {
            val tile = qsTile ?: return
            val app = applicationContext as? TmuxesApp

            val isConnected = if (app != null) {
                app.connectionSupervisor.serverStates.value.values
                    .any { it.status == ServerStatus.CONNECTED }
            } else {
                false
            }

            tile.state = if (isConnected) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.label = if (isConnected) "tmuxes: On" else "tmuxes: Off"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = if (isConnected) "Connected" else "Disconnected"
            }

            tile.icon = Icon.createWithResource(
                this,
                R.drawable.ic_terminal
            )

            tile.updateTile()
        } catch (e: Exception) {
            AppLogger.w(AppLogger.Category.SVC) { "updateTileState failed: ${e.message}" }
        }
    }

    companion object {
        /**
         * Request the system to refresh this tile's listening state.
         */
        fun requestUpdate(context: android.content.Context) {
            requestListeningState(
                context,
                ComponentName(context, QuickConnectTile::class.java)
            )
        }
    }
}

package com.tmuxes.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.tmuxes.MainActivity
import com.tmuxes.R
import com.tmuxes.TmuxesApp
import com.tmuxes.util.AppLogger
import com.tmuxes.widget.WidgetSessionManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Foreground service that keeps SSH connections alive when the app is backgrounded.
 *
 * In the always-connected architecture, this service stays alive as long as
 * any enabled servers exist in the database. It does NOT self-stop when
 * connections drop — the [com.tmuxes.ssh.ConnectionSupervisor] handles
 * reconnection. The service only stops when explicitly told to (e.g., all
 * servers removed from the database).
 */
class SshForegroundService : Service() {

    private lateinit var serviceScope: CoroutineScope
    private var observerJob: Job? = null
    private var widgetManager: WidgetSessionManager? = null

    override fun onCreate() {
        super.onCreate()
        // SupervisorJob + exception handler: a single failing child coroutine
        // must NOT cancel siblings that maintain the notification update or
        // widget loop, AND must not propagate to the thread's uncaught handler
        // (which would kill the foreground service process).
        val handler = CoroutineExceptionHandler { _, e ->
            AppLogger.e(AppLogger.Category.SVC, e) { "SshForegroundService: serviceScope exception (swallowed)" }
        }
        serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + handler)
        try {
            createNotificationChannel()
        } catch (e: Exception) {
            AppLogger.e(AppLogger.Category.SVC, e) { "createNotificationChannel failed" }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // EVERYTHING below must survive failures — Android kills the process
        // if onStartCommand throws an uncaught exception. startForeground() in
        // particular can throw ForegroundServiceStartNotAllowedException on
        // Android 12+ when invoked from a background context without a BG
        // start exemption.
        try {
            val notification = buildNotification(ConnectionNotificationFormatter.starting())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Keep the runtime type aligned with the manifest's specialUse
                // declaration. Passing dataSync here crashes on Android versions
                // that enforce "type must be declared in the service element".
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            AppLogger.e(AppLogger.Category.SVC, e) { "SshForegroundService: startForeground failed" }
            // A service started with startForegroundService() must either call
            // startForeground() successfully or stop itself before the system
            // timeout. Continuing here leaves Android no legal foreground state
            // and causes a process crash.
            stopSelf(startId)
            return START_NOT_STICKY
        }

        // Observe supervisor server states and update notification.
        // serviceScope already has a CoroutineExceptionHandler, but we also
        // wrap the collect body so a single failing emission doesn't tear
        // the observer down.
        observerJob?.cancel()
        val app = applicationContext as? TmuxesApp
        val supervisor = app?.connectionSupervisor
        val serverRepository = app?.serverRepository
        observerJob = serviceScope.launch {
            try {
                if (supervisor == null || serverRepository == null) return@launch
                combine(serverRepository.allServers, supervisor.serverStates) { servers, states ->
                    ConnectionNotificationFormatter.format(
                        servers = servers,
                        states = states,
                        nowElapsedMs = SystemClock.elapsedRealtime()
                    )
                }.collect { copy ->
                    try {
                        val updatedNotification = buildNotification(copy)
                        val notificationManager =
                            getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                        notificationManager?.notify(NOTIFICATION_ID, updatedNotification)
                        widgetManager?.onConnectionChanged()
                    } catch (ce: kotlinx.coroutines.CancellationException) {
                        throw ce
                    } catch (e: Exception) {
                        AppLogger.w(AppLogger.Category.SVC) { "notification update failed: ${e.message}" }
                    }
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                // normal service shutdown
            } catch (e: Exception) {
                AppLogger.w(AppLogger.Category.SVC) { "serverStates collect failed: ${e.message}" }
            }
        }

        // Start the widget session manager (real-time via content listeners)
        if (widgetManager == null) {
            try {
                val app = applicationContext as? TmuxesApp
                if (app != null) {
                    val mgr = WidgetSessionManager(
                        context = this,
                        scope = serviceScope
                    )
                    mgr.start()
                    widgetManager = mgr
                    app.widgetSessionManager = mgr
                }
            } catch (e: Exception) {
                AppLogger.e(AppLogger.Category.SVC, e) { "WidgetSessionManager.start failed" }
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        try { widgetManager?.stop() } catch (_: Throwable) {} // allow-bypass-D5: service.onDestroy cleanup; widget manager teardown best-effort
        widgetManager = null
        val app = applicationContext as? TmuxesApp
        if (app != null) {
            app.widgetSessionManager = null
            // Graceful shutdown is launched on the appScope (which survives the
            // service) with a 3-second cap. We do NOT runBlocking here — that
            // would block the binder thread for the duration. The supervisor
            // and coordinators live on appScope, so they can keep running until
            // the cleanup completes or the timeout fires; the process stays
            // alive long enough thanks to the foreground notification.
            try {
                app.appScope.launch {
                    withTimeoutOrNull(3000) {
                        try { app.sessionCoordinator.closeAll() } catch (_: Throwable) {} // allow-bypass-D5: service.onDestroy cleanup
                        try { app.connectionSupervisor.disconnectAll() } catch (_: Throwable) {} // allow-bypass-D5: service.onDestroy cleanup
                    }
                    try { app.connectionSupervisor.stop() } catch (_: Throwable) {} // allow-bypass-D5: service.onDestroy cleanup
                }
            } catch (_: Throwable) {} // allow-bypass-D5: service.onDestroy cleanup; appScope.launch wrapper
        }
        try { serviceScope.cancel() } catch (_: Throwable) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "tmuxes SSH status",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows which SSH servers are connected, retrying, or waiting for action"
            setShowBadge(false)
        }
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(copy: ConnectionNotificationCopy): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle(copy.title)
            .setSummaryText(copy.subText)
        copy.detailLines.forEach { inboxStyle.addLine(it) }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(copy.title)
            .setContentText(copy.text)
            .setSubText(copy.subText)
            .setSmallIcon(R.drawable.ic_terminal)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setStyle(inboxStyle)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "ssh_connections"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            try {
                val intent = Intent(context, SshForegroundService::class.java)
                context.startForegroundService(intent)
            } catch (e: Exception) {
                // Android 12+: ForegroundServiceStartNotAllowedException when
                // called from a restricted background context. Log and carry on —
                // the supervisor will retry on next event.
                AppLogger.w(AppLogger.Category.SVC) { "startForegroundService failed: ${e.message}" }
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, SshForegroundService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                AppLogger.w(AppLogger.Category.SVC) { "stopService failed: ${e.message}" }
            }
        }
    }
}

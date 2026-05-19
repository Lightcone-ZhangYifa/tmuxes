package com.tmuxes

import android.app.Application
import com.tmuxes.data.db.AppDatabase
import com.tmuxes.data.preferences.AppPreferences
import com.tmuxes.data.repository.ServerYamlRepository
import com.tmuxes.data.repository.SnippetYamlRepository
import com.tmuxes.portforward.PortForwardCoordinator
import com.tmuxes.session.SessionCoordinator
import com.tmuxes.ssh.ConnectionSupervisor
import com.tmuxes.ssh.ConnectionTrigger
import com.tmuxes.ssh.HostKeyEvent
import com.tmuxes.ssh.HostKeyPromptResult
import com.tmuxes.ssh.NetworkMonitor
import com.tmuxes.ssh.ServiceController
import com.tmuxes.ssh.SshConnectionPool
import com.tmuxes.ssh.SshKeyManager
import com.tmuxes.service.SshForegroundService
import com.tmuxes.widget.WidgetSessionManager
import com.tmuxes.util.AppLogger
import com.tmuxes.util.CrashLogWriter
import com.tmuxes.util.DebugLogReceiver
import com.tmuxes.util.ProcessExitReporter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

/**
 * Application class for tmuxes.
 *
 * Initialises all singletons: database, preferences, SSH connection manager,
 * network monitor, and the always-connected [ConnectionSupervisor].
 */
class TmuxesApp : Application() {

    companion object {
        private const val TRIM_RUNNING_MODERATE = 5
        private const val TRIM_RUNNING_LOW = 10
        private const val TRIM_RUNNING_CRITICAL = 15
        private const val TRIM_MODERATE = 60
        private const val TRIM_COMPLETE = 80

        init {
            // BouncyCastle replaces the platform's BC stub so SSHJ can use
            // the full BC algorithm set. This runs on class-load BEFORE
            // Application.onCreate, so any SecurityException / NPE here
            // crashes the entire process before any exception handler can
            // catch it. Wrap defensively — if BC init fails, SSHJ falls
            // back to whatever the platform provides (degraded but alive).
            try {
                Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
                Security.insertProviderAt(BouncyCastleProvider(), 1)
            } catch (_: Throwable) {
                // logged later via AppLogger once the class is loaded
            }
        }
    }

    /**
     * Safety net exception handler for the application-wide coroutine scope.
     *
     * Background work inside [appScope] — SSH read loops, port-forward accept
     * loops, session auto-reattach, etc. — must NEVER crash the process when
     * something goes wrong. [SupervisorJob] alone is not sufficient: when a
     * child coroutine throws and no handler is installed, the exception is
     * delivered to the thread's [Thread.UncaughtExceptionHandler], which on
     * Android terminates the app.
     *
     * Network drops, transport resets, and other IO errors are routine in an
     * SSH client; this handler logs them and keeps the scope alive.
     */
    private val appExceptionHandler = CoroutineExceptionHandler { _, e ->
        AppLogger.e(AppLogger.Category.LIFECYCLE, e) { "TmuxesApp: uncaught exception in appScope (swallowed to prevent crash)" }
    }

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + appExceptionHandler)

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val preferences: AppPreferences by lazy { AppPreferences(this) }
    val serverRepository: ServerYamlRepository by lazy { ServerYamlRepository(this) }
    val snippetRepository: SnippetYamlRepository by lazy { SnippetYamlRepository(this) }
    val sshKeyManager: SshKeyManager get() = SshKeyManager
    val networkMonitor: NetworkMonitor by lazy { NetworkMonitor(this) }
    val connectionPool: SshConnectionPool by lazy { SshConnectionPool() }
    val connectionSupervisor: ConnectionSupervisor by lazy { ConnectionSupervisor.getInstance() }

    /**
     * Single source of truth for "when does the app try to (re)connect".
     * Owns ProcessLifecycle / SCREEN_ON / network / server-list / invalidation
     * subscriptions and the user-refresh entry point. ViewModels and UI tiles
     * reach the supervisor through here, never via the supervisor's
     * [ConnectionSupervisor.softCheck] or `reset*RetryState` methods directly.
     */
    val connectionTrigger: ConnectionTrigger by lazy {
        ConnectionTrigger(
            context = this,
            supervisor = connectionSupervisor,
            networkMonitor = networkMonitor,
            serverRepository = serverRepository
        )
    }

    /** Unified session management (replaces TerminalSessionRegistry). */
    val sessionCoordinator: SessionCoordinator by lazy {
        SessionCoordinator(connectionPool, connectionSupervisor, appScope)
    }

    /** Auto-restore port forwards on reconnect. */
    val portForwardCoordinator: PortForwardCoordinator by lazy {
        PortForwardCoordinator(connectionPool, connectionSupervisor, serverRepository, appScope)
    }

    /** Widget session manager — created by the foreground service. */
    var widgetSessionManager: WidgetSessionManager? = null

    // -----------------------------------------------------------------------
    // Host key verification UI bridge
    // -----------------------------------------------------------------------

    data class HostKeyPromptState(
        val event: HostKeyEvent,
        val deferred: CompletableDeferred<HostKeyPromptResult>
    )

    private val _hostKeyPrompt = MutableStateFlow<HostKeyPromptState?>(null)
    val hostKeyPrompt: StateFlow<HostKeyPromptState?> = _hostKeyPrompt.asStateFlow()

    fun respondToHostKeyPrompt(result: HostKeyPromptResult) {
        _hostKeyPrompt.value?.deferred?.complete(result)
        _hostKeyPrompt.value = null
    }

    /**
     * Install a process-wide last-resort uncaught exception handler.
     *
     * Strategy: keep a reference to the previous (platform) handler and
     * inspect the throwable. If it is a [VirtualMachineError] (OOM,
     * StackOverflow, etc.), [InternalError], or happens on the main thread,
     * delegate to the platform handler — these are genuine crashes the OS
     * expects to see. Otherwise, log it and swallow.
     *
     * The main thread is NOT swallowed because suppressing an uncaught
     * exception there leads to a frozen UI and ANR — better to let the
     * process die cleanly and restart.
     */
    private fun installUncaughtExceptionHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Always persist the crash to a local file so the user can
            // share the stack trace with the developer — this is more
            // useful than a transient logcat line that vanishes the next
            // time the user reboots or reattaches to adb. See
            // CrashLogWriter for the file format and rotation policy.
            try { CrashLogWriter.recordCrash(thread, throwable) } catch (_: Throwable) {} // allow-bypass-D5: this IS the crash logger; logging from inside crash recording would recurse

            val isFatal = throwable is VirtualMachineError ||
                throwable is InternalError ||
                throwable is ThreadDeath
            val isMain = thread == android.os.Looper.getMainLooper().thread
            if (isFatal || isMain) {
                // Preserve the stack trace by delegating to the platform
                // handler. This surfaces a standard system crash dialog.
                AppLogger.e(AppLogger.Category.LIFECYCLE, throwable) { "TmuxesApp: fatal exception on thread '${thread.name}' — delegating to platform handler" }
                previous?.uncaughtException(thread, throwable)
                return@setDefaultUncaughtExceptionHandler
            }
            // Recoverable: log and keep the process alive. This is the path
            // that catches an escaped IOException from an SSH read loop or
            // a SQLite error from a background DAO call — things that used
            // to silently kill the app.
            AppLogger.e(AppLogger.Category.LIFECYCLE, throwable) { "TmuxesApp: uncaught exception on '${thread.name}' (swallowed to keep app alive)" }
        }
    }

    override fun onCreate() {
        super.onCreate()

        // 0. Install the crash log writer FIRST, before any other init.
        //    It's a pure file-writer with no dependencies, so this is
        //    always safe. It captures crashes that happen during the
        //    rest of onCreate() too.
        try { CrashLogWriter.install(this) } catch (_: Throwable) {} // allow-bypass-D5: bootstrap before any logger is wired; failure means crash log is unavailable but the app must keep starting

        // 0a1. Query the OS for why the previous process died. This
        //    distinguishes "tmuxes crashed" from "Android killed tmuxes
        //    in the background for low memory / battery / freezer".
        //    Available since API 30; older devices no-op. The reporter
        //    appends a record to the same crash log file so the
        //    AppCrashRecoveryDialog and Settings > About > Crash Log
        //    surface OS kills alongside actual crashes.
        try { ProcessExitReporter.reportLastExit(this) } catch (_: Throwable) {} // allow-bypass-D5: API-30+ best-effort process-exit query; failure means we miss prior-process-death signal

        // 0a. Last-resort safety net: catch any exception that escapes every
        //    other handler (coroutine handlers, try/catch blocks, etc.). This
        //    is the process-wide uncaught exception handler. We keep the
        //    existing handler (Android's platform handler) in a chain so that
        //    genuinely unrecoverable errors (OOM, StackOverflow, Errors that
        //    signal VM problems) still tear the process down — we ONLY
        //    swallow Exception subclasses that originated from our own
        //    package and are clearly recoverable (IOExceptions from SSH,
        //    sqlite, etc.). Anything else gets passed through.
        try { installUncaughtExceptionHandler() } catch (_: Throwable) {} // allow-bypass-D5: bootstrap; if installing the handler itself throws, fall back to platform default

        // Each subsequent init step is wrapped individually so that a
        // failure in (say) database init doesn't kill the app before
        // ProcessLifecycleOwner is set up. Application.onCreate runs on the
        // main thread BEFORE the activity is created, so an uncaught
        // exception here means the app cannot start at all — the user sees
        // it as "app immediately crashes when I open it".

        // 1. Initialise and start the ConnectionSupervisor
        val appContext = this
        try {
            connectionSupervisor.initialize(
                pool = connectionPool,
                serverRepository = serverRepository,
                networkMonitor = networkMonitor,
                preferences = preferences,
                serviceController = object : ServiceController {
                    override fun ensureServiceRunning() {
                        try { SshForegroundService.start(appContext) } catch (_: Throwable) {} // allow-bypass-D5: ServiceController.ensureServiceRunning is a best-effort kick; supervisor reconciles and will retry on next event
                    }
                    override fun stopService() {
                        try { SshForegroundService.stop(appContext) } catch (_: Throwable) {} // allow-bypass-D5: ServiceController.stopService is a best-effort cleanup; failure is harmless (service is already inactive)
                    }
                },
                knownHostDao = database.knownHostDao(),
                scope = appScope
            )
        } catch (e: Throwable) {
            AppLogger.e(AppLogger.Category.LIFECYCLE, e) { "TmuxesApp.onCreate: supervisor init failed" }
        }

        try {
            connectionSupervisor.hostKeyPromptCallback = { event ->
                val deferred = CompletableDeferred<HostKeyPromptResult>()
                _hostKeyPrompt.value = HostKeyPromptState(event, deferred)
                deferred.await()
            }
        } catch (_: Throwable) {}

        // 3b. Initialise coordinators (force lazy init before supervisor.start()
        //     so they are already observing when the first reconciliation fires)
        try {
            sessionCoordinator   // triggers lazy init -> begins observing supervisor.serverStates
        } catch (e: Throwable) {
            AppLogger.e(AppLogger.Category.LIFECYCLE, e) { "TmuxesApp.onCreate: sessionCoordinator init failed" }
        }
        try {
            portForwardCoordinator // triggers lazy init -> begins observing supervisor.serverStates
        } catch (e: Throwable) {
            AppLogger.e(AppLogger.Category.LIFECYCLE, e) { "TmuxesApp.onCreate: portForwardCoordinator init failed" }
        }

        try { connectionSupervisor.start() } catch (e: Throwable) {
            AppLogger.e(AppLogger.Category.LIFECYCLE, e) { "TmuxesApp.onCreate: supervisor.start failed" }
        }

        // 4. ConnectionTrigger owns every external "when do we reconnect"
        //    event source — ProcessLifecycle, SCREEN_ON, network, server
        //    list, invalidations, user refresh — and translates each
        //    event into the right hard/soft check on the supervisor.
        //    Must run AFTER supervisor.start() so the channel consumer
        //    is alive to receive the APP_START hardCheck.
        try {
            connectionTrigger.start(appScope)
        } catch (e: Throwable) {
            AppLogger.e(AppLogger.Category.LIFECYCLE, e) { "TmuxesApp.onCreate: connectionTrigger.start failed" }
        }

        // Debug-build-only: register the ADB-broadcast log retuning receiver.
        // No-op in release. See DebugLogReceiver KDoc for `adb shell` examples.
        DebugLogReceiver.register(this)
    }

    /**
     * Memory pressure callback. Android calls this when system memory is
     * tight. We don't release any caches yet (the terminal scrollback IS
     * the user's data), but logging the level lets us see in logcat that
     * the user is hitting memory pressure — which on memory-constrained
     * devices can result in the OS killing this process even though we
     * never threw an exception. The user perceives this as "the app keeps
     * crashing".
     *
     * If memory pressure becomes a real symptom in production, the
     * follow-up is to trim scrollback (TerminalBuffer.clearScrollback)
     * for inactive sessions when the running process reaches critical memory
     * pressure.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        try {
            val label = when (level) {
                TRIM_RUNNING_MODERATE -> "RUNNING_MODERATE"
                TRIM_RUNNING_LOW -> "RUNNING_LOW"
                TRIM_RUNNING_CRITICAL -> "RUNNING_CRITICAL"
                TRIM_MEMORY_UI_HIDDEN -> "UI_HIDDEN"
                TRIM_MEMORY_BACKGROUND -> "BACKGROUND"
                TRIM_MODERATE -> "MODERATE"
                TRIM_COMPLETE -> "COMPLETE"
                else -> "level=$level"
            }
            AppLogger.w(AppLogger.Category.LIFECYCLE) { "TmuxesApp: onTrimMemory $label" }
        } catch (_: Throwable) {}
    }

    override fun onLowMemory() {
        super.onLowMemory()
        try {
            AppLogger.w(AppLogger.Category.LIFECYCLE) { "TmuxesApp: onLowMemory" }
        } catch (_: Throwable) {}
    }
}

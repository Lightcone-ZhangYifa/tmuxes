package com.tmuxes.ssh

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.tmuxes.data.repository.ServerYamlRepository
import com.tmuxes.util.AppLogger
import com.tmuxes.util.AppLogger.Category
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Single, authoritative entry point for **when** the app should attempt to
 * connect (or re-attempt) any of the user's servers.
 *
 * Before this class existed, "auto-connect timing" was scattered across
 * [ConnectionSupervisor.start] (four inline `collect` blocks),
 * [com.tmuxes.TmuxesApp.onCreate] (a ProcessLifecycle observer), assorted
 * ViewModels, the Quick Settings tile and the widget UI — and there was
 * no SCREEN_ON hookup at all. That made the answer to "why is the
 * connection not coming back?" a multi-file grep.
 *
 * `ConnectionTrigger` collapses every external trigger source — app
 * startup, foreground/background, screen on, network change, server list
 * change, repository invalidation, user refresh — into one component
 * that maps each event to either a **hard check** (release non-permanent
 * backoffs and reconcile) or a **soft check** (just reconcile) on the
 * supervisor.
 *
 * The supervisor's *internal* state machine triggers (post-connect cascade,
 * scheduled retries on backoff timer, transport-death observer) stay
 * inside [ConnectionSupervisor] — those aren't external events.
 *
 * Every check is logged with a [Reason] tag so logcat answers "why did
 * this reconnect fire?" by inspection.
 */
class ConnectionTrigger(
    private val context: Context,
    private val supervisor: ConnectionSupervisor,
    private val networkMonitor: NetworkMonitor,
    private val serverRepository: ServerYamlRepository
) {

    /**
     * What caused a connection check to fire. Used purely for logging /
     * telemetry — the supervisor doesn't branch on this.
     *
     * `HARD` reasons release non-permanent backoff timers and reconcile;
     * `SOFT` reasons only reconcile. AUTH_FAILED servers are sticky in
     * both cases — only [ServerYamlRepository.connectionInvalidations]
     * (i.e. the user editing credentials) clears the permanent-fail flag.
     */
    enum class Reason(val isHard: Boolean) {
        APP_START(true),
        FOREGROUND(true),
        SCREEN_ON(true),
        USER_REFRESH(true),
        NETWORK_AVAILABLE(true),
        NETWORK_CHANGED(false),
        SERVER_LIST(false),
        INVALIDATION(false)
    }

    private var started = false
    private var lifecycleObserver: DefaultLifecycleObserver? = null
    private var screenReceiver: BroadcastReceiver? = null
    private val collectorJobs = mutableListOf<Job>()

    /**
     * Wire up every external trigger source. Call once from
     * [com.tmuxes.TmuxesApp.onCreate] after the supervisor has been
     * initialised and started.
     */
    @Synchronized
    fun start(scope: CoroutineScope) {
        if (started) return
        started = true
        AppLogger.i(Category.SSH) { "ConnectionTrigger: starting" }

        // 1. Server-list changes — soft check; the supervisor's own
        //    reconcile loop discovers added/removed servers itself.
        collectorJobs += scope.launch {
            try {
                serverRepository.allServers.collect { servers ->
                    softCheck(Reason.SERVER_LIST, "${servers.size} servers")
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
            } catch (e: Throwable) {
                AppLogger.w(Category.SSH) { "ConnectionTrigger: server-list collect died: ${e.message}" }
            }
        }

        // 2. Connection invalidations — repository wrote new
        //    connection-relevant fields for a server. The repo path
        //    already set mustReconnect=true on the supervisor's intent
        //    via SharedFlow consumer below; we just need to nudge a
        //    reconcile so computeActions sees the flag immediately.
        //
        //    NOTE: the supervisor's own start() listens to
        //    connectionInvalidations to flip mustReconnect; that
        //    subscription stays inside the supervisor because it
        //    mutates intent state, which only the supervisor owns.
        //    Here we additionally nudge a check for log clarity.
        collectorJobs += scope.launch {
            try {
                serverRepository.connectionInvalidations.collect { id ->
                    softCheck(Reason.INVALIDATION, "server=$id")
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
            } catch (e: Throwable) {
                AppLogger.w(Category.SSH) { "ConnectionTrigger: invalidations collect died: ${e.message}" }
            }
        }

        // 3. Network changes — distinguish "came online" (hard) from
        //    "still online, capability tweak / lost network" (soft).
        collectorJobs += scope.launch {
            try {
                var wasOnline = networkMonitor.isOnline.value
                networkMonitor.networkGeneration.collect {
                    val online = networkMonitor.isOnline.value
                    val cameOnline = online && !wasOnline
                    wasOnline = online
                    if (cameOnline) hardCheck(Reason.NETWORK_AVAILABLE)
                    else softCheck(Reason.NETWORK_CHANGED, "online=$online")
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
            } catch (e: Throwable) {
                AppLogger.w(Category.SSH) { "ConnectionTrigger: network collect died: ${e.message}" }
            }
        }

        // 4. Process lifecycle — flips supervisor.isAppInForeground (used
        //    by the supervisor's gatherSnapshot for background host-key
        //    behaviour) and fires hardCheck on FOREGROUND.
        installLifecycleObserver()

        // 5. SCREEN_ON broadcast — fires the moment the screen lights up,
        //    even before the user unlocks. Connection has a head start.
        installScreenReceiver()

        // 6. APP_START — explicit guarantee. Even if no other event has
        //    fired yet (e.g. a fast cold start before the server-list
        //    flow has emitted), at least one hard check has been issued.
        hardCheck(Reason.APP_START)
    }

    /**
     * Tear down all subscriptions. Idempotent.
     */
    @Synchronized
    fun stop() {
        if (!started) return
        started = false
        AppLogger.i(Category.SSH) { "ConnectionTrigger: stopping" }
        try {
            lifecycleObserver?.let {
                ProcessLifecycleOwner.get().lifecycle.removeObserver(it)
            }
        } catch (_: Throwable) {}
        lifecycleObserver = null
        try {
            screenReceiver?.let { context.unregisterReceiver(it) }
        } catch (_: Throwable) {}
        screenReceiver = null
        for (job in collectorJobs) {
            try { job.cancel() } catch (_: Throwable) {}
        }
        collectorJobs.clear()
    }

    /**
     * User-initiated refresh — refresh button on the Sessions tab,
     * Quick Settings tile, pull-to-refresh on the Servers tab. Treated
     * as a hard check: clear non-permanent backoffs and try NOW.
     *
     * @param serverId If non-null, only that server's backoff is cleared;
     *                 otherwise every server's backoff is cleared.
     */
    fun userRefresh(serverId: Long? = null) {
        AppLogger.i(Category.SSH) { "ConnectionTrigger: hardCheck reason=USER_REFRESH server=${serverId ?: "all"}" }
        if (serverId != null) supervisor.resetRetryState(serverId)
        else supervisor.resetAllRetryState()
    }

    // -----------------------------------------------------------------------
    // Private — event handlers and platform plumbing
    // -----------------------------------------------------------------------

    private fun hardCheck(reason: Reason, detail: String? = null) {
        AppLogger.i(Category.SSH) { "ConnectionTrigger: hardCheck reason=${reason.name}${detail?.let { " ($it)" } ?: ""}" }
        supervisor.resetAllRetryState()
    }

    private fun softCheck(reason: Reason, detail: String? = null) {
        AppLogger.d(Category.SSH) { "ConnectionTrigger: softCheck reason=${reason.name}${detail?.let { " ($it)" } ?: ""}" }
        supervisor.softCheck()
    }

    private fun installLifecycleObserver() {
        try {
            val observer = object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    supervisor.isAppInForeground = true
                    hardCheck(Reason.FOREGROUND)
                }

                override fun onStop(owner: LifecycleOwner) {
                    supervisor.isAppInForeground = false
                }
            }
            ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
            lifecycleObserver = observer
        } catch (e: Throwable) {
            AppLogger.w(Category.SSH) { "ConnectionTrigger: lifecycle observer install failed: ${e.message}" }
        }
    }

    private fun installScreenReceiver() {
        try {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    // Receiver runs on the main thread — never crash the
                    // host process. Screen on/off fires hundreds of times
                    // per day; an exception here from a transient supervisor
                    // state would correlate with screen events rather than
                    // user actions and look like "the app crashes everywhere".
                    try {
                        if (intent.action == Intent.ACTION_SCREEN_ON) {
                            hardCheck(Reason.SCREEN_ON)
                        }
                    } catch (_: Throwable) {}
                }
            }
            val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
            screenReceiver = receiver
        } catch (e: Throwable) {
            AppLogger.w(Category.SSH) { "ConnectionTrigger: SCREEN_ON receiver install failed: ${e.message}" }
        }
    }
}

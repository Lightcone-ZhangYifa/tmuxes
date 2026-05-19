package com.tmuxes.session

import com.tmuxes.ssh.SshConnectionPool
import com.tmuxes.ssh.SshException
import com.tmuxes.ssh.SshSession
import com.tmuxes.ssh.ConnectionSupervisor
import com.tmuxes.ssh.shellEscape
import com.tmuxes.terminal.emulator.TerminalEmulator
import com.tmuxes.terminal.emulator.TerminalListener
import com.tmuxes.util.AppLogger
import com.tmuxes.util.AppLogger.Category
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Unified session management for both in-app terminal and widget surfaces.
 *
 * Observes [ConnectionSupervisor.serverStates] to automatically reattach
 * DISCONNECTED sessions when a server transitions back to CONNECTED.
 * Manages the full lifecycle: attach, read loop, EOF handling, reattach,
 * detach, and cleanup.
 *
 * Owns the shared session logic used by the app terminal and widgets.
 */
class SessionCoordinator(
    private val connectionPool: SshConnectionPool,
    private val supervisor: ConnectionSupervisor,
    private val scope: CoroutineScope
) {
    private val _sessions = MutableStateFlow<Map<SessionKey, ManagedSession>>(emptyMap())
    val sessions: StateFlow<Map<SessionKey, ManagedSession>> = _sessions.asStateFlow()

    /** Protects the check-then-create pattern in [attach]. */
    private val coordinatorMutex = Mutex()

    /**
     * All active resize buses, indexed by (session key, consumer id).
     * One bus per (consumer × session). Subtype is chosen by ConsumerType:
     * APP_TERMINAL → [InAppResizeBus] (yields to widget on hidden);
     * WIDGET → [WidgetResizeBus] (independent, publishes to registry).
     */
    private val resizeBuses = mutableMapOf<Pair<SessionKey, ConsumerId>, SessionResizeBus>()
    private val busMutex = Mutex()

    /**
     * Logical (server, sessionName) → last widget size. Cross-PTY link
     * source of truth. Updated by [publishWidgetSize] on every widget bus
     * emit; read by [InAppResizeBus] when it goes hidden so the in-app
     * PTY can yield to the widget's size (preventing tmux from min-clamping
     * the remote window down to the in-app size while the user is on the
     * home screen).
     *
     * In-memory only — no persistence; widget bus repopulates on restart.
     */
    private val linkedWidgetSizes = mutableMapOf<TmuxKey, Pair<Int, Int>>()
    private val linkMutex = Mutex()

    /** Create or fetch the resize bus for this (session, consumer). */
    suspend fun attachResizeBus(key: SessionKey, consumer: ConsumerId): SessionResizeBus {
        val tmuxKey = TmuxKey(key.serverId, key.sessionName)
        return busMutex.withLock {
            resizeBuses.getOrPut(Pair(key, consumer)) {
                when (consumer.type) {
                    ConsumerType.APP_TERMINAL -> InAppResizeBus(key, tmuxKey, this, scope)
                    ConsumerType.WIDGET -> WidgetResizeBus(key, tmuxKey, this, scope)
                }
            }
        }
    }

    /** Drop the bus for this (session, consumer). Called from consumer detach. */
    suspend fun detachResizeBus(key: SessionKey, consumer: ConsumerId) {
        busMutex.withLock { resizeBuses.remove(Pair(key, consumer)) }
    }

    /** Snapshot of all buses currently registered for [key]. */
    private suspend fun busesFor(key: SessionKey): List<SessionResizeBus> =
        busMutex.withLock { resizeBuses.entries.filter { it.key.first == key }.map { it.value } }

    /**
     * Read the last widget size linked to this logical tmux session.
     * Called by [InAppResizeBus.setVisible] (false branch) +
     * [InAppResizeBus.replayAfterReconnect] when hidden.
     * Returns null if no widget bus has ever emitted for this tmuxKey.
     */
    suspend fun linkedWidgetSize(tmuxKey: TmuxKey): Pair<Int, Int>? =
        linkMutex.withLock { linkedWidgetSizes[tmuxKey] }

    /**
     * Record this widget's most-recent size and notify any in-app bus on
     * the same logical tmux session so it can yield (if currently hidden).
     * Called by [WidgetResizeBus.emitLocked] only.
     */
    suspend fun publishWidgetSize(tmuxKey: TmuxKey, size: Pair<Int, Int>) {
        linkMutex.withLock { linkedWidgetSizes[tmuxKey] = size }
        // Notify every in-app bus on the same logical tmux session.
        val inAppBuses = busMutex.withLock {
            resizeBuses.values.filterIsInstance<InAppResizeBus>().filter { it.tmuxKey == tmuxKey }
        }
        for (bus in inAppBuses) bus.onLinkedWidgetSizeChanged(size)
    }

    init {
        // Self-driven restoration: observe server state changes and reattach
        // DISCONNECTED sessions when their server becomes CONNECTED again.
        // Also transitions sessions to ENDED when their server is removed.
        //
        // Wrapped in try/catch at every level — a broken state emission must
        // NOT kill the observer permanently (that would leave sessions stuck
        // in DISCONNECTED forever until app restart) and must NOT propagate
        // to the scope's uncaught handler.
        scope.launch {
            try {
                supervisor.serverStates.collect { states ->
                    try {
                        val activeServerIds = states.keys
                        for ((_, session) in _sessions.value) {
                            try {
                                val serverState = states[session.key.serverId]
                                when {
                                    // Server reconnected and session is waiting for recovery
                                    session.state.value == SessionState.DISCONNECTED && serverState?.isUsable == true -> {
                                        launch {
                                            try { reattach(session) } catch (ce: kotlinx.coroutines.CancellationException) {
                                                throw ce
                                            } catch (e: Exception) {
                                                AppLogger.w(Category.SESSION) { "SessionCoordinator: reattach failed for ${session.key}: ${e.message}" }
                                            }
                                        }
                                    }
                                    // Server removed from supervisor (deleted) while session still alive
                                    session.state.value in setOf(SessionState.DISCONNECTED, SessionState.ACTIVE, SessionState.RECONNECTING)
                                            && session.key.serverId !in activeServerIds -> {
                                        session.mutex.withLock {
                                            session.updateState(SessionState.ENDED)
                                            session.sshSession?.let { ssh ->
                                                session.sshSession = null
                                                scope.launch { try { ssh.close() } catch (_: Exception) {} }
                                            }
                                        }
                                        publishSessions()
                                    }
                                }
                            } catch (ce: kotlinx.coroutines.CancellationException) {
                                throw ce
                            } catch (e: Exception) {
                                AppLogger.w(Category.SESSION) { "SessionCoordinator: per-session handler failed: ${e.message}" }
                            }
                        }
                    } catch (ce: kotlinx.coroutines.CancellationException) {
                        throw ce
                    } catch (e: Exception) {
                        AppLogger.w(Category.SESSION) { "SessionCoordinator: state handler failed: ${e.message}" }
                    }
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                // normal shutdown
            } catch (e: Exception) {
                AppLogger.e(Category.SESSION, e) { "SessionCoordinator: serverStates collector died" }
            }
        }
    }

    /**
     * Attach to a tmux session. Returns an existing [ManagedSession] if one
     * is already alive for the computed [SessionKey], otherwise creates a new one.
     *
     * The consumer is tracked so that [detach] can determine when no one is
     * using the session anymore.
     */
    suspend fun attach(request: AttachRequest): ManagedSession = coordinatorMutex.withLock {
        val qualifier = when (request.consumer.type) {
            ConsumerType.APP_TERMINAL -> "app"
            ConsumerType.WIDGET -> "widget_${request.consumer.id}"
        }
        val key = SessionKey(request.serverId, request.sessionName, qualifier)

        // Check for existing session
        val existing = _sessions.value[key]
        if (existing != null) {
            if (existing.state.value != SessionState.ENDED) {
                // Alive session: add consumer and return
                existing.mutex.withLock { existing.consumers.add(request.consumer) }
                existing.emulator.setMaxScrollback(request.scrollbackLines)
                return@withLock existing
            }
            // ENDED session: remove stale entry, create fresh below
            _sessions.update { it - key }
        }

        // Create new session
        val connection = connectionPool.get(request.serverId)
            ?: throw SshException("Not connected (serverId=${request.serverId})")

        val sshSession = openTmuxSession(connection, request.sessionName, request.rows, request.cols)
        val emulator = TerminalEmulator(
            rows = request.rows,
            columns = request.cols,
            maxScrollback = request.scrollbackLines
        )

        // Wire emulator response callback (DA replies, cursor reports) -> SSH
        emulator.dataOutputCallback = { data ->
            scope.launch {
                try { sshSession.write(data) } catch (e: Exception) {
                    AppLogger.w(Category.SESSION) { "SessionCoordinator: emulator write-back failed for $key: ${e.message}" }
                }
            }
        }

        // Wire terminal listener (bell, title change) if provided
        request.terminalListener?.let { emulator.listener = it }

        val session = ManagedSession(
            key = key,
            serverName = request.serverName,
            serverColor = request.serverColor,
            emulator = emulator
        )
        session.sshSession = sshSession
        session.consumers.add(request.consumer)
        session.generation = 0

        startReadLoop(session)

        _sessions.update { it + (key to session) }

        AppLogger.i(Category.SESSION) { "SessionCoordinator: attached $key (${request.cols}x${request.rows})" }
        return@withLock session
    }

    /**
     * Send user keyboard input to the SSH session for the given key.
     * Acquires the per-session mutex to safely access the current sshSession.
     */
    suspend fun sendInput(key: SessionKey, data: ByteArray) {
        val session = _sessions.value[key] ?: return
        session.mutex.withLock {
            session.sshSession?.write(data)
        }
    }

    /**
     * Resize the SSH PTY of the given session. The emulator buffer is resized
     * separately by the rendering view (e.g. TerminalView) — this only sends
     * the window-change message over SSH.
     */
    suspend fun resize(key: SessionKey, cols: Int, rows: Int) {
        if (cols <= 0 || rows <= 0) return
        val session = _sessions.value[key] ?: return
        // Try the cheap path first: send a window-change message over the
        // existing SSH session. If that throws, the session is dead — mark it
        // DISCONNECTED so the serverStates collector picks it up and triggers
        // a full reattach (which will pick up the new emulator dimensions).
        var resizeFailed = false
        session.mutex.withLock {
            val ssh = session.sshSession
            if (ssh == null) {
                resizeFailed = true
            } else {
                try {
                    ssh.resize(cols, rows)
                } catch (e: Exception) {
                    AppLogger.d(Category.SESSION) { "SessionCoordinator: resize failed for $key (will reattach): ${e.message}" }
                    resizeFailed = true
                }
            }
            if (resizeFailed && session.state.value == SessionState.ACTIVE) {
                session.updateState(SessionState.DISCONNECTED)
                publishSessions()
            }
        }
        // Trigger a reattach attempt outside the per-session lock to avoid
        // deadlock with reattach()'s own mutex.withLock.
        if (resizeFailed) {
            val supervisorState = supervisor.serverStates.value[session.key.serverId]
            if (supervisorState?.isUsable == true) {
                scope.launch { reattach(session) }
            }
        }
    }

    /**
     * Rename an attached session in place. Used after a tmux rename so
     * the SessionKey we use to address the live SSH PTY follows the
     * new tmux session name. The underlying SshSession and emulator
     * are NOT touched — only the map key and the ManagedSession.key
     * field update.
     *
     * Without this, an app rename of an attached session orphaned the
     * coordinator entry: subsequent sendInput calls (which look up by
     * the NEW key from `_activeSessionKey`) would silently miss and
     * the user's keystrokes would vanish until a full reattach.
     *
     * Renames every entry whose key matches `(serverId, oldName)` —
     * i.e., both the "app" qualifier and any "widget_*" qualifiers
     * for the same logical session.
     */
    suspend fun rename(serverId: Long, oldName: String, newName: String) = coordinatorMutex.withLock {
        if (oldName == newName) return@withLock
        val current = _sessions.value
        val matching = current.filter { (k, _) -> k.serverId == serverId && k.sessionName == oldName }
        if (matching.isEmpty()) return@withLock
        val updated = current.toMutableMap()
        for ((oldKey, session) in matching) {
            val newKey = oldKey.copy(sessionName = newName)
            session.mutex.withLock { session.key = newKey }
            updated.remove(oldKey)
            updated[newKey] = session
        }
        _sessions.value = updated
        AppLogger.i(Category.SESSION) { "SessionCoordinator: renamed ${matching.size} session(s) from $oldName to $newName" }
    }

    /**
     * Remove a consumer from the session. If no consumers remain and the
     * session is ENDED, it is cleaned up immediately. If the session is
     * ACTIVE or DISCONNECTED with no consumers, it is kept alive for
     * potential reattach or new consumer (e.g., screen rotation).
     */
    fun detach(key: SessionKey, consumer: ConsumerId) {
        val session = _sessions.value[key] ?: return
        scope.launch {
            session.mutex.withLock {
                session.consumers.remove(consumer)
                if (session.consumers.isEmpty() && session.state.value == SessionState.ENDED) {
                    // Atomic CAS update — never raw assignment, to avoid losing
                    // a concurrent attach()'s addition.
                    _sessions.update { it - key }
                    session.sshSession?.let { ssh ->
                        session.sshSession = null
                        scope.launch { try { ssh.close() } catch (_: Exception) {} }
                    }
                }
            }
        }
    }

    /**
     * Force-close a single session: close the SSH channel and remove from the map.
     */
    fun close(key: SessionKey) {
        val session = _sessions.value[key] ?: return
        _sessions.update { it - key }
        scope.launch {
            session.mutex.withLock {
                session.updateState(SessionState.ENDED)
                session.sshSession?.let { ssh ->
                    session.sshSession = null
                    try { ssh.close() } catch (_: Exception) {}
                }
            }
        }
        AppLogger.d(Category.SESSION) { "SessionCoordinator: closed $key" }
    }

    /**
     * Force-close every coordinator entry that points at the same
     * logical (serverId, sessionName) — i.e., the "app" qualifier and
     * any "widget_*" qualifiers. Used after a tmux session kill so
     * widgets attached to the killed session don't keep stale
     * SessionCoordinator entries that take ~100ms to be cleaned up
     * via the EOF path.
     *
     * Mirrors the rename path: every consumer that pointed at a session
     * by (serverId, sessionName) is updated atomically when that session
     * goes away.
     */
    fun closeAllForSession(serverId: Long, sessionName: String) {
        val matching = _sessions.value.filter { (k, _) ->
            k.serverId == serverId && k.sessionName == sessionName
        }
        if (matching.isEmpty()) return
        _sessions.update { it - matching.keys }
        for ((key, session) in matching) {
            scope.launch {
                session.mutex.withLock {
                    session.updateState(SessionState.ENDED)
                    session.sshSession?.let { ssh ->
                        session.sshSession = null
                        try { ssh.close() } catch (_: Exception) {}
                    }
                }
            }
            AppLogger.d(Category.SESSION) { "SessionCoordinator: closed $key (closeAllForSession)" }
        }
    }

    /**
     * Close all sessions and clear the map. Called during app shutdown.
     */
    fun closeAll() {
        val snapshot = _sessions.value
        _sessions.update { emptyMap() }
        for ((_, session) in snapshot) {
            scope.launch {
                session.mutex.withLock {
                    session.updateState(SessionState.ENDED)
                    session.sshSession?.let { ssh ->
                        session.sshSession = null
                        try { ssh.close() } catch (_: Exception) {}
                    }
                }
            }
        }
        AppLogger.i(Category.SESSION) { "SessionCoordinator: closed all sessions" }
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    /**
     * Start the read loop that feeds SSH output into the emulator.
     * The [SshSession.startReading] stores the job internally and cancels it
     * when [SshSession.close] is called.
     */
    private fun startReadLoop(session: ManagedSession) {
        val gen = session.generation
        session.sshSession?.startReading(
            scope = scope,
            onData = { data -> session.emulator.processInput(data) },
            onEnd = { onSessionEof(session, gen) }
        )
    }

    /**
     * Handle EOF from the read loop. Discriminates between transport loss
     * (server disconnected) and tmux session termination (server still up).
     *
     * The [generation] guard prevents a stale EOF from a previous session
     * from clobbering the state of a newer reattached session.
     *
     * Discrimination uses the LIVE pool connection's [SshConnection.isConnected]
     * rather than [supervisor.serverStates], because the supervisor's cached
     * state may not yet reflect a transport that just died (the disconnect
     * listener fires before reconcile updates the cached state). Reading the
     * live state avoids the race that would otherwise cause a network drop
     * to be misclassified as "tmux session killed" and prevent auto-reattach.
     */
    private fun onSessionEof(session: ManagedSession, generation: Int) {
        scope.launch {
            session.mutex.withLock {
                if (session.generation != generation) {
                    AppLogger.t(Category.SESSION) {
                        "coord.eof STALE key=${session.key} gen=$generation current=${session.generation} (skip)"
                    }
                    return@withLock
                }

                // If we already explicitly transitioned to ENDED (via
                // close / closeAllForSession / kill), the EOF is the
                // tail end of the close we just performed — do NOT
                // overwrite the terminal state with DISCONNECTED, even
                // if the transport happens to be down right now. The
                // user explicitly killed this session and should see
                // the SessionEndedOverlay, not the "Connection lost"
                // banner.
                if (session.state.value == SessionState.ENDED) {
                    AppLogger.d(Category.SESSION) { "coord.eof confirmed ENDED key=${session.key}" }
                    session.sshSession = null
                    publishSessions()
                    return@withLock
                }

                val conn = connectionPool.get(session.key.serverId)
                val transportAlive = try { conn?.isConnected() == true } catch (_: Exception) { false }
                if (transportAlive) {
                    // Server still connected but shell EOF -> tmux session killed
                    AppLogger.i(Category.SESSION) {
                        "coord.eof tmux ENDED key=${session.key} (transport alive)"
                    }
                    session.updateState(SessionState.ENDED)
                } else {
                    // Connection lost -> transport failure, awaiting recovery
                    AppLogger.i(Category.SESSION) {
                        "coord.eof transport lost key=${session.key} → DISCONNECTED awaiting recovery"
                    }
                    session.updateState(SessionState.DISCONNECTED)
                }
                session.sshSession = null
                publishSessions()
            }
        }
    }

    /**
     * Reattach a DISCONNECTED session by opening a new SSH shell and
     * re-attaching to the same tmux session. The emulator is preserved
     * (retains scrollback).
     */
    private suspend fun reattach(session: ManagedSession) {
        session.mutex.withLock {
            // Only reattach if still DISCONNECTED (could have been closed meanwhile)
            if (session.state.value != SessionState.DISCONNECTED) {
                AppLogger.t(Category.SESSION) {
                    "coord.reattach skip key=${session.key} state=${session.state.value}"
                }
                return
            }

            session.updateState(SessionState.RECONNECTING)
            session.generation++
            AppLogger.i(Category.SESSION) {
                "coord.reattach → key=${session.key} gen=${session.generation} size=${session.emulator.buffer.columns}x${session.emulator.buffer.rows}"
            }
            publishSessions()

            // Close old SSH session (triggers onEnd with stale generation -> ignored)
            session.sshSession?.let { oldSsh ->
                session.sshSession = null
                try { oldSsh.close() } catch (_: Exception) {}
            }

            try {
                val connection = connectionPool.get(session.key.serverId)
                    ?: throw SshException("Not connected (serverId=${session.key.serverId})")

                val newSshSession = openTmuxSession(
                    connection,
                    session.key.sessionName,
                    session.emulator.buffer.rows,
                    session.emulator.buffer.columns
                )

                // Re-wire emulator response callback to the new SSH session
                session.emulator.dataOutputCallback = { data ->
                    scope.launch {
                        try { newSshSession.write(data) } catch (e: Exception) {
                            AppLogger.w(Category.SESSION) { "SessionCoordinator: emulator write-back failed for ${session.key}: ${e.message}" }
                        }
                    }
                }

                session.sshSession = newSshSession
                startReadLoop(session)
                session.updateState(SessionState.ACTIVE)
                publishSessions()

                AppLogger.i(Category.SESSION) { "SessionCoordinator: reattached ${session.key}" }

                // Replay every consumer's last-known size against the fresh
                // PTY. allocatePTY used the emulator buffer's cached size,
                // which can be stale (e.g., a widget resized while the
                // session was disconnected — the bus has the truth).
                for (bus in busesFor(session.key)) {
                    bus.replayAfterReconnect()
                }
            } catch (e: Exception) {
                // Determine if tmux session is gone or just a transient error
                val message = e.message.orEmpty()
                if (message.contains("session not found", ignoreCase = true) ||
                    message.contains("no sessions", ignoreCase = true)
                ) {
                    session.updateState(SessionState.ENDED)
                    AppLogger.w(Category.SESSION) { "SessionCoordinator: reattach failed (tmux gone) for ${session.key}: $message" }
                } else {
                    session.updateState(SessionState.DISCONNECTED)
                    AppLogger.w(Category.SESSION) { "SessionCoordinator: reattach failed (transient) for ${session.key}: $message" }
                }
                publishSessions()
            }
        }
    }

    /**
     * Allocate a PTY and exec `tmux new-session -A -s <name>` directly inside
     * it. The whole app is tmux-only, so there is no free-shell branch.
     * `tmux new-session -A -s <name>` attaches if the session exists,
     * otherwise creates it.
     */
    private suspend fun openTmuxSession(
        connection: com.tmuxes.ssh.SshConnection,
        sessionName: String,
        rows: Int,
        cols: Int
    ): SshSession {
        val command = "tmux new-session -A -s ${shellEscape(sessionName)}"
        return connection.openSession(rows, cols, command)
    }

    /**
     * Trigger a new emission of the sessions StateFlow so observers are notified.
     */
    private fun publishSessions() {
        _sessions.value = _sessions.value.toMap()
    }
}

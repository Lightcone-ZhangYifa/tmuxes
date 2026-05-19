package com.tmuxes.session

import com.tmuxes.ssh.SshSession
import com.tmuxes.terminal.emulator.TerminalBuffer
import com.tmuxes.terminal.emulator.TerminalEmulator
import com.tmuxes.terminal.emulator.TerminalListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex

data class SessionKey(
    val serverId: Long,
    val sessionName: String,
    val qualifier: String
)

data class ConsumerId(val type: ConsumerType, val id: String)
enum class ConsumerType { APP_TERMINAL, WIDGET }

enum class SessionState {
    ACTIVE,
    DISCONNECTED,
    RECONNECTING,
    ENDED
}

class ManagedSession(
    /**
     * The session's identity key. Mutable because tmux session renames
     * (issued through [SessionCoordinator.rename]) update the key in
     * place: the underlying SSH PTY and emulator are unchanged, only
     * the logical name we use to address them needs to update so
     * sendInput / resize / detach continue to find the session by its
     * new name.
     */
    var key: SessionKey,
    val serverName: String,
    val serverColor: Int,
    val emulator: TerminalEmulator,
    private val _state: MutableStateFlow<SessionState> = MutableStateFlow(SessionState.ACTIVE)
) {
    val state: StateFlow<SessionState> = _state.asStateFlow()

    /** Convenience accessors for UI consumers. */
    val sessionName: String get() = key.sessionName
    val serverId: Long get() = key.serverId
    val isEnded: Boolean get() = state.value == SessionState.ENDED
    val isActive: Boolean get() = state.value == SessionState.ACTIVE

    internal val mutex = Mutex()
    internal val consumers = mutableSetOf<ConsumerId>()
    internal var generation: Int = 0
    internal var sshSession: SshSession? = null

    internal fun updateState(newState: SessionState) {
        _state.value = newState
    }
}

data class AttachRequest(
    val serverId: Long,
    val sessionName: String,
    val serverName: String,
    val serverColor: Int = 0,
    val rows: Int,
    val cols: Int,
    val scrollbackLines: Int = TerminalBuffer.DEFAULT_SCROLLBACK,
    val consumer: ConsumerId,
    val terminalListener: TerminalListener? = null
)

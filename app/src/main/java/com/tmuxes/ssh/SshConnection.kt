package com.tmuxes.ssh

import kotlinx.coroutines.flow.StateFlow

/**
 * SSH connection abstraction.
 *
 * The sole concrete implementation is [DirectSshConnection]. A connection always
 * owns its own SSHJ [net.schmizz.sshj.SSHClient]; if the server has a parent, the
 * parent's [DirectSshConnection.createTunnel] supplies a direct-tcpip channel that
 * SSHJ wraps as the underlying socket (native ProxyJump). This makes channel
 * multiplexing, host-key verification and keepalive the same on every server,
 * including those reached through one or more jump hosts.
 *
 * The [state] StateFlow exposes the transport-level [ConnectionState]. The
 * [ConnectionSupervisor] observes these states only on failure transitions
 * (ERROR / DISCONNECTED) to detect silent transport death without creating a
 * feedback loop on successful connects.
 *
 * UI consumers should NOT observe this directly — they observe the richer
 * `ConnectionSupervisor.serverStates` which tracks per-server retry counts,
 * parent relationships, network state, etc.
 */
interface SshConnection {
    val serverId: Long
    val state: StateFlow<ConnectionState>

    suspend fun connect()
    suspend fun disconnect()
    fun isConnected(): Boolean

    /**
     * Allocate a PTY and exec [command] inside it, returning the live
     * [SshSession] for interactive I/O. The returned session's lifetime
     * is tied to the remote process — when [command] exits, the channel
     * closes and the user is NOT dropped into a login shell.
     *
     * The whole app uses tmux, so [command] is always a tmux invocation
     * (e.g. `tmux new-session -A -s name`) — there is no free-shell
     * surface anywhere in the codebase.
     */
    suspend fun openSession(rows: Int, cols: Int, command: String): SshSession

    suspend fun executeCommand(command: String, timeoutMs: Long = 30_000): String
    suspend fun setupLocalPortForward(localPort: Int, remoteHost: String, remotePort: Int): PortForwardHandle
    suspend fun openChannel(command: String): SshChannel
}

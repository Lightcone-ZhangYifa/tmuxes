package com.tmuxes.ssh

/**
 * Internal connection state for [SshConnection] implementations.
 *
 * Used by [DirectSshConnection] to expose a transport-level state via
 * [SshConnection.state]. The Supervisor observes these transitions to
 * detect transport death.
 *
 * The richer business-facing state model is [ServerStatus] / [ServerConnectionState]
 * (in [ServerConnectionState.kt]) — UI consumers observe `supervisor.serverStates`,
 * not this enum.
 *
 * Auth-vs-network failure classification lives at the supervisor layer
 * (via `isAuthError()` on the thrown exception), not here. The transport
 * state is the same `ERROR` for both cases.
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    AUTHENTICATED,
    ERROR
}

package com.tmuxes.ssh

enum class ServerStatus(val label: String, val isError: Boolean) {
    IDLE("Idle", false),
    DISCONNECTED("Disconnected", false),
    CONNECTING("Connecting", false),
    CONNECTED("Connected", false),
    NETWORK_ERROR("Network error", true),
    AUTH_FAILED("Auth failed", true),
    WAITING_PARENT("Waiting for parent", false),
    PARENT_FAILED("Parent failed", true),
    PAUSED("Paused", false),
    NO_NETWORK("No network", false),
    WAITING_HOST_KEY("Waiting for host key", false);
}

data class ParentInfo(
    val parentId: Long,
    val parentName: String,
    val parentStatus: ServerStatus
)

/**
 * Snapshot of one server's connection lifecycle published by
 * [ConnectionSupervisor]. `nextRetryAt` is a `SystemClock.elapsedRealtime`
 * deadline; UI countdown displays consume it via the
 * `rememberRetryCountdown(nextRetryAt)` Compose hook in
 * `com.tmuxes.ui.components.app.RetryCountdown` — never read it as a
 * one-shot snapshot from inside a composable, that gives a static number
 * that does not tick.
 */
data class ServerConnectionState(
    val status: ServerStatus,
    val errorMessage: String? = null,
    val retryCount: Int = 0,
    val nextRetryAt: Long? = null,
    val parentInfo: ParentInfo? = null
) {
    val isUsable: Boolean get() = status == ServerStatus.CONNECTED
}

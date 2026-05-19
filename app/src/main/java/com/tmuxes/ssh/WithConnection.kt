package com.tmuxes.ssh

import com.tmuxes.util.AppLogger
import com.tmuxes.util.AppLogger.Category
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Get the [SshConnection] for [serverId] from [pool] and execute [block] with it.
 *
 * Throws [SshException] if the server has no entry in the pool, or if its underlying
 * transport is not currently authenticated.
 *
 * Eliminates the duplicated "get connection, check connected, throw on error" pattern
 * from ViewModels and other callers (fixes architecture issue A7).
 */
suspend fun <T> withConnection(
    pool: SshConnectionPool,
    serverId: Long,
    block: suspend (SshConnection) -> T
): T {
    val connection = pool.get(serverId)
        ?: run {
            AppLogger.w(Category.SSH) { "withConnection ✗ id=$serverId — no pool entry" }
            throw SshException("Server $serverId not connected")
        }
    if (!connection.isConnected()) {
        AppLogger.w(Category.SSH) { "withConnection ✗ id=$serverId — pool entry not connected (state=${connection.state.value})" }
        throw SshException("Server $serverId connection lost")
    }
    return block(connection)
}

/**
 * Like [withConnection], but first waits up to [waitMs] for the pool to contain
 * a live connection for [serverId]. Issues a user-refresh through the
 * [ConnectionTrigger] so that a server stuck in NETWORK_ERROR backoff is
 * released and retried immediately — these helpers run in the middle of an
 * explicit user action (kill / create / rename session, execute snippet, etc.)
 * and the user expects "try NOW", not "wait for the next backoff tick".
 *
 * If the connection does not come up in time, the block is never invoked and
 * an [SshException] is thrown — callers surface this as an error message.
 *
 * Routing through [ConnectionTrigger] (rather than poking the supervisor
 * directly) keeps **every** auto-connect entry point funnelled through one
 * component, so "why is the connection coming up right now?" has a single
 * answer in logcat.
 *
 * @param pool SSH connection pool.
 * @param trigger Connection trigger used to issue a USER_REFRESH check.
 * @param serverId Target server id.
 * @param waitMs How long to wait for the connection to become live.
 * @param pollIntervalMs How often to re-check the pool while waiting.
 */
suspend fun <T> withConnectionWaiting(
    pool: SshConnectionPool,
    trigger: ConnectionTrigger,
    serverId: Long,
    waitMs: Long = 10_000L,
    pollIntervalMs: Long = 200L,
    block: suspend (SshConnection) -> T
): T {
    // Fast path: connection is already live.
    pool.get(serverId)?.let { live ->
        if (live.isConnected()) return block(live)
    }

    // Hard-check via the trigger: release any non-permanent backoff timer
    // for this server and reconcile NOW. Cheap and idempotent — the
    // supervisor may already have a connect job in flight, in which case
    // resetRetryState is a no-op for that intent.
    AppLogger.d(Category.SSH) { "withConnectionWaiting → trigger refresh id=$serverId waitMs=$waitMs" }
    trigger.userRefresh(serverId)

    val waited = withTimeoutOrNull(waitMs) {
        while (true) {
            val c = pool.get(serverId)
            if (c != null && c.isConnected()) return@withTimeoutOrNull c
            delay(pollIntervalMs)
        }
        @Suppress("UNREACHABLE_CODE")
        null as SshConnection?
    }

    val connection = waited
        ?: run {
            AppLogger.w(Category.SSH) {
                "withConnectionWaiting ✗ id=$serverId waited=${waitMs}ms — supervisor never produced a live connection"
            }
            throw SshException("Server $serverId not connected (waited ${waitMs}ms)")
        }

    return try {
        block(connection)
    } catch (_: TimeoutCancellationException) {
        AppLogger.w(Category.SSH) { "withConnectionWaiting block ✗ id=$serverId — operation timed out" }
        throw SshException("Operation timed out")
    }
}

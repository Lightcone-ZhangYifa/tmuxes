package com.tmuxes.ssh

import com.tmuxes.data.db.KnownHostDao
import com.tmuxes.data.model.KnownHostEntity
import com.tmuxes.util.AppLogger
import com.tmuxes.util.AppLogger.Category
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.security.PublicKey

/**
 * Result of host key verification when user interaction is needed.
 */
enum class HostKeyPromptResult {
    /** Trust & Remember -- store the key in the database. */
    ACCEPT,
    /** Trust for this session only -- allow the connection but do NOT store the key. */
    TRUST_ONCE,
    /** Reject the connection. */
    REJECT
}

/**
 * Describes what happened during host key verification.
 */
sealed class HostKeyEvent {
    /** Host key is unknown and needs user approval. */
    data class Unknown(
        val hostname: String,
        val port: Int,
        val keyType: String,
        val fingerprint: String
    ) : HostKeyEvent()

    /** Host key has changed from a previously stored value. */
    data class Changed(
        val hostname: String,
        val port: Int,
        val keyType: String,
        val oldFingerprint: String,
        val newFingerprint: String
    ) : HostKeyEvent()
}

/**
 * Custom host key verifier that checks against stored known hosts
 * and delegates to a user-facing callback when the host is unknown
 * or has a changed key.
 *
 * @param knownHostDao DAO for persisting known host records.
 * @param hostname The hostname being connected to.
 * @param port The port being connected to.
 * @param promptCallback Suspending callback invoked to ask the user whether to
 *                       accept an unknown or changed host key. Receives a [HostKeyEvent]
 *                       and must return a [HostKeyPromptResult].
 */
class AppHostKeyVerifier(
    private val knownHostDao: KnownHostDao,
    private val hostname: String,
    private val port: Int,
    private val promptCallback: suspend (HostKeyEvent) -> HostKeyPromptResult
) : HostKeyVerifier {

    /**
     * Internal blocking bridge. SSHJ calls [verify] synchronously from its
     * own transport thread, so we use `runBlocking` to bridge into
     * suspend-land. The whole thing is wrapped in try/catch because any
     * exception (SQLite failure, user-dialog crash, prompt timeout) that
     * escapes this method propagates to the SSHJ transport thread — which
     * is NOT managed by our [TmuxesApp.appScope] handler and would crash
     * the process. On any error we fail-safe by rejecting the host key.
     */
    override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
        return try {
            kotlinx.coroutines.runBlocking {
                kotlinx.coroutines.withTimeoutOrNull(60_000) {
                    try {
                        verifyAsync(key)
                    } catch (t: Throwable) {
                        AppLogger.e(Category.HOSTKEY, t) {
                            "hostkey.verifyAsync ✗ host=$hostname:$port → REJECT"
                        }
                        false
                    }
                } ?: run {
                    AppLogger.w(Category.HOSTKEY) {
                        "hostkey.verify ✗ host=$hostname:$port cause='timeout 60s waiting for user' → REJECT"
                    }
                    false
                }
            }
        } catch (t: Throwable) {
            AppLogger.e(Category.HOSTKEY, t) {
                "hostkey.verify ✗ host=$hostname:$port (outer) → REJECT"
            }
            false
        }
    }

    private suspend fun verifyAsync(key: PublicKey): Boolean {
        val fingerprint = SecurityUtils.getFingerprint(key)
        val keyType = KeyType.fromKey(key).toString()

        val existingHosts = knownHostDao.findByHostPort(hostname, port)

        // Check if we already trust this exact key
        val matchingHost = existingHosts.find { it.fingerprint == fingerprint && it.trusted }
        if (matchingHost != null) {
            AppLogger.d(Category.HOSTKEY) {
                "hostkey.trusted host=$hostname:$port keyType=$keyType fp=$fingerprint"
            }
            return true
        }

        // Check if there is a stored key with a different fingerprint (key changed)
        val changedHost = existingHosts.find {
            it.keyType == keyType && it.fingerprint != fingerprint && it.trusted
        }

        val event = if (changedHost != null) {
            AppLogger.w(Category.HOSTKEY) {
                "hostkey.CHANGED host=$hostname:$port keyType=$keyType " +
                "old=${changedHost.fingerprint} new=$fingerprint — prompting user"
            }
            HostKeyEvent.Changed(
                hostname = hostname,
                port = port,
                keyType = keyType,
                oldFingerprint = changedHost.fingerprint,
                newFingerprint = fingerprint
            )
        } else {
            AppLogger.i(Category.HOSTKEY) {
                "hostkey.unknown host=$hostname:$port keyType=$keyType fp=$fingerprint — prompting user"
            }
            HostKeyEvent.Unknown(
                hostname = hostname,
                port = port,
                keyType = keyType,
                fingerprint = fingerprint
            )
        }

        val result = promptCallback(event)

        return when (result) {
            HostKeyPromptResult.ACCEPT -> {
                // Remove old entries for this host+port+keyType before inserting
                if (changedHost != null) {
                    knownHostDao.delete(changedHost)
                }
                knownHostDao.upsert(
                    KnownHostEntity(
                        hostname = hostname,
                        port = port,
                        keyType = keyType,
                        fingerprint = fingerprint,
                        addedAt = System.currentTimeMillis(),
                        trusted = true
                    )
                )
                AppLogger.i(Category.HOSTKEY) {
                    "hostkey.accept ← stored host=$hostname:$port keyType=$keyType fp=$fingerprint"
                }
                true
            }
            HostKeyPromptResult.TRUST_ONCE -> {
                AppLogger.i(Category.HOSTKEY) {
                    "hostkey.trust_once host=$hostname:$port keyType=$keyType (not persisted)"
                }
                true
            }
            HostKeyPromptResult.REJECT -> {
                AppLogger.w(Category.HOSTKEY) {
                    "hostkey.reject host=$hostname:$port keyType=$keyType (user declined)"
                }
                false
            }
        }
    }

    /**
     * Returns an empty list — we manage known hosts through [KnownHostDao],
     * not through SSHJ's built-in known-hosts file mechanism. Wrapped in
     * try/catch because it runs on SSHJ's transport thread — any uncaught
     * exception (e.g., SQLite error) would crash the process.
     */
    override fun findExistingAlgorithms(hostname: String, port: Int): List<String> {
        return try {
            kotlinx.coroutines.runBlocking {
                knownHostDao.findByHostPort(this@AppHostKeyVerifier.hostname, this@AppHostKeyVerifier.port)
                    .filter { it.trusted }
                    .map { it.keyType }
            }
        } catch (t: Throwable) {
            AppLogger.w(Category.HOSTKEY) {
                "hostkey.findExistingAlgorithms ✗ host=$hostname:$port cause='${t.message}'"
            }
            emptyList()
        }
    }
}

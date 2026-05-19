package com.tmuxes.ssh

import com.tmuxes.data.model.AuthMethod
import com.tmuxes.data.model.ServerEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure [ConnectionSupervisor.computeActions] function.
 *
 * These tests construct [ReconcileSnapshot]s manually and verify the resulting
 * [ReconcileAction] list. No mocking is needed because computeActions is pure.
 */
class ReconcileActionsTest {

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun server(
        id: Long,
        hostname: String = "host$id.example.com",
        username: String = "user",
        isEnabled: Boolean = true,
        parentId: Long? = null,
        authMethod: AuthMethod = AuthMethod.PASSWORD,
        name: String? = null,
        password: String? = null,
        privateKeyData: String? = null
    ) = ServerEntity(
        id = id,
        hostname = hostname,
        username = username,
        isEnabled = isEnabled,
        parentId = parentId,
        authMethod = authMethod,
        name = name,
        password = password,
        privateKeyData = privateKeyData
    )

    private fun intent(
        serverId: Long,
        retryCount: Int = 0,
        nextRetryAt: Long = 0L,
        lastError: String? = null,
        isPermanentlyFailed: Boolean = false,
        hasActiveConnectJob: Boolean = false,
        previousConnectionState: ConnectionState? = null,
        mustReconnect: Boolean = false
    ) = IntentSnapshot(
        serverId = serverId,
        retryCount = retryCount,
        nextRetryAt = nextRetryAt,
        lastError = lastError,
        isPermanentlyFailed = isPermanentlyFailed,
        hasActiveConnectJob = hasActiveConnectJob,
        previousConnectionState = previousConnectionState,
        mustReconnect = mustReconnect
    )

    private fun snapshot(
        servers: List<ServerEntity> = emptyList(),
        connectionStates: Map<Long, ConnectionState> = emptyMap(),
        intents: Map<Long, IntentSnapshot> = emptyMap(),
        isOnline: Boolean = true,
        isForeground: Boolean = true,
        knownHostsCache: Map<Long, List<com.tmuxes.data.model.KnownHostEntity>> = emptyMap(),
        now: Long = 1000L,
        reconnectIntervalSeconds: Int = 60
    ) = ReconcileSnapshot(
        servers = servers,
        connectionStates = connectionStates,
        intents = intents,
        isOnline = isOnline,
        isForeground = isForeground,
        knownHostsCache = knownHostsCache,
        now = now,
        reconnectIntervalSeconds = reconnectIntervalSeconds
    )

    private val supervisor = ConnectionSupervisor.getInstance()

    private inline fun <reified T : ReconcileAction> List<ReconcileAction>.findAction(
        predicate: (T) -> Boolean = { true }
    ): T? = filterIsInstance<T>().find(predicate)

    private inline fun <reified T : ReconcileAction> List<ReconcileAction>.hasAction(
        predicate: (T) -> Boolean = { true }
    ): Boolean = filterIsInstance<T>().any(predicate)

    // -----------------------------------------------------------------------
    // Basic: enabled server, online, foreground → Connect
    // -----------------------------------------------------------------------

    @Test
    fun `enabled server with no connection triggers Connect`() {
        val s = server(1)
        val actions = supervisor.computeActions(snapshot(servers = listOf(s)))

        assertTrue(
            "Should have a Connect action for server 1",
            actions.hasAction<ReconcileAction.Connect> { it.serverId == 1L }
        )
        assertTrue(
            "Should have UpdateState(CONNECTING) for server 1",
            actions.hasAction<ReconcileAction.UpdateState> {
                it.serverId == 1L && it.state.status == ServerStatus.CONNECTING
            }
        )
    }

    // -----------------------------------------------------------------------
    // Disabled server → PAUSED
    // -----------------------------------------------------------------------

    @Test
    fun `disabled server gets PAUSED status`() {
        val s = server(1, isEnabled = false)
        val actions = supervisor.computeActions(snapshot(servers = listOf(s)))

        assertTrue(
            "Should have UpdateState(PAUSED) for disabled server",
            actions.hasAction<ReconcileAction.UpdateState> {
                it.serverId == 1L && it.state.status == ServerStatus.PAUSED
            }
        )
        assertTrue(
            "Should NOT have Connect for disabled server",
            !actions.hasAction<ReconcileAction.Connect> { it.serverId == 1L }
        )
    }

    @Test
    fun `disabled server with active connection triggers Disconnect`() {
        val s = server(1, isEnabled = false)
        val actions = supervisor.computeActions(snapshot(
            servers = listOf(s),
            connectionStates = mapOf(1L to ConnectionState.AUTHENTICATED)
        ))

        assertTrue(
            "Should have Disconnect for disabled but connected server",
            actions.hasAction<ReconcileAction.Disconnect> { it.serverId == 1L }
        )
        assertTrue(
            "Should have PAUSED status",
            actions.hasAction<ReconcileAction.UpdateState> {
                it.serverId == 1L && it.state.status == ServerStatus.PAUSED
            }
        )
    }

    // -----------------------------------------------------------------------
    // Already connected → CONNECTED
    // -----------------------------------------------------------------------

    @Test
    fun `already authenticated server gets CONNECTED status`() {
        val s = server(1)
        val actions = supervisor.computeActions(snapshot(
            servers = listOf(s),
            connectionStates = mapOf(1L to ConnectionState.AUTHENTICATED)
        ))

        assertTrue(
            "Should have UpdateState(CONNECTED)",
            actions.hasAction<ReconcileAction.UpdateState> {
                it.serverId == 1L && it.state.status == ServerStatus.CONNECTED
            }
        )
        assertTrue(
            "Should NOT have Connect for already connected server",
            !actions.hasAction<ReconcileAction.Connect> { it.serverId == 1L }
        )
    }

    // -----------------------------------------------------------------------
    // Currently connecting → CONNECTING
    // -----------------------------------------------------------------------

    @Test
    fun `connecting server gets CONNECTING status and no new Connect`() {
        val s = server(1)
        val actions = supervisor.computeActions(snapshot(
            servers = listOf(s),
            connectionStates = mapOf(1L to ConnectionState.CONNECTING)
        ))

        assertTrue(
            "Should have CONNECTING status",
            actions.hasAction<ReconcileAction.UpdateState> {
                it.serverId == 1L && it.state.status == ServerStatus.CONNECTING
            }
        )
        assertTrue(
            "Should NOT have Connect",
            !actions.hasAction<ReconcileAction.Connect> { it.serverId == 1L }
        )
    }

    // -----------------------------------------------------------------------
    // Auth failed → AUTH_FAILED (no retry)
    // -----------------------------------------------------------------------

    @Test
    fun `permanently failed server gets AUTH_FAILED status`() {
        val s = server(1)
        val actions = supervisor.computeActions(snapshot(
            servers = listOf(s),
            intents = mapOf(1L to intent(1, isPermanentlyFailed = true, lastError = "Wrong password"))
        ))

        assertTrue(
            "Should have AUTH_FAILED",
            actions.hasAction<ReconcileAction.UpdateState> {
                it.serverId == 1L && it.state.status == ServerStatus.AUTH_FAILED
            }
        )
        assertTrue(
            "Should NOT have Connect",
            !actions.hasAction<ReconcileAction.Connect> { it.serverId == 1L }
        )
    }

    @Test
    fun `intent marked permanentlyFailed treated as auth failure`() {
        val s = server(1)
        val actions = supervisor.computeActions(snapshot(
            servers = listOf(s),
            connectionStates = mapOf(1L to ConnectionState.ERROR),
            intents = mapOf(1L to IntentSnapshot(
                serverId = 1L,
                isPermanentlyFailed = true,
                lastError = "Authentication failed"
            ))
        ))

        assertTrue(
            "Should have AUTH_FAILED",
            actions.hasAction<ReconcileAction.UpdateState> {
                it.serverId == 1L && it.state.status == ServerStatus.AUTH_FAILED
            }
        )
    }

    // -----------------------------------------------------------------------
    // No network → NO_NETWORK
    // -----------------------------------------------------------------------

    @Test
    fun `offline device results in NO_NETWORK`() {
        val s = server(1)
        val actions = supervisor.computeActions(snapshot(
            servers = listOf(s),
            isOnline = false
        ))

        assertTrue(
            "Should have NO_NETWORK status",
            actions.hasAction<ReconcileAction.UpdateState> {
                it.serverId == 1L && it.state.status == ServerStatus.NO_NETWORK
            }
        )
        assertTrue(
            "Should NOT have Connect",
            !actions.hasAction<ReconcileAction.Connect> { it.serverId == 1L }
        )
    }

    // -----------------------------------------------------------------------
    // Orphaned child → MarkOrphanedChild
    // -----------------------------------------------------------------------

    @Test
    fun `orphaned child with deleted parent triggers MarkOrphanedChild`() {
        // Child references parentId=99, but that server doesn't exist
        val child = server(2, parentId = 99)
        val actions = supervisor.computeActions(snapshot(
            servers = listOf(child)
        ))

        assertTrue(
            "Should have MarkOrphanedChild for server 2",
            actions.hasAction<ReconcileAction.MarkOrphanedChild> { it.serverId == 2L }
        )
    }

    // -----------------------------------------------------------------------
    // Parent auth failed → child PARENT_FAILED with ParentInfo
    // -----------------------------------------------------------------------

    @Test
    fun `parent auth failure propagates to child as PARENT_FAILED`() {
        val parent = server(1, name = "JumpHost")
        val child = server(2, parentId = 1)

        val actions = supervisor.computeActions(snapshot(
            servers = listOf(parent, child),
            intents = mapOf(
                1L to intent(1, isPermanentlyFailed = true, lastError = "Auth failed"),
                2L to intent(2)
            )
        ))

        val parentFailedAction = actions.findAction<ReconcileAction.UpdateState> {
            it.serverId == 2L && it.state.status == ServerStatus.PARENT_FAILED
        }

        assertTrue("Should have PARENT_FAILED for child", parentFailedAction != null)
        assertEquals("Parent ID in ParentInfo", 1L, parentFailedAction!!.state.parentInfo?.parentId)
        assertEquals("Parent name in ParentInfo", "JumpHost", parentFailedAction.state.parentInfo?.parentName)
        assertEquals("Parent status in ParentInfo", ServerStatus.AUTH_FAILED, parentFailedAction.state.parentInfo?.parentStatus)
    }

    // -----------------------------------------------------------------------
    // Parent not connected → child WAITING_PARENT
    // -----------------------------------------------------------------------

    @Test
    fun `child waits for parent connection`() {
        val parent = server(1, name = "JumpHost")
        val child = server(2, parentId = 1)

        val actions = supervisor.computeActions(snapshot(
            servers = listOf(parent, child),
            connectionStates = mapOf(1L to ConnectionState.CONNECTING)
        ))

        assertTrue(
            "Should have WAITING_PARENT for child",
            actions.hasAction<ReconcileAction.UpdateState> {
                it.serverId == 2L && it.state.status == ServerStatus.WAITING_PARENT
            }
        )
    }

    // -----------------------------------------------------------------------
    // Backoff not elapsed → NETWORK_ERROR with retry info
    // -----------------------------------------------------------------------

    @Test
    fun `backoff not elapsed shows NETWORK_ERROR with retry info`() {
        val s = server(1)
        val actions = supervisor.computeActions(snapshot(
            servers = listOf(s),
            intents = mapOf(1L to intent(
                1,
                retryCount = 3,
                nextRetryAt = 5000L,
                lastError = "Connection refused"
            )),
            now = 2000L // before nextRetryAt of 5000
        ))

        val action = actions.findAction<ReconcileAction.UpdateState> {
            it.serverId == 1L && it.state.status == ServerStatus.NETWORK_ERROR
        }
        assertTrue("Should have NETWORK_ERROR", action != null)
        assertEquals("retryCount", 3, action!!.state.retryCount)
        assertEquals("nextRetryAt", 5000L, action.state.nextRetryAt)
        assertEquals("errorMessage", "Connection refused", action.state.errorMessage)
    }

    @Test
    fun `backoff elapsed triggers new connection`() {
        val s = server(1)
        val actions = supervisor.computeActions(snapshot(
            servers = listOf(s),
            intents = mapOf(1L to intent(
                1,
                retryCount = 3,
                nextRetryAt = 1000L,
                lastError = "Connection refused"
            )),
            now = 2000L // after nextRetryAt
        ))

        assertTrue(
            "Should have Connect after backoff elapsed",
            actions.hasAction<ReconcileAction.Connect> { it.serverId == 1L }
        )
    }

    // -----------------------------------------------------------------------
    // Active connect job → CONNECTING (no new Connect)
    // -----------------------------------------------------------------------

    @Test
    fun `active connect job prevents new Connect`() {
        val s = server(1)
        val actions = supervisor.computeActions(snapshot(
            servers = listOf(s),
            intents = mapOf(1L to intent(1, hasActiveConnectJob = true))
        ))

        assertTrue(
            "Should have CONNECTING status",
            actions.hasAction<ReconcileAction.UpdateState> {
                it.serverId == 1L && it.state.status == ServerStatus.CONNECTING
            }
        )
        assertTrue(
            "Should NOT have Connect",
            !actions.hasAction<ReconcileAction.Connect> { it.serverId == 1L }
        )
    }

    // -----------------------------------------------------------------------
    // Background + unknown host key → WAITING_HOST_KEY
    // -----------------------------------------------------------------------

    @Test
    fun `background mode with unknown host key defers connection`() {
        val s = server(1)
        val actions = supervisor.computeActions(snapshot(
            servers = listOf(s),
            isForeground = false,
            knownHostsCache = mapOf(1L to emptyList()) // no known hosts
        ))

        assertTrue(
            "Should have WAITING_HOST_KEY",
            actions.hasAction<ReconcileAction.UpdateState> {
                it.serverId == 1L && it.state.status == ServerStatus.WAITING_HOST_KEY
            }
        )
        assertTrue(
            "Should NOT have Connect in background with unknown host",
            !actions.hasAction<ReconcileAction.Connect> { it.serverId == 1L }
        )
    }

    @Test
    fun `foreground mode with unknown host key proceeds to connect`() {
        val s = server(1)
        val actions = supervisor.computeActions(snapshot(
            servers = listOf(s),
            isForeground = true // foreground: OK to connect even if host unknown
        ))

        assertTrue(
            "Should have Connect in foreground",
            actions.hasAction<ReconcileAction.Connect> { it.serverId == 1L }
        )
    }

    // -----------------------------------------------------------------------
    // Parent-first ordering (depth sort)
    // -----------------------------------------------------------------------

    @Test
    fun `parent is processed before child in actions`() {
        val parent = server(1, name = "Parent")
        val child = server(2, parentId = 1)

        val actions = supervisor.computeActions(snapshot(
            servers = listOf(child, parent) // child listed first, but parent should sort first
        ))

        // Parent should get a Connect action (processed first)
        val parentConnectIndex = actions.indexOfFirst {
            it is ReconcileAction.Connect && it.serverId == 1L
        }
        // Child should get WAITING_PARENT (since parent not yet connected)
        val childWaitingIndex = actions.indexOfFirst {
            it is ReconcileAction.UpdateState && it.serverId == 2L &&
                it.state.status == ServerStatus.WAITING_PARENT
        }

        assertTrue("Parent should have Connect", parentConnectIndex >= 0)
        assertTrue("Child should have WAITING_PARENT", childWaitingIndex >= 0)
        assertTrue(
            "Parent Connect should come before child WAITING_PARENT",
            parentConnectIndex < childWaitingIndex
        )
    }

    // -----------------------------------------------------------------------
    // Parent recovery → ResetChildBackoffs
    // -----------------------------------------------------------------------

    @Test
    fun `parent becoming authenticated triggers ResetChildBackoffs`() {
        val parent = server(1)
        val child = server(2, parentId = 1)

        val actions = supervisor.computeActions(snapshot(
            servers = listOf(parent, child),
            connectionStates = mapOf(1L to ConnectionState.AUTHENTICATED),
            intents = mapOf(
                1L to intent(1, previousConnectionState = ConnectionState.ERROR)
            )
        ))

        assertTrue(
            "Should have ResetChildBackoffs for parent 1",
            actions.hasAction<ReconcileAction.ResetChildBackoffs> { it.parentId == 1L }
        )
    }

    // -----------------------------------------------------------------------
    // Parent disconnect → CascadeDisconnect
    // -----------------------------------------------------------------------

    @Test
    fun `parent losing connection triggers CascadeDisconnect`() {
        val parent = server(1)
        val child = server(2, parentId = 1)

        val actions = supervisor.computeActions(snapshot(
            servers = listOf(parent, child),
            connectionStates = mapOf(1L to ConnectionState.ERROR),
            intents = mapOf(
                1L to intent(1, previousConnectionState = ConnectionState.AUTHENTICATED)
            )
        ))

        assertTrue(
            "Should have CascadeDisconnect for parent 1",
            actions.hasAction<ReconcileAction.CascadeDisconnect> { it.parentId == 1L }
        )
    }

    // -----------------------------------------------------------------------
    // Deleted server cleanup
    // -----------------------------------------------------------------------

    @Test
    fun `deleted server with intent gets Disconnect and DISCONNECTED`() {
        // No servers in the list, but intent exists for server 5
        val actions = supervisor.computeActions(snapshot(
            servers = emptyList(),
            connectionStates = mapOf(5L to ConnectionState.AUTHENTICATED),
            intents = mapOf(5L to intent(5))
        ))

        assertTrue(
            "Should have Disconnect for deleted server",
            actions.hasAction<ReconcileAction.Disconnect> { it.serverId == 5L }
        )
        assertTrue(
            "Should have DISCONNECTED status for deleted server",
            actions.hasAction<ReconcileAction.UpdateState> {
                it.serverId == 5L && it.state.status == ServerStatus.DISCONNECTED
            }
        )
    }

    // -----------------------------------------------------------------------
    // computeBackoff
    // -----------------------------------------------------------------------

    @Test
    fun `backoff sequence is correct`() {
        // retryCount is incremented BEFORE calling computeBackoff, so first retry is count=1
        // Formula: min(2000 * (1 << retryCount), cap)
        // retryCount=1: 2000 * 2 = 4000
        // retryCount=2: 2000 * 4 = 8000
        // retryCount=3: 2000 * 8 = 16000
        // retryCount=4: 2000 * 16 = 32000
        // retryCount=5: 2000 * 32 = 64000 → capped at 60000
        assertEquals(2000L, ConnectionSupervisor.computeBackoff(0, 60))
        assertEquals(4000L, ConnectionSupervisor.computeBackoff(1, 60))
        assertEquals(8000L, ConnectionSupervisor.computeBackoff(2, 60))
        assertEquals(16000L, ConnectionSupervisor.computeBackoff(3, 60))
        assertEquals(32000L, ConnectionSupervisor.computeBackoff(4, 60))
        assertEquals(60000L, ConnectionSupervisor.computeBackoff(5, 60))
        assertEquals(60000L, ConnectionSupervisor.computeBackoff(6, 60)) // capped
        assertEquals(60000L, ConnectionSupervisor.computeBackoff(100, 60)) // still capped
    }

    @Test
    fun `backoff respects custom cap`() {
        assertEquals(2000L, ConnectionSupervisor.computeBackoff(0, 5))
        assertEquals(4000L, ConnectionSupervisor.computeBackoff(1, 5))
        assertEquals(5000L, ConnectionSupervisor.computeBackoff(2, 5)) // capped at 5s
        assertEquals(5000L, ConnectionSupervisor.computeBackoff(10, 5))
    }

    // -----------------------------------------------------------------------
    // isAuthError
    // -----------------------------------------------------------------------

    @Test
    fun `isAuthError detects auth error messages`() {
        assertTrue(ConnectionSupervisor.isAuthError(
            SshException("Authentication failed for user@host")))
        assertTrue(ConnectionSupervisor.isAuthError(
            SshException("Exhausted available authentication methods")))
    }

    @Test
    fun `isAuthError rejects non-auth errors`() {
        assertTrue(!ConnectionSupervisor.isAuthError(
            SshException("Connection refused")))
        assertTrue(!ConnectionSupervisor.isAuthError(
            SshException("Network unreachable")))
    }

    // -----------------------------------------------------------------------
    // Multiple servers: complex scenario
    // -----------------------------------------------------------------------

    @Test
    fun `complex scenario with mixed server states`() {
        val parent = server(1, name = "Parent")
        val connectedChild = server(2, parentId = 1, name = "ConnChild")
        val disabledServer = server(3, isEnabled = false, name = "Disabled")
        val authFailedServer = server(4, name = "AuthFail")
        val newServer = server(5, name = "New")

        val actions = supervisor.computeActions(snapshot(
            servers = listOf(parent, connectedChild, disabledServer, authFailedServer, newServer),
            connectionStates = mapOf(
                1L to ConnectionState.AUTHENTICATED,
                2L to ConnectionState.AUTHENTICATED
            ),
            intents = mapOf(
                1L to intent(1),
                2L to intent(2),
                3L to intent(3),
                4L to intent(4, isPermanentlyFailed = true, lastError = "Bad key")
            )
        ))

        // Parent: CONNECTED
        assertTrue(actions.hasAction<ReconcileAction.UpdateState> {
            it.serverId == 1L && it.state.status == ServerStatus.CONNECTED
        })
        // Connected child: CONNECTED
        assertTrue(actions.hasAction<ReconcileAction.UpdateState> {
            it.serverId == 2L && it.state.status == ServerStatus.CONNECTED
        })
        // Disabled: PAUSED
        assertTrue(actions.hasAction<ReconcileAction.UpdateState> {
            it.serverId == 3L && it.state.status == ServerStatus.PAUSED
        })
        // Auth failed: AUTH_FAILED
        assertTrue(actions.hasAction<ReconcileAction.UpdateState> {
            it.serverId == 4L && it.state.status == ServerStatus.AUTH_FAILED
        })
        // New server: Connect
        assertTrue(actions.hasAction<ReconcileAction.Connect> { it.serverId == 5L })
    }

    // -----------------------------------------------------------------------
    // Three-level chain — depth sort + WAITING_PARENT propagation
    // -----------------------------------------------------------------------

    @Test
    fun `three-level chain depth sort places root before mid before leaf`() {
        val root = server(1, name = "Root")
        val mid = server(2, parentId = 1, name = "Mid")
        val leaf = server(3, parentId = 2, name = "Leaf")

        // Pass them in deliberately wrong order to verify the depth sort runs.
        val actions = supervisor.computeActions(snapshot(
            servers = listOf(leaf, mid, root)
        ))

        // Find the index of each server's primary action
        val rootConnectIdx = actions.indexOfFirst {
            it is ReconcileAction.Connect && it.serverId == 1L
        }
        val midActionIdx = actions.indexOfFirst {
            (it is ReconcileAction.Connect && it.serverId == 2L) ||
                (it is ReconcileAction.UpdateState && it.serverId == 2L)
        }
        val leafActionIdx = actions.indexOfFirst {
            (it is ReconcileAction.Connect && it.serverId == 3L) ||
                (it is ReconcileAction.UpdateState && it.serverId == 3L)
        }

        assertTrue("root must appear before mid (depth sort)", rootConnectIdx in 0..<midActionIdx)
        assertTrue("mid must appear before leaf (depth sort)", midActionIdx in 0..<leafActionIdx)
    }

    @Test
    fun `three-level chain leaf shows WAITING_PARENT when root is connecting`() {
        val root = server(1, name = "Root")
        val mid = server(2, parentId = 1, name = "Mid")
        val leaf = server(3, parentId = 2, name = "Leaf")

        val actions = supervisor.computeActions(snapshot(
            servers = listOf(root, mid, leaf),
            connectionStates = mapOf(1L to ConnectionState.CONNECTING)
        ))

        // Mid waits for root
        assertTrue(actions.hasAction<ReconcileAction.UpdateState> {
            it.serverId == 2L && it.state.status == ServerStatus.WAITING_PARENT
        })
        // Leaf waits for mid (which is waiting for root)
        assertTrue(actions.hasAction<ReconcileAction.UpdateState> {
            it.serverId == 3L && it.state.status == ServerStatus.WAITING_PARENT
        })
    }

    @Test
    fun `three-level chain emits exactly one CascadeDisconnect for the dropped root`() {
        val root = server(1, name = "Root")
        val mid = server(2, parentId = 1, name = "Mid")
        val leaf = server(3, parentId = 2, name = "Leaf")

        val actions = supervisor.computeActions(snapshot(
            servers = listOf(root, mid, leaf),
            connectionStates = mapOf(1L to ConnectionState.ERROR),
            intents = mapOf(
                1L to intent(1, previousConnectionState = ConnectionState.AUTHENTICATED)
            )
        ))

        // Exactly ONE CascadeDisconnect — for root only.
        // The transitive walk to mid+leaf happens in executeActions, not here.
        val cascades = actions.filterIsInstance<ReconcileAction.CascadeDisconnect>()
        assertEquals(1, cascades.size)
        assertEquals(1L, cascades.first().parentId)
    }

    // -----------------------------------------------------------------------
    // RemoveState — deleted server with intent but no pool entry
    // -----------------------------------------------------------------------

    @Test
    fun `deleted server with intent only emits RemoveState without Disconnect`() {
        val actions = supervisor.computeActions(snapshot(
            servers = emptyList(),
            connectionStates = emptyMap(),  // not in pool
            intents = mapOf(5L to intent(5, retryCount = 3))
        ))

        // No Disconnect because there is no live connection
        assertTrue(
            "must NOT emit Disconnect for non-pool server",
            actions.filterIsInstance<ReconcileAction.Disconnect>().none { it.serverId == 5L }
        )
        // Must clean up the orphan intent
        assertTrue(
            "must emit RemoveState",
            actions.hasAction<ReconcileAction.RemoveState> { it.serverId == 5L }
        )
        // Must publish DISCONNECTED state at least once before removal
        assertTrue(
            "must emit UpdateState DISCONNECTED",
            actions.hasAction<ReconcileAction.UpdateState> {
                it.serverId == 5L && it.state.status == ServerStatus.DISCONNECTED
            }
        )
    }

    @Test
    fun `deleted server with both intent and pool entry emits Disconnect and RemoveState`() {
        val actions = supervisor.computeActions(snapshot(
            servers = emptyList(),
            connectionStates = mapOf(5L to ConnectionState.AUTHENTICATED),
            intents = mapOf(5L to intent(5))
        ))

        assertTrue(
            "must emit Disconnect to tear down the live connection",
            actions.hasAction<ReconcileAction.Disconnect> { it.serverId == 5L }
        )
        assertTrue(
            "must emit RemoveState to clean up tracking",
            actions.hasAction<ReconcileAction.RemoveState> { it.serverId == 5L }
        )
    }

    // -----------------------------------------------------------------------
    // ResetChildBackoffs — only emitted on transition into AUTHENTICATED
    // -----------------------------------------------------------------------

    @Test
    fun `stably authenticated parent does NOT emit ResetChildBackoffs`() {
        val parent = server(1)
        val child = server(2, parentId = 1)

        val actions = supervisor.computeActions(snapshot(
            servers = listOf(parent, child),
            connectionStates = mapOf(1L to ConnectionState.AUTHENTICATED),
            // previous state was ALSO AUTHENTICATED → stable, no transition
            intents = mapOf(
                1L to intent(1, previousConnectionState = ConnectionState.AUTHENTICATED)
            )
        ))

        assertTrue(
            "no ResetChildBackoffs when parent was already authenticated",
            actions.filterIsInstance<ReconcileAction.ResetChildBackoffs>().none { it.parentId == 1L }
        )
    }

    @Test
    fun `parent transitioning from ERROR to AUTHENTICATED emits ResetChildBackoffs`() {
        val parent = server(1)
        val child = server(2, parentId = 1)

        val actions = supervisor.computeActions(snapshot(
            servers = listOf(parent, child),
            connectionStates = mapOf(1L to ConnectionState.AUTHENTICATED),
            intents = mapOf(
                1L to intent(1, previousConnectionState = ConnectionState.ERROR)
            )
        ))

        assertTrue(
            "must emit ResetChildBackoffs on transition to authenticated",
            actions.hasAction<ReconcileAction.ResetChildBackoffs> { it.parentId == 1L }
        )
    }

    // -----------------------------------------------------------------------
    // Write-path invalidation (mustReconnect)
    //
    // The supervisor no longer recomputes fingerprints in the reconcile hot
    // path. Connection-relevant edits in the repository emit an invalidation
    // event the supervisor consumes, setting `mustReconnect=true` on the
    // matching intent. computeActions reacts to that flag.
    //
    // The before/after fingerprint contract itself lives on ServerEntity and
    // is unit-tested in ServerEntityFingerprintTest; here we only verify the
    // *reconcile-time* effect of `mustReconnect`.
    // -----------------------------------------------------------------------

    @Test
    fun `authenticated server without mustReconnect stays connected`() {
        val s = server(1, hostname = "10.0.2.2")
        val actions = supervisor.computeActions(snapshot(
            servers = listOf(s),
            connectionStates = mapOf(1L to ConnectionState.AUTHENTICATED),
            intents = mapOf(1L to intent(1, mustReconnect = false))
        ))
        assertTrue(
            "must NOT disconnect when mustReconnect is false",
            !actions.hasAction<ReconcileAction.Disconnect> { it.serverId == 1L }
        )
        assertTrue(
            "must NOT reconnect when mustReconnect is false",
            !actions.hasAction<ReconcileAction.Connect> { it.serverId == 1L }
        )
    }

    @Test
    fun `authenticated server with mustReconnect triggers Disconnect+Connect`() {
        val s = server(1, hostname = "10.0.2.2")
        val actions = supervisor.computeActions(snapshot(
            servers = listOf(s),
            connectionStates = mapOf(1L to ConnectionState.AUTHENTICATED),
            intents = mapOf(1L to intent(1, mustReconnect = true))
        ))
        assertTrue(
            "must Disconnect the stale connection",
            actions.hasAction<ReconcileAction.Disconnect> { it.serverId == 1L }
        )
        assertTrue(
            "must Connect with the new entity",
            actions.hasAction<ReconcileAction.Connect> { it.serverId == 1L }
        )
        // Disconnect → CONNECTING → Connect, so consumers don't see a
        // stale CONNECTED entry between the two side effects.
        assertTrue(
            "must emit a CONNECTING state update between disconnect and reconnect",
            actions.hasAction<ReconcileAction.UpdateState> {
                it.serverId == 1L && it.state.status == ServerStatus.CONNECTING
            }
        )
    }
}

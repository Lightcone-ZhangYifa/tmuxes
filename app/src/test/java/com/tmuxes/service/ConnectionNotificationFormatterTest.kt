package com.tmuxes.service

import com.tmuxes.data.model.ServerEntity
import com.tmuxes.ssh.ParentInfo
import com.tmuxes.ssh.ServerConnectionState
import com.tmuxes.ssh.ServerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionNotificationFormatterTest {

    @Test
    fun `all connected notification says all servers are active`() {
        val copy = ConnectionNotificationFormatter.format(
            servers = listOf(server(1, "api"), server(2, "db")),
            states = mapOf(
                1L to ServerConnectionState(ServerStatus.CONNECTED),
                2L to ServerConnectionState(ServerStatus.CONNECTED)
            ),
            nowElapsedMs = NOW
        )

        assertEquals("tmuxes: SSH connections active", copy.title)
        assertEquals("2/2 connected. Background sessions stay reachable.", copy.text)
        assertEquals("2/2 connected", copy.subText)
        assertTrue(copy.detailLines.any { it == "api: connected" })
        assertTrue(copy.detailLines.any { it == "db: connected" })
    }

    @Test
    fun `blocking states are called out as action needed with server names`() {
        val copy = ConnectionNotificationFormatter.format(
            servers = listOf(server(1, "api"), server(2, "web"), server(3, "db")),
            states = mapOf(
                1L to ServerConnectionState(ServerStatus.CONNECTED),
                2L to ServerConnectionState(ServerStatus.AUTH_FAILED, errorMessage = "Permission denied"),
                3L to ServerConnectionState(ServerStatus.WAITING_HOST_KEY)
            ),
            nowElapsedMs = NOW
        )

        assertEquals("tmuxes: action needed", copy.title)
        assertEquals("2 servers need attention: web, db", copy.text)
        assertTrue(copy.detailLines.any { it == "web: action needed - authentication failed: Permission denied" })
        assertTrue(copy.detailLines.any { it == "db: action needed - verify host key in tmuxes" })
    }

    @Test
    fun `network retry notification includes retry count and approximate eta`() {
        val copy = ConnectionNotificationFormatter.format(
            servers = listOf(server(1, "api"), server(2, "db")),
            states = mapOf(
                1L to ServerConnectionState(ServerStatus.CONNECTED),
                2L to ServerConnectionState(
                    status = ServerStatus.NETWORK_ERROR,
                    errorMessage = "Connection timed out",
                    retryCount = 2,
                    nextRetryAt = NOW + 61_000
                )
            ),
            nowElapsedMs = NOW
        )

        assertEquals("tmuxes: reconnecting SSH", copy.title)
        assertEquals("1/2 connected; 1 server is retrying after network errors.", copy.text)
        assertTrue(copy.detailLines.any { it == "db: retry 2 in about 61s - Connection timed out" })
    }

    @Test
    fun `offline notification distinguishes no network from generic connecting`() {
        val copy = ConnectionNotificationFormatter.format(
            servers = listOf(server(1, "api"), server(2, "db")),
            states = mapOf(
                1L to ServerConnectionState(ServerStatus.NO_NETWORK),
                2L to ServerConnectionState(ServerStatus.NO_NETWORK)
            ),
            nowElapsedMs = NOW
        )

        assertEquals("tmuxes: waiting for network", copy.title)
        assertEquals("No network; 2 servers will reconnect when Android is online.", copy.text)
        assertTrue(copy.detailLines.any { it == "api: paused - no network; will reconnect automatically" })
    }

    @Test
    fun `parent wait notification names the parent and parent status`() {
        val copy = ConnectionNotificationFormatter.format(
            servers = listOf(server(1, "bastion"), server(2, "db")),
            states = mapOf(
                1L to ServerConnectionState(ServerStatus.CONNECTING),
                2L to ServerConnectionState(
                    status = ServerStatus.WAITING_PARENT,
                    parentInfo = ParentInfo(
                        parentId = 1L,
                        parentName = "bastion",
                        parentStatus = ServerStatus.CONNECTING
                    )
                )
            ),
            nowElapsedMs = NOW
        )

        assertEquals("tmuxes: connecting SSH", copy.title)
        assertEquals("0/2 connected; 2 servers are still connecting.", copy.text)
        assertTrue(copy.detailLines.any { it == "db: waiting for parent bastion (connecting)" })
    }

    private fun server(id: Long, name: String): ServerEntity = ServerEntity(
        id = id,
        name = name,
        hostname = "$name.example.com",
        username = "dev"
    )

    companion object {
        private const val NOW = 10_000L
    }
}

package com.tmuxes.ssh

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Tests for the [withConnection] helper that ViewModels use to safely
 * acquire and operate on an SSH connection from the pool.
 */
class WithConnectionTest {

    /** Minimal stub: only [isConnected] needs to be controllable; everything else throws. */
    private class StubConnection(
        override val serverId: Long,
        private val connected: Boolean
    ) : SshConnection {
        private val _state = MutableStateFlow(ConnectionState.AUTHENTICATED)
        override val state: StateFlow<ConnectionState> = _state
        override suspend fun connect() = Unit
        override suspend fun disconnect() = Unit
        override fun isConnected(): Boolean = connected
        override suspend fun openSession(rows: Int, cols: Int, command: String): SshSession =
            throw UnsupportedOperationException()
        override suspend fun executeCommand(command: String, timeoutMs: Long): String =
            throw UnsupportedOperationException()
        override suspend fun setupLocalPortForward(
            localPort: Int, remoteHost: String, remotePort: Int
        ): PortForwardHandle = throw UnsupportedOperationException()
        override suspend fun openChannel(command: String): SshChannel =
            throw UnsupportedOperationException()
    }

    @Test
    fun `null pool entry throws SshException with not-connected message`() = runBlocking {
        val pool = SshConnectionPool()
        try {
            withConnection(pool, 1L) { "should not reach" }
            fail("Expected SshException")
        } catch (e: SshException) {
            assertTrue(
                "message should mention not connected",
                e.message?.contains("not connected", ignoreCase = true) == true
            )
        }
    }

    @Test
    fun `pool entry but isConnected returns false throws SshException`() = runBlocking {
        val pool = SshConnectionPool()
        pool.put(1L, StubConnection(1L, connected = false))
        try {
            withConnection(pool, 1L) { "should not reach" }
            fail("Expected SshException")
        } catch (e: SshException) {
            assertTrue(
                "message should mention connection lost",
                e.message?.contains("connection lost", ignoreCase = true) == true
            )
        }
    }

    @Test
    fun `connected entry executes block and returns its result`() = runBlocking {
        val pool = SshConnectionPool()
        pool.put(1L, StubConnection(1L, connected = true))
        val result = withConnection(pool, 1L) { conn ->
            assertEquals(1L, conn.serverId)
            "success"
        }
        assertEquals("success", result)
    }

    @Test
    fun `block exception propagates unchanged`() = runBlocking {
        val pool = SshConnectionPool()
        pool.put(1L, StubConnection(1L, connected = true))
        try {
            withConnection(pool, 1L) { throw IllegalStateException("block error") }
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertEquals("block error", e.message)
        }
    }
}

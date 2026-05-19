package com.tmuxes.ssh

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Tests for [SshConnectionPool], the pure-storage connection map.
 */
class SshConnectionPoolTest {

    private fun stub(id: Long): SshConnection = object : SshConnection {
        override val serverId = id
        private val _state = MutableStateFlow(ConnectionState.AUTHENTICATED)
        override val state: StateFlow<ConnectionState> = _state
        override suspend fun connect() = Unit
        override suspend fun disconnect() = Unit
        override fun isConnected() = true
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
    fun `put and get round-trip`() {
        val pool = SshConnectionPool()
        val conn = stub(1L)
        pool.put(1L, conn)
        assertSame(conn, pool.get(1L))
    }

    @Test
    fun `get unknown id returns null`() {
        assertNull(SshConnectionPool().get(99L))
    }

    @Test
    fun `put overwrites existing entry`() {
        val pool = SshConnectionPool()
        val c1 = stub(1L)
        val c2 = stub(1L)
        pool.put(1L, c1)
        pool.put(1L, c2)
        assertSame(c2, pool.get(1L))
        assertNotSame(c1, pool.get(1L))
    }

    @Test
    fun `remove returns and deletes entry`() {
        val pool = SshConnectionPool()
        val conn = stub(1L)
        pool.put(1L, conn)
        assertSame(conn, pool.remove(1L))
        assertNull(pool.get(1L))
    }

    @Test
    fun `remove unknown id returns null without throwing`() {
        assertNull(SshConnectionPool().remove(99L))
    }

    @Test
    fun `getAll returns defensive copy not affected by later mutations`() {
        val pool = SshConnectionPool()
        pool.put(1L, stub(1L))
        val snapshot = pool.getAll()
        assertEquals(1, snapshot.size)
        // Mutate pool after taking snapshot
        pool.put(2L, stub(2L))
        // Snapshot must NOT have grown
        assertEquals(1, snapshot.size)
        // But the live pool has both
        assertEquals(2, pool.getAll().size)
    }

    @Test
    fun `clear empties pool`() {
        val pool = SshConnectionPool()
        pool.put(1L, stub(1L))
        pool.put(2L, stub(2L))
        pool.clear()
        assertTrue(pool.getAll().isEmpty())
        assertNull(pool.get(1L))
        assertNull(pool.get(2L))
    }

    @Test
    fun `concurrent put and get from many threads do not throw`() {
        val pool = SshConnectionPool()
        val executor = Executors.newFixedThreadPool(8)
        try {
            repeat(1000) { i ->
                executor.submit { pool.put(i.toLong(), stub(i.toLong())) }
                executor.submit { pool.get((i % 100).toLong()) }
                executor.submit { pool.getAll() }
            }
        } finally {
            executor.shutdown()
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
        }
        // No assertion on count — purely a no-throw safety test
    }
}

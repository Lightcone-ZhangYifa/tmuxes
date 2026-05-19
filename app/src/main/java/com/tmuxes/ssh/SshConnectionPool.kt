package com.tmuxes.ssh

import com.tmuxes.util.AppLogger
import com.tmuxes.util.AppLogger.Category
import java.util.concurrent.ConcurrentHashMap

class SshConnectionPool {
    private val connections = ConcurrentHashMap<Long, SshConnection>()

    fun put(serverId: Long, connection: SshConnection) {
        val previous = connections.put(serverId, connection)
        AppLogger.d(Category.SSH) {
            "pool.put id=$serverId replaced=${previous != null} size=${connections.size}"
        }
    }
    fun get(serverId: Long): SshConnection? = connections[serverId]
    fun remove(serverId: Long): SshConnection? {
        val removed = connections.remove(serverId)
        if (removed != null) {
            AppLogger.d(Category.SSH) { "pool.remove id=$serverId size=${connections.size}" }
        }
        return removed
    }
    fun getAll(): Map<Long, SshConnection> = connections.toMap()
    fun clear() {
        val n = connections.size
        connections.clear()
        if (n > 0) AppLogger.i(Category.SSH) { "pool.clear removed=$n" }
    }
}

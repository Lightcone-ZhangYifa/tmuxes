package com.tmuxes.data.repository

import android.content.Context
import com.tmuxes.data.model.AuthMethod
import com.tmuxes.data.model.ServerEntity
import com.tmuxes.ssh.AuthConfig
import com.tmuxes.ssh.SshException
import com.tmuxes.util.AppLogger
import com.tmuxes.util.ColorHex
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.StringWriter

class ServerYamlRepository(private val context: Context) {

    companion object {
        private const val TAG = "tmuxes.DB"
        private const val FILE_NAME = "servers.yaml"
        private const val TEMP_SUFFIX = ".tmp"
    }

    private val configDir = File(context.filesDir, "config")
    private val configFile = File(configDir, FILE_NAME)
    private val tempFile = File(configDir, FILE_NAME + TEMP_SUFFIX)

    private val mutex = Mutex()
    private val _servers = mutableListOf<ServerEntity>()

    private val _configFlow = MutableStateFlow<List<ServerEntity>>(emptyList())
    val configFlow: StateFlow<List<ServerEntity>> = _configFlow.asStateFlow()

    /**
     * Emits the id of any server whose connection-relevant fields just changed
     * (see [ServerEntity.connectionFingerprint]). The supervisor collects this
     * flow to disconnect-and-reconnect the affected server immediately, instead
     * of recomputing fingerprints on every reconciliation cycle.
     *
     * Buffer is small but non-zero so a fast batch (e.g. importing a YAML file
     * with N servers) still delivers all ids without blocking the writer.
     */
    private val _connectionInvalidations = MutableSharedFlow<Long>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val connectionInvalidations: SharedFlow<Long> = _connectionInvalidations.asSharedFlow()

    private val yaml: Yaml
        get() {
            val options = DumperOptions().apply {
                defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
                isPrettyFlow = true
                indent = 2
                indicatorIndent = 0
                width = 120
            }
            return Yaml(options)
        }

    init {
        configDir.mkdirs()
        loadFromDisk()
        // Clean up stale temp file
        if (tempFile.exists()) tempFile.delete()
    }

    // ── Sorting ─────────────────────────────────────────────────────────

    private fun sorted(list: List<ServerEntity>): List<ServerEntity> =
        list.sortedWith(compareBy<ServerEntity> { it.sortOrder }.thenBy { it.hostname })

    // ── Reactive data sources ───────────────────────────────────────────

    val allServers: Flow<List<ServerEntity>> = _configFlow.map { sorted(it) }.distinctUntilChanged()

    val rootServers: Flow<List<ServerEntity>> = _configFlow.map { servers ->
        sorted(servers.filter { it.parentId == null })
    }.distinctUntilChanged()

    fun getChildrenOf(parentId: Long): Flow<List<ServerEntity>> = _configFlow.map { servers ->
        sorted(servers.filter { it.parentId == parentId })
    }.distinctUntilChanged()

    // ── Suspend queries ─────────────────────────────────────────────────

    suspend fun getById(id: Long): ServerEntity? = mutex.withLock {
        _servers.find { it.id == id }
    }

    suspend fun getAllDecryptedOnce(): List<ServerEntity> = mutex.withLock {
        sorted(_servers.toList())
    }

    suspend fun getChildrenOfOnce(parentId: Long): List<ServerEntity> = mutex.withLock {
        sorted(_servers.filter { it.parentId == parentId })
    }

    // ── CRUD ────────────────────────────────────────────────────────────

    suspend fun insert(server: ServerEntity): Long = mutex.withLock {
        val newId = if (_servers.isEmpty()) 1L else _servers.maxOf { it.id } + 1
        val newServer = server.copy(id = newId)
        _servers.add(newServer)
        emitAndSave()
        AppLogger.i(AppLogger.Category.DB) {
            "db.servers.insert id=$newId hostname=${server.hostname}:${server.port} username=${server.username}"
        }
        newId
    }

    /**
     * Replace the entity for [server.id]. If the connection-relevant fingerprint
     * changed, emit an invalidation event so the supervisor reconnects with the
     * new config — write-path detection replaces the supervisor's old per-cycle
     * fingerprint compare.
     */
    suspend fun update(server: ServerEntity) {
        val invalidated = mutex.withLock {
            val idx = _servers.indexOfFirst { it.id == server.id }
            if (idx < 0) {
                AppLogger.w(AppLogger.Category.DB) { "db.servers.update ✗ id=${server.id} not found" }
                return@withLock false
            }
            val previous = _servers[idx]
            _servers[idx] = server
            emitAndSave()
            previous.connectionFingerprint() != server.connectionFingerprint()
        }
        AppLogger.i(AppLogger.Category.DB) {
            "db.servers.update id=${server.id} fingerprintChanged=$invalidated"
        }
        if (invalidated) _connectionInvalidations.emit(server.id)
    }

    suspend fun delete(server: ServerEntity): Unit = mutex.withLock {
        // Orphan children — they keep their own credentials and become
        // top-level servers (no implicit re-auth resolution since
        // every server already carries its own credentials).
        val children = _servers.filter { it.parentId == server.id }
        for (child in children) {
            val idx = _servers.indexOfFirst { it.id == child.id }
            if (idx < 0) continue
            _servers[idx] = child.copy(parentId = null)
        }
        _servers.removeAll { it.id == server.id }
        emitAndSave()
        AppLogger.i(AppLogger.Category.DB) {
            "db.servers.delete id=${server.id} ('${server.displayName}') orphanedChildren=${children.size}"
        }
    }

    suspend fun deleteAll(): Unit = mutex.withLock {
        val previous = _servers.size
        _servers.clear()
        emitAndSave()
        AppLogger.w(AppLogger.Category.DB) { "db.servers.deleteAll wiped=$previous" }
    }

    // ── Partial updates ─────────────────────────────────────────────────

    suspend fun updateLastConnected(id: Long, timestamp: Long): Unit = mutex.withLock {
        val idx = _servers.indexOfFirst { it.id == id }
        if (idx >= 0) {
            _servers[idx] = _servers[idx].copy(lastConnectedAt = timestamp)
            emitAndSave()
            AppLogger.d(AppLogger.Category.DB) { "db.servers.lastConnected id=$id ts=$timestamp" }
        }
    }

    suspend fun updateParent(serverId: Long, newParentId: Long?) {
        val invalidated = mutex.withLock {
            val idx = _servers.indexOfFirst { it.id == serverId }
            if (idx < 0) return@withLock false
            val current = _servers[idx]

            // Atomic circular-chain check: walking up from newParentId
            // through the current in-memory tree, confirm we never hit
            // serverId. Without this, the ViewModel's pre-mutation check
            // is racy — a concurrent updateParent on another branch could
            // flip the tree state between the check and this mutation.
            // Done under the repository mutex so no other branch can
            // interleave.
            if (newParentId != null) {
                var cursor: Long? = newParentId
                val visited = mutableSetOf<Long>()
                while (cursor != null) {
                    if (cursor == serverId) {
                        AppLogger.w(AppLogger.Category.DB) {
                            "db.servers.updateParent ✗ id=$serverId newParent=$newParentId rejected (would nest under descendant)"
                        }
                        throw IllegalStateException(
                            "Cannot nest server '${current.displayName}' under its own descendant"
                        )
                    }
                    if (!visited.add(cursor)) break // cycle protection
                    cursor = _servers.firstOrNull { it.id == cursor }?.parentId
                }
            }

            val updated = current.copy(parentId = newParentId)
            _servers[idx] = updated
            emitAndSave()
            current.connectionFingerprint() != updated.connectionFingerprint()
        }
        AppLogger.i(AppLogger.Category.DB) {
            "db.servers.updateParent id=$serverId newParent=$newParentId fingerprintChanged=$invalidated"
        }
        if (invalidated) _connectionInvalidations.emit(serverId)
    }

    suspend fun updateLocalForwards(serverId: Long, localForwards: String?) {
        val invalidated = mutex.withLock {
            val idx = _servers.indexOfFirst { it.id == serverId }
            if (idx < 0) return@withLock false
            val previous = _servers[idx]
            val updated = previous.copy(localForwards = localForwards)
            _servers[idx] = updated
            emitAndSave()
            previous.connectionFingerprint() != updated.connectionFingerprint()
        }
        AppLogger.i(AppLogger.Category.DB) {
            "db.servers.localForwards id=$serverId fingerprintChanged=$invalidated"
        }
        if (invalidated) _connectionInvalidations.emit(serverId)
    }

    suspend fun updateSortOrder(serverId: Long, sortOrder: Int): Unit = mutex.withLock {
        val idx = _servers.indexOfFirst { it.id == serverId }
        if (idx >= 0) {
            _servers[idx] = _servers[idx].copy(sortOrder = sortOrder)
            emitAndSave()
        }
    }

    // ── Auth resolution ─────────────────────────────────────────────────

    /**
     * Resolve the [AuthConfig] for a server. Each server provides its own
     * credentials — there is no fallback chain or parent-credential
     * inheritance. A server with `parentId` uses the parent only as a TCP
     * tunnel; the child still authenticates on its own.
     */
    fun resolveAuthConfig(server: ServerEntity): AuthConfig {
        return when (server.authMethod) {
            AuthMethod.PASSWORD -> {
                val pw = server.password
                    ?: throw SshException("Password not configured for '${server.displayName}'.")
                AuthConfig.Password(pw)
            }
            AuthMethod.KEY -> {
                val key = server.privateKeyData
                    ?: throw SshException("Key data not configured for '${server.displayName}'.")
                AuthConfig.Key(privateKeyData = key, passphrase = null)
            }
            AuthMethod.KEY_WITH_PASSPHRASE -> {
                val key = server.privateKeyData
                    ?: throw SshException("Key data not configured for '${server.displayName}'.")
                AuthConfig.Key(privateKeyData = key, passphrase = server.passphrase)
            }
        }
    }

    // ── YAML text access ────────────────────────────────────────────────

    fun getYamlText(): String {
        return try {
            val servers = _configFlow.value
            if (servers.isEmpty()) return "servers: []\n"
            val root = LinkedHashMap<String, Any?>()
            root["servers"] = servers.map { server -> serverToMap(server) }
            val writer = StringWriter()
            yaml.dump(root, writer)
            "# tmuxes Server Configuration\n# Portable — copy this file to migrate servers between devices.\n\n${writer}"
        } catch (e: Exception) {
            AppLogger.e(AppLogger.Category.DB, e) { "Failed to serialise servers YAML" }
            "servers: []\n"
        }
    }

    suspend fun setFromYamlText(text: String) {
        val invalidatedIds = mutex.withLock {
            val parsed = yaml.load<Any>(text)
            val root = if (parsed is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                parsed as Map<String, Any?>
            } else throw IllegalArgumentException("Invalid YAML: root is not a map")

            val rawServers = root["servers"] as? List<*>
                ?: throw IllegalArgumentException("Invalid YAML: 'servers' key missing or not a list")

            val newServers = mutableListOf<ServerEntity>()
            val seenIds = mutableSetOf<Long>()

            for (item in rawServers) {
                if (item !is Map<*, *>) continue
                @Suppress("UNCHECKED_CAST")
                val map = item as Map<String, Any?>
                val server = mapToServer(map)

                if (server.hostname.isBlank()) throw IllegalArgumentException("Server missing hostname (id=${server.id})")
                if (server.username.isBlank()) throw IllegalArgumentException("Server missing username (id=${server.id})")
                if (!seenIds.add(server.id)) throw IllegalArgumentException("Duplicate server ID: ${server.id}")

                newServers.add(server)
            }

            // Per-server diff: invalidate any server whose connection
            // fingerprint differs from the one we had before. New servers
            // (not previously present) are skipped — the supervisor's normal
            // server-list observer will pick them up and connect.
            val previousById = _servers.associateBy { it.id }
            val ids = mutableListOf<Long>()
            for (next in newServers) {
                val prev = previousById[next.id] ?: continue
                if (prev.connectionFingerprint() != next.connectionFingerprint()) {
                    ids.add(next.id)
                }
            }

            _servers.clear()
            _servers.addAll(newServers)
            emitAndSave()
            ids
        }
        for (id in invalidatedIds) _connectionInvalidations.emit(id)
    }

    // ── Internal helpers ────────────────────────────────────────────────

    /** Emit configFlow and save to disk. Must be called under mutex. */
    private fun emitAndSave() {
        _configFlow.value = _servers.toList()
        saveToDisk()
    }

    // ── Serialization ───────────────────────────────────────────────────

    private fun serverToMap(server: ServerEntity): LinkedHashMap<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        map["id"] = server.id
        map["hostname"] = server.hostname
        map["username"] = server.username
        map["auth_method"] = server.authMethod.name

        if (server.name != null) map["name"] = server.name
        if (server.port != 22) map["port"] = server.port
        if (server.password != null) map["password"] = server.password
        if (server.privateKeyData != null) map["private_key_data"] = server.privateKeyData
        if (server.passphrase != null) map["passphrase"] = server.passphrase
        if (server.color != 0) map["color"] = ColorHex.toYamlString(server.color)
        if (server.parentId != null) map["parent_id"] = server.parentId
        if (server.keepAliveInterval != null) map["keep_alive_interval"] = server.keepAliveInterval
        if (server.compression != null) map["compression"] = server.compression
        if (server.lastConnectedAt != null) map["last_connected_at"] = server.lastConnectedAt
        if (!server.isEnabled) map["is_enabled"] = false
        if (server.sortOrder != 0) map["sort_order"] = server.sortOrder
        if (server.termType != null) map["term_type"] = server.termType
        if (server.connectionTimeout != null) map["connection_timeout"] = server.connectionTimeout
        if (server.transportTimeout != null) map["transport_timeout"] = server.transportTimeout
        if (server.readTimeout != null) map["read_timeout"] = server.readTimeout
        if (server.keepaliveMaxCount != null) map["keepalive_max_count"] = server.keepaliveMaxCount
        if (server.strictHostKey != null) map["strict_host_key"] = server.strictHostKey
        if (server.envVars != null) map["env_vars"] = server.envVars
        if (server.preferredCiphers != null) map["preferred_ciphers"] = server.preferredCiphers
        if (server.preferredKex != null) map["preferred_kex"] = server.preferredKex
        if (server.preferredMacs != null) map["preferred_macs"] = server.preferredMacs
        if (server.preferredHostKeyAlgs != null) map["preferred_host_key_algs"] = server.preferredHostKeyAlgs
        if (server.remoteForwards != null) map["remote_forwards"] = server.remoteForwards
        if (server.localForwards != null) map["local_forwards"] = server.localForwards

        return map
    }

    private fun mapToServer(map: Map<String, Any?>): ServerEntity {
        return ServerEntity(
            id = (map["id"] as? Number)?.toLong() ?: 0L,
            name = map["name"] as? String,
            hostname = (map["hostname"] as? String) ?: "",
            username = (map["username"] as? String) ?: "",
            port = (map["port"] as? Number)?.toInt() ?: 22,
            authMethod = parseAuthMethod(map["auth_method"]),
            password = map["password"] as? String,
            privateKeyData = map["private_key_data"] as? String,
            passphrase = map["passphrase"] as? String,
            color = ColorHex.parse(map["color"]) ?: 0,
            parentId = (map["parent_id"] as? Number)?.toLong(),
            keepAliveInterval = (map["keep_alive_interval"] as? Number)?.toInt(),
            compression = map["compression"] as? Boolean,
            lastConnectedAt = (map["last_connected_at"] as? Number)?.toLong(),
            isEnabled = (map["is_enabled"] as? Boolean) ?: true,
            sortOrder = (map["sort_order"] as? Number)?.toInt() ?: 0,
            termType = map["term_type"] as? String,
            connectionTimeout = (map["connection_timeout"] as? Number)?.toInt(),
            transportTimeout = (map["transport_timeout"] as? Number)?.toInt(),
            readTimeout = (map["read_timeout"] as? Number)?.toInt(),
            keepaliveMaxCount = (map["keepalive_max_count"] as? Number)?.toInt(),
            strictHostKey = map["strict_host_key"] as? String,
            envVars = map["env_vars"] as? String,
            preferredCiphers = map["preferred_ciphers"] as? String,
            preferredKex = map["preferred_kex"] as? String,
            preferredMacs = map["preferred_macs"] as? String,
            preferredHostKeyAlgs = map["preferred_host_key_algs"] as? String,
            remoteForwards = map["remote_forwards"] as? String,
            localForwards = map["local_forwards"] as? String
        )
    }

    private fun parseAuthMethod(value: Any?): AuthMethod {
        val str = (value as? String) ?: return AuthMethod.PASSWORD
        return try { AuthMethod.valueOf(str.uppercase()) } catch (_: Exception) { AuthMethod.PASSWORD }
    }

    // ── Disk I/O ────────────────────────────────────────────────────────

    /**
     * Parse a YAML file into the in-memory `_servers` state. Throws on any
     * malformed input — the caller decides how to recover. Mutates state in
     * place; call only after clearing existing state.
     */
    private fun loadFromFile(file: File) {
        val text = file.readText()
        if (text.isBlank()) return
        val parsed = yaml.load<Any>(text)
        if (parsed !is Map<*, *>) return
        @Suppress("UNCHECKED_CAST")
        val root = parsed as Map<String, Any?>
        val rawServers = root["servers"] as? List<*> ?: return

        for (item in rawServers) {
            if (item !is Map<*, *>) continue
            @Suppress("UNCHECKED_CAST")
            val map = item as Map<String, Any?>
            val server = mapToServer(map)
            _servers.add(server)
        }
        _configFlow.value = _servers.toList()
    }

    private fun loadFromDisk() {
        if (!configFile.exists()) return
        loadFromFile(configFile)
    }

    private fun saveToDisk() {
        try {
            val servers = _servers.toList()
            val root = LinkedHashMap<String, Any?>()
            root["servers"] = servers.map { server -> serverToMap(server) }
            val writer = StringWriter()
            yaml.dump(root, writer)
            val yamlText = "# tmuxes Server Configuration\n# Portable — copy this file to migrate servers between devices.\n\n${writer}"
            configDir.mkdirs()
            tempFile.writeText(yamlText)
            tempFile.renameTo(configFile)
        } catch (e: Exception) {
            AppLogger.e(AppLogger.Category.DB, e) { "Failed to save servers.yaml" }
        }
    }
}

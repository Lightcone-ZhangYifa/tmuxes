package com.tmuxes.ssh

import com.tmuxes.data.model.ServerEntity

/**
 * Builds a complete [SshConfig] by merging per-server overrides with global defaults.
 */
object SshConfigBuilder {

    /**
     * Build a complete [SshConfig] by merging per-server overrides with global defaults.
     *
     * For every nullable field on [ServerEntity], if the server value is non-null it
     * takes precedence; otherwise the corresponding [SshGlobalDefaults] value is used.
     *
     * @param server The server entity (nullable fields = "use global default").
     * @param authConfig Pre-resolved authentication credentials.
     * @param globalPrefs Global SSH default settings.
     * @return A fully-resolved [SshConfig] ready for connection.
     */
    fun build(
        server: ServerEntity,
        authConfig: AuthConfig,
        globalPrefs: SshGlobalDefaults
    ): SshConfig {
        val mergedEnvVars = buildString {
            append(globalPrefs.envVars)
            val serverVars = server.envVars ?: ""
            if (serverVars.isNotBlank()) {
                if (isNotEmpty()) append('\n')
                append(serverVars)
            }
        }.trim()

        return SshConfig(
            hostname = server.hostname,
            port = server.port,
            username = server.username,
            auth = authConfig,
            termType = server.termType ?: globalPrefs.defaultTerm,
            connectTimeout = ((server.connectionTimeout ?: globalPrefs.connectionTimeout) * 1000),
            transportTimeout = ((server.transportTimeout ?: globalPrefs.transportTimeout) * 1000),
            readTimeout = ((server.readTimeout ?: globalPrefs.readTimeout) * 1000),
            keepAliveInterval = server.keepAliveInterval ?: globalPrefs.keepaliveInterval,
            keepAliveMaxCount = server.keepaliveMaxCount ?: globalPrefs.keepaliveMaxCount,
            compression = server.compression ?: globalPrefs.compression,
            strictHostKey = server.strictHostKey ?: globalPrefs.strictHostKey,
            envVars = SshConfig.parseEnvVars(mergedEnvVars),
            preferredCiphers = SshConfig.parseCommaSeparated(server.preferredCiphers ?: globalPrefs.preferredCiphers),
            preferredKex = SshConfig.parseCommaSeparated(server.preferredKex ?: globalPrefs.preferredKex),
            preferredMacs = SshConfig.parseCommaSeparated(server.preferredMacs ?: globalPrefs.preferredMacs),
            preferredHostKeyAlgs = SshConfig.parseCommaSeparated(server.preferredHostKeyAlgs ?: globalPrefs.preferredHostKeyAlgs),
            remoteForwards = parseRemoteForwards(server.remoteForwards)
        )
    }

    /**
     * Parse a remote-forwards string into a list of [RemoteForward].
     *
     * Expected format: one forward per line, each line as `remotePort:localHost:localPort`
     * or `remotePort:localPort` (defaults localHost to 127.0.0.1). Invalid lines are
     * silently skipped.
     */
    fun parseRemoteForwards(text: String?): List<RemoteForward> {
        if (text.isNullOrBlank()) return emptyList()
        return text.lines().mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@mapNotNull null
            val parts = trimmed.split(':')
            when (parts.size) {
                3 -> {
                    val remote = parts[0].trim().toIntOrNull() ?: return@mapNotNull null
                    val host = parts[1].trim()
                    val local = parts[2].trim().toIntOrNull() ?: return@mapNotNull null
                    RemoteForward(remotePort = remote, localHost = host, localPort = local)
                }
                2 -> {
                    val remote = parts[0].trim().toIntOrNull() ?: return@mapNotNull null
                    val local = parts[1].trim().toIntOrNull() ?: return@mapNotNull null
                    RemoteForward(remotePort = remote, localPort = local)
                }
                else -> null
            }
        }
    }
}

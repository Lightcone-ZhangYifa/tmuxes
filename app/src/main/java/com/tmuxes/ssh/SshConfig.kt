package com.tmuxes.ssh

/**
 * Authentication configuration for an SSH connection.
 */
sealed class AuthConfig {
    data class Password(val password: String) : AuthConfig() {
        override fun toString() = "Password(***)"
    }
    data class Key(val privateKeyData: String, val passphrase: String? = null) : AuthConfig() {
        override fun toString() = "Key(***)"
    }
}

/**
 * A remote port forward specification: binds [remotePort] on the remote side
 * and forwards traffic to [localHost]:[localPort] on the local side.
 */
data class RemoteForward(
    val remotePort: Int,
    val localHost: String = "127.0.0.1",
    val localPort: Int
)

/**
 * Complete SSH connection configuration.
 *
 * Centralises every tuneable parameter for an SSH session into a single
 * immutable value object.  [SshConnection] reads fields from this config
 * rather than accepting them as individual constructor parameters.
 *
 * @param hostname Remote host to connect to.
 * @param port Remote port (default 22).
 * @param username User name for authentication.
 * @param auth Credentials -- password or private key.
 * @param termType TERM type advertised when allocating a PTY.
 * @param connectTimeout TCP connect timeout in milliseconds.
 * @param transportTimeout Transport (key-exchange) timeout in milliseconds.
 * @param readTimeout Read timeout in milliseconds (0 = infinite, suitable for interactive shells).
 * @param keepAliveInterval Seconds between keep-alive packets (0 to disable).
 * @param keepAliveMaxCount Number of missed keep-alives before the connection is considered dead.
 * @param compression Whether to enable zlib compression.
 * @param strictHostKey Host-key verification mode: "accept-new", "yes", or "no".
 * @param envVars Environment variables to request on the remote session.
 * @param preferredCiphers Ordered cipher names to prefer (empty = use library defaults).
 * @param preferredKex Ordered key-exchange algorithm names to prefer.
 * @param preferredMacs Ordered MAC algorithm names to prefer.
 * @param preferredHostKeyAlgs Ordered host-key algorithm names to prefer.
 * @param remoteForwards Remote port forward specifications to set up after authentication.
 */
data class SshConfig(
    val hostname: String,
    val port: Int = 22,
    val username: String,
    val auth: AuthConfig,
    val termType: String = "xterm-256color",
    val connectTimeout: Int = 30_000,
    val transportTimeout: Int = 120_000,
    val readTimeout: Int = 0,
    val keepAliveInterval: Int = 60,
    val keepAliveMaxCount: Int = 3,
    val compression: Boolean = false,
    val strictHostKey: String = "accept-new",
    val envVars: Map<String, String> = emptyMap(),
    val preferredCiphers: List<String> = emptyList(),
    val preferredKex: List<String> = emptyList(),
    val preferredMacs: List<String> = emptyList(),
    val preferredHostKeyAlgs: List<String> = emptyList(),
    val remoteForwards: List<RemoteForward> = emptyList()
) {
    companion object {
        /**
         * Parse a multi-line `KEY=VALUE` string into a map.
         * Lines without `=` are silently skipped.
         */
        fun parseEnvVars(text: String): Map<String, String> =
            text.lines().filter { it.contains('=') }.associate {
                val (k, v) = it.split('=', limit = 2)
                k.trim() to v.trim()
            }

        /**
         * Split a comma-separated string into a list of trimmed, non-empty tokens.
         */
        fun parseCommaSeparated(text: String): List<String> =
            if (text.isBlank()) emptyList()
            else text.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }
}

package com.tmuxes.ssh

/**
 * Represents a single Host block from an OpenSSH config file.
 */
data class SshConfigHost(
    val host: String,
    val hostName: String? = null,
    val user: String? = null,
    val port: Int? = null,
    val identityFile: String? = null,
    val proxyJump: String? = null
) {
    /** Display-friendly summary: alias → user@host:port */
    val summary: String
        get() = buildString {
            append(host)
            val target = hostName ?: return@buildString
            append(" → ")
            if (user != null) append("$user@")
            append(target)
            if (port != null && port != 22) append(":$port")
        }
}

/**
 * Parses OpenSSH `~/.ssh/config` text into a list of [SshConfigHost] entries.
 *
 * Handles standard directives: Host, HostName, User, Port, IdentityFile, ProxyJump.
 * Skips wildcard-only Host entries (`Host *`) and `Match` blocks.
 */
object SshConfigParser {

    private val DIRECTIVE_REGEX = Regex("""^\s*(\S+)\s+(.+)$""")

    fun parse(configText: String): List<SshConfigHost> {
        val hosts = mutableListOf<SshConfigHost>()
        var currentHost: String? = null
        var hostName: String? = null
        var user: String? = null
        var port: Int? = null
        var identityFile: String? = null
        var proxyJump: String? = null
        var inMatchBlock = false

        fun flushHost() {
            val h = currentHost ?: return
            hosts.add(
                SshConfigHost(
                    host = h,
                    hostName = hostName,
                    user = user,
                    port = port,
                    identityFile = identityFile,
                    proxyJump = proxyJump
                )
            )
            currentHost = null
            hostName = null
            user = null
            port = null
            identityFile = null
            proxyJump = null
        }

        for (rawLine in configText.lines()) {
            val line = rawLine.trim()

            // Skip empty lines and comments
            if (line.isEmpty() || line.startsWith("#")) continue

            val match = DIRECTIVE_REGEX.matchEntire(line) ?: continue
            val directive = match.groupValues[1]
            val value = match.groupValues[2].trim()

            when (directive.lowercase()) {
                "host" -> {
                    inMatchBlock = false
                    flushHost()
                    // Skip wildcard-only patterns
                    val patterns = value.split(Regex("\\s+"))
                    if (patterns.all { it == "*" || it == "*.*" }) continue
                    // Skip negation-only patterns
                    if (patterns.all { it.startsWith("!") }) continue
                    // Use first non-negation pattern as the host alias
                    val alias = patterns.firstOrNull { !it.startsWith("!") } ?: continue
                    currentHost = alias
                }
                "match" -> {
                    flushHost()
                    inMatchBlock = true
                }
                "hostname" -> if (!inMatchBlock && currentHost != null) hostName = value
                "user" -> if (!inMatchBlock && currentHost != null) user = value
                "port" -> if (!inMatchBlock && currentHost != null) port = value.toIntOrNull()
                "identityfile" -> if (!inMatchBlock && currentHost != null) identityFile = value
                "proxyjump" -> if (!inMatchBlock && currentHost != null) proxyJump = value
            }
        }

        flushHost()
        com.tmuxes.util.AppLogger.i(com.tmuxes.util.AppLogger.Category.CONFIG) {
            "ssh.config parse ← hosts=${hosts.size} bytes=${configText.length}"
        }
        return hosts
    }
}

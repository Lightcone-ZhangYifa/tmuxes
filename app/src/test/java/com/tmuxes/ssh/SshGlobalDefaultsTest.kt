package com.tmuxes.ssh

import com.tmuxes.data.model.ServerEntity
import com.tmuxes.data.settings.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SshGlobalDefaultsTest {

    // -----------------------------------------------------------------
    // SshGlobalDefaults default values
    // -----------------------------------------------------------------

    @Test
    fun `default values come from the Settings registry`() {
        val defaults = SshGlobalDefaults()
        assertEquals(Settings.sshDefaultTerm.default, defaults.defaultTerm)
        assertEquals(Settings.sshConnectionTimeout.default, defaults.connectionTimeout)
        assertEquals(Settings.sshTransportTimeout.default, defaults.transportTimeout)
        assertEquals(Settings.sshReadTimeout.default, defaults.readTimeout)
        assertEquals(Settings.sshKeepaliveMaxCount.default, defaults.keepaliveMaxCount)
        assertEquals(Settings.sshStrictHostKey.default, defaults.strictHostKey)
        assertEquals(Settings.sshEnvVars.default, defaults.envVars)
        assertEquals(Settings.sshPreferredCiphers.default, defaults.preferredCiphers)
        assertEquals(Settings.sshPreferredKex.default, defaults.preferredKex)
        assertEquals(Settings.sshPreferredMacs.default, defaults.preferredMacs)
        assertEquals(Settings.sshPreferredHostKeyAlgs.default, defaults.preferredHostKeyAlgs)
    }

    @Test
    fun `default values have expected literal values`() {
        val defaults = SshGlobalDefaults()
        assertEquals("xterm-256color", defaults.defaultTerm)
        assertEquals(30, defaults.connectionTimeout)
        assertEquals(120, defaults.transportTimeout)
        assertEquals(0, defaults.readTimeout)
        assertEquals(3, defaults.keepaliveMaxCount)
        assertEquals("accept-new", defaults.strictHostKey)
        assertEquals("", defaults.envVars)
        assertEquals("", defaults.preferredCiphers)
        assertEquals("", defaults.preferredKex)
        assertEquals("", defaults.preferredMacs)
        assertEquals("", defaults.preferredHostKeyAlgs)
    }

    // -----------------------------------------------------------------
    // buildSshConfig -- null server fields fall back to global defaults
    // -----------------------------------------------------------------

    private val testAuth = AuthConfig.Password("secret")

    private fun baseServer() = ServerEntity(
        id = 1,
        hostname = "example.com",
        port = 22,
        username = "user"
    )

    @Test
    fun `null server fields fall back to global defaults`() {
        val globals = SshGlobalDefaults(
            defaultTerm = "vt100",
            connectionTimeout = 10,
            transportTimeout = 60,
            readTimeout = 5,
            keepaliveMaxCount = 5,
            strictHostKey = "yes",
            envVars = "LANG=en_US.UTF-8",
            preferredCiphers = "aes256-ctr,aes128-ctr",
            preferredKex = "curve25519-sha256",
            preferredMacs = "hmac-sha2-256",
            preferredHostKeyAlgs = "ssh-ed25519"
        )

        // Server with ALL nullable advanced fields null
        val server = baseServer()

        val config = SshConfigBuilder.build(server, testAuth, globals)

        assertEquals("example.com", config.hostname)
        assertEquals(22, config.port)
        assertEquals("user", config.username)
        assertEquals(testAuth, config.auth)

        // Fallback to globals
        assertEquals("vt100", config.termType)
        assertEquals(10_000, config.connectTimeout)
        assertEquals(60_000, config.transportTimeout)
        assertEquals(5_000, config.readTimeout)
        assertEquals(5, config.keepAliveMaxCount)
        assertEquals("yes", config.strictHostKey)
        assertEquals(mapOf("LANG" to "en_US.UTF-8"), config.envVars)
        assertEquals(listOf("aes256-ctr", "aes128-ctr"), config.preferredCiphers)
        assertEquals(listOf("curve25519-sha256"), config.preferredKex)
        assertEquals(listOf("hmac-sha2-256"), config.preferredMacs)
        assertEquals(listOf("ssh-ed25519"), config.preferredHostKeyAlgs)
    }

    @Test
    fun `non-null server fields override global defaults`() {
        val globals = SshGlobalDefaults(
            defaultTerm = "vt100",
            connectionTimeout = 10,
            transportTimeout = 60,
            readTimeout = 5,
            keepaliveMaxCount = 5,
            strictHostKey = "yes",
            envVars = "LANG=en_US.UTF-8",
            preferredCiphers = "aes256-ctr",
            preferredKex = "curve25519-sha256",
            preferredMacs = "hmac-sha2-256",
            preferredHostKeyAlgs = "ssh-ed25519"
        )

        val server = baseServer().copy(
            termType = "screen",
            connectionTimeout = 20,
            transportTimeout = 90,
            readTimeout = 10,
            keepaliveMaxCount = 7,
            strictHostKey = "no",
            envVars = "FOO=bar",
            preferredCiphers = "chacha20-poly1305",
            preferredKex = "diffie-hellman-group14-sha256",
            preferredMacs = "hmac-sha2-512",
            preferredHostKeyAlgs = "rsa-sha2-512"
        )

        val config = SshConfigBuilder.build(server, testAuth, globals)

        // All server overrides
        assertEquals("screen", config.termType)
        assertEquals(20_000, config.connectTimeout)
        assertEquals(90_000, config.transportTimeout)
        assertEquals(10_000, config.readTimeout)
        assertEquals(7, config.keepAliveMaxCount)
        assertEquals("no", config.strictHostKey)
        assertEquals(listOf("chacha20-poly1305"), config.preferredCiphers)
        assertEquals(listOf("diffie-hellman-group14-sha256"), config.preferredKex)
        assertEquals(listOf("hmac-sha2-512"), config.preferredMacs)
        assertEquals(listOf("rsa-sha2-512"), config.preferredHostKeyAlgs)
    }

    // -----------------------------------------------------------------
    // buildSshConfig -- env var merging
    // -----------------------------------------------------------------

    @Test
    fun `env vars merge global and server entries`() {
        val globals = SshGlobalDefaults(envVars = "LANG=en_US.UTF-8")
        val server = baseServer().copy(envVars = "EDITOR=vim")

        val config = SshConfigBuilder.build(server, testAuth, globals)
        assertEquals(mapOf("LANG" to "en_US.UTF-8", "EDITOR" to "vim"), config.envVars)
    }

    @Test
    fun `server env var overrides global with same key`() {
        val globals = SshGlobalDefaults(envVars = "LANG=en_US.UTF-8")
        val server = baseServer().copy(envVars = "LANG=C")

        val config = SshConfigBuilder.build(server, testAuth, globals)
        // Server value wins because it appears later in the parsed text
        assertEquals(mapOf("LANG" to "C"), config.envVars)
    }

    @Test
    fun `empty global and null server env vars yield empty map`() {
        val globals = SshGlobalDefaults(envVars = "")
        val server = baseServer()

        val config = SshConfigBuilder.build(server, testAuth, globals)
        assertTrue(config.envVars.isEmpty())
    }

    @Test
    fun `global env vars only when server is null`() {
        val globals = SshGlobalDefaults(envVars = "A=1\nB=2")
        val server = baseServer()

        val config = SshConfigBuilder.build(server, testAuth, globals)
        assertEquals(mapOf("A" to "1", "B" to "2"), config.envVars)
    }

    // -----------------------------------------------------------------
    // buildSshConfig -- timeout conversion (seconds to milliseconds)
    // -----------------------------------------------------------------

    @Test
    fun `timeouts are converted from seconds to milliseconds`() {
        val globals = SshGlobalDefaults()
        val server = baseServer().copy(
            connectionTimeout = 15,
            transportTimeout = 45,
            readTimeout = 3
        )

        val config = SshConfigBuilder.build(server, testAuth, globals)
        assertEquals(15_000, config.connectTimeout)
        assertEquals(45_000, config.transportTimeout)
        assertEquals(3_000, config.readTimeout)
    }

    @Test
    fun `global timeout defaults are converted from seconds to milliseconds`() {
        val globals = SshGlobalDefaults(
            connectionTimeout = 30,
            transportTimeout = 120,
            readTimeout = 0
        )
        val server = baseServer()

        val config = SshConfigBuilder.build(server, testAuth, globals)
        assertEquals(30_000, config.connectTimeout)
        assertEquals(120_000, config.transportTimeout)
        assertEquals(0, config.readTimeout)
    }

    // -----------------------------------------------------------------
    // buildSshConfig -- server overrides global for keepAliveInterval and compression
    // -----------------------------------------------------------------

    @Test
    fun `keepAliveInterval and compression from server override global`() {
        val globals = SshGlobalDefaults(keepaliveInterval = 60, compression = false)
        val server = baseServer().copy(
            keepAliveInterval = 120,
            compression = true
        )

        val config = SshConfigBuilder.build(server, testAuth, globals)
        assertEquals(120, config.keepAliveInterval)
        assertEquals(true, config.compression)
    }

    @Test
    fun `keepAliveInterval and compression fall through to global when null`() {
        val globals = SshGlobalDefaults(keepaliveInterval = 90, compression = true)
        val server = baseServer()  // keepAliveInterval=null, compression=null

        val config = SshConfigBuilder.build(server, testAuth, globals)
        assertEquals(90, config.keepAliveInterval)
        assertEquals(true, config.compression)
    }

    @Test
    fun `server hostname port and username pass through`() {
        val globals = SshGlobalDefaults()
        val server = baseServer().copy(hostname = "10.0.0.1", port = 2222, username = "admin")

        val config = SshConfigBuilder.build(server, testAuth, globals)
        assertEquals("10.0.0.1", config.hostname)
        assertEquals(2222, config.port)
        assertEquals("admin", config.username)
    }

    // -----------------------------------------------------------------
    // buildSshConfig -- auth config pass through
    // -----------------------------------------------------------------

    @Test
    fun `password auth config is passed through`() {
        val auth = AuthConfig.Password("mypass")
        val config = SshConfigBuilder.build(baseServer(), auth, SshGlobalDefaults())
        assertEquals(auth, config.auth)
    }

    @Test
    fun `key auth config is passed through`() {
        val auth = AuthConfig.Key("private-key-data", "passphrase")
        val config = SshConfigBuilder.build(baseServer(), auth, SshGlobalDefaults())
        assertEquals(auth, config.auth)
    }

    // -----------------------------------------------------------------
    // buildSshConfig -- remote forwards parsing
    // -----------------------------------------------------------------

    @Test
    fun `null remote forwards yields empty list`() {
        val server = baseServer()
        val config = SshConfigBuilder.build(server, testAuth, SshGlobalDefaults())
        assertTrue(config.remoteForwards.isEmpty())
    }

    @Test
    fun `remote forwards with three-part format`() {
        val server = baseServer().copy(remoteForwards = "8080:localhost:80")
        val config = SshConfigBuilder.build(server, testAuth, SshGlobalDefaults())
        assertEquals(1, config.remoteForwards.size)
        assertEquals(RemoteForward(remotePort = 8080, localHost = "localhost", localPort = 80), config.remoteForwards[0])
    }

    @Test
    fun `remote forwards with two-part format defaults localHost`() {
        val server = baseServer().copy(remoteForwards = "9090:3000")
        val config = SshConfigBuilder.build(server, testAuth, SshGlobalDefaults())
        assertEquals(1, config.remoteForwards.size)
        assertEquals(RemoteForward(remotePort = 9090, localHost = "127.0.0.1", localPort = 3000), config.remoteForwards[0])
    }

    @Test
    fun `multiple remote forwards on separate lines`() {
        val server = baseServer().copy(remoteForwards = "8080:localhost:80\n9090:127.0.0.1:3000")
        val config = SshConfigBuilder.build(server, testAuth, SshGlobalDefaults())
        assertEquals(2, config.remoteForwards.size)
        assertEquals(RemoteForward(remotePort = 8080, localHost = "localhost", localPort = 80), config.remoteForwards[0])
        assertEquals(RemoteForward(remotePort = 9090, localHost = "127.0.0.1", localPort = 3000), config.remoteForwards[1])
    }

    @Test
    fun `invalid remote forward lines are skipped`() {
        val server = baseServer().copy(remoteForwards = "invalid\n8080:localhost:80\n::\nnot:a:number:here")
        val config = SshConfigBuilder.build(server, testAuth, SshGlobalDefaults())
        assertEquals(1, config.remoteForwards.size)
        assertEquals(RemoteForward(remotePort = 8080, localHost = "localhost", localPort = 80), config.remoteForwards[0])
    }

    @Test
    fun `blank remote forwards string yields empty list`() {
        val server = baseServer().copy(remoteForwards = "  \n  ")
        val config = SshConfigBuilder.build(server, testAuth, SshGlobalDefaults())
        assertTrue(config.remoteForwards.isEmpty())
    }

    // -----------------------------------------------------------------
    // parseRemoteForwards static method
    // -----------------------------------------------------------------

    @Test
    fun `parseRemoteForwards null returns empty`() {
        assertEquals(emptyList<RemoteForward>(), SshConfigBuilder.parseRemoteForwards(null))
    }

    @Test
    fun `parseRemoteForwards empty string returns empty`() {
        assertEquals(emptyList<RemoteForward>(), SshConfigBuilder.parseRemoteForwards(""))
    }

    @Test
    fun `parseRemoteForwards three-part line`() {
        val result = SshConfigBuilder.parseRemoteForwards("443:10.0.0.1:8443")
        assertEquals(listOf(RemoteForward(remotePort = 443, localHost = "10.0.0.1", localPort = 8443)), result)
    }

    @Test
    fun `parseRemoteForwards two-part line`() {
        val result = SshConfigBuilder.parseRemoteForwards("5000:5000")
        assertEquals(listOf(RemoteForward(remotePort = 5000, localHost = "127.0.0.1", localPort = 5000)), result)
    }

    @Test
    fun `parseRemoteForwards skips lines with non-integer ports`() {
        val result = SshConfigBuilder.parseRemoteForwards("abc:localhost:80")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseRemoteForwards handles mixed valid and invalid`() {
        val text = "8080:localhost:80\nbad\n9090:3000\n\n:::"
        val result = SshConfigBuilder.parseRemoteForwards(text)
        assertEquals(2, result.size)
        assertEquals(RemoteForward(remotePort = 8080, localHost = "localhost", localPort = 80), result[0])
        assertEquals(RemoteForward(remotePort = 9090, localHost = "127.0.0.1", localPort = 3000), result[1])
    }

    // -----------------------------------------------------------------
    // buildSshConfig -- comma-separated algorithm lists
    // -----------------------------------------------------------------

    @Test
    fun `empty algorithm string yields empty list`() {
        val globals = SshGlobalDefaults(preferredCiphers = "")
        val server = baseServer()
        val config = SshConfigBuilder.build(server, testAuth, globals)
        assertTrue(config.preferredCiphers.isEmpty())
    }

    @Test
    fun `multiple comma-separated algorithms are split and trimmed`() {
        val globals = SshGlobalDefaults(preferredCiphers = " aes256-ctr , aes128-ctr , chacha20-poly1305 ")
        val server = baseServer()
        val config = SshConfigBuilder.build(server, testAuth, globals)
        assertEquals(listOf("aes256-ctr", "aes128-ctr", "chacha20-poly1305"), config.preferredCiphers)
    }

    // -----------------------------------------------------------------
    // buildSshConfig -- all defaults produce valid config
    // -----------------------------------------------------------------

    @Test
    fun `all-default build produces valid config with expected defaults`() {
        val config = SshConfigBuilder.build(baseServer(), testAuth, SshGlobalDefaults())

        assertEquals("xterm-256color", config.termType)
        assertEquals(30_000, config.connectTimeout)
        assertEquals(120_000, config.transportTimeout)
        assertEquals(0, config.readTimeout)
        assertEquals(60, config.keepAliveInterval)
        assertEquals(3, config.keepAliveMaxCount)
        assertEquals(false, config.compression)
        assertEquals("accept-new", config.strictHostKey)
        assertTrue(config.envVars.isEmpty())
        assertTrue(config.preferredCiphers.isEmpty())
        assertTrue(config.preferredKex.isEmpty())
        assertTrue(config.preferredMacs.isEmpty())
        assertTrue(config.preferredHostKeyAlgs.isEmpty())
        assertTrue(config.remoteForwards.isEmpty())
    }
}

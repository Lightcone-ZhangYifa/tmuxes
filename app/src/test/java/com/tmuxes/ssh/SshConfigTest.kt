package com.tmuxes.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SshConfigTest {

    // -------------------------------------------------------------------------
    // parseEnvVars
    // -------------------------------------------------------------------------

    @Test
    fun `parseEnvVars with valid multi-line input`() {
        val input = "FOO=bar\nBAZ=qux"
        val result = SshConfig.parseEnvVars(input)
        assertEquals(mapOf("FOO" to "bar", "BAZ" to "qux"), result)
    }

    @Test
    fun `parseEnvVars trims whitespace around keys and values`() {
        val input = "  KEY  =  value  "
        val result = SshConfig.parseEnvVars(input)
        assertEquals(mapOf("KEY" to "value"), result)
    }

    @Test
    fun `parseEnvVars with value containing equals sign`() {
        val input = "CONN=host=localhost;port=5432"
        val result = SshConfig.parseEnvVars(input)
        assertEquals(mapOf("CONN" to "host=localhost;port=5432"), result)
    }

    @Test
    fun `parseEnvVars with empty string`() {
        val result = SshConfig.parseEnvVars("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseEnvVars with blank string`() {
        val result = SshConfig.parseEnvVars("   ")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseEnvVars skips lines without equals`() {
        val input = "GOOD=value\nmalformed line\nALSO_GOOD=123"
        val result = SshConfig.parseEnvVars(input)
        assertEquals(mapOf("GOOD" to "value", "ALSO_GOOD" to "123"), result)
    }

    @Test
    fun `parseEnvVars with empty value`() {
        val input = "EMPTY="
        val result = SshConfig.parseEnvVars(input)
        assertEquals(mapOf("EMPTY" to ""), result)
    }

    @Test
    fun `parseEnvVars mixed valid and blank lines`() {
        val input = "A=1\n\n\nB=2\n"
        val result = SshConfig.parseEnvVars(input)
        assertEquals(mapOf("A" to "1", "B" to "2"), result)
    }

    // -------------------------------------------------------------------------
    // parseCommaSeparated
    // -------------------------------------------------------------------------

    @Test
    fun `parseCommaSeparated with multiple items`() {
        val result = SshConfig.parseCommaSeparated("aes256-ctr,aes128-ctr,aes256-gcm")
        assertEquals(listOf("aes256-ctr", "aes128-ctr", "aes256-gcm"), result)
    }

    @Test
    fun `parseCommaSeparated trims whitespace`() {
        val result = SshConfig.parseCommaSeparated(" aes256-ctr , aes128-ctr ")
        assertEquals(listOf("aes256-ctr", "aes128-ctr"), result)
    }

    @Test
    fun `parseCommaSeparated with single item`() {
        val result = SshConfig.parseCommaSeparated("aes256-ctr")
        assertEquals(listOf("aes256-ctr"), result)
    }

    @Test
    fun `parseCommaSeparated with empty string`() {
        val result = SshConfig.parseCommaSeparated("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseCommaSeparated with blank string`() {
        val result = SshConfig.parseCommaSeparated("   ")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseCommaSeparated filters empty tokens from trailing comma`() {
        val result = SshConfig.parseCommaSeparated("a,b,")
        assertEquals(listOf("a", "b"), result)
    }

    @Test
    fun `parseCommaSeparated filters empty tokens from leading comma`() {
        val result = SshConfig.parseCommaSeparated(",a,b")
        assertEquals(listOf("a", "b"), result)
    }

    @Test
    fun `parseCommaSeparated with only commas`() {
        val result = SshConfig.parseCommaSeparated(",,,")
        assertTrue(result.isEmpty())
    }

    // -------------------------------------------------------------------------
    // Default values
    // -------------------------------------------------------------------------

    @Test
    fun `default values are correct`() {
        val config = SshConfig(
            hostname = "example.com",
            username = "user",
            auth = AuthConfig.Password("pass")
        )
        assertEquals("example.com", config.hostname)
        assertEquals(22, config.port)
        assertEquals("user", config.username)
        assertEquals("xterm-256color", config.termType)
        assertEquals(30_000, config.connectTimeout)
        assertEquals(120_000, config.transportTimeout)
        assertEquals(0, config.readTimeout)
        assertEquals(60, config.keepAliveInterval)
        assertEquals(3, config.keepAliveMaxCount)
        assertFalse(config.compression)
        assertEquals("accept-new", config.strictHostKey)
        assertTrue(config.envVars.isEmpty())
        assertTrue(config.preferredCiphers.isEmpty())
        assertTrue(config.preferredKex.isEmpty())
        assertTrue(config.preferredMacs.isEmpty())
        assertTrue(config.preferredHostKeyAlgs.isEmpty())
        assertTrue(config.remoteForwards.isEmpty())
    }

    @Test
    fun `all parameters can be overridden`() {
        val envVars = mapOf("LANG" to "en_US.UTF-8")
        val forwards = listOf(RemoteForward(remotePort = 8080, localPort = 80))
        val config = SshConfig(
            hostname = "host",
            port = 2222,
            username = "admin",
            auth = AuthConfig.Key("keydata", "phrase"),
            termType = "vt100",
            connectTimeout = 5000,
            transportTimeout = 60000,
            readTimeout = 10000,
            keepAliveInterval = 30,
            keepAliveMaxCount = 5,
            compression = true,
            strictHostKey = "yes",
            envVars = envVars,
            preferredCiphers = listOf("aes256-ctr"),
            preferredKex = listOf("curve25519-sha256"),
            preferredMacs = listOf("hmac-sha2-256"),
            preferredHostKeyAlgs = listOf("ssh-ed25519"),
            remoteForwards = forwards
        )
        assertEquals("host", config.hostname)
        assertEquals(2222, config.port)
        assertEquals("admin", config.username)
        assertEquals("vt100", config.termType)
        assertEquals(5000, config.connectTimeout)
        assertEquals(60000, config.transportTimeout)
        assertEquals(10000, config.readTimeout)
        assertEquals(30, config.keepAliveInterval)
        assertEquals(5, config.keepAliveMaxCount)
        assertTrue(config.compression)
        assertEquals("yes", config.strictHostKey)
        assertEquals(envVars, config.envVars)
        assertEquals(listOf("aes256-ctr"), config.preferredCiphers)
        assertEquals(listOf("curve25519-sha256"), config.preferredKex)
        assertEquals(listOf("hmac-sha2-256"), config.preferredMacs)
        assertEquals(listOf("ssh-ed25519"), config.preferredHostKeyAlgs)
        assertEquals(forwards, config.remoteForwards)
    }

    // -------------------------------------------------------------------------
    // RemoteForward
    // -------------------------------------------------------------------------

    @Test
    fun `RemoteForward default localHost`() {
        val fwd = RemoteForward(remotePort = 9090, localPort = 8080)
        assertEquals(9090, fwd.remotePort)
        assertEquals("127.0.0.1", fwd.localHost)
        assertEquals(8080, fwd.localPort)
    }

    @Test
    fun `RemoteForward with custom localHost`() {
        val fwd = RemoteForward(remotePort = 443, localHost = "0.0.0.0", localPort = 8443)
        assertEquals(443, fwd.remotePort)
        assertEquals("0.0.0.0", fwd.localHost)
        assertEquals(8443, fwd.localPort)
    }

    @Test
    fun `RemoteForward equality`() {
        val a = RemoteForward(remotePort = 80, localPort = 80)
        val b = RemoteForward(remotePort = 80, localHost = "127.0.0.1", localPort = 80)
        assertEquals(a, b)
    }

    @Test
    fun `RemoteForward copy`() {
        val original = RemoteForward(remotePort = 3000, localPort = 3000)
        val modified = original.copy(localHost = "10.0.0.1")
        assertEquals("10.0.0.1", modified.localHost)
        assertEquals(3000, modified.remotePort)
        assertEquals(3000, modified.localPort)
    }

    // -------------------------------------------------------------------------
    // AuthConfig
    // -------------------------------------------------------------------------

    @Test
    fun `AuthConfig Password masks toString`() {
        val auth = AuthConfig.Password("secret")
        assertEquals("Password(***)", auth.toString())
        assertEquals("secret", auth.password)
    }

    @Test
    fun `AuthConfig Key masks toString`() {
        val auth = AuthConfig.Key("keydata", "passphrase")
        assertEquals("Key(***)", auth.toString())
        assertEquals("keydata", auth.privateKeyData)
        assertEquals("passphrase", auth.passphrase)
    }

    @Test
    fun `AuthConfig Key with null passphrase`() {
        val auth = AuthConfig.Key("keydata")
        assertNull(auth.passphrase)
    }

    // -------------------------------------------------------------------------
    // SshConfig data class identity (copy, equals)
    // -------------------------------------------------------------------------

    @Test
    fun `SshConfig copy preserves unmodified fields`() {
        val original = SshConfig(
            hostname = "h",
            username = "u",
            auth = AuthConfig.Password("p"),
            termType = "vt100"
        )
        val copied = original.copy(port = 2222)
        assertEquals(2222, copied.port)
        assertEquals("vt100", copied.termType)
        assertEquals("h", copied.hostname)
    }

    @Test
    fun `SshConfig equality`() {
        val a = SshConfig(hostname = "h", username = "u", auth = AuthConfig.Password("p"))
        val b = SshConfig(hostname = "h", username = "u", auth = AuthConfig.Password("p"))
        assertEquals(a, b)
    }
}

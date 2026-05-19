package com.tmuxes.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class ServerEntityTest {

    // --- Default construction: all new fields are null ---

    @Test fun `default entity has null termType`() = assertNull(
        ServerEntity(hostname = "h", username = "u").termType
    )
    @Test fun `default entity has null connectionTimeout`() = assertNull(
        ServerEntity(hostname = "h", username = "u").connectionTimeout
    )
    @Test fun `default entity has null transportTimeout`() = assertNull(
        ServerEntity(hostname = "h", username = "u").transportTimeout
    )
    @Test fun `default entity has null readTimeout`() = assertNull(
        ServerEntity(hostname = "h", username = "u").readTimeout
    )
    @Test fun `default entity has null keepaliveMaxCount`() = assertNull(
        ServerEntity(hostname = "h", username = "u").keepaliveMaxCount
    )
    @Test fun `default entity has null strictHostKey`() = assertNull(
        ServerEntity(hostname = "h", username = "u").strictHostKey
    )
    @Test fun `default entity has null envVars`() = assertNull(
        ServerEntity(hostname = "h", username = "u").envVars
    )
    @Test fun `default entity has null preferredCiphers`() = assertNull(
        ServerEntity(hostname = "h", username = "u").preferredCiphers
    )
    @Test fun `default entity has null preferredKex`() = assertNull(
        ServerEntity(hostname = "h", username = "u").preferredKex
    )
    @Test fun `default entity has null preferredMacs`() = assertNull(
        ServerEntity(hostname = "h", username = "u").preferredMacs
    )
    @Test fun `default entity has null preferredHostKeyAlgs`() = assertNull(
        ServerEntity(hostname = "h", username = "u").preferredHostKeyAlgs
    )
    @Test fun `default entity has null remoteForwards`() = assertNull(
        ServerEntity(hostname = "h", username = "u").remoteForwards
    )

    // --- Explicit construction: all fields set ---

    @Test fun `explicit termType is stored`() = assertEquals("xterm-256color",
        ServerEntity(hostname = "h", username = "u", termType = "xterm-256color").termType
    )
    @Test fun `explicit connectionTimeout is stored`() = assertEquals(10,
        ServerEntity(hostname = "h", username = "u", connectionTimeout = 10).connectionTimeout
    )
    @Test fun `explicit transportTimeout is stored`() = assertEquals(60,
        ServerEntity(hostname = "h", username = "u", transportTimeout = 60).transportTimeout
    )
    @Test fun `explicit readTimeout is stored`() = assertEquals(5,
        ServerEntity(hostname = "h", username = "u", readTimeout = 5).readTimeout
    )
    @Test fun `explicit keepaliveMaxCount is stored`() = assertEquals(5,
        ServerEntity(hostname = "h", username = "u", keepaliveMaxCount = 5).keepaliveMaxCount
    )
    @Test fun `explicit strictHostKey is stored`() = assertEquals("yes",
        ServerEntity(hostname = "h", username = "u", strictHostKey = "yes").strictHostKey
    )
    @Test fun `explicit envVars is stored`() = assertEquals("LANG=en_US.UTF-8,TERM=screen",
        ServerEntity(hostname = "h", username = "u", envVars = "LANG=en_US.UTF-8,TERM=screen").envVars
    )
    @Test fun `explicit preferredCiphers is stored`() = assertEquals("aes256-ctr,aes128-ctr",
        ServerEntity(hostname = "h", username = "u", preferredCiphers = "aes256-ctr,aes128-ctr").preferredCiphers
    )
    @Test fun `explicit preferredKex is stored`() = assertEquals("curve25519-sha256",
        ServerEntity(hostname = "h", username = "u", preferredKex = "curve25519-sha256").preferredKex
    )
    @Test fun `explicit preferredMacs is stored`() = assertEquals("hmac-sha2-256",
        ServerEntity(hostname = "h", username = "u", preferredMacs = "hmac-sha2-256").preferredMacs
    )
    @Test fun `explicit preferredHostKeyAlgs is stored`() = assertEquals("ssh-ed25519,rsa-sha2-512",
        ServerEntity(hostname = "h", username = "u", preferredHostKeyAlgs = "ssh-ed25519,rsa-sha2-512").preferredHostKeyAlgs
    )
    @Test fun `explicit remoteForwards is stored`() = assertEquals("8080:localhost:80,9090:localhost:90",
        ServerEntity(hostname = "h", username = "u", remoteForwards = "8080:localhost:80,9090:localhost:90").remoteForwards
    )

    // --- All fields set at once ---

    @Test fun `entity with all advanced fields set`() {
        val entity = ServerEntity(
            hostname = "example.com",
            username = "admin",
            termType = "vt100",
            connectionTimeout = 15,
            transportTimeout = 90,
            readTimeout = 10,
            keepaliveMaxCount = 7,
            strictHostKey = "no",
            envVars = "FOO=bar",
            preferredCiphers = "chacha20-poly1305",
            preferredKex = "diffie-hellman-group14-sha256",
            preferredMacs = "hmac-sha2-512",
            preferredHostKeyAlgs = "ssh-ed25519",
            remoteForwards = "3000:localhost:3000"
        )
        assertEquals("vt100", entity.termType)
        assertEquals(15, entity.connectionTimeout)
        assertEquals(90, entity.transportTimeout)
        assertEquals(10, entity.readTimeout)
        assertEquals(7, entity.keepaliveMaxCount)
        assertEquals("no", entity.strictHostKey)
        assertEquals("FOO=bar", entity.envVars)
        assertEquals("chacha20-poly1305", entity.preferredCiphers)
        assertEquals("diffie-hellman-group14-sha256", entity.preferredKex)
        assertEquals("hmac-sha2-512", entity.preferredMacs)
        assertEquals("ssh-ed25519", entity.preferredHostKeyAlgs)
        assertEquals("3000:localhost:3000", entity.remoteForwards)
    }

    // --- Null means "use global default" ---

    @Test fun `null fields indicate use global default`() {
        val entity = ServerEntity(hostname = "h", username = "u")
        // All advanced config fields should be null, meaning "use global default"
        val advancedFields = listOf(
            entity.termType, entity.connectionTimeout, entity.transportTimeout,
            entity.readTimeout, entity.keepaliveMaxCount, entity.strictHostKey,
            entity.envVars,
            entity.preferredCiphers, entity.preferredKex, entity.preferredMacs,
            entity.preferredHostKeyAlgs, entity.remoteForwards
        )
        assertEquals(12, advancedFields.size)
        advancedFields.forEach { assertNull(it) }
    }

    @Test fun `non-null field overrides global default`() {
        val entity = ServerEntity(hostname = "h", username = "u", termType = "screen")
        assertNotNull(entity.termType)
        assertEquals("screen", entity.termType)
        // Other fields still null (global default)
        assertNull(entity.connectionTimeout)
        assertNull(entity.remoteForwards)
    }

    // --- Existing fields still work ---

    @Test fun `displayName uses name when set`() {
        val entity = ServerEntity(hostname = "h", username = "u", name = "My Server")
        assertEquals("My Server", entity.displayName)
    }

    @Test fun `displayName falls back to user at host`() {
        val entity = ServerEntity(hostname = "example.com", username = "admin")
        assertEquals("admin@example.com", entity.displayName)
    }

    // --- Copy with selective override ---

    @Test fun `copy can set one advanced field while others stay null`() {
        val original = ServerEntity(hostname = "h", username = "u")
        val modified = original.copy(connectionTimeout = 45)
        assertEquals(45, modified.connectionTimeout)
        assertNull(modified.termType)
        assertNull(modified.remoteForwards)
    }

    @Test fun `copy can clear an advanced field back to null`() {
        val original = ServerEntity(hostname = "h", username = "u", termType = "xterm")
        val cleared = original.copy(termType = null)
        assertNull(cleared.termType)
    }
}

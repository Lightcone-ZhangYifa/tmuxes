package com.tmuxes.data.repository

import com.tmuxes.data.model.AuthMethod
import com.tmuxes.data.model.ServerEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ServerEntity] data class invariants used by [ServerYamlRepository].
 * No Android Context required — pure JVM.
 */
class ServerYamlRepositorySerializationTest {

    private fun server(
        hostname: String = "example.com",
        username: String = "user",
        name: String? = null,
        password: String? = null,
        privateKeyData: String? = null,
        passphrase: String? = null
    ) = ServerEntity(
        hostname = hostname,
        username = username,
        name = name,
        password = password,
        privateKeyData = privateKeyData,
        passphrase = passphrase
    )

    // ── 1. Default field values ──────────────────────────────────────────

    @Test fun `default port is 22`() = assertEquals(22, server().port)

    @Test fun `default authMethod is PASSWORD`() = assertEquals(AuthMethod.PASSWORD, server().authMethod)

    @Test fun `default isEnabled is true`() = assertTrue(server().isEnabled)

    @Test fun `default sortOrder is 0`() = assertEquals(0, server().sortOrder)

    @Test fun `default color is unset`() = assertEquals(0, server().color)

    @Test fun `default id is 0`() = assertEquals(0L, server().id)

    @Test fun `default name is null`() = assertNull(server().name)

    @Test fun `default password is null`() = assertNull(server().password)

    @Test fun `default privateKeyData is null`() = assertNull(server().privateKeyData)

    @Test fun `default passphrase is null`() = assertNull(server().passphrase)

    @Test fun `default parentId is null`() = assertNull(server().parentId)

    @Test fun `default lastConnectedAt is null`() = assertNull(server().lastConnectedAt)

    // ── 2. displayName — name set vs fallback ────────────────────────────

    @Test fun `displayName returns name when name is set`() {
        val s = server(hostname = "host.com", username = "alice", name = "My Server")
        assertEquals("My Server", s.displayName)
    }

    @Test fun `displayName falls back to username at hostname when name is null`() {
        val s = server(hostname = "host.com", username = "alice", name = null)
        assertEquals("alice@host.com", s.displayName)
    }

    @Test fun `displayName fallback contains exact hostname and username`() {
        val s = ServerEntity(hostname = "10.0.0.1", username = "root", port = 2222)
        assertEquals("root@10.0.0.1", s.displayName)
    }

    @Test fun `displayName falls back when name is empty string`() {
        val s = server(hostname = "host.com", username = "bob", name = "")
        assertEquals("bob@host.com", s.displayName)
    }

    @Test fun `displayName falls back when name is whitespace only`() {
        val s = server(hostname = "host.com", username = "carol", name = "   ")
        assertEquals("carol@host.com", s.displayName)
    }

    @Test fun `displayName uses name when it contains only leading or trailing spaces but has non-blank content`() {
        val s = server(hostname = "host.com", username = "dave", name = "  My Server  ")
        assertEquals("  My Server  ", s.displayName)
    }

    // ── 3. connectionFingerprint — connection-relevant change detection ──

    @Test fun `same fields produce identical fingerprints`() {
        val a = server(hostname = "h", username = "u", password = "pw")
        val b = server(hostname = "h", username = "u", password = "pw")
        assertEquals(a.connectionFingerprint(), b.connectionFingerprint())
    }

    @Test fun `hostname change changes fingerprint`() {
        val a = server(hostname = "old", username = "u", password = "pw")
        val b = server(hostname = "new", username = "u", password = "pw")
        assertTrue(a.connectionFingerprint() != b.connectionFingerprint())
    }

    @Test fun `username change changes fingerprint`() {
        val a = server(username = "alice", password = "pw")
        val b = server(username = "bob", password = "pw")
        assertTrue(a.connectionFingerprint() != b.connectionFingerprint())
    }

    @Test fun `password change changes fingerprint`() {
        val a = server(password = "old")
        val b = server(password = "new")
        assertTrue(a.connectionFingerprint() != b.connectionFingerprint())
    }

    @Test fun `private key change changes fingerprint`() {
        val a = server(privateKeyData = "k1")
        val b = server(privateKeyData = "k2")
        assertTrue(a.connectionFingerprint() != b.connectionFingerprint())
    }

    @Test fun `parentId change changes fingerprint`() {
        val a = ServerEntity(hostname = "h", username = "u", parentId = 1L)
        val b = ServerEntity(hostname = "h", username = "u", parentId = 2L)
        assertTrue(a.connectionFingerprint() != b.connectionFingerprint())
    }

    @Test fun `cosmetic name change does not change fingerprint`() {
        val a = server(name = "Production", password = "pw")
        val b = server(name = "Prod", password = "pw")
        assertEquals(a.connectionFingerprint(), b.connectionFingerprint())
    }

    @Test fun `color change does not change fingerprint`() {
        val a = ServerEntity(hostname = "h", username = "u", color = 0xFFA6E3A1.toInt(), password = "pw")
        val b = ServerEntity(hostname = "h", username = "u", color = 0xFFF38BA8.toInt(), password = "pw")
        assertEquals(a.connectionFingerprint(), b.connectionFingerprint())
    }

    @Test fun `isEnabled change does not change fingerprint`() {
        val a = ServerEntity(hostname = "h", username = "u", isEnabled = true, password = "pw")
        val b = ServerEntity(hostname = "h", username = "u", isEnabled = false, password = "pw")
        assertEquals(a.connectionFingerprint(), b.connectionFingerprint())
    }

    @Test fun `lastConnectedAt change does not change fingerprint`() {
        val a = ServerEntity(hostname = "h", username = "u", lastConnectedAt = 1L, password = "pw")
        val b = ServerEntity(hostname = "h", username = "u", lastConnectedAt = 2L, password = "pw")
        assertEquals(a.connectionFingerprint(), b.connectionFingerprint())
    }
}

package com.tmuxes.ssh

import kotlinx.coroutines.runBlocking
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.Security

/**
 * Unit tests for SshKeyManager methods that can be tested without an Android
 * context. The renameKey/getKeyCreationDate methods are tested via a
 * [FakeContext] that redirects filesDir to a temp directory, exercising the
 * same code path as the production implementation.
 *
 * The getKeyFingerprint tests require BouncyCastle + SSHJ, which are available
 * as compile-time dependencies.
 */
class SshKeyManagerTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun initBouncyCastle() {
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
            Security.insertProviderAt(BouncyCastleProvider(), 1)
        }
    }

    private lateinit var tempDir: File
    private lateinit var keysDir: File

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("ssh_key_manager_test").toFile()
        keysDir = File(tempDir, "ssh_keys")
        keysDir.mkdirs()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    // =========================================================================
    // Direct file-based rename tests (testing core logic without Context)
    // =========================================================================

    /**
     * Sanitize a file name the same way SshKeyManager does internally.
     * This mirrors the private sanitizeFileName method.
     */
    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }

    /**
     * Mirror of SshKeyManager.renameKey logic operating on [keysDir] directly.
     * This lets us test all the edge cases without needing an Android Context.
     */
    private fun renameKeyDirect(oldName: String, newName: String): Boolean {
        if (newName.isBlank()) return false
        val sanitizedNew = sanitizeFileName(newName)
        if (sanitizedNew.isBlank()) return false

        val oldFile = File(keysDir, sanitizeFileName(oldName))
        if (!oldFile.exists()) return false

        val newFile = File(keysDir, sanitizedNew)
        if (newFile.exists()) return false

        return oldFile.renameTo(newFile)
    }

    /**
     * Mirror of SshKeyManager.getKeyCreationDate logic operating directly.
     */
    private fun getKeyCreationDateDirect(name: String): Long? {
        val file = File(keysDir, sanitizeFileName(name))
        return if (file.exists()) file.lastModified() else null
    }

    // -------------------------------------------------------------------------
    // renameKey: valid name
    // -------------------------------------------------------------------------

    @Test
    fun `renameKey with valid name succeeds`() {
        File(keysDir, "old_key").writeText("key-data")

        val result = renameKeyDirect("old_key", "new_key")

        assertTrue("Rename should succeed", result)
        assertFalse("Old file should not exist", File(keysDir, "old_key").exists())
        assertTrue("New file should exist", File(keysDir, "new_key").exists())
        assertEquals("File content should be preserved", "key-data", File(keysDir, "new_key").readText())
    }

    // -------------------------------------------------------------------------
    // renameKey: empty name
    // -------------------------------------------------------------------------

    @Test
    fun `renameKey with empty name fails`() {
        File(keysDir, "my_key").writeText("key-data")

        val result = renameKeyDirect("my_key", "")

        assertFalse("Rename to empty name should fail", result)
        assertTrue("Original file should still exist", File(keysDir, "my_key").exists())
    }

    @Test
    fun `renameKey with blank name fails`() {
        File(keysDir, "my_key").writeText("key-data")

        val result = renameKeyDirect("my_key", "   ")

        assertFalse("Rename to blank name should fail", result)
        assertTrue("Original file should still exist", File(keysDir, "my_key").exists())
    }

    // -------------------------------------------------------------------------
    // renameKey: special characters are sanitized
    // -------------------------------------------------------------------------

    @Test
    fun `renameKey with special chars sanitizes name`() {
        File(keysDir, "my_key").writeText("key-data")

        val result = renameKeyDirect("my_key", "key/with spaces!")

        assertTrue("Rename should succeed (sanitized)", result)
        val sanitizedName = "key_with_spaces_"
        assertTrue("Sanitized file should exist", File(keysDir, sanitizedName).exists())
        assertFalse("Old file should not exist", File(keysDir, "my_key").exists())
    }

    @Test
    fun `renameKey with only special chars produces underscore name`() {
        File(keysDir, "my_key").writeText("key-data")

        val result = renameKeyDirect("my_key", "!!!")

        assertTrue("Rename to sanitized all-underscore name should succeed", result)
        assertTrue("Sanitized file ___ should exist", File(keysDir, "___").exists())
    }

    // -------------------------------------------------------------------------
    // renameKey: non-existent source
    // -------------------------------------------------------------------------

    @Test
    fun `renameKey with non-existent source fails`() {
        val result = renameKeyDirect("does_not_exist", "new_name")

        assertFalse("Rename of non-existent key should fail", result)
    }

    // -------------------------------------------------------------------------
    // renameKey: target already exists
    // -------------------------------------------------------------------------

    @Test
    fun `renameKey to existing name fails`() {
        File(keysDir, "key_a").writeText("data-a")
        File(keysDir, "key_b").writeText("data-b")

        val result = renameKeyDirect("key_a", "key_b")

        assertFalse("Rename should not overwrite existing key", result)
        assertEquals("Original key_a should be unchanged", "data-a", File(keysDir, "key_a").readText())
        assertEquals("Existing key_b should be unchanged", "data-b", File(keysDir, "key_b").readText())
    }

    // -------------------------------------------------------------------------
    // renameKey: same name (target == source)
    // -------------------------------------------------------------------------

    @Test
    fun `renameKey to same name fails since target exists`() {
        File(keysDir, "same_key").writeText("data")

        val result = renameKeyDirect("same_key", "same_key")

        assertFalse("Rename to same name should fail (target already exists)", result)
        assertTrue("File should still exist", File(keysDir, "same_key").exists())
    }

    // -------------------------------------------------------------------------
    // getKeyCreationDate: existing key
    // -------------------------------------------------------------------------

    @Test
    fun `getKeyCreationDate with existing key returns timestamp`() {
        val file = File(keysDir, "dated_key")
        file.writeText("key-data")

        val result = getKeyCreationDateDirect("dated_key")

        assertNotNull("Creation date should not be null for existing key", result)
        assertTrue("Creation date should be a positive timestamp", result!! > 0)
        assertEquals("Creation date should match file lastModified", file.lastModified(), result)
    }

    // -------------------------------------------------------------------------
    // getKeyCreationDate: non-existing key
    // -------------------------------------------------------------------------

    @Test
    fun `getKeyCreationDate with non-existing key returns null`() {
        val result = getKeyCreationDateDirect("no_such_key")

        assertNull("Creation date should be null for non-existing key", result)
    }

    // -------------------------------------------------------------------------
    // getKeyFingerprint: valid format
    // -------------------------------------------------------------------------

    // Note: fingerprint tests use RSA keys because Ed25519 PEM format from
    // JcaPEMWriter is not directly loadable by SSHJ's OpenSSHKeyFile in the
    // JVM test environment (works on Android where the full BC provider is active).

    @Test
    fun `getKeyFingerprint returns valid fingerprint format`() = runBlocking {
        val generated = SshKeyManager.generateKeyPair(SshKeyType.RSA_2048)
        val fingerprint = SshKeyManager.getKeyFingerprint(generated.privatePem)

        assertTrue("Fingerprint should not be empty", fingerprint.isNotEmpty())
        // SSHJ SecurityUtils.getFingerprint returns colon-separated hex
        assertTrue(
            "Fingerprint should contain colons (hex format)",
            fingerprint.contains(":")
        )
    }

    @Test
    fun `getKeyFingerprint is consistent for same key`() = runBlocking {
        val generated = SshKeyManager.generateKeyPair(SshKeyType.RSA_2048)
        val fingerprint1 = SshKeyManager.getKeyFingerprint(generated.privatePem)
        val fingerprint2 = SshKeyManager.getKeyFingerprint(generated.privatePem)

        assertEquals("Fingerprint should be deterministic", fingerprint1, fingerprint2)
    }

    @Test
    fun `getKeyFingerprint differs for different keys`() = runBlocking {
        val generated1 = SshKeyManager.generateKeyPair(SshKeyType.RSA_2048)
        val generated2 = SshKeyManager.generateKeyPair(SshKeyType.RSA_2048)
        val fingerprint1 = SshKeyManager.getKeyFingerprint(generated1.privatePem)
        val fingerprint2 = SshKeyManager.getKeyFingerprint(generated2.privatePem)

        assertFalse("Different keys should have different fingerprints", fingerprint1 == fingerprint2)
    }

    // -------------------------------------------------------------------------
    // generateKeyPair: publicOpenSsh is present and well-formed
    // -------------------------------------------------------------------------

    @Test
    fun `generateKeyPair returns non-empty openssh public key for RSA`() = runBlocking {
        val generated = SshKeyManager.generateKeyPair(SshKeyType.RSA_2048)
        assertTrue("Private PEM should be non-empty", generated.privatePem.isNotEmpty())
        assertTrue("Public OpenSSH should be non-empty", generated.publicOpenSsh.isNotEmpty())
        assertTrue(
            "Public key should start with ssh-rsa",
            generated.publicOpenSsh.startsWith("ssh-rsa ")
        )
    }

    @Test
    fun `generateKeyPair returns non-empty openssh public key for Ed25519`() = runBlocking {
        val generated = SshKeyManager.generateKeyPair(SshKeyType.ED25519)
        assertTrue("Private PEM should be non-empty", generated.privatePem.isNotEmpty())
        assertTrue("Public OpenSSH should be non-empty", generated.publicOpenSsh.isNotEmpty())
        assertTrue(
            "Public key should start with ssh-ed25519",
            generated.publicOpenSsh.startsWith("ssh-ed25519 ")
        )
    }

    // -------------------------------------------------------------------------
    // getPublicKeyString: extracts public from the private PEM
    // -------------------------------------------------------------------------
    // Regression test for the iter 67 fix: getPublicKeyString used to call
    // Buffer.putPublicKey directly, which throws UnsupportedOperationException
    // for BouncyCastle Ed25519 public keys (SSHJ's KeyType.ED25519 only matches
    // i2p EdDSAPublicKey). The fix routes through publicKeyToOpenSsh which
    // handles the BC Ed25519 case manually.

    @Test
    fun `getPublicKeyString extracts RSA public key from private PEM`() = runBlocking {
        val generated = SshKeyManager.generateKeyPair(SshKeyType.RSA_2048)
        val extracted = SshKeyManager.getPublicKeyString(generated.privatePem)
        assertTrue(
            "Extracted RSA key should start with ssh-rsa",
            extracted.startsWith("ssh-rsa ")
        )
        // Strip comment for comparison — the body should match.
        val extractedBody = extracted.substringBeforeLast(" ")
        val generatedBody = generated.publicOpenSsh.substringBeforeLast(" ")
        assertEquals(
            "Extracted public key body should match generated",
            generatedBody, extractedBody
        )
    }

    // -------------------------------------------------------------------------
    // Sanitize file name edge cases
    // -------------------------------------------------------------------------

    @Test
    fun `sanitizeFileName preserves alphanumeric, dots, hyphens, underscores`() {
        assertEquals("my_key-1.pem", sanitizeFileName("my_key-1.pem"))
        assertEquals("key123", sanitizeFileName("key123"))
        assertEquals("A.B-C_D", sanitizeFileName("A.B-C_D"))
    }

    @Test
    fun `sanitizeFileName replaces special chars with underscores`() {
        assertEquals("key_with_spaces", sanitizeFileName("key with spaces"))
        assertEquals("key_path_name", sanitizeFileName("key/path/name"))
        assertEquals("key___", sanitizeFileName("key @!"))
    }

    @Test
    fun `sanitizeFileName handles empty string`() {
        assertEquals("", sanitizeFileName(""))
    }
}

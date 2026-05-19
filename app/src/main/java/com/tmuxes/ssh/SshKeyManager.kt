package com.tmuxes.ssh

import android.content.Context
import com.tmuxes.util.AppLogger
import com.tmuxes.util.AppLogger.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.common.SecurityUtils
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.StringWriter
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/**
 * Supported SSH key types and their parameters.
 */
enum class SshKeyType(val algorithm: String, val keySizeBits: Int, val displayName: String) {
    ED25519("Ed25519", 256, "Ed25519"),
    ECDSA_256("EC", 256, "ECDSA 256"),
    ECDSA_384("EC", 384, "ECDSA 384"),
    RSA_2048("RSA", 2048, "RSA 2048"),
    RSA_4096("RSA", 4096, "RSA 4096")
}

/**
 * Manages SSH key generation, import/export, and persistent storage.
 *
 * Keys are stored in the app's internal storage under the "ssh_keys" directory,
 * with each key in PEM format.
 */
object SshKeyManager {

    private const val KEYS_DIR = "ssh_keys"
    private const val PUB_EXT = ".pub"

    // Bouncy Castle is registered globally in TmuxesApp.companion.init
    // at the highest priority, replacing Android's stripped-down BC.
    // No per-class registration is needed.

    // -------------------------------------------------------------------------
    // Key generation
    // -------------------------------------------------------------------------

    /**
     * Result of generating a new SSH key pair. Holds the private key PEM text
     * plus the derived OpenSSH public-key line, so callers can save both and
     * avoid later round-tripping through the PKCS#8 parser (which can't
     * recover the public component of a standalone Ed25519 `PRIVATE KEY`
     * block written by [JcaPEMWriter]).
     */
    data class GeneratedKey(val privatePem: String, val publicOpenSsh: String)

    /**
     * Generate a new SSH key pair of the given [type].
     *
     * @return The generated key pair as [GeneratedKey].
     */
    suspend fun generateKeyPair(type: SshKeyType): GeneratedKey = withContext(Dispatchers.IO) {
        AppLogger.timed(Category.KEY, "key.generate type=${type.displayName}") {
            val keyPair: KeyPair = when (type) {
                SshKeyType.ED25519 -> {
                    val kpg = KeyPairGenerator.getInstance("Ed25519", "BC")
                    kpg.generateKeyPair()
                }
                SshKeyType.ECDSA_256 -> {
                    val kpg = KeyPairGenerator.getInstance("EC", "BC")
                    kpg.initialize(ECGenParameterSpec("secp256r1"), SecureRandom())
                    kpg.generateKeyPair()
                }
                SshKeyType.ECDSA_384 -> {
                    val kpg = KeyPairGenerator.getInstance("EC", "BC")
                    kpg.initialize(ECGenParameterSpec("secp384r1"), SecureRandom())
                    kpg.generateKeyPair()
                }
                SshKeyType.RSA_2048 -> {
                    val kpg = KeyPairGenerator.getInstance("RSA", "BC")
                    kpg.initialize(2048, SecureRandom())
                    kpg.generateKeyPair()
                }
                SshKeyType.RSA_4096 -> {
                    val kpg = KeyPairGenerator.getInstance("RSA", "BC")
                    kpg.initialize(4096, SecureRandom())
                    kpg.generateKeyPair()
                }
            }

            try {
                GeneratedKey(
                    privatePem = keyPairToPem(keyPair),
                    publicOpenSsh = publicKeyToOpenSsh(keyPair.public)
                )
            } catch (e: Exception) {
                AppLogger.e(Category.KEY, e) { "key.generate ✗ type=${type.displayName}" }
                throw SshException("Failed to generate ${type.displayName} key pair: ${e.message}", e)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Key import
    // -------------------------------------------------------------------------

    /**
     * Import a private key from PEM or OpenSSH-format string.
     *
     * Validates that the key can be parsed by SSHJ. Returns the key data as-is
     * if valid (it is already in a usable format).
     *
     * @param data The raw private key text.
     * @param passphrase Optional passphrase for encrypted keys.
     * @return The validated private key data.
     */
    suspend fun importKey(data: String, passphrase: String? = null): String = withContext(Dispatchers.IO) {
        AppLogger.d(Category.KEY) { "key.import bytes=${data.length} hasPass=${passphrase != null}" }
        try {
            // Validate by trying to load the key with SSHJ
            val keyFile = SshKeyLoader.loadKeyFile(data, passphrase)
            // Force load to validate
            keyFile.private
            AppLogger.i(Category.KEY) { "key.import ← ok" }
            data
        } catch (e: Exception) {
            AppLogger.w(Category.KEY) { "key.import ✗ cause='${e.message}'" }
            throw SshException("Failed to import key: ${e.message}", e)
        }
    }

    // -------------------------------------------------------------------------
    // Public key extraction
    // -------------------------------------------------------------------------

    /**
     * Extract the public key from a private key in `authorized_keys` format
     * (e.g., "ssh-ed25519 AAAA... comment").
     *
     * @param privateKeyData PEM-encoded private key.
     * @param passphrase Optional passphrase.
     * @param comment Optional comment appended to the key line.
     * @return The public key string suitable for `~/.ssh/authorized_keys`.
     */
    suspend fun getPublicKeyString(
        privateKeyData: String,
        passphrase: String? = null,
        comment: String = "tmuxes"
    ): String = withContext(Dispatchers.IO) {
        try {
            val keyFile = SshKeyLoader.loadKeyFile(privateKeyData, passphrase)
            // Route through publicKeyToOpenSsh so Ed25519 keys loaded via
            // SSHJ's PKCS8KeyFile (which produces a BouncyCastle
            // BCEdDSAPublicKey, not SSHJ's i2p EdDSAPublicKey) are
            // correctly serialized. Using Buffer.putPublicKey directly
            // would throw UnsupportedOperationException for BC Ed25519
            // keys — see the comment on publicKeyToOpenSsh.
            publicKeyToOpenSsh(keyFile.public, comment)
        } catch (e: Exception) {
            throw SshException("Failed to extract public key: ${e.message}", e)
        }
    }

    // -------------------------------------------------------------------------
    // Persistent storage
    // -------------------------------------------------------------------------

    /**
     * Save a private key to app-internal storage.
     *
     * @param name Logical name for the key (used as filename).
     * @param privateKeyData PEM-encoded private key.
     * @param context Android context for file access.
     */
    suspend fun saveKey(name: String, privateKeyData: String, context: Context) =
        withContext(Dispatchers.IO) {
            try {
                val dir = getKeysDir(context)
                val file = File(dir, sanitizeFileName(name))
                file.writeText(privateKeyData)
                AppLogger.i(Category.KEY) { "key.save name='$name' bytes=${privateKeyData.length}" }
            } catch (e: Exception) {
                AppLogger.e(Category.KEY, e) { "key.save ✗ name='$name'" }
                throw SshException("Failed to save key '$name': ${e.message}", e)
            }
        }

    /**
     * Save a freshly generated key pair: writes the private PEM to
     * `<name>` and the OpenSSH public key line to `<name>.pub`.
     *
     * Storing the public key alongside the private key eliminates the need
     * to later re-parse the private PEM to recover the public component —
     * a path that fails for Ed25519 PKCS#8 because [JcaPEMWriter] drops the
     * public component from `BEGIN PRIVATE KEY` blocks.
     */
    suspend fun saveGeneratedKey(name: String, generated: GeneratedKey, context: Context) =
        withContext(Dispatchers.IO) {
            try {
                val dir = getKeysDir(context)
                val sanitized = sanitizeFileName(name)
                File(dir, sanitized).writeText(generated.privatePem)
                File(dir, sanitized + PUB_EXT).writeText(generated.publicOpenSsh)
                AppLogger.i(Category.KEY) { "key.save name='$name' (private + .pub sidecar)" }
            } catch (e: Exception) {
                AppLogger.e(Category.KEY, e) { "key.saveGenerated ✗ name='$name'" }
                throw SshException("Failed to save key '$name': ${e.message}", e)
            }
        }

    /**
     * Return the OpenSSH public-key line for a stored key, preferring a
     * `<name>.pub` sidecar if present. Falls back to extracting from the
     * private PEM via SSHJ if no sidecar is found (supports imported keys
     * that have no sidecar file).
     */
    suspend fun getStoredPublicKey(
        name: String,
        context: Context,
        passphrase: String? = null,
        comment: String = "tmuxes"
    ): String = withContext(Dispatchers.IO) {
        val dir = getKeysDir(context)
        val sanitized = sanitizeFileName(name)
        val pubFile = File(dir, sanitized + PUB_EXT)
        if (pubFile.exists()) {
            return@withContext pubFile.readText().trim()
        }
        val privFile = File(dir, sanitized)
        if (!privFile.exists()) throw SshException("Key '$name' not found")
        getPublicKeyString(privFile.readText(), passphrase, comment)
    }

    /**
     * Load a private key from app-internal storage.
     *
     * @param name Logical name of the key.
     * @param context Android context for file access.
     * @return The PEM-encoded private key data.
     */
    suspend fun loadKey(name: String, context: Context): String = withContext(Dispatchers.IO) {
        try {
            val dir = getKeysDir(context)
            val file = File(dir, sanitizeFileName(name))
            if (!file.exists()) {
                AppLogger.w(Category.KEY) { "key.load ✗ name='$name' (file missing)" }
                throw SshException("Key '$name' not found")
            }
            AppLogger.d(Category.KEY) { "key.load name='$name' bytes=${file.length()}" }
            file.readText()
        } catch (e: SshException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(Category.KEY, e) { "key.load ✗ name='$name'" }
            throw SshException("Failed to load key '$name': ${e.message}", e)
        }
    }

    /**
     * List names of all stored keys. Excludes `.pub` sidecar files — they
     * are a storage implementation detail of [saveGeneratedKey] / the
     * `getStoredPublicKey` fast path and must not show up as separate
     * key entries in the UI list.
     */
    suspend fun listKeys(context: Context): List<String> = withContext(Dispatchers.IO) {
        val dir = getKeysDir(context)
        dir.listFiles()
            ?.filter { it.isFile && !it.name.endsWith(PUB_EXT) }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()
    }

    /**
     * Delete a stored key. Also removes the `.pub` sidecar if present.
     */
    suspend fun deleteKey(name: String, context: Context) = withContext(Dispatchers.IO) {
        val dir = getKeysDir(context)
        val sanitized = sanitizeFileName(name)
        val file = File(dir, sanitized)
        val pubFile = File(dir, sanitized + PUB_EXT)
        val hadPub = pubFile.exists()
        val hadPriv = file.exists()
        if (hadPub) pubFile.delete()
        if (hadPriv) {
            if (!file.delete()) {
                AppLogger.w(Category.KEY) { "key.delete ✗ name='$name' (delete returned false)" }
                throw SshException("Failed to delete key '$name'")
            }
        }
        AppLogger.i(Category.KEY) { "key.delete name='$name' priv=$hadPriv pub=$hadPub" }
    }

    // -------------------------------------------------------------------------
    // Key fingerprint
    // -------------------------------------------------------------------------

    /**
     * Compute the SHA-256 fingerprint of a private key's corresponding public key.
     *
     * @param privateKeyData PEM-encoded private key.
     * @param passphrase Optional passphrase.
     * @return Fingerprint string (e.g. "SHA256:...") as produced by SSHJ's SecurityUtils.
     */
    suspend fun getKeyFingerprint(
        privateKeyData: String,
        passphrase: String? = null
    ): String = withContext(Dispatchers.IO) {
        try {
            val keyFile = SshKeyLoader.loadKeyFile(privateKeyData, passphrase)
            val publicKey = keyFile.public
            val fp = SecurityUtils.getFingerprint(publicKey)
            AppLogger.d(Category.KEY) { "key.fingerprint fp=$fp" }
            fp
        } catch (e: Exception) {
            AppLogger.w(Category.KEY) { "key.fingerprint ✗ cause='${e.message}'" }
            throw SshException("Failed to compute key fingerprint: ${e.message}", e)
        }
    }

    // -------------------------------------------------------------------------
    // Key rename
    // -------------------------------------------------------------------------

    /**
     * Rename a stored key file.
     *
     * @param oldName Current logical name of the key.
     * @param newName Desired new logical name.
     * @param context Android context for file access.
     * @return `true` if rename succeeded, `false` otherwise.
     */
    suspend fun renameKey(oldName: String, newName: String, context: Context): Boolean =
        withContext(Dispatchers.IO) {
            if (newName.isBlank()) return@withContext false
            val sanitizedNew = sanitizeFileName(newName)
            if (sanitizedNew.isBlank()) return@withContext false

            val dir = getKeysDir(context)
            val sanitizedOld = sanitizeFileName(oldName)
            val oldFile = File(dir, sanitizedOld)
            if (!oldFile.exists()) return@withContext false

            val newFile = File(dir, sanitizedNew)
            if (newFile.exists()) return@withContext false // don't overwrite

            val renamed = oldFile.renameTo(newFile)
            if (renamed) {
                // Best-effort rename of the sidecar. Failure is non-fatal —
                // a stale sidecar just means getStoredPublicKey falls back
                // to re-parsing the private key for the new name.
                val oldPub = File(dir, sanitizedOld + PUB_EXT)
                if (oldPub.exists()) {
                    try { oldPub.renameTo(File(dir, sanitizedNew + PUB_EXT)) } catch (e: Exception) {
                        AppLogger.w(Category.KEY) {
                            "key.rename sidecar ✗ '$sanitizedOld.pub' → '$sanitizedNew.pub' cause='${e.message}' — orphan sidecar; will re-derive from private key"
                        }
                    }
                }
            }
            AppLogger.i(Category.KEY) { "key.rename '$oldName' → '$newName' renamed=$renamed" }
            renamed
        }

    // -------------------------------------------------------------------------
    // Key creation date
    // -------------------------------------------------------------------------

    /**
     * Return the filesystem last-modified timestamp for a stored key.
     *
     * @return Epoch millis, or `null` if the key does not exist.
     */
    suspend fun getKeyCreationDate(name: String, context: Context): Long? =
        withContext(Dispatchers.IO) {
            val dir = getKeysDir(context)
            val file = File(dir, sanitizeFileName(name))
            if (file.exists()) file.lastModified() else null
        }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private fun getKeysDir(context: Context): File {
        val dir = File(context.filesDir, KEYS_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }

    private fun keyPairToPem(keyPair: KeyPair): String {
        val writer = StringWriter()
        JcaPEMWriter(writer).use { pemWriter ->
            pemWriter.writeObject(keyPair.private)
        }
        return writer.toString()
    }

    /**
     * Render a `java.security.PublicKey` into the SSH `authorized_keys` line
     * format (e.g. `"ssh-ed25519 AAAA... tmuxes"`).
     *
     * RSA and ECDSA keys are serialized via SSHJ's [Buffer.putPublicKey]
     * which already supports all BouncyCastle key types on those algorithms.
     *
     * **Ed25519 quirk**: SSHJ 0.39.0's `KeyType.ED25519` casts to
     * `net.i2p.crypto.eddsa.EdDSAPublicKey` (algorithm name `"EdDSA"`) in
     * `writePubKeyContentsIntoBuffer`, and its `isMyType` only returns true
     * for keys whose `getAlgorithm()` equals `"EdDSA"`. BouncyCastle's
     * `BCEdDSAPublicKey` reports `"Ed25519"` and is a completely different
     * class, so SSHJ dispatches to `KeyType.UNKNOWN`, which throws
     * `UnsupportedOperationException` on serialization.
     *
     * The i2p eddsa library is packaged inside the SSHJ jar as a runtime-only
     * dep, so we can't reference `EdDSAPublicKey` at compile time. Instead
     * we write the SSH wire format for Ed25519 by hand — it is just:
     *
     *     string "ssh-ed25519"    (uint32 length 11 + 11 bytes)
     *     string <32 raw bytes>   (uint32 length 32 + 32 bytes)
     *
     * The 32 raw public key bytes are the BIT STRING inside BC's
     * `SubjectPublicKeyInfo` X.509 encoding.
     */
    private fun publicKeyToOpenSsh(publicKey: PublicKey, comment: String = "tmuxes"): String {
        val algo = publicKey.algorithm
        return if (algo == "Ed25519" || algo == "EdDSA") {
            val info = SubjectPublicKeyInfo.getInstance(publicKey.encoded)
            val rawBytes = info.publicKeyData.bytes
            val wire = encodeEd25519SshWire(rawBytes)
            val b64 = Base64.getEncoder().encodeToString(wire)
            "ssh-ed25519 $b64 $comment"
        } else {
            val keyType = KeyType.fromKey(publicKey)
            val buf = Buffer.PlainBuffer()
            buf.putPublicKey(publicKey)
            val b64 = Base64.getEncoder().encodeToString(buf.compactData)
            "$keyType $b64 $comment"
        }
    }

    /**
     * Build the SSH wire format for an Ed25519 public key from the raw
     * 32-byte public key material. Output layout:
     *
     *     uint32 len("ssh-ed25519") || "ssh-ed25519"
     *     uint32 len(rawBytes)      || rawBytes
     */
    private fun encodeEd25519SshWire(rawBytes: ByteArray): ByteArray {
        val typeName = "ssh-ed25519".toByteArray(Charsets.US_ASCII)
        val out = ByteArrayOutputStream(4 + typeName.size + 4 + rawBytes.size)
        fun writeUint32(value: Int) {
            out.write((value ushr 24) and 0xff)
            out.write((value ushr 16) and 0xff)
            out.write((value ushr 8) and 0xff)
            out.write(value and 0xff)
        }
        writeUint32(typeName.size)
        out.write(typeName)
        writeUint32(rawBytes.size)
        out.write(rawBytes)
        return out.toByteArray()
    }
}

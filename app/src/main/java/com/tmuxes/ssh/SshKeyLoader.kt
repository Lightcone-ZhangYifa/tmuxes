package com.tmuxes.ssh

import com.hierynomus.sshj.userauth.keyprovider.OpenSSHKeyV1KeyFile
import com.tmuxes.util.AppLogger
import com.tmuxes.util.AppLogger.Category
import net.schmizz.sshj.userauth.keyprovider.FileKeyProvider
import net.schmizz.sshj.userauth.keyprovider.KeyFormat
import net.schmizz.sshj.userauth.keyprovider.KeyProviderUtil
import net.schmizz.sshj.userauth.keyprovider.OpenSSHKeyFile
import net.schmizz.sshj.userauth.keyprovider.PKCS8KeyFile
import net.schmizz.sshj.userauth.keyprovider.PuTTYKeyFile
import net.schmizz.sshj.userauth.password.PasswordUtils
import java.io.StringReader

/**
 * Shared utility for loading SSH private keys via SSHJ.
 *
 * **CRITICAL**: Different SSH key formats require different parser classes:
 *
 * - `-----BEGIN OPENSSH PRIVATE KEY-----` → modern OpenSSH format
 *   (default since OpenSSH 7.8, 2018) → [OpenSSHKeyV1KeyFile]
 * - `-----BEGIN RSA PRIVATE KEY-----`, `-----BEGIN EC PRIVATE KEY-----`,
 *   `-----BEGIN DSA PRIVATE KEY-----` → traditional PEM (PKCS#1 / SEC1)
 *   → [PKCS5KeyFile] or [OpenSSHKeyFile]
 * - `-----BEGIN PRIVATE KEY-----`, `-----BEGIN ENCRYPTED PRIVATE KEY-----`
 *   → PKCS#8 → [PKCS8KeyFile]
 * - `PuTTY-User-Key-File-2:` → PuTTY → [PuTTYKeyFile]
 *
 * Routing by detected format is required because `OpenSSHKeyFile` handles
 * traditional PEM, while modern (`OPENSSH PRIVATE KEY` header) keys use
 * [OpenSSHKeyV1KeyFile].
 */
object SshKeyLoader {
    /**
     * Load a private key from PEM-encoded data, optionally with a passphrase.
     *
     * Auto-detects the key format using SSHJ's [KeyProviderUtil] and
     * dispatches to the correct parser. Returns a fully initialised
     * [FileKeyProvider] ready to authenticate.
     */
    fun loadKeyFile(privateKeyData: String, passphrase: String? = null): FileKeyProvider {
        // Detect format by inspecting the key header. KeyProviderUtil reads
        // the first line of the key data — we duplicate the StringReader
        // into a String first because reading detects-then-parses.
        val format: KeyFormat = try {
            KeyProviderUtil.detectKeyFileFormat(privateKeyData, false)
        } catch (t: Throwable) {
            AppLogger.w(Category.KEY) { "key.detect ✗ cause='${t.message}' → Unknown" }
            KeyFormat.Unknown
        }

        val keyFile: FileKeyProvider = when (format) {
            KeyFormat.OpenSSHv1 -> OpenSSHKeyV1KeyFile()
            KeyFormat.OpenSSH -> OpenSSHKeyFile()
            KeyFormat.PKCS8 -> PKCS8KeyFile()
            KeyFormat.PuTTY -> PuTTYKeyFile()
            // Unknown format: default to OpenSSHKeyV1KeyFile since modern
            // OpenSSH keys are by far the most common (default since OpenSSH
            // 7.8 in 2018). Falls back internally to PEM via BouncyCastle.
            else -> OpenSSHKeyV1KeyFile()
        }
        AppLogger.d(Category.KEY) { "key.loadFile format=$format parser=${keyFile::class.simpleName}" }

        val passwordFinder = passphrase?.let {
            PasswordUtils.createOneOff(it.toCharArray())
        }
        keyFile.init(
            StringReader(privateKeyData),
            null as java.io.Reader?,
            passwordFinder
        )
        return keyFile
    }
}

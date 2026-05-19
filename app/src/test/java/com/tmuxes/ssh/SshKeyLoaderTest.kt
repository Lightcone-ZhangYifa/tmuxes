package com.tmuxes.ssh

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert.assertNotNull
import org.junit.BeforeClass
import org.junit.Test
import java.security.Security

/**
 * Unit tests for [SshKeyLoader] format detection.
 *
 * Critical regression tests for key-format detection. Traditional PEM and
 * modern OpenSSH keys require different SSHJ parser classes; default
 * `ssh-keygen` Ed25519 keys use `OpenSSHKeyV1KeyFile`.
 *
 * These tests use embedded sample keys generated with `ssh-keygen` so the
 * format detection path is exercised independently of any test server.
 */
class SshKeyLoaderTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun initBouncyCastle() {
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
            Security.insertProviderAt(BouncyCastleProvider(), 1)
        }
    }

    /**
     * Modern OpenSSH format (default `ssh-keygen -t ed25519` output since
     * OpenSSH 7.8 in 2018-08-24).
     */
    private val modernEd25519Key = """
        -----BEGIN OPENSSH PRIVATE KEY-----
        b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAAMwAAAAtzc2gtZW
        QyNTUxOQAAACBivodmKz7AnyLjW3/SZZ2gPKE/6e0bkrD4LhfZim+21AAAAJBQfaz3UH2s
        9wAAAAtzc2gtZWQyNTUxOQAAACBivodmKz7AnyLjW3/SZZ2gPKE/6e0bkrD4LhfZim+21A
        AAAECxUWAk0xWVlurFVnEjtMQVk+SFIAlRl2OwssFDaZv3+2K+h2YrPsCfIuNbf9JlnaA8
        oT/p7RuSsPguF9mKb7bUAAAAC3RtdXhlcy10ZXN0AQI=
        -----END OPENSSH PRIVATE KEY-----
    """.trimIndent()

    @Test
    fun `loadKeyFile handles modern OpenSSH format -----BEGIN OPENSSH PRIVATE KEY-----`() {
        // This path must not throw:
        //   java.io.IOException: unrecognised object: OPENSSH PRIVATE KEY
        val keyFile = SshKeyLoader.loadKeyFile(modernEd25519Key, passphrase = null)
        assertNotNull("loadKeyFile must return a FileKeyProvider for modern OpenSSH", keyFile)
        // Force load the key to verify it's parseable
        assertNotNull("public key must be extractable from modern OpenSSH format", keyFile.public)
        assertNotNull("private key must be extractable from modern OpenSSH format", keyFile.private)
    }

    @Test
    fun `loadKeyFile result is usable for SSH client authPublickey`() {
        // The loader's return type was widened from OpenSSHKeyFile to
        // FileKeyProvider in iter 29. SSHClient.authPublickey accepts
        // any KeyProvider, so this must remain assignable to KeyProvider.
        val keyFile = SshKeyLoader.loadKeyFile(modernEd25519Key, passphrase = null)
        // Compile-time + runtime check that it implements KeyProvider
        val asProvider: net.schmizz.sshj.userauth.keyprovider.KeyProvider = keyFile
        assertNotNull(asProvider)
    }
}

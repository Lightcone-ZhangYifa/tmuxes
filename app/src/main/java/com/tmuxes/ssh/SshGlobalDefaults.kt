package com.tmuxes.ssh

import com.tmuxes.data.preferences.AppPreferences
import com.tmuxes.data.settings.Settings

/**
 * Snapshot of SSH default settings sourced from [com.tmuxes.data.settings.Settings].
 *
 * Used by [SshConfigBuilder] to merge per-server overrides with global defaults.
 * Defaults are taken from the registry — adding or changing an SSH default
 * requires editing exactly one declaration in [Settings].
 */
data class SshGlobalDefaults(
    val defaultTerm: String = Settings.sshDefaultTerm.default,
    val connectionTimeout: Int = Settings.sshConnectionTimeout.default,
    val transportTimeout: Int = Settings.sshTransportTimeout.default,
    val readTimeout: Int = Settings.sshReadTimeout.default,
    val keepaliveInterval: Int = Settings.sshKeepaliveInterval.default,
    val keepaliveMaxCount: Int = Settings.sshKeepaliveMaxCount.default,
    val compression: Boolean = Settings.sshCompression.default,
    val strictHostKey: String = Settings.sshStrictHostKey.default,
    val envVars: String = Settings.sshEnvVars.default,
    val preferredCiphers: String = Settings.sshPreferredCiphers.default,
    val preferredKex: String = Settings.sshPreferredKex.default,
    val preferredMacs: String = Settings.sshPreferredMacs.default,
    val preferredHostKeyAlgs: String = Settings.sshPreferredHostKeyAlgs.default
) {
    companion object {
        /** Snapshot the current SSH defaults from preferences, falling back to registered defaults. */
        fun from(prefs: AppPreferences) = SshGlobalDefaults(
            defaultTerm = prefs.get(Settings.sshDefaultTerm),
            connectionTimeout = prefs.get(Settings.sshConnectionTimeout),
            transportTimeout = prefs.get(Settings.sshTransportTimeout),
            readTimeout = prefs.get(Settings.sshReadTimeout),
            keepaliveInterval = prefs.get(Settings.sshKeepaliveInterval),
            keepaliveMaxCount = prefs.get(Settings.sshKeepaliveMaxCount),
            compression = prefs.get(Settings.sshCompression),
            strictHostKey = prefs.get(Settings.sshStrictHostKey),
            envVars = prefs.get(Settings.sshEnvVars),
            preferredCiphers = prefs.get(Settings.sshPreferredCiphers),
            preferredKex = prefs.get(Settings.sshPreferredKex),
            preferredMacs = prefs.get(Settings.sshPreferredMacs),
            preferredHostKeyAlgs = prefs.get(Settings.sshPreferredHostKeyAlgs)
        )
    }
}

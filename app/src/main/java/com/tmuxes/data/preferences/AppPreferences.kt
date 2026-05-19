package com.tmuxes.data.preferences

import android.content.Context
import android.net.Uri
import com.tmuxes.data.config.YamlConfigManager
import com.tmuxes.data.settings.Setting
import com.tmuxes.data.settings.Settings
import com.tmuxes.util.ColorHex
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Thin facade over [YamlConfigManager] that types every read/write through
 * the [Settings] registry. There are no per-setting named getters/setters —
 * callers pass the [Setting] directly:
 *
 *     val theme by preferences.flow(Settings.theme).collectAsState(initial = Settings.theme.default)
 *     viewModelScope.launch { preferences.set(Settings.theme, "dark") }
 *
 * Adding a setting is exactly one declaration in [Settings]. The previous
 * AppPreferences had ~40 hand-typed `Flow<T>` properties + ~40 matching
 * setters, all of which had to be kept in lockstep with [YamlSchema] and
 * [SettingsViewModel] by manual copy-and-paste. That's gone.
 *
 * Helpers for the [favorites set] are kept as named methods because they
 * mutate a Set with non-trivial logic that doesn't belong on every caller.
 */
class AppPreferences(context: Context) {

    private val yamlConfig = YamlConfigManager(context, "global_settings.yaml")

    /** Expose the YAML manager for the YAML editor UI. */
    val yamlConfigManager: YamlConfigManager get() = yamlConfig

    // --- Generic setting access ---

    /** Reactive read; emits the registered default when the key is absent. */
    fun <T : Any> flow(setting: Setting<T>): Flow<T> = yamlConfig.getFlowSetting(setting)

    /** Synchronous snapshot read; returns the registered default if absent. */
    fun <T : Any> get(setting: Setting<T>): T = yamlConfig.getSetting(setting)

    /** Persist a typed value. */
    suspend fun <T : Any> set(setting: Setting<T>, value: T) {
        yamlConfig.setSetting(setting, value)
    }

    // --- Aggregate / non-trivial helpers ---

    /**
     * Atomically write the two `app.last_session_*` keys so we never observe
     * a half-updated state where serverId points at a session that doesn't
     * exist anymore.
     */
    suspend fun setLastSession(serverId: Long, sessionName: String) {
        yamlConfig.setAll(mapOf(
            Settings.lastSessionServerId.key.flatPath to serverId,
            Settings.lastSessionName.key.flatPath to sessionName
        ))
    }

    /** Toggle a session's favorite status by its `$serverId:$sessionName` key. */
    suspend fun toggleFavoriteSession(sessionKey: String) {
        val current = get(Settings.favoriteSessions)
        val updated = if (sessionKey in current) current - sessionKey else current + sessionKey
        set(Settings.favoriteSessions, updated)
    }

    /**
     * Migrate a favorite session entry from [oldKey] to [newKey]. Used after
     * a tmux session rename so the user's favorite mark follows the renamed
     * session instead of being silently lost when its key (which is
     * `$serverId:$sessionName`) changes.
     *
     * No-op if [oldKey] was not a favorite.
     */
    suspend fun renameFavoriteSession(oldKey: String, newKey: String) {
        if (oldKey == newKey) return
        val current = get(Settings.favoriteSessions)
        if (oldKey !in current) return
        set(Settings.favoriteSessions, current - oldKey + newKey)
    }

    fun sessionColorsFlow(): Flow<Map<String, Int>> =
        flow(Settings.sessionColors).map { decodeSessionColors(it) }

    suspend fun setSessionColor(sessionKey: String, color: Int) {
        val encodedKey = Uri.encode(sessionKey)
        val current = get(Settings.sessionColors)
            .filterNot { it.substringBefore('=') == encodedKey }
            .toMutableSet()
        if (color != 0) current.add("$encodedKey=${ColorHex.toYamlString(color)}")
        set(Settings.sessionColors, current)
    }

    suspend fun renameSessionColor(oldKey: String, newKey: String) {
        if (oldKey == newKey) return
        val current = decodeSessionColors(get(Settings.sessionColors))
        val color = current[oldKey] ?: return
        setSessionColor(oldKey, 0)
        setSessionColor(newKey, color)
    }

    private fun decodeSessionColors(entries: Set<String>): Map<String, Int> {
        return entries.mapNotNull { entry ->
            val separator = entry.lastIndexOf('=')
            if (separator <= 0 || separator == entry.lastIndex) return@mapNotNull null
            val key = Uri.decode(entry.substring(0, separator))
            val color = ColorHex.parse(entry.substring(separator + 1)) ?: return@mapNotNull null
            key to color
        }.toMap()
    }
}

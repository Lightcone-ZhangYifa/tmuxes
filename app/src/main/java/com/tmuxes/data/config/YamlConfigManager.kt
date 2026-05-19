package com.tmuxes.data.config

import android.content.Context
import com.tmuxes.data.settings.ColorSetting
import com.tmuxes.data.settings.Settings
import com.tmuxes.util.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.StringWriter

/**
 * YAML-based configuration storage engine.
 *
 * Stores configuration as a structured YAML file with nested sections
 * (e.g. app, terminal, ssh, behavior). Internally, keys use dotted notation
 * ("app.theme", "terminal.font_size") which maps to the nested YAML structure.
 *
 * Thread-safe via [Mutex]. Every mutation auto-saves to disk and emits
 * through [configFlow].
 */
class YamlConfigManager(
    private val context: Context,
    private val fileName: String
) {
    companion object {
        private const val TAG = "tmuxes.CONFIG"
    }

    private val configDir = File(context.filesDir, "config")
    private val configFile = File(configDir, fileName)
    private val tempFile = File(configDir, "$fileName.tmp")
    private val mutex = Mutex()
    private val colorSettingsByPath = Settings.all
        .filterIsInstance<ColorSetting>()
        .associateBy { it.key.flatPath }

    private val _configFlow = MutableStateFlow<Map<String, Any?>>(emptyMap())

    /** Emits the full flattened config map on every change. */
    val configFlow: StateFlow<Map<String, Any?>> = _configFlow.asStateFlow()

    private val yaml: Yaml
        get() {
            val options = DumperOptions().apply {
                defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
                isPrettyFlow = true
                indent = 2
                indicatorIndent = 0
                width = 120
            }
            return Yaml(options)
        }

    init {
        configDir.mkdirs()
        val loaded = loadFromDisk()
        _configFlow.value = loaded
        AppLogger.i(AppLogger.Category.CONFIG) { "config.load[$fileName] ← ${loaded.size} keys" }
    }

    // ---------------------------------------------------------------
    // Setting-aware API
    //
    // Reads and writes go through [com.tmuxes.data.settings.Setting], which
    // owns the parse/serialize/validate logic for each value type. Every
    // consumer of preferences uses these — no string-keyed access outside
    // [setAll] (which exists for atomic multi-key writes like
    // `app.last_session_*`).
    // ---------------------------------------------------------------

    /** Synchronous read using a [com.tmuxes.data.settings.Setting]. */
    fun <T : Any> getSetting(setting: com.tmuxes.data.settings.Setting<T>): T {
        val raw = _configFlow.value[setting.key.flatPath]
        return setting.parseRaw(raw) ?: setting.default
    }

    /** Reactive read using a [com.tmuxes.data.settings.Setting]. */
    fun <T : Any> getFlowSetting(setting: com.tmuxes.data.settings.Setting<T>): Flow<T> =
        _configFlow.map { map ->
            setting.parseRaw(map[setting.key.flatPath]) ?: setting.default
        }.distinctUntilChanged()

    /** Persist a [com.tmuxes.data.settings.Setting] value. */
    suspend fun <T : Any> setSetting(setting: com.tmuxes.data.settings.Setting<T>, value: T) {
        mutex.withLock {
            val current = _configFlow.value.toMutableMap()
            current[setting.key.flatPath] = setting.serialize(value)
            _configFlow.value = current
            saveToDisk(current)
        }
        AppLogger.d(AppLogger.Category.CONFIG) { "config.set[$fileName] ${setting.key.flatPath}=$value" }
    }

    /**
     * Set multiple keys atomically and persist to disk.
     */
    suspend fun setAll(entries: Map<String, Any?>) {
        mutex.withLock {
            val current = _configFlow.value.toMutableMap()
            current.putAll(entries)
            _configFlow.value = current
            saveToDisk(current)
        }
        AppLogger.d(AppLogger.Category.CONFIG) { "config.setAll[$fileName] keys=${entries.keys}" }
    }

    /**
     * Return the current config as formatted YAML text (for the YAML editor).
     */
    fun getYamlText(): String {
        return try {
            val nested = flatToNested(normalizeColorValues(_configFlow.value))
            val writer = StringWriter()
            yaml.dump(nested, writer)
            addSectionComments(writer.toString())
        } catch (e: Exception) {
            AppLogger.e(AppLogger.Category.CONFIG, e) { "Failed to serialise YAML text" }
            ""
        }
    }

    /**
     * Parse YAML text and replace the entire config (for the YAML editor).
     */
    suspend fun setFromYamlText(text: String) {
        mutex.withLock {
            try {
                val parsed = yaml.load<Any>(text)
                val nested = if (parsed is Map<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    parsed as Map<String, Any?>
                } else {
                    emptyMap()
                }
                val flat = normalizeColorValues(nestedToFlat(nested))
                _configFlow.value = flat
                saveToDisk(flat)
            } catch (e: Exception) {
                AppLogger.e(AppLogger.Category.CONFIG, e) { "Failed to parse YAML text" }
                throw e
            }
        }
    }

    /**
     * Reset config: clear the file and emit an empty map.
     * Consumers will fall back to their defaults via getFlow().
     */
    suspend fun reset() {
        mutex.withLock {
            val previous = _configFlow.value.size
            _configFlow.value = emptyMap()
            saveToDisk(emptyMap())
            AppLogger.w(AppLogger.Category.CONFIG) { "config.reset[$fileName] wiped=$previous" }
        }
    }

    // ---------------------------------------------------------------
    // Disk I/O
    // ---------------------------------------------------------------

    /**
     * Parse a YAML file into the flat map representation. Throws on any
     * malformed input — the caller decides how to recover.
     */
    private fun parseYamlFile(file: File): Map<String, Any?> {
        val text = file.readText()
        if (text.isBlank()) return emptyMap()
        val parsed = yaml.load<Any>(text)
        return if (parsed is Map<*, *>) {
            @Suppress("UNCHECKED_CAST")
            nestedToFlat(parsed as Map<String, Any?>)
        } else {
            emptyMap()
        }
    }

    private fun loadFromDisk(): Map<String, Any?> {
        if (!configFile.exists()) return emptyMap()
        return parseYamlFile(configFile)
    }

    private fun saveToDisk(flat: Map<String, Any?>) {
        try {
            val normalized = normalizeColorValues(flat)
            val nested = flatToNested(normalized)
            val writer = StringWriter()
            yaml.dump(nested, writer)
            val yamlText = addSectionComments(writer.toString())
            configDir.mkdirs()
            tempFile.writeText(yamlText)
            if (!tempFile.renameTo(configFile)) {
                configFile.writeText(yamlText)
                tempFile.delete()
            }
        } catch (e: Exception) {
            AppLogger.e(AppLogger.Category.CONFIG, e) { "Failed to save config to $configFile" }
        }
    }

    // ---------------------------------------------------------------
    // Nested <-> Flat conversion
    // ---------------------------------------------------------------

    /**
     * Convert flat dotted keys ("app.theme" -> "dark") into nested maps
     * ({"app": {"theme": "dark"}}).
     */
    private fun flatToNested(flat: Map<String, Any?>): LinkedHashMap<String, Any?> {
        val result = LinkedHashMap<String, Any?>()
        // Sort keys so sections are grouped
        for ((key, value) in flat.toSortedMap()) {
            val parts = key.split(".")
            if (parts.size == 1) {
                result[key] = value
            } else {
                var current: MutableMap<String, Any?> = result
                for (i in 0 until parts.size - 1) {
                    val part = parts[i]
                    val existing = current[part]
                    if (existing is MutableMap<*, *>) {
                        @Suppress("UNCHECKED_CAST")
                        current = existing as MutableMap<String, Any?>
                    } else {
                        val newMap = LinkedHashMap<String, Any?>()
                        current[part] = newMap
                        current = newMap
                    }
                }
                current[parts.last()] = value
            }
        }
        return result
    }

    private fun normalizeColorValues(flat: Map<String, Any?>): Map<String, Any?> {
        if (flat.isEmpty()) return flat
        return flat.mapValues { (path, raw) ->
            val colorSetting = colorSettingsByPath[path] ?: return@mapValues raw
            val parsed = colorSetting.parseRaw(raw) ?: return@mapValues raw
            colorSetting.serialize(parsed)
        }
    }

    /**
     * Convert nested maps into flat dotted keys.
     *
     * Cycle protection: SnakeYAML's default [org.yaml.snakeyaml.Yaml] loader
     * resolves anchors and aliases into real Java object references, so a
     * user-supplied YAML with a self-referencing anchor like:
     *
     *     app: &x
     *       sub: *x
     *
     * produces a map where `map["app"]["sub"]` IS `map["app"]` — a true
     * cycle in the object graph. Recursing without a visited-set guard
     * would StackOverflowError, which is a [VirtualMachineError] that
     * [com.tmuxes.TmuxesApp.installUncaughtExceptionHandler] treats as
     * fatal and delegates to the platform handler, killing the process.
     * Reachable because the YAML file is user-editable via Settings →
     * Edit Config (YAML).
     *
     * Fix: track visited map instances (identity-based) and cap recursion
     * depth. On a cycle or depth overflow, drop the entry silently —
     * saving a pathological config is better than crashing on save.
     */
    private fun nestedToFlat(
        nested: Map<String, Any?>,
        prefix: String = "",
        depth: Int = 0,
        visited: java.util.IdentityHashMap<Map<*, *>, Unit> = java.util.IdentityHashMap()
    ): Map<String, Any?> {
        val result = LinkedHashMap<String, Any?>()
        if (depth > 64) return result           // sanity bound
        if (visited.put(nested, Unit) != null) return result  // already seen → cycle
        for ((key, value) in nested) {
            val fullKey = if (prefix.isEmpty()) key else "$prefix.$key"
            if (value is Map<*, *> && value.keys.all { it is String }) {
                @Suppress("UNCHECKED_CAST")
                result.putAll(nestedToFlat(value as Map<String, Any?>, fullKey, depth + 1, visited))
            } else {
                result[fullKey] = value
            }
        }
        return result
    }

    // ---------------------------------------------------------------
    // Type coercion
    // ---------------------------------------------------------------

    // ---------------------------------------------------------------
    // YAML comment injection
    // ---------------------------------------------------------------

    private fun addSectionComments(yamlText: String): String {
        val sb = StringBuilder()
        sb.appendLine("# tmuxes Configuration - $fileName")
        sb.appendLine("# Auto-generated. Edit with care.")
        sb.appendLine()
        var prevSection = ""
        for (line in yamlText.lines()) {
            // Detect top-level section headers (no leading whitespace, ends with ':')
            if (line.isNotBlank() && !line.startsWith(" ") && !line.startsWith("#") && line.contains(":")) {
                val section = line.substringBefore(":").trim()
                if (section != prevSection && prevSection.isNotEmpty()) {
                    sb.appendLine()
                }
                prevSection = section
            }
            sb.appendLine(line)
        }
        return sb.toString().trimEnd() + "\n"
    }
}

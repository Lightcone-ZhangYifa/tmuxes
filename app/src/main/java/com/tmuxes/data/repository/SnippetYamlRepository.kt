package com.tmuxes.data.repository

import android.content.Context
import com.tmuxes.data.model.CommandSnippet
import com.tmuxes.data.model.EnabledSnippet
import com.tmuxes.data.model.SnippetLibrary
import com.tmuxes.data.model.SnippetsConfig
import com.tmuxes.data.preset.PresetSnippetCatalog
import com.tmuxes.util.AppLogger
import com.tmuxes.util.AppLogger.Category
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

/**
 * YAML-backed snippet repository. The single source of truth is the
 * full [SnippetsConfig] document (one StateFlow, one YAML file). All
 * mutations are atomic transforms on the config.
 *
 * Public API is YAML-native — no Flow<List<XEntity>>, no row-CRUD, no
 * libraryId FK navigation. Library/snippet sugar functions are thin
 * wrappers over [update].
 *
 * Persistence: `context.filesDir/config/snippets.yaml`. Atomic write
 * via `.tmp` rename.
 *
 * On first launch (no `snippets.yaml` yet) the repo seeds itself from
 * [PresetSnippetCatalog].
 */
class SnippetYamlRepository internal constructor(private val configDir: File) {

    /** Production constructor: stores under [Context.getFilesDir]/config/. */
    constructor(context: Context) : this(File(context.filesDir, "config"))

    companion object {
        private const val FILE_NAME = "snippets.yaml"
    }

    private val configFile = File(configDir, FILE_NAME)
    private val tempFile = File(configDir, "$FILE_NAME.tmp")
    private val mutex = Mutex()

    private val _config = MutableStateFlow(SnippetsConfig())

    /** Single source of truth — the entire document state. */
    val config: StateFlow<SnippetsConfig> = _config.asStateFlow()

    /** Convenience: just the libraries list, sorted by sortOrder then name. */
    val libraries: Flow<List<SnippetLibrary>> = _config
        .map { cfg ->
            cfg.libraries.sortedWith(compareBy({ it.sortOrder }, { it.name }))
        }
        .distinctUntilChanged()

    /**
     * Convenience: every snippet whose containing library is enabled
     * AND whose own `is_enabled` is true, paired with its library.
     */
    val enabledSnippets: Flow<List<EnabledSnippet>> = _config
        .map { cfg ->
            cfg.libraries.asSequence()
                .filter { it.isEnabled }
                .flatMap { lib ->
                    lib.snippets.asSequence()
                        .filter { it.isEnabled }
                        .map { snip -> EnabledSnippet(snip, lib) }
                }
                .sortedWith(compareBy({ it.library.sortOrder }, { it.snippet.sortOrder }))
                .toList()
        }
        .distinctUntilChanged()

    init {
        configDir.mkdirs()
        loadFromDisk()
        if (tempFile.exists()) tempFile.delete()
    }

    // -----------------------------------------------------------------
    // Atomic transforms (the core API)
    // -----------------------------------------------------------------

    /**
     * Apply [transform] to the current config and persist. Runs under
     * the mutex; concurrent callers are serialized.
     */
    suspend fun update(transform: (SnippetsConfig) -> SnippetsConfig): SnippetsConfig =
        mutex.withLock {
            val next = transform(_config.value)
            _config.value = next
            saveToDiskLocked()
            next
        }

    // -----------------------------------------------------------------
    // Library sugar
    // -----------------------------------------------------------------

    suspend fun addLibrary(library: SnippetLibrary): Long {
        val id = if (library.id > 0) library.id else nextLibraryId()
        val withId = library.copy(id = id)
        update { cfg -> cfg.copy(libraries = cfg.libraries + withId) }
        AppLogger.i(Category.DB) { "snippet.lib.add id=$id name='${library.name}'" }
        return id
    }

    suspend fun updateLibrary(id: Long, transform: SnippetLibrary.() -> SnippetLibrary) {
        update { cfg ->
            cfg.copy(libraries = cfg.libraries.map {
                if (it.id == id) it.transform() else it
            })
        }
    }

    suspend fun deleteLibrary(id: Long) {
        update { cfg -> cfg.copy(libraries = cfg.libraries.filterNot { it.id == id }) }
        AppLogger.i(Category.DB) { "snippet.lib.delete id=$id (cascade)" }
    }

    suspend fun reorderLibraries(orderedIds: List<Long>) {
        update { cfg ->
            val byId = cfg.libraries.associateBy { it.id }
            cfg.copy(libraries = orderedIds.mapIndexedNotNull { idx, id ->
                byId[id]?.copy(sortOrder = idx)
            })
        }
    }

    // -----------------------------------------------------------------
    // Snippet sugar (always library-scoped)
    // -----------------------------------------------------------------

    suspend fun addSnippet(libraryId: Long, snippet: CommandSnippet): Long {
        val id = if (snippet.id > 0) snippet.id else nextSnippetId()
        val withId = snippet.copy(id = id)
        updateLibrary(libraryId) { copy(snippets = snippets + withId) }
        return id
    }

    suspend fun updateSnippet(
        libraryId: Long,
        snippetId: Long,
        transform: CommandSnippet.() -> CommandSnippet
    ) {
        updateLibrary(libraryId) {
            copy(snippets = snippets.map {
                if (it.id == snippetId) it.transform() else it
            })
        }
    }

    suspend fun deleteSnippet(libraryId: Long, snippetId: Long) {
        updateLibrary(libraryId) {
            copy(snippets = snippets.filterNot { it.id == snippetId })
        }
    }

    suspend fun moveSnippet(fromLibraryId: Long, snippetId: Long, toLibraryId: Long) {
        update { cfg ->
            var moved: CommandSnippet? = null
            val pulled = cfg.libraries.map { lib ->
                if (lib.id == fromLibraryId) {
                    val (target, rest) = lib.snippets.partition { it.id == snippetId }
                    moved = target.firstOrNull()
                    lib.copy(snippets = rest)
                } else lib
            }
            val pushed = pulled.map { lib ->
                val m = moved
                if (lib.id == toLibraryId && m != null) {
                    lib.copy(snippets = lib.snippets + m)
                } else lib
            }
            cfg.copy(libraries = pushed)
        }
    }

    suspend fun reorderSnippets(libraryId: Long, orderedIds: List<Long>) {
        updateLibrary(libraryId) {
            val byId = snippets.associateBy { it.id }
            copy(snippets = orderedIds.mapIndexedNotNull { idx, id ->
                byId[id]?.copy(sortOrder = idx)
            })
        }
    }

    // -----------------------------------------------------------------
    // YAML round-trip (for editor integration; parallel to ServerYamlRepository)
    // -----------------------------------------------------------------

    /**
     * Serialize current config to YAML text. Non-suspend for parity with
     * [ServerYamlRepository.getYamlText] — the editor's load path is
     * synchronous. StateFlow `.value` reads are atomic; mutations always
     * route through [update] under the mutex, so a serialization racing
     * with a write sees one consistent snapshot or the next one but never
     * a torn intermediate.
     */
    fun getYamlText(): String = yamlSerialize(_config.value)

    /**
     * Replace the entire config from YAML text. Throws on parse error
     * (caller validates). Triggers persistence under mutex. Same name
     * and shape as [ServerYamlRepository.setFromYamlText] so the editor
     * dispatches to either repo with identical syntax.
     */
    suspend fun setFromYamlText(text: String) = mutex.withLock {
        val parsed = parseYamlText(text)
        _config.value = parsed
        saveToDiskLocked()
    }

    // -----------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------

    private fun nextLibraryId(): Long =
        (_config.value.libraries.maxOfOrNull { it.id } ?: 0L) + 1L

    private fun nextSnippetId(): Long =
        (_config.value.libraries.flatMap { it.snippets }.maxOfOrNull { it.id } ?: 0L) + 1L

    private fun loadFromDisk() {
        if (!configFile.exists()) {
            seedFromCatalog()
            return
        }
        val parsed = parseYamlText(configFile.readText(Charsets.UTF_8))
        _config.value = parsed
        AppLogger.i(Category.DB) {
            "snippet.yaml load → ${parsed.libraries.size} libraries / " +
                "${parsed.libraries.sumOf { it.snippets.size }} snippets"
        }
    }

    private fun seedFromCatalog() {
        AppLogger.i(Category.DB) {
            "snippet.yaml seed from PresetSnippetCatalog (${PresetSnippetCatalog.libraries.size} libs)"
        }
        var libId = 1L
        var snipId = 1L
        val libs = PresetSnippetCatalog.libraries.mapIndexed { libIdx, libTpl ->
            val snippets = libTpl.snippets.mapIndexed { snipIdx, tpl ->
                CommandSnippet(
                    id = snipId++,
                    name = tpl.name,
                    command = tpl.command,
                    isEnabled = true,
                    isFavorited = false,
                    sortOrder = snipIdx
                )
            }
            SnippetLibrary(
                id = libId++,
                name = libTpl.name,
                description = libTpl.description,
                iconName = libTpl.iconName,
                isEnabled = false,
                sortOrder = (libIdx + 1) * 10,
                snippets = snippets
            )
        }
        _config.value = SnippetsConfig(libs)
        // init can't suspend; do plain (non-mutex) write — only this code path
        // can race with itself, and it only runs when configFile is missing.
        try {
            val text = yamlSerialize(_config.value)
            tempFile.writeText(text, Charsets.UTF_8)
            tempFile.renameTo(configFile)
        } catch (e: Exception) {
            AppLogger.e(Category.DB, e) { "snippet.yaml initial seed write fail: ${e.message}" }
        }
    }

    private fun saveToDiskLocked() {
        try {
            val text = yamlSerialize(_config.value)
            tempFile.writeText(text, Charsets.UTF_8)
            if (!tempFile.renameTo(configFile)) {
                configFile.writeText(text, Charsets.UTF_8)
                tempFile.delete()
            }
        } catch (e: Exception) {
            AppLogger.e(Category.DB, e) { "snippet.yaml save fail: ${e.message}" }
        }
    }

    // -----------------------------------------------------------------
    // YAML schema (snake_case keys + write-if-non-default + nested)
    // -----------------------------------------------------------------

    private fun yamlSerialize(cfg: SnippetsConfig): String {
        val tree = LinkedHashMap<String, Any?>()
        tree["libraries"] = cfg.libraries
            .sortedBy { it.sortOrder }
            .map(::libraryToMap)
        val opts = DumperOptions().apply {
            defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
            isPrettyFlow = true
            indent = 2
            indicatorIndent = 0
            width = 120
        }
        return Yaml(opts).dump(tree)
    }

    private fun libraryToMap(lib: SnippetLibrary): LinkedHashMap<String, Any?> {
        val m = LinkedHashMap<String, Any?>()
        m["id"] = lib.id
        m["name"] = lib.name
        if (lib.description.isNotEmpty()) m["description"] = lib.description
        if (lib.iconName != "Code") m["icon_name"] = lib.iconName
        if (!lib.isEnabled) m["is_enabled"] = false
        if (lib.sortOrder != 0) m["sort_order"] = lib.sortOrder
        if (lib.snippets.isNotEmpty()) {
            m["snippets"] = lib.snippets.sortedBy { it.sortOrder }.map(::snippetToMap)
        }
        return m
    }

    private fun snippetToMap(s: CommandSnippet): LinkedHashMap<String, Any?> {
        val m = LinkedHashMap<String, Any?>()
        m["id"] = s.id
        m["name"] = s.name
        m["command"] = s.command
        if (!s.isEnabled) m["is_enabled"] = false
        if (s.isFavorited) m["is_favorited"] = true
        if (s.sortOrder != 0) m["sort_order"] = s.sortOrder
        return m
    }

    private fun parseYamlText(text: String): SnippetsConfig {
        @Suppress("UNCHECKED_CAST")
        val root = (Yaml().load(text) as? Map<String, Any?>) ?: return SnippetsConfig()
        val libsRaw = (root["libraries"] as? List<*>).orEmpty()
        val libs = libsRaw.mapNotNull { libRaw ->
            val m = libRaw as? Map<*, *> ?: return@mapNotNull null
            mapToLibrary(m)
        }
        return SnippetsConfig(libs)
    }

    private fun mapToLibrary(m: Map<*, *>): SnippetLibrary {
        val nested = (m["snippets"] as? List<*>).orEmpty()
        val snippets = nested.mapNotNull { snipRaw ->
            val sm = snipRaw as? Map<*, *> ?: return@mapNotNull null
            mapToSnippet(sm)
        }
        return SnippetLibrary(
            id = (m["id"] as? Number)?.toLong() ?: 0L,
            name = m["name"] as? String ?: "",
            description = m["description"] as? String ?: "",
            iconName = m["icon_name"] as? String ?: "Code",
            isEnabled = m["is_enabled"] as? Boolean ?: true,
            sortOrder = (m["sort_order"] as? Number)?.toInt() ?: 0,
            snippets = snippets
        )
    }

    private fun mapToSnippet(m: Map<*, *>): CommandSnippet = CommandSnippet(
        id = (m["id"] as? Number)?.toLong() ?: 0L,
        name = m["name"] as? String ?: "",
        command = m["command"] as? String ?: "",
        isEnabled = m["is_enabled"] as? Boolean ?: true,
        isFavorited = m["is_favorited"] as? Boolean ?: false,
        sortOrder = (m["sort_order"] as? Number)?.toInt() ?: 0
    )
}

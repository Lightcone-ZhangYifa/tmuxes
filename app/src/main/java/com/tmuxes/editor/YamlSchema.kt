package com.tmuxes.editor

import com.tmuxes.data.settings.BooleanSetting
import com.tmuxes.data.settings.ColorSetting
import com.tmuxes.data.settings.FloatSetting
import com.tmuxes.data.settings.IntEnumSetting
import com.tmuxes.data.settings.IntSetting
import com.tmuxes.data.settings.LongSetting
import com.tmuxes.data.settings.Setting
import com.tmuxes.data.settings.SettingFile
import com.tmuxes.data.settings.Settings
import com.tmuxes.data.settings.StringEnumSetting
import com.tmuxes.data.settings.StringSetSetting
import com.tmuxes.data.settings.StringSetting
import com.tmuxes.util.ColorHex

enum class YamlFileType { GLOBAL, WIDGET, SERVERS, SNIPPETS }

/**
 * Schema for the YAML editor's diagnostics, completion and type-hint
 * popups.
 *
 * The **global** schema is auto-derived from [Settings.all] — every
 * registered setting becomes a [SchemaNode.Leaf] under whatever section
 * tree its [com.tmuxes.data.settings.SettingKey.path] describes. Adding a
 * new setting in [Settings] makes it appear in the YAML editor with its
 * type, range, enum options and value-typehint without anyone editing
 * this file.
 *
 * **Widget** and **servers** schemas are still hand-written below — they
 * describe payload formats that aren't user preferences (per-widget
 * instance config / per-server entity rows). Those payloads belong to
 * [com.tmuxes.data.config.WidgetYamlConfig] and
 * [com.tmuxes.data.repository.ServerYamlRepository] respectively.
 */
object YamlSchema {

    /**
     * One node in the schema tree. Nodes are either:
     * - [Leaf] — terminal entry pointing at a [Setting] (auto-derived) or
     *   a [InlineSpec] (hand-written for non-Settings YAML payloads).
     * - [Section] — named container of children.
     */
    sealed class SchemaNode {
        abstract val name: String

        data class Section(
            override val name: String,
            val children: List<SchemaNode>
        ) : SchemaNode()

        sealed class Leaf : SchemaNode() {
            abstract val typeHint: String
            /** Run YAML's parsed value through the appropriate validator and report any error. */
            abstract fun validate(rawValue: Any?): String?
            /** Suggested values for completion (enum members, "true"/"false", etc.) — empty if free-form. */
            abstract fun completions(): List<String>
        }

        /** A leaf backed by a registered [Setting]. */
        data class FromSetting(
            override val name: String,
            val setting: Setting<*>
        ) : Leaf() {
            override val typeHint: String get() = settingTypeHint(setting)
            override fun validate(rawValue: Any?): String? = validateSettingValue(setting, rawValue)
            override fun completions(): List<String> = settingCompletions(setting)
        }

        /**
         * A hand-written leaf for YAML payloads that aren't user preferences
         * (server entity rows, per-widget instance config). Carries the same
         * type-hint metadata the auto-derived one does so the analyzer can
         * be unified.
         */
        data class InlineSpec(
            override val name: String,
            val kind: InlineKind,
            val enumValues: List<String> = emptyList(),
            val intRange: IntRange? = null,
            override val typeHint: String
        ) : Leaf() {
            override fun validate(rawValue: Any?): String? = validateInline(this, rawValue)
            override fun completions(): List<String> = when (kind) {
                InlineKind.BOOLEAN -> listOf("true", "false")
                else -> enumValues
            }
        }

        enum class InlineKind { STRING, INT, LONG, FLOAT, BOOLEAN, INT_RANGE, ENUM, COLOR }
    }

    // -------------------------------------------------------------------------
    // Tree construction
    // -------------------------------------------------------------------------

    /** Build a tree out of the registered global Settings. */
    private fun globalTree(): List<SchemaNode> = buildTreeFromSettings(
        Settings.all.filter { it.key.fileType == SettingFile.GLOBAL }
    )

    private fun buildTreeFromSettings(settings: List<Setting<*>>): List<SchemaNode> {
        // Group by first path segment, recurse into the rest.
        return settings
            .groupBy { it.key.path.first() }
            .toSortedMap()
            .map { (sectionName, group) ->
                val (immediate, nested) = group.partition { it.key.path.size == 2 }
                val leaves = immediate.map { SchemaNode.FromSetting(it.key.leafName, it) }
                val deeperBySegment = nested.groupBy { it.key.path[1] }
                val deeperSections = deeperBySegment.map { (subName, subGroup) ->
                    // Strip the first segment and recurse — wrap each child setting
                    // in a synthetic SettingKey-like view that drops the leading section.
                    val rest = subGroup.map { s ->
                        // We don't actually need to clone the Setting — just navigate
                        // from this section by pretending we're rooted at subName.
                        s
                    }
                    buildSubsection(subName, rest, depth = 2)
                }
                SchemaNode.Section(sectionName, leaves + deeperSections)
            }
    }

    private fun buildSubsection(name: String, settings: List<Setting<*>>, depth: Int): SchemaNode.Section {
        val (immediate, nested) = settings.partition { it.key.path.size == depth + 1 }
        val leaves = immediate.map { SchemaNode.FromSetting(it.key.path[depth], it) }
        val deeperBySegment = nested.groupBy { it.key.path[depth] }
        val deeper = deeperBySegment.map { (sub, group) -> buildSubsection(sub, group, depth + 1) }
        return SchemaNode.Section(name, leaves + deeper)
    }

    // -------------------------------------------------------------------------
    // Hand-written trees for non-Settings payloads
    // -------------------------------------------------------------------------

    private fun leaf(
        name: String,
        kind: SchemaNode.InlineKind,
        typeHint: String,
        values: List<String> = emptyList(),
        rangeMin: Int = 0,
        rangeMax: Int = 0
    ) = SchemaNode.InlineSpec(
        name = name,
        kind = kind,
        enumValues = values,
        intRange = if (rangeMin != rangeMax) rangeMin..rangeMax else null,
        typeHint = typeHint
    )

    private val widgetTree: List<SchemaNode> = listOf(
        SchemaNode.Section("session", listOf(
            leaf("server_id", SchemaNode.InlineKind.LONG, "Long"),
            leaf("session_name", SchemaNode.InlineKind.STRING, "String")
        )),
        SchemaNode.Section("widget", listOf(
            leaf("opacity", SchemaNode.InlineKind.INT_RANGE, "Int (0..100)", rangeMin = 0, rangeMax = 100),
            leaf("show_title_bar", SchemaNode.InlineKind.BOOLEAN, "Boolean"),
            leaf("orientation", SchemaNode.InlineKind.ENUM, "0 | 90 | 180 | 270",
                values = listOf("0", "90", "180", "270")),
            leaf("title_accent_color", SchemaNode.InlineKind.COLOR, "Hex color string (\"#RRGGBB\" or \"#AARRGGBB\")")
        )),
        SchemaNode.Section("terminal", listOf(
            leaf("font_family", SchemaNode.InlineKind.STRING, "String (empty = follow global; jetbrains_mono | monospace | sans_serif | serif)",
                values = listOf("jetbrains_mono", "monospace", "sans_serif", "serif")),
            leaf("font_size", SchemaNode.InlineKind.FLOAT, "Float (0 = auto, 6..36 otherwise)"),
            leaf("font_weight", SchemaNode.InlineKind.ENUM, "thin | light | normal | medium | bold",
                values = listOf("thin", "light", "normal", "medium", "bold")),
            leaf("color_scheme", SchemaNode.InlineKind.STRING, "String (empty = follow global; built-in/custom scheme name)",
                values = listOf("catppuccin", "monokai", "dracula", "nord", "solarized", "gruvbox", "one_dark")),
            leaf("cursor_style", SchemaNode.InlineKind.ENUM, "block | underline | bar",
                values = listOf("block", "underline", "bar")),
            leaf("cursor_blink", SchemaNode.InlineKind.BOOLEAN, "Boolean (show cursor in widget)"),
            leaf("cursor_color", SchemaNode.InlineKind.COLOR, "Hex color string (\"#RRGGBB\" or \"#AARRGGBB\", \"#00000000\" = inherit)"),
            leaf("background_opacity", SchemaNode.InlineKind.INT_RANGE, "Int (0..100)", rangeMin = 0, rangeMax = 100),
            leaf("bold_is_bright", SchemaNode.InlineKind.BOOLEAN, "Boolean"),
            leaf("underline_style", SchemaNode.InlineKind.ENUM, "solid | double | dotted | dashed",
                values = listOf("solid", "double", "dotted", "dashed")),
            leaf("line_spacing", SchemaNode.InlineKind.INT_RANGE, "Int (100..150)", rangeMin = 100, rangeMax = 150),
            leaf("padding", SchemaNode.InlineKind.INT_RANGE, "Int (0..24)", rangeMin = 0, rangeMax = 24),
            leaf("scrollback_lines", SchemaNode.InlineKind.INT_RANGE, "Int (100..1000000)", rangeMin = 100, rangeMax = 1_000_000)
        ))
    )

    private val serversTree: List<SchemaNode> = listOf(
        SchemaNode.Section("servers[]", listOf(
            leaf("id", SchemaNode.InlineKind.LONG, "Long"),
            leaf("hostname", SchemaNode.InlineKind.STRING, "String"),
            leaf("port", SchemaNode.InlineKind.INT_RANGE, "Int (1..65535)", rangeMin = 1, rangeMax = 65535),
            leaf("username", SchemaNode.InlineKind.STRING, "String"),
            leaf("name", SchemaNode.InlineKind.STRING, "String"),
            leaf("auth_method", SchemaNode.InlineKind.ENUM, "PASSWORD | KEY | KEY_WITH_PASSPHRASE",
                values = listOf("PASSWORD", "KEY", "KEY_WITH_PASSPHRASE")),
            leaf("password", SchemaNode.InlineKind.STRING, "String"),
            leaf("private_key_data", SchemaNode.InlineKind.STRING, "String"),
            leaf("passphrase", SchemaNode.InlineKind.STRING, "String"),
            leaf("color", SchemaNode.InlineKind.COLOR, "Hex color string (\"#RRGGBB\" or \"#AARRGGBB\")"),
            leaf("parent_id", SchemaNode.InlineKind.LONG, "Long"),
            leaf("keep_alive_interval", SchemaNode.InlineKind.INT_RANGE, "Int (0..3600)", rangeMin = 0, rangeMax = 3600),
            leaf("compression", SchemaNode.InlineKind.BOOLEAN, "Boolean"),
            leaf("last_connected_at", SchemaNode.InlineKind.LONG, "Long"),
            leaf("is_enabled", SchemaNode.InlineKind.BOOLEAN, "Boolean"),
            leaf("sort_order", SchemaNode.InlineKind.INT, "Int"),
            leaf("term_type", SchemaNode.InlineKind.STRING, "String"),
            leaf("connection_timeout", SchemaNode.InlineKind.INT_RANGE, "Int (1..300)", rangeMin = 1, rangeMax = 300),
            leaf("transport_timeout", SchemaNode.InlineKind.INT_RANGE, "Int (1..300)", rangeMin = 1, rangeMax = 300),
            leaf("read_timeout", SchemaNode.InlineKind.INT_RANGE, "Int (1..300)", rangeMin = 1, rangeMax = 300),
            leaf("keepalive_max_count", SchemaNode.InlineKind.INT_RANGE, "Int (0..100)", rangeMin = 0, rangeMax = 100),
            leaf("strict_host_key", SchemaNode.InlineKind.ENUM, "ask | accept | reject",
                values = listOf("ask", "accept", "reject")),
            leaf("env_vars", SchemaNode.InlineKind.STRING, "String"),
            leaf("preferred_ciphers", SchemaNode.InlineKind.STRING, "String"),
            leaf("preferred_kex", SchemaNode.InlineKind.STRING, "String"),
            leaf("preferred_macs", SchemaNode.InlineKind.STRING, "String"),
            leaf("preferred_host_key_algs", SchemaNode.InlineKind.STRING, "String"),
            leaf("remote_forwards", SchemaNode.InlineKind.STRING, "String"),
            leaf("local_forwards", SchemaNode.InlineKind.STRING, "String")
        ))
    )

    /**
     * Schema for `snippets.yaml`. Top-level is a single `libraries:` array;
     * each library object embeds its own `snippets:` array. The `[]`
     * suffix in section names is a marker recognized by
     * [com.tmuxes.editor.YamlDiagnosticAnalyzer.validateSnippets] to
     * indicate "list of homogeneous objects" — same convention as
     * `servers[]`.
     */
    private val snippetsTree: List<SchemaNode> = listOf(
        SchemaNode.Section("libraries[]", listOf(
            leaf("id", SchemaNode.InlineKind.LONG, "Long"),
            leaf("name", SchemaNode.InlineKind.STRING, "String"),
            leaf("description", SchemaNode.InlineKind.STRING, "String"),
            leaf("icon_name", SchemaNode.InlineKind.STRING, "String"),
            leaf("is_enabled", SchemaNode.InlineKind.BOOLEAN, "Boolean"),
            leaf("sort_order", SchemaNode.InlineKind.INT, "Int"),
            SchemaNode.Section("snippets[]", listOf(
                leaf("id", SchemaNode.InlineKind.LONG, "Long"),
                leaf("name", SchemaNode.InlineKind.STRING, "String"),
                leaf("command", SchemaNode.InlineKind.STRING, "String"),
                leaf("is_enabled", SchemaNode.InlineKind.BOOLEAN, "Boolean"),
                leaf("is_favorited", SchemaNode.InlineKind.BOOLEAN, "Boolean"),
                leaf("sort_order", SchemaNode.InlineKind.INT, "Int")
            ))
        ))
    )

    // -------------------------------------------------------------------------
    // Public lookup API
    // -------------------------------------------------------------------------

    private fun rootFor(fileType: YamlFileType): List<SchemaNode> = when (fileType) {
        YamlFileType.GLOBAL -> globalTree()
        YamlFileType.WIDGET -> widgetTree
        YamlFileType.SERVERS -> serversTree
        YamlFileType.SNIPPETS -> snippetsTree
    }

    /** Top-level section names — the things that can appear at YAML root level. */
    fun sectionNames(fileType: YamlFileType = YamlFileType.GLOBAL): List<String> =
        rootFor(fileType).filterIsInstance<SchemaNode.Section>().map { it.name }

    /** Resolve `path` (as a list of segments) to a node, or null if no such path exists. */
    fun nodeAt(path: List<String>, fileType: YamlFileType = YamlFileType.GLOBAL): SchemaNode? {
        if (path.isEmpty()) return null
        var nodes = rootFor(fileType)
        var current: SchemaNode? = null
        for (segment in path) {
            val match = nodes.firstOrNull { it.name == segment } ?: return null
            current = match
            nodes = (match as? SchemaNode.Section)?.children ?: emptyList()
        }
        return current
    }

    /** Children of the section reached by [path]. Empty list if [path] doesn't resolve to a section. */
    fun childrenAt(path: List<String>, fileType: YamlFileType = YamlFileType.GLOBAL): List<SchemaNode> {
        if (path.isEmpty()) return rootFor(fileType)
        return (nodeAt(path, fileType) as? SchemaNode.Section)?.children ?: emptyList()
    }

    /** Convenience: children given a flat dotted path. */
    fun childrenAtFlat(flat: String, fileType: YamlFileType = YamlFileType.GLOBAL): List<SchemaNode> =
        childrenAt(if (flat.isEmpty()) emptyList() else flat.split('.'), fileType)

    /** Convenience: leaf at `<sectionFlat>.<key>`. */
    fun leafAt(sectionFlat: String, key: String, fileType: YamlFileType = YamlFileType.GLOBAL): SchemaNode.Leaf? {
        val sectionPath = if (sectionFlat.isEmpty()) emptyList() else sectionFlat.split('.')
        return nodeAt(sectionPath + key, fileType) as? SchemaNode.Leaf
    }

    // -------------------------------------------------------------------------
    // Validation helpers
    // -------------------------------------------------------------------------

    private fun settingTypeHint(setting: Setting<*>): String = when (setting) {
        is BooleanSetting -> "Boolean"
        is IntSetting -> setting.range?.let { "Int (${it.first}..${it.last})" } ?: "Int"
        is LongSetting -> "Long"
        is FloatSetting -> setting.range?.let { "Float (${it.start}..${it.endInclusive})" } ?: "Float"
        is StringSetting -> "String"
        is StringEnumSetting -> setting.values.joinToString(" | ")
        is IntEnumSetting -> setting.values.joinToString(" | ")
        is StringSetSetting -> "Set<String>"
        is ColorSetting -> "Hex color string (\"#RRGGBB\" or \"#AARRGGBB\")"
    }

    private fun validateSettingValue(setting: Setting<*>, raw: Any?): String? {
        if (raw == null) return null
        when (setting) {
            is BooleanSetting -> if (raw !is Boolean)
                return "Expected Boolean (true/false) for ${setting.key.leafName}"
            is IntSetting -> {
                val n = (raw as? Number)?.toInt()
                    ?: return "Expected Int for ${setting.key.leafName}"
                setting.range?.let {
                    if (n !in it) return "Value $n out of range $it for ${setting.key.leafName}"
                }
            }
            is LongSetting -> if (raw !is Number)
                return "Expected Long for ${setting.key.leafName}"
            is FloatSetting -> {
                val n = (raw as? Number)?.toFloat()
                    ?: return "Expected Float for ${setting.key.leafName}"
                setting.range?.let {
                    if (n !in it) return "Value $n out of range $it for ${setting.key.leafName}"
                }
            }
            is StringEnumSetting -> {
                val s = raw.toString()
                if (s !in setting.values)
                    return "Expected one of ${setting.values.joinToString(", ")} for ${setting.key.leafName}"
            }
            is IntEnumSetting -> {
                val n = (raw as? Number)?.toInt()
                    ?: return "Expected Int for ${setting.key.leafName}"
                if (n !in setting.values)
                    return "Expected one of ${setting.values.joinToString(", ")} for ${setting.key.leafName}"
            }
            is ColorSetting -> {
                val s = raw as? String
                    ?: return "Expected hex color string (\"#RRGGBB\" or \"#AARRGGBB\") for ${setting.key.leafName}"
                if (ColorHex.parse(s) == null)
                    return "Expected hex color string (\"#RRGGBB\" or \"#AARRGGBB\") for ${setting.key.leafName}"
            }
            is StringSetting -> Unit
            is StringSetSetting -> if (raw !is List<*> && raw !is Set<*>)
                return "Expected list/set of strings for ${setting.key.leafName}"
        }
        return null
    }

    private fun settingCompletions(setting: Setting<*>): List<String> = when (setting) {
        is BooleanSetting -> listOf("true", "false")
        is StringEnumSetting -> setting.values
        is IntEnumSetting -> setting.values.map { it.toString() }
        else -> emptyList()
    }

    private fun validateInline(spec: SchemaNode.InlineSpec, raw: Any?): String? {
        if (raw == null) return null
        return when (spec.kind) {
            SchemaNode.InlineKind.BOOLEAN -> if (raw !is Boolean)
                "Expected Boolean (true/false) for ${spec.name}" else null
            SchemaNode.InlineKind.ENUM -> {
                val s = raw.toString()
                if (s !in spec.enumValues)
                    "Expected one of ${spec.enumValues.joinToString(", ")} for ${spec.name}" else null
            }
            SchemaNode.InlineKind.INT_RANGE -> {
                val n = (raw as? Number)?.toInt()
                    ?: return "Expected number for ${spec.name}"
                spec.intRange?.let {
                    if (n !in it) "Value $n out of range $it for ${spec.name}" else null
                }
            }
            SchemaNode.InlineKind.INT,
            SchemaNode.InlineKind.LONG -> if (raw !is Number)
                "Expected number for ${spec.name}" else null
            SchemaNode.InlineKind.COLOR -> {
                val s = raw as? String
                    ?: return "Expected hex color string (\"#RRGGBB\" or \"#AARRGGBB\") for ${spec.name}"
                if (ColorHex.parse(s) == null)
                    "Expected hex color string (\"#RRGGBB\" or \"#AARRGGBB\") for ${spec.name}"
                else null
            }
            SchemaNode.InlineKind.FLOAT -> {
                val n = (raw as? Number)?.toFloat()
                    ?: return "Expected number for ${spec.name}"
                if (spec.name == "font_size" && "0 = auto" in spec.typeHint &&
                    n != 0f && n !in 6f..36f
                ) {
                    "Value $n out of range 0 or 6..36 for ${spec.name}"
                } else {
                    null
                }
            }
            SchemaNode.InlineKind.STRING -> null
        }
    }
}

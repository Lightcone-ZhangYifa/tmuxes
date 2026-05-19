package com.tmuxes.data.settings

import com.tmuxes.util.ColorHex

/**
 * A single declarative entry that drives every part of the settings stack:
 *
 * - **YAML key path** (`key.path`) — used by [com.tmuxes.data.config.YamlConfigManager]
 *   to read/write the value
 * - **YAML schema** — derived automatically by [com.tmuxes.editor.YamlSchema]
 *   from the same field, so the YAML editor's diagnostics, completion and
 *   error highlighting can never drift out of sync with the actual setting
 * - **UI** — when [ui] is non-null, [com.tmuxes.ui.settings.SettingItemRenderer]
 *   picks the right Composable (Switch / Slider / Dropdown / TextField / ...) by
 *   matching on the runtime type
 *
 * Adding a new setting = declaring one [Setting] and adding it to the
 * relevant [com.tmuxes.data.settings.SettingScreenSpec] group. There is no
 * second list to keep in sync.
 */
sealed interface Setting<T : Any> {
    val key: SettingKey
    val default: T
    val ui: SettingUi?

    /** Coerce a YAML-parsed raw value (Boolean / Number / String / List ...) into [T], or null on type mismatch. */
    fun parseRaw(raw: Any?): T?

    /** Convert [T] back to a SnakeYAML-friendly representation. */
    fun serialize(value: T): Any?

    /** Reject values out of range / not in the enum / etc. */
    fun validate(value: T): SettingValidation
}

/**
 * Dotted YAML path + which file the setting lives in.
 *
 * The list form supports arbitrary nesting (e.g. `editor.errorlens.enabled`)
 * which the previous flat-section schema couldn't express, leading to
 * false-positive "Unknown key" warnings on every nested key.
 */
data class SettingKey(
    val path: List<String>,
    val fileType: SettingFile = SettingFile.GLOBAL
) {
    init { require(path.isNotEmpty()) { "SettingKey.path must not be empty" } }

    val flatPath: String get() = path.joinToString(".")
    val parentPath: List<String> get() = path.dropLast(1)
    val leafName: String get() = path.last()
}

/**
 * Which YAML file the setting resides in. Only [GLOBAL] is currently used;
 * per-widget instance config is handled by
 * [com.tmuxes.data.config.WidgetYamlConfig] (one file per widget instance,
 * not modelled as registered settings).
 */
enum class SettingFile { GLOBAL }

/**
 * UI metadata for a setting. `null` means the setting is internal state
 * (e.g. `app.last_session_server_id`) and never appears in any
 * settings screen.
 *
 * @param iconId Logical icon identifier; mapped to a `material.icons` ImageVector
 *               by [com.tmuxes.ui.settings.SettingIcons]. Putting an ID here
 *               instead of an ImageVector keeps the registry free of Compose
 *               imports so it can be unit-tested on pure JVM.
 */
data class SettingUi(
    val title: String,
    val description: String? = null,
    val iconId: String? = null,
    val unit: String? = null,
    val placeholder: String? = null
)

sealed interface SettingValidation {
    data object Ok : SettingValidation
    data class Error(val message: String) : SettingValidation
}

// =============================================================================
// Concrete Setting implementations
// =============================================================================

class BooleanSetting(
    override val key: SettingKey,
    override val default: Boolean,
    override val ui: SettingUi? = null
) : Setting<Boolean> {
    override fun parseRaw(raw: Any?): Boolean? = (raw as? Boolean)
    override fun serialize(value: Boolean): Any = value
    override fun validate(value: Boolean): SettingValidation = SettingValidation.Ok
}

class IntSetting(
    override val key: SettingKey,
    override val default: Int,
    val range: IntRange? = null,
    override val ui: SettingUi? = null
) : Setting<Int> {
    init { range?.let { require(default in it) { "default $default outside range $it for ${key.flatPath}" } } }
    override fun parseRaw(raw: Any?): Int? = (raw as? Number)?.toInt()
    override fun serialize(value: Int): Any = value
    override fun validate(value: Int): SettingValidation =
        if (range == null || value in range) SettingValidation.Ok
        else SettingValidation.Error("Expected Int in $range, got $value")
}

class LongSetting(
    override val key: SettingKey,
    override val default: Long,
    override val ui: SettingUi? = null
) : Setting<Long> {
    override fun parseRaw(raw: Any?): Long? = (raw as? Number)?.toLong()
    override fun serialize(value: Long): Any = value
    override fun validate(value: Long): SettingValidation = SettingValidation.Ok
}

class FloatSetting(
    override val key: SettingKey,
    override val default: Float,
    val range: ClosedFloatingPointRange<Float>? = null,
    override val ui: SettingUi? = null
) : Setting<Float> {
    init { range?.let { require(default in it) { "default $default outside range $it for ${key.flatPath}" } } }
    override fun parseRaw(raw: Any?): Float? = (raw as? Number)?.toFloat()
    override fun serialize(value: Float): Any = value
    override fun validate(value: Float): SettingValidation =
        if (range == null || value in range) SettingValidation.Ok
        else SettingValidation.Error("Expected Float in $range, got $value")
}

class StringSetting(
    override val key: SettingKey,
    override val default: String,
    val maxLength: Int? = null,
    override val ui: SettingUi? = null
) : Setting<String> {
    override fun parseRaw(raw: Any?): String? = raw?.toString()
    override fun serialize(value: String): Any = value
    override fun validate(value: String): SettingValidation =
        if (maxLength == null || value.length <= maxLength) SettingValidation.Ok
        else SettingValidation.Error("Expected length ≤ $maxLength, got ${value.length}")
}

data class EnumOption<T>(val value: T, val label: String)

class StringEnumSetting(
    override val key: SettingKey,
    override val default: String,
    val options: List<EnumOption<String>>,
    override val ui: SettingUi? = null
) : Setting<String> {
    init {
        require(options.isNotEmpty()) { "StringEnumSetting ${key.flatPath} needs at least one option" }
        require(options.any { it.value == default }) {
            "default '$default' not in options for ${key.flatPath}: ${options.map { it.value }}"
        }
        require(options.map { it.value }.toSet().size == options.size) {
            "Duplicate option values in ${key.flatPath}"
        }
    }
    val values: List<String> get() = options.map { it.value }
    override fun parseRaw(raw: Any?): String? = raw?.toString()
    override fun serialize(value: String): Any = value
    override fun validate(value: String): SettingValidation =
        if (value in values) SettingValidation.Ok
        else SettingValidation.Error("Expected one of ${values}, got '$value'")
}

class IntEnumSetting(
    override val key: SettingKey,
    override val default: Int,
    val options: List<EnumOption<Int>>,
    override val ui: SettingUi? = null
) : Setting<Int> {
    init {
        require(options.isNotEmpty()) { "IntEnumSetting ${key.flatPath} needs at least one option" }
        require(options.any { it.value == default }) {
            "default $default not in options for ${key.flatPath}: ${options.map { it.value }}"
        }
        require(options.map { it.value }.toSet().size == options.size) {
            "Duplicate option values in ${key.flatPath}"
        }
    }
    val values: List<Int> get() = options.map { it.value }
    override fun parseRaw(raw: Any?): Int? = (raw as? Number)?.toInt()
    override fun serialize(value: Int): Any = value
    override fun validate(value: Int): SettingValidation =
        if (value in values) SettingValidation.Ok
        else SettingValidation.Error("Expected one of $values, got $value")
}

class StringSetSetting(
    override val key: SettingKey,
    override val default: Set<String>,
    override val ui: SettingUi? = null
) : Setting<Set<String>> {
    override fun parseRaw(raw: Any?): Set<String>? = when (raw) {
        is List<*> -> raw.map { it.toString() }.toSet()
        is Set<*> -> raw.map { it.toString() }.toSet()
        null -> null
        else -> null
    }
    override fun serialize(value: Set<String>): Any = value.toList()
    override fun validate(value: Set<String>): SettingValidation = SettingValidation.Ok
}

/**
 * Color setting backed by an ARGB Int at runtime and serialized as a
 * human-editable hex string in YAML (`#RRGGBB` or `#AARRGGBB`).
 */
class ColorSetting(
    override val key: SettingKey,
    override val default: Int,
    override val ui: SettingUi? = null
) : Setting<Int> {
    override fun parseRaw(raw: Any?): Int? = ColorHex.parse(raw)
    override fun serialize(value: Int): Any = ColorHex.toYamlString(value)
    override fun validate(value: Int): SettingValidation = SettingValidation.Ok
}

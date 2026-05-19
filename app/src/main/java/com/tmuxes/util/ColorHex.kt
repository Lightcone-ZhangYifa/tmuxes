package com.tmuxes.util

import java.util.Locale

/**
 * Single formatter/parser for colors that cross a human-editable config
 * boundary. Runtime rendering still uses Android/Compose ARGB Ints; YAML and
 * JSON store colors as `#RRGGBB` or `#AARRGGBB` strings.
 */
object ColorHex {
    private val rgbRegex = Regex("^#[0-9A-Fa-f]{6}$")
    private val argbRegex = Regex("^#[0-9A-Fa-f]{8}$")

    fun parse(value: String): Int? {
        val trimmed = value.trim()
        return when {
            rgbRegex.matches(trimmed) -> {
                val rgb = trimmed.drop(1).toLong(16)
                (0xFF000000L or rgb).toInt()
            }
            argbRegex.matches(trimmed) -> trimmed.drop(1).toLong(16).toInt()
            else -> null
        }
    }

    fun parse(raw: Any?): Int? = (raw as? String)?.let(::parse)

    fun parseRgb(value: String): Int? {
        val trimmed = value.trim()
        if (!rgbRegex.matches(trimmed)) return null
        val rgb = trimmed.drop(1).toLong(16)
        return (0xFF000000L or rgb).toInt()
    }

    fun isHexColor(raw: Any?): Boolean = parse(raw) != null

    fun toYamlString(color: Int): String {
        if (color == 0) return "#00000000"
        return if (((color ushr 24) and 0xFF) == 0xFF) {
            toRgbString(color)
        } else {
            toArgbString(color)
        }
    }

    fun toArgbString(color: Int): String =
        String.format(Locale.US, "#%08X", color.toLong() and 0xFFFF_FFFFL)

    fun toRgbString(color: Int): String =
        String.format(Locale.US, "#%06X", color and 0x00FF_FFFF)
}

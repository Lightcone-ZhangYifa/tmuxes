package com.tmuxes.terminal.emulator

import com.tmuxes.util.ColorHex

/**
 * Terminal color palette supporting the full 256-color xterm palette,
 * true-color (24-bit), and several popular color scheme presets.
 *
 * Index layout for the 256-color palette:
 *   0-7     Standard ANSI colors
 *   8-15    Bright ANSI colors
 *   16-231  6x6x6 color cube
 *   232-255 Grayscale ramp
 */
object TerminalColors {

    const val DEFAULT_SCHEME_NAME: String = "catppuccin_mocha"

    // -- Default foreground / background (Catppuccin Mocha inspired) --
    const val DEFAULT_FG: Int = 0xFFCCCCCC.toInt()
    const val DEFAULT_BG: Int = 0xFF1E1E2E.toInt()

    // -- Standard 16 ANSI colors (ARGB) --
    // Indices 0-7: normal, 8-15: bright
    private val ansi16: IntArray = intArrayOf(
        // 0  Black
        0xFF000000.toInt(),
        // 1  Red
        0xFFCC0000.toInt(),
        // 2  Green
        0xFF00CC00.toInt(),
        // 3  Yellow
        0xFFCCCC00.toInt(),
        // 4  Blue
        0xFF0000CC.toInt(),
        // 5  Magenta
        0xFFCC00CC.toInt(),
        // 6  Cyan
        0xFF00CCCC.toInt(),
        // 7  White
        0xFFCCCCCC.toInt(),
        // 8  Bright Black (Gray)
        0xFF666666.toInt(),
        // 9  Bright Red
        0xFFFF4444.toInt(),
        // 10 Bright Green
        0xFF44FF44.toInt(),
        // 11 Bright Yellow
        0xFFFFFF44.toInt(),
        // 12 Bright Blue
        0xFF4444FF.toInt(),
        // 13 Bright Magenta
        0xFFFF44FF.toInt(),
        // 14 Bright Cyan
        0xFF44FFFF.toInt(),
        // 15 Bright White
        0xFFFFFFFF.toInt()
    )

    // Full 256-color palette, lazily built once.
    private val palette256: IntArray by lazy { buildPalette256() }

    private fun buildPalette256(): IntArray {
        val palette = IntArray(256)

        // 0-15: copy ANSI 16
        for (i in 0..15) {
            palette[i] = ansi16[i]
        }

        // 16-231: 6x6x6 color cube
        // Each axis value maps: 0->0, 1->95, 2->135, 3->175, 4->215, 5->255
        val cubeValues = intArrayOf(0, 95, 135, 175, 215, 255)
        for (r in 0..5) {
            for (g in 0..5) {
                for (b in 0..5) {
                    val index = 16 + r * 36 + g * 6 + b
                    palette[index] = argb(255, cubeValues[r], cubeValues[g], cubeValues[b])
                }
            }
        }

        // 232-255: grayscale ramp (24 shades from 8 to 238)
        for (i in 0..23) {
            val level = 8 + i * 10
            palette[232 + i] = argb(255, level, level, level)
        }

        return palette
    }

    /**
     * Returns the ARGB color for a 256-color palette index (0-255).
     * Out-of-range indices return [DEFAULT_FG].
     */
    fun get256Color(index: Int): Int {
        return if (index in 0..255) palette256[index] else DEFAULT_FG
    }

    /**
     * Packs r, g, b (0-255 each) into a fully-opaque ARGB integer.
     */
    fun getTrueColor(r: Int, g: Int, b: Int): Int {
        return argb(255, r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }

    /** Helper: pack ARGB components into a single Int. */
    private fun argb(a: Int, r: Int, g: Int, b: Int): Int {
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    // ---------------------------------------------------------------
    // Color scheme presets
    // ---------------------------------------------------------------
    // Each preset is a map of the 16 ANSI colors plus foreground/background.

    data class ColorScheme(
        val name: String,
        val foreground: Int,
        val background: Int,
        val ansi: IntArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ColorScheme) return false
            return name == other.name
        }

        override fun hashCode(): Int = name.hashCode()
    }

    val MONOKAI = ColorScheme(
        name = "Monokai",
        foreground = 0xFFF8F8F2.toInt(),
        background = 0xFF272822.toInt(),
        ansi = intArrayOf(
            0xFF272822.toInt(), 0xFFF92672.toInt(), 0xFFA6E22E.toInt(), 0xFFF4BF75.toInt(),
            0xFF66D9EF.toInt(), 0xFFAE81FF.toInt(), 0xFFA1EFE4.toInt(), 0xFFF8F8F2.toInt(),
            0xFF75715E.toInt(), 0xFFF92672.toInt(), 0xFFA6E22E.toInt(), 0xFFF4BF75.toInt(),
            0xFF66D9EF.toInt(), 0xFFAE81FF.toInt(), 0xFFA1EFE4.toInt(), 0xFFF9F8F5.toInt()
        )
    )

    val SOLARIZED_DARK = ColorScheme(
        name = "Solarized Dark",
        foreground = 0xFF839496.toInt(),
        background = 0xFF002B36.toInt(),
        ansi = intArrayOf(
            0xFF073642.toInt(), 0xFFDC322F.toInt(), 0xFF859900.toInt(), 0xFFB58900.toInt(),
            0xFF268BD2.toInt(), 0xFFD33682.toInt(), 0xFF2AA198.toInt(), 0xFFEEE8D5.toInt(),
            0xFF002B36.toInt(), 0xFFCB4B16.toInt(), 0xFF586E75.toInt(), 0xFF657B83.toInt(),
            0xFF839496.toInt(), 0xFF6C71C4.toInt(), 0xFF93A1A1.toInt(), 0xFFFDF6E3.toInt()
        )
    )

    val DRACULA = ColorScheme(
        name = "Dracula",
        foreground = 0xFFF8F8F2.toInt(),
        background = 0xFF282A36.toInt(),
        ansi = intArrayOf(
            0xFF21222C.toInt(), 0xFFFF5555.toInt(), 0xFF50FA7B.toInt(), 0xFFF1FA8C.toInt(),
            0xFFBD93F9.toInt(), 0xFFFF79C6.toInt(), 0xFF8BE9FD.toInt(), 0xFFF8F8F2.toInt(),
            0xFF6272A4.toInt(), 0xFFFF6E6E.toInt(), 0xFF69FF94.toInt(), 0xFFFFFFA5.toInt(),
            0xFFD6ACFF.toInt(), 0xFFFF92DF.toInt(), 0xFFA4FFFF.toInt(), 0xFFFFFFFF.toInt()
        )
    )

    val NORD = ColorScheme(
        name = "Nord",
        foreground = 0xFFD8DEE9.toInt(),
        background = 0xFF2E3440.toInt(),
        ansi = intArrayOf(
            0xFF3B4252.toInt(), 0xFFBF616A.toInt(), 0xFFA3BE8C.toInt(), 0xFFEBCB8B.toInt(),
            0xFF81A1C1.toInt(), 0xFFB48EAD.toInt(), 0xFF88C0D0.toInt(), 0xFFE5E9F0.toInt(),
            0xFF4C566A.toInt(), 0xFFBF616A.toInt(), 0xFFA3BE8C.toInt(), 0xFFEBCB8B.toInt(),
            0xFF81A1C1.toInt(), 0xFFB48EAD.toInt(), 0xFF8FBCBB.toInt(), 0xFFECEFF4.toInt()
        )
    )

    val CATPPUCCIN_MOCHA = ColorScheme(
        name = "Catppuccin Mocha",
        foreground = 0xFFCDD6F4.toInt(),
        background = 0xFF1E1E2E.toInt(),
        ansi = intArrayOf(
            0xFF45475A.toInt(), 0xFFF38BA8.toInt(), 0xFFA6E3A1.toInt(), 0xFFF9E2AF.toInt(),
            0xFF89B4FA.toInt(), 0xFFF5C2E7.toInt(), 0xFF94E2D5.toInt(), 0xFFBAC2DE.toInt(),
            0xFF585B70.toInt(), 0xFFF38BA8.toInt(), 0xFFA6E3A1.toInt(), 0xFFF9E2AF.toInt(),
            0xFF89B4FA.toInt(), 0xFFF5C2E7.toInt(), 0xFF94E2D5.toInt(), 0xFFA6ADC8.toInt()
        )
    )

    val GRUVBOX_DARK = ColorScheme(
        name = "Gruvbox Dark",
        foreground = 0xFFEBDBB2.toInt(),
        background = 0xFF282828.toInt(),
        ansi = intArrayOf(
            0xFF282828.toInt(), 0xFFCC241D.toInt(), 0xFF98971A.toInt(), 0xFFD79921.toInt(),
            0xFF458588.toInt(), 0xFFB16286.toInt(), 0xFF689D6A.toInt(), 0xFFA89984.toInt(),
            0xFF928374.toInt(), 0xFFFB4934.toInt(), 0xFFB8BB26.toInt(), 0xFFFABD2F.toInt(),
            0xFF83A598.toInt(), 0xFFD3869B.toInt(), 0xFF8EC07C.toInt(), 0xFFEBDBB2.toInt()
        )
    )

    val ONE_DARK = ColorScheme(
        name = "One Dark",
        foreground = 0xFFABB2BF.toInt(),
        background = 0xFF282C34.toInt(),
        ansi = intArrayOf(
            0xFF282C34.toInt(), 0xFFE06C75.toInt(), 0xFF98C379.toInt(), 0xFFE5C07B.toInt(),
            0xFF61AFEF.toInt(), 0xFFC678DD.toInt(), 0xFF56B6C2.toInt(), 0xFFABB2BF.toInt(),
            0xFF545862.toInt(), 0xFFE06C75.toInt(), 0xFF98C379.toInt(), 0xFFE5C07B.toInt(),
            0xFF61AFEF.toInt(), 0xFFC678DD.toInt(), 0xFF56B6C2.toInt(), 0xFFFFFFFF.toInt()
        )
    )

    /** All built-in presets for iteration. */
    val ALL_PRESETS: List<ColorScheme> = listOf(
        MONOKAI, SOLARIZED_DARK, DRACULA, NORD,
        CATPPUCCIN_MOCHA, GRUVBOX_DARK, ONE_DARK
    )

    /**
     * Resolves a color scheme by its lowercase key name.
     *
     * Recognized keys: "monokai", "solarized_dark", "dracula", "nord", "one_dark".
     * Returns [CATPPUCCIN_MOCHA] for unrecognized names.
     */
    fun getSchemeByName(name: String): ColorScheme {
        return when (name.lowercase()) {
            "monokai" -> MONOKAI
            "solarized_dark", "solarized" -> SOLARIZED_DARK
            "dracula" -> DRACULA
            "nord" -> NORD
            "one_dark", "onedark" -> ONE_DARK
            "catppuccin", "catppuccin_mocha" -> CATPPUCCIN_MOCHA
            "gruvbox", "gruvbox_dark" -> GRUVBOX_DARK
            else -> CATPPUCCIN_MOCHA
        }
    }

    fun resolveScheme(name: String, customSchemesJson: String = ""): ColorScheme {
        val customScheme = getCustomSchemes(customSchemesJson)
            .firstOrNull { it.name.equals(name, ignoreCase = true) }
        return customScheme ?: getSchemeByName(name)
    }

    // ---------------------------------------------------------------
    // JSON serialization / deserialization
    // ---------------------------------------------------------------

    /**
     * Serialize a [ColorScheme] to a JSON string.
     *
     * Uses manual string building (no `org.json`) so the logic is testable
     * in plain JUnit without the Android framework.
     *
     * Format:
     * ```json
     * {"name":"...","foreground":"#RRGGBB","background":"#RRGGBB",
     *  "ansi":["#RRGGBB",...]}
     * ```
     */
    fun toJson(scheme: ColorScheme): String {
        val sb = StringBuilder()
        sb.append("{\"name\":\"")
        sb.append(escapeJsonString(scheme.name))
        sb.append("\",\"foreground\":\"")
        sb.append(ColorHex.toRgbString(scheme.foreground))
        sb.append("\",\"background\":\"")
        sb.append(ColorHex.toRgbString(scheme.background))
        sb.append("\",\"ansi\":[")
        for (i in scheme.ansi.indices) {
            if (i > 0) sb.append(',')
            sb.append('"')
            sb.append(ColorHex.toRgbString(scheme.ansi[i]))
            sb.append('"')
        }
        sb.append("]}")
        return sb.toString()
    }

    /** Minimal JSON string escaping for scheme names. */
    private fun escapeJsonString(s: String): String {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
    }

    /**
     * Parse a JSON string into a [ColorScheme].
     * Returns null if the JSON is invalid or missing required fields.
     *
     * Uses a lightweight manual parser so it works in plain JUnit tests.
     */
    fun fromJson(json: String): ColorScheme? {
        return try {
            val trimmed = json.trim()
            if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null

            val name = extractJsonString(trimmed, "name") ?: return null
            val fgHex = extractJsonString(trimmed, "foreground") ?: return null
            val bgHex = extractJsonString(trimmed, "background") ?: return null
            val fg = ColorHex.parseRgb(fgHex) ?: return null
            val bg = ColorHex.parseRgb(bgHex) ?: return null

            val ansiList = extractJsonStringArray(trimmed, "ansi") ?: return null
            if (ansiList.size != 16) return null
            val ansi = IntArray(16) { i ->
                ColorHex.parseRgb(ansiList[i]) ?: return null
            }
            ColorScheme(name = name, foreground = fg, background = bg, ansi = ansi)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parse a JSON array string into a list of [ColorScheme].
     * Returns an empty list for blank/invalid input.
     */
    fun getCustomSchemes(jsonArray: String): List<ColorScheme> {
        if (jsonArray.isBlank()) return emptyList()
        return try {
            splitJsonArrayObjects(jsonArray).mapNotNull { fromJson(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Serialize a list of [ColorScheme] to a JSON array string.
     */
    fun customSchemesToJson(schemes: List<ColorScheme>): String {
        return "[" + schemes.joinToString(",") { toJson(it) } + "]"
    }

    // ---------------------------------------------------------------
    // Lightweight JSON helpers (no org.json dependency)
    // ---------------------------------------------------------------

    /** Extract a string value for a key like `"key":"value"` from a JSON object string. */
    private fun extractJsonString(json: String, key: String): String? {
        val pattern = "\"$key\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"".toRegex()
        val match = pattern.find(json) ?: return null
        return match.groupValues[1]
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }

    /** Extract an array of strings for a key like `"key":["a","b",...]`. */
    private fun extractJsonStringArray(json: String, key: String): List<String>? {
        // Find the start of "key":[
        val keyIdx = json.indexOf("\"$key\"")
        if (keyIdx < 0) return null
        val colonIdx = json.indexOf(':', keyIdx + key.length + 2)
        if (colonIdx < 0) return null
        val bracketIdx = json.indexOf('[', colonIdx)
        if (bracketIdx < 0) return null
        val closeBracket = findMatchingBracket(json, bracketIdx) ?: return null
        val arrayContent = json.substring(bracketIdx + 1, closeBracket).trim()
        if (arrayContent.isEmpty()) return emptyList()

        val result = mutableListOf<String>()
        val stringPattern = "\"((?:[^\"\\\\]|\\\\.)*)\"".toRegex()
        for (match in stringPattern.findAll(arrayContent)) {
            result.add(match.groupValues[1].replace("\\\"", "\"").replace("\\\\", "\\"))
        }
        return result
    }

    /** Find the matching `]` for a `[` at position [start], respecting nesting. */
    private fun findMatchingBracket(s: String, start: Int): Int? {
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until s.length) {
            val c = s[i]
            if (escaped) { escaped = false; continue }
            if (c == '\\') { escaped = true; continue }
            if (c == '"') { inString = !inString; continue }
            if (inString) continue
            if (c == '[') depth++
            else if (c == ']') { depth--; if (depth == 0) return i }
        }
        return null
    }

    /**
     * Split a JSON array of objects `[{...},{...},...]` into individual
     * object strings. Handles nested braces and quoted strings.
     */
    private fun splitJsonArrayObjects(jsonArray: String): List<String> {
        val trimmed = jsonArray.trim()
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return emptyList()
        val inner = trimmed.substring(1, trimmed.length - 1).trim()
        if (inner.isEmpty()) return emptyList()

        val objects = mutableListOf<String>()
        var depth = 0
        var inString = false
        var escaped = false
        var objStart = -1

        for (i in inner.indices) {
            val c = inner[i]
            if (escaped) { escaped = false; continue }
            if (c == '\\') { escaped = true; continue }
            if (c == '"') { inString = !inString; continue }
            if (inString) continue

            when (c) {
                '{' -> {
                    if (depth == 0) objStart = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && objStart >= 0) {
                        objects.add(inner.substring(objStart, i + 1))
                        objStart = -1
                    }
                }
            }
        }
        return objects
    }
}

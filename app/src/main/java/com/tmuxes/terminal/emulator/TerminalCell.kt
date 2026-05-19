package com.tmuxes.terminal.emulator

/**
 * Packed cell encoding using two Longs per cell.
 *
 * Long 1 (packed):
 *   bits 63-43 (21 bits): Unicode code point (U+0000 to U+10FFFF)
 *   bit 42: bold
 *   bit 41: italic
 *   bit 40: underline
 *   bit 39: strikethrough
 *   bit 38: dim
 *   bit 37: widePlaceholder
 *   bits 36-32: reserved
 *   bits 31-0: foreground color (ARGB)
 *
 * Long 2 (color):
 *   bits 63-32: background color (ARGB)
 *   bits 31-0: reserved
 */
object CellEncoding {
    const val DEFAULT_FG: Int = 0xFFCCCCCC.toInt()
    const val DEFAULT_BG: Int = 0xFF1E1E2E.toInt()

    private const val CP_SHIFT = 43
    private const val BOLD_BIT = 42
    private const val ITALIC_BIT = 41
    private const val UNDERLINE_BIT = 40
    private const val STRIKETHROUGH_BIT = 39
    private const val DIM_BIT = 38
    private const val WIDE_PLACEHOLDER_BIT = 37
    private const val BG_SHIFT = 32

    val EMPTY_PACKED: Long = encode(' '.code, DEFAULT_FG, bold = false, italic = false, underline = false, strikethrough = false, dim = false, widePlaceholder = false)
    val EMPTY_COLOR: Long = encodeBg(DEFAULT_BG)

    fun encode(
        codePoint: Int, fg: Int,
        bold: Boolean, italic: Boolean, underline: Boolean,
        strikethrough: Boolean, dim: Boolean, widePlaceholder: Boolean
    ): Long {
        var v = (codePoint.toLong() and 0x1FFFFF) shl CP_SHIFT
        if (bold) v = v or (1L shl BOLD_BIT)
        if (italic) v = v or (1L shl ITALIC_BIT)
        if (underline) v = v or (1L shl UNDERLINE_BIT)
        if (strikethrough) v = v or (1L shl STRIKETHROUGH_BIT)
        if (dim) v = v or (1L shl DIM_BIT)
        if (widePlaceholder) v = v or (1L shl WIDE_PLACEHOLDER_BIT)
        v = v or (fg.toLong() and 0xFFFFFFFFL)
        return v
    }

    fun encodeBg(bg: Int): Long = (bg.toLong() and 0xFFFFFFFFL) shl BG_SHIFT

    fun codePoint(packed: Long): Int = ((packed ushr CP_SHIFT) and 0x1FFFFF).toInt()
    fun fg(packed: Long): Int = packed.toInt()  // lower 32 bits
    fun bg(color: Long): Int = (color ushr BG_SHIFT).toInt()
    fun isBold(packed: Long): Boolean = (packed and (1L shl BOLD_BIT)) != 0L
    fun isItalic(packed: Long): Boolean = (packed and (1L shl ITALIC_BIT)) != 0L
    fun isUnderline(packed: Long): Boolean = (packed and (1L shl UNDERLINE_BIT)) != 0L
    fun isStrikethrough(packed: Long): Boolean = (packed and (1L shl STRIKETHROUGH_BIT)) != 0L
    fun isDim(packed: Long): Boolean = (packed and (1L shl DIM_BIT)) != 0L
    fun isWidePlaceholder(packed: Long): Boolean = (packed and (1L shl WIDE_PLACEHOLDER_BIT)) != 0L
}

/**
 * Read-only view of a terminal line's packed data.
 */
class TerminalLine(
    val packed: LongArray,
    val color: LongArray,
    val length: Int
) {
    fun codePoint(col: Int): Int = CellEncoding.codePoint(packed[col])
    fun fg(col: Int): Int = CellEncoding.fg(packed[col])
    fun bg(col: Int): Int = CellEncoding.bg(color[col])
    fun isBold(col: Int): Boolean = CellEncoding.isBold(packed[col])
    fun isItalic(col: Int): Boolean = CellEncoding.isItalic(packed[col])
    fun isUnderline(col: Int): Boolean = CellEncoding.isUnderline(packed[col])
    fun isStrikethrough(col: Int): Boolean = CellEncoding.isStrikethrough(packed[col])
    fun isDim(col: Int): Boolean = CellEncoding.isDim(packed[col])
    fun isWidePlaceholder(col: Int): Boolean = CellEncoding.isWidePlaceholder(packed[col])
    fun charValue(col: Int): Char {
        val cp = codePoint(col)
        return if (cp in 0..0xFFFF) cp.toChar() else '?'  // Fallback for supplementary plane until renderer handles it
    }
}

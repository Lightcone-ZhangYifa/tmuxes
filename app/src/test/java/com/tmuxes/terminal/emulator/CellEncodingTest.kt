package com.tmuxes.terminal.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CellEncodingTest {

    private fun enc(
        cp: Int = ' '.code, fg: Int = CellEncoding.DEFAULT_FG,
        bold: Boolean = false, italic: Boolean = false,
        underline: Boolean = false, strikethrough: Boolean = false,
        dim: Boolean = false, widePlaceholder: Boolean = false
    ) = CellEncoding.encode(cp, fg, bold, italic, underline, strikethrough, dim, widePlaceholder)

    // --- Code point round-trip ---

    @Test fun `code point 0 round trips`() = assertEquals(0, CellEncoding.codePoint(enc(cp = 0)))
    @Test fun `ASCII A round trips`() = assertEquals('A'.code, CellEncoding.codePoint(enc(cp = 'A'.code)))
    @Test fun `BMP max U+FFFF round trips`() = assertEquals(0xFFFF, CellEncoding.codePoint(enc(cp = 0xFFFF)))
    @Test fun `supplementary U+1F600 round trips`() = assertEquals(0x1F600, CellEncoding.codePoint(enc(cp = 0x1F600)))
    @Test fun `max 21-bit U+10FFFF round trips`() = assertEquals(0x10FFFF, CellEncoding.codePoint(enc(cp = 0x10FFFF)))

    // --- Foreground color round-trip ---

    @Test fun `default fg round trips`() = assertEquals(CellEncoding.DEFAULT_FG, CellEncoding.fg(enc()))
    @Test fun `custom fg round trips`() = assertEquals(0xFF123456.toInt(), CellEncoding.fg(enc(fg = 0xFF123456.toInt())))
    @Test fun `zero fg round trips`() = assertEquals(0, CellEncoding.fg(enc(fg = 0)))
    @Test fun `all-bits fg round trips`() = assertEquals(0xFFFFFFFF.toInt(), CellEncoding.fg(enc(fg = 0xFFFFFFFF.toInt())))

    // --- Background color round-trip ---

    @Test fun `default bg round trips`() = assertEquals(CellEncoding.DEFAULT_BG, CellEncoding.bg(CellEncoding.EMPTY_COLOR))
    @Test fun `custom bg round trips`() {
        val bg = 0xFF654321.toInt()
        assertEquals(bg, CellEncoding.bg(CellEncoding.encodeBg(bg)))
    }
    @Test fun `zero bg round trips`() = assertEquals(0, CellEncoding.bg(CellEncoding.encodeBg(0)))

    // --- Individual attribute flags ---

    @Test fun `bold flag isolated`() {
        val p = enc(bold = true)
        assertTrue(CellEncoding.isBold(p))
        assertFalse(CellEncoding.isItalic(p))
        assertFalse(CellEncoding.isUnderline(p))
        assertFalse(CellEncoding.isStrikethrough(p))
        assertFalse(CellEncoding.isDim(p))
        assertFalse(CellEncoding.isWidePlaceholder(p))
    }

    @Test fun `italic flag isolated`() {
        val p = enc(italic = true)
        assertTrue(CellEncoding.isItalic(p))
        assertFalse(CellEncoding.isBold(p))
    }

    @Test fun `underline flag isolated`() {
        val p = enc(underline = true)
        assertTrue(CellEncoding.isUnderline(p))
        assertFalse(CellEncoding.isBold(p))
    }

    @Test fun `strikethrough flag isolated`() {
        val p = enc(strikethrough = true)
        assertTrue(CellEncoding.isStrikethrough(p))
        assertFalse(CellEncoding.isUnderline(p))
    }

    @Test fun `dim flag isolated`() {
        val p = enc(dim = true)
        assertTrue(CellEncoding.isDim(p))
        assertFalse(CellEncoding.isBold(p))
    }

    @Test fun `widePlaceholder flag isolated`() {
        val p = enc(widePlaceholder = true)
        assertTrue(CellEncoding.isWidePlaceholder(p))
        assertFalse(CellEncoding.isDim(p))
    }

    // --- All attributes simultaneously ---

    @Test fun `all attributes set simultaneously`() {
        val p = enc(cp = 'X'.code, fg = 0xFF010203.toInt(),
            bold = true, italic = true, underline = true,
            strikethrough = true, dim = true, widePlaceholder = true)
        assertEquals('X'.code, CellEncoding.codePoint(p))
        assertEquals(0xFF010203.toInt(), CellEncoding.fg(p))
        assertTrue(CellEncoding.isBold(p))
        assertTrue(CellEncoding.isItalic(p))
        assertTrue(CellEncoding.isUnderline(p))
        assertTrue(CellEncoding.isStrikethrough(p))
        assertTrue(CellEncoding.isDim(p))
        assertTrue(CellEncoding.isWidePlaceholder(p))
    }

    // --- EMPTY constants ---

    @Test fun `EMPTY_PACKED has space code point`() =
        assertEquals(' '.code, CellEncoding.codePoint(CellEncoding.EMPTY_PACKED))

    @Test fun `EMPTY_PACKED has default fg`() =
        assertEquals(CellEncoding.DEFAULT_FG, CellEncoding.fg(CellEncoding.EMPTY_PACKED))

    @Test fun `EMPTY_PACKED has no attributes`() {
        val p = CellEncoding.EMPTY_PACKED
        assertFalse(CellEncoding.isBold(p))
        assertFalse(CellEncoding.isItalic(p))
        assertFalse(CellEncoding.isUnderline(p))
        assertFalse(CellEncoding.isStrikethrough(p))
        assertFalse(CellEncoding.isDim(p))
        assertFalse(CellEncoding.isWidePlaceholder(p))
    }

    // --- Cross-field independence ---

    @Test fun `max fg does not corrupt code point or attributes`() {
        val p = enc(cp = 'A'.code, fg = 0xFFFFFFFF.toInt())
        assertEquals('A'.code, CellEncoding.codePoint(p))
        assertFalse(CellEncoding.isBold(p))
        assertFalse(CellEncoding.isItalic(p))
    }

    @Test fun `max code point does not corrupt fg`() {
        val p = enc(cp = 0x10FFFF, fg = 0)
        assertEquals(0x10FFFF, CellEncoding.codePoint(p))
        assertEquals(0, CellEncoding.fg(p))
    }
}

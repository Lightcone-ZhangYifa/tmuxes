package com.tmuxes.terminal.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for wide (CJK) character handling in the terminal emulator.
 */
class TerminalEmulatorWideCharTest {

    @Test
    fun `isWideChar recognizes CJK unified ideographs`() {
        assertTrue(TerminalEmulator.isWideChar('\u4F60')) // CJK ideograph U+4F60
        assertTrue(TerminalEmulator.isWideChar('\u597D')) // CJK ideograph U+597D
        assertTrue(TerminalEmulator.isWideChar('\u4E16')) // CJK ideograph U+4E16
        assertTrue(TerminalEmulator.isWideChar('\u754C')) // CJK ideograph U+754C
    }

    @Test
    fun `isWideChar recognizes fullwidth forms`() {
        assertTrue(TerminalEmulator.isWideChar('\uFF01')) // fullwidth exclamation mark
        assertTrue(TerminalEmulator.isWideChar('\uFF10')) // fullwidth digit zero
    }

    @Test
    fun `isWideChar recognizes hiragana and katakana`() {
        assertTrue(TerminalEmulator.isWideChar('\u3042')) // hiragana letter A
        assertTrue(TerminalEmulator.isWideChar('\u30A2')) // katakana letter A
    }

    @Test
    fun `isWideChar rejects ASCII`() {
        assertFalse(TerminalEmulator.isWideChar('A'))
        assertFalse(TerminalEmulator.isWideChar(' '))
        assertFalse(TerminalEmulator.isWideChar('~'))
    }

    @Test
    fun `wide char advances cursor by 2`() {
        val emu = TerminalEmulator(rows = 24, columns = 80)
        // Feed the UTF-8 bytes for U+4F60: 0xE4 0xBD 0xA0.
        emu.processInput(byteArrayOf(0xE4.toByte(), 0xBD.toByte(), 0xA0.toByte()))

        // Cursor should be at column 2 (advanced by 2)
        assertEquals(2, emu.cursorCol)

        // Cell at (0,0) should have the character
        val (packed0, _) = emu.buffer.getCellPacked(0, 0)
        assertEquals('\u4F60'.code, CellEncoding.codePoint(packed0))

        // Cell at (0,1) should be a wide placeholder
        val (packed1, _) = emu.buffer.getCellPacked(0, 1)
        assertTrue(CellEncoding.isWidePlaceholder(packed1))
    }

    @Test
    fun `ASCII char advances cursor by 1`() {
        val emu = TerminalEmulator(rows = 24, columns = 80)
        emu.processInput("A".toByteArray())

        assertEquals(1, emu.cursorCol)
        val (packed0, _) = emu.buffer.getCellPacked(0, 0)
        assertEquals('A'.code, CellEncoding.codePoint(packed0))
        val (packed1, _) = emu.buffer.getCellPacked(0, 1)
        assertFalse(CellEncoding.isWidePlaceholder(packed1))
    }

    @Test
    fun `mixed ASCII and CJK display correctly`() {
        val emu = TerminalEmulator(rows = 24, columns = 80)
        // A + U+4F60 + B consumes 1 + 2 + 1 terminal columns.
        val input = "A\u4F60B".toByteArray(Charsets.UTF_8)
        emu.processInput(input)

        assertEquals(4, emu.cursorCol)
        val (packed0, _) = emu.buffer.getCellPacked(0, 0)
        assertEquals('A'.code, CellEncoding.codePoint(packed0))
        val (packed1, _) = emu.buffer.getCellPacked(0, 1)
        assertEquals('\u4F60'.code, CellEncoding.codePoint(packed1))
        val (packed2, _) = emu.buffer.getCellPacked(0, 2)
        assertTrue(CellEncoding.isWidePlaceholder(packed2))
        val (packed3, _) = emu.buffer.getCellPacked(0, 3)
        assertEquals('B'.code, CellEncoding.codePoint(packed3))
    }

    @Test
    fun `wide char at last column wraps to next line`() {
        val emu = TerminalEmulator(rows = 24, columns = 4)
        // Place cursor at column 3 (last position where wide char won't fit)
        emu.processInput("ABC".toByteArray()) // cursor at col 3

        // Now feed a wide char — it should wrap
        emu.processInput("\u4F60".toByteArray(Charsets.UTF_8))

        // The wide char should NOT be at column 3 (no room for 2 cells)
        // Instead it wraps: cursorCol advances past columns, then wraps to col 0 of next row
        // After wrap + placing the char, cursor should be at row 1, col 2
        assertEquals(1, emu.cursorRow)
        assertEquals(2, emu.cursorCol)
        val (packed0, _) = emu.buffer.getCellPacked(1, 0)
        assertEquals('\u4F60'.code, CellEncoding.codePoint(packed0))
        val (packed1, _) = emu.buffer.getCellPacked(1, 1)
        assertTrue(CellEncoding.isWidePlaceholder(packed1))
    }

    @Test
    fun `placeholder cell has default appearance`() {
        val emu = TerminalEmulator(rows = 24, columns = 80)
        emu.processInput("\u4F60".toByteArray(Charsets.UTF_8))

        val (packed1, _) = emu.buffer.getCellPacked(0, 1)
        assertTrue(CellEncoding.isWidePlaceholder(packed1))
        assertFalse(CellEncoding.isBold(packed1))
        assertFalse(CellEncoding.isItalic(packed1))
    }
}

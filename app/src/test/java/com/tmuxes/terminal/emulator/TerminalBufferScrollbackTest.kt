package com.tmuxes.terminal.emulator

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalBufferScrollbackTest {

    private fun buf(rows: Int = 4, cols: Int = 5, scrollback: Int = 100) =
        TerminalBuffer(rows, cols, scrollback)

    private fun encChar(ch: Char): Long =
        CellEncoding.encode(ch.code, CellEncoding.DEFAULT_FG,
            bold = false, italic = false, underline = false,
            strikethrough = false, dim = false, widePlaceholder = false)

    private val defaultColor = CellEncoding.EMPTY_COLOR

    private fun TerminalBuffer.fillRow(row: Int, ch: Char) {
        val packed = encChar(ch)
        for (col in 0 until columns) setCell(row, col, packed, defaultColor)
    }

    // -----------------------------------------------------------------
    // maxScrollback = 100: push 200, only 100 retained
    // -----------------------------------------------------------------

    @Test
    fun `maxScrollback 100 retains only 100 lines after pushing 200`() {
        val b = buf(rows = 1, cols = 3, scrollback = 100)
        for (i in 0 until 200) {
            b.fillRow(0, 'A' + (i % 26))
            b.scrollUp(1)
        }
        assertEquals(100, b.getScrollbackLineCount())
        // Most recent scrollback line (index 0) should be the last line pushed
        // which was line 199 -> char 'A' + (199 % 26) = 'A' + 17 = 'R'
        val mostRecent = b.getScrollbackLine(0)
        assertEquals('R'.code, mostRecent.codePoint(0))
    }

    // -----------------------------------------------------------------
    // maxScrollback = 1: only the most recent line is kept
    // -----------------------------------------------------------------

    @Test
    fun `maxScrollback 1 retains only the most recent scrollback line`() {
        val b = buf(rows = 2, cols = 3, scrollback = 1)
        b.fillRow(0, 'X')
        b.fillRow(1, 'Y')
        b.scrollUp(1) // pushes 'X', screen becomes [Y, blank]
        assertEquals(1, b.getScrollbackLineCount())
        assertEquals('X'.code, b.getScrollbackLine(0).codePoint(0))

        b.fillRow(0, 'Z')
        b.scrollUp(1) // pushes 'Y' (was row 0 after shift), overwrites 'X'
        assertEquals(1, b.getScrollbackLineCount())
        // The ring should have overwritten the previous entry
        val line = b.getScrollbackLine(0)
        // After first scrollUp: screen = [Y, blank]. Then fillRow(0,'Z') -> [Z, blank].
        // scrollUp pushes row 0 = 'Z' into scrollback, screen = [blank, blank].
        assertEquals('Z'.code, line.codePoint(0))
    }

    // -----------------------------------------------------------------
    // maxScrollback = 50000: can hold 50K lines
    // -----------------------------------------------------------------

    @Test
    fun `maxScrollback 50000 can hold 50K lines`() {
        val b = buf(rows = 1, cols = 1, scrollback = 50_000)
        for (i in 0 until 50_000) {
            b.fillRow(0, 'A')
            b.scrollUp(1)
        }
        assertEquals(50_000, b.getScrollbackLineCount())
        // All 50K lines should be accessible
        assertEquals('A'.code, b.getScrollbackLine(0).codePoint(0))
        assertEquals('A'.code, b.getScrollbackLine(49_999).codePoint(0))
    }

    // -----------------------------------------------------------------
    // Default maxScrollback = 10000
    // -----------------------------------------------------------------

    @Test
    fun `default maxScrollback is 10000`() {
        val b = TerminalBuffer(4, 5)
        assertEquals(TerminalBuffer.DEFAULT_SCROLLBACK, b.maxScrollback)
        assertEquals(10_000, b.maxScrollback)
    }

    @Test
    fun `default maxScrollback caps at 10000 lines`() {
        val b = TerminalBuffer(1, 1)
        for (i in 0 until 15_000) {
            b.fillRow(0, 'A')
            b.scrollUp(1)
        }
        assertEquals(10_000, b.getScrollbackLineCount())
    }

    // -----------------------------------------------------------------
    // maxScrollback = 0: no scrollback stored
    // -----------------------------------------------------------------

    @Test
    fun `maxScrollback 0 stores no scrollback`() {
        val b = buf(rows = 3, cols = 3, scrollback = 0)
        b.fillRow(0, 'A')
        b.fillRow(1, 'B')
        b.fillRow(2, 'C')
        b.scrollUp(1)
        assertEquals(0, b.getScrollbackLineCount())
    }

    @Test
    fun `maxScrollback 0 stores no scrollback after many scrolls`() {
        val b = buf(rows = 2, cols = 2, scrollback = 0)
        for (i in 0 until 100) {
            b.fillRow(0, 'X')
            b.scrollUp(1)
        }
        assertEquals(0, b.getScrollbackLineCount())
    }

    @Test
    fun `maxScrollback 0 getScrollbackLine returns blank`() {
        val b = buf(rows = 2, cols = 3, scrollback = 0)
        b.fillRow(0, 'A')
        b.scrollUp(1)
        // Should return a blank line since no scrollback is stored
        val line = b.getScrollbackLine(0)
        assertEquals(' '.code, line.codePoint(0))
    }

    // -----------------------------------------------------------------
    // TerminalEmulator passes maxScrollback through
    // -----------------------------------------------------------------

    @Test
    fun `TerminalEmulator passes maxScrollback to buffer`() {
        val emulator = TerminalEmulator(rows = 4, columns = 10, maxScrollback = 500)
        assertEquals(500, emulator.buffer.maxScrollback)
    }

    @Test
    fun `TerminalEmulator default maxScrollback matches buffer default`() {
        val emulator = TerminalEmulator(rows = 4, columns = 10)
        assertEquals(TerminalBuffer.DEFAULT_SCROLLBACK, emulator.buffer.maxScrollback)
    }

    @Test
    fun `withMaxScrollback shrinking keeps most recent lines`() {
        val b = buf(rows = 1, cols = 1, scrollback = 5)
        for (ch in listOf('A', 'B', 'C', 'D')) {
            b.fillRow(0, ch)
            b.scrollUp(1)
        }

        val shrunk = b.withMaxScrollback(2)

        assertEquals(2, shrunk.maxScrollback)
        assertEquals(2, shrunk.getScrollbackLineCount())
        assertEquals('D'.code, shrunk.getScrollbackLine(0).codePoint(0))
        assertEquals('C'.code, shrunk.getScrollbackLine(1).codePoint(0))
    }

    @Test
    fun `withMaxScrollback growing preserves existing lines and new capacity`() {
        val b = buf(rows = 1, cols = 1, scrollback = 2)
        for (ch in listOf('A', 'B')) {
            b.fillRow(0, ch)
            b.scrollUp(1)
        }

        val grown = b.withMaxScrollback(5)

        assertEquals(5, grown.maxScrollback)
        assertEquals(2, grown.getScrollbackLineCount())
        assertEquals('B'.code, grown.getScrollbackLine(0).codePoint(0))
        assertEquals('A'.code, grown.getScrollbackLine(1).codePoint(0))
        for (ch in listOf('C', 'D', 'E')) {
            grown.fillRow(0, ch)
            grown.scrollUp(1)
        }
        assertEquals(5, grown.getScrollbackLineCount())
    }

    @Test
    fun `TerminalEmulator setMaxScrollback updates live buffer`() {
        val emulator = TerminalEmulator(rows = 1, columns = 1, maxScrollback = 4)
        for (ch in listOf('A', 'B', 'C')) {
            emulator.buffer.fillRow(0, ch)
            emulator.buffer.scrollUp(1)
        }

        emulator.setMaxScrollback(1)

        assertEquals(1, emulator.buffer.maxScrollback)
        assertEquals(1, emulator.buffer.getScrollbackLineCount())
        assertEquals('C'.code, emulator.buffer.getScrollbackLine(0).codePoint(0))
    }
}

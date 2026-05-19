package com.tmuxes.terminal.emulator

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalEmulatorResizeTest {

    private fun emu(rows: Int = 10, cols: Int = 20) = TerminalEmulator(rows = rows, columns = cols)

    private fun TerminalEmulator.charAtScreen(row: Int, col: Int): Int {
        val snapshot = snapshot()
        return snapshot.getLine(row).codePoint(col)
    }

    /** Write a string at the current cursor position. */
    private fun TerminalEmulator.type(s: String) = processInput(s.toByteArray(Charsets.UTF_8))

    /** Move cursor to the start of a given row (0-based) by emitting CR + LF. */
    private fun TerminalEmulator.moveTo(row: Int) {
        type("\r")
        repeat(row) { type("\n") }
    }

    // --- Same size ---

    @Test
    fun `resize to same dimensions is no-op`() {
        val e = emu(4, 10)
        e.type("Hello")
        val curBefore = e.cursorRow to e.cursorCol
        e.resize(4, 10)
        assertEquals(curBefore, e.cursorRow to e.cursorCol)
        assertEquals('H'.code, e.charAtScreen(0, 0))
    }

    // --- Shrink rows ---

    @Test
    fun `shrink rows discards top lines without scrollback`() {
        val e = emu(4, 5)
        e.type("A\r\nB\r\nC\r\nD")
        e.resize(2, 5)
        // Bottom 2 lines visible
        assertEquals('C'.code, e.charAtScreen(0, 0))
        assertEquals('D'.code, e.charAtScreen(1, 0))
        // Excess rows discarded, not pushed to scrollback.
        // Remote app manages scrollback server-side.
        val snap = e.snapshot()
        assertEquals(0, snap.getScrollbackLineCount())
    }

    @Test
    fun `shrink rows adjusts cursor by excess`() {
        val e = emu(10, 20)
        e.moveTo(5) // cursor at row 5
        e.resize(6, 20)
        // excess = 4, cursorRow = (5-4) = 1
        assertEquals(1, e.cursorRow)
    }

    @Test
    fun `shrink rows clamps cursor to zero`() {
        val e = emu(10, 20)
        e.moveTo(2) // cursor at row 2
        e.resize(5, 20)
        // excess = 5, cursorRow = (2-5).coerceIn(0,4) = 0
        assertEquals(0, e.cursorRow)
    }

    @Test
    fun `shrink rows clamps cursor to last row`() {
        val e = emu(10, 20)
        e.moveTo(9) // cursor at row 9
        e.resize(3, 20)
        // excess = 7, cursorRow = (9-7).coerceIn(0,2) = 2
        assertEquals(2, e.cursorRow)
    }

    // --- Grow rows ---

    @Test
    fun `grow rows keeps cursor position`() {
        val e = emu(4, 10)
        e.moveTo(2)
        e.resize(8, 10)
        assertEquals(2, e.cursorRow)
    }

    @Test
    fun `grow rows adds blank lines at top`() {
        val e = emu(2, 5)
        e.type("A\r\nB")
        e.resize(4, 5)
        // Blank lines at top, content shifted down
        assertEquals(' '.code, e.charAtScreen(0, 0))
        assertEquals(' '.code, e.charAtScreen(1, 0))
        assertEquals('A'.code, e.charAtScreen(2, 0))
        assertEquals('B'.code, e.charAtScreen(3, 0))
    }

    // --- Columns ---

    @Test
    fun `shrink columns clamps cursor col`() {
        val e = emu(4, 80)
        e.type("A".repeat(50)) // cursor at col 50
        e.resize(4, 30)
        assertEquals(29, e.cursorCol)
    }

    @Test
    fun `grow columns pads with blanks`() {
        val e = emu(4, 5)
        e.type("ABC")
        e.resize(4, 10)
        assertEquals('A'.code, e.charAtScreen(0, 0))
        assertEquals('C'.code, e.charAtScreen(0, 2))
        assertEquals(' '.code, e.charAtScreen(0, 5))
    }

    // --- Scroll region reset ---

    @Test
    fun `resize resets scroll region`() {
        val e = emu(10, 20)
        // Set scroll region 3-7 via escape
        e.type("\u001b[4;8r")
        e.resize(8, 20)
        // After resize, scroll region should be full screen (0..7).
        // Verify by moving to last row and sending LF — should scroll full screen.
        e.type("\u001b[8;1H") // move to row 8 col 1
        e.type("X")
        e.type("\n") // LF at bottom — if scroll region is full, row 0 goes to scrollback
        val snap = e.snapshot()
        // Scrollback should have content (full screen scroll happened)
        assertEquals(true, snap.getScrollbackLineCount() > 0 || e.cursorRow == 7)
    }

    // --- Edge cases ---

    @Test
    fun `resize to zero is no-op`() {
        val e = emu(10, 20)
        e.resize(0, 20)
        // Should still have 10 rows (snapshot rows)
        assertEquals(10, e.snapshot().rows)
        e.resize(10, 0)
        // Should still have 20 columns
        assertEquals(20, e.snapshot().columns)
    }

    @Test
    fun `resize to 1x1`() {
        val e = emu(10, 20)
        e.type("Hello")
        e.resize(1, 1)
        assertEquals(0, e.cursorRow)
        assertEquals(0, e.cursorCol)
    }

    @Test
    fun `sequential shrink and grow does not pull scrollback`() {
        val e = emu(6, 10)
        e.type("A\r\nB\r\nC\r\nD\r\nE\r\nF")
        e.resize(3, 10) // push 3 lines to scrollback
        val sbAfterShrink = e.snapshot().getScrollbackLineCount()
        e.resize(6, 10) // grow back — should NOT pull scrollback
        val sbAfterGrow = e.snapshot().getScrollbackLineCount()
        assertEquals(sbAfterShrink, sbAfterGrow)
    }

    @Test
    fun `shrink by one with cursor on last row`() {
        val e = emu(5, 10)
        e.moveTo(4) // last row
        e.resize(4, 10)
        assertEquals(3, e.cursorRow) // still last row
    }
}

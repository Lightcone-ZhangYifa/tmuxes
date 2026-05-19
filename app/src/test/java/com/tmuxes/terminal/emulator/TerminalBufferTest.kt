package com.tmuxes.terminal.emulator

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalBufferTest {

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

    private fun TerminalBuffer.charAt(row: Int, col: Int): Int =
        getLine(row).codePoint(col)

    private fun TerminalBuffer.assertRowChar(row: Int, ch: Char) {
        assertEquals("Row $row col 0", ch.code, charAt(row, 0))
    }

    private fun TerminalBuffer.assertRowBlank(row: Int) {
        assertEquals("Row $row col 0 should be blank", ' '.code, charAt(row, 0))
    }

    // --- scrollUp ---

    @Test
    fun `scrollUp pushes top to scrollback and blanks bottom`() {
        val b = buf(4, 5)
        b.fillRow(0, 'A'); b.fillRow(1, 'B'); b.fillRow(2, 'C'); b.fillRow(3, 'D')
        b.scrollUp(1)
        b.assertRowChar(0, 'B')
        b.assertRowChar(1, 'C')
        b.assertRowChar(2, 'D')
        b.assertRowBlank(3)
        assertEquals(1, b.getScrollbackLineCount())
        assertEquals('A'.code, b.getScrollbackLine(0).codePoint(0))
    }

    @Test
    fun `scrollUp by count greater than rows`() {
        val b = buf(3, 5)
        b.fillRow(0, 'A'); b.fillRow(1, 'B'); b.fillRow(2, 'C')
        b.scrollUp(10)
        b.assertRowBlank(0); b.assertRowBlank(1); b.assertRowBlank(2)
        assertEquals(3, b.getScrollbackLineCount())
    }

    @Test
    fun `scrollUp by zero does nothing`() {
        val b = buf(3, 5)
        b.fillRow(0, 'A')
        b.scrollUp(0)
        b.assertRowChar(0, 'A')
        assertEquals(0, b.getScrollbackLineCount())
    }

    // --- scrollDown ---

    @Test
    fun `scrollDown discards bottom and blanks top`() {
        val b = buf(4, 5)
        b.fillRow(0, 'A'); b.fillRow(1, 'B'); b.fillRow(2, 'C'); b.fillRow(3, 'D')
        b.scrollDown(1)
        b.assertRowBlank(0)
        b.assertRowChar(1, 'A')
        b.assertRowChar(2, 'B')
        b.assertRowChar(3, 'C')
        assertEquals(0, b.getScrollbackLineCount()) // 'D' discarded, not in scrollback
    }

    @Test
    fun `scrollDown by count greater than rows`() {
        val b = buf(3, 5)
        b.fillRow(0, 'X'); b.fillRow(1, 'Y'); b.fillRow(2, 'Z')
        b.scrollDown(100)
        b.assertRowBlank(0); b.assertRowBlank(1); b.assertRowBlank(2)
    }

    // --- scrollUpRegion ---

    @Test
    fun `scrollUpRegion preserves lines outside region`() {
        val b = buf(6, 5)
        for (i in 0..5) b.fillRow(i, ('A' + i))
        b.scrollUpRegion(2, 4, 1)
        b.assertRowChar(0, 'A') // above: unchanged
        b.assertRowChar(1, 'B') // above: unchanged
        b.assertRowChar(2, 'D') // shifted up within region
        b.assertRowChar(3, 'E') // shifted up within region
        b.assertRowBlank(4)     // new blank at bottom of region
        b.assertRowChar(5, 'F') // below: unchanged
    }

    @Test
    fun `scrollUpRegion at row 0 pushes to scrollback`() {
        val b = buf(4, 5)
        b.fillRow(0, 'A'); b.fillRow(1, 'B'); b.fillRow(2, 'C'); b.fillRow(3, 'D')
        b.scrollUpRegion(0, 2, 1)
        b.assertRowChar(0, 'B')
        b.assertRowChar(1, 'C')
        b.assertRowBlank(2)
        b.assertRowChar(3, 'D') // outside region
        assertEquals(1, b.getScrollbackLineCount())
    }

    // --- scrollDownRegion ---

    @Test
    fun `scrollDownRegion preserves lines outside region`() {
        val b = buf(6, 5)
        for (i in 0..5) b.fillRow(i, ('A' + i))
        b.scrollDownRegion(2, 4, 1)
        b.assertRowChar(0, 'A') // above: unchanged
        b.assertRowChar(1, 'B') // above: unchanged
        b.assertRowBlank(2)     // new blank at top of region
        b.assertRowChar(3, 'C') // shifted down
        b.assertRowChar(4, 'D') // shifted down ('E' fell off)
        b.assertRowChar(5, 'F') // below: unchanged
    }

    @Test
    fun `scrollDownRegion by region height blanks entire region`() {
        val b = buf(5, 5)
        for (i in 0..4) b.fillRow(i, ('A' + i))
        b.scrollDownRegion(1, 3, 3)
        b.assertRowChar(0, 'A') // outside
        b.assertRowBlank(1); b.assertRowBlank(2); b.assertRowBlank(3)
        b.assertRowChar(4, 'E') // outside
    }

    // --- clearLine ---

    @Test
    fun `clearLine blanks specific row`() {
        val b = buf(4, 5)
        b.fillRow(2, 'X')
        b.clearLine(2)
        b.assertRowBlank(2)
    }

    @Test
    fun `clearLine out of range does not crash`() {
        val b = buf(4, 5)
        b.clearLine(-1)
        b.clearLine(4)
        b.clearLine(100)
        // no crash
    }

    // --- clearAll ---

    @Test
    fun `clearAll blanks visible rows but preserves scrollback`() {
        val b = buf(3, 5)
        b.fillRow(0, 'A'); b.fillRow(1, 'B'); b.fillRow(2, 'C')
        b.scrollUp(1) // create scrollback
        b.clearAll()
        b.assertRowBlank(0); b.assertRowBlank(1); b.assertRowBlank(2)
        assertEquals(1, b.getScrollbackLineCount())
    }

    // --- clearScrollback ---

    @Test
    fun `clearScrollback removes scrollback but preserves screen`() {
        val b = buf(3, 5)
        b.fillRow(0, 'A')
        b.scrollUp(2) // push to scrollback, screen shifts
        b.clearScrollback()
        assertEquals(0, b.getScrollbackLineCount())
        // screen still has whatever was left after scrollUp
    }

    // --- resize ---

    @Test
    fun `resize shrink rows discards top lines without scrollback`() {
        val b = buf(4, 5)
        b.fillRow(0, 'A'); b.fillRow(1, 'B'); b.fillRow(2, 'C'); b.fillRow(3, 'D')
        b.resize(2, 5)
        b.assertRowChar(0, 'C')
        b.assertRowChar(1, 'D')
        // Excess top rows are discarded, NOT pushed to scrollback.
        // Remote app (tmux) manages scrollback server-side; client push
        // would cause duplicate stale content flooding on alt-screen exit.
        assertEquals(0, b.getScrollbackLineCount())
    }

    @Test
    fun `resize grow rows adds blank lines at top`() {
        val b = buf(2, 5)
        b.fillRow(0, 'A'); b.fillRow(1, 'B')
        b.resize(4, 5)
        b.assertRowBlank(0)
        b.assertRowBlank(1)
        b.assertRowChar(2, 'A')
        b.assertRowChar(3, 'B')
    }

    @Test
    fun `resize shrink columns truncates`() {
        val b = buf(2, 8)
        val packed = encChar('Z')
        for (col in 0 until 8) b.setCell(0, col, packed, defaultColor)
        b.resize(2, 4)
        assertEquals('Z'.code, b.charAt(0, 0))
        assertEquals('Z'.code, b.charAt(0, 3))
        assertEquals(4, b.columns)
    }

    @Test
    fun `resize grow columns pads with blanks`() {
        val b = buf(2, 4)
        b.fillRow(0, 'Q')
        b.resize(2, 8)
        assertEquals('Q'.code, b.charAt(0, 0))
        assertEquals(' '.code, b.charAt(0, 5))
    }
}

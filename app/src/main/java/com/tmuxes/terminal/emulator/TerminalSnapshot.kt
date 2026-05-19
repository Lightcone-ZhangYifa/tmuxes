package com.tmuxes.terminal.emulator

/**
 * Immutable snapshot of the terminal state at a point in time.
 * Created under the emulator lock, consumed by the render thread without any locking.
 *
 * Includes both the visible screen and scrollback data so the render thread
 * never needs to touch the live buffer — eliminating the ABBA deadlock between
 * emulator.lock and buffer.lock.
 */
class TerminalSnapshot(
    val rows: Int,
    val columns: Int,
    private val screenPacked: Array<LongArray>,
    private val screenColor: Array<LongArray>,
    val cursorRow: Int,
    val cursorCol: Int,
    val cursorVisible: Boolean,
    private val scrollbackPacked: Array<LongArray>,
    private val scrollbackColor: Array<LongArray>,
    val scrollbackCount: Int
) {
    private val blankLine: TerminalLine by lazy {
        TerminalLine(
            LongArray(columns) { CellEncoding.EMPTY_PACKED },
            LongArray(columns) { CellEncoding.EMPTY_COLOR },
            columns
        )
    }

    fun getLine(row: Int): TerminalLine {
        return if (row in 0 until rows) {
            TerminalLine(screenPacked[row], screenColor[row], columns)
        } else {
            blankLine
        }
    }

    fun getCellPacked(row: Int, col: Int): Pair<Long, Long> {
        return if (row in 0 until rows && col in 0 until columns) {
            screenPacked[row][col] to screenColor[row][col]
        } else {
            CellEncoding.EMPTY_PACKED to CellEncoding.EMPTY_COLOR
        }
    }

    fun getScrollbackLineCount(): Int = scrollbackCount

    /**
     * Returns a scrollback line. Index 0 is the most recent scrollback line
     * (just above the visible area); higher indices go further back in history.
     */
    fun getScrollbackLine(index: Int): TerminalLine {
        return if (index in 0 until scrollbackCount) {
            val cols = scrollbackPacked[index].size
            TerminalLine(scrollbackPacked[index], scrollbackColor[index], cols)
        } else {
            blankLine
        }
    }
}

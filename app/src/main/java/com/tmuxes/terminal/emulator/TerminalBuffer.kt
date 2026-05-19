package com.tmuxes.terminal.emulator

/**
 * Circular buffer that holds the visible terminal screen plus a scrollback
 * history of configurable depth.
 *
 * Terminology:
 *   - **visible area**: the [rows] x [columns] grid currently on screen.
 *     Row 0 is the top of the visible area, row (rows-1) is the bottom.
 *   - **scrollback**: lines that have scrolled off the top of the visible area.
 *     They are kept in a ring buffer of capacity [maxScrollback].
 *
 * Storage: each line is represented as a pair of LongArrays (packed + color),
 * using the encoding defined in [CellEncoding].
 *
 * Thread safety: every public mutating method synchronizes on [lock].
 */
class TerminalBuffer(
    var rows: Int,
    var columns: Int,
    val maxScrollback: Int = DEFAULT_SCROLLBACK
) {
    companion object {
        const val DEFAULT_SCROLLBACK = 10_000
    }

    private val lock = Any()

    /** Ring buffer for scrollback lines — packed data. */
    private var scrollbackPacked: Array<LongArray?> = arrayOfNulls(maxScrollback)
    /** Ring buffer for scrollback lines — color data. */
    private var scrollbackColor: Array<LongArray?> = arrayOfNulls(maxScrollback)

    /** Number of scrollback lines currently stored (0..maxScrollback). */
    private var scrollbackCount: Int = 0

    /** Write-head inside scrollback ring (next position to write). */
    private var scrollbackHead: Int = 0

    /** The visible screen lines — packed data, indexed 0 = top. */
    private var screenPacked: Array<LongArray> = Array(rows) { newBlankPacked() }
    /** The visible screen lines — color data, indexed 0 = top. */
    private var screenColor: Array<LongArray> = Array(rows) { newBlankColor() }

    // -----------------------------------------------------------------
    // Public query API
    // -----------------------------------------------------------------

    /**
     * Returns the line at the given visible row (0 = top of screen)
     * as a [TerminalLine]. Returns a blank line if [row] is out of range.
     */
    fun getLine(row: Int): TerminalLine = synchronized(lock) {
        if (row in 0 until rows) {
            TerminalLine(screenPacked[row], screenColor[row], columns)
        } else {
            TerminalLine(newBlankPacked(), newBlankColor(), columns)
        }
    }

    /**
     * Returns the packed and color values at ([row], [col]) in the visible area.
     * Returns a Pair of (packed, color) Longs.
     */
    fun getCellPacked(row: Int, col: Int): Pair<Long, Long> = synchronized(lock) {
        if (row in 0 until rows && col in 0 until columns) {
            Pair(screenPacked[row][col], screenColor[row][col])
        } else {
            Pair(CellEncoding.EMPTY_PACKED, CellEncoding.EMPTY_COLOR)
        }
    }

    /**
     * Returns the number of scrollback lines currently stored.
     */
    fun getScrollbackLineCount(): Int = synchronized(lock) { scrollbackCount }

    /**
     * Returns a scrollback line as a [TerminalLine].
     * [index] 0 is the most recent scrollback line (just above the visible area),
     * and higher indices go further back in history.
     * Returns a blank line if the index is out of range.
     */
    fun getScrollbackLine(index: Int): TerminalLine = synchronized(lock) {
        if (index < 0 || index >= scrollbackCount) {
            return TerminalLine(newBlankPacked(), newBlankColor(), columns)
        }
        val ringIndex = (scrollbackHead - 1 - index + maxScrollback * 2) % maxScrollback
        val packed = scrollbackPacked[ringIndex] ?: newBlankPacked()
        val color = scrollbackColor[ringIndex] ?: newBlankColor()
        TerminalLine(packed, color, packed.size)
    }

    // -----------------------------------------------------------------
    // Public mutation API
    // -----------------------------------------------------------------

    /**
     * Sets a single cell in the visible area using packed encoding.
     */
    fun setCell(row: Int, col: Int, packed: Long, color: Long) = synchronized(lock) {
        if (row in 0 until rows && col in 0 until columns) {
            screenPacked[row][col] = packed
            screenColor[row][col] = color
        }
    }

    /**
     * Returns the raw packed Long at ([row], [col]).
     * Used by emulator for insertChars/deleteChars operations.
     */
    fun getRawPacked(row: Int, col: Int): Long = synchronized(lock) {
        if (row in 0 until rows && col in 0 until columns) {
            screenPacked[row][col]
        } else {
            CellEncoding.EMPTY_PACKED
        }
    }

    /**
     * Returns the raw color Long at ([row], [col]).
     * Used by emulator for insertChars/deleteChars operations.
     */
    fun getRawColor(row: Int, col: Int): Long = synchronized(lock) {
        if (row in 0 until rows && col in 0 until columns) {
            screenColor[row][col]
        } else {
            CellEncoding.EMPTY_COLOR
        }
    }

    /**
     * Scrolls the visible area up by [count] lines.
     * The top lines are pushed into scrollback; new blank lines appear at the bottom.
     */
    fun scrollUp(count: Int = 1) = synchronized(lock) {
        val effective = count.coerceIn(0, rows)
        for (i in 0 until effective) {
            pushScrollback(screenPacked[0], screenColor[0])
            // Shift visible lines up by one
            for (r in 1 until rows) {
                screenPacked[r - 1] = screenPacked[r]
                screenColor[r - 1] = screenColor[r]
            }
            screenPacked[rows - 1] = newBlankPacked()
            screenColor[rows - 1] = newBlankColor()
        }
    }

    /**
     * Scrolls the visible area up by [count] lines within a scroll region
     * defined by [top] (inclusive) and [bottom] (inclusive).
     * Only lines inside the region move; lines outside are untouched.
     * The top lines of the region are pushed into scrollback only if the
     * region starts at row 0.
     */
    fun scrollUpRegion(top: Int, bottom: Int, count: Int = 1) = synchronized(lock) {
        val effectiveTop = top.coerceIn(0, rows - 1)
        val effectiveBottom = bottom.coerceIn(0, rows - 1)
        if (effectiveTop >= effectiveBottom) return
        val effective = count.coerceIn(0, effectiveBottom - effectiveTop + 1)
        for (i in 0 until effective) {
            if (effectiveTop == 0) {
                pushScrollback(screenPacked[0], screenColor[0])
            }
            for (r in effectiveTop until effectiveBottom) {
                screenPacked[r] = screenPacked[r + 1]
                screenColor[r] = screenColor[r + 1]
            }
            screenPacked[effectiveBottom] = newBlankPacked()
            screenColor[effectiveBottom] = newBlankColor()
        }
    }

    /**
     * Scrolls the visible area down by [count] lines.
     * The bottom lines are discarded; new blank lines appear at the top.
     */
    fun scrollDown(count: Int = 1) = synchronized(lock) {
        val effective = count.coerceIn(0, rows)
        for (i in 0 until effective) {
            for (r in rows - 1 downTo 1) {
                screenPacked[r] = screenPacked[r - 1]
                screenColor[r] = screenColor[r - 1]
            }
            screenPacked[0] = newBlankPacked()
            screenColor[0] = newBlankColor()
        }
    }

    /**
     * Scrolls down within a scroll region.
     */
    fun scrollDownRegion(top: Int, bottom: Int, count: Int = 1) = synchronized(lock) {
        val effectiveTop = top.coerceIn(0, rows - 1)
        val effectiveBottom = bottom.coerceIn(0, rows - 1)
        if (effectiveTop >= effectiveBottom) return
        val effective = count.coerceIn(0, effectiveBottom - effectiveTop + 1)
        for (i in 0 until effective) {
            for (r in effectiveBottom downTo effectiveTop + 1) {
                screenPacked[r] = screenPacked[r - 1]
                screenColor[r] = screenColor[r - 1]
            }
            screenPacked[effectiveTop] = newBlankPacked()
            screenColor[effectiveTop] = newBlankColor()
        }
    }

    /**
     * Clears every cell on the given visible row to empty.
     */
    fun clearLine(row: Int) = synchronized(lock) {
        if (row in 0 until rows) {
            screenPacked[row] = newBlankPacked()
            screenColor[row] = newBlankColor()
        }
    }

    /**
     * Clears every cell in the visible area.  Scrollback is not affected.
     */
    fun clearAll() = synchronized(lock) {
        for (r in 0 until rows) {
            screenPacked[r] = newBlankPacked()
            screenColor[r] = newBlankColor()
        }
    }

    /**
     * Clears all scrollback history without affecting the visible screen.
     */
    fun clearScrollback() = synchronized(lock) {
        for (i in 0 until maxScrollback) {
            scrollbackPacked[i] = null
            scrollbackColor[i] = null
        }
        scrollbackCount = 0
        scrollbackHead = 0
    }

    /**
     * Resizes the visible area.  Existing content is preserved as much as
     * possible: lines are truncated or padded when the column count changes,
     * and rows are added (blank) or removed (pushed to scrollback) as needed.
     */
    fun resize(newRows: Int, newCols: Int) = synchronized(lock) {
        if (newRows <= 0 || newCols <= 0) return

        // Adjust columns on existing screen lines
        val resizedPacked = Array(rows) { r -> resizeLongArray(screenPacked[r], newCols, CellEncoding.EMPTY_PACKED) }
        val resizedColor = Array(rows) { r -> resizeLongArray(screenColor[r], newCols, CellEncoding.EMPTY_COLOR) }

        val newScreenPacked: Array<LongArray>
        val newScreenColor: Array<LongArray>
        if (newRows <= rows) {
            // Shrinking vertically: discard excess top lines.
            // Don't push to scrollback — the remote app (tmux) manages its
            // own scrollback server-side and will redraw after SIGWINCH.
            // Pushing here creates duplicate stale content that floods the
            // view when switching from alt screen back to main screen,
            // because both active and inactive buffers are resized.
            val excess = rows - newRows
            newScreenPacked = Array(newRows) { r -> resizedPacked[r + excess] }
            newScreenColor = Array(newRows) { r -> resizedColor[r + excess] }
        } else {
            // Growing vertically: add blank lines at the top.
            // Don't pull from scrollback — the remote app (tmux) manages
            // its own screen content and will redraw after SIGWINCH.
            // Pulling scrollback would flash old history before the redraw.
            val blankCount = newRows - rows
            newScreenPacked = Array(newRows) { r ->
                if (r < blankCount) newBlankPacked(newCols)
                else resizedPacked[r - blankCount]
            }
            newScreenColor = Array(newRows) { r ->
                if (r < blankCount) newBlankColor(newCols)
                else resizedColor[r - blankCount]
            }
        }

        screenPacked = newScreenPacked
        screenColor = newScreenColor
        rows = newRows
        columns = newCols
    }

    // -----------------------------------------------------------------
    // Snapshot API
    // -----------------------------------------------------------------

    /**
     * Creates a consistent snapshot of the visible screen.
     * Must be called under the emulator lock for consistency.
     */
    fun snapshot(cursorRow: Int, cursorCol: Int, cursorVisible: Boolean): TerminalSnapshot {
        return synchronized(lock) {
            // Copy scrollback lines in logical order (index 0 = most recent)
            // Scrollback arrays are never mutated in place after being pushed —
            // the ring only overwrites slot pointers, not array contents.
            // Safe to share references without copying.
            val sbPacked = Array(scrollbackCount) { i ->
                val ringIndex = (scrollbackHead - 1 - i + maxScrollback * 2) % maxScrollback
                scrollbackPacked[ringIndex] ?: newBlankPacked()
            }
            val sbColor = Array(scrollbackCount) { i ->
                val ringIndex = (scrollbackHead - 1 - i + maxScrollback * 2) % maxScrollback
                scrollbackColor[ringIndex] ?: newBlankColor()
            }
            TerminalSnapshot(
                rows = rows,
                columns = columns,
                screenPacked = Array(rows) { r -> screenPacked[r].copyOf() },
                screenColor = Array(rows) { r -> screenColor[r].copyOf() },
                cursorRow = cursorRow,
                cursorCol = cursorCol,
                cursorVisible = cursorVisible,
                scrollbackPacked = sbPacked,
                scrollbackColor = sbColor,
                scrollbackCount = scrollbackCount
            )
        }
    }

    /**
     * Returns a buffer with the same visible screen and as much scrollback as
     * fits in [newMaxScrollback]. Used when the user changes scrollback depth
     * while a session is alive.
     */
    fun withMaxScrollback(newMaxScrollback: Int): TerminalBuffer = synchronized(lock) {
        val clampedMax = newMaxScrollback.coerceAtLeast(0)
        val next = TerminalBuffer(rows, columns, clampedMax)
        next.screenPacked = Array(rows) { r -> screenPacked[r].copyOf() }
        next.screenColor = Array(rows) { r -> screenColor[r].copyOf() }

        val retained = minOf(scrollbackCount, clampedMax)
        for (i in retained - 1 downTo 0) {
            val ringIndex = (scrollbackHead - 1 - i + maxScrollback * 2) % maxScrollback
            val packed = scrollbackPacked[ringIndex]?.copyOf() ?: newBlankPacked()
            val color = scrollbackColor[ringIndex]?.copyOf() ?: newBlankColor()
            next.pushScrollback(packed, color)
        }
        next
    }

    // -----------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------

    /** Creates a blank packed line of the current column width. */
    internal fun newBlankPacked(cols: Int = columns): LongArray {
        return LongArray(cols) { CellEncoding.EMPTY_PACKED }
    }

    /** Creates a blank color line of the current column width. */
    internal fun newBlankColor(cols: Int = columns): LongArray {
        return LongArray(cols) { CellEncoding.EMPTY_COLOR }
    }

    /** Pushes a line into the scrollback ring buffer. */
    private fun pushScrollback(packed: LongArray, color: LongArray) {
        if (maxScrollback <= 0) return
        scrollbackPacked[scrollbackHead] = packed
        scrollbackColor[scrollbackHead] = color
        scrollbackHead = (scrollbackHead + 1) % maxScrollback
        if (scrollbackCount < maxScrollback) scrollbackCount++
    }

    /** Pads or truncates a LongArray to [targetCols]. */
    private fun resizeLongArray(arr: LongArray, targetCols: Int, fillValue: Long): LongArray {
        if (arr.size == targetCols) return arr
        return LongArray(targetCols) { c ->
            if (c < arr.size) arr[c] else fillValue
        }
    }
}

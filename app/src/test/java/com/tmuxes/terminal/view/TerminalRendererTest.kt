package com.tmuxes.terminal.view

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit tests for [CursorStyle] and terminal rendering configuration logic.
 *
 * Covers enum exhaustiveness, valueOf parsing, line-spacing cell height
 * calculation, and padding-based usable area reduction.
 */
class TerminalRendererTest {

    // == CursorStyle enum =====================================================

    @Test
    fun cursorStyle_hasExactlyThreeValues() {
        assertEquals(3, CursorStyle.entries.size)
    }

    @Test
    fun cursorStyle_containsBlock() {
        assertNotNull(CursorStyle.valueOf("BLOCK"))
        assertEquals(CursorStyle.BLOCK, CursorStyle.valueOf("BLOCK"))
    }

    @Test
    fun cursorStyle_containsUnderline() {
        assertNotNull(CursorStyle.valueOf("UNDERLINE"))
        assertEquals(CursorStyle.UNDERLINE, CursorStyle.valueOf("UNDERLINE"))
    }

    @Test
    fun cursorStyle_containsBar() {
        assertNotNull(CursorStyle.valueOf("BAR"))
        assertEquals(CursorStyle.BAR, CursorStyle.valueOf("BAR"))
    }

    @Test
    fun cursorStyle_valuesInDeclaredOrder() {
        val values = CursorStyle.entries
        assertEquals(CursorStyle.BLOCK, values[0])
        assertEquals(CursorStyle.UNDERLINE, values[1])
        assertEquals(CursorStyle.BAR, values[2])
    }

    @Test(expected = IllegalArgumentException::class)
    fun cursorStyle_invalidNameThrows() {
        CursorStyle.valueOf("IBEAM")
    }

    // == Line spacing cell height calculation =================================

    /**
     * Simulates the cell height formula:
     *   cellHeight = (descent - ascent + leading) * lineSpacing
     *
     * Uses representative font metrics values.
     */
    @Test
    fun lineSpacing_default_doesNotAlterCellHeight() {
        val descent = 5f
        val ascent = -15f
        val leading = 0f
        val lineSpacing = 1.0f

        val cellHeight = (descent - ascent + leading) * lineSpacing
        assertEquals(20f, cellHeight, 0.001f)
    }

    @Test
    fun lineSpacing_1_5x_increasesCellHeight() {
        val descent = 5f
        val ascent = -15f
        val leading = 0f
        val lineSpacing = 1.5f

        val cellHeight = (descent - ascent + leading) * lineSpacing
        assertEquals(30f, cellHeight, 0.001f)
    }

    @Test
    fun lineSpacing_0_8x_decreasesCellHeight() {
        val descent = 5f
        val ascent = -15f
        val leading = 0f
        val lineSpacing = 0.8f

        val cellHeight = (descent - ascent + leading) * lineSpacing
        assertEquals(16f, cellHeight, 0.001f)
    }

    @Test
    fun lineSpacing_withLeading() {
        val descent = 4f
        val ascent = -12f
        val leading = 2f
        val lineSpacing = 1.2f

        val cellHeight = (descent - ascent + leading) * lineSpacing
        // (4 - (-12) + 2) * 1.2 = 18 * 1.2 = 21.6
        assertEquals(21.6f, cellHeight, 0.001f)
    }

    @Test
    fun lineSpacing_2x() {
        val descent = 5f
        val ascent = -15f
        val leading = 0f
        val lineSpacing = 2.0f

        val cellHeight = (descent - ascent + leading) * lineSpacing
        assertEquals(40f, cellHeight, 0.001f)
    }

    // == Padding reduces usable terminal area =================================

    /**
     * Simulates the usable area calculation:
     *   paddingPx = paddingDp * density
     *   usableWidth = viewWidth - paddingPx * 2
     *   usableHeight = viewHeight - paddingPx * 2
     *   cols = (usableWidth / cellWidth).coerceAtLeast(1)
     *   rows = (usableHeight / cellHeight).coerceAtLeast(1)
     */
    @Test
    fun padding_zero_doesNotReduceArea() {
        val viewWidth = 1080
        val viewHeight = 1920
        val paddingDp = 0
        val density = 2.0f
        val cellWidth = 20f
        val cellHeight = 40f

        val paddingPx = (paddingDp * density).toInt()
        val usableWidth = viewWidth - paddingPx * 2
        val usableHeight = viewHeight - paddingPx * 2

        assertEquals(viewWidth, usableWidth)
        assertEquals(viewHeight, usableHeight)

        val cols = (usableWidth / cellWidth).toInt().coerceAtLeast(1)
        val rows = (usableHeight / cellHeight).toInt().coerceAtLeast(1)
        assertEquals(54, cols)
        assertEquals(48, rows)
    }

    @Test
    fun padding_reducesColumnsAndRows() {
        val viewWidth = 1080
        val viewHeight = 1920
        val paddingDp = 8
        val density = 2.0f
        val cellWidth = 20f
        val cellHeight = 40f

        val paddingPx = (paddingDp * density).toInt()
        val usableWidth = viewWidth - paddingPx * 2
        val usableHeight = viewHeight - paddingPx * 2

        assertEquals(1048, usableWidth)  // 1080 - 32
        assertEquals(1888, usableHeight) // 1920 - 32

        val cols = (usableWidth / cellWidth).toInt().coerceAtLeast(1)
        val rows = (usableHeight / cellHeight).toInt().coerceAtLeast(1)
        assertEquals(52, cols)  // 1048/20 = 52.4
        assertEquals(47, rows)  // 1888/40 = 47.2
    }

    @Test
    fun padding_largePadding_reducesSignificantly() {
        val viewWidth = 1080
        val viewHeight = 1920
        val paddingDp = 24
        val density = 3.0f
        val cellWidth = 20f
        val cellHeight = 40f

        val paddingPx = (paddingDp * density).toInt()
        val usableWidth = viewWidth - paddingPx * 2
        val usableHeight = viewHeight - paddingPx * 2

        assertEquals(936, usableWidth)   // 1080 - 144
        assertEquals(1776, usableHeight) // 1920 - 144

        val cols = (usableWidth / cellWidth).toInt().coerceAtLeast(1)
        val rows = (usableHeight / cellHeight).toInt().coerceAtLeast(1)
        assertEquals(46, cols)  // 936/20 = 46.8
        assertEquals(44, rows)  // 1776/40 = 44.4
    }

    @Test
    fun padding_almostFullView_clampsToMinimum() {
        val viewWidth = 100
        val viewHeight = 100
        val paddingDp = 25
        val density = 2.0f
        val cellWidth = 20f
        val cellHeight = 40f

        val paddingPx = (paddingDp * density).toInt()
        val usableWidth = viewWidth - paddingPx * 2
        val usableHeight = viewHeight - paddingPx * 2

        // Usable area is 0 or negative but cols/rows coerce to 1
        assertEquals(0, usableWidth)
        assertEquals(0, usableHeight)

        val cols = (usableWidth / cellWidth).toInt().coerceAtLeast(1)
        val rows = (usableHeight / cellHeight).toInt().coerceAtLeast(1)
        assertEquals(1, cols)
        assertEquals(1, rows)
    }

    @Test
    fun padding_1dp_atVariousDensities() {
        val cellWidth = 10f
        val cellHeight = 20f
        val viewWidth = 200
        val viewHeight = 400

        // density=1 => paddingPx=1 => usable=198x398 => 19x19
        run {
            val paddingPx = (1 * 1.0f).toInt()
            val cols = ((viewWidth - paddingPx * 2) / cellWidth).toInt().coerceAtLeast(1)
            val rows = ((viewHeight - paddingPx * 2) / cellHeight).toInt().coerceAtLeast(1)
            assertEquals(19, cols)
            assertEquals(19, rows)
        }

        // density=2 => paddingPx=2 => usable=196x396 => 19x19
        run {
            val paddingPx = (1 * 2.0f).toInt()
            val cols = ((viewWidth - paddingPx * 2) / cellWidth).toInt().coerceAtLeast(1)
            val rows = ((viewHeight - paddingPx * 2) / cellHeight).toInt().coerceAtLeast(1)
            assertEquals(19, cols)
            assertEquals(19, rows)
        }

        // density=4 => paddingPx=4 => usable=192x392 => 19x19
        run {
            val paddingPx = (1 * 4.0f).toInt()
            val cols = ((viewWidth - paddingPx * 2) / cellWidth).toInt().coerceAtLeast(1)
            val rows = ((viewHeight - paddingPx * 2) / cellHeight).toInt().coerceAtLeast(1)
            assertEquals(19, cols)
            assertEquals(19, rows)
        }
    }

    // == Combined line spacing + padding ======================================

    @Test
    fun lineSpacing_and_padding_combined() {
        val descent = 5f
        val ascent = -15f
        val leading = 0f
        val lineSpacing = 1.5f
        val cellWidth = 20f
        val cellHeight = (descent - ascent + leading) * lineSpacing // 30f

        val viewWidth = 1080
        val viewHeight = 1920
        val paddingDp = 16
        val density = 2.0f

        val paddingPx = (paddingDp * density).toInt() // 32
        val usableWidth = viewWidth - paddingPx * 2    // 1016
        val usableHeight = viewHeight - paddingPx * 2  // 1856

        assertEquals(30f, cellHeight, 0.001f)
        val cols = (usableWidth / cellWidth).toInt().coerceAtLeast(1)
        val rows = (usableHeight / cellHeight).toInt().coerceAtLeast(1)
        assertEquals(50, cols)  // 1016/20 = 50.8
        assertEquals(61, rows)  // 1856/30 = 61.86
    }
}

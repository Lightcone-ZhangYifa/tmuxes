package com.tmuxes.terminal.view

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.tmuxes.terminal.emulator.CellEncoding
import com.tmuxes.terminal.emulator.TerminalColors
import com.tmuxes.terminal.emulator.TerminalEmulator
import com.tmuxes.terminal.emulator.TerminalLine
import com.tmuxes.terminal.emulator.TerminalSnapshot

/** Cursor appearance style. */
enum class CursorStyle { BLOCK, UNDERLINE, BAR }

/**
 * Shared rendering logic for terminal content.
 *
 * Both [TerminalView] (interactive in-app terminal) and
 * [com.tmuxes.widget.TerminalBitmapRenderer] (home-screen widget bitmap)
 * delegate cell-level drawing to this class, eliminating duplicated
 * rendering code.
 */
class TerminalRenderer {

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        textAlign = Paint.Align.LEFT; isLinearText = true
    }
    val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        textAlign = Paint.Align.LEFT; isLinearText = true
    }
    val italicPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        textAlign = Paint.Align.LEFT; isLinearText = true
    }
    val boldItalicPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        textAlign = Paint.Align.LEFT; isLinearText = true
    }
    val bgPaint = Paint()
    val cursorPaint = Paint().apply { style = Paint.Style.FILL; alpha = 180 }

    // Pre-allocated char buffer to avoid per-cell String allocation
    private val charBuf = CharArray(2)

    // Cached ANSI color remap table — O(1) lookup instead of O(16) per cell
    private var cachedScheme: TerminalColors.ColorScheme? = null
    private var ansiLookup = HashMap<Int, Int>(16)

    /**
     * Updates all paints for the given font size and typeface, then computes
     * cell dimensions.
     *
     * @param lineSpacing Multiplier applied to the natural line height.
     *                    1.0 = default (no extra spacing).
     * @return Triple of (cellWidth, cellHeight, baselineOffset).
     */
    fun updatePaints(
        fontSizePx: Float,
        typeface: Typeface,
        lineSpacing: Float = 1.0f
    ): Triple<Float, Float, Float> {
        for (paint in arrayOf(textPaint, boldPaint, italicPaint, boldItalicPaint)) {
            paint.textSize = fontSizePx
        }
        textPaint.typeface = typeface
        boldPaint.typeface = Typeface.create(typeface, Typeface.BOLD)
        italicPaint.typeface = Typeface.create(typeface, Typeface.ITALIC)
        boldItalicPaint.typeface = Typeface.create(typeface, Typeface.BOLD_ITALIC)

        val cw = textPaint.measureText("M")
        val metrics = textPaint.fontMetrics
        val ch = (metrics.descent - metrics.ascent + metrics.leading) * lineSpacing
        val bl = -metrics.ascent
        return Triple(cw, ch, bl)
    }

    fun selectPaint(bold: Boolean, italic: Boolean): Paint = when {
        bold && italic -> boldItalicPaint
        bold -> boldPaint
        italic -> italicPaint
        else -> textPaint
    }

    fun remapAnsiColor(color: Int, scheme: TerminalColors.ColorScheme): Int {
        if (scheme !== cachedScheme) {
            ansiLookup = HashMap(16)
            for (i in 0..15) {
                ansiLookup[TerminalColors.get256Color(i)] = scheme.ansi[i]
            }
            cachedScheme = scheme
        }
        return ansiLookup[color] ?: color
    }

    fun dimColor(color: Int): Int {
        val a = (color ushr 24) and 0xFF
        val r = ((color ushr 16) and 0xFF) * 2 / 3
        val g = ((color ushr 8) and 0xFF) * 2 / 3
        val b = (color and 0xFF) * 2 / 3
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    /**
     * Renders a single [TerminalLine] of packed cells onto the [canvas].
     *
     * Handles per-cell foreground/background colors, bold/italic/underline/
     * strikethrough attributes, and CJK wide-character support.
     */
    fun drawLine(
        canvas: Canvas, line: TerminalLine, y: Float, cols: Int,
        cellWidth: Float, cellHeight: Float, baselineOffset: Float,
        scheme: TerminalColors.ColorScheme,
        boldIsBright: Boolean = false,
        underlineStyle: String = "solid"
    ) {
        val defaultFg = scheme.foreground
        val defaultBg = scheme.background

        var col = 0
        while (col < cols && col < line.length) {
            if (line.isWidePlaceholder(col)) { col++; continue }

            val x = col * cellWidth
            val ch = line.charValue(col)
            val isWide = TerminalEmulator.isWideChar(ch)
            val charWidth = if (isWide) 2 else 1

            // Background
            val cellBg = line.bg(col)
            val rawBg = if (cellBg == CellEncoding.DEFAULT_BG) defaultBg else remapAnsiColor(cellBg, scheme)
            if (rawBg != defaultBg) {
                bgPaint.color = rawBg
                canvas.drawRect(x, y, x + cellWidth * charWidth, y + cellHeight, bgPaint)
            }

            // Foreground
            val cellFg = line.fg(col)
            var fg = if (cellFg == CellEncoding.DEFAULT_FG) defaultFg else remapAnsiColor(cellFg, scheme)
            val isBold = line.isBold(col)
            // Bold-is-bright: shift standard ANSI colors 0-7 to bright 8-15
            if (boldIsBright && isBold && cellFg != CellEncoding.DEFAULT_FG) {
                for (i in 0..7) {
                    if (cellFg == TerminalColors.get256Color(i)) {
                        fg = remapAnsiColor(TerminalColors.get256Color(i + 8), scheme)
                        break
                    }
                }
            }
            val paint = selectPaint(isBold, line.isItalic(col))
            paint.color = if (line.isDim(col)) dimColor(fg) else fg

            // Draw character using pre-allocated char buffer
            if (ch != ' ' && ch != '\u0000') {
                charBuf[0] = ch
                canvas.drawText(charBuf, 0, 1, x, y + baselineOffset, paint)
            }

            // Underline
            if (line.isUnderline(col)) {
                val underlineY = y + baselineOffset + paint.fontMetrics.descent * 0.3f
                val stroke = cellHeight * 0.05f
                val endX = x + cellWidth * charWidth
                paint.strokeWidth = stroke
                when (underlineStyle) {
                    "double" -> {
                        val gap = stroke * 1.5f
                        canvas.drawLine(x, underlineY - gap, endX, underlineY - gap, paint)
                        canvas.drawLine(x, underlineY + gap, endX, underlineY + gap, paint)
                    }
                    "dotted" -> {
                        val dotLen = cellWidth * 0.15f
                        val gapLen = cellWidth * 0.3f
                        var dx = x
                        while (dx < endX) {
                            canvas.drawLine(dx, underlineY, (dx + dotLen).coerceAtMost(endX), underlineY, paint)
                            dx += gapLen
                        }
                    }
                    "dashed" -> {
                        val dashLen = cellWidth * 0.4f
                        val gapLen = cellWidth * 0.2f
                        var dx = x
                        while (dx < endX) {
                            canvas.drawLine(dx, underlineY, (dx + dashLen).coerceAtMost(endX), underlineY, paint)
                            dx += dashLen + gapLen
                        }
                    }
                    else -> canvas.drawLine(x, underlineY, endX, underlineY, paint)
                }
                paint.strokeWidth = 0f
            }

            // Strikethrough
            if (line.isStrikethrough(col)) {
                val strikeY = y + cellHeight * 0.45f
                paint.strokeWidth = cellHeight * 0.05f
                canvas.drawLine(x, strikeY, x + cellWidth * charWidth, strikeY, paint)
                paint.strokeWidth = 0f
            }

            col += charWidth
        }
    }

    /**
     * Draws the cursor at the specified position.
     *
     * Supports block (filled rectangle with inverted character), underline
     * (bottom-edge bar), and vertical bar (left-edge bar) styles.
     *
     * @param cursorStyle  Appearance style (defaults to [CursorStyle.BLOCK]).
     * @param cursorColor  Explicit color override; null = use scheme foreground.
     */
    fun drawCursor(
        canvas: Canvas, snapshot: TerminalSnapshot,
        row: Int, col: Int,
        y: Float, x: Float,
        cellWidth: Float, cellHeight: Float, baselineOffset: Float,
        scheme: TerminalColors.ColorScheme,
        cursorStyle: CursorStyle = CursorStyle.BLOCK,
        cursorColor: Int? = null
    ) {
        when (cursorStyle) {
            CursorStyle.BLOCK -> {
                cursorPaint.color = cursorColor ?: scheme.foreground
                cursorPaint.alpha = 180
                canvas.drawRect(x, y, x + cellWidth, y + cellHeight, cursorPaint)

                // Redraw character under cursor with inverted color
                val (cellPacked, _) = snapshot.getCellPacked(row, col)
                val cellChar = CellEncoding.codePoint(cellPacked)
                val ch = if (cellChar in 0..0xFFFF) cellChar.toChar() else '?'
                if (ch != ' ' && ch != '\u0000') {
                    val paint = selectPaint(CellEncoding.isBold(cellPacked), CellEncoding.isItalic(cellPacked))
                    paint.color = scheme.background
                    charBuf[0] = ch
                    canvas.drawText(charBuf, 0, 1, x, y + baselineOffset, paint)
                }
            }
            CursorStyle.UNDERLINE -> {
                cursorPaint.color = cursorColor ?: scheme.foreground
                cursorPaint.alpha = 220
                val underlineHeight = (cellHeight * 0.12f).coerceAtLeast(2f)
                canvas.drawRect(x, y + cellHeight - underlineHeight, x + cellWidth, y + cellHeight, cursorPaint)
            }
            CursorStyle.BAR -> {
                cursorPaint.color = cursorColor ?: scheme.foreground
                cursorPaint.alpha = 220
                val barWidth = (cellWidth * 0.1f).coerceAtLeast(2f)
                canvas.drawRect(x, y, x + barWidth, y + cellHeight, cursorPaint)
            }
        }
    }
}

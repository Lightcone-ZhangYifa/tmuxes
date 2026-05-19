package com.tmuxes.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import io.github.rosemoe.sora.widget.CodeEditor

/**
 * Transparent overlay view that draws ErrorLens-style inline diagnostic
 * messages at the end of each error/warning line in the [CodeEditor].
 *
 * Positioned as a sibling of the CodeEditor in a FrameLayout, this view
 * queries the editor's row positions and scroll state to render colored
 * text aligned to the end of each diagnostic line.
 *
 * IMPORTANT: This view must NOT intercept touch events — all touches
 * must pass through to the CodeEditor underneath.
 */
class ErrorLensOverlay(context: Context) : View(context) {

    init {
        // Ensure this overlay never intercepts touches, focus, or click events
        isClickable = false
        isFocusable = false
        isFocusableInTouchMode = false
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean = false
    override fun dispatchTouchEvent(event: MotionEvent?): Boolean = false

    data class InlineDiagnostic(
        val line: Int,
        val message: String,
        val isError: Boolean  // true = error (red), false = warning (yellow)
    )

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 32f
        typeface = Typeface.MONOSPACE
    }

    private var diagnostics: List<InlineDiagnostic> = emptyList()
    var editor: CodeEditor? = null

    // Catppuccin Mocha colors with alpha
    private val errorTextColor = 0xCCF38BA8.toInt()   // Red 80% alpha
    private val warningTextColor = 0xCCF9E2AF.toInt() // Yellow 80% alpha

    fun setDiagnosticMessages(diags: List<InlineDiagnostic>) {
        diagnostics = diags
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val ed = editor ?: return
        if (diagnostics.isEmpty()) return

        // Match font size to editor (slightly smaller)
        textPaint.textSize = ed.textSizePx * 0.85f
        textPaint.typeface = ed.typefaceText ?: Typeface.MONOSPACE

        val firstRow = ed.firstVisibleRow
        val lastRow = ed.lastVisibleRow
        val scrollX = ed.scrollX
        val scrollY = ed.scrollY

        for (diag in diagnostics) {
            // Map line to row — for simple cases, line == row (no word wrap)
            val row = diag.line
            if (row < firstRow || row > lastRow) continue

            // Get line end X position
            val content = ed.text ?: continue
            if (diag.line >= content.lineCount) continue
            val colCount = content.getColumnCount(diag.line)

            // Y position: row top + baseline offset, adjusted for scroll
            val rowTop = ed.getRowTop(row) - scrollY
            val rowBottom = ed.getRowBottom(row) - scrollY
            val baseline = rowTop + (rowBottom - rowTop) * 0.75f

            // X position: after the line's last character + gap
            val textRegionOffset = ed.measureTextRegionOffset()
            val lineEndX = ed.getCharOffsetX(diag.line, colCount) + textRegionOffset - scrollX
            val gapPx = ed.dpUnit * 16  // 16dp gap after line content

            // Set color based on severity
            textPaint.color = if (diag.isError) errorTextColor else warningTextColor

            // Draw the message
            val prefix = if (diag.isError) " \u2717 " else " \u26A0 "  // ✗ or ⚠
            canvas.drawText(prefix + diag.message, lineEndX + gapPx, baseline, textPaint)
        }
    }
}

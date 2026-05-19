package com.tmuxes.editor

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import io.github.rosemoe.sora.widget.CodeEditor

/**
 * A [Drawable] that renders ErrorLens-style inline diagnostic messages
 * at the end of each error/warning line in the [CodeEditor].
 *
 * Added via [android.view.View.getOverlay] so it draws on top of the
 * editor content WITHOUT intercepting any touch events.
 */
class ErrorLensDrawable : Drawable() {

    enum class Position {
        EndOfLine,
        Inline,
        HoverOnly
    }

    data class InlineDiagnostic(
        val line: Int,
        val message: String,
        val isError: Boolean
    )

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 32f
        typeface = Typeface.MONOSPACE
    }

    private var diagnostics: List<InlineDiagnostic> = emptyList()
    var editor: CodeEditor? = null
    var position: Position = Position.EndOfLine
        set(value) {
            field = value
            invalidateSelf()
        }
    var fontScale: Float = 0.85f
        set(value) {
            field = value.coerceIn(0.6f, 1.2f)
            invalidateSelf()
        }

    private val errorTextColor = 0xCCF38BA8.toInt()
    private val warningTextColor = 0xCCF9E2AF.toInt()

    fun setDiagnosticMessages(diags: List<InlineDiagnostic>) {
        diagnostics = diags
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        // draw() is called on the main UI thread during the editor's
        // drawing pass, inside View.draw → ViewRootImpl.drawSoftware.
        // An uncaught exception here propagates to the main thread and
        // crashes the process (installUncaughtExceptionHandler in
        // TmuxesApp deliberately does NOT swallow main-thread
        // exceptions.
        //
        // Every accessor below reads live state from Sora's CodeEditor,
        // which is concurrently mutated by Sora's internal layout
        // ThreadPoolExecutor: firstVisibleRow/lastVisibleRow depend on
        // the layout being current; content.lineCount can change
        // between the >= check and getColumnCount; getRowTop /
        // getRowBottom / getCharOffsetX / measureTextRegionOffset can
        // all throw AIOOBE when the editor is mid-rebuild. The only
        // cost of swallowing is a missing diagnostic overlay for one
        // frame — far better than a process kill.
        try {
            val ed = editor ?: return
            if (diagnostics.isEmpty()) return
            if (position == Position.HoverOnly) return

            textPaint.textSize = ed.textSizePx * fontScale
            textPaint.typeface = ed.typefaceText ?: Typeface.MONOSPACE

            val firstRow = ed.firstVisibleRow
            val lastRow = ed.lastVisibleRow
            val scrollX = ed.scrollX
            val scrollY = ed.scrollY

            for (diag in diagnostics) {
                // Each diagnostic is wrapped individually so one bad
                // row index doesn't drop the rest of the overlay.
                try {
                    val row = diag.line
                    if (row < firstRow || row > lastRow) continue

                    val content = ed.text ?: continue
                    if (diag.line >= content.lineCount) continue
                    val colCount = content.getColumnCount(diag.line)

                    val rowTop = ed.getRowTop(row) - scrollY
                    val rowBottom = ed.getRowBottom(row) - scrollY
                    val baseline = rowTop + (rowBottom - rowTop) * 0.75f

                    val textRegionOffset = ed.measureTextRegionOffset()
                    val lineEndX = ed.getCharOffsetX(diag.line, colCount) + textRegionOffset - scrollX
                    val gapPx = ed.dpUnit * 16

                    textPaint.color = if (diag.isError) errorTextColor else warningTextColor

                    val prefix = if (diag.isError) " \u2717 " else " \u26A0 "
                    val message = prefix + diag.message
                    val desiredX = lineEndX + gapPx
                    val x = if (position == Position.Inline) {
                        val maxX = bounds.width() - gapPx - textPaint.measureText(message)
                        desiredX.coerceAtMost(maxX).coerceAtLeast(textRegionOffset + gapPx)
                    } else {
                        desiredX
                    }
                    canvas.drawText(message, x, baseline, textPaint)
                } catch (_: Throwable) {
                    // Skip this diagnostic — the next invalidateSelf
                    // will retry with fresh editor state.
                }
            }
        } catch (_: Throwable) {
            // Swallow the whole frame — never crash the process on
            // a draw-pass failure. A subsequent invalidateSelf will
            // retry with fresh state.
        }
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

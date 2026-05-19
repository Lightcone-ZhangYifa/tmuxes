package com.tmuxes.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.os.Build
import android.util.TypedValue
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import com.tmuxes.R
import com.tmuxes.terminal.emulator.TerminalColors
import com.tmuxes.terminal.emulator.TerminalEmulator
import com.tmuxes.terminal.emulator.TerminalSnapshot
import com.tmuxes.terminal.view.CursorStyle
import com.tmuxes.terminal.view.TerminalRenderer

/**
 * Renders terminal content to a [Bitmap] for use in home screen widgets.
 *
 * Uses the same rendering logic as [com.tmuxes.terminal.view.TerminalView]:
 * per-cell foreground/background colors, bold/italic/underline/strikethrough
 * attributes, cursor rendering, CJK wide character support, and color scheme
 * remapping.
 *
 * The output bitmap matches the in-app terminal appearance exactly.
 */
class TerminalBitmapRenderer(context: Context) {

    private val resources = context.resources
    private val displayMetrics = resources.displayMetrics

    // Load JetBrains Mono, fall back to system monospace
    private val defaultTypeface: Typeface =
        try { ResourcesCompat.getFont(context, R.font.jetbrains_mono) ?: Typeface.MONOSPACE }
        catch (_: Exception) { Typeface.MONOSPACE }

    fun resolveTypeface(fontFamily: String, fontWeight: String = "normal"): Typeface {
        val base = when (fontFamily.lowercase()) {
        "jetbrains_mono", "jetbrains mono", "" -> defaultTypeface
        "monospace" -> Typeface.MONOSPACE
        "sans_serif", "sans-serif" -> Typeface.SANS_SERIF
        "serif" -> Typeface.SERIF
        else -> defaultTypeface
        }
        val weight = when (fontWeight.lowercase()) {
            "thin" -> 100
            "light" -> 300
            "medium" -> 500
            "bold" -> 700
            else -> 400
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(base, weight, false)
        } else {
            Typeface.create(base, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    private val renderer = TerminalRenderer()

    private data class CellMetrics(val cellWidth: Float, val cellHeight: Float, val baselineOffset: Float)

    private fun updateCellMetrics(
        fontSizePx: Float,
        fontFamily: String,
        fontWeight: String,
        lineSpacingPercent: Int
    ): CellMetrics {
        val spacing = (lineSpacingPercent / 100f).coerceIn(0.75f, 2f)
        val (cellWidth, cellHeight, baselineOffset) =
            renderer.updatePaints(fontSizePx, resolveTypeface(fontFamily, fontWeight), spacing)
        return CellMetrics(cellWidth, cellHeight, baselineOffset)
    }

    /**
     * Maximum bitmap dimension.  On API 31+ RemoteViews are stored via
     * ContentProvider (not Binder), so the old 1 MB limit no longer applies.
     * 4096 is safe on all modern devices.
     */
    private val maxBitmapDimension = 4096

    /**
     * Cap [widthPx]×[heightPx] so neither exceeds [maxBitmapDimension],
     * preserving the original aspect ratio (both axes scaled equally).
     */
    fun capDimensions(widthPx: Int, heightPx: Int): Pair<Int, Int> {
        val w = widthPx.coerceAtLeast(1)
        val h = heightPx.coerceAtLeast(1)
        val scale = minOf(
            maxBitmapDimension.toFloat() / w,
            maxBitmapDimension.toFloat() / h,
            1f
        )
        return (w * scale).toInt().coerceAtLeast(1) to
               (h * scale).toInt().coerceAtLeast(1)
    }

    // Title bar paint
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        isLinearText = true
    }

    /**
     * Render a terminal preview to a [Bitmap].
     *
     * @param capturedOutput Raw output from `tmux capture-pane -e -p` with ANSI escape sequences
     * @param widthPx Widget width in pixels
     * @param heightPx Widget height in pixels
     * @param scheme Color scheme to apply
     * @param sessionName Name shown in the title bar
     * @param serverName Server name shown in the title bar
     * @param isConnected Whether the server is currently connected
     * @param connectionState Connection state string for status indicator
     */
    /**
     * Draw a semi-transparent background, clearing first so the alpha channel
     * is honoured on the home screen.
     */
    private fun drawTransparentBackground(canvas: Canvas, bgColor: Int, opacity: Int) {
        val bgAlpha = (opacity * 255 / 100).coerceIn(0, 255)
        val bg = ColorUtils.setAlphaComponent(bgColor, bgAlpha)
        canvas.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        canvas.drawColor(bg)
    }

    @Synchronized
    fun render(
        capturedOutput: String,
        widthPx: Int,
        heightPx: Int,
        scheme: TerminalColors.ColorScheme,
        sessionName: String,
        serverName: String,
        isConnected: Boolean,
        connectionState: String,
        opacity: Int = 100,
        showTitleBar: Boolean = true,
        fontFamily: String = "",
        fontWeight: String = "normal",
        cursorStyle: CursorStyle = CursorStyle.BLOCK,
        showCursor: Boolean = true,
        cursorColor: Int = 0,
        titleAccentColor: Int = 0,
        boldIsBright: Boolean = false,
        underlineStyle: String = "solid",
        lineSpacing: Int = 100,
        terminalPadding: Int = 0
    ): Bitmap {
        val (w, h) = capDimensions(widthPx, heightPx)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val fontSizeSp = computeOptimalFontSize(w, h)
        val fontSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, fontSizeSp, displayMetrics)
        val (cellWidth, cellHeight, baselineOffset) =
            updateCellMetrics(fontSizePx, fontFamily, fontWeight, lineSpacing)

        val titleBarHeight = if (showTitleBar) (cellHeight * 1.2f).coerceAtLeast(20f) else 0f
        val paddingPx = (terminalPadding.coerceAtLeast(0) * displayMetrics.density)
        val contentLeft = paddingPx
        val contentTop = titleBarHeight + paddingPx

        // How many rows/cols fit in the content area
        val contentHeight = (h - titleBarHeight - paddingPx * 2).coerceAtLeast(cellHeight)
        val contentWidth = (w - paddingPx * 2).coerceAtLeast(cellWidth)
        val maxRows = (contentHeight / cellHeight).toInt().coerceAtLeast(1)
        val maxCols = (contentWidth / cellWidth).toInt().coerceAtLeast(1)

        // Parse the captured output through a TerminalEmulator to get cells with colors
        val emulator = TerminalEmulator(maxRows, maxCols)
        if (capturedOutput.isNotBlank()) {
            emulator.processInput(capturedOutput.toByteArray(Charsets.UTF_8))
        }
        val snapshot = emulator.snapshot()

        // Fill background with opacity
        drawTransparentBackground(canvas, scheme.background, opacity)

        // Draw title bar
        if (showTitleBar) {
            drawTitleBar(
                canvas, 0f, 0f, w.toFloat(), titleBarHeight,
                scheme, sessionName, serverName, isConnected, connectionState,
                fontSizePx * 0.8f, titleAccentColor
            )
        }

        // Draw terminal content
        for (row in 0 until maxRows) {
            val y = contentTop + row * cellHeight
            val line = snapshot.getLine(row)
            canvas.save()
            canvas.translate(contentLeft, 0f)
            renderer.drawLine(
                canvas, line, y, maxCols, cellWidth, cellHeight, baselineOffset,
                scheme, boldIsBright, underlineStyle
            )
            canvas.restore()
        }

        // Draw cursor (always at bottom for a captured pane view)
        if (isConnected && showCursor) {
            val cursorRow = snapshot.cursorRow
            val cursorCol = snapshot.cursorCol
            if (cursorRow in 0 until maxRows && cursorCol in 0 until maxCols) {
                renderer.drawCursor(canvas, snapshot, cursorRow, cursorCol,
                    contentTop + cursorRow * cellHeight, contentLeft + cursorCol * cellWidth,
                    cellWidth, cellHeight, baselineOffset, scheme, cursorStyle,
                    cursorColor.takeIf { it != 0 })
            }
        }

        // If disconnected, draw a subtle overlay
        if (!isConnected && capturedOutput.isNotBlank()) {
            canvas.drawColor(0x40000000) // Semi-transparent dark overlay
        }

        return bitmap
    }

    /**
     * Render directly from a live [TerminalEmulator] buffer — no parsing needed.
     * Shows the bottom N rows that fit in the widget (cropped view).
     */
    @Synchronized
    fun renderFromEmulator(
        emulator: TerminalEmulator,
        widthPx: Int,
        heightPx: Int,
        scheme: TerminalColors.ColorScheme,
        sessionName: String,
        serverName: String,
        isConnected: Boolean,
        connectionState: String,
        fontSizeOverrideSp: Float? = null,
        opacity: Int = 100,
        showTitleBar: Boolean = true,
        fontFamily: String = "",
        fontWeight: String = "normal",
        cursorStyle: CursorStyle = CursorStyle.BLOCK,
        showCursor: Boolean = true,
        cursorColor: Int = 0,
        titleAccentColor: Int = 0,
        boldIsBright: Boolean = false,
        underlineStyle: String = "solid",
        lineSpacing: Int = 100,
        terminalPadding: Int = 0
    ): Bitmap {
        val (w, h) = capDimensions(widthPx, heightPx)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val fontSizeSp = fontSizeOverrideSp ?: computeOptimalFontSize(w, h)
        val fontSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, fontSizeSp, displayMetrics)
        val (cellWidth, cellHeight, baselineOffset) =
            updateCellMetrics(fontSizePx, fontFamily, fontWeight, lineSpacing)

        val titleBarHeight = if (showTitleBar) (cellHeight * 1.2f).coerceAtLeast(20f) else 0f
        val paddingPx = (terminalPadding.coerceAtLeast(0) * displayMetrics.density)
        val contentLeft = paddingPx
        val contentTop = titleBarHeight + paddingPx
        val contentHeight = (h - titleBarHeight - paddingPx * 2).coerceAtLeast(cellHeight)
        val contentWidth = (w - paddingPx * 2).coerceAtLeast(cellWidth)
        val visibleRows = (contentHeight / cellHeight).toInt().coerceAtLeast(1)
        val visibleCols = (contentWidth / cellWidth).toInt().coerceAtLeast(1)

        val snapshot = emulator.snapshot()

        // Fill background with opacity
        drawTransparentBackground(canvas, scheme.background, opacity)

        if (showTitleBar) {
            drawTitleBar(canvas, 0f, 0f, w.toFloat(), titleBarHeight,
                scheme, sessionName, serverName, isConnected, connectionState,
                fontSizePx * 0.8f, titleAccentColor)
        }

        // Show the BOTTOM visibleRows of the buffer (cropped view)
        val bufferRows = snapshot.rows
        val startRow = (bufferRows - visibleRows).coerceAtLeast(0)

        for (i in 0 until visibleRows) {
            val bufRow = startRow + i
            if (bufRow < bufferRows) {
                val y = contentTop + i * cellHeight
                val line = snapshot.getLine(bufRow)
                canvas.save()
                canvas.translate(contentLeft, 0f)
                renderer.drawLine(
                    canvas, line, y, visibleCols, cellWidth, cellHeight, baselineOffset,
                    scheme, boldIsBright, underlineStyle
                )
                canvas.restore()
            }
        }

        // Draw cursor
        if (isConnected && showCursor) {
            val cursorRow = snapshot.cursorRow
            val cursorCol = snapshot.cursorCol
            val displayRow = cursorRow - startRow
            if (displayRow in 0 until visibleRows && cursorCol in 0 until visibleCols) {
                renderer.drawCursor(canvas, snapshot, cursorRow, cursorCol,
                    contentTop + displayRow * cellHeight, contentLeft + cursorCol * cellWidth,
                    cellWidth, cellHeight, baselineOffset, scheme, cursorStyle,
                    cursorColor.takeIf { it != 0 })
            }
        }

        if (!isConnected) {
            canvas.drawColor(0x40000000)
        }

        return bitmap
    }

    fun computeOptimalFontSize(widthPx: Int, heightPx: Int): Float {
        val targetCols = 40
        val spToPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1f, displayMetrics)
        val maxFromWidth = (widthPx.toFloat() / targetCols / spToPx / 0.6f)
        return maxFromWidth.coerceIn(7f, 14f)
    }

    @Synchronized
    fun computeTerminalSize(
        widthPx: Int,
        heightPx: Int,
        fontSizeSp: Float,
        showTitleBar: Boolean = true,
        fontFamily: String = "",
        fontWeight: String = "normal",
        lineSpacing: Int = 100,
        terminalPadding: Int = 0
    ): Pair<Int, Int> {
        val fontSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, fontSizeSp, displayMetrics)
        val (cellWidth, cellHeight, _) =
            updateCellMetrics(fontSizePx, fontFamily, fontWeight, lineSpacing)
        val titleBarHeight = if (showTitleBar) (cellHeight * 1.2f).coerceAtLeast(20f) else 0f
        val paddingPx = (terminalPadding.coerceAtLeast(0) * displayMetrics.density)
        val contentHeight = (heightPx - titleBarHeight - paddingPx * 2).coerceAtLeast(cellHeight)
        val contentWidth = (widthPx - paddingPx * 2).coerceAtLeast(cellWidth)
        val cols = (contentWidth / cellWidth).toInt().coerceAtLeast(10)
        val rows = (contentHeight / cellHeight).toInt().coerceAtLeast(3)
        return cols to rows
    }

    /**
     * Swap width/height for 90/270 degree rotations so the terminal
     * content fills the rotated dimensions naturally.
     */
    fun adjustDimensionsForOrientation(widthPx: Int, heightPx: Int, orientation: Int): Pair<Int, Int> {
        return if (orientation == 90 || orientation == 270) heightPx to widthPx else widthPx to heightPx
    }

    /**
     * Rotates a rendered bitmap by the given degrees (0, 90, 180, 270).
     * For 90/270, the output dimensions are swapped relative to the input.
     * The source bitmap is recycled if a new bitmap is created.
     */
    fun rotateBitmap(source: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return source
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (rotated !== source) source.recycle()
        return rotated
    }

    private fun drawTitleBar(
        canvas: Canvas, x: Float, y: Float, width: Float, height: Float,
        scheme: TerminalColors.ColorScheme,
        sessionName: String, serverName: String,
        isConnected: Boolean, connectionState: String,
        fontSize: Float,
        titleAccentColor: Int = 0
    ) {
        // Title bar background (slightly darker than terminal bg)
        val titleBg = darkenColor(scheme.background, 0.7f)
        renderer.bgPaint.color = titleBg
        canvas.drawRect(x, y, x + width, y + height, renderer.bgPaint)

        if (titleAccentColor != 0) {
            renderer.bgPaint.color = titleAccentColor
            val accentHeight = (fontSize * 0.12f).coerceAtLeast(1f)
            canvas.drawRect(x, y + height - accentHeight, x + width, y + height, renderer.bgPaint)
        }

        titlePaint.textSize = fontSize
        titlePaint.typeface = Typeface.create(defaultTypeface, Typeface.BOLD)

        val titleBaseline = y + height * 0.7f

        // Status dot
        val dotRadius = fontSize * 0.25f
        val dotCx = x + 8f + dotRadius
        val dotCy = y + height / 2f
        val dotColor = when {
            isConnected -> 0xFFA6E3A1.toInt() // Green
            connectionState == com.tmuxes.ssh.ServerStatus.AUTH_FAILED.name -> 0xFFF38BA8.toInt() // Red
            connectionState == com.tmuxes.ssh.ServerStatus.CONNECTING.name -> 0xFFF9E2AF.toInt() // Yellow
            connectionState == com.tmuxes.ssh.ServerStatus.NETWORK_ERROR.name -> 0xFFF38BA8.toInt() // Red
            else -> 0xFF6C7086.toInt() // Gray
        }
        renderer.bgPaint.color = dotColor
        canvas.drawCircle(dotCx, dotCy, dotRadius, renderer.bgPaint)

        // Session name
        titlePaint.color = scheme.foreground
        val nameX = dotCx + dotRadius + 6f
        canvas.drawText(sessionName, nameX, titleBaseline, titlePaint)

        // Server name (right-aligned, dimmer)
        if (serverName.isNotBlank()) {
            titlePaint.typeface = defaultTypeface
            titlePaint.color = 0xFF6C7086.toInt()
            titlePaint.textSize = fontSize * 0.9f
            val serverWidth = titlePaint.measureText(serverName)
            canvas.drawText(serverName, width - serverWidth - 8f, titleBaseline, titlePaint)
        }
    }

    private fun darkenColor(color: Int, factor: Float): Int {
        val a = (color ushr 24) and 0xFF
        val r = (((color ushr 16) and 0xFF) * factor).toInt()
        val g = (((color ushr 8) and 0xFF) * factor).toInt()
        val b = ((color and 0xFF) * factor).toInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

}

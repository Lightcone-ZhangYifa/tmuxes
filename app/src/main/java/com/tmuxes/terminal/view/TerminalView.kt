// allow-bypass-D5: hot terminal view; IME/draw/selection wraps are best-effort and self-logging would flood breadcrumbs
package com.tmuxes.terminal.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.Editable
import android.text.Selection
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.ActionMode
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.OverScroller
import androidx.core.content.res.ResourcesCompat
import com.tmuxes.R
import com.tmuxes.terminal.emulator.TerminalBuffer
import com.tmuxes.terminal.emulator.CellEncoding
import com.tmuxes.terminal.emulator.TerminalLine
import com.tmuxes.terminal.emulator.TerminalColors
import com.tmuxes.terminal.emulator.TerminalEmulator
import com.tmuxes.terminal.emulator.TerminalSnapshot
import androidx.core.graphics.ColorUtils
import com.tmuxes.util.AppLogger
import com.tmuxes.util.AppLogger.Category
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Callback interface for events emitted by [TerminalView].
 */
interface TerminalViewCallback {
    /** Raw key/escape-sequence data from the keyboard (hardware DPAD,
     *  IME function keys, ExtraKeysBar). */
    fun onKeyEvent(data: ByteArray)

    /** Text input from the IME that should be sent to the shell. */
    fun onTextInput(text: String)

    /**
     * IME-initiated paste text — distinct from [onTextInput] so the
     * receiver can apply bracketed-paste-mode wrapping (when the running
     * program enabled it via DECSET 2004) without also wrapping every
     * IME composition commit. Defaults to forwarding through
     * [onTextInput] for callers that don't care about the distinction.
     */
    fun onPasteText(text: String) { onTextInput(text) }

    /** Pinch-zoom committed a new font size (called once per pinch
     *  gesture, on scale-end — not on every onScale frame). */
    fun onFontSizeChanged(newSize: Float)

    /** The user selected text in the terminal. */
    fun onTextSelected(text: String)

    /** The user requested a paste from the system clipboard. */
    fun onPasteRequested() {}

    /**
     * Generic byte-stream injection from gestures (e.g. swipe → arrow
     * keys when in copy-mode). Distinct from [onKeyEvent] so the
     * traffic is identifiable in logs and the ViewModel can gate it
     * (e.g. block while a modal is open).
     */
    fun onSendBytes(data: ByteArray, reason: String)
}

/**
 * High-performance Android [View] that renders a terminal emulator screen.
 *
 * This is intentionally a classic View (not Jetpack Compose) because the
 * terminal requires direct [Canvas] rendering for acceptable frame rates when
 * drawing thousands of individually-styled character cells.
 *
 * Features:
 * - Cell-by-cell rendering with per-cell foreground/background, bold, italic,
 *   underline, and strikethrough attributes.
 * - Blinking block / bar / underline cursor.
 * - Scrollback via fling gestures.
 * - Pinch-to-zoom font size adjustment.
 * - Long-press text selection with start/end handles and highlighted region.
 * - Double-tap to select a word.
 * - Tap to position cursor or dismiss selection.
 */
class TerminalView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // -- Callback -----------------------------------------------------------

    var callback: TerminalViewCallback? = null

    // -- Extra key modifiers (CTRL/ALT/SHIFT from the extra keys bar) -------
    // Shared with KeyButton/ExtraKeysBar via com.tmuxes.ui.components.keybar.ModifierSnapshot.

    var extraKeyModifiers: com.tmuxes.ui.components.keybar.ModifierSnapshot =
        com.tmuxes.ui.components.keybar.ModifierSnapshot.NONE
    var onModifiersConsumed: (() -> Unit)? = null

    /**
     * Applies active extra-key modifiers to a byte sequence and consumes them.
     */
    private fun applyModifiers(data: ByteArray): ByteArray {
        val mods = extraKeyModifiers
        val result = ModifierKeys.apply(data, mods.ctrl, mods.alt, mods.shift, mods.meta)
        if (result !== data) onModifiersConsumed?.invoke()
        return result
    }

    // -- Emulator reference -------------------------------------------------

    private var emulator: TerminalEmulator? = null

    /** Public read-only access to the currently bound emulator. */
    val currentEmulator: TerminalEmulator? get() = emulator

    /**
     * Resize bus for the bound session. Set by [com.tmuxes.ui.screens.terminal.TerminalScreen]
     * when wiring this view to a session. All resize requests (size change,
     * font change, attach/detach) flow through here. Null if not yet wired
     * — resize requests then become local-emulator-only no-ops.
     */
    var resizeBus: com.tmuxes.session.SessionResizeBus? = null
        set(value) {
            field = value
            if (value != null && isAttachedToWindow) {
                value.setVisible(true)
                requestSizeRecompute()
            }
        }

    // -- Rendering configuration -------------------------------------------

    /** Font size in scaled pixels. */
    var fontSizeSp: Float = 13f
        set(value) {
            field = value.coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP)
            updateFontMetrics()
            requestSizeRecompute()
            invalidate()
        }

    /** Monospace font family used for terminal text. */
    var fontFamily: Typeface = loadJetBrainsMono(context) ?: Typeface.MONOSPACE
        set(value) {
            field = value
            updateFontMetrics()
            requestSizeRecompute()
            invalidate()
        }

    /** Active color scheme; replaces ANSI palette colors. */
    var colorScheme: TerminalColors.ColorScheme? = null
        set(value) {
            field = value
            invalidate()
        }

    /** Underline style: solid, double, dotted, or dashed. */
    var underlineStyle: String = "solid"
        set(value) { field = value; invalidate() }

    /** Cursor appearance: block, underline, or vertical bar. */
    var cursorStyle: CursorStyle = CursorStyle.BLOCK
        set(value) { field = value; invalidate() }

    /** Explicit cursor color; null = use scheme foreground. */
    var cursorColor: Int? = null
        set(value) { field = value; invalidate() }

    /** Line spacing multiplier (1.0 = default, no extra spacing). */
    var lineSpacing: Float = 1.0f
        set(value) {
            field = value.coerceAtLeast(0.5f)
            updateFontMetrics()
            requestSizeRecompute()
            invalidate()
        }

    /** Inner padding in dp applied on all four sides. */
    var terminalPadding: Int = 0
        set(value) {
            field = value.coerceAtLeast(0)
            requestSizeRecompute()
            invalidate()
        }

    /** Color for the text selection highlight. */
    var selectionHighlightColor: Int = 0x6033B5E5.toInt()
        set(value) {
            field = value
            selectionPaint.color = value
            invalidate()
        }

    /** Background opacity percentage (0 = fully transparent, 100 = opaque). */
    var backgroundOpacity: Int = 100
        set(value) {
            field = value.coerceIn(0, 100)
            invalidate()
        }

    /** Whether the cursor should blink at all. */
    var cursorBlinkEnabled: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            if (isAttachedToWindow) {
                if (value) {
                    cursorBlinkOn = true
                    cursorBlinkHandler.removeCallbacks(cursorBlinkRunnable)
                    cursorBlinkHandler.postDelayed(cursorBlinkRunnable, cursorBlinkSpeed)
                } else {
                    cursorBlinkHandler.removeCallbacks(cursorBlinkRunnable)
                    cursorBlinkOn = true
                    invalidate()
                }
            }
        }

    /** Whether bold text uses bright ANSI colors instead of normal ones. */
    var boldIsBright: Boolean = false

    /** Whether to auto-scroll to bottom when new output arrives. */
    var autoScrollOnOutput: Boolean = true

    /** Whether to scroll to bottom on keystroke when scrolled back. */
    var scrollOnKeystroke: Boolean = true

    /** Whether double-tap selects a word. */
    var doubleTapWordSelect: Boolean = true

    /**
     * Volume-key handling mode.
     *
     * - `"volume"` (default): pass keys through so Android adjusts media volume.
     * - `"esc_tab"`: Vol-Down sends `\x1b` (Esc), Vol-Up sends `\t` (Tab).
     * - `"disabled"`: swallow the key — no volume change, no terminal byte.
     *
     * Anything else is treated as `"volume"` (forward-compat with future modes).
     */
    var volumeKeysAction: String = "volume"

    /** Cursor blink speed in milliseconds. */
    var cursorBlinkSpeed: Long = 530L
        set(value) {
            val clamped = value.coerceAtLeast(100L)
            if (field == clamped) return
            field = clamped
            if (isAttachedToWindow && cursorBlinkEnabled) {
                cursorBlinkHandler.removeCallbacks(cursorBlinkRunnable)
                cursorBlinkHandler.postDelayed(cursorBlinkRunnable, clamped)
            }
        }

    // -- Shared renderer (paints, drawLine, drawCursor) ---------------------

    private val renderer = TerminalRenderer()

    /** Width of a single monospace character cell in pixels. */
    private var cellWidth: Float = 0f

    /** Height of a single terminal row in pixels. */
    private var cellHeight: Float = 0f

    /** Baseline offset within a cell for drawing text. */
    private var baselineOffset: Float = 0f

    // -- Scrollback ---------------------------------------------------------

    /** Current scroll offset in lines (0 = viewing live screen, >0 = scrolled back). */
    private var scrollOffset: Int = 0

    private val scroller = OverScroller(context)

    // -- Cursor blink -------------------------------------------------------

    private var cursorVisible: Boolean = true
    private var cursorBlinkOn: Boolean = true
    private val cursorBlinkHandler = Handler(Looper.getMainLooper())
    private val cursorBlinkRunnable = object : Runnable {
        override fun run() {
            // Main-thread Handler callback — an unguarded throw here
            // propagates to the ActivityThread UI loop. invalidate()
            // and postDelayed are normally safe, but a view detach
            // mid-callback can still race unexpectedly.
            try {
                cursorBlinkOn = !cursorBlinkOn
                invalidate()
                cursorBlinkHandler.postDelayed(this, cursorBlinkSpeed)
            } catch (_: Throwable) {}
        }
    }

    // -- Visual bell ----------------------------------------------------------

    private var visualBellActive = false

    /** Triggers a brief white flash overlay to indicate a bell event. */
    fun flashVisualBell() {
        try {
            visualBellActive = true
            invalidate()
            postDelayed({
                try { visualBellActive = false; invalidate() } catch (_: Throwable) {}
            }, 100)
        } catch (_: Throwable) {}
    }

    // -- Text selection -----------------------------------------------------

    private var selectionActive: Boolean = false
    private var selectionStartRow: Int = -1
    private var selectionStartCol: Int = -1
    private var selectionEndRow: Int = -1
    private var selectionEndCol: Int = -1

    private val selectionPaint = Paint().apply {
        color = selectionHighlightColor
    }

    // -- ActionMode (native copy/paste floating toolbar) ---------------------

    private var actionMode: ActionMode? = null

    private fun startTextSelectionActionMode() {
        // ActionMode.Callback methods run on the main thread and the
        // framework does NOT catch exceptions from them — any throw from
        // `extractSelectedText()` (AIOOBE on a buffer that shrank mid-
        // selection), `normalizeSelection()` (uninitialized selection
        // fields), or the user-supplied `callback.onPasteRequested()`
        // lambda escapes into the UI thread and crashes the app. Wrap
        // every callback body the same way iter 38 wrapped onDraw /
        // onTouchEvent.
        try { actionMode?.finish() } catch (_: Throwable) {}
        try {
            actionMode = startActionMode(object : ActionMode.Callback2() {
                override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                    return try {
                        menu.add(0, MENU_COPY, 0, android.R.string.copy)
                        menu.add(0, MENU_PASTE, 1, android.R.string.paste)
                        menu.add(0, MENU_SELECT_ALL, 2, android.R.string.selectAll)
                        true
                    } catch (_: Throwable) { false }
                }

                override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

                override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                    try {
                        when (item.itemId) {
                            MENU_COPY -> {
                                try { extractSelectedText() } catch (_: Throwable) {}
                                try { clearSelection() } catch (_: Throwable) {}
                                try { mode.finish() } catch (_: Throwable) {}
                            }
                            MENU_PASTE -> {
                                try { callback?.onPasteRequested() } catch (_: Throwable) {}
                                try { clearSelection() } catch (_: Throwable) {}
                                try { mode.finish() } catch (_: Throwable) {}
                            }
                            MENU_SELECT_ALL -> {
                                try { selectAll() } catch (_: Throwable) {}
                                try { invalidate() } catch (_: Throwable) {}
                            }
                        }
                    } catch (_: Throwable) {}
                    return true
                }

                override fun onDestroyActionMode(mode: ActionMode) {
                    actionMode = null
                }

                override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
                    // Position the floating toolbar near the selection.
                    // normalizeSelection can read -1 selection indices during
                    // a lifecycle race — compute defensively.
                    try {
                        val (startRow, startCol, endRow, _) = normalizeSelection()
                        val top = (startRow * cellHeight).toInt()
                        val left = (startCol * cellWidth).toInt()
                        val bottom = ((endRow + 1) * cellHeight).toInt()
                        outRect.set(left, top, width, bottom)
                    } catch (_: Throwable) {
                        // Fallback to full view if selection math fails.
                        outRect.set(0, 0, width, height)
                    }
                }
            }, ActionMode.TYPE_FLOATING)
        } catch (_: Throwable) {
            // startActionMode itself can throw on exotic Android versions /
            // accessibility services — never crash the terminal on a long-press.
            actionMode = null
        }
    }

    private fun dismissActionMode() {
        actionMode?.finish()
        actionMode = null
    }

    private fun selectAll() {
        val emu = emulator ?: return
        selectionActive = true
        selectionStartRow = 0
        selectionStartCol = 0
        selectionEndRow = emu.buffer.rows - 1
        selectionEndCol = emu.buffer.columns - 1
    }

    // -- Gesture detectors --------------------------------------------------

    private val gestureRouter: TerminalGestureRouter
    private val gestureDetector: GestureDetector
    private val scaleGestureDetector: ScaleGestureDetector

    /** Public router knob — TerminalScreen syncs from settings into these. */
    var swipeMode: TerminalGestureRouter.SwipeMode
        get() = gestureRouter.swipeMode
        set(v) { gestureRouter.swipeMode = v }
    var linesPerArrow: Int
        get() = gestureRouter.linesPerArrow
        set(v) { gestureRouter.linesPerArrow = v }
    var copyModeActive: Boolean
        get() = gestureRouter.copyModeActive
        set(v) { gestureRouter.copyModeActive = v }
    var pinchZoomEnabled: Boolean
        get() = gestureRouter.pinchZoomEnabled
        set(v) { gestureRouter.pinchZoomEnabled = v }

    // -- Constants ----------------------------------------------------------

    companion object {
        private const val MIN_FONT_SIZE_SP = 6f
        private const val MAX_FONT_SIZE_SP = 36f
        private const val CURSOR_BLINK_INTERVAL_MS = 530L
        private const val MENU_COPY = 1
        private const val MENU_PASTE = 2
        private const val MENU_SELECT_ALL = 3

        /** Load JetBrains Mono from bundled resources, falling back to null. */
        fun loadJetBrainsMono(context: Context): Typeface? {
            return try {
                ResourcesCompat.getFont(context, R.font.jetbrains_mono)
            } catch (_: Exception) {
                null
            }
        }
    }

    // -- Initialization -----------------------------------------------------

    init {
        isFocusable = true
        isFocusableInTouchMode = true

        gestureRouter = TerminalGestureRouter(TerminalViewGestureHost())
        gestureRouter.doubleTapWordSelect = doubleTapWordSelect
        gestureDetector = GestureDetector(context, gestureRouter)
        scaleGestureDetector = ScaleGestureDetector(context, gestureRouter.scaleListener)

        updateFontMetrics()
    }

    // -- Public API ---------------------------------------------------------

    /**
     * Binds a [TerminalEmulator] to this view.  The view will render the
     * emulator's buffer and cursor state on each draw.
     */
    /** Content listener registered on the current emulator. */
    private var viewContentListener: (() -> Unit)? = null
    private val imeEditable: Editable = Editable.Factory.getInstance().newEditable("")

    fun bindTerminalEmulator(emulator: TerminalEmulator?) {
        val swap = this.emulator !== emulator
        AppLogger.i(Category.TERMINAL) {
            "tv.bind emu=${emulator?.hashCode()} prev=${this.emulator?.hashCode()} swap=$swap"
        }
        // Called from the AndroidView factory/update blocks (wrapped by
        // iter 39) AND from setTerminalEmulator (indirect path via
        // SessionCoordinator callbacks that may NOT be wrapped). Guard
        // each step individually so a torn-down emulator state cannot
        // crash the rebind path.
        try {
            if (swap) {
                try { detachContentListener(this.emulator) } catch (_: Throwable) {}
                this.emulator = emulator
                scrollOffset = 0
                try { clearSelection() } catch (_: Throwable) {}
            }

            try { attachContentListener(emulator) } catch (_: Throwable) {}
            try { requestSizeRecompute() } catch (_: Throwable) {}
            try { invalidate() } catch (_: Throwable) {}
        } catch (t: Throwable) {
            AppLogger.w(Category.TERMINAL) { "tv.bind ✗ ${t.javaClass.simpleName}: ${t.message}" }
        }
    }

    fun setTerminalEmulator(emulator: TerminalEmulator) {
        bindTerminalEmulator(emulator)
    }

    fun releaseInputFocus() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(windowToken, 0)
        clearFocus()
        clearImeEditable()
    }

    private fun attachContentListener(target: TerminalEmulator?) {
        val emu = target ?: return
        val listener = viewContentListener ?: {
            if (autoScrollOnOutput && scrollOffset > 0) {
                scrollOffset = 0
            }
            postInvalidate()
        }.also {
            viewContentListener = it
        }
        emu.removeContentListener(listener)
        emu.addContentListener(listener)
    }

    private fun detachContentListener(target: TerminalEmulator?) {
        val listener = viewContentListener ?: return
        target?.removeContentListener(listener)
    }

    /**
     * The single, canonical resize entry point for [TerminalView]. Posts
     * to the next frame so View dimensions are settled, then computes
     * the current cell-based size and routes it through [resizeBus] via
     * its `setSize(cols, rows)` method. Every caller (size change, font
     * change, padding change, attach, bind) goes through here.
     */
    private fun requestSizeRecompute() {
        if (!isAttachedToWindow) return
        try {
            post { try { applySizeRecompute() } catch (_: Throwable) {} }
        } catch (_: Throwable) {}
    }

    /**
     * Compute current terminal cols/rows from view metrics, push to the
     * local emulator, and notify the [resizeBus]. Never throws —
     * wrapped because View.post lands on the choreographer thread and
     * an escape there crashes the app.
     */
    private fun applySizeRecompute() {
        try {
            val emu = emulator ?: return
            if (width <= 0 || height <= 0 || cellWidth <= 0f || cellHeight <= 0f) return

            val (cols, rows) = computeTerminalSize()
            if (cols <= 0 || rows <= 0) return
            val oldCols = emu.buffer.columns
            val oldRows = emu.buffer.rows
            if (cols != oldCols || rows != oldRows) {
                AppLogger.i(Category.TERMINAL) { "tv.resize ${oldCols}x${oldRows} → ${cols}x${rows}" }
                scrollOffset = 0
                emu.resize(rows, cols)
            }
            // Bus dedupes — it's safe to call every recompute.
            resizeBus?.setSize(cols, rows)
        } catch (t: Throwable) {
            AppLogger.w(Category.TERMINAL) { "tv.resize ✗ ${t.javaClass.simpleName}: ${t.message}" }
        }
    }

    /**
     * Scrolls the view to show the live terminal output (scroll offset 0).
     */
    fun scrollToBottom() {
        scrollOffset = 0
        invalidate()
    }

    /**
     * Computes the terminal dimensions (columns, rows) that fit in the
     * current view size with the current font metrics.
     *
     * @return A [Pair] of (columns, rows). Returns (80, 24) if the view
     *         has not been laid out yet.
     */
    fun computeTerminalSize(): Pair<Int, Int> {
        if (width == 0 || height == 0 || cellWidth == 0f || cellHeight == 0f) {
            return Pair(80, 24)
        }
        val paddingPx = (terminalPadding * resources.displayMetrics.density).toInt()
        val usableWidth = width - paddingPx * 2
        val usableHeight = height - paddingPx * 2
        val cols = (usableWidth / cellWidth).toInt().coerceAtLeast(1)
        val rows = (usableHeight / cellHeight).toInt().coerceAtLeast(1)
        return Pair(cols, rows)
    }

    // -- Font metrics -------------------------------------------------------

    private fun updateFontMetrics() {
        val sizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, fontSizeSp, resources.displayMetrics
        )

        val (cw, ch, bl) = renderer.updatePaints(sizePx, fontFamily, lineSpacing)
        cellWidth = cw
        cellHeight = ch
        baselineOffset = bl
    }

    // -- Drawing ------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // onDraw runs on the main thread; an uncaught exception here would
        // crash the app. Wrap the whole body — better to show a blank frame
        // than to kill the process. Concrete risks: snapshot can race with
        // terminal buffer resize, drawScreenRow can hit AIOOBE on a stale
        // cell-width assumption, cursor draw can NPE if the emulator is
        // being torn down on another thread.
        try {
            val emu = emulator ?: return
            val snapshot = emu.snapshot()

            val scheme = colorScheme
            val defaultBg = scheme?.background ?: CellEncoding.DEFAULT_BG

            // Fill background with opacity applied
            val bgWithAlpha = ColorUtils.setAlphaComponent(defaultBg, backgroundOpacity * 255 / 100)
            canvas.drawColor(bgWithAlpha)

            // Apply terminal padding
            val paddingPx = (terminalPadding * resources.displayMetrics.density)
            val hasPadding = paddingPx > 0f
            if (hasPadding) {
                canvas.save()
                canvas.translate(paddingPx, paddingPx)
                canvas.clipRect(0f, 0f, width - paddingPx * 2, height - paddingPx * 2)
            }

            // Render to the VIEW's size (how many cells fit on screen), not the
            // buffer's size. During resize transitions the buffer may temporarily
            // have different dimensions from the view — using view size ensures
            // we always fill the visible area. Out-of-range reads from the buffer
            // return blank cells, which is the correct behavior.
            val (viewCols, viewRows) = computeTerminalSize()
            val rows = viewRows
            val cols = viewCols
            val scrollbackLines = snapshot.getScrollbackLineCount()
            val maxScroll = scrollbackLines
            scrollOffset = scrollOffset.coerceIn(0, maxScroll)

            for (viewRow in 0 until rows) {
                val y = viewRow * cellHeight
                drawScreenRow(canvas, snapshot, viewRow, y, cols, rows)
            }

            // Draw cursor if on the visible screen area and not scrolled away
            if (scrollOffset == 0) {
                drawCursor(canvas, snapshot, rows, cols)
            }

            // Draw selection overlay
            if (selectionActive) {
                drawSelection(canvas, cols, rows)
            }

            if (hasPadding) {
                canvas.restore()
            }

            // Visual bell overlay (drawn on top of everything, full view)
            if (visualBellActive) {
                canvas.drawColor(0x40FFFFFF)
            }
        } catch (_: Throwable) {
            // Swallow — never crash the process on a draw failure.
            // A subsequent invalidate will retry with fresh state.
        }
    }

    /**
     * Draws a screen row, accounting for scroll offset.
     * All data comes from the immutable [snapshot] — no live buffer access.
     */
    private fun drawScreenRow(
        canvas: Canvas,
        snapshot: TerminalSnapshot,
        viewRow: Int,
        y: Float,
        cols: Int,
        totalRows: Int
    ) {
        val scrollbackLines = snapshot.getScrollbackLineCount()
        val effectiveScroll = scrollOffset.coerceAtMost(scrollbackLines)

        if (viewRow < effectiveScroll) {
            // Scrollback row
            val scrollbackIndex = effectiveScroll - 1 - viewRow
            val line = snapshot.getScrollbackLine(scrollbackIndex)
            drawLine(canvas, line, viewRow, y, cols)
        } else {
            // Live screen row — read from the immutable snapshot
            val screenRow = viewRow - effectiveScroll
            if (screenRow >= 0 && screenRow < snapshot.rows) {
                val line = snapshot.getLine(screenRow)
                drawLine(canvas, line, viewRow, y, cols)
            }
        }
    }

    /**
     * Renders a single [TerminalLine] of packed cells.
     * Delegates core cell rendering to [renderer].
     */
    private fun drawLine(
        canvas: Canvas,
        line: TerminalLine,
        viewRow: Int,
        y: Float,
        cols: Int
    ) {
        val scheme = colorScheme ?: return
        renderer.drawLine(canvas, line, y, cols, cellWidth, cellHeight, baselineOffset, scheme, boldIsBright, underlineStyle)
    }

    /**
     * Draws the terminal cursor using the immutable snapshot.
     * Delegates the actual drawing to [renderer].
     */
    private fun drawCursor(canvas: Canvas, snapshot: TerminalSnapshot, rows: Int, cols: Int) {
        if (!snapshot.cursorVisible || !cursorBlinkOn) return
        val scheme = colorScheme ?: return

        val cursorRow = snapshot.cursorRow
        val cursorCol = snapshot.cursorCol
        if (cursorRow < 0 || cursorRow >= rows || cursorCol < 0 || cursorCol >= cols) return

        val x = cursorCol * cellWidth
        val y = cursorRow * cellHeight

        renderer.drawCursor(canvas, snapshot, cursorRow, cursorCol,
            y, x, cellWidth, cellHeight, baselineOffset, scheme,
            cursorStyle, cursorColor)
    }

    /**
     * Draws the text selection highlight.
     */
    private fun drawSelection(canvas: Canvas, cols: Int, rows: Int) {
        if (!selectionActive) return

        val (startRow, startCol, endRow, endCol) = normalizeSelection()

        for (row in startRow..endRow) {
            if (row < 0 || row >= rows) continue
            val y = row * cellHeight
            val left = if (row == startRow) startCol * cellWidth else 0f
            val right = if (row == endRow) (endCol + 1) * cellWidth else cols * cellWidth
            canvas.drawRect(left, y, right, y + cellHeight, selectionPaint)
        }
    }

    /**
     * Returns (startRow, startCol, endRow, endCol) with start <= end.
     */
    private fun normalizeSelection(): SelectionRange {
        return if (selectionStartRow < selectionEndRow ||
            (selectionStartRow == selectionEndRow && selectionStartCol <= selectionEndCol)
        ) {
            SelectionRange(selectionStartRow, selectionStartCol, selectionEndRow, selectionEndCol)
        } else {
            SelectionRange(selectionEndRow, selectionEndCol, selectionStartRow, selectionStartCol)
        }
    }

    private data class SelectionRange(
        val startRow: Int,
        val startCol: Int,
        val endRow: Int,
        val endCol: Int
    )

    // -- Touch handling -----------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Touch handlers run on the main thread — guard to avoid crashing
        // the host Activity on a gesture-detector or event-conversion bug.
        try {
            scaleGestureDetector.onTouchEvent(event)
            if (!gestureRouter.isScalingNow()) {
                gestureDetector.onTouchEvent(event)
            }

            if (event.action == MotionEvent.ACTION_UP) {
                gestureRouter.touchActionUpResetsScaling()
            }
        } catch (_: Throwable) {}
        return true
    }

    /**
     * Converts a touch coordinate to a terminal (column, row) position,
     * accounting for terminal padding.
     */
    private fun touchToCell(x: Float, y: Float): Pair<Int, Int> {
        val paddingPx = terminalPadding * resources.displayMetrics.density
        val col = ((x - paddingPx) / cellWidth).toInt().coerceAtLeast(0)
        val row = ((y - paddingPx) / cellHeight).toInt().coerceAtLeast(0)
        return Pair(col, row)
    }

    // -- Gesture host (delegates to TerminalGestureRouter) ------------------

    private inner class TerminalViewGestureHost : TerminalGestureHost {
        override fun cellHeightPx(): Float = cellHeight
        override fun isSelectionActive(): Boolean = selectionActive
        override fun currentFontSizeSp(): Float = fontSizeSp
        override fun dispatch(action: TerminalGestureAction) {
            when (action) {
                TerminalGestureAction.OpenIme -> {
                    requestFocus()
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.showSoftInput(this@TerminalView, InputMethodManager.SHOW_IMPLICIT)
                }
                TerminalGestureAction.ClearSelection -> {
                    clearSelection()
                    dismissActionMode()
                }
                is TerminalGestureAction.StartSelection -> {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    val (col, row) = touchToCell(action.x, action.y)
                    selectionActive = true
                    selectionStartRow = row
                    selectionStartCol = col
                    selectionEndRow = row
                    selectionEndCol = col
                    invalidate()
                    startTextSelectionActionMode()
                }
                is TerminalGestureAction.ExtendSelection -> {
                    val (col, row) = touchToCell(action.x, action.y)
                    selectionEndRow = row
                    selectionEndCol = col
                    invalidate()
                }
                TerminalGestureAction.FinishSelection -> {
                    extractSelectedText()
                }
                is TerminalGestureAction.SelectWordAt -> {
                    val (col, row) = touchToCell(action.x, action.y)
                    selectWordAt(row, col)
                    if (selectionActive) startTextSelectionActionMode()
                }
                is TerminalGestureAction.ScrollLocal -> {
                    val maxScroll = emulator?.buffer?.getScrollbackLineCount() ?: 0
                    scrollOffset = (scrollOffset + action.deltaLines).coerceIn(0, maxScroll)
                    invalidate()
                }
                is TerminalGestureAction.FlingLocal -> {
                    val maxScroll = emulator?.buffer?.getScrollbackLineCount() ?: 0
                    scroller.fling(
                        0, (scrollOffset * cellHeight).toInt(),
                        0, (-action.velocityYPx).toInt(),
                        0, 0,
                        0, (maxScroll * cellHeight).toInt()
                    )
                    postInvalidateOnAnimation()
                }
                is TerminalGestureAction.SendArrows -> {
                    val emu = emulator
                    val appCursor = emu?.applicationCursorKeys ?: false
                    val seq = arrowBytes(action.direction, appCursor)
                    val batch = ByteArray(seq.size * action.count)
                    for (i in 0 until action.count) {
                        System.arraycopy(seq, 0, batch, i * seq.size, seq.size)
                    }
                    callback?.onSendBytes(batch, "swipe-arrow-${action.direction}")
                }
                is TerminalGestureAction.FontSizePreview -> {
                    fontSizeSp = action.sp
                }
                is TerminalGestureAction.FontSizeCommit -> {
                    fontSizeSp = action.sp
                    callback?.onFontSizeChanged(action.sp)
                }
            }
        }
    }

    private fun arrowBytes(dir: ArrowDirection, appCursor: Boolean): ByteArray = when (dir) {
        ArrowDirection.UP    -> if (appCursor) "\u001bOA".toByteArray() else "\u001b[A".toByteArray()
        ArrowDirection.DOWN  -> if (appCursor) "\u001bOB".toByteArray() else "\u001b[B".toByteArray()
        ArrowDirection.RIGHT -> if (appCursor) "\u001bOC".toByteArray() else "\u001b[C".toByteArray()
        ArrowDirection.LEFT  -> if (appCursor) "\u001bOD".toByteArray() else "\u001b[D".toByteArray()
    }


    // -- Text selection helpers ---------------------------------------------

    /**
     * Selects the word at the given terminal position.
     */
    private fun selectWordAt(viewRow: Int, col: Int) {
        val emu = emulator ?: return
        val buffer = emu.buffer

        // Determine which line this corresponds to
        val effectiveScroll = scrollOffset.coerceAtMost(buffer.getScrollbackLineCount())
        val line: TerminalLine = if (viewRow < effectiveScroll) {
            val scrollbackIndex = effectiveScroll - 1 - viewRow
            buffer.getScrollbackLine(scrollbackIndex)
        } else {
            val screenRow = viewRow - effectiveScroll
            if (screenRow in 0 until buffer.rows) buffer.getLine(screenRow)
            else return
        }

        val boundedCol = col.coerceIn(0, line.length - 1)
        if (line.charValue(boundedCol) == ' ') return

        // Find word boundaries
        var start = boundedCol
        while (start > 0 && line.charValue(start - 1) != ' ') start--
        var end = boundedCol
        while (end < line.length - 1 && line.charValue(end + 1) != ' ') end++

        selectionActive = true
        selectionStartRow = viewRow
        selectionStartCol = start
        selectionEndRow = viewRow
        selectionEndCol = end
        invalidate()

        extractSelectedText()
    }

    /**
     * Extracts the text from the current selection and notifies the callback.
     */
    private fun extractSelectedText() {
        val emu = emulator ?: return
        val buffer = emu.buffer
        val (startRow, startCol, endRow, endCol) = normalizeSelection()

        val effectiveScroll = scrollOffset.coerceAtMost(buffer.getScrollbackLineCount())
        val sb = StringBuilder()

        for (row in startRow..endRow) {
            val line: TerminalLine = if (row < effectiveScroll) {
                val scrollbackIndex = effectiveScroll - 1 - row
                buffer.getScrollbackLine(scrollbackIndex)
            } else {
                val screenRow = row - effectiveScroll
                if (screenRow in 0 until buffer.rows) buffer.getLine(screenRow)
                else continue
            }

            val colStart = if (row == startRow) startCol else 0
            val colEnd = if (row == endRow) endCol else line.length - 1

            for (c in colStart..colEnd.coerceAtMost(line.length - 1)) {
                if (line.isWidePlaceholder(c)) continue
                sb.append(line.charValue(c))
            }
            if (row < endRow) sb.append('\n')
        }

        val text = sb.toString().trimEnd()
        if (text.isNotEmpty()) {
            callback?.onTextSelected(text)
        }
    }

    /**
     * Clears the current text selection and dismisses the ActionMode toolbar.
     */
    private fun clearSelection() {
        selectionActive = false
        selectionStartRow = -1
        selectionStartCol = -1
        selectionEndRow = -1
        selectionEndCol = -1
        invalidate()
    }

    // -- Scroll animation ---------------------------------------------------

    override fun computeScroll() {
        // Called on every animation frame during a fling. The emulator
        // buffer's getScrollbackLineCount can race with a concurrent
        // resize (iter 44 fixed similar resize race symptoms elsewhere),
        // and postInvalidateOnAnimation can throw IllegalStateException
        // on a detached view. Framework does not catch exceptions from
        // computeScroll — unwrapped throws kill the UI thread.
        try {
            if (scroller.computeScrollOffset()) {
                val maxScroll = emulator?.buffer?.getScrollbackLineCount() ?: 0
                scrollOffset = (scroller.currY / cellHeight).toInt().coerceIn(0, maxScroll)
                postInvalidateOnAnimation()
            }
        } catch (_: Throwable) {
            // Abort the animation quietly — a subsequent touch event
            // will reset scroller state.
            try { scroller.abortAnimation() } catch (_: Throwable) {}
        }
    }

    // -- Keyboard / IME input -----------------------------------------------

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        // Every BaseInputConnection override below runs on the main
        // thread from IME callbacks. The IME framework does NOT catch
        // exceptions from InputConnection methods — any throw escapes
        // to the UI loop and crashes the process.
        //
        // commitText in particular is called on EVERY soft-keyboard
        // keystroke, so one bad state from a detached callback or a
        // broken terminal buffer during typing would manifest as
        // "app crashes randomly when I type" — the textbook "crashes
        // everywhere" complaint for a terminal app. Wrap every method
        // body defensively; the cost is nil on the happy path.
        try {
            // TYPE_NULL tells the IME this is a raw key-event consumer
            // (a terminal), not an editable text field. With TYPE_NULL
            // the IME sends every key as a `sendKeyEvent` call instead
            // of buffering composing text — which is what we need to
            // make IME function keys (Home/End/Tab/arrows) reach the
            // terminal at all (feature request T9).
            //
            // The previous TYPE_CLASS_TEXT setup made IMEs treat the
            // terminal like an EditText, so they did internal cursor
            // management on a dummy `imeEditable` buffer that never
            // reached the remote shell. Standard pattern for Android
            // terminal apps (Termux, JuiceSSH).
            //
            // Some IMEs still use commitText for paste (T11) and for
            // auto-pair brackets (T10) even in TYPE_NULL mode, so the
            // commitText override below remains as a safety net.
            outAttrs.inputType = android.text.InputType.TYPE_NULL
            outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or
                    EditorInfo.IME_FLAG_NO_FULLSCREEN or
                    EditorInfo.IME_ACTION_NONE
        } catch (_: Throwable) {}

        try { clearImeEditable() } catch (_: Throwable) {}

        return object : BaseInputConnection(this@TerminalView, true) {
            // Tracks whether the IME is currently composing text (e.g.,
            // mid-Pinyin / mid-Japanese input). Set in setComposingText,
            // cleared in finishComposingText / commitText. Used by
            // commitText to differentiate "this commit is the END of an
            // IME composition (treat as typing)" from "this commit is a
            // paste or auto-pair (treat as paste)" — the distinction
            // matters because the latter may need bracketed-paste-mode
            // wrapping while the former must NOT be wrapped.
            var wasComposing = false

            // Tracks the last single-char opener committed by the IME
            // (one of `( [ { <`) and the wall-clock time it was set,
            // for SPLIT-COMMIT auto-pair detection: some IMEs commit
            // the opener and closer as two separate commitText calls
            // (e.g., commitText("(", 1) then commitText(")", 0))
            // instead of a single commitText("()", …). Without
            // tracking the previous opener, we'd never detect this
            // pattern via the existing isMatchedPair check (which
            // requires both chars in one commit), and the cursor
            // would land AFTER the closing brace instead of between.
            //
            // The 80ms time window prevents false positives when the
            // user MANUALLY types `(` then `)` slowly: real autopair
            // happens within ~10ms because the IME emits both calls
            // back-to-back, while human typing is at least ~100ms
            // between separate keystrokes.
            //
            // The opener is also cleared by any non-matching commit so
            // unrelated keystrokes can't trigger the pair-cursor
            // heuristic.
            var lastOpener: Char? = null
            var lastOpenerAtMs: Long = 0L

            override fun getEditable(): Editable = imeEditable

            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                try {
                    val str = text?.toString() ?: return true
                    if (str.isEmpty()) {
                        clearImeEditable()
                        return true
                    }

                    // Snapshot then clear the composing flag — this commit
                    // ends any in-progress composition.
                    val composingCommit = wasComposing
                    wasComposing = false

                    // Feature T11: IME paste. When the user taps Paste
                    // in the IME toolbar, many IMEs invoke commitText
                    // with a multi-character clipboard payload (TYPE_NULL
                    // doesn't disable this path). Forward the full
                    // string as paste-text — the screen-level callback
                    // can wrap with bracketed-paste markers when the
                    // running program enabled them.
                    //
                    // For 1-char input we still apply modifier merging
                    // so that physical Ctrl/Alt held WHILE typing on
                    // the soft keyboard reaches the terminal correctly.
                    val bytes = str.toByteArray(Charsets.UTF_8)
                    // Detect split-commit auto-pair BEFORE updating
                    // lastOpener — if the previous commit was an opener
                    // and this one is the matching closer within the
                    // ~80ms autopair window, the IME is doing two-call
                    // auto-pair and we need to emit a Left arrow after
                    // sending the closer so the cursor lands between
                    // the pair (feature T10). The time window prevents
                    // false positives from a user manually typing `(`
                    // then `)` (separate keystrokes are slower).
                    val now = android.os.SystemClock.uptimeMillis()
                    val splitPairCloser = str.length == 1
                            && lastOpener != null
                            && isClosingPairFor(lastOpener!!, str[0])
                            && (now - lastOpenerAtMs) <= 80L
                    if (str.length == 1) {
                        val modified = applyModifiers(bytes)
                        if (modified !== bytes) {
                            callback?.onKeyEvent(modified)
                        } else {
                            callback?.onTextInput(str)
                        }
                    } else if (composingCommit || isMatchedPair(str)) {
                        // End-of-composition or auto-pair — treat as
                        // typing, never wrap with paste markers.
                        callback?.onTextInput(str)
                    } else {
                        // Bare multi-char commit with no composition
                        // history — almost certainly an IME paste action.
                        callback?.onPasteText(str)
                    }

                    // Feature T10: bracket-pair / quote-pair cursor
                    // placement. When an IME auto-pairs characters
                    // (Gboard, most programming keyboards) it commits
                    // the full pair as a single commitText. We detect
                    // a 2-char matched pair and emit an ESC[D (Left
                    // arrow) so the remote shell/editor cursor lands
                    // between the pair, matching the user's on-screen
                    // expectation.
                    //
                    // The previous version only triggered when
                    // newCursorPosition != 1, but most IMEs use the
                    // default cursorPosition=1 (after the pair) and
                    // expect the EditText to do its own cursor
                    // adjustment. Since we're a terminal, that
                    // adjustment never happens unless we emit it
                    // ourselves. So this is now unconditional for any
                    // 2-char matched pair coming through commitText.
                    if (isMatchedPair(str) || splitPairCloser) {
                        val leftArrow = if (emulator?.applicationCursorKeys == true) {
                            "\u001bOD".toByteArray()
                        } else {
                            "\u001b[D".toByteArray()
                        }
                        callback?.onKeyEvent(leftArrow)
                    }

                    // Update lastOpener for the NEXT commit. If this
                    // commit was a single-char opener, remember it
                    // along with the timestamp so the next single-char
                    // closer can be detected as a split-commit auto-pair
                    // within the time window. Otherwise (any non-opener
                    // commit, including the closer we just handled),
                    // clear lastOpener so unrelated keystrokes can't
                    // trigger a stale pair-cursor heuristic.
                    if (str.length == 1 && isOpenerChar(str[0])) {
                        lastOpener = str[0]
                        lastOpenerAtMs = now
                    } else {
                        lastOpener = null
                    }

                    clearImeEditable()
                } catch (_: Throwable) {}
                return true
            }

            override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
                try {
                    // Mirror the actual composing state into wasComposing.
                    // Empty/null `text` means the IME is CLEARING the
                    // composition without committing — must reset the
                    // flag so a subsequent commitText doesn't see a
                    // stale "composing" state and treat a fresh paste
                    // as the end of an in-progress composition.
                    wasComposing = !text.isNullOrEmpty()
                    replaceEditable(text)
                } catch (_: Throwable) {}
                return true
            }

            override fun finishComposingText(): Boolean {
                try {
                    wasComposing = false
                    clearImeEditable()
                } catch (_: Throwable) {}
                return true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                try {
                    // IME can send very large beforeLength on some devices
                    // (entire-editable reset). Clamp to a safe bound so we
                    // don't DOS the shell with thousands of DEL bytes.
                    val n = beforeLength.coerceIn(0, 1024)
                    for (i in 0 until n) {
                        callback?.onKeyEvent(byteArrayOf(0x7F)) // DEL
                    }
                    // Any deletion invalidates the split-commit auto-pair
                    // state — if the user backspaced over an opener, the
                    // next closer must NOT be paired with it.
                    lastOpener = null
                    clearImeEditable()
                } catch (_: Throwable) {}
                return true
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                // Feature T9: IME-supplied function keys (Home/End/Tab/
                // arrows/Delete/etc.) reach us via sendKeyEvent from
                // soft keyboards that have a programming row. We must
                // route them through the same handleKeyDown path
                // hardware keys use. Previously only ACTION_DOWN was
                // handled, which is still correct; the fix below is
                // to ALSO ensure we return the event consumed flag
                // correctly so the IME doesn't retry via fallback
                // commitText which would corrupt the input stream.
                try {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        // Any non-modifier-only key event invalidates the
                        // split-commit auto-pair state. Anything else the
                        // user types between an opener and a potential
                        // closer means they're not the same pair, so the
                        // pair-cursor heuristic must NOT fire across it.
                        // Modifier-only DOWN events (Shift, Ctrl, Alt
                        // pressed without a real key yet) DO leave the
                        // state alone — pressing Shift before typing `)`
                        // shouldn't break a pending `(` autopair.
                        if (!isModifierOnlyKey(event.keyCode)) {
                            lastOpener = null
                        }
                        val handled = handleKeyDown(event)
                        if (handled) return true
                    } else if (event.action == KeyEvent.ACTION_UP) {
                        // Swallow ACTION_UP for keys we handled on DOWN
                        // so the IME doesn't generate a follow-up
                        // commitText to compensate.
                        return true
                    }
                } catch (_: Throwable) {}
                // Fall back to BaseInputConnection for events we don't
                // handle (e.g., system keys, meta-only events). Wrap
                // because the super call can also throw on an exotic
                // View tree state.
                return try { super.sendKeyEvent(event) } catch (_: Throwable) { true }
            }

            override fun performEditorAction(actionCode: Int): Boolean {
                // Map IME actions to terminal Enter key
                try { callback?.onKeyEvent(byteArrayOf('\r'.code.toByte())) } catch (_: Throwable) {}
                return true
            }

            override fun performContextMenuAction(id: Int): Boolean {
                // Feature T11: IME paste. Some IMEs route paste
                // through performContextMenuAction(android.R.id.paste)
                // instead of commitText. Read the primary clip and
                // send it as text input, matching the behaviour of
                // commitText for a multi-char clipboard payload.
                try {
                    when (id) {
                        android.R.id.paste -> {
                            val clipboard = context.getSystemService(
                                android.content.Context.CLIPBOARD_SERVICE
                            ) as? android.content.ClipboardManager
                            val clipText = clipboard
                                ?.primaryClip
                                ?.getItemAt(0)
                                ?.text
                                ?.toString()
                            if (!clipText.isNullOrEmpty()) {
                                callback?.onPasteText(clipText)
                            }
                            return true
                        }
                        android.R.id.copy, android.R.id.cut -> {
                            // The terminal has its own copy gesture
                            // (long-press selection). Forward the
                            // action to the base implementation for
                            // IMEs that still want to try.
                            return try {
                                super.performContextMenuAction(id)
                            } catch (_: Throwable) { false }
                        }
                        android.R.id.selectAll -> return false
                    }
                } catch (_: Throwable) {}
                return try { super.performContextMenuAction(id) } catch (_: Throwable) { false }
            }

            override fun closeConnection() {
                try { clearImeEditable() } catch (_: Throwable) {}
                try { super.closeConnection() } catch (_: Throwable) {}
            }
        }
    }

    /**
     * True if [s] is a 2-character matched bracket/quote pair like
     * "()", "[]", "{}", "''", "\"\"", "``", "<>". Used by
     * [android.view.inputmethod.InputConnection.commitText] to detect
     * IME auto-pairing and emit a Left arrow so the remote shell
     * cursor lands between the pair.
     */
    private fun isMatchedPair(s: String): Boolean {
        if (s.length != 2) return false
        return when (s) {
            "()", "[]", "{}", "<>",
            "''", "\"\"", "``" -> true
            else -> false
        }
    }

    /**
     * True if [c] is the OPENING half of a bracket / quote pair we
     * recognize for IME auto-pair cursor placement (feature T10).
     * Used by the split-commit auto-pair detection in the
     * InputConnection's commitText override.
     */
    private fun isOpenerChar(c: Char): Boolean = when (c) {
        '(', '[', '{', '<', '\'', '"', '`' -> true
        else -> false
    }

    /**
     * True if [closer] is the matching closing half of [opener] for
     * the bracket/quote pairs we recognize. Symmetric quotes (single,
     * double, backtick) are their own closer.
     */
    private fun isClosingPairFor(opener: Char, closer: Char): Boolean = when (opener) {
        '(' -> closer == ')'
        '[' -> closer == ']'
        '{' -> closer == '}'
        '<' -> closer == '>'
        '\'' -> closer == '\''
        '"' -> closer == '"'
        '`' -> closer == '`'
        else -> false
    }

    /**
     * True if [keyCode] is a modifier key (Shift / Ctrl / Alt / Meta /
     * Caps Lock / Num Lock / Scroll Lock / Function). Used by the
     * sendKeyEvent split-commit autopair invalidation so that pressing
     * Shift before typing `)` does NOT break a pending `(` autopair —
     * the user is still in the middle of typing the closer.
     */
    private fun isModifierOnlyKey(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT,
        KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT,
        KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT,
        KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT,
        KeyEvent.KEYCODE_CAPS_LOCK,
        KeyEvent.KEYCODE_NUM_LOCK,
        KeyEvent.KEYCODE_SCROLL_LOCK,
        KeyEvent.KEYCODE_FUNCTION,
        KeyEvent.KEYCODE_SYM -> true
        else -> false
    }

    override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
        return super.onKeyPreIme(keyCode, event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Volume keys are configurable via [volumeKeysAction]. Default
        // ("volume") passes them to the system so Android adjusts media
        // volume — preserves the phone's native UX. Power users can
        // remap to Esc/Tab in Settings → Input & Interaction.
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
            keyCode == KeyEvent.KEYCODE_VOLUME_MUTE
        ) {
            return when (volumeKeysAction) {
                "esc_tab" -> handleVolumeAsEscTab(keyCode, event)
                // "disabled" consumes the event so neither the terminal
                // nor the system reacts. Returning true is the Android
                // contract for "I handled this, don't fall through."
                "disabled" -> true
                // "volume" (default) and any unknown value: forward to
                // the system, which routes to AudioManager.
                else -> super.onKeyDown(keyCode, event)
            }
        }
        return try {
            handleKeyDown(event) || super.onKeyDown(keyCode, event)
        } catch (_: Throwable) {
            // Key events run on the main thread — never crash on a handler
            // bug. Consume the event to stop Android from retrying.
            true
        }
    }

    /**
     * Map Vol-Down → ESC, Vol-Up → TAB. Vol-Mute has no sensible terminal
     * mapping and falls through to the system as a normal mute press.
     */
    private fun handleVolumeAsEscTab(keyCode: Int, event: KeyEvent): Boolean {
        val byte: Byte = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> 0x1B
            KeyEvent.KEYCODE_VOLUME_UP -> '\t'.code.toByte()
            else -> return super.onKeyDown(keyCode, event)
        }
        callback?.onKeyEvent(byteArrayOf(byte))
        return true
    }

    /**
     * Translates an Android [KeyEvent] into terminal bytes, applies merged
     * modifiers (hardware OR ExtraKeysBar), and sends through the callback.
     */
    private fun handleKeyDown(event: KeyEvent): Boolean {
        val rawBytes = keyEventToBytes(event) ?: return false
        if (scrollOnKeystroke && scrollOffset > 0) {
            scrollOffset = 0
        }
        val mods = extraKeyModifiers
        val ctrl = event.isCtrlPressed || mods.ctrl
        val alt = event.isAltPressed || mods.alt
        val shift = event.isShiftPressed || mods.shift
        val meta = event.isMetaPressed || mods.meta
        val modified = ModifierKeys.apply(rawBytes, ctrl, alt, shift, meta)
        callback?.onKeyEvent(modified)
        if (modified !== rawBytes) onModifiersConsumed?.invoke()
        return true
    }

    /**
     * Converts a [KeyEvent] to the raw (unmodified) byte sequence for the terminal.
     * Modifier application is handled by [handleKeyDown] via [ModifierKeys.apply].
     */
    private fun keyEventToBytes(event: KeyEvent): ByteArray? {
        val emu = emulator
        val appCursorMode = emu?.applicationCursorKeys ?: false

        return when (event.keyCode) {
            KeyEvent.KEYCODE_ENTER -> byteArrayOf('\r'.code.toByte())
            KeyEvent.KEYCODE_DEL -> byteArrayOf(0x7F)
            KeyEvent.KEYCODE_FORWARD_DEL -> "\u001b[3~".toByteArray()
            KeyEvent.KEYCODE_TAB -> byteArrayOf('\t'.code.toByte())
            KeyEvent.KEYCODE_ESCAPE -> byteArrayOf(0x1B)
            // Volume keys intentionally NOT captured — see onKeyDown.

            KeyEvent.KEYCODE_DPAD_UP ->
                if (appCursorMode) "\u001bOA".toByteArray() else "\u001b[A".toByteArray()
            KeyEvent.KEYCODE_DPAD_DOWN ->
                if (appCursorMode) "\u001bOB".toByteArray() else "\u001b[B".toByteArray()
            KeyEvent.KEYCODE_DPAD_RIGHT ->
                if (appCursorMode) "\u001bOC".toByteArray() else "\u001b[C".toByteArray()
            KeyEvent.KEYCODE_DPAD_LEFT ->
                if (appCursorMode) "\u001bOD".toByteArray() else "\u001b[D".toByteArray()

            KeyEvent.KEYCODE_MOVE_HOME -> "\u001b[H".toByteArray()
            KeyEvent.KEYCODE_MOVE_END -> "\u001b[F".toByteArray()
            KeyEvent.KEYCODE_PAGE_UP -> "\u001b[5~".toByteArray()
            KeyEvent.KEYCODE_PAGE_DOWN -> "\u001b[6~".toByteArray()
            KeyEvent.KEYCODE_INSERT -> "\u001b[2~".toByteArray()

            KeyEvent.KEYCODE_F1 -> "\u001bOP".toByteArray()
            KeyEvent.KEYCODE_F2 -> "\u001bOQ".toByteArray()
            KeyEvent.KEYCODE_F3 -> "\u001bOR".toByteArray()
            KeyEvent.KEYCODE_F4 -> "\u001bOS".toByteArray()
            KeyEvent.KEYCODE_F5 -> "\u001b[15~".toByteArray()
            KeyEvent.KEYCODE_F6 -> "\u001b[17~".toByteArray()
            KeyEvent.KEYCODE_F7 -> "\u001b[18~".toByteArray()
            KeyEvent.KEYCODE_F8 -> "\u001b[19~".toByteArray()
            KeyEvent.KEYCODE_F9 -> "\u001b[20~".toByteArray()
            KeyEvent.KEYCODE_F10 -> "\u001b[21~".toByteArray()
            KeyEvent.KEYCODE_F11 -> "\u001b[23~".toByteArray()
            KeyEvent.KEYCODE_F12 -> "\u001b[24~".toByteArray()

            else -> {
                // Regular character: get with hardware Shift/CapsLock applied
                // (layout-dependent) but strip Ctrl/Alt/Meta — ModifierKeys handles those.
                val stripMask = KeyEvent.META_CTRL_MASK or KeyEvent.META_ALT_MASK or KeyEvent.META_META_MASK
                val baseChar = event.getUnicodeChar(event.metaState and stripMask.inv())
                if (baseChar != 0) {
                    String(Character.toChars(baseChar)).toByteArray(Charsets.UTF_8)
                } else {
                    null
                }
            }
        }
    }

    // -- Lifecycle ----------------------------------------------------------

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        try {
            attachContentListener(emulator)
            resizeBus?.setVisible(true)
            requestSizeRecompute()
            if (cursorBlinkEnabled) {
                cursorBlinkHandler.postDelayed(cursorBlinkRunnable, cursorBlinkSpeed)
            }
        } catch (_: Throwable) {}
    }

    override fun onDetachedFromWindow() {
        try { releaseInputFocus() } catch (_: Throwable) {}
        try { detachContentListener(emulator) } catch (_: Throwable) {}
        try { resizeBus?.setVisible(false) } catch (_: Throwable) {}
        try { cursorBlinkHandler.removeCallbacks(cursorBlinkRunnable) } catch (_: Throwable) {}
        try { scroller.abortAnimation() } catch (_: Throwable) {}
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        AppLogger.d(Category.TERMINAL) { "tv.size ${oldw}x${oldh} → ${w}x${h}" }
        super.onSizeChanged(w, h, oldw, oldh)
        try { applySizeRecompute() } catch (t: Throwable) {
            AppLogger.w(Category.TERMINAL) { "tv.onSizeChanged resize ✗ ${t.javaClass.simpleName}" }
        }
    }

    private fun replaceEditable(text: CharSequence?) {
        val safeText = text ?: ""
        imeEditable.replace(0, imeEditable.length, safeText)
        Selection.setSelection(imeEditable, imeEditable.length)
    }

    private fun clearImeEditable() {
        replaceEditable("")
    }
}

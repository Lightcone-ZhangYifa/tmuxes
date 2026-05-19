package com.tmuxes.terminal.view

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import kotlin.math.abs

/**
 * Single source of truth for terminal gesture semantics.
 *
 * Public API (Android event path):
 *   - extends [GestureDetector.SimpleOnGestureListener] for single-touch
 *   - exposes [scaleListener] for pinch zoom
 *
 * Internal API (pure-JVM, unit-testable):
 *   - [handleScroll] / [handleFling] / [handlePinchBegin] / [handlePinchScale]
 *     / [handlePinchEnd] take primitive values so tests don't need to mock
 *     [MotionEvent] / [ScaleGestureDetector].
 *
 * Mode-aware routing:
 * - Vertical drag in [SwipeMode.SCROLL_LOCAL] → [TerminalGestureAction.ScrollLocal]
 * - Vertical drag in [SwipeMode.ARROW_KEYS] → [TerminalGestureAction.SendArrows]
 * - [SwipeMode.AUTO] uses [copyModeActive] to pick between the two so the
 *   gesture follows the Edit-mode FAB without per-screen wiring.
 *
 * Pinch zoom uses live preview + single commit at gesture end:
 * - onScale → [TerminalGestureAction.FontSizePreview] (cheap; no Settings write)
 * - onScaleEnd → [TerminalGestureAction.FontSizeCommit] (one Settings write per pinch)
 *
 * The router holds no `TerminalView`, `Settings`, or `Context` references —
 * everything it needs comes through the [TerminalGestureHost] interface.
 */
class TerminalGestureRouter(
    private val host: TerminalGestureHost
) : GestureDetector.SimpleOnGestureListener() {

    enum class SwipeMode { AUTO, SCROLL_LOCAL, ARROW_KEYS }

    var swipeMode: SwipeMode = SwipeMode.AUTO
    var linesPerArrow: Int = 1
    var copyModeActive: Boolean = false
    var pinchZoomEnabled: Boolean = true
    var doubleTapWordSelect: Boolean = true

    private var pendingFontSizeSp: Float? = null
    private var scaling: Boolean = false

    fun isScalingNow(): Boolean = scaling

    fun touchActionUpResetsScaling() {
        scaling = false
    }

    // -----------------------------------------------------------------
    // Android single-touch entry points → internal pure-logic handlers
    // -----------------------------------------------------------------

    override fun onDown(e: MotionEvent): Boolean = true

    override fun onSingleTapUp(e: MotionEvent): Boolean = handleSingleTap()

    override fun onDoubleTap(e: MotionEvent): Boolean = handleDoubleTap(e.x, e.y)

    override fun onLongPress(e: MotionEvent) {
        handleLongPress(e.x, e.y)
    }

    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean = handleScroll(distanceY = distanceY, eventX = e2.x, eventY = e2.y)

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean = handleFling(velocityY = velocityY)

    // -----------------------------------------------------------------
    // Internal handlers — testable without Android classes
    // -----------------------------------------------------------------

    internal fun handleSingleTap(): Boolean {
        if (host.isSelectionActive()) {
            host.dispatch(TerminalGestureAction.ClearSelection)
        } else {
            host.dispatch(TerminalGestureAction.OpenIme)
        }
        return true
    }

    internal fun handleDoubleTap(x: Float, y: Float): Boolean {
        if (!doubleTapWordSelect) return false
        host.dispatch(TerminalGestureAction.SelectWordAt(x, y))
        return true
    }

    internal fun handleLongPress(x: Float, y: Float) {
        host.dispatch(TerminalGestureAction.StartSelection(x, y))
    }

    internal fun handleScroll(
        distanceY: Float,
        eventX: Float,
        eventY: Float
    ): Boolean {
        if (host.isSelectionActive()) {
            host.dispatch(TerminalGestureAction.ExtendSelection(eventX, eventY))
            return true
        }

        val cellHeight = host.cellHeightPx().takeIf { it > 0f } ?: return true
        val lines = (distanceY / cellHeight).toInt()
        if (lines == 0) return true

        when (effectiveSwipeMode()) {
            SwipeMode.SCROLL_LOCAL -> {
                host.dispatch(TerminalGestureAction.ScrollLocal(deltaLines = lines))
            }
            SwipeMode.ARROW_KEYS -> {
                // Mobile-native content-pull semantics: finger drag DOWN
                // (distanceY < 0) reveals OLDER content → send UP arrows.
                // Finger drag UP (distanceY > 0) reveals NEWER content →
                // send DOWN arrows. tmux copy-mode UP-arrow walks cursor
                // toward older history; DOWN-arrow walks toward live.
                val direction = if (lines > 0) ArrowDirection.DOWN else ArrowDirection.UP
                val n = (abs(lines) * linesPerArrow.coerceAtLeast(1)).coerceAtLeast(1)
                host.dispatch(TerminalGestureAction.SendArrows(direction, n))
            }
            SwipeMode.AUTO -> Unit
        }
        return true
    }

    internal fun handleFling(velocityY: Float): Boolean {
        if (host.isSelectionActive()) {
            host.dispatch(TerminalGestureAction.FinishSelection)
            return true
        }
        when (effectiveSwipeMode()) {
            SwipeMode.SCROLL_LOCAL -> {
                host.dispatch(TerminalGestureAction.FlingLocal(velocityYPx = velocityY))
            }
            SwipeMode.ARROW_KEYS -> {
                val cellHeight = host.cellHeightPx().takeIf { it > 0f } ?: return true
                val n = (abs(velocityY) / cellHeight / FLING_VELOCITY_DIVISOR)
                    .toInt().coerceIn(1, FLING_ARROW_CAP)
                // Same content-pull semantics as handleScroll.
                val direction = if (velocityY > 0) ArrowDirection.DOWN else ArrowDirection.UP
                host.dispatch(TerminalGestureAction.SendArrows(direction, n))
            }
            SwipeMode.AUTO -> Unit
        }
        return true
    }

    internal fun handlePinchBegin(): Boolean {
        if (!pinchZoomEnabled) return false
        scaling = true
        pendingFontSizeSp = host.currentFontSizeSp()
        return true
    }

    internal fun handlePinchScale(scaleFactor: Float): Boolean {
        val current = pendingFontSizeSp ?: return false
        val newSize = (current * scaleFactor)
            .coerceIn(MIN_FONT_SP, MAX_FONT_SP)
        if (abs(newSize - current) > FONT_DELTA_EPSILON) {
            pendingFontSizeSp = newSize
            host.dispatch(TerminalGestureAction.FontSizePreview(newSize))
        }
        return true
    }

    internal fun handlePinchEnd() {
        val finalSize = pendingFontSizeSp
        pendingFontSizeSp = null
        scaling = false
        if (finalSize != null) {
            host.dispatch(TerminalGestureAction.FontSizeCommit(finalSize))
        }
    }

    // -----------------------------------------------------------------
    // Pinch — Android wrapper around internal handlers
    // -----------------------------------------------------------------

    val scaleListener: ScaleGestureDetector.OnScaleGestureListener =
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean =
                handlePinchBegin()

            override fun onScale(detector: ScaleGestureDetector): Boolean =
                handlePinchScale(detector.scaleFactor)

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                handlePinchEnd()
            }
        }

    private fun effectiveSwipeMode(): SwipeMode = when (swipeMode) {
        SwipeMode.AUTO -> if (copyModeActive) SwipeMode.ARROW_KEYS else SwipeMode.SCROLL_LOCAL
        else -> swipeMode
    }

    companion object {
        const val MIN_FONT_SP = 6f
        const val MAX_FONT_SP = 36f
        const val FONT_DELTA_EPSILON = 0.1f
        const val FLING_VELOCITY_DIVISOR = 8f
        const val FLING_ARROW_CAP = 20
    }
}

enum class ArrowDirection { UP, DOWN, LEFT, RIGHT }

sealed interface TerminalGestureAction {
    object OpenIme : TerminalGestureAction
    object ClearSelection : TerminalGestureAction
    data class StartSelection(val x: Float, val y: Float) : TerminalGestureAction
    data class ExtendSelection(val x: Float, val y: Float) : TerminalGestureAction
    object FinishSelection : TerminalGestureAction
    data class SelectWordAt(val x: Float, val y: Float) : TerminalGestureAction
    data class ScrollLocal(val deltaLines: Int) : TerminalGestureAction
    data class FlingLocal(val velocityYPx: Float) : TerminalGestureAction
    data class SendArrows(val direction: ArrowDirection, val count: Int) : TerminalGestureAction
    data class FontSizePreview(val sp: Float) : TerminalGestureAction
    data class FontSizeCommit(val sp: Float) : TerminalGestureAction
}

interface TerminalGestureHost {
    fun cellHeightPx(): Float
    fun isSelectionActive(): Boolean
    fun currentFontSizeSp(): Float
    fun dispatch(action: TerminalGestureAction)
}

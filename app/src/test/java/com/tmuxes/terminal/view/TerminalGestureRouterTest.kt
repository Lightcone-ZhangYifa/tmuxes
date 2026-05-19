package com.tmuxes.terminal.view

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for [TerminalGestureRouter].
 *
 * The router exposes internal `handle*` functions taking primitive
 * values so tests don't need to mock Android `MotionEvent` /
 * `ScaleGestureDetector`. The Android entry points (`onScroll`,
 * `scaleListener.onScale`, etc.) are thin wrappers around these.
 */
class TerminalGestureRouterTest {

    private val recorded = mutableListOf<TerminalGestureAction>()

    private inner class FakeHost(
        var fontSize: Float = 14f,
        var cellHeight: Float = 32f,
        var selectionActive: Boolean = false
    ) : TerminalGestureHost {
        override fun cellHeightPx() = cellHeight
        override fun isSelectionActive() = selectionActive
        override fun currentFontSizeSp() = fontSize
        override fun dispatch(action: TerminalGestureAction) { recorded.add(action) }
    }

    // -----------------------------------------------------------------
    // Mode routing — vertical swipe
    // -----------------------------------------------------------------

    @Test fun `auto + copy-mode → SendArrows for swipe`() {
        val router = TerminalGestureRouter(FakeHost()).apply {
            swipeMode = TerminalGestureRouter.SwipeMode.AUTO
            copyModeActive = true
        }
        // distanceY > 0 = finger dragged UP = newer content desired = DOWN arrow
        router.handleScroll(distanceY = 96f, eventX = 0f, eventY = 0f) // 3 cells, finger up
        val a = recorded.single() as TerminalGestureAction.SendArrows
        assertEquals(ArrowDirection.DOWN, a.direction)
        assertEquals(3, a.count)
    }

    @Test fun `auto + normal mode → ScrollLocal for swipe`() {
        val router = TerminalGestureRouter(FakeHost()).apply {
            swipeMode = TerminalGestureRouter.SwipeMode.AUTO
            copyModeActive = false
        }
        router.handleScroll(distanceY = 96f, eventX = 0f, eventY = 0f)
        val a = recorded.single() as TerminalGestureAction.ScrollLocal
        assertEquals(3, a.deltaLines)
    }

    @Test fun `forced scroll → ScrollLocal even in copy-mode`() {
        val router = TerminalGestureRouter(FakeHost()).apply {
            swipeMode = TerminalGestureRouter.SwipeMode.SCROLL_LOCAL
            copyModeActive = true
        }
        router.handleScroll(distanceY = 64f, eventX = 0f, eventY = 0f)
        assertTrue(recorded.single() is TerminalGestureAction.ScrollLocal)
    }

    @Test fun `forced arrow_keys → SendArrows even outside copy-mode`() {
        val router = TerminalGestureRouter(FakeHost()).apply {
            swipeMode = TerminalGestureRouter.SwipeMode.ARROW_KEYS
            copyModeActive = false
        }
        // distanceY < 0 = finger dragged DOWN = older content desired = UP arrow
        router.handleScroll(distanceY = -64f, eventX = 0f, eventY = 0f) // 2 cells, finger down
        val a = recorded.single() as TerminalGestureAction.SendArrows
        assertEquals(ArrowDirection.UP, a.direction)
        assertEquals(2, a.count)
    }

    @Test fun `linesPerArrow multiplier`() {
        val router = TerminalGestureRouter(FakeHost()).apply {
            swipeMode = TerminalGestureRouter.SwipeMode.ARROW_KEYS
            linesPerArrow = 3
        }
        router.handleScroll(distanceY = 96f, eventX = 0f, eventY = 0f) // 3 cells, finger up
        val a = recorded.single() as TerminalGestureAction.SendArrows
        assertEquals(ArrowDirection.DOWN, a.direction) // finger UP = newer content = DOWN arrow
        assertEquals(9, a.count) // 3 cells × 3 multiplier = 9 arrows
    }

    @Test fun `tiny swipe under one cell is ignored`() {
        val router = TerminalGestureRouter(FakeHost()).apply {
            swipeMode = TerminalGestureRouter.SwipeMode.ARROW_KEYS
        }
        router.handleScroll(distanceY = 10f, eventX = 0f, eventY = 0f) // < 1 cell
        assertTrue(recorded.isEmpty())
    }

    // -----------------------------------------------------------------
    // Fling
    // -----------------------------------------------------------------

    @Test fun `fling with arrow_keys caps at 20`() {
        val router = TerminalGestureRouter(FakeHost()).apply {
            swipeMode = TerminalGestureRouter.SwipeMode.ARROW_KEYS
        }
        // velocityY > 0 = finger flicked UP = newer content desired = DOWN arrow
        router.handleFling(velocityY = 100_000f)
        val a = recorded.single() as TerminalGestureAction.SendArrows
        assertEquals(TerminalGestureRouter.FLING_ARROW_CAP, a.count)
        assertEquals(ArrowDirection.DOWN, a.direction)
    }

    @Test fun `fling with scroll_local emits FlingLocal`() {
        val router = TerminalGestureRouter(FakeHost()).apply {
            swipeMode = TerminalGestureRouter.SwipeMode.SCROLL_LOCAL
        }
        router.handleFling(velocityY = 5_000f)
        val a = recorded.single() as TerminalGestureAction.FlingLocal
        assertEquals(5_000f, a.velocityYPx, 0.001f)
    }

    // -----------------------------------------------------------------
    // Pinch
    // -----------------------------------------------------------------

    @Test fun `pinch live preview without commit`() {
        val router = TerminalGestureRouter(FakeHost(fontSize = 14f))
        router.handlePinchBegin()
        router.handlePinchScale(1.1f)
        router.handlePinchScale(1.1f)
        val previews = recorded.filterIsInstance<TerminalGestureAction.FontSizePreview>()
        val commits = recorded.filterIsInstance<TerminalGestureAction.FontSizeCommit>()
        assertEquals(2, previews.size)
        assertEquals(0, commits.size)
        assertTrue(router.isScalingNow())
    }

    @Test fun `pinch commit on scale end`() {
        val router = TerminalGestureRouter(FakeHost(fontSize = 14f))
        router.handlePinchBegin()
        router.handlePinchScale(1.5f) // 14 → 21
        router.handlePinchEnd()
        val commits = recorded.filterIsInstance<TerminalGestureAction.FontSizeCommit>()
        assertEquals(1, commits.size)
        assertEquals(21f, commits.first().sp, 0.5f)
        assertFalse(router.isScalingNow())
    }

    @Test fun `pinch disabled → onPinchBegin returns false`() {
        val router = TerminalGestureRouter(FakeHost()).apply {
            pinchZoomEnabled = false
        }
        val accepted = router.handlePinchBegin()
        assertFalse(accepted)
        assertFalse(router.isScalingNow())
    }

    @Test fun `pinch clamps to font size bounds`() {
        val router = TerminalGestureRouter(FakeHost(fontSize = 14f))
        router.handlePinchBegin()
        router.handlePinchScale(10f) // way past max
        router.handlePinchEnd()
        val commit = recorded.filterIsInstance<TerminalGestureAction.FontSizeCommit>().first()
        assertEquals(TerminalGestureRouter.MAX_FONT_SP, commit.sp, 0.001f)
    }

    // -----------------------------------------------------------------
    // Tap / selection
    // -----------------------------------------------------------------

    @Test fun `single tap → OpenIme`() {
        val router = TerminalGestureRouter(FakeHost(selectionActive = false))
        router.handleSingleTap()
        assertTrue(recorded.single() is TerminalGestureAction.OpenIme)
    }

    @Test fun `single tap with selection → ClearSelection`() {
        val router = TerminalGestureRouter(FakeHost(selectionActive = true))
        router.handleSingleTap()
        assertTrue(recorded.single() is TerminalGestureAction.ClearSelection)
    }

    @Test fun `double tap word select disabled`() {
        val router = TerminalGestureRouter(FakeHost()).apply {
            doubleTapWordSelect = false
        }
        val handled = router.handleDoubleTap(0f, 0f)
        assertFalse(handled)
        assertTrue(recorded.isEmpty())
    }

    @Test fun `selection active swipe extends selection not arrows`() {
        val router = TerminalGestureRouter(FakeHost(selectionActive = true)).apply {
            swipeMode = TerminalGestureRouter.SwipeMode.ARROW_KEYS
        }
        router.handleScroll(distanceY = 96f, eventX = 100f, eventY = 200f)
        val a = recorded.single() as TerminalGestureAction.ExtendSelection
        assertEquals(100f, a.x, 0.001f)
        assertEquals(200f, a.y, 0.001f)
    }
}

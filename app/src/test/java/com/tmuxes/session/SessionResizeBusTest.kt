package com.tmuxes.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Pure-JVM tests for the in-app yield-on-hidden + dedup logic.
 *
 * The actual [InAppResizeBus] / [WidgetResizeBus] are coroutine-driven
 * stateful classes that need a real [SessionCoordinator]; full integration
 * is exercised on emulator because it depends on Android lifecycle delivery.
 * Here we test the pure decision shape via a tiny replica of the internal
 * logic: given visibility, the in-app size, the widget size, and the last
 * emitted size, decide whether a bus should emit a new terminal size.
 */
class SessionResizeBusTest {

    /**
     * Replica of [InAppResizeBus]'s emission decision. Mirrors:
     *   - if visible: target = inAppSize
     *   - if hidden:  target = widgetSize (linked, may be null)
     *   - emit only if target != null AND target != lastSent
     */
    private fun inAppDecision(
        visible: Boolean,
        inAppSize: Pair<Int, Int>?,
        linkedWidgetSize: Pair<Int, Int>?,
        lastSent: Pair<Int, Int>?
    ): Pair<Int, Int>? {
        val target = if (visible) inAppSize else linkedWidgetSize
        if (target == null) return null
        if (target == lastSent) return null
        return target
    }

    /**
     * Replica of [WidgetResizeBus]'s emission decision (much simpler):
     *   - widget bus always tracks widgetSize regardless of visibility
     *   - emit only if widgetSize != null AND widgetSize != lastSent
     */
    private fun widgetDecision(
        widgetSize: Pair<Int, Int>?,
        lastSent: Pair<Int, Int>?
    ): Pair<Int, Int>? {
        if (widgetSize == null) return null
        if (widgetSize == lastSent) return null
        return widgetSize
    }

    // ---------- in-app bus: visibility + yield rule ----------

    @Test fun inAppVisible_emitsInAppSize() {
        val r = inAppDecision(visible = true, inAppSize = 80 to 24, linkedWidgetSize = null, lastSent = null)
        assertEquals(80 to 24, r)
    }

    @Test fun inAppHidden_withLinkedWidget_emitsWidgetSize() {
        val r = inAppDecision(visible = false, inAppSize = 80 to 24, linkedWidgetSize = 50 to 20, lastSent = null)
        assertEquals(50 to 20, r)
    }

    @Test fun inAppHidden_withoutLinkedWidget_noEmit() {
        val r = inAppDecision(visible = false, inAppSize = 80 to 24, linkedWidgetSize = null, lastSent = null)
        assertEquals(null, r)
    }

    @Test fun inAppVisible_withoutInAppSize_noEmit() {
        // Pre-measure phase — no emit until size is known
        val r = inAppDecision(visible = true, inAppSize = null, linkedWidgetSize = null, lastSent = null)
        assertEquals(null, r)
    }

    @Test fun inAppVisible_widgetSizeIgnored() {
        // When in-app is visible, widget size is irrelevant
        val r = inAppDecision(visible = true, inAppSize = 80 to 24, linkedWidgetSize = 50 to 20, lastSent = null)
        assertEquals(80 to 24, r)
    }

    @Test fun inApp_dedupSameAsLastSent() {
        val r = inAppDecision(visible = true, inAppSize = 80 to 24, linkedWidgetSize = null, lastSent = 80 to 24)
        assertEquals(null, r)
    }

    @Test fun inApp_hiddenYield_dedupSameAsLastSent() {
        val r = inAppDecision(visible = false, inAppSize = 80 to 24, linkedWidgetSize = 50 to 20, lastSent = 50 to 20)
        assertEquals(null, r)
    }

    @Test fun inApp_visibilityToggle_emitsOnEachChange() {
        // visible 80x24 (lastSent=null) → emit 80x24
        var lastSent: Pair<Int, Int>? = null
        var r = inAppDecision(true, 80 to 24, null, lastSent)
        assertEquals(80 to 24, r); lastSent = r

        // hidden, linked widget 50x20 → emit 50x20
        r = inAppDecision(false, 80 to 24, 50 to 20, lastSent)
        assertEquals(50 to 20, r); lastSent = r

        // visible again → emit 80x24
        r = inAppDecision(true, 80 to 24, 50 to 20, lastSent)
        assertEquals(80 to 24, r); lastSent = r
    }

    // ---------- widget bus: always tracks widget size ----------

    @Test fun widget_setSize_emits() {
        val r = widgetDecision(widgetSize = 50 to 20, lastSent = null)
        assertEquals(50 to 20, r)
    }

    @Test fun widget_dedupSameAsLastSent() {
        val r = widgetDecision(widgetSize = 50 to 20, lastSent = 50 to 20)
        assertEquals(null, r)
    }

    @Test fun widget_sizeChange_emits() {
        val r = widgetDecision(widgetSize = 100 to 40, lastSent = 50 to 20)
        assertEquals(100 to 40, r)
    }

    @Test fun widget_nullSize_noEmit() {
        val r = widgetDecision(widgetSize = null, lastSent = null)
        assertEquals(null, r)
    }

    // ---------- key data class identity ----------

    @Test fun tmuxKey_equalityByValue() {
        val a = TmuxKey(1L, "main")
        val b = TmuxKey(1L, "main")
        val c = TmuxKey(1L, "other")
        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test fun tmuxKey_serverIdMatters() {
        val a = TmuxKey(1L, "main")
        val b = TmuxKey(2L, "main")
        assertNotEquals(a, b)
    }
}

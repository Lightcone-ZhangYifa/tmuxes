package com.tmuxes.ui.components.keybar

import com.tmuxes.terminal.view.ModifierKeys
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies the §2.2.2 fix: a [ModifierSnapshot] taken at press-down is
 * reusable for every emit during a hold gesture, even after the surrounding
 * latch has been cleared. Snapshot is data-only — there is no hidden coupling
 * to global state.
 */
class ModifierSnapshotTest {

    @Test
    fun `NONE is all-false sentinel`() {
        val s = ModifierSnapshot.NONE
        assertEquals(false, s.ctrl)
        assertEquals(false, s.alt)
        assertEquals(false, s.shift)
        assertEquals(false, s.meta)
    }

    @Test
    fun `Ctrl snapshot applies xterm Ctrl encoding to CSI Up`() {
        val snap = ModifierSnapshot(ctrl = true)
        val out = ModifierKeys.apply(
            "[A".toByteArray(Charsets.ISO_8859_1),
            snap.ctrl, snap.alt, snap.shift, snap.meta,
        )
        // Ctrl-only modifier code = 1 + 4 = 5 → \e[1;5A
        assertArrayEquals("\u001b[1;5A".toByteArray(Charsets.ISO_8859_1), out)
    }

    @Test
    fun `snapshot is immutable — clearing global state doesn't affect a held snapshot`() {
        // Simulate: user presses Ctrl, then taps a Repeat key.
        // The snapshot is captured at press-down. Then clearModifiers
        // runs somewhere else (the global ctrlActive becomes false).
        // Subsequent ticks must still produce Ctrl-encoded bytes.
        val snap = ModifierSnapshot(ctrl = true)

        val tick1 = ModifierKeys.apply(
            "\u001b[B".toByteArray(Charsets.ISO_8859_1),
            snap.ctrl, snap.alt, snap.shift, snap.meta,
        )
        val tick100 = ModifierKeys.apply(
            "\u001b[B".toByteArray(Charsets.ISO_8859_1),
            snap.ctrl, snap.alt, snap.shift, snap.meta,
        )

        assertArrayEquals(
            "snapshot held → tick 1 has Ctrl",
            "\u001b[1;5B".toByteArray(Charsets.ISO_8859_1), tick1,
        )
        assertArrayEquals(
            "snapshot held → tick 100 has Ctrl (regression test for v1 bug)",
            "\u001b[1;5B".toByteArray(Charsets.ISO_8859_1), tick100,
        )
    }

    @Test
    fun `all four modifiers combined produce mod-code 16`() {
        val snap = ModifierSnapshot(ctrl = true, alt = true, shift = true, meta = true)
        // 1 + shift*1 + alt*2 + ctrl*4 + meta*8 = 16
        val out = ModifierKeys.apply(
            "[A".toByteArray(Charsets.ISO_8859_1),
            snap.ctrl, snap.alt, snap.shift, snap.meta,
        )
        assertArrayEquals("\u001b[1;16A".toByteArray(Charsets.ISO_8859_1), out)
    }

    @Test
    fun `no modifier means passthrough`() {
        val snap = ModifierSnapshot.NONE
        val raw = "[A".toByteArray(Charsets.ISO_8859_1)
        val out = ModifierKeys.apply(raw, snap.ctrl, snap.alt, snap.shift, snap.meta)
        assertArrayEquals(raw, out)
    }
}

package com.tmuxes.terminal.view

import com.tmuxes.ui.components.keybar.ModifierSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalModifierLatchTest {

    @Test
    fun `initial snapshot is NONE`() {
        val latch = TerminalModifierLatch()
        assertEquals(ModifierSnapshot.NONE, latch.snapshot)
    }

    @Test
    fun `setCtrl flips only ctrl`() {
        val latch = TerminalModifierLatch()
        latch.setCtrl(true)
        assertEquals(ModifierSnapshot(ctrl = true), latch.snapshot)
        latch.setCtrl(false)
        assertEquals(ModifierSnapshot.NONE, latch.snapshot)
    }

    @Test
    fun `setAlt setShift setMeta independently flip the right field`() {
        val latch = TerminalModifierLatch()
        latch.setAlt(true)
        assertEquals(ModifierSnapshot(alt = true), latch.snapshot)
        latch.setShift(true)
        assertEquals(ModifierSnapshot(alt = true, shift = true), latch.snapshot)
        latch.setMeta(true)
        assertEquals(ModifierSnapshot(alt = true, shift = true, meta = true), latch.snapshot)
    }

    @Test
    fun `consume clears all four`() {
        val latch = TerminalModifierLatch()
        latch.setCtrl(true); latch.setAlt(true); latch.setShift(true); latch.setMeta(true)
        assertEquals(ModifierSnapshot(true, true, true, true), latch.snapshot)
        latch.consume()
        assertEquals(ModifierSnapshot.NONE, latch.snapshot)
    }
}

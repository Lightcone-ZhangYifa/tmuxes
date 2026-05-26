package com.tmuxes.ui.screens.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalSystemBarsTest {

    @Test
    fun `unknown status bar behavior falls back to reserve`() {
        assertEquals(
            TerminalStatusBarAreaBehavior.Reserve,
            TerminalStatusBarAreaBehavior.fromKey("unknown")
        )
    }

    @Test
    fun `reserve behavior protects the terminal safe area`() {
        assertTrue(terminalStatusBarReservesSafeArea("reserve"))
    }

    @Test
    fun `draw behind behavior does not reserve the status bar safe area`() {
        assertFalse(terminalStatusBarReservesSafeArea("draw_behind"))
    }
}

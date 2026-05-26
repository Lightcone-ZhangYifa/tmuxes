package com.tmuxes.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalWidgetDefaultsTest {

    @Test
    fun `widget terminal defaults use Dracula with translucent background`() {
        val config = TerminalWidget.Companion.WidgetConfig()

        assertEquals("dracula", config.colorScheme)
        assertEquals(50, config.backgroundOpacity)
        assertEquals(100, config.opacity)
    }
}

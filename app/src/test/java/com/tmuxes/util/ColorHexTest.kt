package com.tmuxes.util

import com.tmuxes.data.settings.ColorSetting
import com.tmuxes.data.settings.SettingKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ColorHexTest {

    @Test fun `yaml string uses rgb for opaque colors`() {
        assertEquals("#A6E3A1", ColorHex.toYamlString(0xFFA6E3A1.toInt()))
    }

    @Test fun `yaml string keeps alpha when color is translucent`() {
        assertEquals("#6033B5E5", ColorHex.toYamlString(0x6033B5E5))
    }

    @Test fun `zero color is transparent hex instead of integer zero`() {
        assertEquals("#00000000", ColorHex.toYamlString(0))
    }

    @Test fun `parse rgb string as opaque argb`() {
        assertEquals(0xFFA6E3A1.toInt(), ColorHex.parse("#A6E3A1"))
    }

    @Test fun `parse argb string preserves alpha`() {
        assertEquals(0x6033B5E5, ColorHex.parse("#6033B5E5"))
    }

    @Test fun `parse rejects non hex color values`() {
        assertNull(ColorHex.parse("A6E3A1"))
        assertNull(ColorHex.parse("#A6E"))
        assertNull(ColorHex.parse(0xFFA6E3A1.toInt()))
    }

    @Test fun `color setting serializes yaml as hex and rejects numeric raw`() {
        val setting = ColorSetting(SettingKey(listOf("terminal", "cursor_color")), 0)
        assertEquals("#A6E3A1", setting.serialize(0xFFA6E3A1.toInt()))
        assertEquals(0xFFA6E3A1.toInt(), setting.parseRaw("#A6E3A1"))
        assertNull(setting.parseRaw(0xFFA6E3A1.toInt()))
    }
}

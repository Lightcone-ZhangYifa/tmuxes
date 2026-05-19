package com.tmuxes.terminal.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalColorsTest {

    // -----------------------------------------------------------------------
    // Round-trip: toJson() -> fromJson() for all presets
    // -----------------------------------------------------------------------

    @Test fun `round-trip Monokai`() = assertRoundTrip(TerminalColors.MONOKAI)
    @Test fun `round-trip Solarized Dark`() = assertRoundTrip(TerminalColors.SOLARIZED_DARK)
    @Test fun `round-trip Dracula`() = assertRoundTrip(TerminalColors.DRACULA)
    @Test fun `round-trip Nord`() = assertRoundTrip(TerminalColors.NORD)
    @Test fun `round-trip Catppuccin Mocha`() = assertRoundTrip(TerminalColors.CATPPUCCIN_MOCHA)
    @Test fun `round-trip Gruvbox Dark`() = assertRoundTrip(TerminalColors.GRUVBOX_DARK)
    @Test fun `round-trip One Dark`() = assertRoundTrip(TerminalColors.ONE_DARK)

    private fun assertRoundTrip(original: TerminalColors.ColorScheme) {
        val json = TerminalColors.toJson(original)
        val restored = TerminalColors.fromJson(json)
        assertNotNull("fromJson returned null for ${original.name}", restored)
        restored!!
        assertEquals(original.name, restored.name)
        // Colors lose alpha (forced to 0xFF), but RGB must match
        assertColorEquals("foreground", original.foreground, restored.foreground)
        assertColorEquals("background", original.background, restored.background)
        assertEquals(16, restored.ansi.size)
        for (i in 0 until 16) {
            assertColorEquals("ansi[$i]", original.ansi[i], restored.ansi[i])
        }
    }

    /** Compare only the RGB portion (lower 24 bits) since hex is #RRGGBB. Alpha is always 0xFF after round-trip. */
    private fun assertColorEquals(label: String, expected: Int, actual: Int) {
        val expRgb = expected and 0xFFFFFF
        val actRgb = actual and 0xFFFFFF
        assertEquals("$label RGB mismatch: expected #${"%06X".format(expRgb)}, got #${"%06X".format(actRgb)}", expRgb, actRgb)
        // After round-trip, alpha must be 0xFF
        assertEquals("$label alpha must be 0xFF after round-trip", 0xFF, (actual ushr 24) and 0xFF)
    }

    // -----------------------------------------------------------------------
    // fromJson() with invalid input returns null
    // -----------------------------------------------------------------------

    @Test fun `fromJson with empty string returns null`() {
        assertNull(TerminalColors.fromJson(""))
    }

    @Test fun `fromJson with garbage returns null`() {
        assertNull(TerminalColors.fromJson("not json at all"))
    }

    @Test fun `fromJson with missing name returns null`() {
        assertNull(TerminalColors.fromJson("""{"foreground":"#FFFFFF","background":"#000000","ansi":[]}"""))
    }

    @Test fun `fromJson with wrong ansi count returns null`() {
        // Only 3 entries instead of 16
        val json = """{"name":"Bad","foreground":"#FFFFFF","background":"#000000","ansi":["#000000","#111111","#222222"]}"""
        assertNull(TerminalColors.fromJson(json))
    }

    @Test fun `fromJson with invalid hex color returns null`() {
        val ansi = (0 until 16).joinToString(",") { "\"#000000\"" }
        val json = """{"name":"Bad","foreground":"#ZZZZZZ","background":"#000000","ansi":[$ansi]}"""
        assertNull(TerminalColors.fromJson(json))
    }

    @Test fun `fromJson with invalid hex in ansi returns null`() {
        val ansi = (0 until 15).joinToString(",") { "\"#000000\"" } + ",\"#GGGGGG\""
        val json = """{"name":"Bad","foreground":"#FFFFFF","background":"#000000","ansi":[$ansi]}"""
        assertNull(TerminalColors.fromJson(json))
    }

    @Test fun `fromJson with short hex string returns null`() {
        val ansi = (0 until 16).joinToString(",") { "\"#000000\"" }
        val json = """{"name":"Bad","foreground":"#FFF","background":"#000000","ansi":[$ansi]}"""
        assertNull(TerminalColors.fromJson(json))
    }

    // -----------------------------------------------------------------------
    // getCustomSchemes() edge cases
    // -----------------------------------------------------------------------

    @Test fun `getCustomSchemes with empty string returns empty list`() {
        assertTrue(TerminalColors.getCustomSchemes("").isEmpty())
    }

    @Test fun `getCustomSchemes with blank string returns empty list`() {
        assertTrue(TerminalColors.getCustomSchemes("   ").isEmpty())
    }

    @Test fun `getCustomSchemes with invalid json returns empty list`() {
        assertTrue(TerminalColors.getCustomSchemes("not a json array").isEmpty())
    }

    @Test fun `getCustomSchemes with empty json array returns empty list`() {
        assertTrue(TerminalColors.getCustomSchemes("[]").isEmpty())
    }

    @Test fun `getCustomSchemes with valid json array returns correct schemes`() {
        val scheme1 = TerminalColors.DRACULA
        val scheme2 = TerminalColors.NORD
        val jsonArray = TerminalColors.customSchemesToJson(listOf(scheme1, scheme2))
        val result = TerminalColors.getCustomSchemes(jsonArray)
        assertEquals(2, result.size)
        assertEquals(scheme1.name, result[0].name)
        assertEquals(scheme2.name, result[1].name)
    }

    @Test fun `getCustomSchemes skips invalid entries in array`() {
        // One valid, one invalid
        val valid = TerminalColors.toJson(TerminalColors.MONOKAI)
        val jsonArray = """[$valid, {"bad":"entry"}]"""
        val result = TerminalColors.getCustomSchemes(jsonArray)
        assertEquals(1, result.size)
        assertEquals("Monokai", result[0].name)
    }

    // -----------------------------------------------------------------------
    // Color values preserved through serialization
    // -----------------------------------------------------------------------

    @Test fun `specific color values preserved for Catppuccin Mocha`() {
        val json = TerminalColors.toJson(TerminalColors.CATPPUCCIN_MOCHA)
        val restored = TerminalColors.fromJson(json)!!
        // Catppuccin Mocha foreground: 0xFFCDD6F4 -> RGB = 0xCDD6F4
        assertEquals(0xCDD6F4, restored.foreground and 0xFFFFFF)
        // Background: 0xFF1E1E2E -> RGB = 0x1E1E2E
        assertEquals(0x1E1E2E, restored.background and 0xFFFFFF)
        // ANSI 0 (dark): 0xFF45475A -> RGB = 0x45475A
        assertEquals(0x45475A, restored.ansi[0] and 0xFFFFFF)
        // ANSI 1 (red): 0xFFF38BA8 -> RGB = 0xF38BA8
        assertEquals(0xF38BA8, restored.ansi[1] and 0xFFFFFF)
    }

    @Test fun `black color 000000 round trips`() {
        val ansi = IntArray(16) { 0xFF000000.toInt() }
        val scheme = TerminalColors.ColorScheme("All Black", 0xFF000000.toInt(), 0xFF000000.toInt(), ansi)
        val restored = TerminalColors.fromJson(TerminalColors.toJson(scheme))!!
        assertEquals(0xFF000000.toInt(), restored.foreground)
        assertEquals(0xFF000000.toInt(), restored.background)
        for (i in 0 until 16) {
            assertEquals(0xFF000000.toInt(), restored.ansi[i])
        }
    }

    @Test fun `white color FFFFFF round trips`() {
        val ansi = IntArray(16) { 0xFFFFFFFF.toInt() }
        val scheme = TerminalColors.ColorScheme("All White", 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), ansi)
        val restored = TerminalColors.fromJson(TerminalColors.toJson(scheme))!!
        assertEquals(0xFFFFFF, restored.foreground and 0xFFFFFF)
        assertEquals(0xFFFFFF, restored.background and 0xFFFFFF)
    }

    // -----------------------------------------------------------------------
    // customSchemesToJson() + getCustomSchemes() round-trip all presets
    // -----------------------------------------------------------------------

    @Test fun `customSchemesToJson round-trip for all presets`() {
        val allPresets = TerminalColors.ALL_PRESETS
        val json = TerminalColors.customSchemesToJson(allPresets)
        val restored = TerminalColors.getCustomSchemes(json)
        assertEquals(allPresets.size, restored.size)
        for (i in allPresets.indices) {
            assertEquals(allPresets[i].name, restored[i].name)
            assertColorEquals("preset[$i].foreground", allPresets[i].foreground, restored[i].foreground)
            assertColorEquals("preset[$i].background", allPresets[i].background, restored[i].background)
        }
    }

    @Test fun `resolveScheme prefers matching custom scheme`() {
        val ansi = IntArray(16) { 0xFF112233.toInt() }
        val custom = TerminalColors.ColorScheme(
            name = "Office Dark",
            foreground = 0xFFEFEFEF.toInt(),
            background = 0xFF101820.toInt(),
            ansi = ansi
        )
        val json = TerminalColors.customSchemesToJson(listOf(custom))

        val resolved = TerminalColors.resolveScheme("office dark", json)

        assertEquals("Office Dark", resolved.name)
        assertColorEquals("custom.background", custom.background, resolved.background)
    }

    // -----------------------------------------------------------------------
    // ALL_PRESETS contains all 7 presets
    // -----------------------------------------------------------------------

    @Test fun `ALL_PRESETS contains all 7 presets`() {
        assertEquals(7, TerminalColors.ALL_PRESETS.size)
        val names = TerminalColors.ALL_PRESETS.map { it.name }.toSet()
        assertTrue("Monokai" in names)
        assertTrue("Solarized Dark" in names)
        assertTrue("Dracula" in names)
        assertTrue("Nord" in names)
        assertTrue("Catppuccin Mocha" in names)
        assertTrue("Gruvbox Dark" in names)
        assertTrue("One Dark" in names)
    }

    // -----------------------------------------------------------------------
    // toJson output format validation
    // -----------------------------------------------------------------------

    @Test fun `toJson produces valid hex format`() {
        val json = TerminalColors.toJson(TerminalColors.MONOKAI)
        // Should contain #RRGGBB format strings
        assertTrue(json.contains("#F8F8F2"))  // Monokai foreground
        assertTrue(json.contains("#272822"))  // Monokai background
        assertTrue(json.contains("\"name\":\"Monokai\""))
    }
}

package com.tmuxes.data.settings

import com.tmuxes.i18n.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRegistryTest {

    @Test
    fun `registry has at least every preference key migrated`() {
        // This list is the persisted preference-key contract. Removing or
        // renaming a key must be an explicit schema decision, not incidental
        // cleanup during settings UI work.
        val expected = setOf(
            "app.accent_color", "app.bubble_opacity", "app.color_palette",
            "app.corner_style", "app.density", "app.fab_opacity",
            "app.favorite_sessions", "app.language", "app.last_session_name",
            "app.last_session_server_id", "app.navigation_style", "app.start_screen",
            "app.session_colors", "app.status_bar_style", "app.theme", "app.type_scale",
            "behavior.auto_scroll_output", "behavior.bell_enabled",
            "behavior.double_tap_word_select", "behavior.extra_keys_enabled",
            "behavior.keep_screen_on", "behavior.scroll_on_keystroke",
            "behavior.swipe_lines_per_arrow", "behavior.terminal_pinch_zoom",
            "behavior.vertical_swipe_mode", "behavior.vibration_enabled",
            "behavior.visual_bell",
            "editor.auto_complete", "editor.auto_indent", "editor.auto_save",
            "editor.auto_save_delay", "editor.bracket_pairing",
            "editor.current_line_highlight", "editor.errorlens.bg_intensity",
            "editor.errorlens.debounce", "editor.errorlens.enabled",
            "editor.errorlens.font_size", "editor.errorlens.hide_while_typing",
            "editor.errorlens.level", "editor.errorlens.position",
            "editor.errorlens.truncation", "editor.font_family",
            "editor.font_size", "editor.indent_guides", "editor.line_height",
            "editor.pinch_zoom",
            "editor.read_only", "editor.show_line_numbers", "editor.tab_width",
            "editor.theme", "editor.word_wrap",
            "ssh.compression", "ssh.connection_timeout", "ssh.default_term",
            "ssh.env_vars", "ssh.keepalive_interval", "ssh.keepalive_max_count",
            "ssh.preferred_ciphers", "ssh.preferred_host_key_algs",
            "ssh.preferred_kex", "ssh.preferred_macs", "ssh.read_timeout",
            "ssh.reconnect_interval_seconds", "ssh.strict_host_key",
            "ssh.transport_timeout",
            "terminal.background_opacity", "terminal.bold_is_bright",
            "terminal.color_scheme", "terminal.cursor_blink",
            "terminal.cursor_blink_speed", "terminal.cursor_color",
            "terminal.cursor_style", "terminal.custom_schemes",
            "terminal.extra_keys_height",
            "terminal.font_family", "terminal.font_size", "terminal.font_weight",
            "terminal.line_spacing", "terminal.padding", "terminal.scrollback_lines",
            "terminal.selection_color", "terminal.tmux_prefix_key",
            "terminal.underline_style", "terminal.volume_keys"
        )
        val actual = Settings.all.map { it.key.flatPath }.toSet()
        val missing = expected - actual
        assertTrue("Settings registry missing: $missing", missing.isEmpty())
    }

    @Test
    fun `no duplicate paths within the same fileType`() {
        val pairs = Settings.all.map { it.key.fileType to it.key.flatPath }
        assertEquals("Duplicate paths in registry", pairs.size, pairs.toSet().size)
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `every default validates against its own setting`() {
        for (s in Settings.all) {
            val verdict = (s as Setting<Any>).validate(s.default)
            assertTrue(
                "default for ${s.key.flatPath} fails validate: $verdict",
                verdict is SettingValidation.Ok
            )
        }
    }

    @Test
    fun `byPath round-trips for every registered setting`() {
        for (s in Settings.all) {
            val found = Settings.byPath(s.key.flatPath, s.key.fileType)
            assertNotNull("byPath miss for ${s.key.flatPath}", found)
            assertEquals(s.key.flatPath, found!!.key.flatPath)
        }
    }

    @Test
    fun `parseRaw and serialize round-trip default`() {
        for (s in Settings.all) {
            @Suppress("UNCHECKED_CAST")
            val any = s as Setting<Any>
            val raw = any.serialize(any.default)
            val parsed = any.parseRaw(raw)
            assertEquals(
                "round-trip mismatch for ${s.key.flatPath}",
                any.default,
                parsed
            )
        }
    }

    @Test
    fun `every enum setting's default appears in its option list`() {
        for (s in Settings.all) {
            when (s) {
                is StringEnumSetting -> assertTrue(
                    "default '${s.default}' not in options for ${s.key.flatPath}",
                    s.values.contains(s.default)
                )
                is IntEnumSetting -> assertTrue(
                    "default ${s.default} not in options for ${s.key.flatPath}",
                    s.values.contains(s.default)
                )
                else -> Unit
            }
        }
    }

    @Test
    fun `language setting options are sourced from app language catalog`() {
        val expected = AppLanguage.entries.map { it.settingValue to it.displayName }
        val actual = Settings.appLanguage.options.map { it.value to it.label }
        assertEquals(expected, actual)
    }

    @Test
    fun `app appearance screen exposes language setting`() {
        val paths = SettingScreens.appAppearance.groups
            .flatMap { group -> group.items }
            .filterIsInstance<SettingItem.Reg>()
            .map { item -> item.setting.key.flatPath }

        assertTrue(paths.contains(Settings.appLanguage.key.flatPath))
    }
}

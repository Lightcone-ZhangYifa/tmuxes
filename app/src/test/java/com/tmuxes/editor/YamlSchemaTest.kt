package com.tmuxes.editor

import com.tmuxes.data.settings.BooleanSetting
import com.tmuxes.data.settings.ColorSetting
import com.tmuxes.data.settings.IntSetting
import com.tmuxes.data.settings.SettingFile
import com.tmuxes.data.settings.Settings
import com.tmuxes.data.settings.StringEnumSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies that the YAML schema tree exposed by [YamlSchema] matches the
 * expectations of the editor (diagnostics + completion). The schema is
 * tree-based: every node is either a [YamlSchema.SchemaNode.Section] or a
 * [YamlSchema.SchemaNode.Leaf]; leaves come in two flavours,
 * [YamlSchema.SchemaNode.FromSetting] (auto-derived from
 * [com.tmuxes.data.settings.Settings]) and
 * [YamlSchema.SchemaNode.InlineSpec] (hand-written for non-Settings
 * payloads — server rows, widget instance config).
 */
class YamlSchemaTest {

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun childNames(path: String, ft: YamlFileType) =
        YamlSchema.childrenAtFlat(path, ft).map { it.name }

    private fun leafTypeHint(section: String, key: String, ft: YamlFileType) =
        YamlSchema.leafAt(section, key, ft)?.typeHint

    // =======================================================================
    // GLOBAL — derived directly from Settings registry
    // =======================================================================

    @Test fun globalRootContainsApp() = assertTrue("app" in YamlSchema.sectionNames(YamlFileType.GLOBAL))
    @Test fun globalRootContainsBehavior() = assertTrue("behavior" in YamlSchema.sectionNames(YamlFileType.GLOBAL))
    @Test fun globalRootContainsSsh() = assertTrue("ssh" in YamlSchema.sectionNames(YamlFileType.GLOBAL))
    @Test fun globalRootContainsTerminal() = assertTrue("terminal" in YamlSchema.sectionNames(YamlFileType.GLOBAL))
    @Test fun globalRootContainsEditor() = assertTrue("editor" in YamlSchema.sectionNames(YamlFileType.GLOBAL))

    @Test fun globalRootSectionsAreSorted() {
        val names = YamlSchema.sectionNames(YamlFileType.GLOBAL)
        assertEquals(names, names.sorted())
    }

    @Test fun globalRootSectionsExactly() {
        // The set should be exactly the unique top-level segments declared in Settings.
        val expected = Settings.all
            .filter { it.key.fileType == SettingFile.GLOBAL }
            .map { it.key.path.first() }
            .toSortedSet()
            .toList()
        assertEquals(expected, YamlSchema.sectionNames(YamlFileType.GLOBAL))
    }

    @Test fun everyGlobalSettingResolvesToFromSettingLeaf() {
        for (s in Settings.all.filter { it.key.fileType == SettingFile.GLOBAL }) {
            val node = YamlSchema.nodeAt(s.key.path, YamlFileType.GLOBAL)
            assertNotNull("Missing leaf for ${s.key.flatPath}", node)
            assertTrue(
                "Expected FromSetting leaf for ${s.key.flatPath}, was $node",
                node is YamlSchema.SchemaNode.FromSetting
            )
            assertEquals(s, (node as YamlSchema.SchemaNode.FromSetting).setting)
        }
    }

    // ── Nested errorlens.* (the original false-positive driver) ─────────────

    @Test fun editorContainsErrorlensSection() {
        assertTrue("errorlens" in childNames("editor", YamlFileType.GLOBAL))
    }

    @Test fun editorErrorlensIsSection() {
        val node = YamlSchema.nodeAt(listOf("editor", "errorlens"), YamlFileType.GLOBAL)
        assertTrue("expected errorlens to be a Section, was $node",
            node is YamlSchema.SchemaNode.Section)
    }

    @Test fun editorErrorlensEnabledIsBooleanLeaf() {
        val leaf = YamlSchema.leafAt("editor.errorlens", "enabled", YamlFileType.GLOBAL)
        assertNotNull(leaf)
        assertEquals("Boolean", leaf!!.typeHint)
    }

    @Test fun editorErrorlensLevelIsEnumLeaf() {
        val leaf = YamlSchema.leafAt("editor.errorlens", "level", YamlFileType.GLOBAL)
        assertNotNull(leaf)
        assertTrue("error_only" in leaf!!.completions())
        assertTrue("error_warning" in leaf.completions())
        assertTrue("all" in leaf.completions())
    }

    @Test fun editorErrorlensTruncationIsIntRangeLeaf() {
        val leaf = YamlSchema.leafAt("editor.errorlens", "truncation", YamlFileType.GLOBAL)
        assertNotNull(leaf)
        assertEquals("Int (20..400)", leaf!!.typeHint)
    }

    @Test fun editorErrorlensAllNestedKeysPresent() {
        // editor.errorlens.* should expose all 8 nested settings registered
        // under that prefix — these were the keys flagged "Unknown" by the
        // old flat-section schema.
        val keys = childNames("editor.errorlens", YamlFileType.GLOBAL).toSet()
        val expected = setOf(
            "enabled", "level", "position", "bg_intensity", "font_size",
            "truncation", "debounce", "hide_while_typing"
        )
        assertEquals(expected, keys)
    }

    // ── Spot-check FromSetting leaf metadata ────────────────────────────────

    @Test fun terminalFontFamilyEnumCompletions() {
        val leaf = YamlSchema.leafAt("terminal", "font_family", YamlFileType.GLOBAL)!!
        assertTrue("jetbrains_mono" in leaf.completions())
        assertTrue("monospace" in leaf.completions())
    }

    @Test fun terminalFontSizeIsIntRangeHint() {
        assertEquals("Int (6..36)", leafTypeHint("terminal", "font_size", YamlFileType.GLOBAL))
    }

    @Test fun terminalCursorBlinkSpeedRangeHint() {
        assertEquals("Int (100..2000)", leafTypeHint("terminal", "cursor_blink_speed", YamlFileType.GLOBAL))
    }

    @Test fun terminalBackgroundOpacityRangeHint() {
        assertEquals("Int (0..100)", leafTypeHint("terminal", "background_opacity", YamlFileType.GLOBAL))
    }

    @Test fun terminalCursorColorIsHexHint() {
        assertEquals("Hex color string (\"#RRGGBB\" or \"#AARRGGBB\")", leafTypeHint("terminal", "cursor_color", YamlFileType.GLOBAL))
    }

    @Test fun terminalCursorBlinkIsBooleanHint() {
        assertEquals("Boolean", leafTypeHint("terminal", "cursor_blink", YamlFileType.GLOBAL))
    }

    @Test fun terminalColorSchemeEnumHint() {
        val hint = leafTypeHint("terminal", "color_scheme", YamlFileType.GLOBAL)!!
        assertTrue("catppuccin missing in $hint", "catppuccin" in hint)
        assertTrue("monokai missing in $hint", "monokai" in hint)
    }

    @Test fun terminalCustomSchemesIsStringHint() {
        assertEquals("String", leafTypeHint("terminal", "custom_schemes", YamlFileType.GLOBAL))
    }

    @Test fun appLastSessionServerIdIsLongHint() {
        assertEquals("Long", leafTypeHint("app", "last_session_server_id", YamlFileType.GLOBAL))
    }

    @Test fun editorTabWidthIsIntEnumHint() {
        val hint = leafTypeHint("editor", "tab_width", YamlFileType.GLOBAL)!!
        assertTrue("2 missing in $hint", "2" in hint)
        assertTrue("4 missing in $hint", "4" in hint)
        assertTrue("8 missing in $hint", "8" in hint)
    }

    @Test fun appFavoriteSessionsIsSetOfStringHint() {
        assertEquals("Set<String>", leafTypeHint("app", "favorite_sessions", YamlFileType.GLOBAL))
    }

    // ── validate() routes to underlying setting ─────────────────────────────

    @Test fun validateBooleanRejectsString() {
        val leaf = YamlSchema.leafAt("terminal", "cursor_blink", YamlFileType.GLOBAL)!!
        assertNotNull(leaf.validate("yes"))
    }

    @Test fun validateBooleanAcceptsTrue() {
        val leaf = YamlSchema.leafAt("terminal", "cursor_blink", YamlFileType.GLOBAL)!!
        assertNull(leaf.validate(true))
    }

    @Test fun validateIntRangeRejectsOutOfRange() {
        val leaf = YamlSchema.leafAt("terminal", "font_size", YamlFileType.GLOBAL)!!
        assertNotNull(leaf.validate(200))
    }

    @Test fun validateIntRangeAcceptsInRange() {
        val leaf = YamlSchema.leafAt("terminal", "font_size", YamlFileType.GLOBAL)!!
        assertNull(leaf.validate(14))
    }

    @Test fun validateStringEnumRejectsUnknown() {
        val leaf = YamlSchema.leafAt("terminal", "color_scheme", YamlFileType.GLOBAL)!!
        assertNotNull(leaf.validate("hot_pink"))
    }

    @Test fun validateStringEnumAcceptsKnown() {
        val leaf = YamlSchema.leafAt("terminal", "color_scheme", YamlFileType.GLOBAL)!!
        assertNull(leaf.validate("monokai"))
    }

    @Test fun validateNullPassesThrough() {
        // null = absent from YAML; no error should be reported.
        val leaf = YamlSchema.leafAt("terminal", "font_size", YamlFileType.GLOBAL)!!
        assertNull(leaf.validate(null))
    }

    @Test fun validateColorRejectsNumber() {
        val leaf = YamlSchema.leafAt("terminal", "cursor_color", YamlFileType.GLOBAL)!!
        assertNotNull(leaf.validate(0xFF00FFL))
    }

    @Test fun validateColorAcceptsHexString() {
        val leaf = YamlSchema.leafAt("terminal", "cursor_color", YamlFileType.GLOBAL)!!
        assertNull(leaf.validate("#FF00FF"))
        assertNull(leaf.validate("#60FF00FF"))
    }

    @Test fun validateColorRejectsMalformedHexString() {
        val leaf = YamlSchema.leafAt("terminal", "cursor_color", YamlFileType.GLOBAL)!!
        assertNotNull(leaf.validate("#FFF"))
    }

    @Test fun validateLongAcceptsAnyNumber() {
        val leaf = YamlSchema.leafAt("app", "last_session_server_id", YamlFileType.GLOBAL)!!
        assertNull(leaf.validate(42))
        assertNull(leaf.validate(42L))
    }

    @Test fun validateSetOfStringAcceptsList() {
        val leaf = YamlSchema.leafAt("app", "favorite_sessions", YamlFileType.GLOBAL)!!
        assertNull(leaf.validate(listOf("a", "b")))
    }

    @Test fun validateSetOfStringRejectsString() {
        val leaf = YamlSchema.leafAt("app", "favorite_sessions", YamlFileType.GLOBAL)!!
        assertNotNull(leaf.validate("a,b"))
    }

    // ── completions() ───────────────────────────────────────────────────────

    @Test fun booleanCompletionsAreTrueFalse() {
        val leaf = YamlSchema.leafAt("terminal", "cursor_blink", YamlFileType.GLOBAL)!!
        assertEquals(listOf("true", "false"), leaf.completions())
    }

    @Test fun stringSettingHasNoCompletions() {
        val leaf = YamlSchema.leafAt("ssh", "default_term", YamlFileType.GLOBAL)!!
        assertTrue(leaf.completions().isEmpty())
    }

    @Test fun intSettingHasNoCompletions() {
        val leaf = YamlSchema.leafAt("terminal", "font_size", YamlFileType.GLOBAL)!!
        assertTrue(leaf.completions().isEmpty())
    }

    @Test fun intEnumCompletionsAreNumericStrings() {
        val leaf = YamlSchema.leafAt("editor", "tab_width", YamlFileType.GLOBAL)!!
        assertEquals(listOf("2", "4", "8"), leaf.completions())
    }

    // ── Underlying setting linkage ──────────────────────────────────────────

    @Test fun terminalFontSizeLinksToIntSetting() {
        val leaf = YamlSchema.nodeAt(listOf("terminal", "font_size"), YamlFileType.GLOBAL)
            as YamlSchema.SchemaNode.FromSetting
        assertTrue(leaf.setting is IntSetting)
        assertEquals(6..36, (leaf.setting as IntSetting).range)
    }

    @Test fun terminalColorSchemeLinksToStringEnumSetting() {
        val leaf = YamlSchema.nodeAt(listOf("terminal", "color_scheme"), YamlFileType.GLOBAL)
            as YamlSchema.SchemaNode.FromSetting
        assertTrue(leaf.setting is StringEnumSetting)
    }

    @Test fun terminalCursorColorLinksToColorSetting() {
        val leaf = YamlSchema.nodeAt(listOf("terminal", "cursor_color"), YamlFileType.GLOBAL)
            as YamlSchema.SchemaNode.FromSetting
        assertTrue(leaf.setting is ColorSetting)
    }

    @Test fun terminalBoldIsBrightLinksToBooleanSetting() {
        val leaf = YamlSchema.nodeAt(listOf("terminal", "bold_is_bright"), YamlFileType.GLOBAL)
            as YamlSchema.SchemaNode.FromSetting
        assertTrue(leaf.setting is BooleanSetting)
    }

    // =======================================================================
    // WIDGET — hand-written tree for supported per-widget overrides
    // =======================================================================

    @Test fun widgetRootContainsSession() = assertTrue("session" in YamlSchema.sectionNames(YamlFileType.WIDGET))
    @Test fun widgetRootContainsWidget() = assertTrue("widget" in YamlSchema.sectionNames(YamlFileType.WIDGET))
    @Test fun widgetRootContainsTerminal() = assertTrue("terminal" in YamlSchema.sectionNames(YamlFileType.WIDGET))

    @Test fun widgetRootSectionsExactly() {
        assertEquals(listOf("session", "widget", "terminal"), YamlSchema.sectionNames(YamlFileType.WIDGET))
    }

    @Test fun widgetSessionHasServerIdAndSessionName() {
        val keys = childNames("session", YamlFileType.WIDGET).toSet()
        assertEquals(setOf("server_id", "session_name"), keys)
    }

    @Test fun widgetSessionServerIdIsLong() {
        assertEquals("Long", leafTypeHint("session", "server_id", YamlFileType.WIDGET))
    }

    @Test fun widgetSessionNameIsString() {
        assertEquals("String", leafTypeHint("session", "session_name", YamlFileType.WIDGET))
    }

    @Test fun widgetWidgetSectionHasChromeSettings() {
        val keys = childNames("widget", YamlFileType.WIDGET).toSet()
        assertEquals(setOf("opacity", "show_title_bar", "orientation", "title_accent_color"), keys)
    }

    @Test fun widgetOpacityIsIntRange0To100() {
        assertEquals("Int (0..100)", leafTypeHint("widget", "opacity", YamlFileType.WIDGET))
    }

    @Test fun widgetTitleAccentColorIsHex() {
        val leaf = YamlSchema.leafAt("widget", "title_accent_color", YamlFileType.WIDGET)!!
        assertEquals("Hex color string (\"#RRGGBB\" or \"#AARRGGBB\")", leaf.typeHint)
        assertNull(leaf.validate("#604FC3F7"))
        assertNotNull(leaf.validate(0x604FC3F7))
    }

    @Test fun widgetShowTitleBarIsBoolean() {
        assertEquals("Boolean", leafTypeHint("widget", "show_title_bar", YamlFileType.WIDGET))
    }

    @Test fun widgetOrientationEnumValues() {
        val leaf = YamlSchema.leafAt("widget", "orientation", YamlFileType.WIDGET)!!
        assertEquals(listOf("0", "90", "180", "270"), leaf.completions())
    }

    @Test fun widgetTerminalContainsOnlySupportedKeys() {
        val widgetKeys = childNames("terminal", YamlFileType.WIDGET).toSet()
        assertEquals(setOf(
            "font_family", "font_size", "font_weight", "color_scheme",
            "cursor_style", "cursor_blink", "cursor_color",
            "background_opacity", "bold_is_bright", "underline_style",
            "line_spacing", "padding", "scrollback_lines"
        ), widgetKeys)
        assertFalse("cursor_blink_speed is not meaningful for static widgets",
            "cursor_blink_speed" in widgetKeys)
        assertFalse("extra key settings belong to the in-app terminal only",
            "extra_keys_height" in widgetKeys)
    }

    @Test fun widgetTerminalFontSizeAllowsAuto() {
        assertEquals(
            "Float (0 = auto, 6..36 otherwise)",
            leafTypeHint("terminal", "font_size", YamlFileType.WIDGET)
        )
    }

    @Test fun widgetLegacyOrientationValidatesEnum() {
        val leaf = YamlSchema.leafAt("widget", "orientation", YamlFileType.WIDGET)!!
        assertNull(leaf.validate("90"))
        assertNotNull(leaf.validate("45"))
    }

    @Test fun widgetLegacyOpacityRejectsOutOfRange() {
        val leaf = YamlSchema.leafAt("widget", "opacity", YamlFileType.WIDGET)!!
        assertNotNull(leaf.validate(200))
        assertNull(leaf.validate(50))
    }

    @Test fun widgetLegacyServerIdRejectsString() {
        val leaf = YamlSchema.leafAt("session", "server_id", YamlFileType.WIDGET)!!
        assertNotNull(leaf.validate("not a number"))
    }

    @Test fun widgetLegacyServerIdAcceptsNumber() {
        val leaf = YamlSchema.leafAt("session", "server_id", YamlFileType.WIDGET)!!
        assertNull(leaf.validate(42L))
    }

    @Test fun widgetLegacyShowTitleBarRejectsString() {
        val leaf = YamlSchema.leafAt("widget", "show_title_bar", YamlFileType.WIDGET)!!
        assertNotNull(leaf.validate("yes"))
    }

    // =======================================================================
    // SERVERS — hand-written `servers[]` row schema
    // =======================================================================

    private val serversType = YamlFileType.SERVERS

    @Test fun serversRootIsExactlyServersList() {
        assertEquals(listOf("servers[]"), YamlSchema.sectionNames(serversType))
    }

    @Test fun serversRowHasExpectedFieldCount() {
        // 1 id, 1 hostname, 1 port, 1 username, 1 name, 1 auth_method,
        // 3 credentials (password, private_key_data, passphrase),
        // color, parent_id, keep_alive_interval,
        // compression, last_connected_at, is_enabled, sort_order,
        // 11 advanced ssh fields, remote_forwards, local_forwards = 29
        val keys = childNames("servers[]", serversType)
        assertEquals(29, keys.size)
    }

    @Test fun serversIdIsLong() {
        assertEquals("Long", leafTypeHint("servers[]", "id", serversType))
    }

    @Test fun serversHostnameIsString() {
        assertEquals("String", leafTypeHint("servers[]", "hostname", serversType))
    }

    @Test fun serversPortIsIntRange1To65535() {
        assertEquals("Int (1..65535)", leafTypeHint("servers[]", "port", serversType))
    }

    @Test fun serversColorIsHex() {
        val leaf = YamlSchema.leafAt("servers[]", "color", serversType)!!
        assertEquals("Hex color string (\"#RRGGBB\" or \"#AARRGGBB\")", leaf.typeHint)
        assertNull(leaf.validate("#A6E3A1"))
        assertNotNull(leaf.validate(-5842271))
    }

    @Test fun serversUsernameIsString() {
        assertEquals("String", leafTypeHint("servers[]", "username", serversType))
    }

    @Test fun serversAuthMethodEnumValues() {
        val leaf = YamlSchema.leafAt("servers[]", "auth_method", serversType)!!
        assertEquals(listOf("PASSWORD", "KEY", "KEY_WITH_PASSPHRASE"), leaf.completions())
        assertFalse("PARENT must be removed", "PARENT" in leaf.completions())
    }

    @Test fun serversCompressionIsBoolean() {
        assertEquals("Boolean", leafTypeHint("servers[]", "compression", serversType))
    }

    @Test fun serversIsEnabledIsBoolean() {
        assertEquals("Boolean", leafTypeHint("servers[]", "is_enabled", serversType))
    }

    @Test fun serversKeepAliveIntervalRange() {
        assertEquals("Int (0..3600)", leafTypeHint("servers[]", "keep_alive_interval", serversType))
    }

    @Test fun serversConnectionTimeoutRange() {
        assertEquals("Int (1..300)", leafTypeHint("servers[]", "connection_timeout", serversType))
    }

    @Test fun serversStrictHostKeyEnumValues() {
        val leaf = YamlSchema.leafAt("servers[]", "strict_host_key", serversType)!!
        assertEquals(listOf("ask", "accept", "reject"), leaf.completions())
    }

    @Test fun serversLastConnectedAtIsLong() {
        assertEquals("Long", leafTypeHint("servers[]", "last_connected_at", serversType))
    }

    @Test fun serversSortOrderIsInt() {
        assertEquals("Int", leafTypeHint("servers[]", "sort_order", serversType))
    }

    @Test fun serversAuthProfilesSectionRemoved() {
        // The auth-profile fallback machinery was deleted; each server simply
        // carries its own credentials.
        assertTrue(YamlSchema.childrenAtFlat("servers[].auth_profiles[]", serversType).isEmpty())
    }

    @Test fun serversValidatePortOutOfRange() {
        val leaf = YamlSchema.leafAt("servers[]", "port", serversType)!!
        assertNotNull(leaf.validate(70000))
    }

    @Test fun serversValidatePortInRange() {
        val leaf = YamlSchema.leafAt("servers[]", "port", serversType)!!
        assertNull(leaf.validate(22))
    }

    @Test fun serversValidateAuthMethodUnknown() {
        val leaf = YamlSchema.leafAt("servers[]", "auth_method", serversType)!!
        assertNotNull(leaf.validate("OAUTH"))
    }

    @Test fun serversValidateAuthMethodKnown() {
        val leaf = YamlSchema.leafAt("servers[]", "auth_method", serversType)!!
        assertNull(leaf.validate("PASSWORD"))
    }

    // =======================================================================
    // Edge cases — unknown / empty paths
    // =======================================================================

    @Test fun emptyPathReturnsRootForGlobal() {
        val rootNames = YamlSchema.childrenAtFlat("", YamlFileType.GLOBAL).map { it.name }
        assertEquals(YamlSchema.sectionNames(YamlFileType.GLOBAL), rootNames)
    }

    @Test fun unknownTopLevelSectionReturnsEmpty() {
        assertTrue(YamlSchema.childrenAtFlat("nonexistent", YamlFileType.GLOBAL).isEmpty())
    }

    @Test fun unknownNestedSectionReturnsEmpty() {
        assertTrue(YamlSchema.childrenAtFlat("editor.does_not_exist", YamlFileType.GLOBAL).isEmpty())
    }

    @Test fun unknownLeafReturnsNull() {
        assertNull(YamlSchema.leafAt("terminal", "nonexistent_key", YamlFileType.GLOBAL))
    }

    @Test fun emptyKeyInSectionReturnsNull() {
        assertNull(YamlSchema.leafAt("terminal", "", YamlFileType.GLOBAL))
    }

    @Test fun nodeAtEmptyPathReturnsNull() {
        assertNull(YamlSchema.nodeAt(emptyList(), YamlFileType.GLOBAL))
    }

    @Test fun nodeAtSectionPathReturnsSection() {
        val node = YamlSchema.nodeAt(listOf("terminal"), YamlFileType.GLOBAL)
        assertTrue(node is YamlSchema.SchemaNode.Section)
    }

    @Test fun nodeAtLeafPathReturnsLeaf() {
        val node = YamlSchema.nodeAt(listOf("terminal", "font_size"), YamlFileType.GLOBAL)
        assertTrue(node is YamlSchema.SchemaNode.Leaf)
    }

    @Test fun leafAtAcrossNestedPath() {
        val leaf = YamlSchema.leafAt("editor.errorlens", "debounce", YamlFileType.GLOBAL)
        assertNotNull(leaf)
    }

    // =======================================================================
    // SNIPPETS — hand-written for libraries[]/snippets[] hierarchy
    // =======================================================================

    @Test fun snippetsRootIsExactlyLibrariesList() {
        assertEquals(listOf("libraries[]"), YamlSchema.sectionNames(YamlFileType.SNIPPETS))
    }

    @Test fun libraryRowHasExpectedFields() {
        val keys = childNames("libraries[]", YamlFileType.SNIPPETS).toSet()
        assertEquals(
            setOf("id", "name", "description", "icon_name", "is_enabled", "sort_order", "snippets[]"),
            keys
        )
    }

    @Test fun snippetRowHasExpectedFields() {
        val keys = childNames("libraries[].snippets[]", YamlFileType.SNIPPETS).toSet()
        assertEquals(
            setOf("id", "name", "command", "is_enabled", "is_favorited", "sort_order"),
            keys
        )
    }

    @Test fun libraryIdIsLong() {
        assertEquals("Long", leafTypeHint("libraries[]", "id", YamlFileType.SNIPPETS))
    }

    @Test fun libraryNameIsString() {
        assertEquals("String", leafTypeHint("libraries[]", "name", YamlFileType.SNIPPETS))
    }

    @Test fun libraryIsEnabledIsBoolean() {
        assertEquals("Boolean", leafTypeHint("libraries[]", "is_enabled", YamlFileType.SNIPPETS))
    }

    @Test fun snippetCommandIsString() {
        assertEquals("String", leafTypeHint("libraries[].snippets[]", "command", YamlFileType.SNIPPETS))
    }
}

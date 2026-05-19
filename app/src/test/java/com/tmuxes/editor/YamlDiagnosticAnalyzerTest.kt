package com.tmuxes.editor

import org.junit.Assert.*
import org.junit.Test

class YamlDiagnosticAnalyzerTest {

    // ── Valid YAML ───────────────────────────────────────────────────────────

    @Test
    fun validYamlReturnsNoDiagnostics() {
        val yaml = """
            terminal:
              color_scheme: dracula
              cursor_blink: true
              font_weight: bold
        """.trimIndent()
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.WIDGET)
        assertTrue("Expected no diagnostics for valid YAML, got: $result", result.isEmpty())
    }

    @Test
    fun emptyYamlReturnsNoDiagnostics() {
        val result = YamlDiagnosticAnalyzer.analyze("", fileType = YamlFileType.WIDGET)
        assertTrue("Expected no diagnostics for empty YAML", result.isEmpty())
    }

    @Test
    fun yamlWithOnlyCommentsReturnsNoDiagnostics() {
        val yaml = "# This is a comment\n# Another comment"
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.WIDGET)
        assertTrue("Expected no diagnostics for comment-only YAML", result.isEmpty())
    }

    // ── Syntax errors ────────────────────────────────────────────────────────

    @Test
    fun syntaxErrorUnclosedBracketReturnsError() {
        val yaml = "terminal:\n  font_size: [invalid"
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.WIDGET)
        assertTrue("Expected at least one diagnostic for syntax error", result.isNotEmpty())
        assertTrue("Expected ERROR severity for syntax error",
            result.any { it.severity == YamlDiagnosticAnalyzer.Severity.ERROR })
    }

    @Test
    fun syntaxErrorReturnsExactlyOneDiagnostic() {
        val yaml = "terminal:\n  font_size: {unclosed"
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.WIDGET)
        assertEquals("Expected exactly one diagnostic for single syntax error", 1, result.size)
    }

    @Test
    fun syntaxErrorHasNonEmptyMessage() {
        val yaml = "bad: yaml: here: [\n  unclosed"
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.WIDGET)
        assertTrue("Expected at least one diagnostic", result.isNotEmpty())
        assertTrue("Expected non-empty message", result.first().message.isNotEmpty())
    }

    // ── Unknown sections ─────────────────────────────────────────────────────

    @Test
    fun unknownSectionAtRootReturnsWarning() {
        val yaml = "unknown_section:\n  key: value"
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.WIDGET)
        assertTrue("Expected at least one diagnostic for unknown section", result.isNotEmpty())
        assertTrue("Expected WARNING for unknown section",
            result.any { it.severity == YamlDiagnosticAnalyzer.Severity.WARNING && "unknown_section" in it.message })
    }

    @Test
    fun unknownSectionDoesNotCascadeToChildKeys() {
        val yaml = "unknown_section:\n  any_key: any_value"
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.WIDGET)
        // Only one warning for the unknown section, not for the key inside it
        assertEquals("Expected exactly one warning for unknown section", 1, result.size)
    }

    @Test
    fun knownSectionsDoNotProduceWarning() {
        val yaml = "terminal:\n  cursor_blink: true\nwidget:\n  show_title_bar: false"
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.WIDGET)
        assertTrue("Expected no diagnostics for known sections with valid keys",
            result.none { it.severity == YamlDiagnosticAnalyzer.Severity.WARNING })
    }

    // ── Unknown keys ─────────────────────────────────────────────────────────

    @Test
    fun unknownKeyInTerminalSectionReturnsWarning() {
        val yaml = "terminal:\n  unknown_key: value"
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.WIDGET)
        assertTrue("Expected at least one diagnostic for unknown key", result.isNotEmpty())
        assertTrue("Expected WARNING for unknown key",
            result.any { it.severity == YamlDiagnosticAnalyzer.Severity.WARNING && "unknown_key" in it.message })
    }

    @Test
    fun unknownKeyInWidgetSectionReturnsWarning() {
        val yaml = "widget:\n  bogus_setting: 123"
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.WIDGET)
        assertTrue("Expected WARNING for unknown key in widget",
            result.any { it.severity == YamlDiagnosticAnalyzer.Severity.WARNING && "bogus_setting" in it.message })
    }

    @Test
    fun unknownKeyInSessionSectionReturnsWarning() {
        val yaml = "session:\n  nonexistent: value"
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.WIDGET)
        assertTrue("Expected WARNING for unknown key in session",
            result.any { it.severity == YamlDiagnosticAnalyzer.Severity.WARNING && "nonexistent" in it.message })
    }

    // ── ENUM validation ──────────────────────────────────────────────────────

    @Test
    fun customColorSchemeNamePassesCheck() {
        val yaml = "terminal:\n  color_scheme: invalid_scheme"
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.WIDGET)
        assertTrue("Widget color_scheme accepts custom scheme names",
            result.none { it.severity == YamlDiagnosticAnalyzer.Severity.ERROR })
    }

    @Test
    fun validEnumValueForColorSchemePassesCheck() {
        val yaml = "terminal:\n  color_scheme: dracula"
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.WIDGET)
        assertTrue("Expected no error for valid color_scheme", result.none { it.severity == YamlDiagnosticAnalyzer.Severity.ERROR })
    }

    @Test
    fun invalidEnumValueForCursorStyleReturnsError() {
        val yaml = "terminal:\n  cursor_style: arrow"
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.WIDGET)
        assertTrue("Expected ERROR for invalid cursor_style",
            result.any { it.severity == YamlDiagnosticAnalyzer.Severity.ERROR && "cursor_style" in it.message })
    }

    @Test
    fun validEnumValueForOrientationPassesCheck() {
        val yaml = "widget:\n  orientation: \"90\""
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.WIDGET)
        assertTrue("Expected no error for valid orientation",
            result.none { it.severity == YamlDiagnosticAnalyzer.Severity.ERROR })
    }

    @Test
    fun invalidEnumValueForOrientationReturnsError() {
        val yaml = "widget:\n  orientation: 45"
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.WIDGET)
        assertTrue("Expected ERROR for invalid orientation",
            result.any { it.severity == YamlDiagnosticAnalyzer.Severity.ERROR && "orientation" in it.message })
    }

    // ── INT_RANGE validation ─────────────────────────────────────────────────

    @Test
    fun intRangeViolationForOpacityOver100ReturnsError() {
        val yaml = "widget:\n  opacity: 200"
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.WIDGET)
        assertTrue("Expected ERROR for opacity out of range",
            result.any { it.severity == YamlDiagnosticAnalyzer.Severity.ERROR && "opacity" in it.message })
    }

    @Test
    fun intRangeViolationForOpacityBelow0ReturnsError() {
        val yaml = "widget:\n  opacity: -1"
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.WIDGET)
        assertTrue("Expected ERROR for opacity below range",
            result.any { it.severity == YamlDiagnosticAnalyzer.Severity.ERROR && "opacity" in it.message })
    }

    @Test
    fun validOpacityPassesRangeCheck() {
        val yaml = "widget:\n  opacity: 50"
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.WIDGET)
        assertTrue("Expected no error for opacity=50", result.none { it.severity == YamlDiagnosticAnalyzer.Severity.ERROR })
    }

    @Test
    fun intRangeViolationForBackgroundOpacityReturnsError() {
        val yaml = "terminal:\n  background_opacity: 200"
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.WIDGET)
        assertTrue("Expected ERROR for background_opacity out of range",
            result.any { it.severity == YamlDiagnosticAnalyzer.Severity.ERROR && "background_opacity" in it.message })
    }

    // ── BOOLEAN validation ───────────────────────────────────────────────────

    @Test
    fun invalidBooleanValueForCursorBlinkReturnsError() {
        val yaml = "terminal:\n  cursor_blink: maybe"
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.WIDGET)
        assertTrue("Expected ERROR for invalid cursor_blink value",
            result.any { it.severity == YamlDiagnosticAnalyzer.Severity.ERROR && "cursor_blink" in it.message })
    }

    @Test
    fun validBooleanTrueForCursorBlinkPassesCheck() {
        val yaml = "terminal:\n  cursor_blink: true"
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.WIDGET)
        assertTrue("Expected no error for cursor_blink=true", result.none { it.severity == YamlDiagnosticAnalyzer.Severity.ERROR })
    }

    @Test
    fun validBooleanFalseForCursorBlinkPassesCheck() {
        val yaml = "terminal:\n  cursor_blink: false"
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.WIDGET)
        assertTrue("Expected no error for cursor_blink=false", result.none { it.severity == YamlDiagnosticAnalyzer.Severity.ERROR })
    }

    @Test
    fun invalidBooleanValueForShowTitleBarReturnsError() {
        val yaml = "widget:\n  show_title_bar: yes_please"
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.WIDGET)
        assertTrue("Expected ERROR for invalid show_title_bar value",
            result.any { it.severity == YamlDiagnosticAnalyzer.Severity.ERROR && "show_title_bar" in it.message })
    }

    @Test
    fun validBooleanTrueForShowTitleBarPassesCheck() {
        val yaml = "widget:\n  show_title_bar: true"
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.WIDGET)
        assertTrue("Expected no error for show_title_bar=true", result.none { it.severity == YamlDiagnosticAnalyzer.Severity.ERROR })
    }

    // ── FLOAT_RANGE validation ───────────────────────────────────────────────

    @Test
    fun floatRangeViolationForFontSizeOver36ReturnsError() {
        val yaml = "terminal:\n  font_size: 100.0"
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.WIDGET)
        assertTrue("Expected ERROR for font_size out of range",
            result.any { it.severity == YamlDiagnosticAnalyzer.Severity.ERROR && "font_size" in it.message })
    }

    @Test
    fun floatRangeViolationForFontSizeBelow6ReturnsError() {
        val yaml = "terminal:\n  font_size: 3.0"
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.WIDGET)
        assertTrue("Expected ERROR for font_size below range",
            result.any { it.severity == YamlDiagnosticAnalyzer.Severity.ERROR && "font_size" in it.message })
    }

    @Test
    fun validFontSizePassesRangeCheck() {
        val yaml = "terminal:\n  font_size: 14"
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.WIDGET)
        assertTrue("Expected no error for font_size=14", result.none { it.severity == YamlDiagnosticAnalyzer.Severity.ERROR })
    }

    // ── Line numbers ─────────────────────────────────────────────────────────

    @Test
    fun diagnosticForTerminalKeyHasCorrectLineNumber() {
        val yaml = "terminal:\n  cursor_style: arrow"
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.WIDGET)
        assertTrue("Expected at least one diagnostic", result.isNotEmpty())
        // "  cursor_style:" is on line index 1 (0-based)
        assertTrue("Expected line 1 for cursor_style diagnostic",
            result.any { it.line == 1 && "cursor_style" in it.message })
    }

    @Test
    fun diagnosticForRootSectionHasCorrectLineNumber() {
        val yaml = "unknown_section:\n  key: value"
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.WIDGET)
        assertTrue("Expected at least one diagnostic", result.isNotEmpty())
        // "unknown_section:" is on line index 0
        assertTrue("Expected line 0 for root section diagnostic",
            result.any { it.line == 0 && "unknown_section" in it.message })
    }

    @Test
    fun diagnosticLineNumberIsZeroBasedIndex() {
        val yaml = "terminal:\n  cursor_blink: maybe"
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.WIDGET)
        assertTrue("Expected at least one diagnostic", result.isNotEmpty())
        // cursor_blink is on line index 1
        val diag = result.first { "cursor_blink" in it.message }
        assertEquals("Expected 0-based line index 1 for cursor_blink", 1, diag.line)
    }

    // ── Multiple errors ──────────────────────────────────────────────────────

    @Test
    fun multipleErrorsReportedInOneDocument() {
        val yaml = """
            terminal:
              color_scheme: invalid_scheme
              cursor_blink: maybe
              font_size: 100.0
        """.trimIndent()
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.WIDGET)
        assertTrue("Expected errors for invalid boolean and font size", result.size >= 2)
        assertTrue("All should be errors",
            result.all { it.severity == YamlDiagnosticAnalyzer.Severity.ERROR })
    }

    @Test
    fun mixedSectionsWithErrorsAndWarnings() {
        val yaml = """
            terminal:
              cursor_style: arrow
            unknown_section:
              key: value
        """.trimIndent()
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.WIDGET)
        assertTrue("Expected at least one ERROR", result.any { it.severity == YamlDiagnosticAnalyzer.Severity.ERROR })
        assertTrue("Expected at least one WARNING", result.any { it.severity == YamlDiagnosticAnalyzer.Severity.WARNING })
    }

    @Test
    fun unknownKeyAndInvalidValueInSameSection() {
        val yaml = """
            terminal:
              unknown_key: value
              cursor_blink: maybe
        """.trimIndent()
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.WIDGET)
        assertTrue("Expected WARNING for unknown key",
            result.any { it.severity == YamlDiagnosticAnalyzer.Severity.WARNING })
        assertTrue("Expected ERROR for invalid cursor_blink",
            result.any { it.severity == YamlDiagnosticAnalyzer.Severity.ERROR })
    }

    // ── Valid combinations pass ──────────────────────────────────────────────

    @Test
    fun validOpacityAndShowTitleBarAndOrientationPassCheck() {
        val yaml = """
            widget:
              opacity: 50
              show_title_bar: true
              orientation: "90"
        """.trimIndent()
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.WIDGET)
        assertTrue("Expected no diagnostics for valid widget values, got: $result", result.isEmpty())
    }

    @Test
    fun validTerminalConfigPassesAllChecks() {
        val yaml = """
            terminal:
              color_scheme: catppuccin
              cursor_blink: true
              cursor_style: block
              font_size: 14
              font_weight: normal
              background_opacity: 80
        """.trimIndent()
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.WIDGET)
        assertTrue("Expected no diagnostics for fully valid terminal config, got: $result", result.isEmpty())
    }

    @Test
    fun multipleValidSectionsPassAllChecks() {
        val yaml = """
            terminal:
              cursor_blink: false
              color_scheme: nord
            widget:
              opacity: 100
              show_title_bar: false
            session:
              session_name: my-session
        """.trimIndent()
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.WIDGET)
        assertTrue("Expected no diagnostics for multiple valid sections, got: $result", result.isEmpty())
    }

    // ── Edge cases ───────────────────────────────────────────────────────────

    @Test
    fun nullValueForKeyReturnsNoDiagnostics() {
        val yaml = "terminal:\n  cursor_blink:"
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.WIDGET)
        // null values are silently accepted
        assertTrue("Expected no errors for null value", result.none { it.severity == YamlDiagnosticAnalyzer.Severity.ERROR })
    }

    @Test
    fun nonMapRootReturnsNoDiagnostics() {
        val yaml = "just a string value"
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.WIDGET)
        assertTrue("Expected no diagnostics for non-map root", result.isEmpty())
    }

    @Test
    fun nonMapSectionValueSkipsValidation() {
        val yaml = "terminal: just_a_string"
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.WIDGET)
        // A non-map section value should not crash or produce spurious errors
        assertTrue("Expected no crash for non-map section value", true)
    }

    @Test
    fun intRangeBoundaryMinIsValid() {
        val yaml = "widget:\n  opacity: 0"
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.WIDGET)
        assertTrue("Expected no error for opacity=0 (boundary min)", result.none { it.severity == YamlDiagnosticAnalyzer.Severity.ERROR })
    }

    @Test
    fun intRangeBoundaryMaxIsValid() {
        val yaml = "widget:\n  opacity: 100"
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.WIDGET)
        assertTrue("Expected no error for opacity=100 (boundary max)", result.none { it.severity == YamlDiagnosticAnalyzer.Severity.ERROR })
    }

    @Test
    fun floatRangeBoundaryMinIsValid() {
        val yaml = "terminal:\n  font_size: 6"
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.WIDGET)
        assertTrue("Expected no error for font_size=6 (boundary min)", result.none { it.severity == YamlDiagnosticAnalyzer.Severity.ERROR })
    }

    @Test
    fun floatRangeBoundaryMaxIsValid() {
        val yaml = "terminal:\n  font_size: 36"
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.WIDGET)
        assertTrue("Expected no error for font_size=36 (boundary max)", result.none { it.severity == YamlDiagnosticAnalyzer.Severity.ERROR })
    }

    // ── SERVERS file type – valid YAML ──────────────────────────────────────

    @Test
    fun validServersYamlReturnsNoDiagnostics() {
        val yaml = """
            servers:
              - id: 1
                hostname: 192.168.1.100
                username: root
                auth_method: PASSWORD
                password: secret
                port: 22
        """.trimIndent()
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.SERVERS)
        assertTrue("Expected no diagnostics for valid servers YAML, got: $result", result.isEmpty())
    }

    @Test
    fun validServerWithEveryDirectFieldReturnsNoDiagnostics() {
        // Auth-profile fallbacks were removed; every server now declares
        // its own credentials directly. This test exercises a server that
        // sets all three credential-shaped fields side-by-side, which is
        // a valid (if unusual) configuration.
        val yaml = """
            servers:
              - id: 1
                hostname: example.com
                username: admin
                auth_method: KEY_WITH_PASSPHRASE
                password: secret
                private_key_data: "key-data"
                passphrase: "phrase"
        """.trimIndent()
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.SERVERS)
        assertTrue("Expected no diagnostics, got: $result", result.isEmpty())
    }

    @Test
    fun emptyServersListReturnsNoDiagnostics() {
        val yaml = "servers: []"
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.SERVERS)
        assertTrue("Expected no diagnostics for empty servers list", result.isEmpty())
    }

    // ── SERVERS – structure errors ──────────────────────────────────────────

    @Test
    fun serversMissingServersKeyReturnsWarning() {
        val yaml = "other_key:\n  hostname: test"
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.SERVERS)
        assertTrue("Expected warning for missing servers key",
            result.any { it.severity == YamlDiagnosticAnalyzer.Severity.WARNING && "other_key" in it.message })
    }

    @Test
    fun serversNotAListReturnsError() {
        val yaml = "servers:\n  hostname: test"
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.SERVERS)
        assertTrue("Expected error when servers is not a list",
            result.any { it.severity == YamlDiagnosticAnalyzer.Severity.ERROR && "list" in it.message })
    }

    @Test
    fun unknownRootKeyBesidesServersReturnsWarning() {
        val yaml = """
            servers:
              - id: 1
                hostname: test
                username: root
                auth_method: PASSWORD
            extra_key: value
        """.trimIndent()
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.SERVERS)
        assertTrue("Expected warning for unknown root key",
            result.any { it.severity == YamlDiagnosticAnalyzer.Severity.WARNING && "extra_key" in it.message })
    }

    // ── SERVERS – unknown keys ──────────────────────────────────────────────

    @Test
    fun unknownKeyInServerEntryReturnsWarning() {
        val yaml = """
            servers:
              - id: 1
                hostname: test
                username: root
                auth_method: PASSWORD
                bogus_field: value
        """.trimIndent()
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.SERVERS)
        assertTrue("Expected warning for unknown key in servers[]",
            result.any { it.severity == YamlDiagnosticAnalyzer.Severity.WARNING && "bogus_field" in it.message })
    }

    @Test
    fun unknownTopLevelKeyInServerEntryReturnsWarning() {
        val yaml = """
            servers:
              - id: 1
                hostname: test
                username: root
                auth_method: PASSWORD
                completely_unknown_field: value
        """.trimIndent()
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.SERVERS)
        assertTrue("Expected warning for unknown key",
            result.any { it.severity == YamlDiagnosticAnalyzer.Severity.WARNING && "completely_unknown_field" in it.message })
    }

    // ── SERVERS – enum validation ───────────────────────────────────────────

    @Test
    fun invalidAuthMethodEnumReturnsError() {
        val yaml = """
            servers:
              - id: 1
                hostname: test
                username: root
                auth_method: INVALID_METHOD
        """.trimIndent()
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.SERVERS)
        assertTrue("Expected error for invalid auth_method",
            result.any { it.severity == YamlDiagnosticAnalyzer.Severity.ERROR && "auth_method" in it.message })
    }

    @Test
    fun validAuthMethodEnumPassesCheck() {
        val yaml = """
            servers:
              - id: 1
                hostname: test
                username: root
                auth_method: KEY_WITH_PASSPHRASE
        """.trimIndent()
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.SERVERS)
        assertTrue("Expected no error for valid auth_method",
            result.none { it.severity == YamlDiagnosticAnalyzer.Severity.ERROR })
    }

    @Test
    fun invalidStrictHostKeyEnumReturnsError() {
        val yaml = """
            servers:
              - id: 1
                hostname: test
                username: root
                auth_method: PASSWORD
                strict_host_key: invalid
        """.trimIndent()
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.SERVERS)
        assertTrue("Expected error for invalid strict_host_key",
            result.any { it.severity == YamlDiagnosticAnalyzer.Severity.ERROR && "strict_host_key" in it.message })
    }

    // ── SERVERS – range validation ──────────────────────────────────────────

    @Test
    fun portOutOfRangeReturnsError() {
        val yaml = """
            servers:
              - id: 1
                hostname: test
                username: root
                auth_method: PASSWORD
                port: 99999
        """.trimIndent()
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.SERVERS)
        assertTrue("Expected error for port out of range",
            result.any { it.severity == YamlDiagnosticAnalyzer.Severity.ERROR && "port" in it.message })
    }

    @Test
    fun validPortPassesCheck() {
        val yaml = """
            servers:
              - id: 1
                hostname: test
                username: root
                auth_method: PASSWORD
                port: 2222
        """.trimIndent()
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.SERVERS)
        assertTrue("Expected no error for valid port",
            result.none { it.severity == YamlDiagnosticAnalyzer.Severity.ERROR })
    }

    @Test
    fun keepAliveIntervalOutOfRangeReturnsError() {
        val yaml = """
            servers:
              - id: 1
                hostname: test
                username: root
                auth_method: PASSWORD
                keep_alive_interval: 9999
        """.trimIndent()
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.SERVERS)
        assertTrue("Expected error for keep_alive_interval out of range",
            result.any { it.severity == YamlDiagnosticAnalyzer.Severity.ERROR && "keep_alive_interval" in it.message })
    }

    @Test
    fun connectionTimeoutOutOfRangeReturnsError() {
        val yaml = """
            servers:
              - id: 1
                hostname: test
                username: root
                auth_method: PASSWORD
                connection_timeout: 500
        """.trimIndent()
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.SERVERS)
        assertTrue("Expected error for connection_timeout out of range",
            result.any { it.severity == YamlDiagnosticAnalyzer.Severity.ERROR && "connection_timeout" in it.message })
    }

    // ── SERVERS – boolean validation ────────────────────────────────────────

    @Test
    fun invalidBooleanForCompressionReturnsError() {
        val yaml = """
            servers:
              - id: 1
                hostname: test
                username: root
                auth_method: PASSWORD
                compression: maybe
        """.trimIndent()
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.SERVERS)
        assertTrue("Expected error for invalid compression value",
            result.any { it.severity == YamlDiagnosticAnalyzer.Severity.ERROR && "compression" in it.message })
    }

    @Test
    fun validBooleanForIsEnabledPassesCheck() {
        val yaml = """
            servers:
              - id: 1
                hostname: test
                username: root
                auth_method: PASSWORD
                is_enabled: false
        """.trimIndent()
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.SERVERS)
        assertTrue("Expected no error for is_enabled=false",
            result.none { it.severity == YamlDiagnosticAnalyzer.Severity.ERROR })
    }

    // ── SERVERS – multiple errors ───────────────────────────────────────────

    @Test
    fun multipleServerErrorsReported() {
        val yaml = """
            servers:
              - id: 1
                hostname: test
                username: root
                auth_method: INVALID
                port: 99999
                compression: maybe
        """.trimIndent()
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.SERVERS)
        assertTrue("Expected at least 3 errors", result.count { it.severity == YamlDiagnosticAnalyzer.Severity.ERROR } >= 3)
    }

    @Test
    fun multipleServerEntriesValidatedIndependently() {
        val yaml = """
            servers:
              - id: 1
                hostname: good.com
                username: root
                auth_method: PASSWORD
              - id: 2
                hostname: bad.com
                username: admin
                auth_method: INVALID
        """.trimIndent()
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.SERVERS)
        assertTrue("Expected error only for second server's auth_method",
            result.any { it.severity == YamlDiagnosticAnalyzer.Severity.ERROR && "auth_method" in it.message })
        assertEquals("Expected exactly one error", 1, result.count { it.severity == YamlDiagnosticAnalyzer.Severity.ERROR })
    }

    // ── SERVERS – full valid config ─────────────────────────────────────────

    @Test
    fun fullyPopulatedValidServerPassesAllChecks() {
        val yaml = """
            servers:
              - id: 1
                hostname: 192.168.1.100
                username: root
                name: "My Server"
                auth_method: PASSWORD
                port: 2222
                password: secret
                color: "#608D8B"
                is_enabled: true
                sort_order: 0
                compression: true
                keep_alive_interval: 60
                connection_timeout: 30
                transport_timeout: 30
                read_timeout: 30
                keepalive_max_count: 3
                strict_host_key: ask
                term_type: xterm-256color
        """.trimIndent()
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.SERVERS)
        assertTrue("Expected no diagnostics for fully valid server, got: $result", result.isEmpty())
    }

    // =======================================================================
    // SNIPPETS payload — top-level libraries[] + nested snippets[] list
    // =======================================================================

    @Test fun validSnippetsYamlReturnsNoDiagnostics() {
        val yaml = """
            libraries:
              - id: 1
                name: tmux Sessions
                is_enabled: true
                sort_order: 10
                snippets:
                  - id: 1
                    name: tmux ls
                    command: tmux ls
                    is_enabled: true
        """.trimIndent()
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.SNIPPETS)
        assertTrue("Expected no diagnostics, got: $result", result.isEmpty())
    }

    @Test fun snippetsMissingLibrariesKeyReturnsWarning() {
        val yaml = """
            other_key: value
        """.trimIndent()
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.SNIPPETS)
        assertTrue(
            "Expected warning for unknown root key, got: $result",
            result.any { it.severity == YamlDiagnosticAnalyzer.Severity.WARNING && "other_key" in it.message }
        )
    }

    @Test fun librariesNotAListReturnsError() {
        val yaml = """
            libraries:
              id: 1
        """.trimIndent()
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.SNIPPETS)
        assertTrue(
            "Expected error when libraries is not a list, got: $result",
            result.any { it.severity == YamlDiagnosticAnalyzer.Severity.ERROR && "list" in it.message.lowercase() }
        )
    }

    @Test fun unknownKeyInLibraryReturnsWarning() {
        val yaml = """
            libraries:
              - id: 1
                name: Test
                bogus_field: value
        """.trimIndent()
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.SNIPPETS)
        assertTrue(
            "Expected warning for unknown library field, got: $result",
            result.any { it.severity == YamlDiagnosticAnalyzer.Severity.WARNING && "bogus_field" in it.message }
        )
    }

    @Test fun invalidBooleanInLibraryReturnsError() {
        val yaml = """
            libraries:
              - id: 1
                name: Test
                is_enabled: maybe
        """.trimIndent()
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.SNIPPETS)
        assertTrue(
            "Expected error for invalid is_enabled, got: $result",
            result.any { it.severity == YamlDiagnosticAnalyzer.Severity.ERROR }
        )
    }

    @Test fun multipleLibrariesValidatedIndependently() {
        val yaml = """
            libraries:
              - id: 1
                name: Good Library
                is_enabled: true
              - id: 2
                name: Bad Library
                is_enabled: invalid
        """.trimIndent()
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.SNIPPETS)
        val errors = result.filter { it.severity == YamlDiagnosticAnalyzer.Severity.ERROR }
        assertEquals("Expected exactly one error, got: $result", 1, errors.size)
    }

    @Test fun unknownKeyInSnippetReturnsWarning() {
        val yaml = """
            libraries:
              - id: 1
                name: Test
                snippets:
                  - id: 1
                    name: Snippet
                    command: echo test
                    bad_field: value
        """.trimIndent()
        val result = YamlDiagnosticAnalyzer.analyze(yaml, fileType = YamlFileType.SNIPPETS)
        assertTrue(
            "Expected warning for unknown snippet field, got: $result",
            result.any { it.severity == YamlDiagnosticAnalyzer.Severity.WARNING && "bad_field" in it.message }
        )
    }
}

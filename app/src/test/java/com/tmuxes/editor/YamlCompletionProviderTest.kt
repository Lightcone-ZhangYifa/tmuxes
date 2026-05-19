package com.tmuxes.editor

import org.junit.Assert.*
import org.junit.Test

class YamlCompletionProviderTest {

    // -----------------------------------------------------------------------
    // parseContext tests
    // -----------------------------------------------------------------------

    @Test
    fun `parseContext - root level empty cursor returns empty section and empty prefix`() {
        val lines = listOf("session:")
        val ctx = YamlCompletionProvider.parseContext(lines, 0, 0)
        assertEquals("", ctx.sectionPath)
        assertEquals("", ctx.keyPrefix)
        assertFalse(ctx.isValuePosition)
    }

    @Test
    fun `parseContext - root level partial key returns prefix`() {
        val lines = listOf("ses")
        val ctx = YamlCompletionProvider.parseContext(lines, 0, 3)
        assertEquals("", ctx.sectionPath)
        assertEquals("ses", ctx.keyPrefix)
        assertFalse(ctx.isValuePosition)
    }

    @Test
    fun `parseContext - inside section returns sectionPath`() {
        val lines = listOf(
            "terminal:",
            "  font"
        )
        val ctx = YamlCompletionProvider.parseContext(lines, 1, 6)
        assertEquals("terminal", ctx.sectionPath)
        assertEquals("font", ctx.keyPrefix)
        assertFalse(ctx.isValuePosition)
    }

    @Test
    fun `parseContext - inside section with empty key prefix`() {
        val lines = listOf(
            "terminal:",
            "  "
        )
        val ctx = YamlCompletionProvider.parseContext(lines, 1, 2)
        assertEquals("terminal", ctx.sectionPath)
        assertEquals("", ctx.keyPrefix)
        assertFalse(ctx.isValuePosition)
    }

    @Test
    fun `parseContext - in value position after colon with prefix`() {
        val lines = listOf(
            "terminal:",
            "  color_scheme: dra"
        )
        val ctx = YamlCompletionProvider.parseContext(lines, 1, 19)
        assertEquals("terminal", ctx.sectionPath)
        assertEquals("color_scheme", ctx.currentKey)
        assertEquals("dra", ctx.valuePrefix)
        assertTrue(ctx.isValuePosition)
    }

    @Test
    fun `parseContext - in empty value position after colon and space`() {
        val lines = listOf(
            "terminal:",
            "  cursor_blink: "
        )
        val ctx = YamlCompletionProvider.parseContext(lines, 1, 17)
        assertEquals("terminal", ctx.sectionPath)
        assertEquals("cursor_blink", ctx.currentKey)
        assertEquals("", ctx.valuePrefix)
        assertTrue(ctx.isValuePosition)
    }

    @Test
    fun `parseContext - root value position`() {
        val lines = listOf("session: my")
        val ctx = YamlCompletionProvider.parseContext(lines, 0, 11)
        assertEquals("", ctx.sectionPath)
        assertEquals("session", ctx.currentKey)
        assertEquals("my", ctx.valuePrefix)
        assertTrue(ctx.isValuePosition)
    }

    @Test
    fun `parseContext - cursor line out of range returns empty context`() {
        val lines = listOf("terminal:")
        val ctx = YamlCompletionProvider.parseContext(lines, 5, 0)
        assertEquals(YamlCompletionProvider.CompletionContext(), ctx)
    }

    @Test
    fun `parseContext - negative cursor line returns empty context`() {
        val lines = listOf("terminal:")
        val ctx = YamlCompletionProvider.parseContext(lines, -1, 0)
        assertEquals(YamlCompletionProvider.CompletionContext(), ctx)
    }

    // -----------------------------------------------------------------------
    // getCompletions - key completions
    // -----------------------------------------------------------------------

    @Test
    fun `getCompletions - root keys include session terminal widget`() {
        val completions = YamlCompletionProvider.getCompletions(
            sectionPath = "",
            keyOrPrefix = "",
            isValue = false,
            fileType = YamlFileType.WIDGET
        )
        val labels = completions.map { it.label }
        assertTrue("session missing", "session" in labels)
        assertTrue("terminal missing", "terminal" in labels)
        assertTrue("widget missing", "widget" in labels)
    }

    @Test
    fun `getCompletions - root key insert text uses section suffix with newline`() {
        val completions = YamlCompletionProvider.getCompletions(
            sectionPath = "",
            keyOrPrefix = "terminal",
            isValue = false,
            fileType = YamlFileType.WIDGET
        )
        val entry = completions.find { it.label == "terminal" }
        assertNotNull(entry)
        assertEquals("terminal:\n  ", entry!!.insertText)
    }

    @Test
    fun `getCompletions - terminal keys filtered by prefix font returns font_family font_size font_weight`() {
        val completions = YamlCompletionProvider.getCompletions(
            sectionPath = "terminal",
            keyOrPrefix = "font",
            isValue = false,
            fileType = YamlFileType.WIDGET
        )
        val labels = completions.map { it.label }
        assertTrue("font_family missing", "font_family" in labels)
        assertTrue("font_size missing", "font_size" in labels)
        assertTrue("font_weight missing", "font_weight" in labels)
        assertTrue("only font-prefixed keys expected", labels.all { it.startsWith("font") })
    }

    @Test
    fun `getCompletions - terminal key insert text uses value suffix`() {
        val completions = YamlCompletionProvider.getCompletions(
            sectionPath = "terminal",
            keyOrPrefix = "cursor_blink",
            isValue = false,
            fileType = YamlFileType.WIDGET
        )
        val entry = completions.find { it.label == "cursor_blink" }
        assertNotNull(entry)
        assertEquals("cursor_blink: ", entry!!.insertText)
    }

    @Test
    fun `getCompletions - excludes existing keys`() {
        val completions = YamlCompletionProvider.getCompletions(
            sectionPath = "terminal",
            keyOrPrefix = "font",
            isValue = false,
            existingKeys = setOf("font_family", "font_weight"),
            fileType = YamlFileType.WIDGET
        )
        val labels = completions.map { it.label }
        assertFalse("font_family should be excluded", "font_family" in labels)
        assertFalse("font_weight should be excluded", "font_weight" in labels)
        assertTrue("font_size should be included", "font_size" in labels)
    }

    @Test
    fun `getCompletions - empty prefix returns all keys for section`() {
        val completions = YamlCompletionProvider.getCompletions(
            sectionPath = "terminal",
            keyOrPrefix = "",
            isValue = false,
            fileType = YamlFileType.WIDGET
        )
        val labels = completions.map { it.label }
        assertTrue(labels.contains("font_family"))
        assertTrue(labels.contains("color_scheme"))
        assertTrue(labels.contains("cursor_blink"))
    }

    @Test
    fun `getCompletions - unknown section returns empty list`() {
        val completions = YamlCompletionProvider.getCompletions(
            sectionPath = "nonexistent",
            keyOrPrefix = "",
            isValue = false,
            fileType = YamlFileType.WIDGET
        )
        assertTrue(completions.isEmpty())
    }

    @Test
    fun `getCompletions - prefix with no matches returns empty list`() {
        val completions = YamlCompletionProvider.getCompletions(
            sectionPath = "terminal",
            keyOrPrefix = "zzz",
            isValue = false,
            fileType = YamlFileType.WIDGET
        )
        assertTrue(completions.isEmpty())
    }

    // -----------------------------------------------------------------------
    // getCompletions - value completions: enum
    // -----------------------------------------------------------------------

    @Test
    fun `getCompletions - enum value completion for color_scheme with prefix dra returns dracula`() {
        val completions = YamlCompletionProvider.getCompletions(
            sectionPath = "terminal",
            keyOrPrefix = "color_scheme",
            isValue = true,
            valuePrefix = "dra",
            fileType = YamlFileType.WIDGET
        )
        val labels = completions.map { it.label }
        assertEquals(listOf("dracula"), labels)
    }

    @Test
    fun `getCompletions - enum value completion for color_scheme with empty prefix returns all values`() {
        val completions = YamlCompletionProvider.getCompletions(
            sectionPath = "terminal",
            keyOrPrefix = "color_scheme",
            isValue = true,
            valuePrefix = "",
            fileType = YamlFileType.WIDGET
        )
        val labels = completions.map { it.label }
        assertTrue(labels.contains("dracula"))
        assertTrue(labels.contains("gruvbox"))
        assertTrue(labels.contains("nord"))
        assertTrue(labels.contains("catppuccin"))
    }

    @Test
    fun `getCompletions - enum value completion prefix no match returns empty`() {
        val completions = YamlCompletionProvider.getCompletions(
            sectionPath = "terminal",
            keyOrPrefix = "color_scheme",
            isValue = true,
            valuePrefix = "zzz",
            fileType = YamlFileType.WIDGET
        )
        assertTrue(completions.isEmpty())
    }

    @Test
    fun `getCompletions - enum value completion for cursor_style`() {
        val completions = YamlCompletionProvider.getCompletions(
            sectionPath = "terminal",
            keyOrPrefix = "cursor_style",
            isValue = true,
            valuePrefix = "",
            fileType = YamlFileType.WIDGET
        )
        val labels = completions.map { it.label }
        assertTrue(labels.contains("block"))
        assertTrue(labels.contains("underline"))
        assertTrue(labels.contains("bar"))
    }

    @Test
    fun `getCompletions - enum value completion for font_weight with prefix b`() {
        val completions = YamlCompletionProvider.getCompletions(
            sectionPath = "terminal",
            keyOrPrefix = "font_weight",
            isValue = true,
            valuePrefix = "b",
            fileType = YamlFileType.WIDGET
        )
        val labels = completions.map { it.label }
        assertTrue(labels.contains("bold"))
        assertFalse(labels.contains("thin"))
        assertFalse(labels.contains("normal"))
    }

    // -----------------------------------------------------------------------
    // getCompletions - value completions: boolean
    // -----------------------------------------------------------------------

    @Test
    fun `getCompletions - boolean value completion for cursor_blink returns true and false`() {
        val completions = YamlCompletionProvider.getCompletions(
            sectionPath = "terminal",
            keyOrPrefix = "cursor_blink",
            isValue = true,
            valuePrefix = "",
            fileType = YamlFileType.WIDGET
        )
        val labels = completions.map { it.label }
        assertTrue("true missing", "true" in labels)
        assertTrue("false missing", "false" in labels)
        assertEquals(2, labels.size)
    }

    @Test
    fun `getCompletions - boolean value completion with prefix t returns only true`() {
        val completions = YamlCompletionProvider.getCompletions(
            sectionPath = "terminal",
            keyOrPrefix = "cursor_blink",
            isValue = true,
            valuePrefix = "t",
            fileType = YamlFileType.WIDGET
        )
        val labels = completions.map { it.label }
        assertEquals(listOf("true"), labels)
    }

    @Test
    fun `getCompletions - boolean value completion with prefix f returns only false`() {
        val completions = YamlCompletionProvider.getCompletions(
            sectionPath = "terminal",
            keyOrPrefix = "cursor_blink",
            isValue = true,
            valuePrefix = "f",
            fileType = YamlFileType.WIDGET
        )
        val labels = completions.map { it.label }
        assertEquals(listOf("false"), labels)
    }

    @Test
    fun `getCompletions - boolean value completion detail is Boolean`() {
        val completions = YamlCompletionProvider.getCompletions(
            sectionPath = "terminal",
            keyOrPrefix = "cursor_blink",
            isValue = true,
            valuePrefix = "",
            fileType = YamlFileType.WIDGET
        )
        completions.forEach { assertTrue(it.detail.startsWith("Boolean")) }
    }

    // -----------------------------------------------------------------------
    // getCompletions - value completions: range types are not enumerable
    // -----------------------------------------------------------------------
    //
    // Numeric ranges (Int / Float / IntRange) have no enumerable completion
    // set — the user is expected to type a number directly. The previous
    // schema synthesized a single "min-value as label, range hint as detail"
    // sentinel; the registry-derived schema drops that since it conveyed no
    // information the type-hint sidebar isn't already showing.

    @Test
    fun `getCompletions - widget opacity range returns no completions`() {
        val completions = YamlCompletionProvider.getCompletions(
            sectionPath = "widget",
            keyOrPrefix = "opacity",
            isValue = true,
            valuePrefix = "",
            fileType = YamlFileType.WIDGET
        )
        assertTrue("range types should not produce value completions", completions.isEmpty())
    }

    @Test
    fun `getCompletions - terminal font_size range returns no completions`() {
        val completions = YamlCompletionProvider.getCompletions(
            sectionPath = "terminal",
            keyOrPrefix = "font_size",
            isValue = true,
            valuePrefix = "",
            fileType = YamlFileType.WIDGET
        )
        assertTrue("range types should not produce value completions", completions.isEmpty())
    }

    // -----------------------------------------------------------------------
    // getCompletions - value completions: non-completable types return empty
    // -----------------------------------------------------------------------

    @Test
    fun `getCompletions - int type (scrollback_lines) returns empty completions`() {
        val completions = YamlCompletionProvider.getCompletions(
            sectionPath = "terminal",
            keyOrPrefix = "scrollback_lines",
            isValue = true,
            valuePrefix = "",
            fileType = YamlFileType.WIDGET
        )
        assertTrue(completions.isEmpty())
    }

    @Test
    fun `getCompletions - value for unknown key returns empty`() {
        val completions = YamlCompletionProvider.getCompletions(
            sectionPath = "terminal",
            keyOrPrefix = "nonexistent_key",
            isValue = true,
            valuePrefix = "",
            fileType = YamlFileType.WIDGET
        )
        assertTrue(completions.isEmpty())
    }

    // -----------------------------------------------------------------------
    // Integration: parseContext + getCompletions
    // -----------------------------------------------------------------------

    @Test
    fun `integration - parse context at start then get root key completions`() {
        val lines = listOf("")
        val ctx = YamlCompletionProvider.parseContext(lines, 0, 0)
        assertFalse(ctx.isValuePosition)
        assertEquals("", ctx.sectionPath)

        val completions = YamlCompletionProvider.getCompletions(
            sectionPath = ctx.sectionPath,
            keyOrPrefix = ctx.keyPrefix,
            isValue = ctx.isValuePosition,
            fileType = YamlFileType.WIDGET
        )
        val labels = completions.map { it.label }
        assertTrue(labels.contains("terminal"))
        assertTrue(labels.contains("session"))
        assertTrue(labels.contains("widget"))
    }

    @Test
    fun `integration - parse context inside section then get value completions`() {
        val lines = listOf(
            "terminal:",
            "  color_scheme: dra"
        )
        val ctx = YamlCompletionProvider.parseContext(lines, 1, 19)
        assertTrue(ctx.isValuePosition)
        assertEquals("terminal", ctx.sectionPath)
        assertEquals("color_scheme", ctx.currentKey)
        assertEquals("dra", ctx.valuePrefix)

        val completions = YamlCompletionProvider.getCompletions(
            sectionPath = ctx.sectionPath,
            keyOrPrefix = ctx.currentKey,
            isValue = ctx.isValuePosition,
            valuePrefix = ctx.valuePrefix,
            fileType = YamlFileType.WIDGET
        )
        assertEquals(listOf("dracula"), completions.map { it.label })
    }

    @Test
    fun `integration - parse key prefix inside section then get key completions`() {
        val lines = listOf(
            "terminal:",
            "  cursor"
        )
        val ctx = YamlCompletionProvider.parseContext(lines, 1, 8)
        assertFalse(ctx.isValuePosition)
        assertEquals("terminal", ctx.sectionPath)
        assertEquals("cursor", ctx.keyPrefix)

        val completions = YamlCompletionProvider.getCompletions(
            sectionPath = ctx.sectionPath,
            keyOrPrefix = ctx.keyPrefix,
            isValue = ctx.isValuePosition,
            fileType = YamlFileType.WIDGET
        )
        val labels = completions.map { it.label }
        assertTrue(labels.all { it.startsWith("cursor") })
        assertTrue(labels.contains("cursor_blink"))
        assertTrue(labels.contains("cursor_style"))
    }
}

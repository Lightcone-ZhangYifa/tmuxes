package com.tmuxes.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DocCommandsTest {

    private fun snap(vararg lines: String) = ListDocSnapshot(lines.toList())
    private fun caret(line: Int, col: Int) = Selection.caret(line, col)
    private fun sel(sl: Int, sc: Int, el: Int, ec: Int) = Selection(sl, sc, el, ec)

    @Test fun `DeleteLines no-sel middle line`() {
        assertCommand(
            snap("a", "b", "c"),
            caret(1, 0),
            DocCommands.DeleteLines,
            patches = listOf(DocPatch(1, 0, 2, 0, "")),
            lines = listOf("a", "c"),
            expectedSelection = caret(1, 0),
        )
    }

    @Test fun `DeleteLines no-sel last line of multi-line`() {
        assertCommand(
            snap("a", "b", "c"),
            caret(2, 0),
            DocCommands.DeleteLines,
            patches = listOf(DocPatch(1, 1, 2, 1, "")),
            lines = listOf("a", "b"),
            expectedSelection = caret(1, 1),
        )
    }

    @Test fun `DeleteLines single-line doc preserves empty line`() {
        assertCommand(
            snap("hello"),
            caret(0, 2),
            DocCommands.DeleteLines,
            patches = listOf(DocPatch(0, 0, 0, 5, "")),
            lines = listOf(""),
            expectedSelection = caret(0, 0),
        )
    }

    @Test fun `DeleteLines multi-line selection middle`() {
        assertCommand(
            snap("a", "b", "c", "d"),
            sel(1, 0, 2, 1),
            DocCommands.DeleteLines,
            patches = listOf(DocPatch(1, 0, 3, 0, "")),
            lines = listOf("a", "d"),
            expectedSelection = caret(1, 0),
        )
    }

    @Test fun `DeleteLines selection ending at col 0 excludes that line`() {
        assertCommand(
            snap("a", "b", "c", "d"),
            sel(1, 0, 2, 0),
            DocCommands.DeleteLines,
            patches = listOf(DocPatch(1, 0, 2, 0, "")),
            lines = listOf("a", "c", "d"),
            expectedSelection = caret(1, 0),
        )
    }

    @Test fun `DeleteLines covers entire document`() {
        assertCommand(
            snap("a", "b", "c"),
            sel(0, 0, 2, 1),
            DocCommands.DeleteLines,
            patches = listOf(DocPatch(0, 0, 2, 1, "")),
            lines = listOf(""),
            expectedSelection = caret(0, 0),
        )
    }

    @Test fun `MoveLinesUp no-sel middle line uses delete then insert`() {
        assertCommand(
            snap("a", "b", "c"),
            caret(1, 0),
            DocCommands.MoveLinesUp,
            patches = listOf(
                DocPatch(0, 0, 1, 0, ""),
                DocPatch(1, 0, 1, 0, "a\n"),
            ),
            lines = listOf("b", "a", "c"),
            expectedSelection = caret(0, 0),
        )
    }

    @Test fun `MoveLinesUp first line is no-op`() {
        assertNull(DocCommands.MoveLinesUp.compute(snap("a", "b"), caret(0, 1)))
    }

    @Test fun `MoveLinesUp last line appends above after block`() {
        assertCommand(
            snap("a", "b", "c"),
            caret(2, 1),
            DocCommands.MoveLinesUp,
            patches = listOf(
                DocPatch(1, 0, 2, 0, ""),
                DocPatch(1, 1, 1, 1, "\nb"),
            ),
            lines = listOf("a", "c", "b"),
            expectedSelection = caret(2, 1),
        )
    }

    @Test fun `MoveLinesUp multi-line selection follows block up`() {
        assertCommand(
            snap("a", "b", "c", "d", "e"),
            sel(2, 0, 3, 1),
            DocCommands.MoveLinesUp,
            patches = listOf(
                DocPatch(1, 0, 2, 0, ""),
                DocPatch(3, 0, 3, 0, "b\n"),
            ),
            lines = listOf("a", "c", "d", "b", "e"),
            expectedSelection = sel(1, 0, 2, 1),
        )
    }

    @Test fun `MoveLinesDown no-sel middle line uses delete then insert`() {
        assertCommand(
            snap("a", "b", "c", "d"),
            caret(1, 1),
            DocCommands.MoveLinesDown,
            patches = listOf(
                DocPatch(2, 0, 3, 0, ""),
                DocPatch(1, 0, 1, 0, "c\n"),
            ),
            lines = listOf("a", "c", "b", "d"),
            expectedSelection = caret(2, 1),
        )
    }

    @Test fun `MoveLinesDown last line is no-op`() {
        assertNull(DocCommands.MoveLinesDown.compute(snap("a", "b"), caret(1, 0)))
    }

    @Test fun `MoveLinesDown second-to-last line deletes below at EOF`() {
        assertCommand(
            snap("a", "b", "c"),
            caret(1, 1),
            DocCommands.MoveLinesDown,
            patches = listOf(
                DocPatch(1, 1, 2, 1, ""),
                DocPatch(1, 0, 1, 0, "c\n"),
            ),
            lines = listOf("a", "c", "b"),
            expectedSelection = caret(2, 1),
        )
    }

    @Test fun `MoveLinesDown multi-line selection follows block down`() {
        assertCommand(
            snap("a", "b", "c", "d", "e"),
            sel(1, 0, 2, 1),
            DocCommands.MoveLinesDown,
            patches = listOf(
                DocPatch(3, 0, 4, 0, ""),
                DocPatch(1, 0, 1, 0, "d\n"),
            ),
            lines = listOf("a", "d", "b", "c", "e"),
            expectedSelection = sel(2, 0, 3, 1),
        )
    }

    @Test fun `ToggleLineComment no-sel adds marker at indent`() {
        assertCommand(
            snap("  foo"),
            caret(0, 2),
            DocCommands.ToggleLineComment,
            patches = listOf(DocPatch(0, 2, 0, 2, "# ")),
            lines = listOf("  # foo"),
            expectedSelection = caret(0, 4),
        )
    }

    @Test fun `ToggleLineComment no-sel removes marker at indent`() {
        assertCommand(
            snap("  # foo"),
            caret(0, 5),
            DocCommands.ToggleLineComment,
            patches = listOf(DocPatch(0, 2, 0, 4, "")),
            lines = listOf("  foo"),
            expectedSelection = caret(0, 3),
        )
    }

    @Test fun `ToggleLineComment partial commented comments only uncommented lines`() {
        assertCommand(
            snap("# a", "b", "# c"),
            sel(0, 0, 2, 3),
            DocCommands.ToggleLineComment,
            patches = listOf(DocPatch(1, 0, 1, 0, "# ")),
            lines = listOf("# a", "# b", "# c"),
            expectedSelection = sel(0, 0, 2, 3),
        )
    }

    @Test fun `ToggleLineComment all commented uncomments all lines`() {
        assertCommand(
            snap("# a", "  # b", "# c"),
            sel(0, 0, 2, 3),
            DocCommands.ToggleLineComment,
            patches = listOf(
                DocPatch(0, 0, 0, 2, ""),
                DocPatch(1, 2, 1, 4, ""),
                DocPatch(2, 0, 2, 2, ""),
            ),
            lines = listOf("a", "  b", "c"),
            expectedSelection = sel(0, 0, 2, 1),
        )
    }

    @Test fun `ToggleLineComment empty line adds marker`() {
        assertCommand(
            snap("", "foo", ""),
            sel(0, 0, 2, 0),
            DocCommands.ToggleLineComment,
            patches = listOf(
                DocPatch(0, 0, 0, 0, "# "),
                DocPatch(1, 0, 1, 0, "# "),
            ),
            lines = listOf("# ", "# foo", ""),
            expectedSelection = sel(0, 2, 2, 0),
        )
    }

    @Test fun `indent no-sel inserts spaces at line start and caret follows`() {
        assertCommand(
            snap("abc"),
            caret(0, 1),
            DocCommands.indent(2),
            patches = listOf(DocPatch(0, 0, 0, 0, "  ")),
            lines = listOf("  abc"),
            expectedSelection = caret(0, 3),
        )
    }

    @Test fun `indent multi-line selection emits one insert per line`() {
        assertCommand(
            snap("a", "b", "c"),
            sel(0, 0, 2, 1),
            DocCommands.indent(2),
            patches = listOf(
                DocPatch(0, 0, 0, 0, "  "),
                DocPatch(1, 0, 1, 0, "  "),
                DocPatch(2, 0, 2, 0, "  "),
            ),
            lines = listOf("  a", "  b", "  c"),
            expectedSelection = sel(0, 2, 2, 3),
        )
    }

    @Test fun `outdent removes up to tabWidth leading spaces`() {
        assertCommand(
            snap("    abc"),
            caret(0, 5),
            DocCommands.outdent(2),
            patches = listOf(DocPatch(0, 0, 0, 2, "")),
            lines = listOf("  abc"),
            expectedSelection = caret(0, 3),
        )
    }

    @Test fun `outdent removes only available leading spaces`() {
        assertCommand(
            snap(" abc"),
            caret(0, 2),
            DocCommands.outdent(2),
            patches = listOf(DocPatch(0, 0, 0, 1, "")),
            lines = listOf("abc"),
            expectedSelection = caret(0, 1),
        )
    }

    @Test fun `outdent no leading spaces is no-op`() {
        assertNull(DocCommands.outdent(2).compute(snap("abc"), caret(0, 1)))
    }

    @Test fun `outdent multi-line skips lines without spaces`() {
        assertCommand(
            snap("    a", "  b", "c"),
            sel(0, 0, 2, 1),
            DocCommands.outdent(2),
            patches = listOf(
                DocPatch(0, 0, 0, 2, ""),
                DocPatch(1, 0, 1, 2, ""),
            ),
            lines = listOf("  a", "b", "c"),
            expectedSelection = sel(0, 0, 2, 1),
        )
    }

    @Test fun `Duplicate no-sel middle line inserts copy above current line`() {
        assertCommand(
            snap("a", "b", "c"),
            caret(1, 0),
            DocCommands.Duplicate,
            patches = listOf(DocPatch(1, 0, 1, 0, "b\n")),
            lines = listOf("a", "b", "b", "c"),
            expectedSelection = caret(2, 0),
        )
    }

    @Test fun `Duplicate no-sel last line`() {
        assertCommand(
            snap("a", "b", "c"),
            caret(2, 1),
            DocCommands.Duplicate,
            patches = listOf(DocPatch(2, 0, 2, 0, "c\n")),
            lines = listOf("a", "b", "c", "c"),
            expectedSelection = caret(3, 1),
        )
    }

    @Test fun `Duplicate multi-line selection follows original block down`() {
        assertCommand(
            snap("a", "b", "c", "d"),
            sel(1, 0, 2, 1),
            DocCommands.Duplicate,
            patches = listOf(DocPatch(1, 0, 1, 0, "b\nc\n")),
            lines = listOf("a", "b", "c", "b", "c", "d"),
            expectedSelection = sel(3, 0, 4, 1),
        )
    }

    @Test fun `smartTab no-sel inserts spaces at caret`() {
        assertCommand(
            snap("abcd"),
            caret(0, 2),
            DocCommands.smartTab(2),
            patches = listOf(DocPatch(0, 2, 0, 2, "  ")),
            lines = listOf("ab  cd"),
            expectedSelection = caret(0, 4),
        )
    }

    @Test fun `smartTab single-line selection replaces with spaces`() {
        assertCommand(
            snap("abcd"),
            sel(0, 1, 0, 3),
            DocCommands.smartTab(2),
            patches = listOf(DocPatch(0, 1, 0, 3, "  ")),
            lines = listOf("a  d"),
            expectedSelection = caret(0, 3),
        )
    }

    @Test fun `smartTab multi-line selection delegates to indent`() {
        assertCommand(
            snap("a", "b", "c"),
            sel(0, 0, 2, 1),
            DocCommands.smartTab(2),
            patches = listOf(
                DocPatch(0, 0, 0, 0, "  "),
                DocPatch(1, 0, 1, 0, "  "),
                DocPatch(2, 0, 2, 0, "  "),
            ),
            lines = listOf("  a", "  b", "  c"),
            expectedSelection = sel(0, 2, 2, 3),
        )
    }

    @Test fun `pasteLineAbove appends newline when missing`() {
        assertCommand(
            snap("a", "b"),
            caret(1, 0),
            DocCommands.pasteLineAbove("x"),
            patches = listOf(DocPatch(1, 0, 1, 0, "x\n")),
            lines = listOf("a", "x", "b"),
            expectedSelection = caret(2, 0),
        )
    }

    @Test fun `pasteLineAbove preserves existing trailing newline`() {
        assertCommand(
            snap("a", "b"),
            caret(0, 1),
            DocCommands.pasteLineAbove("x\ny\n"),
            patches = listOf(DocPatch(0, 0, 0, 0, "x\ny\n")),
            lines = listOf("x", "y", "a", "b"),
            expectedSelection = caret(2, 1),
        )
    }

    @Test fun `Selection lastLine excludes trailing line at col 0`() {
        assertEquals(2, Selection(1, 0, 3, 0).lastLine)
        assertEquals(3, Selection(1, 0, 3, 1).lastLine)
        assertEquals(1, Selection(1, 0, 1, 5).lastLine)
        assertEquals(0, Selection(0, 0, 0, 0).lastLine)
    }

    @Test fun `Selection isEmpty caret`() {
        assert(Selection.caret(5, 3).isEmpty)
        assert(!Selection(5, 3, 5, 4).isEmpty)
        assert(!Selection(5, 3, 6, 3).isEmpty)
    }

    private fun assertCommand(
        snap: ListDocSnapshot,
        initialSelection: Selection,
        command: DocCommand,
        patches: List<DocPatch>,
        lines: List<String>,
        expectedSelection: Selection,
    ) {
        val result = command.compute(snap, initialSelection)!!
        assertEquals(patches, result.patches)

        val applied = applyPatches(snap.lines(), initialSelection, result.patches)
        assertEquals(lines, applied.lines)
        assertEquals(expectedSelection, applied.selection)
    }

    private data class Applied(val lines: List<String>, val selection: Selection)

    private fun applyPatches(
        inputLines: List<String>,
        inputSelection: Selection,
        patches: List<DocPatch>,
    ): Applied {
        var lines = inputLines
        var selection = inputSelection
        patches.forEach { patch ->
            val text = lines.joinToString("\n")
            val start = lines.flatOffset(patch.startLine, patch.startCol)
            val end = lines.flatOffset(patch.endLine, patch.endCol)
            val nextText = text.substring(0, start) + patch.replacement + text.substring(end)
            val nextLines = nextText.toLines()
            val nextStart = nextLines.positionOf(
                follow(lines, selection.startLine, selection.startCol, start, end, patch.replacement),
            )
            val nextEnd = nextLines.positionOf(
                follow(lines, selection.endLine, selection.endCol, start, end, patch.replacement),
            )
            selection = Selection(
                startLine = nextStart.first,
                startCol = nextStart.second,
                endLine = nextEnd.first,
                endCol = nextEnd.second,
            )
            lines = nextLines
        }
        return Applied(lines, selection)
    }

    private fun follow(
        lines: List<String>,
        line: Int,
        col: Int,
        start: Int,
        end: Int,
        replacement: String,
    ): Int {
        val pos = lines.flatOffset(line, col)
        val oldLen = end - start
        val newLen = replacement.length
        return when {
            oldLen == 0 && pos >= start -> pos + newLen
            oldLen == 0 -> pos
            replacement.isEmpty() && pos <= start -> pos
            replacement.isEmpty() && pos <= end -> start
            replacement.isEmpty() -> pos - oldLen
            pos < start -> pos
            pos <= end -> start + newLen
            else -> pos + newLen - oldLen
        }
    }

    private fun ListDocSnapshot.lines(): List<String> =
        (0 until lineCount).map { lineAt(it) }

    private fun List<String>.flatOffset(line: Int, col: Int): Int {
        var offset = 0
        for (i in 0 until line) {
            offset += this[i].length + 1
        }
        return offset + col
    }

    private fun List<String>.positionOf(offset: Int): Pair<Int, Int> {
        var remaining = offset.coerceAtLeast(0)
        forEachIndexed { index, line ->
            if (remaining <= line.length) return index to remaining
            remaining -= line.length + 1
        }
        val last = lastIndex.coerceAtLeast(0)
        return last to getOrElse(last) { "" }.length
    }

    private fun String.toLines(): List<String> {
        val result = mutableListOf<String>()
        var start = 0
        indices.forEach { index ->
            if (this[index] == '\n') {
                result += substring(start, index)
                start = index + 1
            }
        }
        result += substring(start)
        return result
    }
}

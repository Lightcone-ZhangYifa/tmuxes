package com.tmuxes.editor

/**
 * Pure editor commands for keybar document edits. Commands emit one or more
 * fine-grained [DocPatch] values; the runner applies them in order and lets
 * Sora's cursor-follow behavior determine the final caret/selection.
 */
object DocCommands {

    val DeleteLines: DocCommand = DocCommand { snap, sel ->
        if (snap.lineCount == 0) return@DocCommand null
        val first = sel.firstLine.coerceIn(0, snap.lineCount - 1)
        val last = sel.lastLine.coerceIn(first, snap.lineCount - 1)
        val patch = when {
            first == 0 && last == snap.lineCount - 1 -> {
                DocPatch(0, 0, last, snap.lineAt(last).length, "")
            }
            last == snap.lineCount - 1 -> {
                DocPatch(first - 1, snap.lineAt(first - 1).length, last, snap.lineAt(last).length, "")
            }
            else -> {
                DocPatch(first, 0, last + 1, 0, "")
            }
        }
        CommandResult(listOf(patch))
    }

    val MoveLinesUp: DocCommand = DocCommand { snap, sel ->
        if (snap.lineCount == 0) return@DocCommand null
        val first = sel.firstLine.coerceIn(0, snap.lineCount - 1)
        val last = sel.lastLine.coerceIn(first, snap.lineCount - 1)
        if (first == 0) return@DocCommand null

        val above = snap.lineAt(first - 1)
        val deleteAbove = DocPatch(first - 1, 0, first, 0, "")
        val insertAboveAfterBlock = if (last == snap.lineCount - 1) {
            DocPatch(last - 1, snap.lineAt(last).length, last - 1, snap.lineAt(last).length, "\n$above")
        } else {
            DocPatch(last, 0, last, 0, "$above\n")
        }
        CommandResult(listOf(deleteAbove, insertAboveAfterBlock))
    }

    val MoveLinesDown: DocCommand = DocCommand { snap, sel ->
        if (snap.lineCount == 0) return@DocCommand null
        val first = sel.firstLine.coerceIn(0, snap.lineCount - 1)
        val last = sel.lastLine.coerceIn(first, snap.lineCount - 1)
        if (last >= snap.lineCount - 1) return@DocCommand null

        val below = snap.lineAt(last + 1)
        val deleteBelow = if (last + 1 == snap.lineCount - 1) {
            DocPatch(last, snap.lineAt(last).length, last + 1, below.length, "")
        } else {
            DocPatch(last + 1, 0, last + 2, 0, "")
        }
        val insertBelowBeforeBlock = DocPatch(first, 0, first, 0, "$below\n")
        CommandResult(listOf(deleteBelow, insertBelowBeforeBlock))
    }

    val ToggleLineComment: DocCommand = DocCommand { snap, sel ->
        if (snap.lineCount == 0) return@DocCommand null
        val first = sel.firstLine.coerceIn(0, snap.lineCount - 1)
        val last = sel.lastLine.coerceIn(first, snap.lineCount - 1)
        val lines = (first..last).map { snap.lineAt(it) }
        val allCommented = lines.all { it.isLineCommented() }
        val patches = (first..last).mapNotNull { line ->
            val text = snap.lineAt(line)
            val indent = text.indentLength()
            if (allCommented) {
                if (text.substring(indent).startsWith("# ")) {
                    DocPatch(line, indent, line, indent + 2, "")
                } else {
                    null
                }
            } else if (text.isLineCommented()) {
                null
            } else {
                DocPatch(line, indent, line, indent, "# ")
            }
        }
        patches.toResult()
    }

    fun indent(tabWidth: Int): DocCommand = DocCommand { snap, sel ->
        if (snap.lineCount == 0) return@DocCommand null
        val first = sel.firstLine.coerceIn(0, snap.lineCount - 1)
        val last = sel.lastLine.coerceIn(first, snap.lineCount - 1)
        val spaces = " ".repeat(tabWidth.coerceAtLeast(0))
        if (spaces.isEmpty()) return@DocCommand null
        (first..last).map { line ->
            DocPatch(line, 0, line, 0, spaces)
        }.toResult()
    }

    fun outdent(tabWidth: Int): DocCommand = DocCommand { snap, sel ->
        if (snap.lineCount == 0) return@DocCommand null
        val first = sel.firstLine.coerceIn(0, snap.lineCount - 1)
        val last = sel.lastLine.coerceIn(first, snap.lineCount - 1)
        val width = tabWidth.coerceAtLeast(0)
        if (width == 0) return@DocCommand null
        val patches = (first..last).mapNotNull { line ->
            val remove = snap.lineAt(line).leadingSpacesToRemove(width)
            if (remove == 0) null else DocPatch(line, 0, line, remove, "")
        }
        patches.toResult()
    }

    val Duplicate: DocCommand = DocCommand { snap, sel ->
        if (snap.lineCount == 0) return@DocCommand null
        val first = sel.firstLine.coerceIn(0, snap.lineCount - 1)
        val last = sel.lastLine.coerceIn(first, snap.lineCount - 1)
        val block = (first..last).joinToString("\n") { snap.lineAt(it) }
        CommandResult(listOf(DocPatch(first, 0, first, 0, "$block\n")))
    }

    fun smartTab(tabWidth: Int): DocCommand = DocCommand { snap, sel ->
        if (snap.lineCount == 0) return@DocCommand null
        if (sel.firstLine != sel.lastLine) {
            indent(tabWidth).compute(snap, sel)
        } else {
            val spaces = " ".repeat(tabWidth.coerceAtLeast(0))
            if (spaces.isEmpty()) null else {
                CommandResult(listOf(DocPatch(sel.startLine, sel.startCol, sel.endLine, sel.endCol, spaces)))
            }
        }
    }

    fun pasteLineAbove(text: String): DocCommand = DocCommand { snap, sel ->
        if (snap.lineCount == 0) return@DocCommand null
        val line = sel.startLine.coerceIn(0, snap.lineCount - 1)
        val lineText = if (text.endsWith("\n")) text else "$text\n"
        CommandResult(listOf(DocPatch(line, 0, line, 0, lineText)))
    }

    private fun List<DocPatch>.toResult(): CommandResult? =
        if (isEmpty()) null else CommandResult(this)

    private fun String.indentLength(): Int = takeWhile { it == ' ' || it == '\t' }.length

    private fun String.leadingSpacesToRemove(limit: Int): Int {
        var count = 0
        while (count < limit && count < length && this[count] == ' ') {
            count++
        }
        return count
    }

    private fun String.isLineCommented(): Boolean =
        substring(indentLength()).startsWith("# ")
}

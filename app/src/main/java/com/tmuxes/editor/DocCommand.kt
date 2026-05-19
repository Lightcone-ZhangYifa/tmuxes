package com.tmuxes.editor

/**
 * Read-only view over editor lines. O(1) per line read. Lets [DocCommand]
 * implementations stay pure and testable without depending on Sora's
 * `CodeEditor` / `Content`.
 */
interface DocSnapshot {
    val lineCount: Int
    fun lineAt(index: Int): String
}

/**
 * Selection range (caret == selection.end). Encapsulates the multi-line
 * IDE convention: a selection ending at column 0 of a new line does NOT
 * cover that line — see [lastLine].
 */
data class Selection(
    val startLine: Int, val startCol: Int,
    val endLine: Int, val endCol: Int,
) {
    val isEmpty: Boolean get() = startLine == endLine && startCol == endCol

    val firstLine: Int get() = startLine

    /**
     * Last line covered by the selection, applying the IDE convention
     * "selection ending at column 0 of a new line doesn't include that line".
     */
    val lastLine: Int get() = if (endCol == 0 && endLine > startLine) endLine - 1 else endLine

    companion object {
        fun caret(line: Int, col: Int) = Selection(line, col, line, col)
    }
}

/** A single Sora `Content.replace(...)` operation. */
data class DocPatch(
    val startLine: Int, val startCol: Int,
    val endLine: Int, val endCol: Int,
    val replacement: String,
)

/** Result of executing a [DocCommand]. */
data class CommandResult(
    val patches: List<DocPatch>,
)

/**
 * Pure function: (snapshot, selection) → command result, or `null` for no-op.
 *
 * Implementations MUST be pure — no `CodeEditor`, no Sora APIs, no global
 * state. The signature itself enforces this: commands can only emit text
 * patches, so cursor placement must come from Sora's natural follow behavior.
 */
fun interface DocCommand {
    fun compute(snapshot: DocSnapshot, selection: Selection): CommandResult?
}

/**
 * Minimal in-memory [DocSnapshot] for tests / lightweight callers.
 */
class ListDocSnapshot(private val lines: List<String>) : DocSnapshot {
    override val lineCount: Int get() = lines.size
    override fun lineAt(index: Int): String = lines[index]
}

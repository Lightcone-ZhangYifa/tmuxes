package com.tmuxes.editor

/**
 * Separates programmatic editor replacement from user edits.
 *
 * Sora emits content-change events for CodeEditor.setText(). Those events must
 * update syntax analysis, but they must not be recorded as user edits. This
 * state machine keeps that decision local and deterministic.
 */
class YamlEditorTextSync {
    enum class ChangeAction {
        SetNewText,
        Insert,
        Delete,
        Other
    }

    data class Replacement(
        val token: Int,
        val fileKey: String,
        val expectedText: String,
        val inSetTextCall: Boolean
    )

    private var nextToken = 1
    private var replacement: Replacement? = null

    fun beginReplacement(fileKey: String, expectedText: String): Int {
        val token = nextToken++
        replacement = Replacement(
            token = token,
            fileKey = fileKey,
            expectedText = expectedText,
            inSetTextCall = true
        )
        return token
    }

    fun finishReplacementCall(token: Int) {
        val current = replacement ?: return
        if (current.token == token) {
            replacement = current.copy(inSetTextCall = false)
        }
    }

    fun shouldRecordAsUserEdit(
        fileKey: String,
        action: ChangeAction,
        editorText: String
    ): Boolean {
        val current = replacement
        if (current != null && current.fileKey == fileKey) {
            val isSetTextEvent = action == ChangeAction.SetNewText
            val reachedExpectedText = editorText == current.expectedText
            if (current.inSetTextCall || isSetTextEvent || reachedExpectedText) {
                if (!current.inSetTextCall || reachedExpectedText || isSetTextEvent) {
                    replacement = null
                }
                return false
            }
            replacement = null
        }

        if (action == ChangeAction.SetNewText) {
            return false
        }

        return true
    }

    fun clear() {
        replacement = null
    }
}

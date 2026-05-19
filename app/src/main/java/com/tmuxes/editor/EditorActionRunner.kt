package com.tmuxes.editor

import android.content.Context
import io.github.rosemoe.sora.widget.CodeEditor

class EditorActionRunner(
    private val editor: CodeEditor,
    context: Context,
) {
    private val appContext = context.applicationContext

    fun dispatch(action: KeyAction, toggleFn: () -> Unit): Boolean =
        when (action) {
            is KeyAction.External -> {
                action.invoke()
                true
            }
            KeyAction.FnToggle -> {
                toggleFn()
                true
            }
            is KeyAction.Mutate -> withBatch {
                applyMutateInternal(action.command)
            }
            is KeyAction.Native -> withBatch {
                action.invoke(EditorActionContext(editor, appContext, ::applyMutateInternal))
                true
            }
        }

    internal fun applyMutateInternal(command: DocCommand): Boolean {
        val result = command.compute(editor.docSnapshot(), editor.currentSelection()) ?: return false
        result.patches.forEach { patch ->
            editor.text.replace(
                patch.startLine, patch.startCol,
                patch.endLine, patch.endCol,
                patch.replacement,
            )
        }
        return true
    }

    private fun withBatch(block: () -> Boolean): Boolean {
        val content = editor.text
        content.beginBatchEdit()
        return try {
            block()
        } finally {
            content.endBatchEdit()
        }
    }
}

internal fun CodeEditor.docSnapshot(): DocSnapshot = object : DocSnapshot {
    override val lineCount: Int get() = this@docSnapshot.text.lineCount
    override fun lineAt(index: Int): String = this@docSnapshot.text.getLineString(index)
}

internal fun CodeEditor.currentSelection(): Selection {
    val cur = cursor
    return Selection(cur.leftLine, cur.leftColumn, cur.rightLine, cur.rightColumn)
}

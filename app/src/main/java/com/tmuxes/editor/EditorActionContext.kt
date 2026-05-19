package com.tmuxes.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import io.github.rosemoe.sora.widget.CodeEditor

enum class ClipKind(val label: String) {
    TEXT("tmuxes.editor.TEXT"),
    LINE("tmuxes.editor.LINE"),
}

data class ClipboardContent(
    val text: String,
    val kind: ClipKind,
)

class EditorActionContext internal constructor(
    val editor: CodeEditor,
    private val context: Context,
    private val applyMutateInternal: (DocCommand) -> Boolean,
) {
    fun readClip(): ClipboardContent {
        val manager = clipboardManager()
        val clip = manager.primaryClip ?: return ClipboardContent("", ClipKind.TEXT)
        val text = clip.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        val kind = if (clip.description?.label?.toString() == ClipKind.LINE.label) {
            ClipKind.LINE
        } else {
            ClipKind.TEXT
        }
        return ClipboardContent(text, kind)
    }

    fun writeClip(text: String, kind: ClipKind) {
        clipboardManager().setPrimaryClip(ClipData.newPlainText(kind.label, text))
    }

    fun applyMutate(command: DocCommand): Boolean = applyMutateInternal(command)

    private fun clipboardManager(): ClipboardManager =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
}

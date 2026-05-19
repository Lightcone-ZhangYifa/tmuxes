package com.tmuxes.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YamlEditorTextSyncTest {

    @Test
    fun `setText event is not recorded as user edit`() {
        val sync = YamlEditorTextSync()
        val token = sync.beginReplacement("servers", "servers: []\n")
        sync.finishReplacementCall(token)

        val userEdit = sync.shouldRecordAsUserEdit(
            fileKey = "servers",
            action = YamlEditorTextSync.ChangeAction.SetNewText,
            editorText = "servers: []\n"
        )

        assertFalse(userEdit)
    }

    @Test
    fun `delete and insert events inside setText call are not user edits`() {
        val sync = YamlEditorTextSync()
        val token = sync.beginReplacement("servers", "servers: []\n")

        assertFalse(
            sync.shouldRecordAsUserEdit(
                fileKey = "servers",
                action = YamlEditorTextSync.ChangeAction.Delete,
                editorText = ""
            )
        )
        assertFalse(
            sync.shouldRecordAsUserEdit(
                fileKey = "servers",
                action = YamlEditorTextSync.ChangeAction.Insert,
                editorText = "servers: []\n"
            )
        )

        sync.finishReplacementCall(token)
    }

    @Test
    fun `first real edit after programmatic replacement is recorded`() {
        val sync = YamlEditorTextSync()
        val token = sync.beginReplacement("servers", "servers: []\n")
        sync.finishReplacementCall(token)

        val userEdit = sync.shouldRecordAsUserEdit(
            fileKey = "servers",
            action = YamlEditorTextSync.ChangeAction.Insert,
            editorText = "servers:\n  - id: demo\n"
        )

        assertTrue(userEdit)
    }

    @Test
    fun `untracked insert is treated as user edit`() {
        val sync = YamlEditorTextSync()

        val userEdit = sync.shouldRecordAsUserEdit(
            fileKey = "global",
            action = YamlEditorTextSync.ChangeAction.Insert,
            editorText = "theme: dark\n"
        )

        assertTrue(userEdit)
    }
}

package com.tmuxes.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YamlEditorSessionTest {

    @Test
    fun `loading current file creates a clean buffer`() {
        val session = YamlEditorSession("servers")

        val text = session.loadCurrent { "servers: []\n" }

        assertEquals("servers: []\n", text)
        assertFalse(session.snapshot().isCurrentFileDirty)
        assertFalse(session.snapshot().hasDirtyFiles)
    }

    @Test
    fun `switching to another clean file does not make it dirty`() {
        val session = YamlEditorSession("widget_42")
        session.loadCurrent { "widget:\n  label: demo\n" }

        val result = session.switchTo(
            fileKey = "servers",
            currentEditorText = "widget:\n  label: demo\n",
            loader = { "servers: []\n" }
        )

        assertEquals("servers", result.fileKey)
        assertEquals("servers: []\n", result.text)
        assertFalse(result.snapshot.isCurrentFileDirty)
        assertEquals(emptySet<String>(), result.snapshot.dirtyFileKeys)
    }

    @Test
    fun `switching preserves dirty state on the file that was left`() {
        val session = YamlEditorSession("widget_42")
        session.loadCurrent { "widget:\n  label: demo\n" }

        val result = session.switchTo(
            fileKey = "servers",
            currentEditorText = "widget:\n  label: changed\n",
            loader = { "servers: []\n" }
        )

        assertFalse(result.snapshot.isCurrentFileDirty)
        assertEquals(setOf("widget_42"), result.snapshot.dirtyFileKeys)
    }

    @Test
    fun `switching back to a dirty file returns its working text`() {
        val session = YamlEditorSession("widget_42")
        session.loadCurrent { "widget:\n  label: demo\n" }
        session.switchTo(
            fileKey = "servers",
            currentEditorText = "widget:\n  label: changed\n",
            loader = { "servers: []\n" }
        )

        val result = session.switchTo(
            fileKey = "widget_42",
            currentEditorText = "servers: []\n",
            loader = { error("widget buffer should already be loaded") }
        )

        assertEquals("widget:\n  label: changed\n", result.text)
        assertTrue(result.snapshot.isCurrentFileDirty)
        assertEquals(setOf("widget_42"), result.snapshot.dirtyFileKeys)
    }

    @Test
    fun `accepting clean text clears dirty state`() {
        val session = YamlEditorSession("servers")
        session.loadCurrent { "servers: []\n" }
        session.editCurrent("servers:\n  - id: demo\n")

        val snapshot = session.saveCurrent("servers:\n  - id: demo\n")

        assertFalse(snapshot.isCurrentFileDirty)
        assertEquals(emptySet<String>(), snapshot.dirtyFileKeys)
    }

    @Test
    fun `external clean replacement updates an untouched file`() {
        val session = YamlEditorSession("global")
        session.load("servers") { "servers: []\n" }

        val snapshot = session.acceptClean("servers", "servers:\n  - id: demo\n")

        assertFalse(snapshot.hasDirtyFiles)
        assertEquals("servers:\n  - id: demo\n", session.textFor("servers"))
    }
}

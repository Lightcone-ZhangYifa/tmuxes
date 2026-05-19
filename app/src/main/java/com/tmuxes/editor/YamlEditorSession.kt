package com.tmuxes.editor

/**
 * Owns YAML editor buffers independently from the Sora editor widget.
 *
 * Each file has a clean baseline, loaded from storage or accepted after save,
 * and a working buffer that may contain unsaved edits. Switching files only
 * moves the active key and never changes whether a buffer is dirty.
 */
class YamlEditorSession(initialFileKey: String) {
    private data class Buffer(
        val cleanText: String,
        val workingText: String
    ) {
        val isDirty: Boolean get() = cleanText != workingText
    }

    data class Snapshot(
        val currentFileKey: String,
        val isCurrentFileDirty: Boolean,
        val dirtyFileKeys: Set<String>
    ) {
        val hasDirtyFiles: Boolean get() = dirtyFileKeys.isNotEmpty()
    }

    data class SwitchResult(
        val fileKey: String,
        val text: String,
        val snapshot: Snapshot
    )

    var currentFileKey: String = initialFileKey
        private set

    private val buffers = linkedMapOf<String, Buffer>()

    fun loadCurrent(loader: (String) -> String): String = load(currentFileKey, loader)

    fun load(fileKey: String, loader: (String) -> String): String {
        buffers[fileKey]?.let { return it.workingText }
        val text = loader(fileKey)
        buffers[fileKey] = Buffer(cleanText = text, workingText = text)
        return text
    }

    fun editCurrent(text: String): Snapshot {
        edit(currentFileKey, text)
        return snapshot()
    }

    fun edit(fileKey: String, text: String): Snapshot {
        val previous = buffers[fileKey]
        val cleanText = previous?.cleanText ?: ""
        buffers[fileKey] = Buffer(cleanText = cleanText, workingText = text)
        return snapshot()
    }

    fun acceptClean(fileKey: String, text: String): Snapshot {
        buffers[fileKey] = Buffer(cleanText = text, workingText = text)
        return snapshot()
    }

    fun saveCurrent(text: String): Snapshot {
        acceptClean(currentFileKey, text)
        return snapshot()
    }

    fun switchTo(
        fileKey: String,
        currentEditorText: String,
        loader: (String) -> String
    ): SwitchResult {
        if (fileKey != currentFileKey) {
            edit(currentFileKey, currentEditorText)
            currentFileKey = fileKey
        }
        val text = load(fileKey, loader)
        return SwitchResult(
            fileKey = fileKey,
            text = text,
            snapshot = snapshot()
        )
    }

    fun textFor(fileKey: String): String? = buffers[fileKey]?.workingText

    fun isDirty(fileKey: String): Boolean = buffers[fileKey]?.isDirty == true

    fun dirtyFileKeys(): Set<String> = buffers
        .filterValues { it.isDirty }
        .keys
        .toCollection(linkedSetOf())

    fun snapshot(): Snapshot = Snapshot(
        currentFileKey = currentFileKey,
        isCurrentFileDirty = isDirty(currentFileKey),
        dirtyFileKeys = dirtyFileKeys()
    )
}

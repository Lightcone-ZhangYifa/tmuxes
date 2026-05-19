package com.tmuxes.editor

object YamlCompletionProvider {

    data class CompletionContext(
        val sectionPath: String = "",
        val keyPrefix: String = "",
        val currentKey: String = "",
        val valuePrefix: String = "",
        val isValuePosition: Boolean = false
    )

    data class CompletionEntry(
        val label: String,
        val detail: String,
        val insertText: String = label
    )

    fun parseContext(lines: List<String>, cursorLine: Int, cursorCol: Int): CompletionContext {
        if (cursorLine < 0 || cursorLine >= lines.size) return CompletionContext()
        val line = lines[cursorLine]
        val textBeforeCursor = if (cursorCol <= line.length) line.substring(0, cursorCol) else line
        val indent = textBeforeCursor.length - textBeforeCursor.trimStart().length

        var sectionPath = ""
        if (indent > 0) {
            for (i in cursorLine downTo 0) {
                val scanLine = lines[i].trimEnd()
                if (scanLine.isNotEmpty() && !scanLine.startsWith(" ") && scanLine.endsWith(":")) {
                    sectionPath = scanLine.removeSuffix(":").trim()
                    break
                }
            }
        }

        val colonIndex = textBeforeCursor.indexOf(':')
        if (colonIndex >= 0 && cursorCol > colonIndex) {
            val keyPart = textBeforeCursor.substring(0, colonIndex).trim()
            val valuePart = textBeforeCursor.substring(colonIndex + 1).trimStart()
            return CompletionContext(
                sectionPath = sectionPath,
                currentKey = keyPart,
                valuePrefix = valuePart,
                isValuePosition = true
            )
        }

        return CompletionContext(
            sectionPath = sectionPath,
            keyPrefix = textBeforeCursor.trimStart(),
            isValuePosition = false
        )
    }

    fun getCompletions(
        sectionPath: String,
        keyOrPrefix: String,
        isValue: Boolean,
        valuePrefix: String = "",
        existingKeys: Set<String> = emptySet(),
        fileType: YamlFileType = YamlFileType.GLOBAL
    ): List<CompletionEntry> {
        if (isValue) return getValueCompletions(sectionPath, keyOrPrefix, valuePrefix, fileType)
        return getKeyCompletions(sectionPath, keyOrPrefix, existingKeys, fileType)
    }

    private fun getKeyCompletions(
        sectionPath: String,
        prefix: String,
        existingKeys: Set<String>,
        fileType: YamlFileType
    ): List<CompletionEntry> {
        val children = YamlSchema.childrenAtFlat(sectionPath, fileType)
        return children
            .filter { it.name.startsWith(prefix) && it.name !in existingKeys }
            .map { node ->
                val isLeaf = node is YamlSchema.SchemaNode.Leaf
                val typeHint = if (isLeaf) (node as YamlSchema.SchemaNode.Leaf).typeHint else "Section"
                val insertSuffix = when {
                    !isLeaf -> ":\n  "
                    sectionPath.isEmpty() -> ":\n  "
                    else -> ": "
                }
                CompletionEntry(
                    label = node.name,
                    detail = typeHint,
                    insertText = node.name + insertSuffix
                )
            }
    }

    private fun getValueCompletions(
        sectionPath: String,
        key: String,
        prefix: String,
        fileType: YamlFileType
    ): List<CompletionEntry> {
        val leaf = YamlSchema.leafAt(sectionPath, key, fileType) ?: return emptyList()
        return leaf.completions()
            .filter { it.startsWith(prefix) }
            .map { CompletionEntry(it, leaf.typeHint) }
    }
}

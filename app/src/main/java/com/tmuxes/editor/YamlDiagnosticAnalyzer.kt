package com.tmuxes.editor

import com.tmuxes.util.AppLogger
import com.tmuxes.util.AppLogger.Category
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.error.YAMLException

/**
 * Walks a parsed YAML document and emits diagnostics by consulting
 * [YamlSchema], which is itself derived from the [com.tmuxes.data.settings.Settings]
 * registry. Adding a setting to the registry makes it appear here without
 * any change to the analyzer.
 *
 * The analyzer recurses into nested maps so deeply-nested keys
 * (`editor.errorlens.enabled`) are validated as readily as flat ones.
 */
object YamlDiagnosticAnalyzer {
    enum class Severity { ERROR, WARNING, INFO }
    data class Diagnostic(val line: Int, val message: String, val severity: Severity)

    fun analyze(yamlText: String, fileType: YamlFileType = YamlFileType.GLOBAL): List<Diagnostic> =
        AppLogger.timed(Category.EDITOR, "yaml.analyze type=$fileType bytes=${yamlText.length}") {
            val diagnostics = mutableListOf<Diagnostic>()
            val parsed: Any?
            try {
                parsed = Yaml().load<Any>(yamlText)
            } catch (e: YAMLException) {
                val line = extractLineFromException(e, yamlText)
                AppLogger.d(Category.EDITOR) {
                    "yaml.analyze syntax-error line=$line msg='${e.message?.take(100) ?: ""}'"
                }
                diagnostics.add(Diagnostic(line, e.message ?: "YAML syntax error", Severity.ERROR))
                return@timed diagnostics
            }
            if (parsed == null || parsed !is Map<*, *>) return@timed diagnostics
            @Suppress("UNCHECKED_CAST")
            when (fileType) {
                YamlFileType.SERVERS -> validateServers(parsed as Map<String, Any?>, yamlText.lines(), diagnostics)
                YamlFileType.SNIPPETS -> validateSnippets(parsed as Map<String, Any?>, yamlText.lines(), diagnostics)
                else -> validateMap(
                    map = parsed as Map<String, Any?>,
                    pathSoFar = emptyList(),
                    indentLevel = 0,
                    lines = yamlText.lines(),
                    diagnostics = diagnostics,
                    fileType = fileType
                )
            }
            if (diagnostics.isNotEmpty()) {
                val errors = diagnostics.count { it.severity == Severity.ERROR }
                val warns = diagnostics.count { it.severity == Severity.WARNING }
                AppLogger.d(Category.EDITOR) {
                    "yaml.analyze ← total=${diagnostics.size} errors=$errors warns=$warns"
                }
            }
            diagnostics
        }

    /**
     * Recursively walk a YAML map, comparing each entry against the
     * schema tree. Unknown keys produce WARNING diagnostics; bad values
     * produce ERROR diagnostics.
     */
    private fun validateMap(
        map: Map<String, Any?>,
        pathSoFar: List<String>,
        indentLevel: Int,
        lines: List<String>,
        diagnostics: MutableList<Diagnostic>,
        fileType: YamlFileType
    ) {
        val children = YamlSchema.childrenAt(pathSoFar, fileType)
        val knownNames = children.map { it.name }.toSet()
        for ((rawKey, value) in map) {
            val key = rawKey.toString()
            val line = findKeyLine(lines, key, indentLevel)
            if (key !in knownNames) {
                val context = if (pathSoFar.isEmpty()) "root" else pathSoFar.joinToString(".")
                diagnostics.add(Diagnostic(line, "Unknown key: $key in $context", Severity.WARNING))
                continue
            }
            val node = children.first { it.name == key }
            when (node) {
                is YamlSchema.SchemaNode.Section -> {
                    if (value is Map<*, *>) {
                        @Suppress("UNCHECKED_CAST")
                        validateMap(
                            map = value as Map<String, Any?>,
                            pathSoFar = pathSoFar + key,
                            indentLevel = indentLevel + 1,
                            lines = lines,
                            diagnostics = diagnostics,
                            fileType = fileType
                        )
                    } else if (value != null) {
                        diagnostics.add(Diagnostic(line, "Expected nested map under $key", Severity.ERROR))
                    }
                }
                is YamlSchema.SchemaNode.Leaf -> {
                    val msg = node.validate(value)
                    if (msg != null) diagnostics.add(Diagnostic(line, msg, Severity.ERROR))
                }
            }
        }
    }

    /**
     * Servers payload still has a tailored validator because it's a
     * top-level list (not a map of sections), and the auth-profile sub-list
     * was removed in an earlier refactor.
     */
    private fun validateServers(
        root: Map<String, Any?>,
        lines: List<String>,
        diagnostics: MutableList<Diagnostic>
    ) {
        val serversValue = root["servers"]
        for ((key, _) in root) {
            if (key != "servers") {
                diagnostics.add(Diagnostic(findKeyLine(lines, key, 0),
                    "Unknown key: $key (expected: servers)", Severity.WARNING))
            }
        }
        if (serversValue == null) return
        if (serversValue !is List<*>) {
            diagnostics.add(Diagnostic(findKeyLine(lines, "servers", 0),
                "Expected 'servers' to be a list", Severity.ERROR))
            return
        }
        val serverChildren = YamlSchema.childrenAt(listOf("servers[]"), YamlFileType.SERVERS)
        val serverKeyNames = serverChildren.map { it.name }.toSet()
        for (item in serversValue) {
            if (item !is Map<*, *>) continue
            @Suppress("UNCHECKED_CAST")
            val serverMap = item as Map<String, Any?>
            for ((rawKey, value) in serverMap) {
                val key = rawKey.toString()
                val line = findKeyLine(lines, key, 2)
                if (key !in serverKeyNames) {
                    diagnostics.add(Diagnostic(line, "Unknown key: $key in servers[]", Severity.WARNING))
                    continue
                }
                val leaf = serverChildren.first { it.name == key } as? YamlSchema.SchemaNode.Leaf
                    ?: continue
                val msg = leaf.validate(value)
                if (msg != null) diagnostics.add(Diagnostic(line, msg, Severity.ERROR))
            }
        }
    }

    /**
     * Snippets payload has a top-level `libraries:` list with nested
     * `snippets:` lists per library — two layers of homogeneous-object
     * arrays. The schema marks both with the `[]` suffix convention
     * (`libraries[]`, `libraries[].snippets[]`); this validator unpacks
     * both layers and runs leaf-level type checks on every field.
     */
    private fun validateSnippets(
        root: Map<String, Any?>,
        lines: List<String>,
        diagnostics: MutableList<Diagnostic>
    ) {
        for ((rawKey, _) in root) {
            val key = rawKey.toString()
            if (key != "libraries") {
                diagnostics.add(Diagnostic(
                    findKeyLine(lines, key, 0),
                    "Unknown key: $key (expected: libraries)",
                    Severity.WARNING
                ))
            }
        }
        val libsValue = root["libraries"] ?: return
        if (libsValue !is List<*>) {
            diagnostics.add(Diagnostic(
                findKeyLine(lines, "libraries", 0),
                "Expected 'libraries' to be a list",
                Severity.ERROR
            ))
            return
        }

        val libraryChildren = YamlSchema.childrenAt(listOf("libraries[]"), YamlFileType.SNIPPETS)
        // Library schema mixes leaves (id, name, …) with one nested
        // section (snippets[]); split for separate handling.
        val libraryLeafNames = libraryChildren
            .filterIsInstance<YamlSchema.SchemaNode.Leaf>()
            .map { it.name }.toSet()
        val snippetChildren = YamlSchema.childrenAt(
            listOf("libraries[]", "snippets[]"),
            YamlFileType.SNIPPETS
        )
        val snippetLeafNames = snippetChildren
            .filterIsInstance<YamlSchema.SchemaNode.Leaf>()
            .map { it.name }.toSet()

        for (libRaw in libsValue) {
            val lib = libRaw as? Map<*, *> ?: continue
            for ((rawKey, value) in lib) {
                val key = rawKey.toString()
                val line = findKeyLine(lines, key, 2)
                when {
                    key == "snippets" -> {
                        // Nested list — validated below.
                        if (value != null && value !is List<*>) {
                            diagnostics.add(Diagnostic(line,
                                "Expected 'snippets' to be a list", Severity.ERROR))
                        }
                    }
                    key in libraryLeafNames -> {
                        val leaf = libraryChildren.first { it.name == key }
                            as YamlSchema.SchemaNode.Leaf
                        val msg = leaf.validate(value)
                        if (msg != null) diagnostics.add(Diagnostic(line, msg, Severity.ERROR))
                    }
                    else -> {
                        diagnostics.add(Diagnostic(line,
                            "Unknown key: $key in libraries[]", Severity.WARNING))
                    }
                }
            }
            val snippetsValue = lib["snippets"] as? List<*> ?: continue
            for (snipRaw in snippetsValue) {
                val snip = snipRaw as? Map<*, *> ?: continue
                for ((rawKey, value) in snip) {
                    val key = rawKey.toString()
                    val line = findKeyLine(lines, key, 6)
                    if (key !in snippetLeafNames) {
                        diagnostics.add(Diagnostic(line,
                            "Unknown key: $key in snippets[]", Severity.WARNING))
                        continue
                    }
                    val leaf = snippetChildren.first { it.name == key }
                        as YamlSchema.SchemaNode.Leaf
                    val msg = leaf.validate(value)
                    if (msg != null) diagnostics.add(Diagnostic(line, msg, Severity.ERROR))
                }
            }
        }
    }

    private fun findKeyLine(lines: List<String>, key: String, indentLevel: Int): Int {
        val prefix = " ".repeat(indentLevel * 2)
        val pattern = "$prefix$key:"
        for ((i, line) in lines.withIndex()) {
            if (line.startsWith(pattern) && (line.length == pattern.length || line[pattern.length] == ' ')) return i
        }
        return 0
    }

    private fun extractLineFromException(e: YAMLException, yamlText: String): Int {
        val lineMatch = Regex("line (\\d+)").find(e.message ?: "")
        return if (lineMatch != null) (lineMatch.groupValues[1].toIntOrNull() ?: 1) - 1 else 0
    }
}

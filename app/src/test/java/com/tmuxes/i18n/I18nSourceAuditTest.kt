package com.tmuxes.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class I18nSourceAuditTest {

    @Test
    fun `language setting resolves system english and chinese`() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromSetting("system"))
        assertEquals(AppLanguage.EN, AppLanguage.fromSetting("en"))
        assertEquals(AppLanguage.ZH_HANS, AppLanguage.fromSetting("zh-Hans"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromSetting("unknown"))
    }

    @Test
    fun `zh Hans catalog covers core managed copy`() {
        val zh = I18n(AppLanguage.ZH_HANS)
        val messages = listOf(
            "Language",
            "Settings",
            "No problems detected",
            "Replace all",
            "Failed to add server: {error}",
            "Failed to create session: {error}",
            "Snippet duplicated",
            "Modified (Auto-saving)"
        )

        messages.forEach { message ->
            assertNotEquals("Missing zh-Hans translation for: $message", message, zh.t(message))
        }
        assertEquals("添加服务器失败：boom", zh.t("Failed to add server: {error}", mapOf("error" to "boom")))
        assertEquals("第 42 行", zh.t("Ln {line}", mapOf("line" to 42)))
    }

    @Test
    fun `standard license names remain untranslated`() {
        val zh = I18n(AppLanguage.ZH_HANS)

        STANDARD_LICENSE_NAMES.forEach { licenseName ->
            assertEquals(licenseName, zh.t(licenseName))
        }
    }

    @Test
    fun `source has no direct user visible text bypasses`() {
        val violations = ktFiles(sourceRoot()).flatMap { file ->
            val lines = file.readLines()
            val lineViolations = lines.mapIndexedNotNull { index, line ->
                val lineNumber = index + 1
                val rawTextLiteral = RAW_TEXT_LITERAL.containsMatchIn(line)
                val rawSnackbar = RAW_SNACKBAR_LITERAL.containsMatchIn(line)
                val rawToast = RAW_TOAST_LITERAL.containsMatchIn(line)
                val rawViewModelError = RAW_VIEWMODEL_ERROR.containsMatchIn(line)

                if (!rawTextLiteral && !rawSnackbar && !rawToast && !rawViewModelError) {
                    null
                } else if (isAllowedBypass(file, line)) {
                    null
                } else {
                    "${file.relativeTo(projectRoot()).path}:$lineNumber: ${line.trim()}"
                }
            }
            lineViolations + directTextCallViolations(file, lines)
        }.toList()

        assertTrue(
            "Direct UI text bypasses i18n catalog:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    @Test
    fun `source has no development compatibility storage remnants`() {
        val roots = listOf(sourceRoot(), testRoot(), projectFile("build.gradle.kts"))
        val violations = roots.flatMap { root ->
            val files = if (root.isFile) sequenceOf(root) else ktFiles(root)
                .filterNot { it.name == "I18nSourceAuditTest.kt" }
            files.flatMap { file ->
                file.readLines().asSequence().mapIndexedNotNull { index, line ->
                    val lineNumber = index + 1
                    val matched = FORBIDDEN_COMPATIBILITY_PATTERNS.firstOrNull { it.containsMatchIn(line) }
                    if (matched == null) null else "${file.relativeTo(projectRoot()).path}:$lineNumber: ${line.trim()}"
                }
            }.toList()
        }

        assertFalse(
            "Removed compatibility/storage-version artifacts remain:\n${violations.joinToString("\n")}",
            violations.isNotEmpty()
        )
    }

    @Test
    fun `app version matches current release and database remains first schema`() {
        val gradle = projectFile("build.gradle.kts").readText()
        val db = projectFile("src/main/java/com/tmuxes/data/db/AppDatabase.kt").readText()

        assertTrue(gradle.contains("versionCode = 2"))
        assertTrue(gradle.contains("versionName = \"1.0.1\""))
        assertTrue(db.contains("version = 1"))
        assertTrue(db.contains("\"tmuxes_database_v1\""))
        assertFalse(db.contains("add" + "Mig" + "rations"))
        assertFalse(db.contains("fallbackTo" + "Destructive" + "Mig" + "ration"))
    }

    @Test
    fun `source and docs avoid regional device vendor callouts`() {
        val roots = listOf(
            projectFile("src/main"),
            File(projectRoot(), "docs"),
            File(projectRoot(), "README.md")
        ).filter { it.exists() }

        val violations = roots.flatMap { root ->
            textFiles(root).flatMap { file ->
                file.readLines().asSequence().mapIndexedNotNull { index, line ->
                    val matched = REGIONAL_VENDOR_PATTERNS.firstOrNull { it.containsMatchIn(line) }
                    if (matched == null) null else "${file.relativeTo(projectRoot()).path}:${index + 1}: ${line.trim()}"
                }
            }.toList()
        }

        assertTrue(
            "Regional device-vendor copy remains:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    @Test
    fun `source comments are release ready`() {
        val root = projectRoot()
        val roots = listOf(
            File(root, "app/src/main"),
            File(root, "app/src/test"),
            File(root, "app/src/androidTest"),
            File(root, "gradle/scripts"),
            File(root, ".github"),
            File(root, ".editorconfig"),
            File(root, ".gitattributes"),
            File(root, ".gitignore"),
            File(root, "app/build.gradle.kts"),
            File(root, "build.gradle.kts"),
            File(root, "gradle.properties.example"),
            File(root, "settings.gradle.kts")
        ).filter { it.exists() }

        val violations = roots.flatMap { scanRoot ->
            textFiles(scanRoot).flatMap { file ->
                file.readLines().asSequence().flatMapIndexed { index, line ->
                    commentFragments(line).asSequence().mapNotNull { comment ->
                        val lineRef = "${file.relativeTo(root).path}:${index + 1}"
                        when {
                            CJK_TEXT.containsMatchIn(comment) ->
                                "$lineRef: non-English comment text: ${line.trim()}"
                            DOC_HANDOFF_COMMENT.containsMatchIn(comment) ->
                                "$lineRef: comment should explain the rule locally: ${line.trim()}"
                            else -> null
                        }
                    }
                }
            }.toList()
        }

        assertTrue(
            "Release comments must be self-contained English:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    private fun isAllowedBypass(file: File, line: String): Boolean {
        val path = file.invariantSeparatorsPath
        return path.endsWith("AddEditServerScreen.kt") && line.contains("Text(\"\${server.displayName}")
    }

    private fun directTextCallViolations(file: File, lines: List<String>): List<String> {
        val violations = mutableListOf<String>()
        lines.forEachIndexed { index, line ->
            if (!TEXT_CALL_START.containsMatchIn(line)) return@forEachIndexed

            val end = (index + TEXT_CALL_LOOKAHEAD_LINES).coerceAtMost(lines.lastIndex)
            for (candidateIndex in index..end) {
                val candidate = lines[candidateIndex]
                val rawNamedText = RAW_TEXT_NAMED_ALPHA_LITERAL.containsMatchIn(candidate) ||
                    RAW_TEXT_NAMED_IF_ALPHA_LITERAL.containsMatchIn(candidate)
                val rawPositionalText = candidateIndex == index + 1 &&
                    RAW_TEXT_POSITIONAL_ALPHA_LITERAL.containsMatchIn(candidate)

                if ((rawNamedText || rawPositionalText) && !isAllowedBypass(file, candidate)) {
                    val lineNumber = candidateIndex + 1
                    violations += "${file.relativeTo(projectRoot()).path}:$lineNumber: ${candidate.trim()}"
                    break
                }
                if (candidateIndex > index && TEXT_ARGUMENT_LINE.containsMatchIn(candidate)) break
                if (candidateIndex == index + 1 && candidate.trimStart().startsWith("t(")) break
            }
        }
        return violations
    }

    private fun ktFiles(root: File): Sequence<File> =
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }

    private fun textFiles(root: File): Sequence<File> =
        if (root.isFile) {
            sequenceOf(root)
        } else {
            root.walkTopDown().filter { file ->
                file.isFile && file.extension in SEARCHABLE_TEXT_EXTENSIONS
            }
        }

    private fun sourceRoot(): File = projectFile("src/main/java/com/tmuxes")

    private fun testRoot(): File = projectFile("src/test/java/com/tmuxes")

    private fun projectRoot(): File =
        generateSequence(File(".").absoluteFile.normalize()) { it.parentFile }
            .first { File(it, "app/build.gradle.kts").exists() || File(it, "build.gradle.kts").exists() }

    private fun projectFile(path: String): File {
        val root = projectRoot()
        val candidates = listOf(File(root, "app/$path"), File(root, path))
        return candidates.firstOrNull { it.exists() }
            ?: error("Project file not found: $path")
    }

    companion object {
        private val RAW_TEXT_LITERAL = Regex("(?<![A-Za-z0-9_])Text\\s*\\(\\s*\\\"")
        private val TEXT_CALL_START = Regex("(?<![A-Za-z0-9_])Text\\s*\\(")
        private val RAW_TEXT_NAMED_ALPHA_LITERAL = Regex("\\btext\\s*=\\s*\\\"[A-Za-z]")
        private val RAW_TEXT_NAMED_IF_ALPHA_LITERAL =
            Regex("\\btext\\s*=\\s*if\\s*\\([^)]*\\)\\s*\\\"[A-Za-z]")
        private val RAW_TEXT_POSITIONAL_ALPHA_LITERAL = Regex("^\\s*\\\"[A-Za-z]")
        private val TEXT_ARGUMENT_LINE = Regex("\\btext\\s*=")
        private const val TEXT_CALL_LOOKAHEAD_LINES = 6
        private val RAW_SNACKBAR_LITERAL = Regex("showSnackbar\\s*\\(\\s*\\\"|showSnackbar\\s*\\(\\s*message\\s*=\\s*\\\"")
        private val RAW_TOAST_LITERAL = Regex("Toast\\.makeText\\s*\\([^,]+,\\s*\\\"")
        private val RAW_VIEWMODEL_ERROR =
            Regex("_(errorMessage|loadError|systemInfoError)\\.value\\s*=\\s*\\\"")

        private val FORBIDDEN_COMPATIBILITY_PATTERNS = listOf(
            Regex("fallbackTo" + "Destructive" + "Mig" + "ration"),
            Regex("add" + "Mig" + "rations\\s*\\("),
            Regex("Mig" + "ration|MIG" + "RATION"),
            Regex("version\\s*=\\s*(1[0-9]|[2-9])"),
            Regex("tmuxes_database_v(?!1\\b)\\d+"),
            Regex("\\." + "bak\\b"),
            Regex("\\.broken\\."),
            Regex("\\b" + "quar" + "antine\\b", RegexOption.IGNORE_CASE),
            Regex("\\b" + "leg" + "acy\\b", RegexOption.IGNORE_CASE),
            Regex("backward" + "-compatible", RegexOption.IGNORE_CASE),
            Regex("iter-[0-9]+")
        )

        private val SEARCHABLE_TEXT_EXTENSIONS = setOf(
            "kt", "kts", "java", "xml", "md", "properties", "gradle", "yaml", "yml", "sh"
        )

        private val CJK_TEXT = Regex("[\\p{IsHan}]")
        private val DOC_HANDOFF_COMMENT = Regex(
            "docs/|see\\s+docs|see\\s+[^\\s]+\\.md|README\\.md|CHANGELOG\\.md|" +
                "THIRD_PARTY_NOTICES\\.md|NOTICE\\.md|LICENSE\\.md",
            RegexOption.IGNORE_CASE
        )

        private fun commentFragments(line: String): List<String> {
            val trimmed = line.trimStart()
            val fragments = mutableListOf<String>()

            val lineComment = line.indexOf("//")
            if (lineComment >= 0) fragments += line.substring(lineComment)

            if (
                trimmed.startsWith("*") ||
                trimmed.startsWith("/*") ||
                trimmed.startsWith("/**") ||
                trimmed.startsWith("<!--")
            ) {
                fragments += trimmed
            }

            if (trimmed.startsWith("#") && !trimmed.startsWith("#!")) {
                fragments += trimmed
            }

            return fragments
        }

        private fun brand(name: String): Regex =
            Regex("\\b${Regex.escape(name)}\\b", RegexOption.IGNORE_CASE)

        private val REGIONAL_VENDOR_PATTERNS = listOf(
            brand("OP" + "PO"),
            brand("Color" + "OS"),
            brand("Xiao" + "mi"),
            brand("Hua" + "wei"),
            brand("Vi" + "vo"),
            brand("MI" + "UI"),
            brand("EM" + "UI"),
            brand("Funtouch" + "OS"),
            Regex("Chinese\\s+O" + "EMs?", RegexOption.IGNORE_CASE),
            Regex("\u56fd\u5185" + "\u5382\u5546|" + "\u56fd\u4ea7" + "\u673a|" + "\u56fd\u4ea7")
        )

        private val STANDARD_LICENSE_NAMES = listOf(
            "GNU General Public License v3.0 only",
            "GNU Lesser General Public License v2.1 only",
            "Apache License 2.0",
            "Bouncy Castle Licence",
            "MIT License",
            "ISC License",
            "SIL Open Font License 1.1",
            "Eclipse Public License 1.0",
            "Eclipse Public License 2.0"
        )
    }
}

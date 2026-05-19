// allow-bypass-D5: Sora editor calls are best-effort UI updates; failures keep the current editor state.
package com.tmuxes.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.viewinterop.AndroidView
import com.tmuxes.TmuxesApp
import com.tmuxes.data.config.WidgetYamlConfig
import com.tmuxes.data.settings.Settings
import com.tmuxes.editor.EditorFabCluster
import com.tmuxes.editor.EditorKeybar
import com.tmuxes.editor.EditorTheme
import com.tmuxes.editor.ErrorLensDrawable
import com.tmuxes.editor.OutputLogEntry
import com.tmuxes.editor.YamlDiagnosticAnalyzer
import com.tmuxes.editor.YamlEditorSession
import com.tmuxes.editor.YamlEditorTextSync
import com.tmuxes.editor.YamlFileType
import com.tmuxes.i18n.I18nRuntime
import com.tmuxes.i18n.t
import com.tmuxes.ui.components.app.AppDialog
import com.tmuxes.ui.components.app.AppIconButton
import com.tmuxes.ui.components.app.AppIconRole
import com.tmuxes.ui.components.app.AppTextField
import com.tmuxes.ui.design.appTokens
import com.tmuxes.util.safeLaunch
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.ScrollEvent
import io.github.rosemoe.sora.event.TextSizeChangeEvent
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer
import io.github.rosemoe.sora.lang.styling.color.ConstColor
import io.github.rosemoe.sora.lang.styling.line.LineBackground
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import io.github.rosemoe.sora.widget.getComponent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.io.File

/**
 * Built-in YAML text editor powered by Sora Editor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YamlEditorScreen(
    onNavigateBack: () -> Unit,
    widgetId: Int = -1  // -1 = global settings, >=0 = specific widget
) {
    val tokens = MaterialTheme.appTokens
    val context = LocalContext.current
    val app = context.applicationContext as? TmuxesApp
    val preferences = app?.preferences
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val editorPalette = remember(tokens.colors) {
        EditorTheme.Palette(
            wholeBackground = tokens.colors.surface.toArgb(),
            lineNumberBackground = tokens.colors.surfaceContainer.toArgb(),
            currentLine = tokens.colors.surfaceContainerHighest.toArgb(),
            selection = tokens.colors.primaryContainer.toArgb(),
            divider = tokens.colors.divider.toArgb(),
            lineNumber = tokens.colors.onSurfaceVariant.toArgb(),
            lineNumberCurrent = tokens.colors.primary.toArgb(),
            text = tokens.colors.onSurface.toArgb(),
            nonPrintable = tokens.colors.onSurfaceVariant.toArgb(),
            cursor = tokens.colors.primary.toArgb(),
            handle = tokens.colors.secondary.toArgb(),
            underline = tokens.colors.info.toArgb(),
            highlightedDelimiter = tokens.colors.accent.toArgb(),
            completionBackground = tokens.colors.surfaceContainerHigh.toArgb(),
            completionItemCurrent = tokens.colors.surfaceContainerHighest.toArgb(),
            isDark = tokens.colors.isDark
        )
    }

    // ── File selector state ──────────────────────────────────────────────

    /** Represents a file that can be opened in the editor. */
    data class EditorFile(val label: String, val key: String)

    // Scan available files
    val widgetConfigDir = remember { File(context.filesDir, "config/widgets") }
    var availableFiles by remember { mutableStateOf(emptyList<EditorFile>()) }
    val initialFileKey = remember(widgetId) {
        when {
            widgetId == -2 -> "servers"
            widgetId < 0 -> "global"
            else -> "widget_$widgetId"
        }
    }
    var showFileDropdown by remember { mutableStateOf(false) }

    val editorSession = remember(initialFileKey) { YamlEditorSession(initialFileKey) }
    val textSync = remember { YamlEditorTextSync() }
    var editorSnapshot by remember { mutableStateOf(editorSession.snapshot()) }
    val currentFileKey = editorSnapshot.currentFileKey
    val isModified = editorSnapshot.isCurrentFileDirty

    fun scanFiles(): List<EditorFile> {
        // File system I/O — exists(), isDirectory, listFiles() can all
        // throw SecurityException on restricted storage, and listFiles
        // can throw IOException on a device with filesystem quirks.
        // Swallow any failure so the editor opens with at least the two
        // built-in files instead of crashing.
        val files = mutableListOf(EditorFile("global_settings.yaml", "global"))
        files.add(EditorFile("servers.yaml", "servers"))
        files.add(EditorFile("snippets.yaml", "snippets"))
        try {
            val dir = widgetConfigDir
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.filter { it.name.startsWith("widget_") && it.name.endsWith(".yaml") }
                    ?.sortedBy { it.name }
                    ?.forEach { f ->
                        val id = f.nameWithoutExtension.removePrefix("widget_")
                        files.add(EditorFile("widget_$id.yaml", "widget_$id"))
                    }
            }
        } catch (_: Throwable) {
            // Leave files with just the two built-ins.
        }
        return files
    }

    // Initialize file list — LaunchedEffect runs on the main thread in
    // the composition scope; a throw from scanFiles would fail the effect
    // coroutine and propagate to the main thread. Extra guard so
    // LaunchedEffect's own body is also protected against any other
    // unexpected error (e.g. mutable state assignment on a disposed
    // composition during a rapid navigation).
    LaunchedEffect(Unit) {
        try {
            availableFiles = scanFiles()
        } catch (_: Throwable) {}
    }

    fun isGlobalFile(key: String) = key == "global"
    fun fileTypeForKey(key: String): YamlFileType = when (key) {
        "global" -> YamlFileType.GLOBAL
        "servers" -> YamlFileType.SERVERS
        "snippets" -> YamlFileType.SNIPPETS
        else -> YamlFileType.WIDGET
    }

    // ── Editor and overlay references ────────────────────────────────────

    var editorRef by remember { mutableStateOf<CodeEditor?>(null) }
    val errorLensDrawable = remember { ErrorLensDrawable() }
    var appliedEditorTheme by remember { mutableStateOf<String?>(null) }
    var appliedEditorPalette by remember { mutableStateOf<EditorTheme.Palette?>(null) }
    var appliedEditorFontFamily by remember { mutableStateOf<String?>(null) }

    // State
    var showSettings by remember { mutableStateOf(false) }
    var showUnsavedDialog by remember { mutableStateOf(false) }
    var showGoLineDialog by remember { mutableStateOf(false) }
    var isSavingBeforeLeave by remember { mutableStateOf(false) }

    // Auto-save job
    var autoSaveJob by remember { mutableStateOf<Job?>(null) }

    // Guard against preference <-> editor update loop
    var suppressFontSizeSync by remember { mutableStateOf(false) }

    // Diagnostics debounce job
    var diagnosticsJob by remember { mutableStateOf<Job?>(null) }

    // Diagnostics list for OutputPane
    var currentDiagnostics by remember { mutableStateOf<List<YamlDiagnosticAnalyzer.Diagnostic>>(emptyList()) }

    // Output log
    val outputLog = remember { mutableStateListOf<OutputLogEntry>() }

    // Output pane height for drag resize
    // OutputPane height plumbing removed — bottom area is now a Box-anchored
    // FAB cluster (EditorFabCluster) with bubble overlays. State machine
    // for which bubble is open lives inside EditorFabCluster.

    // ── Collect editor preferences ───────────────────────────────────────

    val editorFontSize by (preferences?.flow(Settings.editorFontSize)?.collectAsState(initial = Settings.editorFontSize.default)
        ?: remember { mutableIntStateOf(Settings.editorFontSize.default) })
    val editorFontFamily by (preferences?.flow(Settings.editorFontFamily)?.collectAsState(initial = Settings.editorFontFamily.default)
        ?: remember { mutableStateOf(Settings.editorFontFamily.default) })
    val editorLineHeight by (preferences?.flow(Settings.editorLineHeight)?.collectAsState(initial = Settings.editorLineHeight.default)
        ?: remember { mutableIntStateOf(Settings.editorLineHeight.default) })
    val editorTheme by (preferences?.flow(Settings.editorTheme)?.collectAsState(initial = Settings.editorTheme.default)
        ?: remember { mutableStateOf(Settings.editorTheme.default) })
    val editorShowLineNumbers by (preferences?.flow(Settings.editorShowLineNumbers)?.collectAsState(initial = Settings.editorShowLineNumbers.default)
        ?: remember { mutableStateOf(Settings.editorShowLineNumbers.default) })
    val editorIndentGuides by (preferences?.flow(Settings.editorIndentGuides)?.collectAsState(initial = Settings.editorIndentGuides.default)
        ?: remember { mutableStateOf(Settings.editorIndentGuides.default) })
    val editorWordWrap by (preferences?.flow(Settings.editorWordWrap)?.collectAsState(initial = Settings.editorWordWrap.default)
        ?: remember { mutableStateOf(Settings.editorWordWrap.default) })
    val editorAutoIndent by (preferences?.flow(Settings.editorAutoIndent)?.collectAsState(initial = Settings.editorAutoIndent.default)
        ?: remember { mutableStateOf(Settings.editorAutoIndent.default) })
    val editorAutoComplete by (preferences?.flow(Settings.editorAutoComplete)?.collectAsState(initial = Settings.editorAutoComplete.default)
        ?: remember { mutableStateOf(Settings.editorAutoComplete.default) })
    val editorBracketPairing by (preferences?.flow(Settings.editorBracketPairing)?.collectAsState(initial = Settings.editorBracketPairing.default)
        ?: remember { mutableStateOf(Settings.editorBracketPairing.default) })
    val editorPinchZoom by (preferences?.flow(Settings.editorPinchZoom)?.collectAsState(initial = Settings.editorPinchZoom.default)
        ?: remember { mutableStateOf(Settings.editorPinchZoom.default) })
    val editorReadOnly by (preferences?.flow(Settings.editorReadOnly)?.collectAsState(initial = Settings.editorReadOnly.default)
        ?: remember { mutableStateOf(Settings.editorReadOnly.default) })
    val editorTabWidth by (preferences?.flow(Settings.editorTabWidth)?.collectAsState(initial = Settings.editorTabWidth.default)
        ?: remember { mutableIntStateOf(Settings.editorTabWidth.default) })
    val editorCurrentLineHighlight by (preferences?.flow(Settings.editorCurrentLineHighlight)?.collectAsState(initial = Settings.editorCurrentLineHighlight.default)
        ?: remember { mutableStateOf(Settings.editorCurrentLineHighlight.default) })
    val editorAutoSave by (preferences?.flow(Settings.editorAutoSave)?.collectAsState(initial = Settings.editorAutoSave.default)
        ?: remember { mutableStateOf(Settings.editorAutoSave.default) })
    val editorAutoSaveDelay by (preferences?.flow(Settings.editorAutoSaveDelay)?.collectAsState(initial = Settings.editorAutoSaveDelay.default)
        ?: remember { mutableIntStateOf(Settings.editorAutoSaveDelay.default) })
    val errorLensEnabled by (preferences?.flow(Settings.errorLensEnabled)?.collectAsState(initial = Settings.errorLensEnabled.default)
        ?: remember { mutableStateOf(Settings.errorLensEnabled.default) })
    val errorLensLevel by (preferences?.flow(Settings.errorLensLevel)?.collectAsState(initial = Settings.errorLensLevel.default)
        ?: remember { mutableStateOf(Settings.errorLensLevel.default) })
    val errorLensPosition by (preferences?.flow(Settings.errorLensPosition)?.collectAsState(initial = Settings.errorLensPosition.default)
        ?: remember { mutableStateOf(Settings.errorLensPosition.default) })
    val errorLensFontSize by (preferences?.flow(Settings.errorLensFontSize)?.collectAsState(initial = Settings.errorLensFontSize.default)
        ?: remember { mutableStateOf(Settings.errorLensFontSize.default) })
    val errorLensDebounce by (preferences?.flow(Settings.errorLensDebounce)?.collectAsState(initial = Settings.errorLensDebounce.default)
        ?: remember { mutableIntStateOf(Settings.errorLensDebounce.default) })
    val errorLensBgIntensity by (preferences?.flow(Settings.errorLensBgIntensity)?.collectAsState(initial = Settings.errorLensBgIntensity.default)
        ?: remember { mutableIntStateOf(Settings.errorLensBgIntensity.default) })
    val errorLensTruncation by (preferences?.flow(Settings.errorLensTruncation)?.collectAsState(initial = Settings.errorLensTruncation.default)
        ?: remember { mutableIntStateOf(Settings.errorLensTruncation.default) })
    val errorLensHideWhileTyping by (preferences?.flow(Settings.errorLensHideWhileTyping)?.collectAsState(initial = Settings.errorLensHideWhileTyping.default)
        ?: remember { mutableStateOf(Settings.errorLensHideWhileTyping.default) })

    fun errorLensPositionFor(value: String): ErrorLensDrawable.Position = when (value) {
        "inline" -> ErrorLensDrawable.Position.Inline
        "hover" -> ErrorLensDrawable.Position.HoverOnly
        else -> ErrorLensDrawable.Position.EndOfLine
    }

    fun errorLensFontScaleFor(value: String): Float = when (value) {
        "smaller" -> 0.72f
        "larger" -> 1.0f
        else -> 0.85f
    }

    LaunchedEffect(errorLensPosition, errorLensFontSize) {
        errorLensDrawable.position = errorLensPositionFor(errorLensPosition)
        errorLensDrawable.fontScale = errorLensFontScaleFor(errorLensFontSize)
    }

    // ── Load / Save helpers ──────────────────────────────────────────────

    fun loadYamlForKey(key: String): String {
        return when {
            key == "global" -> app?.preferences?.yamlConfigManager?.getYamlText() ?: ""
            key == "servers" -> app?.serverRepository?.getYamlText() ?: ""
            key == "snippets" -> app?.snippetRepository?.getYamlText() ?: ""
            else -> {
                val id = key.removePrefix("widget_").toIntOrNull() ?: return ""
                WidgetYamlConfig(context).getYamlText(id)
            }
        }
    }

    suspend fun persistYamlForKey(key: String, text: String) {
        when {
            key == "global" -> app?.preferences?.yamlConfigManager?.setFromYamlText(text)
            key == "servers" -> app?.serverRepository?.setFromYamlText(text)
            key == "snippets" -> app?.snippetRepository?.setFromYamlText(text)
            else -> {
                val id = key.removePrefix("widget_").toIntOrNull()
                    ?: error("Invalid widget config key: $key")
                WidgetYamlConfig(context).setFromYamlText(id, text)
            }
        }
    }

    fun refreshSnapshot() {
        editorSnapshot = editorSession.snapshot()
    }

    fun markBufferClean(key: String, text: String) {
        editorSnapshot = editorSession.acceptClean(key, text)
    }

    fun updateBufferText(key: String, text: String) {
        editorSnapshot = editorSession.edit(key, text)
    }

    fun loadBufferText(key: String): String =
        editorSession.load(key) { loadYamlForKey(it) }

    fun changeActionFor(action: Int): YamlEditorTextSync.ChangeAction = when (action) {
        ContentChangeEvent.ACTION_SET_NEW_TEXT -> YamlEditorTextSync.ChangeAction.SetNewText
        ContentChangeEvent.ACTION_INSERT -> YamlEditorTextSync.ChangeAction.Insert
        ContentChangeEvent.ACTION_DELETE -> YamlEditorTextSync.ChangeAction.Delete
        else -> YamlEditorTextSync.ChangeAction.Other
    }

    fun applyEditorText(
        editor: CodeEditor,
        key: String,
        text: String,
        recreateLanguage: Boolean = false
    ) {
        try {
            EditorTheme.bindYamlLanguage(
                editor = editor,
                fileType = fileTypeForKey(key),
                recreate = recreateLanguage
            )
        } catch (_: Throwable) {}

        if (editor.text.toString() != text) {
            val token = textSync.beginReplacement(key, text)
            try {
                editor.setText(text)
            } catch (_: Throwable) {
            } finally {
                textSync.finishReplacementCall(token)
            }
        } else {
            textSync.clear()
        }

        EditorTheme.refreshYamlAnalysis(editor)
        scope.safeLaunch(tag = "YamlEditor.analysis") {
            delay(150)
            EditorTheme.refreshYamlAnalysis(editor)
        }
    }

    fun saveYamlForKey(key: String, text: String, silent: Boolean = false) {
        scope.safeLaunch(tag = "YamlEditor") {
            try {
                persistYamlForKey(key, text)
                markBufferClean(key, text)
                if (!silent) {
                    outputLog.add(OutputLogEntry(message = I18nRuntime.t("Saved {name}", "name" to key)))
                    snackbarHostState.showSnackbar(I18nRuntime.t("Configuration saved"))
                }
            } catch (e: Exception) {
                outputLog.add(OutputLogEntry(message = I18nRuntime.t("Save failed: {error}", "error" to e.message)))
                if (!silent) snackbarHostState.showSnackbar(I18nRuntime.t("Error: {error}", "error" to e.message))
            }
        }
    }

    fun saveCurrentFile() {
        val editor = editorRef ?: return
        val key = editorSession.currentFileKey
        val text = editor.text.toString()
        updateBufferText(key, text)
        saveYamlForKey(key, text)
    }

    fun captureCurrentEditorBuffer() {
        val editor = editorRef ?: return
        updateBufferText(editorSession.currentFileKey, editor.text.toString())
    }

    fun dirtyFileKeys(): List<String> {
        return editorSession.dirtyFileKeys().toList()
    }

    fun requestLeaveEditor() {
        if (isSavingBeforeLeave) return
        captureCurrentEditorBuffer()
        if (dirtyFileKeys().isNotEmpty()) {
            showUnsavedDialog = true
        } else {
            onNavigateBack()
        }
    }

    fun saveModifiedFilesAndLeave() {
        captureCurrentEditorBuffer()
        val keysToSave = dirtyFileKeys()
        if (keysToSave.isEmpty()) {
            showUnsavedDialog = false
            onNavigateBack()
            return
        }

        isSavingBeforeLeave = true
        scope.safeLaunch(tag = "YamlEditor.exit") {
            val failed = mutableListOf<String>()
            keysToSave.forEach { key ->
                val text = editorSession.textFor(key) ?: loadYamlForKey(key)
                try {
                    persistYamlForKey(key, text)
                    markBufferClean(key, text)
                    outputLog.add(OutputLogEntry(message = I18nRuntime.t("Saved {name}", "name" to key)))
                } catch (e: Exception) {
                    failed.add(key)
                    outputLog.add(OutputLogEntry(message = I18nRuntime.t("Save failed for {name}: {error}", "name" to key, "error" to e.message)))
                }
            }

            isSavingBeforeLeave = false
            showUnsavedDialog = false
            if (failed.isEmpty()) {
                onNavigateBack()
            } else {
                snackbarHostState.showSnackbar(
                    I18nRuntime.t("Failed to save {names}", "names" to failed.joinToString())
                )
            }
        }
    }

    // ── Diagnostics ──────────────────────────────────────────────────────

    fun runDiagnostics(editor: CodeEditor) {
        // Sora editor calls (setDiagnostics, styles ops, setSelection,
        // setText) can throw RejectedExecutionException when the
        // internal layout thread pool is saturated. Wrap the whole
        // function body defensively — diagnostics are cosmetic and
        // must never crash the editor screen.
        try {
        val fileType = fileTypeForKey(editorSession.currentFileKey)
        val styles = editor.styles
        if (styles != null) {
            styles.eraseAllLineStyles()
        }

        if (!errorLensEnabled) {
            editor.setDiagnostics(DiagnosticsContainer())
            currentDiagnostics = emptyList()
            errorLensDrawable.setDiagnosticMessages(emptyList())
            editor.invalidate()
            return
        }

        val text = editor.text.toString()
        val results = YamlDiagnosticAnalyzer.analyze(text, fileType)
        val container = DiagnosticsContainer()
        val filteredResults = results.filter { diag ->
            when (errorLensLevel) {
                "error_only" -> diag.severity == YamlDiagnosticAnalyzer.Severity.ERROR
                "error_warning" -> diag.severity == YamlDiagnosticAnalyzer.Severity.ERROR ||
                        diag.severity == YamlDiagnosticAnalyzer.Severity.WARNING
                else -> true
            }
        }

        currentDiagnostics = filteredResults

        val content = editor.text
        val bgAlpha = (errorLensBgIntensity * 255 / 100).coerceIn(0, 255)
        val errorBgColor = (bgAlpha shl 24) or 0x00F38BA8
        val warningBgColor = (bgAlpha shl 24) or 0x00F9E2AF

        for (diag in filteredResults) {
            val line = diag.line.coerceIn(0, content.lineCount - 1)
            val startIdx = content.getCharIndex(line, 0)
            val endIdx = content.getCharIndex(line, content.getColumnCount(line))
            val severity: Short = when (diag.severity) {
                YamlDiagnosticAnalyzer.Severity.ERROR -> DiagnosticRegion.SEVERITY_ERROR
                YamlDiagnosticAnalyzer.Severity.WARNING -> DiagnosticRegion.SEVERITY_WARNING
                else -> DiagnosticRegion.SEVERITY_TYPO
            }

            // Errors are shown through ErrorLens text and line backgrounds.
            container.addDiagnostic(DiagnosticRegion(startIdx, endIdx, severity))

            if (styles != null) {
                val bgColor = when (diag.severity) {
                    YamlDiagnosticAnalyzer.Severity.ERROR -> errorBgColor
                    YamlDiagnosticAnalyzer.Severity.WARNING -> warningBgColor
                    else -> warningBgColor
                }
                styles.addLineStyle(LineBackground(line, ConstColor(bgColor)))
            }
        }

        editor.setDiagnostics(container)

        // Update ErrorLens inline text via ViewOverlay drawable
        val truncLen = errorLensTruncation
        errorLensDrawable.position = errorLensPositionFor(errorLensPosition)
        errorLensDrawable.fontScale = errorLensFontScaleFor(errorLensFontSize)
        errorLensDrawable.setDiagnosticMessages(filteredResults.map { diag ->
            ErrorLensDrawable.InlineDiagnostic(
                line = diag.line.coerceIn(0, content.lineCount - 1),
                message = if (diag.message.length > truncLen) diag.message.take(truncLen) + "..." else diag.message,
                isError = diag.severity == YamlDiagnosticAnalyzer.Severity.ERROR
            )
        })
        editor.invalidate()
        } catch (_: Throwable) {
            // Sora internal crash or recomposition race — diagnostics are cosmetic.
        }
    }

    // ── File switching ───────────────────────────────────────────────────

    fun switchToFile(key: String) {
        if (key == editorSession.currentFileKey) return
        val editor = editorRef ?: return

        val result = editorSession.switchTo(
            fileKey = key,
            currentEditorText = editor.text.toString()
        ) { loadYamlForKey(it) }
        editorSnapshot = result.snapshot
        applyEditorText(
            editor = editor,
            key = result.fileKey,
            text = result.text,
            recreateLanguage = true
        )

        outputLog.add(OutputLogEntry(message = I18nRuntime.t("Switched to {name}", "name" to key)))

        scope.safeLaunch(tag = "YamlEditor") {
            delay(300)
            EditorTheme.refreshYamlAnalysis(editor)
            runDiagnostics(editor)
        }
    }

    // ── Watch for external config changes (global YAML) ────────────────
    // Always listen to configFlow. When the currently viewed file is
    // global and it changes externally (e.g., via Settings GUI), auto-
    // reload the editor if there are no unsaved edits.

    LaunchedEffect(Unit) {
        val manager = app?.preferences?.yamlConfigManager ?: return@LaunchedEffect
        var lastKnownText = manager.getYamlText()
        try {
            manager.configFlow.collect {
                try {
                    val newText = manager.getYamlText()
                    if (newText != lastKnownText) {
                        lastKnownText = newText
                        val editor = editorRef ?: return@collect
                        if (editorSession.currentFileKey != "global") {
                            if (!editorSession.isDirty("global")) markBufferClean("global", newText)
                            return@collect
                        }
                        val editorText = editor.text.toString()
                        if (editorText != newText && !editorSession.isDirty("global")) {
                            markBufferClean("global", newText)
                            applyEditorText(editor, "global", newText, recreateLanguage = true)
                            scope.safeLaunch(tag = "YamlEditor") {
                                delay(300)
                                runDiagnostics(editor)
                            }
                            outputLog.add(OutputLogEntry(message = I18nRuntime.t("Config updated externally")))
                            snackbarHostState.showSnackbar(I18nRuntime.t("Config updated externally"))
                        }
                    }
                } catch (ce: kotlinx.coroutines.CancellationException) {
                    throw ce
                } catch (_: Throwable) {}
            }
        } catch (_: kotlinx.coroutines.CancellationException) {
        } catch (_: Throwable) {}
    }

    // ── Watch for external changes (servers.yaml) ────────────────────────
    LaunchedEffect(Unit) {
        val serverRepo = app?.serverRepository ?: return@LaunchedEffect
        var lastKnownServers = serverRepo.getYamlText()
        try {
            serverRepo.configFlow.collect {
                try {
                    val newText = serverRepo.getYamlText()
                    if (newText != lastKnownServers) {
                        lastKnownServers = newText
                        val editor = editorRef ?: return@collect
                        if (editorSession.currentFileKey != "servers") {
                            if (!editorSession.isDirty("servers")) markBufferClean("servers", newText)
                            return@collect
                        }
                        val editorText = editor.text.toString()
                        if (editorText != newText && !editorSession.isDirty("servers")) {
                            markBufferClean("servers", newText)
                            applyEditorText(editor, "servers", newText, recreateLanguage = true)
                            scope.safeLaunch(tag = "YamlEditor") {
                                delay(300)
                                runDiagnostics(editor)
                            }
                            outputLog.add(OutputLogEntry(message = I18nRuntime.t("Servers config updated externally")))
                            snackbarHostState.showSnackbar(I18nRuntime.t("Servers config updated externally"))
                        }
                    }
                } catch (ce: kotlinx.coroutines.CancellationException) {
                    throw ce
                } catch (_: Throwable) {}
            }
        } catch (_: kotlinx.coroutines.CancellationException) {
        } catch (_: Throwable) {}
    }

    // ── Watch for external changes (snippets.yaml) ───────────────────────
    LaunchedEffect(Unit) {
        val snippetRepo = app?.snippetRepository ?: return@LaunchedEffect
        var lastKnownSnippets = snippetRepo.getYamlText()
        try {
            snippetRepo.config.collect {
                try {
                    val newText = snippetRepo.getYamlText()
                    if (newText != lastKnownSnippets) {
                        lastKnownSnippets = newText
                        val editor = editorRef ?: return@collect
                        if (editorSession.currentFileKey != "snippets") {
                            if (!editorSession.isDirty("snippets")) markBufferClean("snippets", newText)
                            return@collect
                        }
                        val editorText = editor.text.toString()
                        if (editorText != newText && !editorSession.isDirty("snippets")) {
                            markBufferClean("snippets", newText)
                            applyEditorText(editor, "snippets", newText, recreateLanguage = true)
                            scope.safeLaunch(tag = "YamlEditor") {
                                delay(300)
                                runDiagnostics(editor)
                            }
                            outputLog.add(OutputLogEntry(message = I18nRuntime.t("Snippets config updated externally")))
                            snackbarHostState.showSnackbar(I18nRuntime.t("Snippets config updated externally"))
                        }
                    }
                } catch (ce: kotlinx.coroutines.CancellationException) {
                    throw ce
                } catch (_: Throwable) {}
            }
        } catch (_: kotlinx.coroutines.CancellationException) {
        } catch (_: Throwable) {}
    }

    // Release editor on disposal
    DisposableEffect(Unit) {
        onDispose {
            try { editorRef?.release() } catch (_: Throwable) {}
        }
    }

    // ── Display filename for title ───────────────────────────────────────

    val displayFileName = remember(currentFileKey, availableFiles) {
        availableFiles.find { it.key == currentFileKey }?.label
            ?: if (isGlobalFile(currentFileKey)) "global_settings.yaml" else "$currentFileKey.yaml"
    }

    BackHandler(
        enabled = !showUnsavedDialog &&
            !showGoLineDialog &&
            !showSettings &&
            !showFileDropdown &&
            !isSavingBeforeLeave
    ) {
        requestLeaveEditor()
    }

    // ── Dialogs ──────────────────────────────────────────────────────────

    // Unsaved changes dialog. Three actions share the app-wide dialog action row.
    if (showUnsavedDialog) {
        val dirtyCount = dirtyFileKeys().size
        AppDialog(
            title = "Unsaved Changes",
            text = if (dirtyCount > 1) {
                "You have unsaved changes in $dirtyCount files. Save all modified files before leaving?"
            } else {
                "You have unsaved changes. Save before leaving?"
            },
            onDismiss = { if (!isSavingBeforeLeave) showUnsavedDialog = false },
            confirmLabel = when {
                isSavingBeforeLeave -> "Saving..."
                dirtyCount > 1 -> "Save All"
                else -> "Save"
            },
            confirmEnabled = !isSavingBeforeLeave,
            onConfirm = { saveModifiedFilesAndLeave() },
            dismissLabel = "Cancel",
            neutralLabel = "Discard",
            onNeutral = {
                showUnsavedDialog = false
                onNavigateBack()
            },
            neutralEnabled = !isSavingBeforeLeave,
            neutralStyle = com.tmuxes.ui.components.app.AppButtonStyle.Outlined
        )
    }

    // Go to line dialog
    if (showGoLineDialog) {
        var lineInput by remember { mutableStateOf("") }
        AppDialog(
            title = "Go to Line",
            onDismiss = { showGoLineDialog = false },
            confirmLabel = "Go",
            onConfirm = {
                val line = lineInput.toIntOrNull()
                if (line != null && line > 0) {
                    val editor = editorRef
                    if (editor != null) {
                        try {
                            val targetLine = (line - 1).coerceIn(0, editor.text.lineCount - 1)
                            editor.setSelection(targetLine, 0)
                        } catch (_: Throwable) {}
                    }
                }
                showGoLineDialog = false
            },
            content = {
                AppTextField(
                    value = lineInput,
                    onValueChange = { lineInput = it.filter { c -> c.isDigit() } },
                    label = "Line number",
                    keyboardType = KeyboardType.Number,
                    singleLine = true
                )
            }
        )
    }

    // Settings bottom sheet
    if (showSettings && preferences != null) {
        EditorSettingsSheet(onDismiss = { showSettings = false })
    }

    // ── Main Layout ──────────────────────────────────────────────────────

    val dirtyKeysSnapshot = editorSnapshot.dirtyFileKeys
    val hasDirtyFiles = editorSnapshot.hasDirtyFiles

    androidx.compose.material3.Scaffold(
        topBar = {
            // AppTopBar's title slot is String-only; the file-selector
            // dropdown anchored to the title is bespoke, so we keep a
            // raw TopAppBar here but token-drive every color / type.
            androidx.compose.material3.TopAppBar(
                title = {
                    // Clickable file title with dropdown
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { showFileDropdown = true }
                        ) {
                            Column {
                                Text(displayFileName, style = tokens.type.titleMedium)
                                Text(
                                    text = when {
                                        isModified && editorAutoSave -> t("Modified (Auto-saving)")
                                        isModified -> t("Modified")
                                        hasDirtyFiles -> t("Unsaved changes")
                                        else -> t("Saved")
                                    },
                                    style = tokens.type.labelSmall,
                                    color = when {
                                        isModified || hasDirtyFiles -> tokens.colors.error
                                        else -> tokens.colors.onSurfaceVariant
                                    }
                                )
                            }
                            Icon(
                                Icons.Filled.ArrowDropDown,
                                contentDescription = t("Select file"),
                                tint = tokens.colors.onSurfaceVariant
                            )
                        }

                        DropdownMenu(
                            expanded = showFileDropdown,
                            onDismissRequest = { showFileDropdown = false }
                        ) {
                            availableFiles.forEach { file ->
                                val fileModified = file.key in dirtyKeysSnapshot ||
                                    (file.key == currentFileKey && isModified)
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = file.label + if (fileModified) " *" else "",
                                            style = tokens.type.bodyMedium,
                                            color = if (file.key == currentFileKey)
                                                tokens.colors.primary
                                            else
                                                tokens.colors.onSurface
                                        )
                                    },
                                    onClick = {
                                        showFileDropdown = false
                                        switchToFile(file.key)
                                    }
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    AppIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        onClick = { requestLeaveEditor() },
                        contentDescription = "Back"
                    )
                },
                actions = {
                    AppIconButton(
                        icon = Icons.Filled.Settings,
                        onClick = { showSettings = true },
                        contentDescription = "Settings",
                        role = AppIconRole.OnSurfaceVariant
                    )
                    AppIconButton(
                        icon = Icons.Filled.Save,
                        onClick = { saveCurrentFile() },
                        enabled = isModified,
                        contentDescription = "Save",
                        role = if (isModified) AppIconRole.Primary else AppIconRole.OnSurfaceVariant
                    )
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = tokens.colors.surface,
                    navigationIconContentColor = tokens.colors.onSurface,
                    titleContentColor = tokens.colors.onSurface,
                    actionIconContentColor = tokens.colors.onSurface
                )
            )
        },
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        containerColor = tokens.colors.background,
        // Don't consume IME insets — let the inner Column's imePadding() handle it
        // so the function keys move up with the keyboard (matching terminal behavior)
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            // ── Editor area: CodeEditor + FAB cluster overlay ──────────
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            AndroidView(
                factory = { ctx ->
                    EditorTheme.initialize(ctx)

                    val initialKey = editorSession.currentFileKey
                    val codeEditor = CodeEditor(ctx).apply {
                        EditorTheme.configureYamlEditor(
                            editor = this,
                            editorTheme = editorTheme,
                            appPalette = editorPalette,
                            fontFamily = editorFontFamily,
                            fileType = fileTypeForKey(initialKey)
                        )
                        appliedEditorTheme = editorTheme
                        appliedEditorPalette = editorPalette
                        appliedEditorFontFamily = editorFontFamily

                        val initialText = loadBufferText(initialKey)
                        applyEditorText(this, initialKey, initialText)

                        // Listen for text changes
                        subscribeAlways(ContentChangeEvent::class.java) contentChange@{
                            val editorText = this@apply.text.toString()
                            val action = changeActionFor(it.action)
                            val key = editorSession.currentFileKey
                            if (!textSync.shouldRecordAsUserEdit(key, action, editorText)) {
                                refreshSnapshot()
                                EditorTheme.refreshYamlAnalysis(this@apply)
                                return@contentChange
                            }

                            updateBufferText(key, editorText)
                            if (errorLensHideWhileTyping) {
                                errorLensDrawable.setDiagnosticMessages(emptyList())
                            }

                            // Debounced diagnostics
                            diagnosticsJob?.cancel()
                            diagnosticsJob = scope.safeLaunch(tag = "YamlEditor") {
                                delay(errorLensDebounce.toLong())
                                runDiagnostics(this@apply)
                            }

                            // Auto-save
                            if (editorAutoSave) {
                                autoSaveJob?.cancel()
                                autoSaveJob = scope.safeLaunch(tag = "YamlEditor") {
                                    delay(editorAutoSaveDelay.toLong() * 1000)
                                    saveCurrentFile()
                                }
                            }
                        }

                        // Sync pinch-zoom to preference in real-time.
                        // Uses Dispatchers.Main.immediate so the preference
                        // writes synchronously within the same frame, and
                        // suppressFontSizeSync properly guards the loop.
                        subscribeAlways(TextSizeChangeEvent::class.java) {
                            if (!suppressFontSizeSync) {
                                val newSizePx = it.newTextSize
                                val scaledDensity = (
                                    resources.displayMetrics.density *
                                        resources.configuration.fontScale
                                ).coerceAtLeast(0.01f)
                                val newSizeSp = newSizePx / scaledDensity
                                val rounded = newSizeSp.toInt().coerceIn(6, 36)
                                suppressFontSizeSync = true
                                scope.safeLaunch(context = kotlinx.coroutines.Dispatchers.Main.immediate, tag = "YamlEditor.fontSize") {
                                    preferences?.set(Settings.editorFontSize, rounded)
                                    suppressFontSizeSync = false
                                }
                            }
                            errorLensDrawable.invalidateSelf()
                        }

                        // Initial diagnostics
                        scope.safeLaunch(tag = "YamlEditor") {
                            delay(500)
                            runDiagnostics(this@apply)
                        }

                        editorRef = this
                    }

                    // ErrorLens inline text via ViewOverlay — draws on top of
                    // the editor WITHOUT intercepting touch events.
                    errorLensDrawable.editor = codeEditor
                    errorLensDrawable.setBounds(0, 0, codeEditor.width, codeEditor.height)
                    codeEditor.overlay.add(errorLensDrawable)

                    // Update drawable bounds when editor resizes (e.g., IME show/hide)
                    codeEditor.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
                        errorLensDrawable.setBounds(0, 0, right - left, bottom - top)
                        errorLensDrawable.invalidateSelf()
                    }

                    // Repaint ErrorLens on scroll
                    codeEditor.subscribeAlways(ScrollEvent::class.java) {
                        errorLensDrawable.invalidateSelf()
                        codeEditor.invalidate()
                    }

                    codeEditor
                },
                update = { _ ->
                    // Only apply settings that actually changed to avoid
                    // recomposition storms (setTextSize triggers TextSizeChangeEvent
                    // which writes preference which triggers recomposition).
                    //
                    // Sora layout calls can reject work when its internal
                    // executor is saturated, so each setting is applied
                    // independently.
                    val editor = editorRef ?: return@AndroidView
                    try {
                        val resources = editor.context.resources
                        val scaledDensity = (
                            resources.displayMetrics.density *
                                resources.configuration.fontScale
                        ).coerceAtLeast(0.01f)
                        val targetSizePx = editorFontSize.toFloat() * scaledDensity
                        if (kotlin.math.abs(editor.textSizePx - targetSizePx) > 1f) {
                            suppressFontSizeSync = true
                            try { editor.setTextSize(editorFontSize.toFloat()) } catch (_: Throwable) {}
                            suppressFontSizeSync = false
                        }
                    } catch (_: Throwable) {
                        suppressFontSizeSync = false
                    }
                    try {
                        if (appliedEditorTheme != editorTheme || appliedEditorPalette != editorPalette) {
                            EditorTheme.applyPalette(editor, editorTheme, editorPalette)
                            EditorTheme.bindYamlLanguage(
                                editor = editor,
                                fileType = fileTypeForKey(editorSession.currentFileKey),
                                recreate = true
                            )
                            EditorTheme.refreshYamlAnalysis(editor)
                            appliedEditorTheme = editorTheme
                            appliedEditorPalette = editorPalette
                        }
                    } catch (_: Throwable) {}
                    try {
                        if (appliedEditorFontFamily != editorFontFamily) {
                            EditorTheme.applyTypeface(editor, editorFontFamily)
                            appliedEditorFontFamily = editorFontFamily
                        }
                    } catch (_: Throwable) {}
                    try {
                        val targetLineSpacing = (editorLineHeight / 100f).coerceIn(1f, 2f)
                        if (kotlin.math.abs(editor.lineSpacingMultiplier - targetLineSpacing) > 0.01f) {
                            editor.setLineSpacing(2f, targetLineSpacing)
                        }
                    } catch (_: Throwable) {}
                    try { editor.setLineNumberEnabled(editorShowLineNumbers) } catch (_: Throwable) {}
                    try { editor.setWordwrap(editorWordWrap) } catch (_: Throwable) {}
                    try { editor.props.autoIndent = editorAutoIndent } catch (_: Throwable) {}
                    try { editor.setBlockLineEnabled(editorIndentGuides) } catch (_: Throwable) {}
                    try { editor.props.drawSideBlockLine = editorIndentGuides } catch (_: Throwable) {}
                    try {
                        val completion = editor.getComponent(EditorAutoCompletion::class.java)
                        completion.setEnabled(editorAutoComplete)
                        if (!editorAutoComplete) editor.hideAutoCompleteWindow()
                    } catch (_: Throwable) {}
                    try { editor.props.symbolPairAutoCompletion = editorBracketPairing } catch (_: Throwable) {}
                    try { editor.setScalable(editorPinchZoom) } catch (_: Throwable) {}
                    try { editor.isEditable = !editorReadOnly } catch (_: Throwable) {}
                    try { editor.setTabWidth(editorTabWidth) } catch (_: Throwable) {}
                    try { editor.setHighlightCurrentLine(editorCurrentLineHighlight) } catch (_: Throwable) {}
                },
                modifier = Modifier.fillMaxSize()
            )

            // ── FAB cluster overlays the editor only (NOT the keybar) ─
            EditorFabCluster(
                diagnostics = currentDiagnostics,
                outputLog = outputLog,
                editor = editorRef,
                onDiagnosticClick = { line ->
                    try { editorRef?.setSelection(line, 0) } catch (_: Throwable) {}
                }
            )
            } // end editor Box

            // ── EditorKeybar (always at bottom) ──────────────────────────
            EditorKeybar(
                editor = editorRef,
                onGoLine = { showGoLineDialog = true }
            )
        }
    }
}

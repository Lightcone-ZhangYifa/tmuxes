package com.tmuxes.editor

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.tmuxes.R
import com.tmuxes.util.AppLogger
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.dsl.languages
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import io.github.rosemoe.sora.widget.getComponent
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import org.eclipse.tm4e.core.registry.IThemeSource

/**
 * Configures the Sora editor instance used by the YAML editor screen.
 */
object EditorTheme {
    private var initialized = false

    data class Palette(
        val wholeBackground: Int,
        val lineNumberBackground: Int,
        val currentLine: Int,
        val selection: Int,
        val divider: Int,
        val lineNumber: Int,
        val lineNumberCurrent: Int,
        val text: Int,
        val nonPrintable: Int,
        val cursor: Int,
        val handle: Int,
        val underline: Int,
        val highlightedDelimiter: Int,
        val completionBackground: Int,
        val completionItemCurrent: Int,
        val isDark: Boolean
    )

    val catppuccinMochaPalette = Palette(
        wholeBackground = 0xFF1E1E2E.toInt(),
        lineNumberBackground = 0xFF181825.toInt(),
        currentLine = 0xFF313244.toInt(),
        selection = 0xFF45475A.toInt(),
        divider = 0xFF585B70.toInt(),
        lineNumber = 0xFF6C7086.toInt(),
        lineNumberCurrent = 0xFF7F849C.toInt(),
        text = 0xFFCDD6F4.toInt(),
        nonPrintable = 0xFFA6ADC8.toInt(),
        cursor = 0xFFF5E0DC.toInt(),
        handle = 0xFFCBA6F7.toInt(),
        underline = 0xFF89B4FA.toInt(),
        highlightedDelimiter = 0xFFB4BEFE.toInt(),
        completionBackground = 0xFF181825.toInt(),
        completionItemCurrent = 0xFF313244.toInt(),
        isDark = true
    )

    val catppuccinLattePalette = Palette(
        wholeBackground = 0xFFEFF1F5.toInt(),
        lineNumberBackground = 0xFFE6E9EF.toInt(),
        currentLine = 0xFFCCD0DA.toInt(),
        selection = 0xFFBCC0CC.toInt(),
        divider = 0xFFACB0BE.toInt(),
        lineNumber = 0xFF9CA0B0.toInt(),
        lineNumberCurrent = 0xFF7C7F93.toInt(),
        text = 0xFF4C4F69.toInt(),
        nonPrintable = 0xFF6C6F85.toInt(),
        cursor = 0xFFDC8A78.toInt(),
        handle = 0xFF8839EF.toInt(),
        underline = 0xFF1E66F5.toInt(),
        highlightedDelimiter = 0xFF7287FD.toInt(),
        completionBackground = 0xFFE6E9EF.toInt(),
        completionItemCurrent = 0xFFDCE0E8.toInt(),
        isDark = false
    )

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        try {
            FileProviderRegistry.getInstance().addFileProvider(
                AssetsFileResolver(context.applicationContext.assets)
            )

            loadTheme("textmate/catppuccin-mocha.json", "catppuccin-mocha", isDark = true)
            loadTheme("textmate/catppuccin-latte.json", "catppuccin-latte", isDark = false)
            ThemeRegistry.getInstance().setTheme("catppuccin-mocha")

            // Load classic TextMate YAML grammar (plist XML format)
            GrammarRegistry.getInstance().loadGrammars(
                languages {
                    language("yaml") {
                        grammar = "textmate/yaml.tmLanguage"
                        defaultScopeName() // source.yaml
                    }
                }
            )
            AppLogger.i(AppLogger.Category.EDITOR) { "EditorTheme initialized" }
            initialized = true
        } catch (e: Exception) {
            AppLogger.e(AppLogger.Category.EDITOR, e) { "EditorTheme initialization failed" }
        }
    }

    private fun loadTheme(path: String, name: String, isDark: Boolean) {
        val themeSource = IThemeSource.fromInputStream(
            FileProviderRegistry.getInstance().tryGetInputStream(path),
            path,
            null
        )
        val themeModel = ThemeModel(themeSource, name).apply { this.isDark = isDark }
        ThemeRegistry.getInstance().loadTheme(themeModel)
    }

    fun applyToEditor(
        editor: CodeEditor,
        editorTheme: String = "catppuccin_mocha",
        appPalette: Palette = catppuccinMochaPalette,
        fontFamily: String = "jetbrains_mono"
    ) {
        configureYamlEditor(
            editor = editor,
            editorTheme = editorTheme,
            appPalette = appPalette,
            fontFamily = fontFamily,
            fileType = YamlFileType.GLOBAL
        )
    }

    fun configureYamlEditor(
        editor: CodeEditor,
        editorTheme: String,
        appPalette: Palette,
        fontFamily: String,
        fileType: YamlFileType
    ) {
        try {
            applyPalette(editor, editorTheme, appPalette)
            applyTypeface(editor, fontFamily)
            bindYamlLanguage(editor, fileType, recreate = true)
            applyEditorDefaults(editor)
            configureCompletionPopup(editor)
            refreshYamlAnalysis(editor)
            AppLogger.i(AppLogger.Category.EDITOR) { "Configured YAML editor" }
        } catch (e: Exception) {
            AppLogger.e(AppLogger.Category.EDITOR, e) { "Failed to configure YAML editor" }
        }
    }

    fun ensureYamlLanguage(editor: CodeEditor): TextMateLanguage? {
        return bindYamlLanguage(editor, YamlFileType.GLOBAL, recreate = false)
    }

    fun bindYamlLanguage(
        editor: CodeEditor,
        fileType: YamlFileType,
        recreate: Boolean = false
    ): TextMateLanguage? {
        return try {
            val existing = editor.editorLanguage
            val language = if (!recreate && existing is TextMateLanguage) {
                existing
            } else {
                TextMateLanguage.create("source.yaml", true).also { language ->
                    editor.setEditorLanguage(language)
                }
            }
            language.tabSize = 2
            language.useTab(false)
            language.setCompleterKeywords(keywordsFor(fileType))
            language
        } catch (e: Exception) {
            AppLogger.e(AppLogger.Category.EDITOR, e) { "Failed to bind YAML TextMate language" }
            null
        }
    }

    fun refreshYamlAnalysis(editor: CodeEditor) {
        try {
            editor.rerunAnalysis()
            editor.invalidate()
        } catch (e: Exception) {
            AppLogger.e(AppLogger.Category.EDITOR, e) { "Failed to refresh YAML analysis" }
        }
    }

    private fun keywordsFor(fileType: YamlFileType): Array<String> {
        return buildList {
            addAll(YamlSchema.sectionNames(fileType))
            for (section in YamlSchema.sectionNames(fileType)) {
                addAll(YamlSchema.childrenAtFlat(section, fileType).map { it.name })
            }
        }.toTypedArray()
    }

    private fun applyEditorDefaults(editor: CodeEditor) {
        editor.setLineSpacing(2f, 1.2f)
        editor.setHighlightCurrentLine(true)
        editor.setLineNumberEnabled(true)
        editor.setDividerWidth(1f)
        editor.props.deleteMultiSpaces = -1
        editor.props.deleteEmptyLineFast = false
    }

    private fun configureCompletionPopup(editor: CodeEditor) {
        try {
            val completion = editor.getComponent(EditorAutoCompletion::class.java)
            completion.setMaxHeight(
                (editor.context.resources.displayMetrics.heightPixels * 0.3).toInt()
            )
            completion.setCompletionWndPositionMode(EditorAutoCompletion.WINDOW_POS_MODE_AUTO)
            completion.setEnabledAnimation(true)
        } catch (_: Exception) {
            // allow-bypass-D5: completion popup tuning is optional.
        }
    }

    fun applyPalette(
        editor: CodeEditor,
        editorTheme: String,
        appPalette: Palette
    ) {
        val palette = if (editorTheme == "catppuccin_mocha") catppuccinMochaPalette else appPalette
        val textMateTheme = if (editorTheme == "catppuccin_mocha" || palette.isDark) {
            "catppuccin-mocha"
        } else {
            "catppuccin-latte"
        }

        ThemeRegistry.getInstance().setTheme(textMateTheme)
        val colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())

        colorScheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, palette.wholeBackground)
        colorScheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, palette.lineNumberBackground)
        colorScheme.setColor(EditorColorScheme.LINE_NUMBER, palette.lineNumber)
        colorScheme.setColor(EditorColorScheme.LINE_NUMBER_CURRENT, palette.lineNumberCurrent)
        colorScheme.setColor(EditorColorScheme.LINE_NUMBER_PANEL, palette.lineNumberBackground)
        colorScheme.setColor(EditorColorScheme.LINE_NUMBER_PANEL_TEXT, palette.lineNumberCurrent)
        colorScheme.setColor(EditorColorScheme.LINE_DIVIDER, palette.divider)
        colorScheme.setColor(EditorColorScheme.CURRENT_LINE, palette.currentLine)
        colorScheme.setColor(EditorColorScheme.SELECTION_INSERT, palette.cursor)
        colorScheme.setColor(EditorColorScheme.SELECTION_HANDLE, palette.handle)
        colorScheme.setColor(EditorColorScheme.TEXT_NORMAL, palette.text)
        colorScheme.setColor(EditorColorScheme.UNDERLINE, palette.underline)
        colorScheme.setColor(EditorColorScheme.BLOCK_LINE, palette.divider)
        colorScheme.setColor(EditorColorScheme.BLOCK_LINE_CURRENT, palette.lineNumber)
        colorScheme.setColor(EditorColorScheme.SIDE_BLOCK_LINE, palette.divider)
        colorScheme.setColor(EditorColorScheme.NON_PRINTABLE_CHAR, palette.nonPrintable)
        colorScheme.setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_UNDERLINE, palette.highlightedDelimiter)
        colorScheme.setColor(EditorColorScheme.COMPLETION_WND_BACKGROUND, palette.completionBackground)
        colorScheme.setColor(EditorColorScheme.COMPLETION_WND_TEXT_PRIMARY, palette.text)
        colorScheme.setColor(EditorColorScheme.COMPLETION_WND_TEXT_SECONDARY, palette.lineNumber)
        colorScheme.setColor(EditorColorScheme.COMPLETION_WND_ITEM_CURRENT, palette.completionItemCurrent)
        colorScheme.setColor(EditorColorScheme.COMPLETION_WND_CORNER, palette.divider)

        editor.colorScheme = colorScheme
    }

    fun applyTypeface(editor: CodeEditor, fontFamily: String) {
        val typeface = typefaceFor(editor.context, fontFamily)
        editor.typefaceText = typeface
        editor.typefaceLineNumber = typeface
    }

    fun typefaceFor(context: Context, fontFamily: String): Typeface = when (fontFamily) {
        "monospace" -> Typeface.MONOSPACE
        "sans_serif" -> Typeface.SANS_SERIF
        "serif" -> Typeface.SERIF
        else -> try {
            ResourcesCompat.getFont(context, R.font.jetbrains_mono) ?: Typeface.MONOSPACE
        } catch (_: Exception) {
            Typeface.MONOSPACE
        }
    }
}

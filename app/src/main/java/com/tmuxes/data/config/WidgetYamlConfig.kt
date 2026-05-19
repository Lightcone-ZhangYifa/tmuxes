package com.tmuxes.data.config

import android.content.Context
import com.tmuxes.data.settings.Settings
import com.tmuxes.util.AppLogger
import com.tmuxes.util.ColorHex
import com.tmuxes.widget.TerminalWidget.Companion.WidgetConfig
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.StringWriter

/**
 * Per-widget YAML configuration storage.
 *
 * Each widget instance gets its own file: `widget_{appWidgetId}.yaml`
 * stored in `context.filesDir/config/widgets/`.
 *
 * The YAML format uses human-readable grouped sections:
 * session, widget, terminal.
 */
class WidgetYamlConfig(private val context: Context) {

    companion object {
        private const val TAG = "tmuxes.CONFIG"
    }

    private val configDir = File(context.filesDir, "config/widgets")

    private val yaml: Yaml
        get() {
            val options = DumperOptions().apply {
                defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
                isPrettyFlow = true
                indent = 2
                indicatorIndent = 0
                width = 120
            }
            return Yaml(options)
        }

    init {
        configDir.mkdirs()
    }

    private fun fileFor(appWidgetId: Int) = File(configDir, "widget_$appWidgetId.yaml")
    private fun tempFor(appWidgetId: Int) = File(configDir, "widget_$appWidgetId.yaml.tmp")

    // ---------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------

    private fun parseConfigFile(file: File): WidgetConfig? {
        val text = file.readText()
        if (text.isBlank()) return null
        val parsed = yaml.load<Any>(text)
        return if (parsed is Map<*, *>) {
            @Suppress("UNCHECKED_CAST")
            mapToConfig(parsed as Map<String, Any?>)
        } else {
            null
        }
    }

    fun getConfig(appWidgetId: Int): WidgetConfig {
        val file = fileFor(appWidgetId)
        if (!file.exists()) return WidgetConfig()
        return parseConfigFile(file) ?: WidgetConfig()
    }

    fun saveConfig(appWidgetId: Int, config: WidgetConfig) {
        try {
            configDir.mkdirs()
            val map = configToMap(config)
            val writer = StringWriter()
            yaml.dump(map, writer)
            val text = addComments(writer.toString(), appWidgetId)
            val target = fileFor(appWidgetId)
            val temp = tempFor(appWidgetId)
            temp.writeText(text)
            if (!temp.renameTo(target)) {
                target.writeText(text)
                temp.delete()
            }
        } catch (e: Exception) {
            AppLogger.e(AppLogger.Category.CONFIG, e) { "Failed to save widget config for $appWidgetId" }
        }
    }

    fun removeConfig(appWidgetId: Int) {
        try {
            fileFor(appWidgetId).delete()
            tempFor(appWidgetId).delete()
        } catch (e: Exception) {
            AppLogger.e(AppLogger.Category.CONFIG, e) { "Failed to remove widget config for $appWidgetId" }
        }
    }

    fun getYamlText(appWidgetId: Int): String {
        return try {
            val file = fileFor(appWidgetId)
            if (file.exists()) file.readText() else ""
        } catch (e: Exception) {
            AppLogger.e(AppLogger.Category.CONFIG, e) { "Failed to read widget YAML for $appWidgetId" }
            ""
        }
    }

    fun setFromYamlText(appWidgetId: Int, text: String) {
        try {
            val parsed = yaml.load<Any>(text)
            if (parsed is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                saveConfig(appWidgetId, mapToConfig(parsed as Map<String, Any?>))
            } else {
                throw IllegalArgumentException("Invalid widget YAML: root is not a map")
            }
        } catch (e: Exception) {
            AppLogger.e(AppLogger.Category.CONFIG, e) { "Failed to parse widget YAML for $appWidgetId" }
            throw e
        }
    }

    // ---------------------------------------------------------------
    // WidgetConfig <-> Map conversion
    // ---------------------------------------------------------------

    private fun configToMap(config: WidgetConfig): LinkedHashMap<String, Any?> {
        val root = LinkedHashMap<String, Any?>()

        val session = LinkedHashMap<String, Any?>()
        session["server_id"] = config.serverId
        session["session_name"] = config.sessionName
        root["session"] = session

        val widget = LinkedHashMap<String, Any?>()
        widget["opacity"] = config.opacity
        widget["show_title_bar"] = config.showTitleBar
        widget["orientation"] = config.orientation
        widget["title_accent_color"] = ColorHex.toYamlString(config.titleAccentColor)
        root["widget"] = widget

        val terminal = LinkedHashMap<String, Any?>()
        terminal["font_family"] = config.fontFamily
        terminal["font_size"] = config.fontSize.toDouble()
        terminal["font_weight"] = config.fontWeight
        terminal["color_scheme"] = config.colorScheme
        terminal["cursor_style"] = config.cursorStyle
        terminal["cursor_blink"] = config.cursorBlink
        terminal["cursor_color"] = ColorHex.toYamlString(config.cursorColor)
        terminal["background_opacity"] = config.backgroundOpacity
        terminal["bold_is_bright"] = config.boldIsBright
        terminal["underline_style"] = config.underlineStyle
        terminal["line_spacing"] = config.lineSpacing
        terminal["padding"] = config.terminalPadding
        terminal["scrollback_lines"] = config.scrollbackLines
        root["terminal"] = terminal

        return root
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapToConfig(map: Map<String, Any?>): WidgetConfig {
        val session = (map["session"] as? Map<String, Any?>) ?: emptyMap()
        val widget = (map["widget"] as? Map<String, Any?>) ?: emptyMap()
        val terminal = (map["terminal"] as? Map<String, Any?>) ?: emptyMap()

        return WidgetConfig(
            serverId = (session["server_id"] as? Number)?.toLong() ?: 0L,
            sessionName = (session["session_name"] as? String) ?: "",
            opacity = (widget["opacity"] as? Number)?.toInt() ?: 100,
            showTitleBar = (widget["show_title_bar"] as? Boolean) ?: true,
            orientation = (widget["orientation"] as? Number)?.toInt() ?: 0,
            titleAccentColor = ColorHex.parse(widget["title_accent_color"]) ?: 0x604FC3F7.toInt(),
            fontFamily = (terminal["font_family"] as? String) ?: Settings.terminalFontFamily.default,
            fontSize = (terminal["font_size"] as? Number)?.toFloat() ?: 0f,
            fontWeight = (terminal["font_weight"] as? String) ?: Settings.terminalFontWeight.default,
            colorScheme = (terminal["color_scheme"] as? String) ?: Settings.terminalColorScheme.default,
            cursorStyle = (terminal["cursor_style"] as? String) ?: Settings.terminalCursorStyle.default,
            cursorBlink = (terminal["cursor_blink"] as? Boolean) ?: Settings.terminalCursorBlink.default,
            cursorColor = ColorHex.parse(terminal["cursor_color"]) ?: Settings.terminalCursorColor.default,
            backgroundOpacity = (terminal["background_opacity"] as? Number)?.toInt() ?: Settings.terminalBackgroundOpacity.default,
            boldIsBright = (terminal["bold_is_bright"] as? Boolean) ?: Settings.terminalBoldIsBright.default,
            underlineStyle = (terminal["underline_style"] as? String) ?: Settings.terminalUnderlineStyle.default,
            lineSpacing = (terminal["line_spacing"] as? Number)?.toInt() ?: Settings.terminalLineSpacing.default,
            terminalPadding = (terminal["padding"] as? Number)?.toInt() ?: Settings.terminalPadding.default,
            scrollbackLines = (terminal["scrollback_lines"] as? Number)?.toInt() ?: Settings.terminalScrollbackLines.default
        )
    }

    // ---------------------------------------------------------------
    // YAML comment injection
    // ---------------------------------------------------------------

    private fun addComments(yamlText: String, appWidgetId: Int): String {
        val sb = StringBuilder()
        sb.appendLine("# Widget Configuration (ID: $appWidgetId)")
        sb.appendLine()
        sb.append(yamlText)
        return sb.toString().trimEnd() + "\n"
    }
}

package com.tmuxes.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import com.tmuxes.TmuxesApp
import com.tmuxes.data.config.WidgetYamlConfig
import com.tmuxes.data.settings.Settings

/**
 * Home screen terminal tile widget that renders a real terminal preview
 * as a [android.graphics.Bitmap] in an [android.widget.ImageView].
 *
 * Uses the same Canvas-based rendering pipeline as the in-app terminal:
 * per-cell ANSI colors, bold/italic/underline attributes, cursor rendering,
 * JetBrains Mono font, and color scheme remapping.
 *
 * Shares live [com.tmuxes.terminal.emulator.TerminalEmulator] instances
 * with the in-app terminal via [com.tmuxes.session.SessionCoordinator].
 * [WidgetSessionManager] registers content listeners on the emulator and
 * renders bitmaps in real-time via [TerminalBitmapRenderer].
 *
 * This produces widgets that look identical to the in-app terminal.
 */
open class TerminalWidget : AppWidgetProvider() {

    companion object {
        private val ALL_PROVIDERS: List<Class<out TerminalWidget>> = listOf(
            TerminalWidget::class.java,
            TerminalWidgetSmall::class.java,
            TerminalWidgetMedium::class.java,
            TerminalWidgetLarge::class.java
        )

        // -----------------------------------------------------------------
        // Per-widget config data class
        // -----------------------------------------------------------------

        /**
         * All per-widget configuration.
         * Empty strings mean "use global app setting".
         * fontSize 0 means "auto-compute from widget size".
         */
        data class WidgetConfig(
            // Session binding
            val serverId: Long = 0,
            val sessionName: String = "",
            // Widget-specific
            val opacity: Int = 100,
            val showTitleBar: Boolean = true,
            val orientation: Int = 0,
            // Terminal appearance (independent per widget)
            val fontFamily: String = Settings.terminalFontFamily.default,
            val fontSize: Float = 0f,
            val fontWeight: String = Settings.terminalFontWeight.default,
            val colorScheme: String = Settings.terminalColorScheme.default,
            val cursorStyle: String = Settings.terminalCursorStyle.default,
            // Static home-screen widgets cannot animate cursor blinking reliably.
            // This value is therefore used as cursor visibility in widget UI.
            val cursorBlink: Boolean = Settings.terminalCursorBlink.default,
            val cursorColor: Int = Settings.terminalCursorColor.default,
            val titleAccentColor: Int = 0x604FC3F7.toInt(),
            val backgroundOpacity: Int = Settings.terminalBackgroundOpacity.default,
            val boldIsBright: Boolean = Settings.terminalBoldIsBright.default,
            val underlineStyle: String = Settings.terminalUnderlineStyle.default,
            val lineSpacing: Int = Settings.terminalLineSpacing.default,
            val terminalPadding: Int = Settings.terminalPadding.default,
            val scrollbackLines: Int = Settings.terminalScrollbackLines.default
        )

        // -----------------------------------------------------------------
        // Config read/write (YAML-backed)
        // -----------------------------------------------------------------

        fun getConfig(context: Context, appWidgetId: Int): WidgetConfig {
            return WidgetYamlConfig(context).getConfig(appWidgetId)
        }

        fun saveConfig(context: Context, appWidgetId: Int, config: WidgetConfig) {
            WidgetYamlConfig(context).saveConfig(appWidgetId, config)
        }

        fun removeBinding(context: Context, appWidgetId: Int) {
            WidgetYamlConfig(context).removeConfig(appWidgetId)
        }

        /** Get all widget bindings as (appWidgetId -> serverId, sessionName) */
        fun getAllBindings(context: Context): Map<Int, Pair<Long, String>> {
            val mgr = AppWidgetManager.getInstance(context)
            val result = mutableMapOf<Int, Pair<Long, String>>()
            for (providerClass in ALL_PROVIDERS) {
                val ids = mgr.getAppWidgetIds(ComponentName(context, providerClass))
                for (id in ids) {
                    val config = getConfig(context, id)
                    val serverId = config.serverId
                    val sessionName = config.sessionName
                    if (serverId > 0 && sessionName.isNotBlank()) {
                        result[id] = serverId to sessionName
                    }
                }
            }
            return result
        }

        /** Update all widget instances. Delegates to WidgetSessionManager if available. */
        fun updateAll(context: Context) {
            try {
                val app = context.applicationContext as? TmuxesApp
                val manager = app?.widgetSessionManager
                if (manager != null) {
                    manager.renderAllWidgets()
                }
            } catch (_: Throwable) {
                // Widget rendering must never crash the host process.
            }
        }

    }

    /**
     * AppWidgetProvider's default [onReceive] dispatches to [onUpdate] /
     * [onDeleted] / [onAppWidgetOptionsChanged] etc. based on the intent
     * action and extras. A malformed broadcast (wrong extras type, missing
     * action) can still reach us through custom broadcasts or adb, and the
     * default dispatcher reads extras via [android.os.Bundle.getBundle] /
     * [android.os.Bundle.getIntArray] which can return null even when the
     * parent's declared types are non-null. Wrap the whole dispatch so a
     * parameter-nullability throw (Kotlin's `Parameter specified as
     * non-null is null` synthetic NPE) cannot bring down the host.
     */
    override fun onReceive(context: Context, intent: android.content.Intent) {
        try {
            super.onReceive(context, intent)
        } catch (_: Throwable) {
            // Intentionally swallow — widget dispatch must never crash the
            // hosting process. The next scheduled update will retry.
        }
    }

    override fun onUpdate(context: Context, mgr: AppWidgetManager, appWidgetIds: IntArray?) {
        // Widget provider callbacks run on the main thread — any uncaught
        // exception here kills the process. Guard every call path.
        try { updateAll(context) } catch (t: Throwable) {
            com.tmuxes.util.AppLogger.w(com.tmuxes.util.AppLogger.Category.RENDER) {
                "widget.onUpdate ✗ cause='${t.message}' — next AppWidgetProvider tick will retry"
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, mgr: AppWidgetManager, appWidgetId: Int, newOptions: Bundle?
    ) {
        try {
            val app = context.applicationContext as? TmuxesApp
            val manager = app?.widgetSessionManager
            if (manager != null) {
                manager.onWidgetOptionsChanged(appWidgetId)
            } else {
                updateAll(context)
            }
        } catch (_: Throwable) {}
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray?) {
        try {
            val app = context.applicationContext as? TmuxesApp
            val ids = appWidgetIds ?: return
            for (id in ids) {
                try { app?.widgetSessionManager?.onWidgetRemoved(id) } catch (_: Throwable) {} // allow-bypass-D5: widget cleanup
                try { removeBinding(context, id) } catch (_: Throwable) {} // allow-bypass-D5: widget cleanup
            }
        } catch (_: Throwable) {}
    }
}

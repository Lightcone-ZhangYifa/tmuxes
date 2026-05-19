package com.tmuxes.launcher

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import com.tmuxes.ui.design.AppColorPalette
import com.tmuxes.ui.design.ThemeAccents
import com.tmuxes.util.AppLogger
import com.tmuxes.util.AppLogger.Category

object LauncherIconController {
    private const val DEFAULT_DARK_ALIAS = "LauncherIconDefaultDark"
    private const val DEFAULT_LIGHT_ALIAS = "LauncherIconDefaultLight"
    private val lock = Any()
    private var pendingAlias: String? = null

    private val customFamilies = setOf(
        "Blue",
        "Violet",
        "Cyan",
        "Teal",
        "Green",
        "Rose",
        "Orange",
        "Amber"
    )

    private val aliases = buildList {
        add(DEFAULT_DARK_ALIAS)
        add(DEFAULT_LIGHT_ALIAS)
        customFamilies.forEach { family ->
            add("LauncherIcon${family}Dark")
            add("LauncherIcon${family}Light")
        }
    }

    fun request(
        context: Context,
        themeMode: String,
        paletteKey: String,
        accentArgb: Int
    ) {
        val appContext = context.applicationContext
        val targetAlias = resolveAlias(appContext, themeMode, paletteKey, accentArgb)
        try {
            val launcherStateIsClean = isLauncherStateClean(
                packageManager = appContext.packageManager,
                packageName = appContext.packageName,
                targetAlias = targetAlias
            )
            synchronized(lock) {
                pendingAlias = if (launcherStateIsClean) null else targetAlias
            }
        } catch (t: Throwable) {
            synchronized(lock) { pendingAlias = targetAlias }
            AppLogger.w(Category.LIFECYCLE) {
                "LauncherIconController: queued launcher icon alias=$targetAlias after state read failed cause='${t.message}'"
            }
        }
    }

    fun applyPending(context: Context): Boolean {
        val targetAlias = synchronized(lock) { pendingAlias } ?: return false
        val applied = applyAlias(context.applicationContext, targetAlias)
        if (applied) {
            synchronized(lock) {
                if (pendingAlias == targetAlias) pendingAlias = null
            }
        }
        return applied
    }

    private fun applyAlias(context: Context, targetAlias: String): Boolean {
        val packageManager = context.packageManager
        val packageName = context.packageName
        return try {
            if (isLauncherStateClean(packageManager, packageName, targetAlias)) {
                true
            } else {
                setAliasEnabled(packageManager, packageName, targetAlias, enabled = true)
                aliases.filter { it != targetAlias }.forEach { alias ->
                    setAliasEnabled(packageManager, packageName, alias, enabled = false)
                }
                AppLogger.i(Category.LIFECYCLE) {
                    "LauncherIconController: applied launcher icon alias=$targetAlias"
                }
                true
            }
        } catch (t: Throwable) {
            AppLogger.w(Category.LIFECYCLE) {
                "LauncherIconController: failed to apply launcher icon alias=$targetAlias cause='${t.message}'"
            }
            false
        }
    }

    private fun resolveAlias(
        context: Context,
        themeMode: String,
        paletteKey: String,
        accentArgb: Int
    ): String {
        val suffix = if (isDarkTheme(context, themeMode)) "Dark" else "Light"
        return when (AppColorPalette.fromKey(paletteKey)) {
            AppColorPalette.Custom -> {
                val family = ThemeAccents.selectedOption(accentArgb).label
                    .takeIf { it in customFamilies }
                    ?: "Blue"
                "LauncherIcon$family$suffix"
            }
            /*
             * Launcher icons are package resources, so they cannot consume the
             * runtime Material You wallpaper palette. Dynamic color falls back
             * to the default app mark for the active light/dark mode.
             */
            AppColorPalette.Default,
            AppColorPalette.MaterialYou -> "LauncherIconDefault$suffix"
        }
    }

    private fun isDarkTheme(context: Context, themeMode: String): Boolean =
        when (themeMode) {
            "light" -> false
            "dark" -> true
            else -> {
                val nightMode = context.resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK
                nightMode == Configuration.UI_MODE_NIGHT_YES
            }
        }

    private fun setAliasEnabled(
        packageManager: PackageManager,
        packageName: String,
        alias: String,
        enabled: Boolean
    ) {
        val component = ComponentName(packageName, "$packageName.$alias")
        val current = packageManager.getComponentEnabledSetting(component)
        if (isAlreadyInState(alias, current, enabled)) return

        packageManager.setComponentEnabledSetting(
            component,
            if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            },
            PackageManager.DONT_KILL_APP
        )
    }

    private fun isLauncherStateClean(
        packageManager: PackageManager,
        packageName: String,
        targetAlias: String
    ): Boolean =
        aliases.all { alias ->
            val current = packageManager.getComponentEnabledSetting(
                ComponentName(packageName, "$packageName.$alias")
            )
            isAlreadyInState(alias, current, enabled = alias == targetAlias)
        }

    private fun isAlreadyInState(alias: String, current: Int, enabled: Boolean): Boolean =
        if (enabled) {
            current == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                (alias == DEFAULT_DARK_ALIAS && current == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
        } else {
            current == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                (alias != DEFAULT_DARK_ALIAS && current == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
        }
}

package com.tmuxes.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tmuxes.TmuxesApp
import com.tmuxes.data.preferences.AppPreferences
import com.tmuxes.data.settings.Setting
import com.tmuxes.util.AppLogger
import com.tmuxes.util.safeLaunch

/**
 * Settings ViewModel — a thin write-side wrapper that takes any registered
 * [Setting] and persists a value through [AppPreferences].
 *
 * Reads go through `preferences.flow(setting).collectAsState(initial = setting.default)`
 * directly inside Composables — no need to project every setting onto a
 * named [kotlinx.coroutines.flow.StateFlow] in the ViewModel just to call
 * `collectAsState()` on it. The previous AppPreferences exposed ~40 typed
 * Flows and this ViewModel re-exposed each as a typed StateFlow with a
 * matching setter wrapper; that hand-typed plumbing is gone.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as TmuxesApp
    val preferences: AppPreferences = app.preferences

    /** Persist a setting value on the viewModel scope. */
    fun <T : Any> set(setting: Setting<T>, value: T) {
        viewModelScope.safeLaunch(tag = "SettingsVM") {
            try {
                preferences.set(setting, value)
            } catch (e: Throwable) {
                AppLogger.w(AppLogger.Category.UI) { "SettingsVM: failed to write ${setting.key.flatPath}: ${e.message}" }
            }
        }
    }
}

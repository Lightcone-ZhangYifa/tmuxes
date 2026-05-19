package com.tmuxes.ui.screens.settings

import androidx.compose.runtime.Composable
import com.tmuxes.data.settings.SettingScreens
import com.tmuxes.ui.settings.SettingScreenScaffold

@Composable
fun ShellSessionScreen(onNavigateBack: () -> Unit) {
    SettingScreenScaffold(SettingScreens.shellSession, onNavigateBack)
}

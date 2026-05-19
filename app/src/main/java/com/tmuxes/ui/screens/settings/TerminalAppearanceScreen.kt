package com.tmuxes.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tmuxes.data.settings.SettingGroup
import com.tmuxes.data.settings.SettingItem
import com.tmuxes.data.settings.SettingScreens
import com.tmuxes.data.settings.Settings
import com.tmuxes.terminal.emulator.TerminalColors
import com.tmuxes.ui.settings.SettingScreenScaffold
import com.tmuxes.ui.viewmodel.SettingsViewModel

@Composable
fun TerminalAppearanceScreen(
    onNavigateBack: () -> Unit,
    onEditCustomScheme: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel()
) {
    val currentScheme by viewModel.preferences.flow(Settings.terminalColorScheme)
        .collectAsState(initial = Settings.terminalColorScheme.default)
    val customSchemesJson by viewModel.preferences.flow(Settings.terminalCustomSchemes)
        .collectAsState(initial = Settings.terminalCustomSchemes.default)
    val customSchemes = TerminalColors.getCustomSchemes(customSchemesJson)
    val spec = SettingScreens.terminalAppearance.copy(
        groups = SettingScreens.terminalAppearance.groups.map { group ->
            if (group.title != "Color") {
                group
            } else {
                SettingGroup(
                    title = group.title,
                    items = listOf(
                        SettingItem.Custom {
                            TerminalColorSchemeSelector(
                                currentScheme = currentScheme,
                                customSchemes = customSchemes,
                                onSchemeChange = { viewModel.set(Settings.terminalColorScheme, it) },
                                onEditCustom = onEditCustomScheme
                            )
                        }
                    )
                )
            }
        }
    )

    SettingScreenScaffold(spec, onNavigateBack)
}

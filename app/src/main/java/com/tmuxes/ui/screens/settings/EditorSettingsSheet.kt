package com.tmuxes.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import com.tmuxes.ui.components.app.appElasticVerticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tmuxes.data.settings.SettingScreens
import com.tmuxes.ui.components.app.AppBottomSheet
import com.tmuxes.ui.components.app.AppSpacerSize
import com.tmuxes.ui.components.app.AppVerticalSpacer
import com.tmuxes.ui.design.appTokens
import com.tmuxes.ui.settings.SettingGroupsRenderer

/**
 * Modal bottom sheet shown by the YAML editor when the user taps the
 * gear icon. Renders the same registered editor settings as
 * [SettingScreens.editor]; reads/writes go through the registry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorSettingsSheet(onDismiss: () -> Unit) {
    val tokens = MaterialTheme.appTokens
    AppBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .appElasticVerticalScroll(rememberScrollState())
        ) {
            Text(
                text = SettingScreens.editor.title,
                style = tokens.type.titleLarge,
                color = tokens.colors.onSurface,
                modifier = Modifier.padding(start = tokens.space.xs, top = tokens.space.xs)
            )
            SettingGroupsRenderer(SettingScreens.editor)
            AppVerticalSpacer(AppSpacerSize.Xl)
        }
    }
}

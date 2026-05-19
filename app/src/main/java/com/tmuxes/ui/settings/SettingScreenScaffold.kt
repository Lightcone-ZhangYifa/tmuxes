package com.tmuxes.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import com.tmuxes.ui.components.app.appElasticVerticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tmuxes.data.settings.Setting
import com.tmuxes.data.settings.SettingItem
import com.tmuxes.data.settings.SettingScreenSpec
import com.tmuxes.ui.components.app.AppCard
import com.tmuxes.ui.components.app.AppScaffold
import com.tmuxes.ui.components.app.AppSectionHeader
import com.tmuxes.ui.components.app.AppVerticalSpacer
import com.tmuxes.ui.components.app.AppSpacerSize
import com.tmuxes.ui.components.app.rememberAppEntryScrollState
import com.tmuxes.ui.design.appTokens
import androidx.compose.foundation.layout.PaddingValues

/**
 * Generic settings screen — takes a [SettingScreenSpec] and renders
 * every group / item from the registry through token-driven App
 * components.
 *
 * Custom-rendered items (color scheme picker, etc.) declare
 * themselves as [SettingItem.Custom] in the spec — they receive the
 * same card slot but render their own content.
 */
@Composable
fun SettingScreenScaffold(
    spec: SettingScreenSpec,
    onNavigateBack: () -> Unit
) {
    val tokens = MaterialTheme.appTokens
    val scrollState = rememberAppEntryScrollState(spec.id)
    AppScaffold(
        title = spec.title,
        onBack = onNavigateBack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .appElasticVerticalScroll(scrollState)
                .padding(horizontal = tokens.space.lg),
            verticalArrangement = Arrangement.spacedBy(tokens.space.sm)
        ) {
            SettingGroupsRenderer(spec)
            AppVerticalSpacer(AppSpacerSize.Lg)
        }
    }
}

/**
 * Just the groups + cards, without a Scaffold. Used by [AppScaffold]
 * (above) and by the YAML editor's modal bottom sheet, which doesn't
 * want a nested top-bar.
 */
@Composable
fun SettingGroupsRenderer(spec: SettingScreenSpec) {
    val tokens = MaterialTheme.appTokens
    spec.groups.forEach { group ->
        AppSectionHeader(text = group.title)
        AppCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(tokens.space.xs)
        ) {
            Column {
                group.items.forEach { item ->
                    when (item) {
                        is SettingItem.Reg -> {
                            @Suppress("UNCHECKED_CAST")
                            SettingItemRenderer(item.setting as Setting<Any>)
                        }
                        is SettingItem.Custom -> item.render()
                    }
                }
            }
        }
    }
}

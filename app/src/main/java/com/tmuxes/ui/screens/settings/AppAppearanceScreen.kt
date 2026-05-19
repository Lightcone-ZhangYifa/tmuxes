package com.tmuxes.ui.screens.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tmuxes.data.settings.Setting
import com.tmuxes.data.settings.SettingGroup
import com.tmuxes.data.settings.SettingItem
import com.tmuxes.data.settings.SettingScreenSpec
import com.tmuxes.data.settings.SettingScreens
import com.tmuxes.data.settings.Settings
import com.tmuxes.i18n.t
import com.tmuxes.ui.components.app.AppCard
import com.tmuxes.ui.components.app.AppScaffold
import com.tmuxes.ui.components.app.AppSectionHeader
import com.tmuxes.ui.components.app.AppSpacerSize
import com.tmuxes.ui.components.app.AppVerticalSpacer
import com.tmuxes.ui.components.app.appElasticVerticalScroll
import com.tmuxes.ui.components.app.appPressable
import com.tmuxes.ui.components.app.rememberAppEntryScrollState
import com.tmuxes.ui.design.AppColorPalette
import com.tmuxes.ui.design.ThemeAccentOption
import com.tmuxes.ui.design.ThemeAccents
import com.tmuxes.ui.design.appTokens
import com.tmuxes.ui.settings.SettingGroupsRenderer
import com.tmuxes.ui.settings.SettingItemRenderer
import com.tmuxes.ui.viewmodel.SettingsViewModel

@Composable
fun AppAppearanceScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val tokens = MaterialTheme.appTokens
    val theme by viewModel.preferences.flow(Settings.theme)
        .collectAsState(initial = Settings.theme.default)
    val palette by viewModel.preferences.flow(Settings.appColorPalette)
        .collectAsState(initial = Settings.appColorPalette.default)
    val accentColor by viewModel.preferences.flow(Settings.appAccentColor)
        .collectAsState(initial = Settings.appAccentColor.default)
    val remainingSettings = remember {
        SettingScreens.appAppearance.withoutSettings(
            Settings.appLanguage,
            Settings.theme,
            Settings.appColorPalette,
            Settings.appAccentColor
        )
    }
    val scrollState = rememberAppEntryScrollState(SettingScreens.appAppearance.id)

    AppScaffold(
        title = SettingScreens.appAppearance.title,
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
            AppSectionHeader("Language")
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(tokens.space.xs)
            ) {
                SettingItemRenderer(Settings.appLanguage, viewModel = viewModel)
            }

            AppSectionHeader("Theme")
            AppCard(modifier = Modifier.fillMaxWidth()) {
                ThemeModeSelector(
                    currentTheme = theme,
                    onThemeChange = { viewModel.set(Settings.theme, it) }
                )
            }

            AppSectionHeader("Color")
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(tokens.space.lg)) {
                    PaletteSelector(
                        currentPalette = palette,
                        accentColor = accentColor,
                        onPaletteChange = { selected ->
                            viewModel.set(Settings.appColorPalette, selected)
                        },
                        onCustomPalette = {
                            viewModel.set(Settings.appColorPalette, "custom")
                        }
                    )
                    if (palette == "custom") {
                        AccentFamilyPicker(
                            currentAccent = accentColor,
                            onAccentChange = { selected ->
                                viewModel.set(Settings.appColorPalette, "custom")
                                viewModel.set(Settings.appAccentColor, selected)
                            }
                        )
                    }
                }
            }

            SettingGroupsRenderer(remainingSettings)
            AppVerticalSpacer(AppSpacerSize.Lg)
        }
    }
}

@Composable
private fun ThemeModeSelector(
    currentTheme: String,
    onThemeChange: (String) -> Unit
) {
    val options = listOf(
        AppearanceChoice("dark", "Dark", Icons.Filled.DarkMode),
        AppearanceChoice("light", "Light", Icons.Filled.LightMode),
        AppearanceChoice("system", "System", Icons.Filled.SettingsBrightness)
    )
    ChoiceRow(
        options = options,
        currentValue = currentTheme,
        onSelect = onThemeChange
    )
}

@Composable
private fun PaletteSelector(
    currentPalette: String,
    accentColor: Int,
    onPaletteChange: (String) -> Unit,
    onCustomPalette: () -> Unit
) {
    val tokens = MaterialTheme.appTokens
    val supportsDynamic = Build.VERSION.SDK_INT >= 31
    val defaultAccent = ThemeAccents.selectedOption(0)
    val customAccent = ThemeAccents.selectedOption(accentColor)
    val currentPaletteFamily = AppColorPalette.fromKey(currentPalette)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(tokens.space.sm)
    ) {
        PaletteChoiceTile(
            title = "Default",
            icon = Icons.Filled.Palette,
            selected = currentPaletteFamily == AppColorPalette.Default,
            enabled = true,
            modifier = Modifier.weight(1f),
            onClick = { onPaletteChange("default") },
            preview = { AccentPairSwatch(defaultAccent.darkArgb, defaultAccent.lightArgb) }
        )
        PaletteChoiceTile(
            title = "Dynamic",
            icon = Icons.Filled.SettingsBrightness,
            selected = currentPalette == "material_you",
            enabled = supportsDynamic,
            modifier = Modifier.weight(1f),
            onClick = { onPaletteChange("material_you") }
        )
        PaletteChoiceTile(
            title = "Custom",
            icon = Icons.Filled.ColorLens,
            selected = currentPalette == "custom",
            enabled = true,
            modifier = Modifier.weight(1f),
            onClick = onCustomPalette,
            preview = { AccentPairSwatch(customAccent.darkArgb, customAccent.lightArgb) }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentFamilyPicker(
    currentAccent: Int,
    onAccentChange: (Int) -> Unit
) {
    val tokens = MaterialTheme.appTokens
    val selected = ThemeAccents.selectedOption(currentAccent)
    Column(verticalArrangement = Arrangement.spacedBy(tokens.space.sm)) {
        Text(
            text = t("Color"),
            style = tokens.type.titleSmall,
            color = tokens.colors.onSurface
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(tokens.space.sm),
            verticalArrangement = Arrangement.spacedBy(tokens.space.sm)
        ) {
            ThemeAccents.presets.forEach { option ->
                AccentFamilyTile(
                    option = option,
                    selected = option.argb == selected.argb,
                    onClick = { onAccentChange(option.argb) }
                )
            }
        }
    }
}

@Composable
private fun ChoiceRow(
    options: List<AppearanceChoice>,
    currentValue: String,
    onSelect: (String) -> Unit
) {
    val tokens = MaterialTheme.appTokens
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(tokens.space.sm)
    ) {
        options.forEach { option ->
            ChoiceTile(
                title = option.title,
                icon = option.icon,
                selected = currentValue == option.value,
                enabled = true,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(option.value) }
            )
        }
    }
}

@Composable
private fun ChoiceTile(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val tokens = MaterialTheme.appTokens
    val container = if (selected) tokens.colors.primaryContainer else tokens.colors.surfaceContainerHigh
    val content = if (selected) tokens.colors.onPrimaryContainer else tokens.colors.onSurfaceVariant
    Box(
        modifier = modifier
            .heightIn(min = 72.dpUnit())
            .clip(tokens.shape.md)
            .background(container)
            .border(
                width = 1.dpUnit(),
                color = if (selected) tokens.colors.primary else tokens.colors.outlineVariant,
                shape = tokens.shape.md
            )
            .alpha(if (enabled) 1f else 0.45f)
            .appPressable(enabled = enabled, onClick = onClick)
            .padding(tokens.space.md),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(tokens.space.xs)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(20.dpUnit())
            )
            Text(
                text = t(title),
                style = tokens.type.labelMedium,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PaletteChoiceTile(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    preview: (@Composable () -> Unit)? = null
) {
    val tokens = MaterialTheme.appTokens
    val container = if (selected) tokens.colors.primaryContainer else tokens.colors.surfaceContainerHigh
    val content = if (selected) tokens.colors.onPrimaryContainer else tokens.colors.onSurfaceVariant
    Box(
        modifier = modifier
            .heightIn(min = 88.dpUnit())
            .clip(tokens.shape.md)
            .background(container)
            .border(
                width = 1.dpUnit(),
                color = if (selected) tokens.colors.primary else tokens.colors.outlineVariant,
                shape = tokens.shape.md
            )
            .alpha(if (enabled) 1f else 0.45f)
            .appPressable(enabled = enabled, onClick = onClick)
            .padding(tokens.space.md)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(20.dpUnit())
            )
            Text(
                text = t(title),
                style = tokens.type.labelMedium,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (preview != null) {
                preview()
            } else {
                Spacer(Modifier.height(18.dpUnit()))
            }
        }
    }
}

@Composable
private fun AccentFamilyTile(
    option: ThemeAccentOption,
    selected: Boolean,
    onClick: () -> Unit
) {
    val tokens = MaterialTheme.appTokens
    val container = if (selected) tokens.colors.primaryContainer else tokens.colors.surfaceContainerHigh
    val content = if (selected) tokens.colors.onPrimaryContainer else tokens.colors.onSurface
    Row(
        modifier = Modifier
            .widthIn(min = 122.dpUnit())
            .heightIn(min = 44.dpUnit())
            .clip(tokens.shape.md)
            .background(container)
            .border(
                width = 1.dpUnit(),
                color = if (selected) tokens.colors.primary else tokens.colors.outlineVariant,
                shape = tokens.shape.md
            )
            .appPressable(onClick = onClick)
            .padding(horizontal = tokens.space.sm, vertical = tokens.space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AccentPairSwatch(option.darkArgb, option.lightArgb)
        Spacer(Modifier.width(tokens.space.sm))
        Text(
            text = t(option.label),
            style = tokens.type.labelMedium,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(16.dpUnit())
            )
        }
    }
}

@Composable
private fun AccentPairSwatch(
    darkArgb: Int,
    lightArgb: Int,
    modifier: Modifier = Modifier
) {
    val tokens = MaterialTheme.appTokens
    Row(
        modifier = modifier
            .width(44.dpUnit())
            .height(18.dpUnit())
            .clip(tokens.shape.xs)
            .border(1.dpUnit(), tokens.colors.outlineVariant, tokens.shape.xs)
    ) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color(darkArgb))
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color(lightArgb))
        )
    }
}

private data class AppearanceChoice(
    val value: String,
    val title: String,
    val icon: ImageVector
)

private fun SettingScreenSpec.withoutSettings(
    vararg settings: Setting<*>
): SettingScreenSpec {
    val hidden = settings.toSet()
    val remainingGroups = groups.mapNotNull { group ->
        val remainingItems = group.items.filterNot { item ->
            item is SettingItem.Reg && item.setting in hidden
        }
        if (remainingItems.isEmpty()) null else SettingGroup(group.title, remainingItems)
    }
    return copy(groups = remainingGroups)
}

private fun Int.dpUnit() = androidx.compose.ui.unit.Dp(this.toFloat())

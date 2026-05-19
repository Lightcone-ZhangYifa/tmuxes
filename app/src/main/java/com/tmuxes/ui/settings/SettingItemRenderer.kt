package com.tmuxes.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tmuxes.data.settings.BooleanSetting
import com.tmuxes.data.settings.ColorSetting
import com.tmuxes.data.settings.FloatSetting
import com.tmuxes.data.settings.IntEnumSetting
import com.tmuxes.data.settings.IntSetting
import com.tmuxes.data.settings.LongSetting
import com.tmuxes.data.settings.Setting
import com.tmuxes.data.settings.StringEnumSetting
import com.tmuxes.data.settings.StringSetSetting
import com.tmuxes.data.settings.StringSetting
import com.tmuxes.i18n.t
import com.tmuxes.ui.components.app.AppSlider
import com.tmuxes.ui.components.app.AppSwitch
import com.tmuxes.ui.components.app.AppTextField
import com.tmuxes.ui.design.ThemeAccents
import com.tmuxes.ui.design.appTokens
import com.tmuxes.ui.viewmodel.SettingsViewModel
import kotlin.math.roundToInt

/**
 * Render any registered [Setting] in the user-facing settings UI by
 * picking the appropriate App component from its runtime type. All
 * styling reads from `LocalAppTokens` — zero hardcoded colors, sizes
 * or spacings.
 *
 * - [BooleanSetting] → [AppSwitch] row
 * - [IntSetting] with a range → [AppSlider] row; without → numeric [AppTextField]
 * - [StringEnumSetting] / [IntEnumSetting] → token-styled dropdown row
 * - [StringSetting] → [AppTextField] row
 * - [ColorSetting] → preset color chip strip
 * - [LongSetting] / [FloatSetting] / [StringSetSetting] → no UI
 */
@Composable
fun <T : Any> SettingItemRenderer(
    setting: Setting<T>,
    viewModel: SettingsViewModel = viewModel()
) {
    val value by viewModel.preferences.flow(setting).collectAsState(initial = setting.default)

    SettingValueRenderer(
        setting = setting,
        value = value,
        onValueChange = { viewModel.set(setting, it) }
    )
}

/**
 * Render a registered setting against a caller-owned value. This keeps the
 * setting metadata (title, description, icon, range, enum options) reusable
 * outside global preferences, notably server overrides and per-widget
 * terminal settings.
 */
@Composable
fun <T : Any> SettingValueRenderer(
    setting: Setting<T>,
    value: T,
    onValueChange: (T) -> Unit,
    titleOverride: String? = null,
    descriptionOverride: String? = null
) {
    val baseUi = setting.ui ?: return
    val ui = baseUi.copy(
        title = titleOverride ?: baseUi.title,
        description = descriptionOverride ?: baseUi.description
    )
    val icon = SettingIcons[ui.iconId]

    when (setting) {
        is BooleanSetting -> SwitchRow(
            ui = ui, icon = icon,
            checked = value as Boolean,
            onCheckedChange = { onValueChange(it.asSettingValue()) }
        )
        is IntSetting -> if (setting.range != null) SliderRow(
            ui = ui, icon = icon,
            value = value as Int, range = setting.range,
            onValueChange = { onValueChange(it.asSettingValue()) }
        ) else IntFieldRow(
            ui = ui, icon = icon,
            value = value as Int,
            onValueChange = { onValueChange(it.asSettingValue()) }
        )
        is StringEnumSetting -> StringDropdownRow(
            ui = ui, icon = icon,
            currentValue = value as String,
            options = setting.options.map { it.value to t(it.label) },
            onValueChange = { onValueChange(it.asSettingValue()) }
        )
        is IntEnumSetting -> IntDropdownRow(
            ui = ui, icon = icon,
            currentValue = value as Int,
            options = setting.options.map { it.value to t(it.label) },
            onValueChange = { onValueChange(it.asSettingValue()) }
        )
        is StringSetting -> TextFieldRow(
            ui = ui, icon = icon,
            value = value as String,
            multiline = (ui.placeholder?.contains('\n') == true) ||
                (ui.description?.contains("per line", ignoreCase = true) == true),
            onValueChange = { onValueChange(it.asSettingValue()) }
        )
        is ColorSetting -> ColorChipsRow(
            ui = ui, icon = icon,
            currentColor = value as Int,
            allowDefault = setting.default == 0,
            onColorChange = { onValueChange(it.asSettingValue()) }
        )
        is FloatSetting,
        is LongSetting,
        is StringSetSetting -> Unit
    }
}

@Suppress("UNCHECKED_CAST")
private fun <T : Any> Any.asSettingValue(): T = this as T

/**
 * Render an optional per-entity override for a registered setting. `null`
 * means "inherit the global value"; a non-null value is saved on the owning
 * entity. This is intentionally generic so server pages can expose every SSH
 * override without rebuilding one-off controls.
 */
@Composable
fun <T : Any> NullableSettingOverrideRenderer(
    setting: Setting<T>,
    value: T?,
    onValueChange: (T?) -> Unit,
    inheritDescription: String = "Using global default"
) {
    val baseUi = setting.ui ?: return
    val tokens = MaterialTheme.appTokens
    val icon = SettingIcons[baseUi.iconId]
    val custom = value != null
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(tokens.shape.md)
                .clickable {
                    onValueChange(if (custom) null else value ?: setting.default)
                }
                .padding(horizontal = tokens.space.lg, vertical = tokens.space.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingHeader(
                ui = baseUi.copy(
                    description = if (custom) "Custom override" else inheritDescription
                ),
                icon = icon
            ) {
                AppSwitch(
                    checked = custom,
                    onCheckedChange = { checked ->
                        onValueChange(if (checked) value ?: setting.default else null)
                    }
                )
            }
        }

        if (custom) {
            SettingValueRenderer(
                setting = setting,
                value = value ?: setting.default,
                onValueChange = { onValueChange(it) }
            )
        }
    }
}

// =============================================================================
// Internal control rows — all token-driven, no hardcoded styling
// =============================================================================

@Composable
private fun SettingHeader(
    ui: com.tmuxes.data.settings.SettingUi,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    trailing: (@Composable () -> Unit)? = null
) {
    val tokens = MaterialTheme.appTokens
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon, contentDescription = null,
            tint = tokens.colors.onSurfaceVariant,
            modifier = Modifier.padding(end = tokens.space.lg)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(t(ui.title), style = tokens.type.bodyLarge, color = tokens.colors.onSurface)
            ui.description?.let {
                Text(t(it), style = tokens.type.bodySmall, color = tokens.colors.onSurfaceVariant)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(tokens.space.md))
            trailing()
        }
    }
}

@Composable
private fun SwitchRow(
    ui: com.tmuxes.data.settings.SettingUi,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val tokens = MaterialTheme.appTokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(tokens.shape.md)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = tokens.space.lg, vertical = tokens.space.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingHeader(ui, icon) {
            AppSwitch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun SliderRow(
    ui: com.tmuxes.data.settings.SettingUi,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit
) {
    val tokens = MaterialTheme.appTokens
    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = tokens.space.lg, vertical = tokens.space.md)
    ) {
        SettingHeader(ui, icon) {
                Text(
                    text = "$value${ui.unit ?: ""}",
                style = tokens.type.monoSmall,
                color = tokens.colors.primary
            )
        }
        Spacer(Modifier.height(tokens.space.xs))
        val steps = (range.last - range.first - 1).coerceAtLeast(0)
        AppSlider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt().coerceIn(range)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = if (steps in 1..30) steps else 0
        )
    }
}

@Composable
private fun IntFieldRow(
    ui: com.tmuxes.data.settings.SettingUi,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    val tokens = MaterialTheme.appTokens
    var text by remember(value) { mutableStateOf(value.toString()) }
    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = tokens.space.lg, vertical = tokens.space.md)
    ) {
        SettingHeader(ui, icon)
        Spacer(Modifier.height(tokens.space.sm))
        AppTextField(
            value = text,
            onValueChange = { newText ->
                text = newText
                newText.toIntOrNull()?.let(onValueChange)
            },
            modifier = Modifier.fillMaxWidth(),
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
        )
    }
}

@Composable
private fun StringDropdownRow(
    ui: com.tmuxes.data.settings.SettingUi,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    currentValue: String,
    options: List<Pair<String, String>>,
    onValueChange: (String) -> Unit
) {
    val tokens = MaterialTheme.appTokens
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(tokens.shape.md)
                .clickable { expanded = true }
                .padding(horizontal = tokens.space.lg, vertical = tokens.space.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon, contentDescription = null,
                tint = tokens.colors.onSurfaceVariant,
                modifier = Modifier.padding(end = tokens.space.lg)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(t(ui.title), style = tokens.type.bodyLarge, color = tokens.colors.onSurface)
                Text(
                    text = options.find { it.first == currentValue }?.second ?: currentValue,
                    style = tokens.type.bodySmall,
                    color = tokens.colors.onSurfaceVariant
                )
                ui.description?.let {
                    Text(t(it), style = tokens.type.bodySmall, color = tokens.colors.onSurfaceVariant)
                }
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(label, style = tokens.type.bodyMedium)
                            if (value == currentValue) {
                                Spacer(Modifier.width(tokens.space.sm))
                                Icon(
                                    Icons.Filled.Check, contentDescription = null,
                                    tint = tokens.colors.primary
                                )
                            }
                        }
                    },
                    onClick = { onValueChange(value); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun IntDropdownRow(
    ui: com.tmuxes.data.settings.SettingUi,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    currentValue: Int,
    options: List<Pair<Int, String>>,
    onValueChange: (Int) -> Unit
) {
    val tokens = MaterialTheme.appTokens
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(tokens.shape.md)
                .clickable { expanded = true }
                .padding(horizontal = tokens.space.lg, vertical = tokens.space.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon, contentDescription = null,
                tint = tokens.colors.onSurfaceVariant,
                modifier = Modifier.padding(end = tokens.space.lg)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(t(ui.title), style = tokens.type.bodyLarge, color = tokens.colors.onSurface)
                Text(
                    text = options.find { it.first == currentValue }?.second ?: currentValue.toString(),
                    style = tokens.type.bodySmall,
                    color = tokens.colors.onSurfaceVariant
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(label, style = tokens.type.bodyMedium)
                            if (value == currentValue) {
                                Spacer(Modifier.width(tokens.space.sm))
                                Icon(
                                    Icons.Filled.Check, contentDescription = null,
                                    tint = tokens.colors.primary
                                )
                            }
                        }
                    },
                    onClick = { onValueChange(value); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun TextFieldRow(
    ui: com.tmuxes.data.settings.SettingUi,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    multiline: Boolean,
    onValueChange: (String) -> Unit
) {
    val tokens = MaterialTheme.appTokens
    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = tokens.space.lg, vertical = tokens.space.md)
    ) {
        SettingHeader(ui, icon)
        Spacer(Modifier.height(tokens.space.sm))
        AppTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = ui.placeholder?.let { t(it) },
            singleLine = !multiline,
            minLines = if (multiline) 3 else 1,
            maxLines = if (multiline) 6 else 1
        )
    }
}

@Composable
private fun ColorChipsRow(
    ui: com.tmuxes.data.settings.SettingUi,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    currentColor: Int,
    allowDefault: Boolean,
    onColorChange: (Int) -> Unit
) {
    val tokens = MaterialTheme.appTokens
    val presets = ThemeAccents.presets.map { it.argb to it.label }
    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = tokens.space.lg, vertical = tokens.space.md)
    ) {
        SettingHeader(ui, icon) {
            Box(
                modifier = Modifier
                    .size(24.dpFromTokens())
                    .clip(CircleShape)
                    .background(
                        if (allowDefault && currentColor == 0) {
                            tokens.colors.surfaceContainerHighest
                        } else {
                            Color(currentColor)
                        }
                    )
                    .border(1.dpFromTokens(), tokens.colors.divider, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (allowDefault && currentColor == 0) {
                    Icon(
                        Icons.Filled.SettingsBrightness,
                        contentDescription = t("Default"),
                        tint = tokens.colors.onSurfaceVariant,
                        modifier = Modifier.size(14.dpFromTokens())
                    )
                }
            }
        }
        Spacer(Modifier.height(tokens.space.md))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(tokens.space.sm)
        ) {
            if (allowDefault) {
                val selected = currentColor == 0
                Box(
                    modifier = Modifier
                        .size(28.dpFromTokens())
                        .clip(CircleShape)
                        .background(tokens.colors.surfaceContainerHighest)
                        .border(
                            width = if (selected) 2.dpFromTokens() else 1.dpFromTokens(),
                            color = if (selected) tokens.colors.primary else tokens.colors.divider,
                            shape = CircleShape
                        )
                        .clickable { onColorChange(0) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (selected) Icons.Filled.Check else Icons.Filled.SettingsBrightness,
                        contentDescription = t("Default"),
                        tint = if (selected) tokens.colors.primary else tokens.colors.onSurfaceVariant,
                        modifier = Modifier.size(14.dpFromTokens())
                    )
                }
            }
            presets.forEach { (color, label) ->
                val selected = currentColor != 0 && ThemeAccents.selectedOption(currentColor).argb == color
                Box(
                    modifier = Modifier
                        .size(28.dpFromTokens())
                        .clip(CircleShape)
                        .background(if (color == 0) tokens.colors.surfaceVariant else Color(color))
                        .border(
                            width = if (selected) 2.dpFromTokens() else 1.dpFromTokens(),
                            color = if (selected) tokens.colors.primary else tokens.colors.divider,
                            shape = CircleShape
                        )
                        .clickable { onColorChange(color) },
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Icon(
                            Icons.Filled.Check, contentDescription = t(label),
                            tint = Color(color).readableCheckColor(),
                            modifier = Modifier.size(14.dpFromTokens())
                        )
                    }
                }
            }
        }
    }
}

private fun Color.readableCheckColor(): Color =
    if (luminance() > 0.48f) Color.Black else Color.White

private fun Int.dpFromTokens() = androidx.compose.ui.unit.Dp(this.toFloat())
private fun Float.dpFromTokens() = androidx.compose.ui.unit.Dp(this)
private fun Double.dpFromTokens() = androidx.compose.ui.unit.Dp(this.toFloat())

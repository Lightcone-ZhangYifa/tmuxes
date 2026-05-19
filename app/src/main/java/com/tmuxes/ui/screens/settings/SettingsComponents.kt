package com.tmuxes.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import com.tmuxes.i18n.t
import com.tmuxes.terminal.emulator.TerminalColors
import com.tmuxes.ui.components.app.AppCard
import com.tmuxes.ui.components.app.AppCardVariant
import com.tmuxes.ui.components.app.AppSectionHeader
import com.tmuxes.ui.components.app.AppSlider
import com.tmuxes.ui.components.app.AppSwitch
import com.tmuxes.ui.components.app.AppTextField
import com.tmuxes.ui.components.app.appPressable
import com.tmuxes.ui.design.ThemeAccents
import com.tmuxes.ui.design.appTokens
import java.util.Locale
import kotlin.math.roundToInt

// Section headers, list-card containers, and dividers used to live here as
// thin wrappers (SettingsSectionHeader / SettingsCard / SettingsDivider).
// They have moved into the App* family — callers now use AppSectionHeader,
// AppListCard, and AppHorizontalDivider(inset = true) directly.

// ---------------------------------------------------------------------------
// Switch item
// ---------------------------------------------------------------------------

@Composable
internal fun SettingSwitchItem(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val tokens = MaterialTheme.appTokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(tokens.shape.md)
            .appPressable { onCheckedChange(!checked) }
            .padding(horizontal = tokens.space.lg, vertical = tokens.space.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tokens.colors.onSurfaceVariant,
            modifier = Modifier.padding(end = tokens.space.lg)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = t(title),
                style = tokens.type.bodyLarge,
                color = tokens.colors.onSurface
            )
            Text(
                text = t(description),
                style = tokens.type.bodySmall,
                color = tokens.colors.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(tokens.space.md))
        AppSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

// ---------------------------------------------------------------------------
// Navigation item (clickable row that navigates to another screen)
// ---------------------------------------------------------------------------

@Composable
internal fun SettingsNavigationItem(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    val tokens = MaterialTheme.appTokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(tokens.shape.md)
            .appPressable(onClick = onClick)
            .padding(horizontal = tokens.space.lg, vertical = tokens.space.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tokens.colors.onSurfaceVariant,
            modifier = Modifier.padding(end = tokens.space.lg)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = t(title),
                style = tokens.type.bodyLarge,
                color = tokens.colors.onSurface
            )
            Text(
                text = description,
                style = tokens.type.bodySmall,
                color = tokens.colors.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = tokens.colors.onSurfaceVariant
        )
    }
}

// ---------------------------------------------------------------------------
// Dropdown setting item (reusable for TERM type, strict host key, etc.)
// ---------------------------------------------------------------------------

@Composable
internal fun SettingsDropdownItem(
    icon: ImageVector,
    title: String,
    description: String,
    currentValue: String,
    options: List<Pair<String, String>>,
    onValueChange: (String) -> Unit
) {
    val tokens = MaterialTheme.appTokens
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(tokens.shape.md)
            .appPressable { expanded = true }
            .padding(horizontal = tokens.space.lg, vertical = tokens.space.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tokens.colors.onSurfaceVariant,
            modifier = Modifier.padding(end = tokens.space.lg)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = tokens.type.bodyLarge,
                color = tokens.colors.onSurface
            )
            Text(
                text = options.find { it.first == currentValue }?.second?.let { t(it) } ?: currentValue,
                style = tokens.type.bodySmall,
                color = tokens.colors.onSurfaceVariant
            )
            Text(
                text = t(description),
                style = tokens.type.labelSmall,
                color = tokens.colors.outline
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(t(label))
                            if (value == currentValue) {
                                Spacer(modifier = Modifier.width(tokens.space.sm))
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = tokens.colors.primary
                                )
                            }
                        }
                    },
                    onClick = {
                        onValueChange(value)
                        expanded = false
                    }
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Slider setting item (reusable for timeouts, intervals, etc.)
// ---------------------------------------------------------------------------

@Composable
internal fun SettingsSliderItem(
    icon: ImageVector,
    title: String,
    description: String,
    value: Int,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: (Int) -> String,
    onValueChange: (Int) -> Unit
) {
    val tokens = MaterialTheme.appTokens
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.space.lg, vertical = tokens.space.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tokens.colors.onSurfaceVariant,
                modifier = Modifier.padding(end = tokens.space.lg)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = t(title),
                    style = tokens.type.bodyLarge,
                    color = tokens.colors.onSurface
                )
                Text(
                    text = t(description),
                    style = tokens.type.labelSmall,
                    color = tokens.colors.outline
                )
            }
            Text(
                text = valueLabel(value),
                style = tokens.type.mono,
                color = tokens.colors.primary
            )
        }
        Spacer(modifier = Modifier.height(tokens.space.xs))
        AppSlider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = valueRange,
            steps = steps
        )
    }
}

// ---------------------------------------------------------------------------
// Text field setting item (reusable for shell, startup command, env vars)
// ---------------------------------------------------------------------------

@Composable
internal fun SettingsTextFieldItem(
    icon: ImageVector,
    title: String,
    description: String,
    value: String,
    placeholder: String,
    singleLine: Boolean,
    onValueChange: (String) -> Unit
) {
    val tokens = MaterialTheme.appTokens
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.space.lg, vertical = tokens.space.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tokens.colors.onSurfaceVariant,
                modifier = Modifier.padding(end = tokens.space.lg)
            )
            Column {
                Text(
                    text = t(title),
                    style = tokens.type.bodyLarge,
                    color = tokens.colors.onSurface
                )
                Text(
                    text = t(description),
                    style = tokens.type.labelSmall,
                    color = tokens.colors.outline
                )
            }
        }
        Spacer(modifier = Modifier.height(tokens.space.sm))
        AppTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = placeholder,
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 3,
            maxLines = if (singleLine) 1 else 6
        )
    }
}

// ---------------------------------------------------------------------------
// Color chip setting (for selection color, cursor color)
// ---------------------------------------------------------------------------

@Composable
internal fun ColorChipSetting(
    icon: ImageVector,
    title: String,
    description: String,
    currentColor: Int,
    onColorChange: (Int) -> Unit,
    allowDefault: Boolean = false,
    defaultLabel: String = "Default"
) {
    val tokens = MaterialTheme.appTokens
    val presetColors = ThemeAccents.presets.map { it.argb to it.label }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.space.lg, vertical = tokens.space.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tokens.colors.onSurfaceVariant,
                modifier = Modifier.padding(end = tokens.space.lg)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = tokens.type.bodyLarge,
                    color = tokens.colors.onSurface
                )
                Text(
                    text = description,
                    style = tokens.type.bodySmall,
                    color = tokens.colors.onSurfaceVariant
                )
            }
            Box(
                modifier = Modifier
                    .size(24.dpUnit())
                    .clip(CircleShape)
                    .background(
                        if (allowDefault && currentColor == 0) {
                            tokens.colors.surfaceContainerHighest
                        } else {
                            Color(currentColor)
                        }
                    )
                    .border(
                        width = 1.dpUnit(),
                        color = tokens.colors.divider,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (allowDefault && currentColor == 0) {
                    Icon(
                        imageVector = Icons.Filled.SettingsBrightness,
                        contentDescription = t(defaultLabel),
                        tint = tokens.colors.onSurfaceVariant,
                        modifier = Modifier.size(14.dpUnit())
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(tokens.space.md))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(tokens.space.sm)
        ) {
            if (allowDefault) {
                val isSelected = currentColor == 0
                Box(
                    modifier = Modifier
                        .size(28.dpUnit())
                        .clip(CircleShape)
                        .background(tokens.colors.surfaceContainerHighest)
                        .border(
                            width = if (isSelected) 2.dpUnit() else 0.5f.dpUnitF(),
                            color = if (isSelected) tokens.colors.primary else tokens.colors.outlineVariant,
                            shape = CircleShape
                        )
                        .appPressable { onColorChange(0) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Filled.Check else Icons.Filled.SettingsBrightness,
                        contentDescription = t(defaultLabel),
                        tint = if (isSelected) tokens.colors.primary else tokens.colors.onSurfaceVariant,
                        modifier = Modifier.size(14.dpUnit())
                    )
                }
            }
            presetColors.forEach { (color, label) ->
                val isSelected = currentColor != 0 && ThemeAccents.selectedOption(currentColor).argb == color
                Box(
                    modifier = Modifier
                        .size(28.dpUnit())
                        .clip(CircleShape)
                        .background(Color(color))
                        .then(
                            if (isSelected) Modifier.border(
                                width = 2.dpUnit(),
                                color = tokens.colors.primary,
                                shape = CircleShape
                            ) else Modifier.border(
                                width = 0.5f.dpUnitF(),
                                color = tokens.colors.outlineVariant,
                                shape = CircleShape
                            )
                        )
                        .appPressable { onColorChange(color) },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = t(label),
                            tint = Color(color).readableCheckColor(),
                            modifier = Modifier.size(14.dpUnit())
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Collapsible section header
// ---------------------------------------------------------------------------

@Composable
internal fun SettingsCollapsibleHeader(
    text: String,
    expanded: Boolean,
    onClick: () -> Unit
) {
    val tokens = MaterialTheme.appTokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(tokens.shape.sm)
            .appPressable(onClick = onClick)
            .padding(top = tokens.space.lg, bottom = tokens.space.xs, start = tokens.space.xs, end = tokens.space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = t(text).uppercase(),
            style = tokens.type.sectionHeader,
            color = tokens.colors.primary,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = t(if (expanded) "Collapse" else "Expand"),
            tint = tokens.colors.primary,
            modifier = Modifier.size(20.dpUnit())
        )
    }
}

// ---------------------------------------------------------------------------
// Collapsible section (header + animated content)
// ---------------------------------------------------------------------------

@Composable
internal fun SettingsCollapsibleSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    val tokens = MaterialTheme.appTokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(tokens.shape.sm)
            .appPressable(onClick = onToggle)
            .padding(vertical = tokens.space.sm, horizontal = tokens.space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            style = tokens.type.sectionHeader,
            color = tokens.colors.primary,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = t(if (expanded) "Collapse" else "Expand"),
            tint = tokens.colors.primary,
            modifier = Modifier.size(20.dpUnit())
        )
    }
    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        content()
    }
}

// ---------------------------------------------------------------------------
// Color scheme selector
// ---------------------------------------------------------------------------

@Composable
internal fun ColorSchemeSelector(
    currentScheme: String,
    customSchemes: List<TerminalColors.ColorScheme> = emptyList(),
    onSchemeChange: (String) -> Unit
) {
    val tokens = MaterialTheme.appTokens
    val builtInSchemes = listOf(
        "" to "Follow terminal setting",
        "catppuccin" to "Catppuccin Mocha",
        "monokai" to "Monokai",
        "dracula" to "Dracula",
        "nord" to "Nord",
        "solarized" to "Solarized Dark",
        "gruvbox" to "Gruvbox Dark",
        "one_dark" to "One Dark"
    )
    val schemes = builtInSchemes + dedupedCustomSchemeOptions(customSchemes, builtInSchemes)

    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(tokens.shape.md)
            .appPressable { expanded = true }
            .padding(horizontal = tokens.space.lg, vertical = tokens.space.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.ColorLens,
            contentDescription = null,
            tint = tokens.colors.onSurfaceVariant,
            modifier = Modifier.padding(end = tokens.space.lg)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = t("Color Scheme"),
                style = tokens.type.bodyLarge,
                color = tokens.colors.onSurface
            )
            Text(
                text = schemes.find { it.first == currentScheme }?.second?.let { t(it) } ?: currentScheme,
                style = tokens.type.bodySmall,
                color = tokens.colors.onSurfaceVariant
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            schemes.forEach { (value, label) ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(t(label))
                            if (value == currentScheme) {
                                Spacer(modifier = Modifier.width(tokens.space.sm))
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = tokens.colors.primary
                                )
                            }
                        }
                    },
                    onClick = {
                        onSchemeChange(value)
                        expanded = false
                    }
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Font size slider
// ---------------------------------------------------------------------------

@Composable
internal fun FontSizeSetting(
    currentSize: Int,
    onSizeChange: (Int) -> Unit
) {
    val tokens = MaterialTheme.appTokens
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.space.lg, vertical = tokens.space.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.FormatSize,
                contentDescription = null,
                tint = tokens.colors.onSurfaceVariant,
                modifier = Modifier.padding(end = tokens.space.lg)
            )
            Text(
                text = t("Font Size"),
                style = tokens.type.bodyLarge,
                color = tokens.colors.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${currentSize}sp",
                style = tokens.type.mono,
                color = tokens.colors.primary
            )
        }
        Spacer(modifier = Modifier.height(tokens.space.sm))
        AppSlider(
            value = currentSize.toFloat(),
            onValueChange = { onSizeChange(it.roundToInt()) },
            valueRange = 6f..36f,
            steps = 29
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = t("A"),
                style = tokens.type.labelSmall,
                color = tokens.colors.onSurfaceVariant
            )
            Text(
                text = t("A"),
                style = tokens.type.titleMedium,
                color = tokens.colors.onSurfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------------------------
// App font scale slider
// ---------------------------------------------------------------------------

@Composable
internal fun AppFontScaleSlider(
    currentScale: Int,
    onScaleChange: (Int) -> Unit
) {
    val tokens = MaterialTheme.appTokens
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.space.lg, vertical = tokens.space.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.FormatSize,
                contentDescription = null,
                tint = tokens.colors.onSurfaceVariant,
                modifier = Modifier.padding(end = tokens.space.lg)
            )
            Text(
                text = t("Font Scale"),
                style = tokens.type.bodyLarge,
                color = tokens.colors.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${currentScale}%",
                style = tokens.type.mono,
                color = tokens.colors.primary
            )
        }
        Text(
            text = t("Scale all app UI text (does not affect terminal)"),
            style = tokens.type.bodySmall,
            color = tokens.colors.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(tokens.space.sm))
        AppSlider(
            value = currentScale.toFloat(),
            onValueChange = { onScaleChange(it.roundToInt()) },
            valueRange = 75f..150f,
            steps = 14
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "75%",
                style = tokens.type.bodySmall,
                color = tokens.colors.onSurfaceVariant
            )
            Text(
                text = "150%",
                style = tokens.type.bodySmall,
                color = tokens.colors.onSurfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Font family selector
// ---------------------------------------------------------------------------

@Composable
internal fun FontFamilySelector(
    currentFamily: String,
    onFamilyChange: (String) -> Unit
) {
    val tokens = MaterialTheme.appTokens
    val families = listOf(
        "" to "Follow terminal setting",
        "jetbrains_mono" to "JetBrains Mono",
        "monospace" to "Monospace (System)",
        "sans_serif" to "Sans Serif",
        "serif" to "Serif"
    )

    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(tokens.shape.md)
            .appPressable { expanded = true }
            .padding(horizontal = tokens.space.lg, vertical = tokens.space.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.TextFormat,
            contentDescription = null,
            tint = tokens.colors.onSurfaceVariant,
            modifier = Modifier.padding(end = tokens.space.lg)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = t("Font Family"),
                style = tokens.type.bodyLarge,
                color = tokens.colors.onSurface
            )
            Text(
                text = t(families.find { it.first == currentFamily }?.second ?: currentFamily),
                style = tokens.type.bodySmall,
                color = tokens.colors.onSurfaceVariant
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            families.forEach { (value, label) ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = t(label),
                                fontFamily = when (value) {
                                    "monospace" -> FontFamily.Monospace
                                    "sans_serif" -> FontFamily.SansSerif
                                    "serif" -> FontFamily.Serif
                                    else -> FontFamily.Default
                                }
                            )
                            if (value == currentFamily) {
                                Spacer(modifier = Modifier.width(tokens.space.sm))
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = tokens.colors.primary
                                )
                            }
                        }
                    },
                    onClick = {
                        onFamilyChange(value)
                        expanded = false
                    }
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Start screen selector
// ---------------------------------------------------------------------------

@Composable
internal fun StartScreenSelector(
    currentScreen: String,
    onScreenChange: (String) -> Unit
) {
    val tokens = MaterialTheme.appTokens
    val options = listOf(
        "last_session" to "Last Session",
        "sessions" to "Sessions List",
        "servers" to "Server List"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.space.lg, vertical = tokens.space.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.PhoneAndroid,
                contentDescription = null,
                tint = tokens.colors.onSurfaceVariant,
                modifier = Modifier.padding(end = tokens.space.lg)
            )
            Text(
                text = t("Start Screen"),
                style = tokens.type.bodyLarge,
                color = tokens.colors.onSurface
            )
        }
        Spacer(modifier = Modifier.height(tokens.space.md))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(tokens.space.sm)
        ) {
            options.forEach { (value, label) ->
                val isSelected = currentScreen == value
                SelectablePill(
                    isSelected = isSelected,
                    onClick = { onScreenChange(value) },
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(tokens.space.sm + tokens.space.xxs),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = t(label),
                            style = tokens.type.labelSmall,
                            color = if (isSelected)
                                tokens.colors.onPrimaryContainer
                            else
                                tokens.colors.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Reconnect speed selector
// ---------------------------------------------------------------------------

@Composable
internal fun ReconnectSpeedSelector(
    currentInterval: Int,
    onIntervalChange: (Int) -> Unit
) {
    val tokens = MaterialTheme.appTokens
    val speeds = listOf(
        30 to "Aggressive (30s)",
        60 to "Normal (60s)",
        300 to "Conservative (5min)"
    )

    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(tokens.shape.md)
            .appPressable { expanded = true }
            .padding(horizontal = tokens.space.lg, vertical = tokens.space.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Visibility,
            contentDescription = null,
            tint = tokens.colors.onSurfaceVariant,
            modifier = Modifier.padding(end = tokens.space.lg)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = t("Reconnect Speed"),
                style = tokens.type.bodyLarge,
                color = tokens.colors.onSurface
            )
            Text(
                text = speeds.find { it.first == currentInterval }?.second?.let { t(it) }
                    ?: t("{seconds}s", "seconds" to currentInterval),
                style = tokens.type.bodySmall,
                color = tokens.colors.onSurfaceVariant
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            speeds.forEach { (value, label) ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(t(label))
                            if (value == currentInterval) {
                                Spacer(modifier = Modifier.width(tokens.space.sm))
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = tokens.colors.primary
                                )
                            }
                        }
                    },
                    onClick = {
                        onIntervalChange(value)
                        expanded = false
                    }
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Terminal Color Scheme selector with ANSI preview strip
// ---------------------------------------------------------------------------

@Composable
internal fun TerminalColorSchemeSelector(
    currentScheme: String,
    customSchemes: List<TerminalColors.ColorScheme> = emptyList(),
    onSchemeChange: (String) -> Unit,
    onEditCustom: () -> Unit
) {
    val tokens = MaterialTheme.appTokens
    val builtInSchemes = listOf(
        "catppuccin" to "Catppuccin Mocha",
        "monokai" to "Monokai",
        "dracula" to "Dracula",
        "nord" to "Nord",
        "solarized" to "Solarized Dark",
        "gruvbox" to "Gruvbox Dark",
        "one_dark" to "One Dark"
    )
    val customOptions = dedupedCustomSchemeOptions(customSchemes, builtInSchemes)
    val schemes = builtInSchemes + customOptions

    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(tokens.shape.md)
            .appPressable { expanded = true }
            .padding(horizontal = tokens.space.lg, vertical = tokens.space.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.ColorLens,
                contentDescription = null,
                tint = tokens.colors.onSurfaceVariant,
                modifier = Modifier.padding(end = tokens.space.lg)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = t("Color Scheme"),
                    style = tokens.type.bodyLarge,
                    color = tokens.colors.onSurface
                )
                Text(
                    text = schemes.find { it.first == currentScheme }?.second?.let { t(it) } ?: currentScheme,
                    style = tokens.type.bodySmall,
                    color = tokens.colors.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = t("Edit custom scheme"),
                tint = tokens.colors.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dpUnit())
                    .appPressable(onClick = onEditCustom)
            )
        }

        // ANSI preview strip for the current scheme
        Spacer(modifier = Modifier.height(tokens.space.sm + tokens.space.xxs))
        AnsiPreviewStrip(schemeName = currentScheme, customSchemes = customSchemes)

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            schemes.forEach { (value, label) ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(label)
                                if (value == currentScheme) {
                                    Spacer(modifier = Modifier.width(tokens.space.sm))
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = tokens.colors.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(tokens.space.xs))
                            AnsiPreviewStrip(schemeName = value, customSchemes = customSchemes)
                        }
                    },
                    onClick = {
                        onSchemeChange(value)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
internal fun AnsiPreviewStrip(
    schemeName: String,
    customSchemes: List<TerminalColors.ColorScheme> = emptyList()
) {
    val tokens = MaterialTheme.appTokens
    val scheme = customSchemes.firstOrNull { it.name.equals(schemeName, ignoreCase = true) }
        ?: TerminalColors.getSchemeByName(schemeName)
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dpUnit())
    ) {
        // Show ANSI colors 0-7 (standard palette) — palette colors come
        // from the terminal scheme (data, not theme), so Color(int) reads
        // are intentional.
        for (i in 0..7) {
            val argb = scheme.ansi[i]
            Box(
                modifier = Modifier
                    .size(18.dpUnit())
                    .clip(tokens.shape.xs)
                    .background(Color(argb))
                    .border(
                        width = 0.5f.dpUnitF(),
                        color = tokens.colors.outlineVariant,
                        shape = tokens.shape.xs
                    )
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Cursor Style selector (Block / Underline / Bar)
// ---------------------------------------------------------------------------

@Composable
internal fun CursorStyleSelector(
    currentStyle: String,
    onStyleChange: (String) -> Unit
) {
    val tokens = MaterialTheme.appTokens
    val styles = listOf(
        Triple("block", "Block", "█"),
        Triple("underline", "Line", "_"),
        Triple("bar", "Bar", "│")
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.space.lg, vertical = tokens.space.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = null,
                tint = tokens.colors.onSurfaceVariant,
                modifier = Modifier.padding(end = tokens.space.lg)
            )
            Text(
                text = t("Cursor Style"),
                style = tokens.type.bodyLarge,
                color = tokens.colors.onSurface
            )
        }
        Spacer(modifier = Modifier.height(tokens.space.md))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(tokens.space.sm)
        ) {
            styles.forEach { (value, label, icon) ->
                val isSelected = currentStyle == value
                SelectablePill(
                    isSelected = isSelected,
                    onClick = { onStyleChange(value) },
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(tokens.space.md),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(tokens.space.xs)
                    ) {
                        Text(
                            text = icon,
                            style = tokens.type.mono,
                            color = if (isSelected)
                                tokens.colors.onPrimaryContainer
                            else
                                tokens.colors.onSurfaceVariant
                        )
                        Text(
                            text = t(label),
                            style = tokens.type.labelSmall,
                            color = if (isSelected)
                                tokens.colors.onPrimaryContainer
                            else
                                tokens.colors.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Cursor Blink setting (switch + speed slider)
// ---------------------------------------------------------------------------

@Composable
internal fun CursorBlinkSetting(
    blinkEnabled: Boolean,
    blinkSpeed: Int,
    onBlinkChange: (Boolean) -> Unit,
    onSpeedChange: (Int) -> Unit
) {
    val tokens = MaterialTheme.appTokens
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.space.lg, vertical = tokens.space.md)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(tokens.shape.md)
                .appPressable { onBlinkChange(!blinkEnabled) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Timer,
                contentDescription = null,
                tint = tokens.colors.onSurfaceVariant,
                modifier = Modifier.padding(end = tokens.space.lg)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = t("Cursor Blink"),
                    style = tokens.type.bodyLarge,
                    color = tokens.colors.onSurface
                )
                Text(
                    text = t("Animate the cursor with a blink effect"),
                    style = tokens.type.bodySmall,
                    color = tokens.colors.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(tokens.space.md))
            AppSwitch(
                checked = blinkEnabled,
                onCheckedChange = onBlinkChange
            )
        }

        if (blinkEnabled) {
            Spacer(modifier = Modifier.height(tokens.space.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = t("Blink Speed"),
                    style = tokens.type.bodyMedium,
                    color = tokens.colors.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${blinkSpeed}ms",
                    style = tokens.type.mono,
                    color = tokens.colors.primary
                )
            }
            Spacer(modifier = Modifier.height(tokens.space.xs))
            AppSlider(
                value = blinkSpeed.toFloat(),
                onValueChange = { onSpeedChange(it.roundToInt()) },
                valueRange = 200f..1000f,
                steps = 15
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = t("Fast"),
                    style = tokens.type.labelSmall,
                    color = tokens.colors.onSurfaceVariant
                )
                Text(
                    text = t("Slow"),
                    style = tokens.type.labelSmall,
                    color = tokens.colors.onSurfaceVariant
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Scrollback Lines selector (discrete chips)
// ---------------------------------------------------------------------------

@Composable
internal fun ScrollbackSelector(
    currentLines: Int,
    onLinesChange: (Int) -> Unit
) {
    val tokens = MaterialTheme.appTokens
    val options = listOf(
        1000 to "1K",
        5000 to "5K",
        10000 to "10K",
        50000 to "50K",
        100000 to "100K"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.space.lg, vertical = tokens.space.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Terminal,
                contentDescription = null,
                tint = tokens.colors.onSurfaceVariant,
                modifier = Modifier.padding(end = tokens.space.lg)
            )
            Column {
                Text(
                    text = t("Scrollback Lines"),
                    style = tokens.type.bodyLarge,
                    color = tokens.colors.onSurface
                )
                Text(
                    text = t("Maximum lines kept in scroll history"),
                    style = tokens.type.bodySmall,
                    color = tokens.colors.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(tokens.space.md))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(tokens.space.xs + 2.dpUnit())
        ) {
            options.forEach { (value, label) ->
                val isSelected = currentLines == value
                SelectablePill(
                    isSelected = isSelected,
                    onClick = { onLinesChange(value) },
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = tokens.space.sm),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = tokens.type.labelMedium,
                            color = if (isSelected)
                                tokens.colors.onPrimaryContainer
                            else
                                tokens.colors.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

/**
 * Selectable pill — used by every "tap to choose one of N" cluster
 * (theme, start screen, cursor style, scrollback). Picks card
 * container color from token state (selected vs not).
 */
@Composable
private fun SelectablePill(
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val tokens = MaterialTheme.appTokens
    val container = if (isSelected) tokens.colors.primaryContainer
    else tokens.colors.surfaceContainerHighest
    Box(
        modifier = modifier
            .clip(tokens.shape.md)
            .background(container)
            .appPressable(onClick = onClick)
    ) {
        content()
    }
}

/**
 * Element-physical dp helper — for swatch sizes / icon sizes that are
 * fixed visual dimensions (not theme-driven spacing). Prefer
 * `tokens.space.X` for layout spacing.
 */
private fun Int.dpUnit() = androidx.compose.ui.unit.Dp(this.toFloat())
private fun Float.dpUnitF() = androidx.compose.ui.unit.Dp(this)

private fun Color.readableCheckColor(): Color =
    if (luminance() > 0.48f) Color.Black else Color.White

private fun dedupedCustomSchemeOptions(
    customSchemes: List<TerminalColors.ColorScheme>,
    builtInSchemes: List<Pair<String, String>>
): List<Pair<String, String>> {
    val reserved = builtInSchemes
        .flatMap { listOf(it.first, it.second) }
        .map { it.trim().lowercase(Locale.ROOT) }
        .toSet()
    val seen = reserved.toMutableSet()

    return customSchemes.mapNotNull { scheme ->
        val name = scheme.name.trim()
        if (name.isEmpty()) return@mapNotNull null
        val key = name.lowercase(Locale.ROOT)
        if (!seen.add(key)) return@mapNotNull null
        name to name
    }
}

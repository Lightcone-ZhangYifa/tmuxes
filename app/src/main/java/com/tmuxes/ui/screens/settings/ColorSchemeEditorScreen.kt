// allow-bypass-D5: defensive Toast/JSON wraps; failure is user-visible elsewhere
package com.tmuxes.ui.screens.settings

import android.content.ClipData
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import com.tmuxes.ui.components.app.appElasticVerticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tmuxes.terminal.emulator.TerminalColors
import com.tmuxes.ui.components.app.AppButton
import com.tmuxes.ui.components.app.AppButtonStyle
import com.tmuxes.ui.components.app.AppCard
import com.tmuxes.ui.components.app.AppDialog
import com.tmuxes.ui.components.app.AppScaffold
import com.tmuxes.ui.components.app.AppSectionHeader
import com.tmuxes.ui.components.app.AppTextField
import com.tmuxes.ui.components.app.rememberAppEntryScrollState
import com.tmuxes.i18n.I18nRuntime
import com.tmuxes.i18n.t
import com.tmuxes.ui.design.appTokens
import com.tmuxes.ui.viewmodel.SettingsViewModel
import com.tmuxes.util.ColorHex
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Color Scheme Editor Screen
// ---------------------------------------------------------------------------

private data class ColorSchemeDraft(
    val name: String,
    val foreground: Int,
    val background: Int,
    val ansi: List<Int>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorSchemeEditorScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val tokens = MaterialTheme.appTokens
    val customSchemesJson by viewModel.preferences
        .flow(com.tmuxes.data.settings.Settings.terminalCustomSchemes)
        .collectAsState(initial = com.tmuxes.data.settings.Settings.terminalCustomSchemes.default)
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberAppEntryScrollState("color_scheme_editor")

    // Editable state
    var schemeName by remember { mutableStateOf("Custom") }
    var foreground by remember { mutableIntStateOf(TerminalColors.CATPPUCCIN_MOCHA.foreground) }
    var background by remember { mutableIntStateOf(TerminalColors.CATPPUCCIN_MOCHA.background) }
    val ansiColors = remember {
        mutableStateListOf(*TerminalColors.CATPPUCCIN_MOCHA.ansi.toTypedArray())
    }

    // Preset dropdown
    var presetExpanded by remember { mutableStateOf(false) }

    // Color edit dialog
    var editingTarget by remember { mutableStateOf<ColorEditTarget?>(null) }
    var showUnsavedDialog by remember { mutableStateOf(false) }
    var savedDraft by remember { mutableStateOf<ColorSchemeDraft?>(null) }

    fun currentDraft(): ColorSchemeDraft {
        return ColorSchemeDraft(
            name = schemeName,
            foreground = foreground,
            background = background,
            ansi = ansiColors.toList()
        )
    }

    LaunchedEffect(Unit) {
        savedDraft = currentDraft()
    }

    fun loadPreset(scheme: TerminalColors.ColorScheme) {
        schemeName = scheme.name + " (custom)"
        foreground = scheme.foreground
        background = scheme.background
        ansiColors.clear()
        ansiColors.addAll(scheme.ansi.toList())
    }

    fun buildScheme(): TerminalColors.ColorScheme {
        return TerminalColors.ColorScheme(
            name = schemeName,
            foreground = foreground,
            background = background,
            ansi = ansiColors.toIntArray()
        )
    }

    fun saveScheme() {
        val current = TerminalColors.getCustomSchemes(customSchemesJson).toMutableList()
        // Replace if same name exists, else append
        val idx = current.indexOfFirst { it.name == schemeName }
        if (idx >= 0) {
            current[idx] = buildScheme()
        } else {
            current.add(buildScheme())
        }
        viewModel.set(
            com.tmuxes.data.settings.Settings.terminalCustomSchemes,
            TerminalColors.customSchemesToJson(current)
        )
        savedDraft = currentDraft()
        try {
            Toast.makeText(
                context,
                I18nRuntime.t("Saved \"{name}\"", "name" to schemeName),
                Toast.LENGTH_SHORT
            ).show()
        } catch (_: Throwable) {}
    }

    val hasUnsavedChanges = savedDraft?.let { currentDraft() != it } == true

    fun requestNavigateBack() {
        if (hasUnsavedChanges) {
            showUnsavedDialog = true
        } else {
            onNavigateBack()
        }
    }

    BackHandler(enabled = editingTarget == null && !showUnsavedDialog) {
        requestNavigateBack()
    }

    AppScaffold(
        title = "Color Scheme Editor",
        onBack = { requestNavigateBack() },
        actions = {
            AppButton(
                text = "Save",
                onClick = { saveScheme() },
                style = AppButtonStyle.Text
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .appElasticVerticalScroll(scrollState)
                .padding(horizontal = tokens.space.lg),
            verticalArrangement = Arrangement.spacedBy(tokens.space.md)
        ) {
            // -- Scheme Name --
            AppTextField(
                value = schemeName,
                onValueChange = { schemeName = it },
                label = "Scheme Name",
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // -- Based On dropdown --
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dpUnit())
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(tokens.shape.md)
                        .clickable { presetExpanded = true }
                        .padding(horizontal = tokens.space.lg, vertical = tokens.space.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = t("Based on"),
                        style = tokens.type.bodyLarge,
                        color = tokens.colors.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = t("Select preset"),
                        style = tokens.type.bodySmall,
                        color = tokens.colors.primary
                    )
                    DropdownMenu(
                        expanded = presetExpanded,
                        onDismissRequest = { presetExpanded = false }
                    ) {
                        TerminalColors.ALL_PRESETS.forEach { preset ->
                            DropdownMenuItem(
                                text = { Text(t(preset.name)) },
                                onClick = {
                                    loadPreset(preset)
                                    presetExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // -- Foreground + Background --
            AppSectionHeader(text = "Base Colors")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(tokens.space.md)
            ) {
                ColorCard(
                    label = "Foreground",
                    color = foreground,
                    modifier = Modifier.weight(1f),
                    onClick = { editingTarget = ColorEditTarget.Foreground }
                )
                ColorCard(
                    label = "Background",
                    color = background,
                    modifier = Modifier.weight(1f),
                    onClick = { editingTarget = ColorEditTarget.Background }
                )
            }

            // -- 16 ANSI colors grid --
            AppSectionHeader(text = "ANSI Colors (0-15)")

            val ansiLabels = listOf(
                "Black", "Red", "Green", "Yellow",
                "Blue", "Magenta", "Cyan", "White",
                "Bright Black", "Bright Red", "Bright Green", "Bright Yellow",
                "Bright Blue", "Bright Magenta", "Bright Cyan", "Bright White"
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dpUnit()),
                horizontalArrangement = Arrangement.spacedBy(tokens.space.sm),
                verticalArrangement = Arrangement.spacedBy(tokens.space.sm)
            ) {
                itemsIndexed(ansiColors) { index, color ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(tokens.shape.sm)
                            .clickable { editingTarget = ColorEditTarget.Ansi(index) }
                            .padding(tokens.space.xs)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dpUnit())
                                .clip(CircleShape)
                                // Palette swatch — driven by terminal scheme data, not theme.
                                .background(Color(color))
                                .border(
                                    1.dpUnit(),
                                    tokens.colors.outlineVariant,
                                    CircleShape
                                )
                        )
                        Spacer(Modifier.height(tokens.space.xxs))
                        Text(
                            text = t(ansiLabels.getOrElse(index) { "$index" }),
                            style = tokens.type.labelSmall,
                            color = tokens.colors.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }

            // -- Preview --
            AppSectionHeader(text = "Preview")

            // Preview card uses scheme background color (data-driven, not theme).
            // Plain Box-based card so we can apply the user's scheme color
            // without conflicting with token-driven AppCard colors.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dpUnit())
                    .clip(tokens.shape.md)
                    .background(Color(background))
            ) {
                Column(
                    modifier = Modifier.padding(tokens.space.md),
                    verticalArrangement = Arrangement.spacedBy(tokens.space.xxs)
                ) {
                    Text(
                        text = t("user@server:~\$ ls -la"),
                        color = Color(foreground),
                        style = tokens.type.monoSmall
                    )
                    Row {
                        Text(t("drwxr-xr-x "), color = Color(ansiColors.getOrElse(4) { foreground }), style = tokens.type.monoSmall)
                        Text(t("Documents"), color = Color(ansiColors.getOrElse(6) { foreground }), style = tokens.type.monoSmall)
                    }
                    Row {
                        Text(t("-rw-r--r-- "), color = Color(foreground), style = tokens.type.monoSmall)
                        Text(t("readme.md"), color = Color(ansiColors.getOrElse(2) { foreground }), style = tokens.type.monoSmall)
                    }
                    Row {
                        Text(t("-rwxr-xr-x "), color = Color(ansiColors.getOrElse(1) { foreground }), style = tokens.type.monoSmall)
                        Text(t("build.sh"), color = Color(ansiColors.getOrElse(3) { foreground }), style = tokens.type.monoSmall)
                    }
                    Text(
                        text = t("user@server:~\$ _"),
                        color = Color(foreground),
                        style = tokens.type.monoSmall
                    )
                }
            }

            // -- Import / Export --
            AppSectionHeader(text = "Import / Export")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(tokens.space.md)
            ) {
                AppButton(
                    text = "Export",
                    onClick = {
                        // Clipboard + Toast can both throw on platform quirks /
                        // dying activities — wrap the whole button body.
                        scope.launch {
                            try {
                                val json = TerminalColors.toJson(buildScheme())
                                clipboard.setClipEntry(
                                    ClipEntry(ClipData.newPlainText("tmuxes color scheme", json))
                                )
                                try { Toast.makeText(context, I18nRuntime.t("Copied to clipboard"), Toast.LENGTH_SHORT).show() } catch (_: Throwable) {}
                            } catch (_: Throwable) {}
                        }
                    },
                    modifier = Modifier.weight(1f),
                    style = AppButtonStyle.Outlined,
                    leadingIcon = Icons.Filled.ContentCopy
                )
                AppButton(
                    text = "Import",
                    onClick = {
                        scope.launch {
                            try {
                                val clipData = clipboard.getClipEntry()?.clipData
                                val text = if (clipData != null && clipData.itemCount > 0) {
                                    clipData.getItemAt(0).coerceToText(context)?.toString()
                                } else {
                                    null
                                }
                                if (text != null) {
                                    val parsed = TerminalColors.fromJson(text)
                                    if (parsed != null) {
                                        schemeName = parsed.name
                                        foreground = parsed.foreground
                                        background = parsed.background
                                        ansiColors.clear()
                                        ansiColors.addAll(parsed.ansi.toList())
                                        try {
                                            Toast.makeText(
                                                context,
                                                I18nRuntime.t("Imported \"{name}\"", "name" to parsed.name),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } catch (_: Throwable) {}
                                    } else {
                                        try { Toast.makeText(context, I18nRuntime.t("Invalid color scheme JSON"), Toast.LENGTH_SHORT).show() } catch (_: Throwable) {}
                                    }
                                } else {
                                    try { Toast.makeText(context, I18nRuntime.t("Clipboard is empty"), Toast.LENGTH_SHORT).show() } catch (_: Throwable) {}
                                }
                            } catch (_: Throwable) {}
                        }
                    },
                    modifier = Modifier.weight(1f),
                    style = AppButtonStyle.Primary,
                    leadingIcon = Icons.Filled.ContentPaste
                )
            }

            Spacer(modifier = Modifier.height(tokens.space.lg))
        }
    }

    if (showUnsavedDialog) {
        AppDialog(
            title = "Unsaved Changes",
            text = "You have unsaved color scheme changes. Save before leaving?",
            onDismiss = { showUnsavedDialog = false },
            confirmLabel = "Save",
            onConfirm = {
                saveScheme()
                showUnsavedDialog = false
                onNavigateBack()
            },
            dismissLabel = "Cancel",
            neutralLabel = "Discard",
            onNeutral = {
                showUnsavedDialog = false
                onNavigateBack()
            },
            neutralStyle = AppButtonStyle.Outlined
        )
    }

    // Color edit dialog
    editingTarget?.let { target ->
        val currentColor = when (target) {
            is ColorEditTarget.Foreground -> foreground
            is ColorEditTarget.Background -> background
            is ColorEditTarget.Ansi -> ansiColors[target.index]
        }
        val title = when (target) {
            is ColorEditTarget.Foreground -> t("Foreground Color")
            is ColorEditTarget.Background -> t("Background Color")
            is ColorEditTarget.Ansi -> t("ANSI Color {index}", "index" to target.index)
        }
        ColorEditDialog(
            title = title,
            initialColor = currentColor,
            onDismiss = { editingTarget = null },
            onConfirm = { newColor ->
                when (target) {
                    is ColorEditTarget.Foreground -> foreground = newColor
                    is ColorEditTarget.Background -> background = newColor
                    is ColorEditTarget.Ansi -> ansiColors[target.index] = newColor
                }
                editingTarget = null
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Color edit target
// ---------------------------------------------------------------------------

private sealed class ColorEditTarget {
    data object Foreground : ColorEditTarget()
    data object Background : ColorEditTarget()
    data class Ansi(val index: Int) : ColorEditTarget()
}

// ---------------------------------------------------------------------------
// Color card (tappable swatch with label)
// ---------------------------------------------------------------------------

@Composable
private fun ColorCard(
    label: String,
    color: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val tokens = MaterialTheme.appTokens
    AppCard(
        modifier = modifier,
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(tokens.space.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dpUnit())
                    .clip(CircleShape)
                    // Palette swatch — driven by scheme data, not theme.
                    .background(Color(color))
                    .border(
                        1.dpUnit(),
                        tokens.colors.outlineVariant,
                        CircleShape
                    )
            )
            Spacer(Modifier.width(tokens.space.md))
            Column {
                Text(
                    text = t(label),
                    style = tokens.type.bodyMedium,
                    color = tokens.colors.onSurface
                )
                Text(
                    text = ColorHex.toRgbString(color),
                    style = tokens.type.monoSmall,
                    color = tokens.colors.onSurfaceVariant
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Color edit dialog (hex input + preview swatch)
// ---------------------------------------------------------------------------

@Composable
private fun ColorEditDialog(
    title: String,
    initialColor: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val tokens = MaterialTheme.appTokens
    var hexInput by remember { mutableStateOf(ColorHex.toRgbString(initialColor)) }
    var parseError by remember { mutableStateOf(false) }

    val previewColor = remember(hexInput) {
        ColorHex.parseRgb(hexInput)?.let { Color(it) }
    }

    AppDialog(
        title = title,
        onDismiss = onDismiss,
        confirmLabel = "Apply",
        confirmEnabled = previewColor != null,
        onConfirm = {
            ColorHex.parseRgb(hexInput)?.let(onConfirm)
        },
        content = {
            Column(
                verticalArrangement = Arrangement.spacedBy(tokens.space.md)
            ) {
                // Preview swatch — color is data-driven (user-typed hex),
                // not theme-driven.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dpUnit())
                        .clip(tokens.shape.sm)
                        .background(previewColor ?: Color.Transparent)
                        .border(
                            1.dpUnit(),
                            tokens.colors.divider,
                            tokens.shape.sm
                        )
                )

                AppTextField(
                    value = hexInput,
                    onValueChange = { input ->
                        hexInput = input
                        parseError = input.isNotBlank() && ColorHex.parseRgb(input) == null
                    },
                    label = "Hex Color (#RRGGBB)",
                    isError = parseError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (parseError) {
                    Text(
                        text = t("Invalid hex color"),
                        color = tokens.colors.error,
                        style = tokens.type.bodySmall
                    )
                }
            }
        }
    )
}

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

/**
 * Element-physical dp helper — for swatch sizes / icon sizes that are
 * fixed visual dimensions (not theme-driven spacing). Prefer
 * `tokens.space.X` for layout spacing.
 */
private fun Int.dpUnit() = androidx.compose.ui.unit.Dp(this.toFloat())

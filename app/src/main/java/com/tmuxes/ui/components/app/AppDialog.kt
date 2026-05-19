package com.tmuxes.ui.components.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tmuxes.i18n.LocalI18n
import com.tmuxes.i18n.t
import com.tmuxes.ui.design.appTokens

/**
 * Universal modal dialog. Every modal in the app should go through this
 * token-driven surface so shape, spacing, scrolling and action layout
 * stay consistent across screens.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String? = "Cancel",
    onDismissAction: (() -> Unit)? = null,
    confirmStyle: AppButtonStyle = AppButtonStyle.Primary,
    confirmEnabled: Boolean = true,
    neutralLabel: String? = null,
    onNeutral: (() -> Unit)? = null,
    neutralEnabled: Boolean = true,
    neutralStyle: AppButtonStyle = AppButtonStyle.Text,
    contentScrollable: Boolean = true,
    customActions: (@Composable FlowRowScope.() -> Unit)? = null,
    text: String? = null,
    textArgs: Map<String, Any?> = emptyMap(),
    content: @Composable (() -> Unit)? = null
) {
    val tokens = MaterialTheme.appTokens
    val i18n = LocalI18n.current
    val dialogTitle = t(title)
    val dialogText = text?.let {
        if (textArgs.isEmpty()) i18n.t(it) else i18n.t(it, textArgs)
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.space.lg)
                .widthIn(max = 560.dpUnit()),
            shape = tokens.shape.lg,
            color = tokens.colors.surfaceContainerHigh,
            contentColor = tokens.colors.onSurface,
            tonalElevation = tokens.elevation.level1,
            shadowElevation = tokens.elevation.level4
        ) {
            Column(
                modifier = Modifier.padding(tokens.space.xl),
                verticalArrangement = Arrangement.spacedBy(tokens.space.lg)
            ) {
                Text(
                    text = dialogTitle,
                    style = tokens.type.titleLarge,
                    color = tokens.colors.onSurface
                )

                if (dialogText != null || content != null) {
                    val body: @Composable () -> Unit = {
                        Column(verticalArrangement = Arrangement.spacedBy(tokens.space.md)) {
                            if (dialogText != null) {
                                Text(
                                    text = dialogText,
                                    style = tokens.type.bodyMedium,
                                    color = tokens.colors.onSurfaceVariant
                                )
                            }
                            if (content != null) content()
                        }
                    }
                    if (contentScrollable) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 460.dpUnit())
                                .appElasticVerticalScroll(rememberScrollState())
                        ) {
                            body()
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 460.dpUnit())
                        ) {
                            body()
                        }
                    }
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(tokens.space.sm, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(tokens.space.xs)
                ) {
                    if (customActions != null) {
                        customActions()
                    } else {
                        if (dismissLabel != null) {
                            AppButton(
                                text = dismissLabel,
                                onClick = onDismissAction ?: onDismiss,
                                style = AppButtonStyle.Text
                            )
                        }
                        if (neutralLabel != null && onNeutral != null) {
                            AppButton(
                                text = neutralLabel,
                                onClick = onNeutral,
                                style = neutralStyle,
                                enabled = neutralEnabled
                            )
                        }
                        AppButton(
                            text = confirmLabel,
                            onClick = onConfirm,
                            style = confirmStyle,
                            enabled = confirmEnabled
                        )
                    }
                }
            }
        }
    }
}

/**
 * Common destructive-confirmation dialog. Replaces every "are you sure
 * you want to delete X" pattern across the app.
 */
@Composable
fun AppDeleteDialog(
    title: String,
    message: String,
    messageArgs: Map<String, Any?> = emptyMap(),
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = "Delete"
) {
    AppDialog(
        title = title,
        text = message,
        textArgs = messageArgs,
        onDismiss = onDismiss,
        confirmLabel = confirmLabel,
        onConfirm = onConfirm,
        confirmStyle = AppButtonStyle.Danger
    )
}

private fun Int.dpUnit() = androidx.compose.ui.unit.Dp(this.toFloat())

package com.tmuxes.ui.components.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.tmuxes.i18n.t
import com.tmuxes.ui.design.IdentityColors
import com.tmuxes.ui.design.appTokens

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IdentityColorPicker(
    selectedColor: Int,
    onColorSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = MaterialTheme.appTokens
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(tokens.space.sm),
        verticalArrangement = Arrangement.spacedBy(tokens.space.sm)
    ) {
        IdentityColors.presets.forEach { option ->
            val selected = option.argb == selectedColor ||
                (selectedColor != IdentityColors.NONE &&
                    (option.darkArgb == selectedColor || option.lightArgb == selectedColor))
            Box(
                modifier = Modifier
                    .size(36.dpUnit())
                    .clip(CircleShape)
                    .background(tokens.colors.surfaceVariant)
                    .border(
                        width = if (selected) 2.dpUnit() else 1.dpUnit(),
                        color = if (selected) tokens.colors.onSurface else tokens.colors.outlineVariant,
                        shape = CircleShape
                    )
                    .clickable { onColorSelected(option.argb) },
                contentAlignment = Alignment.Center
            ) {
                if (option.argb != IdentityColors.NONE) {
                    Row(Modifier.fillMaxSize()) {
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(Color(option.darkArgb))
                        )
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(Color(option.lightArgb))
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = t(option.label),
                        tint = tokens.colors.onSurfaceVariant,
                        modifier = Modifier.size(16.dpUnit())
                    )
                }
            }
        }
    }
}

private fun Int.dpUnit() = androidx.compose.ui.unit.Dp(this.toFloat())

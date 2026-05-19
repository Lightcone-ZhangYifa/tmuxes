package com.tmuxes.ui.components.app

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tmuxes.ui.design.appTokens

/** 1dp divider using `tokens.colors.divider`. Replaces 38 hand-rolled `outline.copy(alpha=0.5f)` calls. */
@Composable
fun AppHorizontalDivider(
    modifier: Modifier = Modifier,
    inset: Boolean = false
) {
    val tokens = MaterialTheme.appTokens
    HorizontalDivider(
        modifier = if (inset) modifier.padding(horizontal = tokens.space.lg) else modifier,
        thickness = 1.dp,
        color = tokens.colors.divider
    )
}

@Composable
fun AppVerticalDivider(modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.appTokens
    VerticalDivider(
        modifier = modifier,
        thickness = 1.dp,
        color = tokens.colors.divider
    )
}

/** Vertical spacing between section blocks — uses `tokens.space.lg` by default. */
@Composable
fun AppVerticalSpacer(size: AppSpacerSize = AppSpacerSize.Lg) {
    val tokens = MaterialTheme.appTokens
    val dp = when (size) {
        AppSpacerSize.Xs -> tokens.space.xs
        AppSpacerSize.Sm -> tokens.space.sm
        AppSpacerSize.Md -> tokens.space.md
        AppSpacerSize.Lg -> tokens.space.lg
        AppSpacerSize.Xl -> tokens.space.xl
        AppSpacerSize.Xxl -> tokens.space.xxl
    }
    Spacer(Modifier.height(dp))
}

@Composable
fun AppHorizontalSpacer(size: AppSpacerSize = AppSpacerSize.Sm) {
    val tokens = MaterialTheme.appTokens
    val dp = when (size) {
        AppSpacerSize.Xs -> tokens.space.xs
        AppSpacerSize.Sm -> tokens.space.sm
        AppSpacerSize.Md -> tokens.space.md
        AppSpacerSize.Lg -> tokens.space.lg
        AppSpacerSize.Xl -> tokens.space.xl
        AppSpacerSize.Xxl -> tokens.space.xxl
    }
    Spacer(Modifier.width(dp))
}

enum class AppSpacerSize { Xs, Sm, Md, Lg, Xl, Xxl }

package com.tmuxes.ui.components.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tmuxes.i18n.t
import com.tmuxes.ui.design.appTokens

/**
 * Section header — uppercase tracking-extended label using
 * `type.sectionHeader`. Replaces 7 sites that hand-rolled
 * `letterSpacing = 1.5.sp` + `labelMedium`.
 */
@Composable
fun AppSectionHeader(
    text: String,
    modifier: Modifier = Modifier
) {
    val tokens = MaterialTheme.appTokens
    Text(
        text = t(text).uppercase(),
        style = tokens.type.sectionHeader,
        color = tokens.colors.primary,
        modifier = modifier.padding(
            start = tokens.space.xs,
            top = tokens.space.lg,
            bottom = tokens.space.xs
        )
    )
}

/**
 * A grouped section: header + content slot. The content is
 * vertically arranged with [AppCard] semantics — caller passes
 * children which get a single combined card.
 */
@Composable
fun AppSection(
    title: String?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val tokens = MaterialTheme.appTokens
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            AppSectionHeader(text = title)
        }
        AppCard(modifier = Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp())) {
            content()
        }
    }
}

private fun Int.dp() = androidx.compose.ui.unit.Dp(this.toFloat())

package com.tmuxes.ui.components.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.tmuxes.i18n.t
import com.tmuxes.ui.design.appTokens

/**
 * Universal top app bar. Replaces 12 hand-rolled `TopAppBar`/
 * `CenterAlignedTopAppBar` blocks across the screens.
 *
 * The [navigation] parameter chooses leading icon: back arrow, menu
 * (drawer), or none. Actions are a free composable slot for trailing
 * icon buttons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    titleIcon: ImageVector? = null,
    titleMeta: String? = null,
    navigation: AppTopBarNav = AppTopBarNav.None,
    onNavClick: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    centered: Boolean = false,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    subtitle: String? = null
) {
    val tokens = MaterialTheme.appTokens
    val titleText = t(title)
    val subtitleText = subtitle?.let { t(it) }
    val titleSlot: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (titleIcon != null) {
                Surface(
                    shape = tokens.shape.sm,
                    color = tokens.colors.primaryContainer,
                    contentColor = tokens.colors.onPrimaryContainer
                ) {
                    Box(
                        modifier = Modifier.size(tokens.space.xxl),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = titleIcon,
                            contentDescription = null,
                            modifier = Modifier.size(tokens.space.lg + tokens.space.xs)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(tokens.space.md))
            }
            if (subtitleText != null) {
                Column {
                    AppTopBarTitleLine(title = titleText, meta = titleMeta)
                    Text(
                        text = subtitleText,
                        style = tokens.type.bodyMedium,
                        color = tokens.colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                AppTopBarTitleLine(title = titleText, meta = titleMeta)
            }
        }
    }
    val navSlot: @Composable () -> Unit = {
        when (navigation) {
            AppTopBarNav.Back -> AppIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                onClick = { onNavClick?.invoke() },
                contentDescription = t("Back")
            )
            AppTopBarNav.Drawer -> AppIconButton(
                icon = Icons.Filled.Menu,
                onClick = { onNavClick?.invoke() },
                contentDescription = t("Menu")
            )
            AppTopBarNav.None -> Unit
        }
    }
    val colors = TopAppBarDefaults.topAppBarColors(
        containerColor = tokens.colors.surface,
        scrolledContainerColor = tokens.colors.surfaceContainer,
        navigationIconContentColor = tokens.colors.onSurface,
        titleContentColor = tokens.colors.onSurface,
        actionIconContentColor = tokens.colors.onSurface
    )
    if (centered) {
        CenterAlignedTopAppBar(
            modifier = modifier,
            title = titleSlot,
            navigationIcon = navSlot,
            actions = { Row { actions() } },
            colors = colors,
            scrollBehavior = scrollBehavior
        )
    } else {
        TopAppBar(
            modifier = modifier,
            title = titleSlot,
            navigationIcon = navSlot,
            actions = { Row { actions() } },
            colors = colors,
            scrollBehavior = scrollBehavior
        )
    }
}

enum class AppTopBarNav { None, Back, Drawer }

@Composable
private fun AppTopBarTitleLine(
    title: String,
    meta: String?
) {
    val tokens = MaterialTheme.appTokens
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = tokens.type.titleLarge,
            color = tokens.colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (meta != null) Modifier.weight(1f, fill = false) else Modifier
        )
        if (meta != null) {
            Spacer(modifier = Modifier.width(tokens.space.sm))
            Text(
                text = meta,
                style = tokens.type.labelLarge,
                color = tokens.colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

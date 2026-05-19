package com.tmuxes.ui.components.app

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Dvr
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GetApp
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.MiscellaneousServices
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.tmuxes.i18n.t
import com.tmuxes.ui.design.appTokens

/**
 * String → ImageVector for snippet libraries (single source of truth).
 * Falls back to a Code icon if the user hand-edited the YAML to an
 * unknown name.
 */
fun libraryIcon(iconName: String): ImageVector = when (iconName) {
    "Dvr" -> Icons.AutoMirrored.Filled.Dvr
    "MiscellaneousServices" -> Icons.Filled.MiscellaneousServices
    "Inventory2" -> Icons.Filled.Inventory2
    "Folder" -> Icons.Filled.Folder
    "Lan" -> Icons.Filled.Lan
    "Monitor" -> Icons.Filled.Monitor
    "Code" -> Icons.Filled.Code
    "GetApp" -> Icons.Filled.GetApp
    "Article" -> Icons.AutoMirrored.Filled.Article
    "Security" -> Icons.Filled.Security
    "Terminal" -> Icons.Filled.Terminal
    "Storage" -> Icons.Filled.Storage
    "Build" -> Icons.Filled.Build
    "BugReport" -> Icons.Filled.BugReport
    "Cloud" -> Icons.Filled.Cloud
    else -> Icons.Filled.Code
}

/**
 * Section header used inside the terminal command-panel sheet — title
 * on the left, a thin divider filling the rest of the row.
 */
@Composable
fun CommandPanelSectionHeader(title: String) {
    val tokens = MaterialTheme.appTokens
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = t(title),
            style = tokens.type.labelLarge,
            color = tokens.colors.primary
        )
        Spacer(modifier = Modifier.width(tokens.space.md))
        HorizontalDivider(color = tokens.colors.outlineVariant, modifier = Modifier.weight(1f))
    }
}

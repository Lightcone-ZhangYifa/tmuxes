package com.tmuxes.ui.components.app

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tmuxes.i18n.t
import com.tmuxes.ui.design.appTokens

/**
 * The unified multi-select top bar — pass to `AppScaffold(topBar = ...)`
 * via the topBar escape hatch when [AppMultiSelectState.isActive] is true.
 *
 * Pair with [rememberAppMultiSelectState].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppMultiSelectTopBar(
    selectedCount: Int,
    totalCount: Int,
    isAllSelected: Boolean,
    onSelectAllToggle: () -> Unit,
    onClose: () -> Unit,
    actions: @Composable RowScope.() -> Unit
) {
    val tokens = MaterialTheme.appTokens
    val selectedText = t("{count} selected", "count" to selectedCount)
    TopAppBar(
        navigationIcon = {
            val closeInteractionSource = remember { MutableInteractionSource() }
            IconButton(
                onClick = onClose,
                modifier = Modifier.appPressFeedback(closeInteractionSource),
                interactionSource = closeInteractionSource
            ) {
                Icon(Icons.Filled.Close, contentDescription = t("Close"))
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = selectedText,
                    style = tokens.type.titleMedium
                )
                Spacer(Modifier.width(16.dp))
                Row(
                    modifier = Modifier
                        .appPressable(onClick = onSelectAllToggle)
                        .padding(vertical = 4.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isAllSelected,
                        onCheckedChange = { onSelectAllToggle() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = tokens.colors.primary
                        )
                    )
                    Text(
                        text = t("All"),
                        style = tokens.type.bodyMedium
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = tokens.colors.primaryContainer,
            navigationIconContentColor = tokens.colors.onPrimaryContainer,
            titleContentColor = tokens.colors.onPrimaryContainer,
            actionIconContentColor = tokens.colors.onPrimaryContainer
        )
    )
}

package com.tmuxes.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tmuxes.i18n.I18n
import com.tmuxes.i18n.LocalI18n
import com.tmuxes.i18n.t
import com.tmuxes.ui.components.app.appPressFeedback
import com.tmuxes.ui.components.app.appPressable
import io.github.rosemoe.sora.event.EventManager
import io.github.rosemoe.sora.event.PublishSearchResultEvent
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher

/**
 * Body of the Find bubble — VS Code-style search + optional replace.
 *
 * Layout (compact, single row by default; two rows with replace expanded):
 *
 * ```
 * ▶ [search…  Aa .* ]  N of M  ▲ ▼ ✕
 *    [replace…       ]         ↩ ⏬
 * ```
 *
 * - Left chevron toggles the replace row (▶ collapsed / ▼ expanded).
 * - Aa / .* are inline trailing toggles inside the search input box.
 * - Match counter "N of M" sits between input and nav buttons.
 * - Replace row is indented to align under the search input.
 *
 * Internal state persists across bubble open/close because
 * [com.tmuxes.ui.components.app.AppFabBubble] always composes its content.
 * `rememberSaveable` further survives configuration changes.
 */
@Composable
fun FindBubbleContent(
    editor: CodeEditor?,
    modifier: Modifier = Modifier
) {
    var searchText by rememberSaveable { mutableStateOf("") }
    var replaceText by rememberSaveable { mutableStateOf("") }
    var showReplace by rememberSaveable { mutableStateOf(false) }
    var caseSensitive by rememberSaveable { mutableStateOf(false) }
    var useRegex by rememberSaveable { mutableStateOf(false) }
    // matchTick bumps after every nav/replace so the "N of M" derivedState recomputes.
    var matchTick by remember { mutableStateOf(0) }

    fun runSearch() {
        val ed = editor ?: return
        if (searchText.isEmpty()) {
            ed.searcher.stopSearch()
            matchTick++
            return
        }
        val type = if (useRegex) EditorSearcher.SearchOptions.TYPE_REGULAR_EXPRESSION
                   else EditorSearcher.SearchOptions.TYPE_NORMAL
        ed.searcher.search(
            searchText,
            EditorSearcher.SearchOptions(type, !caseSensitive)
        )
        matchTick++
    }

    LaunchedEffect(searchText, caseSensitive, useRegex) { runSearch() }

    // Sora runs search() on a background thread; matchedPositionCount is 0
    // immediately after search() returns, and currentMatchedPositionIndex is -1
    // until the user navigates. Subscribe to PublishSearchResultEvent so the
    // "N of M" counter updates when results land, and auto-jump to the first
    // match (VSCode behavior) so the index starts at 1 instead of "0 of N".
    DisposableEffect(editor) {
        val receipt = editor?.subscribeAlways(
            PublishSearchResultEvent::class.java,
            EventManager.NoUnsubscribeReceiver<PublishSearchResultEvent> { ev ->
                val s = ev.getSearcher()
                try {
                    if (s.hasQuery() && s.matchedPositionCount > 0 && s.currentMatchedPositionIndex < 0) {
                        s.gotoNext()
                    }
                } catch (_: Throwable) { /* display logic also clamps */ }
                matchTick++
            }
        )
        onDispose { receipt?.unsubscribe() }
    }

    val i18n = LocalI18n.current
    val matchInfo by remember(editor, i18n) {
        derivedStateOf {
            @Suppress("UNUSED_EXPRESSION") matchTick // recompute trigger
            computeMatchInfo(editor, i18n)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CompactIconButton(
                icon = if (showReplace) Icons.Filled.KeyboardArrowDown
                       else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Toggle Replace",
                onClick = { showReplace = !showReplace }
            )
            Spacer(Modifier.width(4.dp))

            SearchInput(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = "Search",
                imeAction = ImeAction.Search,
                trailing = {
                    InlineToggleText("Aa", caseSensitive) { caseSensitive = !caseSensitive }
                    InlineToggleText(".*", useRegex) { useRegex = !useRegex }
                },
                modifier = Modifier.weight(1f)
            )

            Spacer(Modifier.width(6.dp))
            Text(
                text = matchInfo,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(min = 36.dp).padding(end = 4.dp)
            )

            CompactIconButton(
                icon = Icons.Filled.KeyboardArrowUp,
                contentDescription = "Previous match",
                enabled = editor?.searcher?.hasQuery() == true,
                onClick = {
                    editor?.searcher?.gotoPrevious()
                    matchTick++
                }
            )
            CompactIconButton(
                icon = Icons.Filled.KeyboardArrowDown,
                contentDescription = "Next match",
                enabled = editor?.searcher?.hasQuery() == true,
                onClick = {
                    editor?.searcher?.gotoNext()
                    matchTick++
                }
            )
            CompactIconButton(
                icon = Icons.Filled.Close,
                contentDescription = "Close find",
                onClick = {
                    searchText = ""
                    editor?.searcher?.stopSearch()
                    matchTick++
                }
            )
        }

        AnimatedVisibility(
            visible = showReplace,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Indent so the replace input aligns under the search input
                // (chevron 28dp + 4dp spacer).
                Spacer(Modifier.width(28.dp + 4.dp))

                SearchInput(
                    value = replaceText,
                    onValueChange = { replaceText = it },
                    placeholder = "Replace",
                    imeAction = ImeAction.Done,
                    trailing = null,
                    modifier = Modifier.weight(1f)
                )

                Spacer(Modifier.width(6.dp))
                // Spacer to mirror the match-counter slot above.
                Spacer(Modifier.widthIn(min = 36.dp).width(36.dp).padding(end = 4.dp))

                CompactIconButton(
                    icon = Icons.AutoMirrored.Filled.KeyboardReturn,
                    contentDescription = "Replace one",
                    enabled = editor?.searcher?.hasQuery() == true,
                    onClick = {
                        editor?.searcher?.replaceCurrentMatch(replaceText)
                        matchTick++
                    }
                )
                CompactIconButton(
                    icon = Icons.Filled.DoneAll,
                    contentDescription = "Replace all",
                    enabled = editor?.searcher?.hasQuery() == true,
                    onClick = {
                        editor?.searcher?.replaceAll(replaceText)
                        matchTick++
                    }
                )
                // Trailing 28dp slot to mirror the close button above.
                Spacer(Modifier.width(28.dp))
            }
        }
    }
}

@Composable
private fun CompactIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(28.dp)
            .appPressFeedback(interactionSource, enabled),
        interactionSource = interactionSource
    ) {
        Icon(icon, t(contentDescription), modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun InlineToggleText(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val fg = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
             else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .padding(horizontal = 1.dp)
            .size(22.dp)
            .background(bg, RoundedCornerShape(4.dp))
            .appPressable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, fontSize = 11.sp, color = fg)
    }
}

@Composable
private fun SearchInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    imeAction: ImeAction,
    trailing: (@Composable RowScope.() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = imeAction),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = t(placeholder),
                            color = onSurfaceVariant.copy(alpha = 0.5f),
                            fontSize = 13.sp
                        )
                    }
                    inner()
                }
            }
        )
        trailing?.invoke(this)
    }
}

/**
 * Compute "N of M" from the editor's searcher state. Returns "" when no
 * query is active, "No matches" when the search returned nothing.
 *
 * Wrapped in try/catch because Sora's `matchedPositions` can throw when
 * accessed during a search re-run (background thread state transition).
 */
private fun computeMatchInfo(editor: CodeEditor?, i18n: I18n): String {
    val s = editor?.searcher ?: return ""
    if (!s.hasQuery()) return ""
    return try {
        val total = s.matchedPositionCount
        if (total == 0) return i18n.t("No matches")
        // currentMatchedPositionIndex is -1 between search-issued and search-published
        // (or if user hasn't navigated). Clamp to 1 so we never show "0 of N" alongside
        // visibly highlighted matches.
        val idx = s.currentMatchedPositionIndex.coerceAtLeast(0) + 1
        i18n.t("{current} of {total}", mapOf("current" to idx, "total" to total))
    } catch (_: Throwable) {
        ""
    }
}

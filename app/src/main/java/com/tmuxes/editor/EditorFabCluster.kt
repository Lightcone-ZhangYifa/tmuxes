package com.tmuxes.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.tmuxes.ui.components.app.AppFab
import com.tmuxes.ui.components.app.AppFabBadge
import com.tmuxes.ui.components.app.AppFabBadgeTone
import com.tmuxes.ui.components.app.AppFabBubble
import com.tmuxes.ui.components.app.AppFabBubbleController
import com.tmuxes.ui.components.app.rememberAppFabBubbleController
import com.tmuxes.ui.design.appTokens
import io.github.rosemoe.sora.widget.CodeEditor

private const val ID_PROBLEMS = "editor.problems"
private const val ID_OUTPUT = "editor.output"
private const val ID_FIND = "editor.find"

/**
 * The Editor's bottom-end FAB cluster: 3 mutually-exclusive popover
 * bubbles for Problems / Output / Find. Replaces the previous
 * `OutputPane` TabRow + drag-to-resize layout.
 *
 * Drop into a Box (typically the screen-level Box that also hosts the
 * editor body). Renders both the FAB column and the bubble overlays —
 * caller does not need to manage state.
 *
 * @param diagnostics live YAML diagnostics; drives the Problems bubble
 *   and the FAB badge (red dot for errors, yellow for warnings).
 * @param outputLog append-only log shown in the Output bubble.
 * @param editor optional Sora editor reference for Find search/replace
 *   and for jumping to a diagnostic line on tap.
 * @param onDiagnosticClick called with the 0-based line of the tapped
 *   problem; usually wired to `editor?.setSelection(line, 0)`.
 */
@Composable
fun BoxScope.EditorFabCluster(
    diagnostics: List<YamlDiagnosticAnalyzer.Diagnostic>,
    outputLog: List<OutputLogEntry>,
    editor: CodeEditor?,
    onDiagnosticClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = MaterialTheme.appTokens
    val controller: AppFabBubbleController = rememberAppFabBubbleController()

    val errorCount = diagnostics.count { it.severity == YamlDiagnosticAnalyzer.Severity.ERROR }
    val warningCount = diagnostics.count { it.severity == YamlDiagnosticAnalyzer.Severity.WARNING }
    val problemsBadge: AppFabBadge? = when {
        errorCount > 0 -> AppFabBadge(
            tone = AppFabBadgeTone.Danger,
            text = if (errorCount > 99) "99+" else errorCount.toString()
        )
        warningCount > 0 -> AppFabBadge(
            tone = AppFabBadgeTone.Warning,
            text = if (warningCount > 99) "99+" else warningCount.toString()
        )
        else -> null
    }
    val outputBadge: AppFabBadge? =
        if (outputLog.isNotEmpty()) AppFabBadge(AppFabBadgeTone.Info) else null

    // Bubbles — always composed (state persists across open/close);
    // visibility-gated by the controller.
    AppFabBubble(
        visible = controller.isOpen(ID_PROBLEMS),
        onDismiss = controller::close
    ) {
        ProblemsBubbleContent(
            diagnostics = diagnostics,
            onDiagnosticClick = { line ->
                onDiagnosticClick(line)
                controller.close()
            }
        )
    }
    AppFabBubble(
        visible = controller.isOpen(ID_OUTPUT),
        onDismiss = controller::close
    ) {
        OutputBubbleContent(outputLog = outputLog)
    }
    AppFabBubble(
        visible = controller.isOpen(ID_FIND),
        onDismiss = controller::close
    ) {
        FindBubbleContent(editor = editor)
    }

    // FAB column — bottom-end of parent Box.
    Column(
        modifier = modifier
            .align(Alignment.BottomEnd)
            .padding(tokens.space.md),
        verticalArrangement = Arrangement.spacedBy(tokens.space.sm)
    ) {
        AppFab(
            icon = Icons.Filled.ErrorOutline,
            onClick = { controller.toggle(ID_PROBLEMS) },
            contentDescription = "Problems",
            selected = controller.isOpen(ID_PROBLEMS),
            badge = problemsBadge
        )
        AppFab(
            icon = Icons.Filled.Terminal,
            onClick = { controller.toggle(ID_OUTPUT) },
            contentDescription = "Output",
            selected = controller.isOpen(ID_OUTPUT),
            badge = outputBadge
        )
        AppFab(
            icon = Icons.Filled.Search,
            onClick = { controller.toggle(ID_FIND) },
            contentDescription = "Find",
            selected = controller.isOpen(ID_FIND)
        )
    }
}

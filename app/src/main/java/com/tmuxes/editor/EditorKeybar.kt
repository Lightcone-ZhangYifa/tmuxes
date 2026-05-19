package com.tmuxes.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tmuxes.ui.components.keybar.KeyButton
import com.tmuxes.ui.components.keybar.KeySpec
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.SelectionMovement

private val SYM_SIZE = 14.sp
private const val EDITOR_KEY_HEIGHT_DP = 32

@Composable
fun EditorKeybar(
    editor: CodeEditor?,
    onGoLine: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var fnActive by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val runner = remember(editor, context) { editor?.let { EditorActionRunner(it, context) } }
    val tabWidth = editor?.tabWidth ?: 2

    val bg = MaterialTheme.colorScheme.surfaceVariant
    val bgActive = MaterialTheme.colorScheme.surfaceBright
    val fg = MaterialTheme.colorScheme.onSurface

    fun dispatch(action: KeyAction) {
        runner?.dispatch(action) { fnActive = !fnActive }
    }

    val keyContent: @Composable (KeySpec, Modifier) -> Unit = { spec, weightModifier ->
        KeyButton(
            spec = spec,
            background = bg,
            backgroundActive = bgActive,
            contentColor = fg,
            keyHeight = EDITOR_KEY_HEIGHT_DP,
            modifier = weightModifier,
        )
    }

    Surface(modifier = modifier.fillMaxWidth(), tonalElevation = 2.dp) {
        Column(
            Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (!fnActive) {
                Page1Row1(keyContent, onGoLine, ::dispatch)
                Page1Row2(keyContent, tabWidth, fnActive, ::dispatch)
            } else {
                Page2Row1(keyContent, ::dispatch)
                Page2Row2(keyContent, fnActive, ::dispatch)
            }
        }
    }
}

@Composable
private fun Page1Row1(
    Key: @Composable (KeySpec, Modifier) -> Unit,
    onGoLine: () -> Unit,
    dispatch: (KeyAction) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(2.dp)) {
        Key(actionKey("Undo") { dispatch(KeyAction.Native { it.editor.undo() }) }, Modifier.weight(1f))
        Key(actionKey("Redo") { dispatch(KeyAction.Native { it.editor.redo() }) }, Modifier.weight(1f))
        Key(actionKey("Cut") {
            dispatch(
                KeyAction.Native { context ->
                    val editor = context.editor
                    if (editor.cursor.isSelected) {
                        val text = editor.text.subSequence(editor.cursor.left, editor.cursor.right).toString()
                        context.writeClip(text, ClipKind.TEXT)
                        editor.deleteText()
                    } else {
                        val lineText = editor.text.getLineString(editor.cursor.leftLine) + "\n"
                        context.writeClip(lineText, ClipKind.LINE)
                        context.applyMutate(DocCommands.DeleteLines)
                    }
                },
            )
        }, Modifier.weight(1f))
        Key(actionKey("Copy") {
            dispatch(
                KeyAction.Native { context ->
                    val editor = context.editor
                    if (editor.cursor.isSelected) {
                        val text = editor.text.subSequence(editor.cursor.left, editor.cursor.right).toString()
                        context.writeClip(text, ClipKind.TEXT)
                    } else {
                        val lineText = editor.text.getLineString(editor.cursor.leftLine) + "\n"
                        context.writeClip(lineText, ClipKind.LINE)
                    }
                },
            )
        }, Modifier.weight(1f))
        Key(actionKey("Paste") {
            dispatch(
                KeyAction.Native { context ->
                    val clip = context.readClip()
                    when (clip.kind) {
                        ClipKind.LINE -> context.applyMutate(DocCommands.pasteLineAbove(clip.text))
                        ClipKind.TEXT -> context.editor.pasteText()
                    }
                },
            )
        }, Modifier.weight(1f))
        Key(actionKey("Dup") { dispatch(KeyAction.Mutate(DocCommands.Duplicate)) }, Modifier.weight(1f))
        Key(actionKey("Delete Line") { dispatch(KeyAction.Mutate(DocCommands.DeleteLines)) }, Modifier.weight(1f))
        Key(actionKey("Go") { dispatch(KeyAction.External { onGoLine() }) }, Modifier.weight(1f))
    }
}

@Composable
private fun Page1Row2(
    Key: @Composable (KeySpec, Modifier) -> Unit,
    tabWidth: Int,
    fnActive: Boolean,
    dispatch: (KeyAction) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(2.dp)) {
        Key(KeySpec.Once("⇥", fontSize = SYM_SIZE) {
            dispatch(KeyAction.Mutate(DocCommands.smartTab(tabWidth)))
        }, Modifier.weight(1f))
        Key(actionKey("Sel") {
            dispatch(
                KeyAction.Native { context ->
                    val editor = context.editor
                    val lineCount = editor.text.lineCount
                    if (lineCount == 0) return@Native
                    val lastLine = lineCount - 1
                    val lastCol = editor.text.getLineString(lastLine).length
                    editor.setSelectionRegion(0, 0, lastLine, lastCol, /* makeRightVisible = */ false)
                },
            )
        }, Modifier.weight(1f))
        Key(actionKey("Cmnt") {
            dispatch(KeyAction.Mutate(DocCommands.ToggleLineComment))
        }, Modifier.weight(1f))
        Key(actionKey("Ind+") {
            dispatch(KeyAction.Mutate(DocCommands.indent(tabWidth)))
        }, Modifier.weight(1f))
        Key(actionKey("Ind-") {
            dispatch(KeyAction.Mutate(DocCommands.outdent(tabWidth)))
        }, Modifier.weight(1f))
        Key(actionKey("Mv↑") {
            dispatch(KeyAction.Mutate(DocCommands.MoveLinesUp))
        }, Modifier.weight(1f))
        Key(actionKey("Mv↓") {
            dispatch(KeyAction.Mutate(DocCommands.MoveLinesDown))
        }, Modifier.weight(1f))
        Key(
            KeySpec.Toggle(
                label = if (fnActive) "Fn •" else "Fn",
                active = fnActive,
                onToggle = { dispatch(KeyAction.FnToggle) },
            ),
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun Page2Row1(
    Key: @Composable (KeySpec, Modifier) -> Unit,
    dispatch: (KeyAction) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(2.dp)) {
        symKey(Key, ":", dispatch)
        symKey(Key, "-", dispatch)
        symKey(Key, "\"", dispatch)
        symKey(Key, "'", dispatch)
        symKey(Key, "#", dispatch)
        navKey(Key, "↑", SelectionMovement.UP, dispatch)
        symKey(Key, "*", dispatch)
        symKey(Key, "&", dispatch)
    }
}

@Composable
private fun Page2Row2(
    Key: @Composable (KeySpec, Modifier) -> Unit,
    fnActive: Boolean,
    dispatch: (KeyAction) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(2.dp)) {
        symKey(Key, "[", dispatch)
        symKey(Key, "]", dispatch)
        symKey(Key, "{", dispatch)
        symKey(Key, "}", dispatch)
        navKey(Key, "←", SelectionMovement.LEFT, dispatch)
        navKey(Key, "↓", SelectionMovement.DOWN, dispatch)
        navKey(Key, "→", SelectionMovement.RIGHT, dispatch)
        Key(
            KeySpec.Toggle(
                label = if (fnActive) "Fn •" else "Fn",
                active = fnActive,
                onToggle = { dispatch(KeyAction.FnToggle) },
            ),
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun RowScope.symKey(
    Key: @Composable (KeySpec, Modifier) -> Unit,
    sym: String,
    dispatch: (KeyAction) -> Unit,
) {
    Key(
        KeySpec.Once(sym, fontSize = SYM_SIZE) {
            dispatch(KeyAction.Native { it.editor.commitText(sym) })
        },
        Modifier.weight(1f),
    )
}

@Composable
private fun RowScope.navKey(
    Key: @Composable (KeySpec, Modifier) -> Unit,
    label: String,
    movement: SelectionMovement,
    dispatch: (KeyAction) -> Unit,
) {
    Key(
        KeySpec.Once(label, fontSize = SYM_SIZE) {
            dispatch(KeyAction.Native { it.editor.moveSelection(movement) })
        },
        Modifier.weight(1f),
    )
}

private fun actionKey(label: String, onPress: () -> Unit): KeySpec.Once =
    KeySpec.Once(label = label, translateLabel = true, onPress = onPress)

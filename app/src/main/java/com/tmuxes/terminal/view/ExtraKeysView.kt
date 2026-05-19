package com.tmuxes.terminal.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tmuxes.ui.components.keybar.KeyButton
import com.tmuxes.ui.components.keybar.KeySpec
import com.tmuxes.ui.components.keybar.ModifierSnapshot

/**
 * Two-page extra-keys bar with FN toggle.
 *
 * Modifier keys (Ctrl/Alt/Shift/Meta) and the Fn page-toggle are
 * [KeySpec.Toggle]; every other key is [KeySpec.Repeat]. Modifier state
 * is read off [latch] at press-down and applied to every emit during a
 * Repeat hold (see [com.tmuxes.ui.components.keybar.KeyButton] +
 * ModifierSnapshotTest for the snapshot semantics).
 */
@Composable
fun ExtraKeysBar(
    onKeyPress: (ByteArray) -> Unit,
    latch: TerminalModifierLatch,
    applicationCursorKeys: Boolean = false,
    keyHeight: Int = KEY_HEIGHT_DP,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    var fnActive by remember { mutableStateOf(false) }

    val keyBg = MaterialTheme.colorScheme.surfaceVariant
    val keyBgActive = MaterialTheme.colorScheme.surfaceBright

    val currentApplicationCursorKeys by rememberUpdatedState(applicationCursorKeys)
    val currentOnKeyPress by rememberUpdatedState(onKeyPress)

    // Snapshot read at press-down by KeyButton — see ModifierSnapshotTest.
    val modifiersProvider: () -> ModifierSnapshot = remember(latch) { { latch.snapshot } }
    val onModifiersConsumed: () -> Unit = remember(latch) { { latch.consume() } }

    val safeKeyHeight = keyHeight.coerceIn(16, 96)

    // Arrow byte sequences depend on remote shell's applicationCursorKeys
    // protocol state — read fresh per emit, NOT snapshotted.
    fun arrowUp() = if (currentApplicationCursorKeys) "\u001bOA".toByteArray() else "\u001b[A".toByteArray()
    fun arrowDown() = if (currentApplicationCursorKeys) "\u001bOB".toByteArray() else "\u001b[B".toByteArray()
    fun arrowRight() = if (currentApplicationCursorKeys) "\u001bOC".toByteArray() else "\u001b[C".toByteArray()
    fun arrowLeft() = if (currentApplicationCursorKeys) "\u001bOD".toByteArray() else "\u001b[D".toByteArray()

    val keyContent: @Composable (KeySpec, Modifier) -> Unit = { spec, weightModifier ->
        KeyButton(
            spec = spec,
            background = keyBg,
            backgroundActive = keyBgActive,
            contentColor = contentColor,
            keyHeight = safeKeyHeight,
            modifier = weightModifier,
            modifiersProvider = modifiersProvider,
            onEmit = currentOnKeyPress,
            onModifiersConsumed = onModifiersConsumed,
        )
    }
    val arrowUpFn: () -> ByteArray = ::arrowUp
    val arrowDownFn: () -> ByteArray = ::arrowDown
    val arrowLeftFn: () -> ByteArray = ::arrowLeft
    val arrowRightFn: () -> ByteArray = ::arrowRight

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = containerColor,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (!fnActive) {
                DefaultPage1(
                    keyContent, latch,
                    arrowUpFn, arrowDownFn, arrowLeftFn, arrowRightFn,
                    onFnToggle = { fnActive = !fnActive },
                    fnActive = fnActive,
                )
            } else {
                DefaultPage2(
                    keyContent, latch,
                    onMetaToggleAction = {
                        if (latch.snapshot.meta) {
                            latch.setMeta(false)
                            currentOnKeyPress(byteArrayOf(0x1B))
                        } else {
                            latch.setMeta(true)
                        }
                    },
                    onFnToggle = { fnActive = !fnActive },
                    fnActive = fnActive,
                )
            }
        }
    }
}

// ─── Layout components ──────────────────────────────────────────────

@Composable
private fun DefaultPage1(
    Key: @Composable (KeySpec, Modifier) -> Unit,
    latch: TerminalModifierLatch,
    arrowUp: () -> ByteArray, arrowDown: () -> ByteArray,
    arrowLeft: () -> ByteArray, arrowRight: () -> ByteArray,
    onFnToggle: () -> Unit,
    fnActive: Boolean,
) {
    val mods = latch.snapshot
    // Row 1: ESC  Home  End  Ins  Del  ↑  ⌫  ⏎
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Key(KeySpec.Repeat("ESC") { byteArrayOf(0x1B) }, Modifier.weight(1f))
        Key(KeySpec.Repeat("Home") { "\u001b[H".toByteArray() }, Modifier.weight(1f))
        Key(KeySpec.Repeat("End") { "\u001b[F".toByteArray() }, Modifier.weight(1f))
        Key(KeySpec.Repeat("Ins") { "\u001b[2~".toByteArray() }, Modifier.weight(1f))
        Key(KeySpec.Repeat("Del") { "\u001b[3~".toByteArray() }, Modifier.weight(1f))
        Key(KeySpec.Repeat("↑", fontSize = SYM_FONT_SIZE, send = arrowUp), Modifier.weight(1f))
        Key(KeySpec.Repeat("⌫", fontSize = SYM_FONT_SIZE) { byteArrayOf(0x7F) }, Modifier.weight(1f))
        Key(KeySpec.Repeat("⏎", fontSize = SYM_FONT_SIZE) { byteArrayOf('\r'.code.toByte()) }, Modifier.weight(1f))
    }
    // Row 2: ⇥  Ctrl  Alt  Shift  ←  ↓  →  Fn
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Key(KeySpec.Repeat("⇥", fontSize = SYM_FONT_SIZE) { byteArrayOf(0x09) }, Modifier.weight(1f))
        Key(KeySpec.Toggle("Ctrl", mods.ctrl) { latch.setCtrl(!mods.ctrl) }, Modifier.weight(1f))
        Key(KeySpec.Toggle("Alt", mods.alt) { latch.setAlt(!mods.alt) }, Modifier.weight(1f))
        Key(KeySpec.Toggle("Shift", mods.shift) { latch.setShift(!mods.shift) }, Modifier.weight(1f))
        Key(KeySpec.Repeat("←", fontSize = SYM_FONT_SIZE, send = arrowLeft), Modifier.weight(1f))
        Key(KeySpec.Repeat("↓", fontSize = SYM_FONT_SIZE, send = arrowDown), Modifier.weight(1f))
        Key(KeySpec.Repeat("→", fontSize = SYM_FONT_SIZE, send = arrowRight), Modifier.weight(1f))
        Key(KeySpec.Toggle(if (fnActive) "Fn •" else "Fn", fnActive, onToggle = onFnToggle), Modifier.weight(1f))
    }
}

@Composable
private fun DefaultPage2(
    Key: @Composable (KeySpec, Modifier) -> Unit,
    latch: TerminalModifierLatch,
    onMetaToggleAction: () -> Unit,
    onFnToggle: () -> Unit,
    fnActive: Boolean,
) {
    val mods = latch.snapshot
    // Row 1: F1  F2  F3  F4  F5  F6  PgUp  Meta
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Key(KeySpec.Repeat("F1") { "\u001bOP".toByteArray() }, Modifier.weight(1f))
        Key(KeySpec.Repeat("F2") { "\u001bOQ".toByteArray() }, Modifier.weight(1f))
        Key(KeySpec.Repeat("F3") { "\u001bOR".toByteArray() }, Modifier.weight(1f))
        Key(KeySpec.Repeat("F4") { "\u001bOS".toByteArray() }, Modifier.weight(1f))
        Key(KeySpec.Repeat("F5") { "\u001b[15~".toByteArray() }, Modifier.weight(1f))
        Key(KeySpec.Repeat("F6") { "\u001b[17~".toByteArray() }, Modifier.weight(1f))
        Key(KeySpec.Repeat("PgUp") { "\u001b[5~".toByteArray() }, Modifier.weight(1f))
        Key(KeySpec.Toggle("Meta", mods.meta, onToggle = onMetaToggleAction), Modifier.weight(1f))
    }
    // Row 2: F7  F8  F9  F10  F11  F12  PgDn  Fn
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Key(KeySpec.Repeat("F7") { "\u001b[18~".toByteArray() }, Modifier.weight(1f))
        Key(KeySpec.Repeat("F8") { "\u001b[19~".toByteArray() }, Modifier.weight(1f))
        Key(KeySpec.Repeat("F9") { "\u001b[20~".toByteArray() }, Modifier.weight(1f))
        Key(KeySpec.Repeat("F10") { "\u001b[21~".toByteArray() }, Modifier.weight(1f))
        Key(KeySpec.Repeat("F11") { "\u001b[23~".toByteArray() }, Modifier.weight(1f))
        Key(KeySpec.Repeat("F12") { "\u001b[24~".toByteArray() }, Modifier.weight(1f))
        Key(KeySpec.Repeat("PgDn") { "\u001b[6~".toByteArray() }, Modifier.weight(1f))
        Key(KeySpec.Toggle(if (fnActive) "Fn •" else "Fn", fnActive, onToggle = onFnToggle), Modifier.weight(1f))
    }
}

private const val KEY_HEIGHT_DP = 32
private val SYM_FONT_SIZE = 14.sp

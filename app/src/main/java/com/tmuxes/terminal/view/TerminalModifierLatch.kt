package com.tmuxes.terminal.view

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tmuxes.ui.components.keybar.ModifierSnapshot

/**
 * State holder for the terminal-side soft modifier latch (the Ctrl/Alt/
 * Shift/Meta toggles on [ExtraKeysBar]). Bundles what was previously four
 * `var` flags + four setter lambdas + one `clearModifiers` reset into a
 * single Composable-scoped object.
 *
 * The held [snapshot] is fed to:
 *  - [com.tmuxes.ui.components.keybar.KeyButton] at press-down (snapshotted
 *    for the duration of a Repeat hold so modifiers persist across every
 *    repeated emit — see ModifierSnapshotTest)
 *  - [TerminalView.extraKeyModifiers] for physical-keyboard handling
 *
 * [consume] is invoked once at hold-end (release) so the latched flags
 * don't carry into the next gesture.
 */
@Stable
class TerminalModifierLatch internal constructor(
    initial: ModifierSnapshot = ModifierSnapshot.NONE,
) {
    var snapshot: ModifierSnapshot by mutableStateOf(initial)
        private set

    fun setCtrl(active: Boolean)  { snapshot = snapshot.copy(ctrl = active) }
    fun setAlt(active: Boolean)   { snapshot = snapshot.copy(alt = active) }
    fun setShift(active: Boolean) { snapshot = snapshot.copy(shift = active) }
    fun setMeta(active: Boolean)  { snapshot = snapshot.copy(meta = active) }

    fun consume() { snapshot = ModifierSnapshot.NONE }
}

@Composable
fun rememberTerminalModifierLatch(): TerminalModifierLatch =
    remember { TerminalModifierLatch() }

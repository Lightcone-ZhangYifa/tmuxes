package com.tmuxes.ui.components.keybar

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Three categories of keybar buttons shared by terminal and editor surfaces.
 *
 * Each surface picks two of the three:
 * - Terminal ExtraKeysBar: [Toggle] (modifiers + Fn) + [Repeat] (every action key)
 * - Editor   EditorKeybar:  [Toggle] (Fn)              + [Once]   (every action key)
 *
 * `Repeat` and `Once` differ only in long-press behavior: both emit once on press,
 * but `Repeat` continues to emit while held (after [INITIAL_REPEAT_DELAY_MS] then
 * every [REPEAT_INTERVAL_MS]), whereas `Once` ignores hold duration. `Toggle`
 * flips an [Toggle.active] flag and never emits on hold either.
 */
sealed interface KeySpec {
    val label: String
    val fontSize: TextUnit get() = DEFAULT_FONT_SIZE
    val translateLabel: Boolean get() = false

    /** Sticky state-flip key (Ctrl/Alt/Shift/Meta/Fn). */
    data class Toggle(
        override val label: String,
        val active: Boolean,
        override val fontSize: TextUnit = DEFAULT_FONT_SIZE,
        override val translateLabel: Boolean = false,
        val onToggle: () -> Unit,
    ) : KeySpec

    /**
     * Auto-repeating action key. Holding past [INITIAL_REPEAT_DELAY_MS] starts
     * repeating at [REPEAT_INTERVAL_MS]. [send] is a lambda (not a precomputed
     * ByteArray) because the byte sequence may depend on terminal protocol
     * state read at emit time (e.g. arrow keys flip between CSI and SS3 based
     * on `applicationCursorKeys`). Modifier keys are snapshotted at press-down
     * by [KeyButton] and applied to every emit during the hold.
     */
    data class Repeat(
        override val label: String,
        override val fontSize: TextUnit = DEFAULT_FONT_SIZE,
        override val translateLabel: Boolean = false,
        val send: () -> ByteArray,
    ) : KeySpec

    /** Single-shot action key. Press emits [onPress] exactly once; holding does not repeat. */
    data class Once(
        override val label: String,
        override val fontSize: TextUnit = DEFAULT_FONT_SIZE,
        override val translateLabel: Boolean = false,
        val onPress: () -> Unit,
    ) : KeySpec
}

/** Snapshot of modifier-key state taken at press-down for [KeySpec.Repeat]. */
data class ModifierSnapshot(
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
    val meta: Boolean = false,
) {
    companion object {
        val NONE = ModifierSnapshot()
    }
}

internal val DEFAULT_FONT_SIZE: TextUnit = 11.sp

/** Initial delay before auto-repeat kicks in, after the first immediate emit. */
const val INITIAL_REPEAT_DELAY_MS: Long = 400L

/** Interval between repeated emits while a [KeySpec.Repeat] key is held. */
const val REPEAT_INTERVAL_MS: Long = 80L

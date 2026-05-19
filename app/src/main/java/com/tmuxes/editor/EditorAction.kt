package com.tmuxes.editor

/**
 * What a keybar button does, expressed as a discriminated union.
 *
 * - [Mutate]   — pure document mutation, dispatched through
 *                [EditorActionRunner]. One batched patch sequence.
 * - [Native]   — Sora-intrinsic single-step operation (clipboard / undo /
 *                redo / commitText / moveSelection / custom selectAll).
 * - [External] — side effect outside the editor (Go-to-line dialog).
 * - [FnToggle] — switches keybar between Page 1 and Page 2.
 */
sealed interface KeyAction {
    data class Mutate(val command: DocCommand) : KeyAction

    fun interface Native : KeyAction {
        fun invoke(context: EditorActionContext)
    }

    fun interface External : KeyAction {
        fun invoke()
    }

    object FnToggle : KeyAction
}

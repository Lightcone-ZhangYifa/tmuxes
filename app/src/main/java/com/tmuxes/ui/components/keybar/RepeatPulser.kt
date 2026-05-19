package com.tmuxes.ui.components.keybar

import kotlinx.coroutines.delay

/**
 * Auto-repeat suspend pulse used by [KeySpec.Repeat]. The caller emits the
 * first tick synchronously before launching this in a coroutine; this body
 * waits [initialDelayMs], then loops with [intervalMs] between [onTick]
 * invocations until the coroutine is cancelled — the next [delay] call
 * throws [kotlinx.coroutines.CancellationException] which unwinds the loop.
 *
 * Extracted as an internal top-level suspend fun so [RepeatPulserTest] can
 * verify timing under virtual time without Compose dependencies.
 */
internal suspend fun repeatPulse(
    initialDelayMs: Long,
    intervalMs: Long,
    onTick: () -> Unit,
) {
    delay(initialDelayMs)
    while (true) {
        onTick()
        delay(intervalMs)
    }
}

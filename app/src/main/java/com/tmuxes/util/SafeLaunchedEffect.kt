package com.tmuxes.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope

/**
 * Like [LaunchedEffect], but wraps the suspend lambda in a try/catch so an
 * uncaught exception inside the body is logged and swallowed instead of
 * propagating to the composition's exception handler — which on most
 * devices means the main thread's uncaught handler, and that means
 * "activity crashes".
 *
 * **Why this matters**: `LaunchedEffect(key) { riskyCall() }` is one of
 * the easiest ways to introduce a main-thread crash in Compose. The body
 * runs on the Main dispatcher, and any throw propagates out of the
 * coroutine into Compose's launched-scope exception handler, which is the
 * default Main handler — i.e. kills the activity. Every riskyCall in the
 * codebase (suspend repository calls, navigation transitions during state
 * change, snackbar dismissals on dying activities, Flow first() waits
 * hitting a CancellationException from a dead scope, etc.) is a latent
 * crash unless wrapped.
 *
 * [CancellationException] is rethrown so structured concurrency still
 * works — cancelling the composition cancels the effect as usual.
 *
 * @param key Composition key; when it changes, the effect is cancelled
 *   and restarted (same as [LaunchedEffect]).
 * @param tag Log tag for swallowed exceptions. Pass the screen name and
 *   effect purpose so it's easy to find in logcat.
 * @param block Suspend body to run. Identical signature to [LaunchedEffect].
 */
@Composable
fun SafeLaunchedEffect(
    key: Any?,
    tag: String = "SafeLaunchedEffect",
    block: suspend CoroutineScope.() -> Unit
) {
    LaunchedEffect(key) {
        try {
            block()
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Throwable) {
            AppLogger.e(AppLogger.Category.UI, e) { "$tag: uncaught exception (swallowed)" }
        }
    }
}

/**
 * Two-key variant of [SafeLaunchedEffect]. Restarts when either key changes.
 */
@Composable
fun SafeLaunchedEffect(
    key1: Any?,
    key2: Any?,
    tag: String = "SafeLaunchedEffect",
    block: suspend CoroutineScope.() -> Unit
) {
    LaunchedEffect(key1, key2) {
        try {
            block()
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Throwable) {
            AppLogger.e(AppLogger.Category.UI, e) { "$tag: uncaught exception (swallowed)" }
        }
    }
}

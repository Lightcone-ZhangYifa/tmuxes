package com.tmuxes.util

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Like [kotlinx.coroutines.launch], but installs a [CoroutineExceptionHandler]
 * that logs and swallows uncaught exceptions instead of letting them propagate
 * to the thread's default uncaught exception handler.
 *
 * On Android the default handler for a `SupervisorJob` scope (such as
 * `viewModelScope`) without its own handler terminates the process. That
 * behaviour is catastrophic for an SSH client where every coroutine can hit
 * a transient network / IO / database failure — a single slipped exception
 * in a fire-and-forget launch ends the app.
 *
 * Use this in place of `viewModelScope.launch { ... }`, any custom scope's
 * `launch { ... }`, or any one-off `CoroutineScope(...).launch { ... }`.
 * Structured concurrency is preserved: the returned [Job] cancels normally
 * when the parent scope cancels, and [kotlinx.coroutines.CancellationException]
 * is NOT logged.
 */
fun CoroutineScope.safeLaunch(
    context: CoroutineContext = EmptyCoroutineContext,
    tag: String = "safeLaunch",
    block: suspend CoroutineScope.() -> Unit
): Job {
    val handler = CoroutineExceptionHandler { _, e ->
        if (e is kotlinx.coroutines.CancellationException) return@CoroutineExceptionHandler
        AppLogger.e(AppLogger.Category.UI, e) { "$tag: uncaught exception (swallowed)" }
    }
    return launch(context + handler, block = block)
}

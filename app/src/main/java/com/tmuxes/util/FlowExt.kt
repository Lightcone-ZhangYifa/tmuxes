package com.tmuxes.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

/**
 * Intercept any upstream exception on [this] Flow, log it under the given
 * [tag], and emit [default] so downstream collectors see a degraded — but
 * non-crashing — state.
 *
 * Purpose: `stateIn` propagates upstream exceptions to its collector scope.
 * When the scope is a [androidx.lifecycle.viewModelScope] with no handler
 * attached (the default), a Flow error crashes the ViewModel, which tears
 * down the composition and kills the app UI.
 *
 * Use this on every `Flow` that feeds into `stateIn` inside a ViewModel —
 * Room DAO flows, preference flows, repository flows, Supervisor flows, etc.
 * The cost is nil on the happy path; on the error path, a log line plus a
 * default emission keeps the UI alive and allows a subsequent successful
 * emission to recover.
 *
 * @param default Value to emit when the upstream Flow throws.
 * @param tag     Log tag (typically the ViewModel name + property).
 * @param category Which [AppLogger.Category] the log line goes under.
 */
fun <T> Flow<T>.catchSafe(
    default: T,
    tag: String,
    category: AppLogger.Category = AppLogger.Category.UI
): Flow<T> = catch { e ->
    if (e is kotlinx.coroutines.CancellationException) throw e
    AppLogger.w(category) { "$tag: flow error (emitting default): ${e.message}" }
    emit(default)
}

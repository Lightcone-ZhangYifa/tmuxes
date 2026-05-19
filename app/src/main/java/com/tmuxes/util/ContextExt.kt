package com.tmuxes.util

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity

/**
 * Walk the [ContextWrapper] chain to find the underlying [ComponentActivity].
 *
 * Compose `LocalContext.current` may return a [ContextThemeWrapper] (or some
 * other wrapper) around the hosting activity rather than the activity
 * itself. A naked `LocalContext.current as ComponentActivity` non-null cast
 * then throws [ClassCastException] during composition, which runs on the
 * main thread and — because [com.tmuxes.TmuxesApp.installUncaughtExceptionHandler]
 * deliberately delegates main-thread exceptions to the platform handler —
 * crashes the whole process.
 *
 * Use this helper in `viewModelStoreOwner = ...` default parameters and
 * anywhere else where the code needs to reach the activity from a Compose
 * [Context]. Returns `null` if no [ComponentActivity] is found in the
 * wrapper chain (e.g. a Compose Preview context, a unit test context,
 * an accessibility overlay), so callers can fall back gracefully.
 */
fun Context.findComponentActivity(): ComponentActivity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is ComponentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

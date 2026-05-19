package com.tmuxes.util

import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.navOptions

/**
 * Extension wrappers around [NavController.navigate] that swallow
 * lifecycle-related exceptions instead of letting them crash the host
 * Activity.
 *
 * **Why this is needed**: [NavController.navigate] throws
 * `IllegalStateException` / `IllegalArgumentException` in several
 * race-condition scenarios that are easy to hit in real use but hard to
 * reproduce in manual testing:
 *
 * - Fast double-tap on a button whose onClick calls navigate — the first
 *   call puts the controller into a transition state, and the second
 *   throws "Cannot navigate to destination ...".
 * - A navigate call after the Activity has been state-saved but before
 *   onResume (`IllegalStateException: FragmentManager has been destroyed`
 *   on Fragment-hosted navigators).
 * - A stale `navController` reference passed through a lambda that
 *   outlives the composition (classic "captured var" leak).
 * - An orchestration bug where an unknown route id is passed (typo,
 *   refactor gone wrong) — these should be fixed, but should not crash
 *   the user's session.
 *
 * Every onClick button in the app's navigation graph is a latent crash
 * unless these are handled. Wrapping here centralises the fix.
 *
 * [CancellationException] is not thrown from navigate; the only
 * exceptions we swallow are lifecycle-state IllegalState/Argument. We
 * re-throw anything else to preserve the ability to crash on programmer
 * errors during development.
 */
fun NavController.safeNavigate(route: String) {
    AppLogger.i(AppLogger.Category.NAV) { "nav → route=$route" }
    try {
        navigate(route)
    } catch (e: IllegalStateException) {
        AppLogger.w(AppLogger.Category.NAV) { "nav ✗ route=$route cause='${e.message}'" }
    } catch (e: IllegalArgumentException) {
        AppLogger.w(AppLogger.Category.NAV) { "nav ✗ route=$route cause='${e.message}'" }
    }
}

fun NavController.safeNavigate(route: String, builder: NavOptionsBuilder.() -> Unit) {
    AppLogger.i(AppLogger.Category.NAV) { "nav → route=$route (with builder)" }
    try {
        navigate(route, navOptions(builder))
    } catch (e: IllegalStateException) {
        AppLogger.w(AppLogger.Category.NAV) { "nav ✗ route=$route cause='${e.message}'" }
    } catch (e: IllegalArgumentException) {
        AppLogger.w(AppLogger.Category.NAV) { "nav ✗ route=$route cause='${e.message}'" }
    }
}

fun NavController.safeNavigate(route: String, navOptions: NavOptions) {
    AppLogger.i(AppLogger.Category.NAV) { "nav → route=$route (with options)" }
    try {
        navigate(route, navOptions)
    } catch (e: IllegalStateException) {
        AppLogger.w(AppLogger.Category.NAV) { "nav ✗ route=$route cause='${e.message}'" }
    } catch (e: IllegalArgumentException) {
        AppLogger.w(AppLogger.Category.NAV) { "nav ✗ route=$route cause='${e.message}'" }
    }
}

/**
 * Safer pop — [NavController.popBackStack] itself already returns Boolean
 * instead of throwing on empty stack, but it can still throw
 * IllegalStateException if the controller is in a bad state (e.g. during
 * config change). Returns false on any throw.
 */
fun NavController.safePopBackStack(): Boolean {
    return try {
        val popped = popBackStack()
        AppLogger.d(AppLogger.Category.NAV) { "nav.pop popped=$popped" }
        popped
    } catch (e: IllegalStateException) {
        AppLogger.w(AppLogger.Category.NAV) { "nav.pop ✗ cause='${e.message}'" }
        false
    }
}

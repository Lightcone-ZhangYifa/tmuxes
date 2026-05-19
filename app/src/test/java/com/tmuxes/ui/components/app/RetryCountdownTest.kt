package com.tmuxes.ui.components.app

import android.os.SystemClock
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-JVM tests for the non-Composable parts of RetryCountdown:
 * - [remainingSeconds] math against `SystemClock.elapsedRealtime()`
 * - [formatCountdownText] copy across all (seconds, retryCount, verbose) cases
 * - [RetryCountdownState] data semantics
 *
 * The Composable hook + ticker are validated on emulator (the user-visible
 * "Retrying in 5s → 4s → 3s …" recomposition cadence cannot be unit-tested
 * without Robolectric / Compose runtime, both heavyweight for what is
 * effectively a `delay(1000)` loop).
 */
class RetryCountdownTest {

    // ----- remainingSeconds -----

    @Test fun remainingSecondsForFutureDeadline() {
        val now = SystemClock.elapsedRealtime()
        assertEquals(5, remainingSeconds(now + 5_000L))
    }

    @Test fun remainingSecondsForPastDeadlineClampsToZero() {
        val now = SystemClock.elapsedRealtime()
        assertEquals(0, remainingSeconds(now - 1_000L))
    }

    @Test fun remainingSecondsForExactlyNowIsZero() {
        val now = SystemClock.elapsedRealtime()
        assertEquals(0, remainingSeconds(now))
    }

    @Test fun remainingSecondsTruncatesSubSecond() {
        // 5500ms → 5s (integer div by 1000)
        val now = SystemClock.elapsedRealtime()
        assertEquals(5, remainingSeconds(now + 5_500L))
    }

    // ----- RetryCountdownState -----

    @Test fun stateNullSecondsZeroPeak() {
        val s = RetryCountdownState(seconds = null, peakSeconds = 0)
        assertEquals(null, s.seconds)
        assertEquals(0, s.peakSeconds)
    }

    @Test fun stateActiveCountdown() {
        val s = RetryCountdownState(seconds = 3, peakSeconds = 5)
        assertEquals(3, s.seconds)
        assertEquals(5, s.peakSeconds)
    }

    @Test fun stateExpiredCountdown() {
        val s = RetryCountdownState(seconds = 0, peakSeconds = 5)
        assertEquals(0, s.seconds)
        assertEquals(5, s.peakSeconds)
    }

    // ----- formatCountdownText: short form -----

    @Test fun shortNullSeconds() {
        assertEquals("Reconnecting…", formatCountdownText(null, 0, verbose = false))
    }

    @Test fun shortZeroSeconds() {
        assertEquals("Reconnecting…", formatCountdownText(0, 0, verbose = false))
    }

    @Test fun shortPositiveSeconds() {
        assertEquals("Auto retry · 5s", formatCountdownText(5, 0, verbose = false))
    }

    @Test fun shortIgnoresRetryCount() {
        // short form never mentions retry count
        assertEquals("Auto retry · 7s", formatCountdownText(7, 3, verbose = false))
    }

    // ----- formatCountdownText: verbose form -----

    @Test fun verboseNullSeconds() {
        assertEquals(
            "Connection lost · reconnecting…",
            formatCountdownText(null, 0, verbose = true)
        )
    }

    @Test fun verboseZeroSeconds() {
        assertEquals(
            "Connection lost · reconnecting…",
            formatCountdownText(0, 2, verbose = true)
        )
    }

    @Test fun verbosePositiveSecondsNoRetryYet() {
        assertEquals(
            "Reconnect · 8s",
            formatCountdownText(8, 0, verbose = true)
        )
    }

    @Test fun verbosePositiveSecondsWithRetryCount() {
        assertEquals(
            "Reconnect #3 · 4s",
            formatCountdownText(4, 3, verbose = true)
        )
    }

    @Test fun verboseSingleSecondCountdown() {
        assertEquals(
            "Reconnect #1 · 1s",
            formatCountdownText(1, 1, verbose = true)
        )
    }
}

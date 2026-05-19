package com.tmuxes.ui.components.keybar

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the auto-repeat timing for [KeySpec.Repeat] under
 * kotlinx-coroutines-test virtual time. The first tick is emitted by the
 * caller before launching the pulse, so [repeatPulse] only emits ticks
 * AFTER the initial delay.
 *
 * Constants under test: [INITIAL_REPEAT_DELAY_MS] = 400, [REPEAT_INTERVAL_MS] = 80.
 *
 * Note: with kotlinx-coroutines-test 1.6+ StandardTestDispatcher,
 * advanceTimeBy moves virtual time but doesn't drain coroutines at the
 * new boundary — [runCurrent] does that.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RepeatPulserTest {

    @Test
    fun `no ticks before initial delay`() = runTest {
        var ticks = 0
        val job = launch { repeatPulse(400, 80) { ticks++ } }
        advanceTimeBy(399); runCurrent()
        assertEquals("ticks before 400ms initial delay", 0, ticks)
        job.cancelAndJoin()
    }

    @Test
    fun `first tick fires after initial delay`() = runTest {
        var ticks = 0
        val job = launch { repeatPulse(400, 80) { ticks++ } }
        advanceTimeBy(401); runCurrent()
        assertEquals("after initial delay", 1, ticks)
        job.cancelAndJoin()
    }

    @Test
    fun `second tick fires within an interval after first`() = runTest {
        var ticks = 0
        val job = launch { repeatPulse(400, 80) { ticks++ } }
        advanceTimeBy(401); runCurrent()
        assertEquals(1, ticks)
        advanceTimeBy(81); runCurrent()
        assertEquals("by one interval past first, second has fired", 2, ticks)
        job.cancelAndJoin()
    }

    @Test
    fun `pulse emits steady ticks at interval`() = runTest {
        var ticks = 0
        val job = launch { repeatPulse(400, 80) { ticks++ } }
        advanceTimeBy(401); runCurrent()
        repeat(10) { advanceTimeBy(80); runCurrent() }
        assertEquals(11, ticks)
        job.cancelAndJoin()
    }

    @Test
    fun `cancellation stops further ticks`() = runTest {
        var ticks = 0
        val job = launch { repeatPulse(400, 80) { ticks++ } }
        advanceTimeBy(401); runCurrent()
        advanceTimeBy(80); runCurrent()
        val snapshot = ticks
        assertTrue("at least 2 ticks before cancel", snapshot >= 2)
        job.cancelAndJoin()
        advanceTimeBy(1000); runCurrent()
        assertEquals("no further ticks after cancellation", snapshot, ticks)
    }
}

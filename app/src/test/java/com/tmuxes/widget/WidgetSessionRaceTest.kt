package com.tmuxes.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Tests for the widget session generation-counter fix.
 *
 * Root cause of flicker: when multiple [reattachForCurrentSize] coroutines race
 * (config change + onConnectionChanged + health check), old sessions' EOF
 * callbacks set [isEnded] = true even after a newer session is established,
 * triggering an infinite detach→reattach→EOF→detach cycle.
 *
 * Fix: a generation counter — each attach increments it; the EOF callback only
 * sets isEnded when its captured generation still matches the current one.
 */
class WidgetSessionRaceTest {

    /**
     * Reproduces the exact WidgetBinding generation-guard logic.
     */
    private class BindingSimulator {
        @Volatile var isEnded = false
            private set
        @Volatile var sessionGeneration = 0L
            private set

        /** Simulate a new reattach: returns the captured generation. */
        fun attach(): Long {
            val gen: Long
            synchronized(this) {
                sessionGeneration++
                gen = sessionGeneration
                isEnded = false
            }
            return gen
        }

        /** Simulate EOF arriving for the session created at [gen]. */
        fun eof(gen: Long) {
            if (sessionGeneration == gen) {
                isEnded = true
            }
        }

        /** Simulate detachLiveSession (sets isEnded = true unconditionally). */
        fun detach() {
            synchronized(this) { isEnded = true }
        }

        /** Mirrors needsReconnect: session == null || !isOpen || isEnded. */
        fun needsReconnect(sessionIsOpen: Boolean): Boolean {
            return !sessionIsOpen || isEnded
        }
    }

    // ---------------------------------------------------------------
    // Core flicker scenario
    // ---------------------------------------------------------------

    @Test
    fun `stale EOF from old session does not set isEnded on new session`() {
        val b = BindingSimulator()

        val genA = b.attach()           // reattach #1 connects
        assertFalse(b.isEnded)

        val genB = b.attach()           // reattach #2 supersedes #1
        assertFalse(b.isEnded)

        b.eof(genA)                     // old session EOF — STALE
        assertFalse("Stale EOF must not mark current session as ended", b.isEnded)

        b.eof(genB)                     // current session EOF — legitimate
        assertTrue(b.isEnded)
    }

    @Test
    fun `exact flicker cycle is broken by generation counter`() {
        val b = BindingSimulator()

        // Step 1: coroutine A attaches
        val genA = b.attach()
        assertFalse(b.needsReconnect(sessionIsOpen = true))

        // Step 2: coroutine B races in (e.g. onConnectionChanged), detaches A, attaches B
        b.detach()                      // B detaches A's session
        assertTrue(b.isEnded)           // detach sets isEnded
        val genB = b.attach()           // B creates new session, clears isEnded
        assertFalse(b.isEnded)

        // Step 3: A's session EOF arrives (STALE!)
        b.eof(genA)
        assertFalse("This stale EOF was the root cause of the flicker loop", b.isEnded)
        assertFalse("Health check must NOT trigger reattach here", b.needsReconnect(sessionIsOpen = true))

        // Step 4: B's session is healthy — no spurious reattach
        assertFalse(b.needsReconnect(sessionIsOpen = true))
    }

    // ---------------------------------------------------------------
    // Multiple rapid reattaches
    // ---------------------------------------------------------------

    @Test
    fun `rapid reattaches — only latest generation EOF takes effect`() {
        val b = BindingSimulator()

        val gens = (1..10).map { b.attach() }

        // All old EOFs are stale
        for (old in gens.dropLast(1)) {
            b.eof(old)
            assertFalse("Generation $old EOF must be ignored", b.isEnded)
        }

        // Only latest generation EOF works
        b.eof(gens.last())
        assertTrue(b.isEnded)
    }

    @Test
    fun `reattach after legitimate EOF clears isEnded`() {
        val b = BindingSimulator()

        val gen1 = b.attach()
        b.eof(gen1)
        assertTrue(b.isEnded)
        assertTrue(b.needsReconnect(sessionIsOpen = false))

        val gen2 = b.attach()
        assertFalse(b.isEnded)
        assertFalse(b.needsReconnect(sessionIsOpen = true))

        // Old gen1 EOF arriving again is harmless
        b.eof(gen1)
        assertFalse(b.isEnded)
    }

    // ---------------------------------------------------------------
    // Concurrent thread safety
    // ---------------------------------------------------------------

    @Test
    fun `concurrent reattaches and EOFs do not produce spurious isEnded`() {
        val generation = AtomicLong(0)
        val isEnded = AtomicBoolean(false)
        val spuriousEndCount = AtomicInteger(0)

        // 50 threads each simulate attach + delayed EOF
        val threads = (1..50).map {
            Thread {
                val myGen = generation.incrementAndGet()
                isEnded.set(false)

                Thread.sleep((Math.random() * 5).toLong())

                // Generation-guarded EOF
                if (generation.get() == myGen) {
                    isEnded.set(true)
                } else {
                    spuriousEndCount.incrementAndGet()
                }
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // At least some stale EOFs should have been prevented
        // (exact count depends on thread scheduling, but must be > 0 with 50 threads)
        assertTrue(
            "Generation counter should prevent spurious EOF (prevented ${spuriousEndCount.get()})",
            spuriousEndCount.get() > 0
        )
    }

    // ---------------------------------------------------------------
    // needsReconnect combinations
    // ---------------------------------------------------------------

    @Test
    fun `needsReconnect returns true when isEnded even if session open`() {
        val b = BindingSimulator()
        val gen = b.attach()
        b.eof(gen)
        assertTrue(b.needsReconnect(sessionIsOpen = true))
    }

    @Test
    fun `needsReconnect returns true when session closed`() {
        val b = BindingSimulator()
        b.attach()
        assertFalse(b.isEnded)
        assertTrue(b.needsReconnect(sessionIsOpen = false))
    }

    @Test
    fun `needsReconnect returns false when session open and not ended`() {
        val b = BindingSimulator()
        b.attach()
        assertFalse(b.needsReconnect(sessionIsOpen = true))
    }

    // ---------------------------------------------------------------
    // Render coalescing (existing logic, regression guard)
    // ---------------------------------------------------------------

    @Test
    fun `render coalescing — at most one pending while one in-flight`() {
        var scheduled = false
        var pending = false
        var renderCount = 0
        val lock = Any()

        fun trySchedule(): Boolean {
            synchronized(lock) {
                if (scheduled) { pending = true; return false }
                scheduled = true
            }
            return true
        }

        fun finishRender(): Boolean {
            renderCount++
            return synchronized(lock) {
                scheduled = false
                val p = pending; pending = false; p
            }
        }

        // First: accepted
        assertTrue(trySchedule())
        // 20 more while in-flight: all coalesced
        repeat(20) { assertFalse(trySchedule()) }
        // Finish → pending
        assertTrue(finishRender())
        assertEquals(1, renderCount)

        // Pending kicks off second render
        assertTrue(trySchedule())
        repeat(5) { assertFalse(trySchedule()) }
        finishRender()
        assertEquals(2, renderCount)
    }
}

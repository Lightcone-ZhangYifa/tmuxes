package com.tmuxes.util

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [catchSafe].
 *
 * Verifies that:
 * - Happy-path emissions pass through unchanged
 * - A thrown exception is caught and replaced with the default
 * - CancellationException is NOT swallowed (rethrown)
 *
 * Regression coverage for the iter 59 fix that added `.catch` to every
 * preference stateIn in SettingsViewModel. A broken upstream Flow would
 * previously propagate to the viewModelScope exception handler and kill
 * the ViewModel, which is observed as "settings screen crashes on open".
 */
class FlowExtTest {

    @Test
    fun `catchSafe passes happy-path emissions through unchanged`() = runBlocking {
        val out = flowOf(1, 2, 3)
            .catchSafe(default = -1, tag = "test")
            .toList()
        assertEquals(listOf(1, 2, 3), out)
    }

    @Test
    fun `catchSafe emits default on upstream throw`() = runBlocking {
        val out = flow<Int> {
            emit(1)
            emit(2)
            throw RuntimeException("boom")
        }.catchSafe(default = -1, tag = "test").toList()

        // Happy-path emissions come first, then the default from the catch
        assertEquals(listOf(1, 2, -1), out)
    }

    @Test
    fun `catchSafe emits default on immediate throw before any happy emission`() = runBlocking {
        val out = flow<Int> {
            throw IllegalStateException("start-up error")
        }.catchSafe(default = 42, tag = "test").toList()

        assertEquals(listOf(42), out)
    }

    @Test
    fun `catchSafe rethrows CancellationException`() = runBlocking {
        var caught = false
        try {
            flow<Int> {
                throw kotlinx.coroutines.CancellationException("cancelled")
            }.catchSafe(default = 99, tag = "test").toList()
        } catch (_: kotlinx.coroutines.CancellationException) {
            caught = true
        }
        assertTrue("CancellationException should be rethrown", caught)
    }

    @Test
    fun `catchSafe works with nullable type parameter`() = runBlocking {
        val out = flow<String?> {
            emit("hello")
            throw RuntimeException("fail")
        }.catchSafe(default = null, tag = "test").toList()

        assertEquals(listOf("hello", null), out)
    }
}

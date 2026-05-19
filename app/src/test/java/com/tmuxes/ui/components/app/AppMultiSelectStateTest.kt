package com.tmuxes.ui.components.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [AppMultiSelectState].
 *
 * Specifically exercises the `mapNotNull { it as? Long }` pattern used
 * by LibraryDetailScreen — iter 69 fixed a ClassCastException here where
 * `map { it as Long }` threw on every element if a stale non-Long key
 * leaked into the selection set from a prior screen.
 *
 * These tests document the expected behavior:
 * - `selectedKeys` is `Set<Any>` and may contain mixed types
 * - `mapNotNull { it as? Long }` silently drops non-Long elements
 * - activate/deactivate manage isActive state
 * - selectAll / deselectAll mutate the key set
 */
class AppMultiSelectStateTest {

    @Test
    fun `activate with initial key adds key and marks active`() {
        val state = AppMultiSelectState()
        state.activate(42L)
        assertTrue(state.isActive)
        assertEquals(1, state.selectedCount)
        assertTrue(state.isSelected(42L))
    }

    @Test
    fun `activate without initial key is empty but active`() {
        val state = AppMultiSelectState()
        state.activate()
        assertTrue(state.isActive)
        assertEquals(0, state.selectedCount)
    }

    @Test
    fun `deactivate clears keys and marks inactive`() {
        val state = AppMultiSelectState()
        state.activate(1L)
        state.toggleItem(2L)
        state.toggleItem(3L)
        state.deactivate()
        assertFalse(state.isActive)
        assertEquals(0, state.selectedCount)
    }

    @Test
    fun `toggleItem adds and removes`() {
        val state = AppMultiSelectState()
        state.activate()
        state.toggleItem(1L)
        assertTrue(state.isSelected(1L))
        state.toggleItem(1L)
        assertFalse(state.isSelected(1L))
    }

    @Test
    fun `selectAll adds keys to existing set`() {
        val state = AppMultiSelectState()
        state.activate(1L)
        state.selectAll(listOf(2L, 3L, 4L))
        assertEquals(4, state.selectedCount)
    }

    @Test
    fun `isAllSelected detects complete selection`() {
        val state = AppMultiSelectState()
        state.activate()
        state.selectAll(listOf(1L, 2L, 3L))
        assertTrue(state.isAllSelected(listOf(1L, 2L, 3L)))
        assertFalse(state.isAllSelected(listOf(1L, 2L, 3L, 4L)))
    }

    @Test
    fun `isAllSelected is false for empty input`() {
        val state = AppMultiSelectState()
        state.activate()
        state.selectAll(listOf(1L, 2L))
        assertFalse(state.isAllSelected(emptyList()))
    }

    // -------------------------------------------------------------------------
    // Regression: mapNotNull { it as? Long } pattern used by LibraryDetailScreen
    // -------------------------------------------------------------------------
    // iter 69 fixed a ClassCastException where `map { it as Long }` threw
    // on every element if a stale non-Long key leaked into the set from a
    // prior screen. These tests document the safe extraction pattern.

    @Test
    fun `mapNotNull-as-Long extracts only Long entries from mixed set`() {
        val state = AppMultiSelectState()
        state.activate()
        // Simulate a mixed selection — e.g. a prior screen left String keys
        state.toggleItem(1L)
        state.toggleItem("stale_string_key")
        state.toggleItem(2L)
        state.toggleItem(3.14)

        val longs = state.selectedKeys.mapNotNull { it as? Long }.toSet()

        assertEquals(setOf(1L, 2L), longs)
    }

    @Test
    fun `naive map-as-Long would throw on non-Long entries`() {
        val state = AppMultiSelectState()
        state.activate()
        state.toggleItem(1L)
        state.toggleItem("bad_key")

        var threw = false
        try {
            @Suppress("UNCHECKED_CAST")
            state.selectedKeys.map { it as Long }.toSet()
        } catch (_: ClassCastException) {
            threw = true
        }
        assertTrue(
            "Naive `map { it as Long }` must throw ClassCastException " +
                "on mixed input — this test documents the bug iter 69 fixed",
            threw
        )
    }

    @Test
    fun `mapNotNull-as-Long returns empty set when all entries are non-Long`() {
        val state = AppMultiSelectState()
        state.activate()
        state.toggleItem("a")
        state.toggleItem("b")

        val longs = state.selectedKeys.mapNotNull { it as? Long }.toSet()

        assertEquals(emptySet<Long>(), longs)
    }
}

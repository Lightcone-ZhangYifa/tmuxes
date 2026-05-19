package com.tmuxes.ui.components.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for the mutual-exclusion state machine of
 * [AppFabBubbleController]. The composable wrapper + `remember` are
 * exercised on emulator.
 */
class AppFabBubbleControllerTest {

    private fun newController() = AppFabBubbleController()

    @Test fun initiallyClosed() {
        val c = newController()
        assertNull(c.openId)
        assertFalse(c.isAnyOpen)
        assertFalse(c.isOpen("a"))
    }

    @Test fun toggleOpensWhenClosed() {
        val c = newController()
        c.toggle("a")
        assertEquals("a", c.openId)
        assertTrue(c.isAnyOpen)
        assertTrue(c.isOpen("a"))
        assertFalse(c.isOpen("b"))
    }

    @Test fun toggleSameIdCloses() {
        val c = newController()
        c.toggle("a")
        c.toggle("a")
        assertNull(c.openId)
        assertFalse(c.isAnyOpen)
    }

    @Test fun toggleDifferentIdSwitchesMutuallyExclusive() {
        val c = newController()
        c.toggle("a")
        assertTrue(c.isOpen("a"))
        c.toggle("b")
        assertFalse(c.isOpen("a"))
        assertTrue(c.isOpen("b"))
        assertEquals("b", c.openId)
    }

    @Test fun openSetsId() {
        val c = newController()
        c.open("x")
        assertEquals("x", c.openId)
        c.open("y")
        assertEquals("y", c.openId)
    }

    @Test fun closeClears() {
        val c = newController()
        c.open("x")
        c.close()
        assertNull(c.openId)
    }

    @Test fun threeFabExclusivity() {
        // Simulate the editor's 3-FAB cluster: Problems / Output / Find
        val c = newController()
        c.toggle("problems")
        assertTrue(c.isOpen("problems"))
        assertFalse(c.isOpen("output"))
        assertFalse(c.isOpen("find"))

        c.toggle("output")
        assertFalse(c.isOpen("problems"))
        assertTrue(c.isOpen("output"))
        assertFalse(c.isOpen("find"))

        c.toggle("find")
        assertFalse(c.isOpen("problems"))
        assertFalse(c.isOpen("output"))
        assertTrue(c.isOpen("find"))

        c.toggle("find")
        assertNull(c.openId)
    }

    @Test fun badgeDataClassEquality() {
        val a = AppFabBadge(AppFabBadgeTone.Danger, "3")
        val b = AppFabBadge(AppFabBadgeTone.Danger, "3")
        val c = AppFabBadge(AppFabBadgeTone.Warning, "3")
        assertEquals(a, b)
        assertFalse(a == c)
    }

    @Test fun badgeEmptyTextDefault() {
        val a = AppFabBadge(AppFabBadgeTone.Info)
        assertEquals("", a.text)
    }
}

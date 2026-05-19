package com.tmuxes.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Pure-JVM tests for [AppLogger]. Compose / android.util.Log lookups are
 * stubbed by Robolectric in instrumented tests; here we exercise the level
 * gating, breadcrumb ring, and helper semantics without emitting to logcat.
 */
class AppLoggerTest {

    @Before
    fun reset() {
        // Restore default per-category levels first, THEN clear breadcrumbs
        // so the level-change breadcrumb itself doesn't count toward tests.
        AppLogger.setLevelForAll(AppLogger.Level.DEBUG)
        AppLogger.clearBreadcrumbs()
    }

    // ----- Level gating -----

    @Test fun setLevelTakesEffect() {
        AppLogger.setLevel(AppLogger.Category.SSH, AppLogger.Level.WARN)
        assertEquals(AppLogger.Level.WARN, AppLogger.levelOf(AppLogger.Category.SSH))
    }

    @Test fun setLevelForAllSetsAllCategories() {
        AppLogger.setLevelForAll(AppLogger.Level.ERROR)
        for (c in AppLogger.Category.values()) {
            assertEquals(AppLogger.Level.ERROR, AppLogger.levelOf(c))
        }
    }

    @Test fun shouldLogTrueWhenAtOrAboveMinimum() {
        AppLogger.setLevel(AppLogger.Category.SSH, AppLogger.Level.DEBUG)
        assertTrue(AppLogger.shouldLog(AppLogger.Category.SSH, AppLogger.Level.DEBUG))
        assertTrue(AppLogger.shouldLog(AppLogger.Category.SSH, AppLogger.Level.INFO))
        assertTrue(AppLogger.shouldLog(AppLogger.Category.SSH, AppLogger.Level.WARN))
        assertTrue(AppLogger.shouldLog(AppLogger.Category.SSH, AppLogger.Level.ERROR))
    }

    @Test fun shouldLogFalseWhenBelowMinimum() {
        AppLogger.setLevel(AppLogger.Category.SSH, AppLogger.Level.INFO)
        assertFalse(AppLogger.shouldLog(AppLogger.Category.SSH, AppLogger.Level.TRACE))
        assertFalse(AppLogger.shouldLog(AppLogger.Category.SSH, AppLogger.Level.DEBUG))
    }

    @Test fun perCategoryLevelsAreIndependent() {
        AppLogger.setLevel(AppLogger.Category.SSH, AppLogger.Level.TRACE)
        AppLogger.setLevel(AppLogger.Category.UI, AppLogger.Level.WARN)
        assertEquals(AppLogger.Level.TRACE, AppLogger.levelOf(AppLogger.Category.SSH))
        assertEquals(AppLogger.Level.WARN, AppLogger.levelOf(AppLogger.Category.UI))
    }

    // ----- 17 categories present -----

    @Test fun seventeenCategoriesEnumerated() {
        assertEquals(17, AppLogger.Category.values().size)
    }

    @Test fun categoryTagsArePrefixed() {
        for (c in AppLogger.Category.values()) {
            assertTrue("category ${c.name} tag must be 'tmuxes.X'",
                c.tag.startsWith("tmuxes."))
        }
    }

    // ----- Lambda body NOT evaluated when below level -----

    @Test fun debugLambdaSkippedWhenLevelHigher() {
        AppLogger.setLevel(AppLogger.Category.SSH, AppLogger.Level.WARN)
        var called = false
        AppLogger.d(AppLogger.Category.SSH) { called = true; "should-not-build" }
        assertFalse("DEBUG lambda must not run when level=WARN", called)
    }

    @Test fun traceLambdaSkippedByDefault() {
        AppLogger.setLevel(AppLogger.Category.SSH, AppLogger.Level.DEBUG)
        var called = false
        AppLogger.t(AppLogger.Category.SSH) { called = true; "should-not-build" }
        assertFalse("TRACE lambda must not run when level=DEBUG", called)
    }

    @Test fun errorLambdaAlwaysRunsAtErrorLevel() {
        AppLogger.setLevel(AppLogger.Category.SSH, AppLogger.Level.ERROR)
        var called = false
        AppLogger.e(AppLogger.Category.SSH) { called = true; "x" }
        assertTrue("ERROR lambda must run when level=ERROR", called)
    }

    // ----- Breadcrumbs -----

    @Test fun breadcrumbsCaptureWarnAndError() {
        AppLogger.w(AppLogger.Category.SSH) { "ssh.warn-1" }
        AppLogger.e(AppLogger.Category.SSH) { "ssh.error-1" }
        val snap = AppLogger.snapshotBreadcrumbs()
        assertTrue("must contain ssh.warn-1", snap.any { it.contains("ssh.warn-1") })
        assertTrue("must contain ssh.error-1", snap.any { it.contains("ssh.error-1") })
    }

    @Test fun breadcrumbsBoundedTo256() {
        repeat(300) { i -> AppLogger.w(AppLogger.Category.SSH) { "msg-$i" } }
        val snap = AppLogger.snapshotBreadcrumbs()
        assertEquals("ring buffer must cap at 256", 256, snap.size)
        // The oldest 44 entries should have been dropped.
        assertFalse("oldest entry msg-0 must have been dropped", snap.any { it.contains("msg-0") })
        assertTrue("newest entry msg-299 must be present", snap.any { it.contains("msg-299") })
    }

    @Test fun clearBreadcrumbsEmptiesRing() {
        AppLogger.w(AppLogger.Category.SSH) { "x" }
        assertTrue(AppLogger.snapshotBreadcrumbs().isNotEmpty())
        AppLogger.clearBreadcrumbs()
        assertTrue(AppLogger.snapshotBreadcrumbs().isEmpty())
    }

    @Test fun breadcrumbLineFormatHasLevelAndCategoryShortName() {
        AppLogger.w(AppLogger.Category.HOSTKEY) { "hostkey.changed" }
        val snap = AppLogger.snapshotBreadcrumbs()
        assertEquals(1, snap.size)
        // Format: "[HH:mm:ss.SSS L/SHORTCAT] msg" where SHORTCAT is the
        // category name without the "tmuxes." prefix.
        val line = snap[0]
        assertTrue("line must contain W/HOSTKEY but was: $line", line.contains("W/HOSTKEY"))
        assertTrue("line must contain hostkey.changed but was: $line", line.contains("hostkey.changed"))
    }

    // ----- timed helper -----

    @Test fun timedReturnsBlockResult() {
        val out = AppLogger.timed(AppLogger.Category.SSH, "op") { 42 }
        assertEquals(42, out)
    }

    @Test fun timedRethrowsAndLogsOnException() {
        val ex = RuntimeException("boom")
        try {
            AppLogger.timed(AppLogger.Category.SSH, "op") { throw ex }
            fail("should have rethrown")
        } catch (t: Throwable) {
            assertSame(ex, t)
        }
        // Breadcrumb should mark the failure
        val snap = AppLogger.snapshotBreadcrumbs()
        assertTrue("breadcrumb must include ✗ marker", snap.any { it.contains("op ✗") })
        assertTrue("breadcrumb must include error type", snap.any { it.contains("RuntimeException") })
    }

}

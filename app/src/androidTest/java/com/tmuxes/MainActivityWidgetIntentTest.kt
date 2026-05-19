package com.tmuxes

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityWidgetIntentTest {

    @Test
    fun coldStartLaunchIntentCapturesWidgetTarget() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val launchIntent = widgetIntent(context, "cold-start-session").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val activity = instrumentation.startActivitySync(launchIntent) as MainActivity

        try {
            instrumentation.waitForIdleSync()

            val request = activity.lastLaunchRequestForTest()
            assertNotNull(request)
            assertEquals(1L, request?.serverId)
            assertEquals("cold-start-session", request?.sessionName)
            assertTrue((request?.requestId ?: 0L) > 0L)
        } finally {
            activity.finish()
            instrumentation.waitForIdleSync()
        }
    }

    @Test
    fun handleLaunchIntentReplacesPendingWidgetTarget() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val activity = instrumentation.startActivitySync(launchIntent) as MainActivity

        try {
            instrumentation.runOnMainSync {
                activity.handleLaunchIntent(widgetIntent(context, "first-session"))
            }
            instrumentation.waitForIdleSync()

            val firstRequest = activity.lastLaunchRequestForTest()
            assertNotNull(firstRequest)
            assertEquals("first-session", firstRequest?.sessionName)

            instrumentation.runOnMainSync {
                activity.handleLaunchIntent(widgetIntent(context, "second-session"))
            }
            instrumentation.waitForIdleSync()

            val secondRequest = activity.lastLaunchRequestForTest()
            assertNotNull(secondRequest)
            assertEquals("second-session", secondRequest?.sessionName)
            assertTrue((secondRequest?.requestId ?: 0L) > (firstRequest?.requestId ?: 0L))
        } finally {
            activity.finish()
            instrumentation.waitForIdleSync()
        }
    }

    private fun widgetIntent(context: android.content.Context, sessionName: String): Intent =
        Intent(context, MainActivity::class.java).apply {
            putExtra("server_id", 1L)
            putExtra("session_name", sessionName)
        }
}

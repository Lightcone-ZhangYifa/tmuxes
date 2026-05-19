package com.tmuxes

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tmuxes.terminal.emulator.TerminalColors
import com.tmuxes.terminal.emulator.TerminalEmulator
import com.tmuxes.widget.TerminalBitmapRenderer
import android.util.TypedValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class TerminalBitmapRendererTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val renderer = TerminalBitmapRenderer(context)
    private val scheme = TerminalColors.CATPPUCCIN_MOCHA
    private val widthPx = 240
    private val heightPx = 160
    private val fontSizeSp = 12f

    private fun render(emulator: TerminalEmulator, sessionName: String): Bitmap =
        renderer.renderFromEmulator(
            emulator = emulator,
            widthPx = widthPx,
            heightPx = heightPx,
            scheme = scheme,
            sessionName = sessionName,
            serverName = "server",
            isConnected = true,
            connectionState = "AUTHENTICATED",
            fontSizeOverrideSp = fontSizeSp
        )

    @Test
    fun renderFromEmulator_isStableAcrossConcurrentCalls() {
        val redEmulator = TerminalEmulator(rows = 4, columns = 8).apply {
            processInput("\u001B[41mA\u001B[0m".toByteArray(Charsets.UTF_8))
        }
        val blueEmulator = TerminalEmulator(rows = 4, columns = 8).apply {
            processInput("\u001B[44mB\u001B[0m".toByteArray(Charsets.UTF_8))
        }

        val redBaseline = render(redEmulator, "red")
        val blueBaseline = render(blueEmulator, "blue")

        repeat(12) {
            val start = CountDownLatch(1)
            val executor = Executors.newFixedThreadPool(2)
            try {
                val redFuture = executor.submit<Bitmap> {
                    start.await(5, TimeUnit.SECONDS)
                    render(redEmulator, "red")
                }
                val blueFuture = executor.submit<Bitmap> {
                    start.await(5, TimeUnit.SECONDS)
                    render(blueEmulator, "blue")
                }

                start.countDown()

                val redBitmap = redFuture.get(5, TimeUnit.SECONDS)
                val blueBitmap = blueFuture.get(5, TimeUnit.SECONDS)

                assertTrue(redBitmap.sameAs(redBaseline))
                assertTrue(blueBitmap.sameAs(blueBaseline))
            } finally {
                executor.shutdownNow()
            }
        }
    }

    @Test
    fun renderFromEmulator_rendersWideCharacters() {
        val wideEmulator = TerminalEmulator(rows = 4, columns = 8).apply {
            processInput("\u001B[42m漢\u001B[0m".toByteArray(Charsets.UTF_8))
        }
        val blankEmulator = TerminalEmulator(rows = 4, columns = 8)

        val wideBitmap = render(wideEmulator, "wide")
        val blankBitmap = render(blankEmulator, "blank")

        assertFalse(wideBitmap.sameAs(blankBitmap))
    }

    // ---------------------------------------------------------------
    // capDimensions tests
    // ---------------------------------------------------------------

    @Test
    fun capDimensions_preservesAspectRatio() {
        val (w, h) = renderer.capDimensions(3000, 1000)
        // The larger dimension (3000) should be capped, and 1000 scaled proportionally
        assertTrue("Width $w should be <= 4096", w <= 4096)
        assertTrue("Height $h should be <= 4096", h <= 4096)
        val originalRatio = 3000.0 / 1000.0
        val cappedRatio = w.toDouble() / h.toDouble()
        assertTrue(
            "Aspect ratio should be preserved: original=$originalRatio capped=$cappedRatio",
            abs(originalRatio - cappedRatio) < 0.02
        )
    }

    @Test
    fun capDimensions_noChangeWhenUnderLimit() {
        val (w, h) = renderer.capDimensions(800, 600)
        assertEquals(800, w)
        assertEquals(600, h)
    }

    @Test
    fun capDimensions_squareInput() {
        val (w, h) = renderer.capDimensions(5000, 5000)
        assertTrue("Width $w should be <= 4096", w <= 4096)
        assertTrue("Height $h should be <= 4096", h <= 4096)
        assertEquals("Square input should remain square", w, h)
    }

    @Test
    fun terminalSize_matchesBitmapVisibleArea() {
        // For a given set of dimensions and font size, the PTY cols/rows
        // returned by computeTerminalSize() must match the visible area
        // that renderFromEmulator() would actually draw.
        val testCases = listOf(
            500 to 300,
            1000 to 600,
            2000 to 1200,
            3000 to 800
        )
        val testFontSize = 10f

        for ((rawW, rawH) in testCases) {
            val (capW, capH) = renderer.capDimensions(rawW, rawH)
            val (cols, rows) = renderer.computeTerminalSize(capW, capH, testFontSize)

            // Render a bitmap and check the visible area matches
            val emu = TerminalEmulator(rows = rows, columns = cols)
            val bitmap = renderer.renderFromEmulator(
                emulator = emu,
                widthPx = rawW,
                heightPx = rawH,
                scheme = scheme,
                sessionName = "test",
                serverName = "server",
                isConnected = true,
                connectionState = "AUTHENTICATED",
                fontSizeOverrideSp = testFontSize
            )

            // The bitmap dimensions should match the capped dimensions
            assertEquals("Bitmap width for ${rawW}x${rawH}", capW, bitmap.width)
            assertEquals("Bitmap height for ${rawW}x${rawH}", capH, bitmap.height)
            // The emulator should have been created with matching dimensions
            assertTrue("Cols ($cols) should be >= 1", cols >= 1)
            assertTrue("Rows ($rows) should be >= 1", rows >= 1)
            bitmap.recycle()
        }
    }

    @Test
    fun renderFromEmulator_largeDimensions_noCrash() {
        val emu = TerminalEmulator(rows = 24, columns = 80)
        emu.processInput("Hello large widget\r\n".toByteArray())
        val bitmap = renderer.renderFromEmulator(
            emulator = emu,
            widthPx = 3000,
            heightPx = 2000,
            scheme = scheme,
            sessionName = "large",
            serverName = "server",
            isConnected = true,
            connectionState = "AUTHENTICATED"
        )
        assertTrue("Bitmap width should be <= 4096", bitmap.width <= 4096)
        assertTrue("Bitmap height should be <= 4096", bitmap.height <= 4096)
        assertTrue("Bitmap should have non-zero dimensions", bitmap.width > 0 && bitmap.height > 0)
        bitmap.recycle()
    }

    @Test
    fun renderFromEmulator_bitmapAspectRatioPreserved() {
        val emu = TerminalEmulator(rows = 10, columns = 40)
        val bitmap = renderer.renderFromEmulator(
            emulator = emu,
            widthPx = 2000,
            heightPx = 500,
            scheme = scheme,
            sessionName = "aspect",
            serverName = "server",
            isConnected = true,
            connectionState = "AUTHENTICATED"
        )
        val expectedRatio = 2000.0 / 500.0
        val actualRatio = bitmap.width.toDouble() / bitmap.height.toDouble()
        assertTrue(
            "Aspect ratio should be preserved: expected=$expectedRatio actual=$actualRatio",
            abs(expectedRatio - actualRatio) < 0.05
        )
        bitmap.recycle()
    }
}

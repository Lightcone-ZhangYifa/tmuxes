package com.tmuxes.terminal.view

import android.view.inputmethod.EditorInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tmuxes.terminal.emulator.TerminalEmulator
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TerminalViewLifecycleTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun terminalView_rebinds_and_keeps_fullscreen_size_after_ime_close_and_reentry() {
        val emulator = TerminalEmulator(rows = 3, columns = 10)
        val showTerminal = mutableStateOf(true)

        var terminalView: TerminalView? = null
        var isFullScreenSized = false

        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                if (showTerminal.value) {
                    AndroidView(
                        factory = { context ->
                            TerminalView(context).apply {
                                callback = object : TerminalViewCallback {
                                    override fun onKeyEvent(data: ByteArray) = Unit
                                    override fun onTextInput(text: String) = Unit
                                    override fun onFontSizeChanged(newSize: Float) = Unit
                                    override fun onTextSelected(text: String) = Unit
                                    override fun onSendBytes(data: ByteArray, reason: String) = Unit
                                }
                                bindTerminalEmulator(emulator)
                                terminalView = this
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.runOnUiThread {
                isFullScreenSized = terminalView != null &&
                    emulator.buffer.rows > 3 &&
                    emulator.buffer.columns > 10
            }
            isFullScreenSized
        }

        val fullRows = emulator.buffer.rows
        val fullCols = emulator.buffer.columns

        composeRule.runOnUiThread {
            val view = terminalView ?: error("TerminalView was not created")
            view.requestFocus()
            val connection = view.onCreateInputConnection(EditorInfo())
            connection.commitText("echo test", 1)
            connection.deleteSurroundingText(1, 0)
            connection.finishComposingText()
            connection.closeConnection()
            view.releaseInputFocus()
            showTerminal.value = false
        }

        composeRule.waitForIdle()

        composeRule.runOnUiThread {
            showTerminal.value = true
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.runOnUiThread {
                isFullScreenSized = terminalView != null &&
                    terminalView?.currentEmulator === emulator &&
                    emulator.buffer.rows >= fullRows &&
                    emulator.buffer.columns >= fullCols
            }
            isFullScreenSized
        }

        assertNotNull(terminalView)
        assertSame(emulator, terminalView?.currentEmulator)
        assertTrue(emulator.buffer.rows >= fullRows)
        assertTrue(emulator.buffer.columns >= fullCols)
    }
}

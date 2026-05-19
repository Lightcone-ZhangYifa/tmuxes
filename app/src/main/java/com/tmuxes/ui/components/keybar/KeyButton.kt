// allow-bypass-D5: keybar haptic + onPress wraps; failure is recoverable and self-logging would flood breadcrumbs on every tap
package com.tmuxes.ui.components.keybar

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tmuxes.i18n.t
import com.tmuxes.terminal.view.ModifierKeys
import com.tmuxes.ui.design.appTokens
import com.tmuxes.ui.design.pressAnimationSpec
import com.tmuxes.util.safeLaunch
import kotlinx.coroutines.Job

/**
 * Unified keybar button. Dispatches at composition time on the [KeySpec]
 * subtype so each variant gets its own stable `pointerInput(Unit)` block —
 * gestures survive parent recompositions even when lambda references in
 * the spec change.
 *
 * For [KeySpec.Repeat], the caller must supply [modifiersProvider] (read at
 * press-down to snapshot Ctrl/Alt/Shift/Meta), [onEmit] (sink for the
 * encoded bytes after [ModifierKeys.apply]), and [onModifiersConsumed]
 * (called once at hold-end so the surrounding screen can clear latched
 * modifier flags).
 *
 * For [KeySpec.Toggle] and [KeySpec.Once] those parameters are unused and
 * default to no-ops.
 */
@Composable
fun KeyButton(
    spec: KeySpec,
    background: Color,
    backgroundActive: Color,
    contentColor: Color,
    keyHeight: Int,
    modifier: Modifier = Modifier,
    modifiersProvider: () -> ModifierSnapshot = { ModifierSnapshot.NONE },
    onEmit: (ByteArray) -> Unit = {},
    onModifiersConsumed: () -> Unit = {},
) {
    when (spec) {
        is KeySpec.Toggle -> ToggleButton(spec, background, backgroundActive, contentColor, keyHeight, modifier)
        is KeySpec.Once -> OnceButton(spec, background, contentColor, keyHeight, modifier)
        is KeySpec.Repeat -> RepeatButton(
            spec, background, contentColor, keyHeight, modifier,
            modifiersProvider, onEmit, onModifiersConsumed,
        )
    }
}

@Composable
private fun ToggleButton(
    spec: KeySpec.Toggle,
    background: Color,
    backgroundActive: Color,
    contentColor: Color,
    keyHeight: Int,
    modifier: Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val currentOnToggle by rememberUpdatedState(spec.onToggle)
    var pressed by remember { mutableStateOf(false) }
    KeySurface(
        background = if (spec.active) backgroundActive else background,
        pressed = pressed,
        modifier = modifier.height(keyHeight.dp).pointerInput(Unit) {
            detectTapGestures(onPress = {
                pressed = true
                val released = tryAwaitRelease()
                pressed = false
                if (released) {
                    try { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } catch (_: Throwable) {}
                    try { currentOnToggle() } catch (_: Throwable) {}
                }
            })
        },
    ) {
        KeyLabel(spec.label, spec.translateLabel, contentColor, spec.fontSize)
    }
}

@Composable
private fun OnceButton(
    spec: KeySpec.Once,
    background: Color,
    contentColor: Color,
    keyHeight: Int,
    modifier: Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val currentOnPress by rememberUpdatedState(spec.onPress)
    var pressed by remember { mutableStateOf(false) }
    KeySurface(
        background = background,
        pressed = pressed,
        modifier = modifier.height(keyHeight.dp).pointerInput(Unit) {
            detectTapGestures(onPress = {
                pressed = true
                val released = tryAwaitRelease()
                pressed = false
                if (released) {
                    try { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } catch (_: Throwable) {}
                    try { currentOnPress() } catch (_: Throwable) {}
                }
            })
        },
    ) {
        KeyLabel(spec.label, spec.translateLabel, contentColor, spec.fontSize)
    }
}

@Composable
private fun RepeatButton(
    spec: KeySpec.Repeat,
    background: Color,
    contentColor: Color,
    keyHeight: Int,
    modifier: Modifier,
    modifiersProvider: () -> ModifierSnapshot,
    onEmit: (ByteArray) -> Unit,
    onModifiersConsumed: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val currentSend by rememberUpdatedState(spec.send)
    val currentModifiersProvider by rememberUpdatedState(modifiersProvider)
    val currentOnEmit by rememberUpdatedState(onEmit)
    val currentOnModifiersConsumed by rememberUpdatedState(onModifiersConsumed)
    val scope = rememberCoroutineScope()
    var repeatJob by remember { mutableStateOf<Job?>(null) }
    var pressed by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { repeatJob?.cancel() } }

    KeySurface(
        background = background,
        pressed = pressed,
        modifier = modifier.height(keyHeight.dp).pointerInput(Unit) {
            detectTapGestures(onPress = { _ ->
                pressed = true
                try { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } catch (_: Throwable) {}
                val snapshot = try { currentModifiersProvider() } catch (_: Throwable) { ModifierSnapshot.NONE }
                emitOnce(currentSend, snapshot, currentOnEmit)
                repeatJob = scope.safeLaunch(tag = "keybar.repeat") {
                    repeatPulse(INITIAL_REPEAT_DELAY_MS, REPEAT_INTERVAL_MS) {
                        emitOnce(currentSend, snapshot, currentOnEmit)
                    }
                }
                tryAwaitRelease()
                pressed = false
                repeatJob?.cancel()
                repeatJob = null
                try { currentOnModifiersConsumed() } catch (_: Throwable) {}
            })
        },
    ) {
        KeyLabel(spec.label, spec.translateLabel, contentColor, spec.fontSize)
    }
}

@Composable
private fun KeySurface(
    background: Color,
    pressed: Boolean,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val tokens = MaterialTheme.appTokens
    val scale by animateFloatAsState(
        targetValue = if (pressed) tokens.motion.keyPressScale else 1f,
        animationSpec = tokens.motion.pressAnimationSpec(),
        label = "key_press_feedback"
    )
    Surface(
        modifier = modifier.scale(scale),
        color = background,
        shape = tokens.shape.xs,
        tonalElevation = tokens.elevation.level2,
    ) {
        content()
    }
}

@Composable
private fun KeyLabel(
    label: String,
    translate: Boolean,
    color: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
) {
    Text(
        text = if (translate) t(label) else label,
        color = color,
        fontSize = fontSize,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier
            .padding(horizontal = 2.dp, vertical = 6.dp)
            .fillMaxWidth(),
    )
}

private fun emitOnce(
    send: () -> ByteArray,
    snapshot: ModifierSnapshot,
    onEmit: (ByteArray) -> Unit,
) {
    val bytes = try { send() } catch (_: Throwable) { return }
    val encoded = ModifierKeys.apply(bytes, snapshot.ctrl, snapshot.alt, snapshot.shift, snapshot.meta)
    try { onEmit(encoded) } catch (_: Throwable) {}
}

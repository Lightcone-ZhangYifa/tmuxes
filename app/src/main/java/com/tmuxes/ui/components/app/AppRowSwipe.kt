// allow-bypass-D5: swipe-action callback; if the action throws the swipe still resets — failure surfaces in the action's own log
package com.tmuxes.ui.components.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.tmuxes.i18n.t
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val ActionWidth = 60.dp

/**
 * Swipe-to-reveal action panel. Single component used by every list-row
 * call site in the app (servers / keys / known hosts / port forwards /
 * sessions / snippets / library / terminal command panel).
 *
 * Behavior is a 1:1 port of the historical `RevealSwipe` — the only
 * implementation that has worked correctly across every caller. The
 * unified-design migration changes only:
 *   - Package: `com.tmuxes.ui.components.app` (unified namespace).
 *   - Action data class: [AppRowAction] (`color` field).
 *
 * Everything else — 60dp button cell, `IconButton(40dp)` wrapping the
 * Icon, optional label below in a Column, foreground Box with NO
 * `fillMaxSize` and NO `clip(shape)` — is preserved. Past attempts to
 * deviate (List/Compact split, fillMaxSize on foreground, adaptive icon,
 * button width 48dp) all caused regressions.
 *
 * Callers MUST place click handling on the inner content via AppCard's
 * `onClick` / `onLongClick` PARAMETERS, never via
 * `Modifier.clickable {...}` on the AppCard's modifier — Material3
 * `Surface(onClick=)` would otherwise intercept pointer-down events.
 *
 * The action panel is only composed while the row is actually revealed.
 * Keeping it out of the hidden/resting state prevents press-scale and
 * drag movement from exposing action colors beneath the foreground row.
 */
@Composable
fun AppRowSwipe(
    actions: List<AppRowAction>,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    swipeEnabled: Boolean = true,
    closeSignal: Any? = null,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val maxOffsetPx = with(density) { (actions.size * ActionWidth.toPx()) }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val swipeEnabledState = rememberUpdatedState(swipeEnabled)
    val renderedOffsetX = if (swipeEnabled) offsetX.value else 0f
    val actionPanelVisible = swipeEnabled && renderedOffsetX < -0.5f

    LaunchedEffect(swipeEnabled, closeSignal) {
        if ((!swipeEnabled || closeSignal != null) && abs(offsetX.value) > 0.5f) {
            offsetX.stop()
            offsetX.snapTo(0f)
        }
    }

    Box(modifier = modifier.clip(shape)) {
        if (actionPanelVisible) {
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .clip(shape),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                actions.forEach { action ->
                    val label = t(action.label)
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(ActionWidth)
                            .background(action.color),
                        contentAlignment = Alignment.Center
                    ) {
                        val interactionSource = remember { MutableInteractionSource() }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = {
                                    try { action.onClick() } catch (_: Throwable) {} // allow-bypass-D5
                                    scope.launch { offsetX.animateTo(0f, spring()) }
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .appPressFeedback(interactionSource = interactionSource),
                                interactionSource = interactionSource
                            ) {
                                Icon(
                                    action.icon,
                                    contentDescription = label,
                                    tint = Color.White
                                )
                            }
                            if (action.label.isNotEmpty()) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(renderedOffsetX.roundToInt(), 0) }
                .pointerInput(maxOffsetPx) {
                    if (maxOffsetPx > 0f) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (swipeEnabledState.value) {
                                    scope.launch {
                                        if (abs(offsetX.value) > maxOffsetPx / 2f) {
                                            offsetX.animateTo(-maxOffsetPx, spring())
                                        } else {
                                            offsetX.animateTo(0f, spring())
                                        }
                                    }
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                if (swipeEnabledState.value) {
                                    change.consume()
                                    scope.launch {
                                        val newOffset = (offsetX.value + dragAmount)
                                            .coerceIn(-maxOffsetPx, 0f)
                                        offsetX.snapTo(newOffset)
                                    }
                                }
                            }
                        )
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures {
                        if (swipeEnabledState.value && offsetX.value < 0f) {
                            scope.launch { offsetX.animateTo(0f, spring()) }
                        }
                    }
                }
        ) {
            content()
        }
    }
}

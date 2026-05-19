package com.tmuxes.ui.components.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tmuxes.ui.design.appTokens

/**
 * Tone of an FAB badge — picks a color from the status tokens.
 */
enum class AppFabBadgeTone { Danger, Warning, Info, Success }

/**
 * A small badge dot rendered on an [AppFab]'s top-right corner. `text`
 * may be empty — then it renders as just a colored dot. Counts > 99
 * should be passed in already-truncated as `"99+"`.
 */
data class AppFabBadge(
    val tone: AppFabBadgeTone,
    val text: String = ""
)

/**
 * Mutually-exclusive FAB-bubble state machine. At most one bubble may be
 * "open" at a time — calling [toggle] on a different id closes the
 * previous bubble and opens the new one. `openId == null` means all
 * bubbles are closed.
 */
@Stable
class AppFabBubbleController internal constructor() {
    var openId: String? by mutableStateOf(null)
        private set

    fun toggle(id: String) {
        openId = if (openId == id) null else id
    }

    fun open(id: String) {
        openId = id
    }

    fun close() {
        openId = null
    }

    fun isOpen(id: String): Boolean = openId == id

    val isAnyOpen: Boolean get() = openId != null
}

@Composable
fun rememberAppFabBubbleController(): AppFabBubbleController =
    remember { AppFabBubbleController() }

/**
 * The popover container hosted above an [AppFab]. Always composed (so
 * its content's state — text inputs, scroll positions — persists across
 * open/close); animates in / out via [AnimatedVisibility].
 *
 * Layout contract:
 * - Bubble is bottom-end aligned, with its bottom edge flush with the
 *   bottom-most FAB (`bottom = tokens.space.md`, matching the FAB
 *   cluster's outer padding).
 * - End padding clears the FAB column (`md + 56dp + sm`), leaving an
 *   `sm`-gap visual separator between bubble and FAB column.
 * - Width and height are **max-only** — content drives the actual size,
 *   capped at [maxWidth] / [maxHeight]. Bubble surface uses
 *   [animateContentSize] so content growth/shrink animates smoothly.
 *
 * Tap behavior:
 * - Tap on full-screen scrim → [onDismiss].
 * - Tap on bubble surface → swallowed (no dismiss). Uses
 *   `clickable(indication=null)` rather than `detectTapGestures` so
 *   taps on empty padding inside the bubble are reliably consumed
 *   without racing with child pointer inputs.
 */
@Composable
fun AppFabBubble(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    maxWidth: Dp = 480.dp,
    maxHeight: Dp = 400.dp,
    content: @Composable () -> Unit
) {
    val tokens = MaterialTheme.appTokens
    val enterSpec = tween<Float>(
        durationMillis = tokens.motion.durationMedium1,
        easing = tokens.motion.emphasizedDecelerate
    )
    val exitSpec = tween<Float>(
        durationMillis = tokens.motion.durationShort2,
        easing = tokens.motion.emphasizedAccelerate
    )
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = enterSpec) +
            scaleIn(animationSpec = enterSpec, initialScale = tokens.motion.containerTransformScale),
        exit = fadeOut(animationSpec = exitSpec) +
            scaleOut(animationSpec = exitSpec, targetScale = tokens.motion.containerTransformScale),
        modifier = modifier
    ) {
        val scrimInteraction = remember { MutableInteractionSource() }
        val bubbleInteraction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = scrimInteraction,
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.BottomEnd
        ) {
            Surface(
                modifier = Modifier
                    .padding(
                        start = tokens.space.lg,
                        end = tokens.space.md + 56.dp + tokens.space.sm,
                        top = tokens.space.lg,
                        bottom = tokens.space.md
                    )
                    .widthIn(max = maxWidth)
                    .heightIn(max = maxHeight)
                    .animateContentSize(
                        animationSpec = tween(
                            durationMillis = tokens.motion.durationMedium1,
                            easing = tokens.motion.emphasized
                        )
                    )
                    .clickable(
                        interactionSource = bubbleInteraction,
                        indication = null,
                        onClick = { /* swallow */ }
                    ),
                shape = tokens.shape.lg,
                color = tokens.colors.surfaceContainerHigh.copy(alpha = tokens.opacity.bubble), // allow-bypass-B3: bubble translucency (configurable via app.bubble_opacity)
                contentColor = tokens.colors.onSurface,
                tonalElevation = tokens.elevation.level3,
                shadowElevation = tokens.elevation.level3
            ) {
                content()
            }
        }
    }
}

@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.tmuxes.ui.components.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import com.tmuxes.ui.design.appTokens
import com.tmuxes.ui.design.pressAnimationSpec

@Composable
fun Modifier.appPressFeedback(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
    targetScale: Float = MaterialTheme.appTokens.motion.pressScale
): Modifier {
    val tokens = MaterialTheme.appTokens
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && isPressed) targetScale else 1f,
        animationSpec = tokens.motion.pressAnimationSpec(),
        label = "app_press_feedback"
    )
    return scale(scale)
}

@Composable
fun Modifier.appPressable(
    enabled: Boolean = true,
    role: Role? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return appPressFeedback(
        interactionSource = interactionSource,
        enabled = enabled
    ).combinedClickable(
        enabled = enabled,
        role = role,
        interactionSource = interactionSource,
        indication = LocalIndication.current,
        onLongClick = onLongClick,
        onClick = onClick
    )
}

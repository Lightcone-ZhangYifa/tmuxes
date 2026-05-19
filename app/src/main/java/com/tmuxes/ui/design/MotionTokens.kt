package com.tmuxes.ui.design

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable

private val EmphasizedEasing: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
private val StandardEasing: Easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
private val EmphasizedAccelerateEasing: Easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
private val EmphasizedDecelerateEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

/**
 * Motion tokens — Material 3 emphasized + standard easing curves and
 * the duration tiers used app-wide. Animation calls in components read
 * from these tokens; no ad-hoc 200/300/500 ms numbers in screen code.
 */
@Immutable
data class MotionTokens(
    val durationShort1: Int = 50,
    val durationShort2: Int = 100,
    val durationMedium1: Int = 250,
    val durationMedium2: Int = 350,
    val durationLong1: Int = 450,
    val durationLong2: Int = 550,
    val durationPulse: Int = 800,
    val emphasized: Easing = EmphasizedEasing,
    val standard: Easing = StandardEasing,
    val emphasizedAccelerate: Easing = EmphasizedAccelerateEasing,
    val emphasizedDecelerate: Easing = EmphasizedDecelerateEasing,
    val pressScale: Float = 0.96f,
    val keyPressScale: Float = 0.94f,
    val containerTransformScale: Float = 0.92f,
    val dragLiftScale: Float = 1.03f,
    val dragLiftAlpha: Float = 0.92f,
    val pulseAlphaMin: Float = 0.40f,
    val pulseAlphaMax: Float = 1.0f
) {
    companion object {
        val Default = MotionTokens()
    }
}

fun MotionTokens.pressAnimationSpec(): AnimationSpec<Float> =
    tween(durationMillis = durationShort2, easing = emphasizedDecelerate)

fun <T> MotionTokens.listPlacementSpec(): FiniteAnimationSpec<T> =
    spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

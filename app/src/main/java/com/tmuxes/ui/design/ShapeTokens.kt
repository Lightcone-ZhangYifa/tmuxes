package com.tmuxes.ui.design

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.RectangleShape

/**
 * Shape tokens. Three user-selectable styles (sharp / rounded / pill)
 * applied to seven semantic slots.
 *
 * Slots:
 * - `none`  flat surface (no rounding)
 * - `xs`    chip / small status pill
 * - `sm`    small button / text field
 * - `md`    list-item / menu container
 * - `lg`    card / dialog body (the most common slot)
 * - `xl`    bottom sheet
 * - `pill`  fully rounded (FAB, circular icon button)
 */
@Immutable
data class ShapeTokens(
    val none: Shape,
    val xs: Shape,
    val sm: Shape,
    val md: Shape,
    val lg: Shape,
    val xl: Shape,
    val pill: Shape
) {
    companion object {
        fun of(style: AppCornerStyle): ShapeTokens = when (style) {
            AppCornerStyle.Sharp -> ShapeTokens(
                none = RectangleShape,
                xs = RectangleShape,
                sm = RectangleShape,
                md = RectangleShape,
                lg = RectangleShape,
                xl = RectangleShape,
                pill = CircleShape
            )
            AppCornerStyle.Rounded -> ShapeTokens(
                none = RectangleShape,
                xs = RoundedCornerShape(4.dp()),
                sm = RoundedCornerShape(8.dp()),
                md = RoundedCornerShape(12.dp()),
                lg = RoundedCornerShape(16.dp()),
                xl = RoundedCornerShape(topStart = 24.dp(), topEnd = 24.dp()),
                pill = CircleShape
            )
            AppCornerStyle.Pill -> ShapeTokens(
                none = RectangleShape,
                xs = RoundedCornerShape(8.dp()),
                sm = RoundedCornerShape(16.dp()),
                md = RoundedCornerShape(24.dp()),
                lg = RoundedCornerShape(28.dp()),
                xl = RoundedCornerShape(topStart = 32.dp(), topEnd = 32.dp()),
                pill = CircleShape
            )
        }
    }
}

enum class AppCornerStyle {
    Sharp, Rounded, Pill;

    companion object {
        fun fromKey(key: String): AppCornerStyle = when (key) {
            "sharp" -> Sharp
            "pill" -> Pill
            else -> Rounded
        }
    }
}

private fun Int.dp() = androidx.compose.ui.unit.Dp(this.toFloat())

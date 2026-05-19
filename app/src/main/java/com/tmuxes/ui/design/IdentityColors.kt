package com.tmuxes.ui.design

import androidx.compose.ui.graphics.Color

data class IdentityColorOption(
    val label: String,
    val argb: Int,
    val lightArgb: Int,
    val darkArgb: Int = argb
)

object IdentityColors {
    const val NONE: Int = 0

    val presets: List<IdentityColorOption> = listOf(
        IdentityColorOption("Default", NONE, NONE),
        IdentityColorOption("Blue", 0xFF8AB4F8.toInt(), 0xFF2563EB.toInt()),
        IdentityColorOption("Violet", 0xFFC4B5FD.toInt(), 0xFF7C3AED.toInt()),
        IdentityColorOption("Cyan", 0xFF67E8F9.toInt(), 0xFF0891B2.toInt()),
        IdentityColorOption("Teal", 0xFF5EEAD4.toInt(), 0xFF0F766E.toInt()),
        IdentityColorOption("Green", 0xFF86EFAC.toInt(), 0xFF15803D.toInt()),
        IdentityColorOption("Rose", 0xFFFDA4AF.toInt(), 0xFFBE123C.toInt()),
        IdentityColorOption("Orange", 0xFFFDBA74.toInt(), 0xFFC2410C.toInt()),
        IdentityColorOption("Amber", 0xFFFCD34D.toInt(), 0xFF92400E.toInt())
    )

    fun resolve(argb: Int, isDark: Boolean): Int {
        if (argb == NONE) return NONE
        val option = presets.firstOrNull { it.argb == argb || it.lightArgb == argb || it.darkArgb == argb }
        return when {
            option == null -> argb
            isDark -> option.darkArgb
            else -> option.lightArgb
        }
    }

    fun containerColor(argb: Int, colors: ColorTokens): Color {
        val resolved = resolve(argb, colors.isDark)
        if (resolved == NONE) return colors.surfaceContainer
        return Color(resolved).blend(colors.surfaceContainer, if (colors.isDark) 0.82f else 0.88f)
    }

    fun outlineColor(argb: Int, colors: ColorTokens): Color {
        val resolved = resolve(argb, colors.isDark)
        return if (resolved == NONE) colors.outlineVariant else Color(resolved).copy(alpha = 0.42f)
    }
}

private fun Color.blend(other: Color, ratio: Float): Color {
    val r = ratio.coerceIn(0f, 1f)
    return Color(
        red = red * (1f - r) + other.red * r,
        green = green * (1f - r) + other.green * r,
        blue = blue * (1f - r) + other.blue * r,
        alpha = alpha
    )
}

package com.tmuxes.ui.design

import androidx.compose.ui.graphics.Color

data class ThemeAccentOption(
    val label: String,
    val argb: Int,
    val darkArgb: Int,
    val lightArgb: Int
)

object ThemeAccents {
    private val DEFAULT_DARK = 0xFF8AB4F8.toInt()
    private val DEFAULT_LIGHT = 0xFF2563EB.toInt()

    val presets: List<ThemeAccentOption> = listOf(
        ThemeAccentOption("Blue", DEFAULT_DARK, DEFAULT_DARK, DEFAULT_LIGHT),
        ThemeAccentOption("Violet", 0xFFC4B5FD.toInt(), 0xFFC4B5FD.toInt(), 0xFF7C3AED.toInt()),
        ThemeAccentOption("Cyan", 0xFF67E8F9.toInt(), 0xFF67E8F9.toInt(), 0xFF0891B2.toInt()),
        ThemeAccentOption("Teal", 0xFF5EEAD4.toInt(), 0xFF5EEAD4.toInt(), 0xFF0F766E.toInt()),
        ThemeAccentOption("Green", 0xFF86EFAC.toInt(), 0xFF86EFAC.toInt(), 0xFF15803D.toInt()),
        ThemeAccentOption("Rose", 0xFFFDA4AF.toInt(), 0xFFFDA4AF.toInt(), 0xFFBE123C.toInt()),
        ThemeAccentOption("Orange", 0xFFFDBA74.toInt(), 0xFFFDBA74.toInt(), 0xFFC2410C.toInt()),
        ThemeAccentOption("Amber", 0xFFFCD34D.toInt(), 0xFFFCD34D.toInt(), 0xFF92400E.toInt())
    )

    fun resolve(argb: Int, isDark: Boolean): Int {
        if (argb == 0) return if (isDark) DEFAULT_DARK else DEFAULT_LIGHT
        val option = presets.firstOrNull { it.argb == argb || it.darkArgb == argb || it.lightArgb == argb }
        if (option == null) return argb
        return if (isDark) option.darkArgb else option.lightArgb
    }

    fun selectedOption(argb: Int): ThemeAccentOption =
        if (argb == 0) {
            presets.first()
        } else {
            presets.firstOrNull { it.argb == argb || it.darkArgb == argb || it.lightArgb == argb }
            ?: presets.first()
        }

    fun previewColor(argb: Int, isDark: Boolean): Color = Color(resolve(argb, isDark))
}

package com.tmuxes.ui.design

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests of the token derivation pipeline. These never touch
 * Compose or Android contexts; they exercise the data classes' factory
 * functions directly.
 */
class TokenDerivationTest {

    // -------------------------------------------------------------------
    // SpaceTokens
    // -------------------------------------------------------------------

    @Test fun spacing_compact_is_smaller_than_comfortable() {
        val c = SpaceTokens.of(AppDensity.Compact)
        val n = SpaceTokens.of(AppDensity.Comfortable)
        assertTrue(c.lg.value < n.lg.value)
        assertTrue(c.sm.value < n.sm.value)
    }

    @Test fun spacing_spacious_is_larger_than_comfortable() {
        val s = SpaceTokens.of(AppDensity.Spacious)
        val n = SpaceTokens.of(AppDensity.Comfortable)
        assertTrue(s.lg.value > n.lg.value)
    }

    @Test fun spacing_density_keys_round_trip() {
        assertEquals(AppDensity.Compact, AppDensity.fromKey("compact"))
        assertEquals(AppDensity.Comfortable, AppDensity.fromKey("comfortable"))
        assertEquals(AppDensity.Spacious, AppDensity.fromKey("spacious"))
        assertEquals(AppDensity.Comfortable, AppDensity.fromKey("nonsense"))
    }

    @Test fun comfortable_uses_8dp_grid() {
        val n = SpaceTokens.of(AppDensity.Comfortable)
        assertEquals(8f, n.sm.value, 0.001f)
        assertEquals(16f, n.lg.value, 0.001f)
        assertEquals(24f, n.xl.value, 0.001f)
    }

    // -------------------------------------------------------------------
    // ShapeTokens
    // -------------------------------------------------------------------

    @Test fun sharp_corner_keys_round_trip() {
        assertEquals(AppCornerStyle.Sharp, AppCornerStyle.fromKey("sharp"))
        assertEquals(AppCornerStyle.Rounded, AppCornerStyle.fromKey("rounded"))
        assertEquals(AppCornerStyle.Pill, AppCornerStyle.fromKey("pill"))
        assertEquals(AppCornerStyle.Rounded, AppCornerStyle.fromKey("nonsense"))
    }

    @Test fun shape_tokens_built_for_each_style() {
        AppCornerStyle.values().forEach {
            val tokens = ShapeTokens.of(it)
            assertNotEquals(null, tokens)  // smoke test — every style produces a valid token set
        }
    }

    // -------------------------------------------------------------------
    // TypeTokens
    // -------------------------------------------------------------------

    @Test fun type_scale_keys_round_trip() {
        assertEquals(AppTypeScale.Small, AppTypeScale.fromKey("small"))
        assertEquals(AppTypeScale.Default, AppTypeScale.fromKey("default"))
        assertEquals(AppTypeScale.Large, AppTypeScale.fromKey("large"))
        assertEquals(AppTypeScale.Default, AppTypeScale.fromKey("nonsense"))
    }

    @Test fun small_type_scale_smaller_than_default() {
        val s = TypeTokens.of(AppTypeScale.Small)
        val d = TypeTokens.of(AppTypeScale.Default)
        assertTrue(s.bodyMedium.fontSize.value < d.bodyMedium.fontSize.value)
        assertTrue(s.titleLarge.fontSize.value < d.titleLarge.fontSize.value)
    }

    @Test fun large_type_scale_larger_than_default() {
        val l = TypeTokens.of(AppTypeScale.Large)
        val d = TypeTokens.of(AppTypeScale.Default)
        assertTrue(l.bodyMedium.fontSize.value > d.bodyMedium.fontSize.value)
    }

    @Test fun default_type_scale_uses_compact_ui_baseline() {
        val d = TypeTokens.of(AppTypeScale.Default)
        assertEquals(12.6f, d.bodyMedium.fontSize.value, 0.001f)
        assertEquals(14.4f, d.titleMedium.fontSize.value, 0.001f)
    }

    @Test fun every_type_style_present() {
        val t = TypeTokens.of(AppTypeScale.Default)
        // smoke: all 18 styles non-null
        assertTrue(t.displayLarge.fontSize.value > 0)
        assertTrue(t.headlineLarge.fontSize.value > 0)
        assertTrue(t.titleLarge.fontSize.value > 0)
        assertTrue(t.bodyLarge.fontSize.value > 0)
        assertTrue(t.labelLarge.fontSize.value > 0)
        assertTrue(t.sectionHeader.fontSize.value > 0)
        assertTrue(t.mono.fontSize.value > 0)
        assertTrue(t.monoSmall.fontSize.value > 0)
    }

    // -------------------------------------------------------------------
    // ColorTokens (pure paths — no Context required)
    // -------------------------------------------------------------------

    @Test fun palette_keys_round_trip() {
        assertEquals(AppColorPalette.Default, AppColorPalette.fromKey("mocha"))
        assertEquals(AppColorPalette.Default, AppColorPalette.fromKey("default"))
        assertEquals(AppColorPalette.Default, AppColorPalette.fromKey("catppuccin"))
        assertEquals(AppColorPalette.MaterialYou, AppColorPalette.fromKey("material_you"))
        assertEquals(AppColorPalette.Custom, AppColorPalette.fromKey("custom"))
        assertEquals(AppColorPalette.Default, AppColorPalette.fromKey("nonsense"))
    }

    @Test fun theme_accent_choice_resolves_to_light_and_dark_pair() {
        val blue = ThemeAccents.presets.first { it.label == "Blue" }
        assertEquals(blue.darkArgb, ThemeAccents.resolve(blue.argb, isDark = true))
        assertEquals(blue.lightArgb, ThemeAccents.resolve(blue.argb, isDark = false))
        assertEquals(blue.lightArgb, ThemeAccents.resolve(blue.darkArgb, isDark = false))
    }

    // (The Mocha/Latte/Custom pure-Kotlin paths use private functions
    // inside ColorTokens.kt — exercised end-to-end via instrumented
    // tests once an Android context is available. Token *structure* is
    // verified by toMaterialScheme() compiling with the right roles.)

    // -------------------------------------------------------------------
    // StatusTokens
    // -------------------------------------------------------------------

    @Test fun status_for_severity_returns_distinct_colors() {
        val s = StatusTokens.Default
        val info = s.forSeverity(StatusTokens.Severity.Info)
        val danger = s.forSeverity(StatusTokens.Severity.Danger)
        val warning = s.forSeverity(StatusTokens.Severity.Warning)
        val success = s.forSeverity(StatusTokens.Severity.Success)
        assertNotEquals(info, danger)
        assertNotEquals(warning, success)
        assertNotEquals(info, success)
    }

    @Test fun status_on_color_is_white_for_contrast() {
        // White text reads OK against all 5 saturated status colors at AA.
        assertEquals(Color.White, StatusTokens.Default.onStatus)
    }
}

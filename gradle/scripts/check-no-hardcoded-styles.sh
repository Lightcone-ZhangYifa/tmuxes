#!/usr/bin/env bash
#
# CI gate — fail if any new hardcoded styling violation creeps into the
# screens layer. The design tokens at ui/design/ are the only place
# allowed to declare raw colors, shapes, sizes, and typography.
#
# Allowed exceptions:
# - components/app/StatusTokens.kt and design/ColorTokens.kt (palette source)
# - components/ColorPickerDialog.kt (deliberate user-pickable swatch palette)
# - servers/AddEditServerScreen.kt:groupColorPresets (deliberate user-pickable palette)
# - terminal/TerminalScreen.kt: terminalScheme.* / TerminalColors / ansiColors[]
#   reads (terminal grid is independent of App design tokens)
# - settings/ColorSchemeEditorScreen.kt: terminal-grid color preview
# - settings/SettingsComponents.kt:presetColors (legacy widget-config palette,
#   kept until widget-config-by-registry is implemented)
#
# Anything else is a violation.

set -euo pipefail

ROOT="${ROOT:-app/src/main/java/com/tmuxes/ui}"

errors=0

# 1) Color(0xFF...) literals
#    Allowed: ColorPickerDialog (deliberate user-pickable swatch palette),
#    groupColorPresets in AddEditServerScreen (deliberate user-pickable palette
#    for server tags), presetColors in SettingsComponents (legacy widget-config
#    palette), and any terminal-grid color reads (terminalScheme.* /
#    terminalColors.* / ansiColors[] / previewColor) — those are GRID, not shell.
COLOR_HITS=$(grep -rEn 'Color\(0x' \
    "$ROOT/screens" "$ROOT/components" \
    2>/dev/null \
    | grep -vE 'ColorPickerDialog|terminalScheme\.|terminalColors\.|ansiColors\[|TerminalColors|previewColor' \
    | awk '
        # Skip lines inside an AddEditServerScreen `groupColorPresets` array
        # (10 entries declared in the order Blue grey, Green, Red, Yellow, Blue,
        # Mauve, Pink, Teal, Peach, Sapphire — comments make this easy to spot).
        /AddEditServerScreen.*\/\/ (Blue grey|Green|Red|Yellow|Blue|Mauve|Pink|Teal|Peach|Sapphire)/ { next }
        # Skip presetColors palette in SettingsComponents.kt
        /SettingsComponents.*0xFF[0-9A-Fa-f]{6}\.toInt\(\)/ { next }
        { print }
    ' \
    || true)
if [ -n "$COLOR_HITS" ]; then
    echo "❌ Hardcoded Color(0x...) literals found outside design tokens:"
    echo "$COLOR_HITS"
    errors=$((errors + $(echo "$COLOR_HITS" | wc -l)))
fi

# 2) RoundedCornerShape(N.dp) literals
#    Allowed: components/app/ (the App component definitions), ColorPickerDialog
#    (deliberate small dialog), RevealSwipe (interaction primitive, not a screen).
SHAPE_HITS=$(grep -rEn 'RoundedCornerShape\([0-9]' "$ROOT/screens" "$ROOT/components" 2>/dev/null \
    | grep -vE 'components/app/|ColorPickerDialog|RevealSwipe' \
    || true)
if [ -n "$SHAPE_HITS" ]; then
    echo "❌ Hardcoded RoundedCornerShape(N.dp) found — use tokens.shape.*:"
    echo "$SHAPE_HITS"
    errors=$((errors + $(echo "$SHAPE_HITS" | wc -l)))
fi

# 3) fontSize = N.sp / letterSpacing = N.sp literals
SP_HITS=$(grep -rEn 'fontSize = [0-9]+\.sp|letterSpacing = [0-9]+(\.[0-9]+)?\.sp' \
    "$ROOT/screens" "$ROOT/components" 2>/dev/null \
    | grep -vE 'components/app/' \
    || true)
if [ -n "$SP_HITS" ]; then
    echo "❌ Hardcoded .sp literals found — use tokens.type.*:"
    echo "$SP_HITS"
    errors=$((errors + $(echo "$SP_HITS" | wc -l)))
fi

# 4) Raw Material 3 components that should go through App* wrappers
MATERIAL_HITS=$(grep -rEn ' TopAppBar\(| FloatingActionButton\(| AlertDialog\(' \
    "$ROOT/screens" 2>/dev/null \
    || true)
if [ -n "$MATERIAL_HITS" ]; then
    echo "❌ Raw Material 3 components in screens — use App* wrappers:"
    echo "$MATERIAL_HITS"
    errors=$((errors + $(echo "$MATERIAL_HITS" | wc -l)))
fi

if [ "$errors" -gt 0 ]; then
    echo ""
    echo "💥 $errors styling violation(s) found. Use design tokens and App* components instead of raw screen-level styling."
    exit 1
fi

echo "✅ No hardcoded styling violations in screens/ or components/."

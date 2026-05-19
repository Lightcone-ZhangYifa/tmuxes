#!/usr/bin/env bash
#
# Settings-registry gate. All preference access goes through typed Settings
# objects, and YAML color values stay portable hex strings.
#
# C1  No `getSharedPreferences(...)` calls anywhere
# C2  No string-keyed `yamlConfig.get/getFlow/set("...")` API
# C3  All preferences.flow/get/set must take a Settings.X object
# C4  Config colors crossing YAML boundaries must be hex strings, not raw ints

set -euo pipefail

ROOT="${ROOT:-app/src/main/java/com/tmuxes}"
errors=0
report() {
    echo "❌ $1"
    echo "$2"
    errors=$((errors + $(echo "$2" | wc -l)))
}

C1=$(grep -rEn '\bgetSharedPreferences\b' "$ROOT" 2>/dev/null || true)
if [ -n "$C1" ]; then report "C1: getSharedPreferences(...) — use AppPreferences + Settings registry:" "$C1"; fi

C2=$(grep -rEn 'yamlConfig\.(get|getFlow|set)\("' "$ROOT" 2>/dev/null || true)
if [ -n "$C2" ]; then report "C2: yamlConfig string-key API — use yamlConfig.{get,getFlow,set}Setting(Settings.X):" "$C2"; fi

# C3: detect string-literal args specifically. Legit calls pass either
# `Settings.X` (possibly fully qualified) or a `Setting<T>` parameter. The
# only way to misuse this API is to pass a string literal — flag only that.
C3=$(grep -rEn '\bpreferences\.(flow|get|set)\("' "$ROOT" 2>/dev/null \
     | grep -vE 'AppPreferences\.kt|YamlConfigManager\.kt' \
     || true)
if [ -n "$C3" ]; then report "C3: preferences.{flow,get,set} called with a string literal — pass Settings.X:" "$C3"; fi

C4_HINTS=$(grep -rEn 'Int \(ARGB color|ARGB widget title accent|Expected Int \(ARGB color|ARGB integer color' "$ROOT/data" "$ROOT/editor" 2>/dev/null || true)
if [ -n "$C4_HINTS" ]; then report "C4: config color schema/copy must describe hex strings, not integer ARGB:" "$C4_HINTS"; fi

C4_NUMERIC_READS=$(grep -rEn '\["[a-z_]*color[a-z_]*"\][[:space:]]+as\?[[:space:]]+Number|toIntOrNull\(\).*(color|Color)' "$ROOT/data" "$ROOT/editor" 2>/dev/null || true)
if [ -n "$C4_NUMERIC_READS" ]; then report "C4: YAML color fields must not parse numeric color values:" "$C4_NUMERIC_READS"; fi

C4_DIRECT_WRITES=$(grep -rEn '\["[a-z_]*color[a-z_]*"\][[:space:]]*=[[:space:]]*[A-Za-z0-9_.]+[cC]olor\b' "$ROOT/data" "$ROOT/editor" 2>/dev/null || true)
if [ -n "$C4_DIRECT_WRITES" ]; then report "C4: YAML color fields must serialize via ColorHex.toYamlString(...):" "$C4_DIRECT_WRITES"; fi

if [ "$errors" -gt 0 ]; then
    echo ""
    echo "💥 $errors settings-registry violation(s). Use typed Settings objects and serialize YAML colors as hex strings."
    exit 1
fi
echo "✅ Settings registry (C1-C4) clean."

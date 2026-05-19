#!/usr/bin/env bash
#
# Misc discipline gate. App-private storage and explicit imports keep builds
# portable and reviews predictable.
#
# G1  No /sdcard/ / getExternalStorage* / Environment.getExternalStorage* paths
# H1  No wildcard imports (`import x.y.*`)

set -euo pipefail

ROOT="${ROOT:-app/src/main/java/com/tmuxes}"

errors=0
report() {
    echo "❌ $1"
    echo "$2"
    errors=$((errors + $(echo "$2" | wc -l)))
}

G1=$(grep -rEn 'File\("/sdcard|getExternalStorage|Environment\.getExternalStorage' "$ROOT" 2>/dev/null || true)
if [ -n "$G1" ]; then report "G1: external-storage path — use context.filesDir (app private dir):" "$G1"; fi

H1=$(grep -rEn '^import [a-zA-Z0-9_.]+\.\*$' "$ROOT" 2>/dev/null || true)
if [ -n "$H1" ]; then report "H1: wildcard import — list explicit symbols:" "$H1"; fi

if [ "$errors" -gt 0 ]; then
    echo ""
    echo "💥 $errors misc-discipline violation(s). Use app-private storage and explicit imports."
    exit 1
fi
echo "✅ Misc discipline (G1, H1) clean."

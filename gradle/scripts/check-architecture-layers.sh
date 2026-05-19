#!/usr/bin/env bash
#
# Architecture layering gate. Keep UI, data, and SSH packages independent so
# feature work does not create circular dependencies.
#
# F1  ui/screens/ cannot import com.tmuxes.data.db.*
# F2  ui/screens/ cannot import com.tmuxes.ssh.internal.*
# F3  data/ cannot import com.tmuxes.ui.*
# F4  ssh/ cannot import com.tmuxes.ui.*

set -euo pipefail

ROOT="${ROOT:-app/src/main/java/com/tmuxes}"

errors=0
report() {
    echo "❌ $1"
    echo "$2"
    errors=$((errors + $(echo "$2" | wc -l)))
}

F1=$(grep -rEn '^import com\.tmuxes\.data\.db' "$ROOT/ui/screens" 2>/dev/null || true)
if [ -n "$F1" ]; then report "F1: ui/screens/ imports data.db — go through ViewModel + Repository:" "$F1"; fi

F2=$(grep -rEn '^import com\.tmuxes\.ssh\.internal' "$ROOT/ui/screens" 2>/dev/null || true)
if [ -n "$F2" ]; then report "F2: ui/screens/ imports ssh.internal — go through public ssh API or ViewModel:" "$F2"; fi

F3=$(grep -rEn '^import com\.tmuxes\.ui' "$ROOT/data" 2>/dev/null || true)
if [ -n "$F3" ]; then report "F3: data/ imports ui — data must not depend on UI:" "$F3"; fi

F4=$(grep -rEn '^import com\.tmuxes\.ui' "$ROOT/ssh" 2>/dev/null || true)
if [ -n "$F4" ]; then report "F4: ssh/ imports ui — ssh must not depend on UI:" "$F4"; fi

if [ "$errors" -gt 0 ]; then
    echo ""
    echo "💥 $errors architecture-layering violation(s). Keep ui/screens out of data.db and ssh.internal, and keep data/ssh independent of ui."
    exit 1
fi
echo "✅ Architecture layers (F1-F4) clean."

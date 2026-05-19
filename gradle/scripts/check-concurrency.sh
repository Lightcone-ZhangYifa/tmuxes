#!/usr/bin/env bash
#
# Concurrency safety gate. App work must run in owned scopes, and blocking
# coroutine bridges must stay isolated to the SSH transport adapter.
#
# E1  No GlobalScope (use viewModelScope / lifecycleScope / scoped CoroutineScope)
# E2  No runBlocking(...) outside HostKeyVerifier.kt (SSHJ transport thread bridge)

set -euo pipefail

ROOT="${ROOT:-app/src/main/java/com/tmuxes}"

errors=0
report() {
    echo "❌ $1"
    echo "$2"
    errors=$((errors + $(echo "$2" | wc -l)))
}

E1=$(grep -rEn '\bGlobalScope\b' "$ROOT" 2>/dev/null || true)
if [ -n "$E1" ]; then report "E1: GlobalScope — use viewModelScope or scoped CoroutineScope with cleanup:" "$E1"; fi

E2=$(grep -rEn '\brunBlocking\b' "$ROOT" 2>/dev/null \
     | grep -vE 'HostKeyVerifier\.kt|//|/\*' \
     || true)
if [ -n "$E2" ]; then report "E2: runBlocking outside HostKeyVerifier.kt — refactor to suspend or use the existing transport-bridge file:" "$E2"; fi

if [ "$errors" -gt 0 ]; then
    echo ""
    echo "💥 $errors concurrency violation(s). Use owned CoroutineScopes and keep runBlocking out of app code."
    exit 1
fi
echo "✅ Concurrency (E1-E2) clean."

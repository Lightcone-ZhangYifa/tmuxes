#!/usr/bin/env bash
#
# Logging gate. All app logging goes through AppLogger so release builds avoid
# eager message construction and crash reports keep useful breadcrumbs.
#
# D1  No `import android.util.Log` outside util/ {AppLogger,CrashLogWriter}.kt
# D2  No raw `Log.d/i/w/e/v(...)` calls outside util/
# D3  No `println(...)` debug output anywhere
# D4  AppLogger.X(Category.Y, "literal") forbidden — must be lambda form
# D5  Empty silent `catch (_: Throwable|Exception) {}` blocks must either:
#       - have an obvious cleanup verb in the try body
#         (close/cancel/disconnect/unregister/abort/dismiss/finish/delete/
#          recycle/stopSelf/join/interrupt/quit/shutdown/unbindService/
#          removeCallbacks/releaseLock/destroy)
#       - have an inline `// allow-bypass-D5: <reason>` comment, OR
#       - sit in a file with `// allow-bypass-D5: <reason>` as the first
#         line (defensive Compose / IME / draw wrappers)
# D6  `Category.valueOf(...)` only allowed in AppLogger.kt + DebugLogReceiver.kt
#     (debug ADB broadcast must construct Category from a user-typed string)

set -euo pipefail

ROOT="${ROOT:-app/src/main/java/com/tmuxes}"
ALLOW='util/AppLogger\.kt|util/CrashLogWriter\.kt'

errors=0
report() {
    echo "❌ $1"
    echo "$2"
    errors=$((errors + $(echo "$2" | wc -l)))
}

# --- D1 / D2 / D3 ---
D1=$(grep -rEn '^import android\.util\.Log$' "$ROOT" 2>/dev/null | grep -vE "$ALLOW" || true)
if [ -n "$D1" ]; then report "D1: import android.util.Log outside AppLogger — use AppLogger:" "$D1"; fi

D2=$(grep -rEn '\bLog\.[diwev]\(' "$ROOT" 2>/dev/null | grep -vE "$ALLOW" || true)
if [ -n "$D2" ]; then report "D2: raw Log.X(...) call outside AppLogger — use AppLogger.X(Category, msg):" "$D2"; fi

D3=$(grep -rEn '\bprintln\(' "$ROOT" 2>/dev/null || true)
if [ -n "$D3" ]; then report "D3: println(...) debug output — use AppLogger:" "$D3"; fi

# --- D4: lambda form only ---
# Pattern: AppLogger.[tdiwe](Category.X, "...) — eager string literal forbidden.
D4=$(grep -rEn 'AppLogger\.[tdiwe]\([^)]*Category\.[A-Z_]+\s*,\s*"' "$ROOT" 2>/dev/null \
     | grep -vE 'allow-bypass-D4' \
     | grep -vE "$ALLOW" \
     || true)
if [ -n "$D4" ]; then report "D4: eager-string AppLogger call — use lambda form { \"...\" }:" "$D4"; fi

# --- D5: silent catches with no log + no cleanup pattern + no allow-bypass ---
D5=$(python3 - "$ROOT" <<'PYEOF' || true
import re
import sys
from pathlib import Path

root = Path(sys.argv[1])
CLEANUP = re.compile(
    r'\b(close|cancel|disconnect|unregister[A-Za-z]*|removeObserver|abort[A-Za-z]*|'
    r'dismiss|finish|delete|recycle|stopSelf|join|interrupt|quit|shutdown|'
    r'unbindService|removeCallbacks|release[A-Za-z]*|destroy|detach[A-Za-z]*|'
    r'acknowledge[A-Za-z]*)\s*\('
)
PATTERN = re.compile(
    r'try\s*\{([^{}]*?)\}\s*catch\s*\(_: (Throwable|Exception)\??\)\s*\{\s*\}'
)
LINE_ALLOW = re.compile(r'allow-bypass-D5')

violations = []
for path in root.rglob("*.kt"):
    text = path.read_text()
    # File-level allow: first non-blank line containing allow-bypass-D5
    first_lines = '\n'.join(text.split('\n')[:3])
    if LINE_ALLOW.search(first_lines):
        continue
    # Locate each empty silent catch and find its line number
    lines = text.split('\n')
    line_starts = [0]
    for ln in lines:
        line_starts.append(line_starts[-1] + len(ln) + 1)
    for m in PATTERN.finditer(text):
        body = m.group(1).strip()
        if CLEANUP.search(body):
            continue
        # Determine line number of the catch
        catch_pos = m.end() - 2  # near closing }
        line_no = next(i for i, s in enumerate(line_starts) if s > catch_pos) - 1
        line_text = lines[line_no] if line_no < len(lines) else ""
        if LINE_ALLOW.search(line_text):
            continue
        # Print the catch line
        violations.append(f"{path.relative_to(root.parent.parent.parent.parent.parent.parent)}:{line_no+1}:{line_text.strip()[:120]}")
print('\n'.join(violations))
PYEOF
)
if [ -n "$D5" ]; then report "D5: silent catch (Throwable|Exception) {} — add log call, change to a recognized cleanup verb, or annotate with // allow-bypass-D5: <reason>:" "$D5"; fi

# --- D6: Category.valueOf only in AppLogger + DebugLogReceiver ---
D6=$(grep -rEn 'AppLogger\.Category\.valueOf|\bCategory\.valueOf\(' "$ROOT" 2>/dev/null \
     | grep -vE 'AppLogger\.kt|DebugLogReceiver\.kt|allow-bypass-D6' \
     || true)
if [ -n "$D6" ]; then report "D6: Category.valueOf(...) outside AppLogger/DebugLogReceiver — use Category.X enum literal:" "$D6"; fi

if [ "$errors" -gt 0 ]; then
    echo ""
    echo "💥 $errors logging violation(s). Use AppLogger, lambda messages, and explicit silent-catch bypass reasons."
    exit 1
fi
echo "✅ Logging (D1-D6) clean."

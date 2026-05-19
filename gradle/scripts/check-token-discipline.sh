#!/usr/bin/env bash
#
# Token discipline gate. Screens consume the app token system and app-level
# components instead of reading raw Material theme primitives directly.
#
# B1  No `MaterialTheme.colorScheme.*` reads in screens (use tokens.colors.*)
# B2  No `MaterialTheme.typography.*` reads in screens (use tokens.type.*)
# B3  No `.copy(alpha = X)` in screens (use tokens.colors.divider/etc;
#     allow with `// allow-bypass-B3: <reason>` inline comment)
# B4  No `.copy(fontSize/fontWeight/fontFamily = X)` in screens
# B5  No raw M3 form primitives (`Card / Switch / Slider / Checkbox / RadioButton`) in screens
# B6  No raw `OutlinedTextField` in screens (allow with file-level
#     `// allow-bypass-B6: ExposedDropdownMenuBox anchor` header)

set -euo pipefail

ROOT="${ROOT:-app/src/main/java/com/tmuxes/ui}"
SCREENS="$ROOT/screens"

errors=0
report() {
    echo "❌ $1"
    echo "$2"
    errors=$((errors + $(echo "$2" | wc -l)))
}

# B1: MaterialTheme.colorScheme.X
B1=$(grep -rEn 'MaterialTheme\.colorScheme' "$SCREENS" 2>/dev/null | grep -vE 'allow-bypass-B1' || true)
if [ -n "$B1" ]; then report "B1: MaterialTheme.colorScheme.X in screens — use tokens.colors.X:" "$B1"; fi

# B2: MaterialTheme.typography.X
B2=$(grep -rEn 'MaterialTheme\.typography' "$SCREENS" 2>/dev/null | grep -vE 'allow-bypass-B2' || true)
if [ -n "$B2" ]; then report "B2: MaterialTheme.typography.X in screens — use tokens.type.X:" "$B2"; fi

# B3: .copy(alpha = X). Allow inline `// allow-bypass-B3: …`.
B3=$(grep -rEn '\.copy\(alpha = ' "$SCREENS" 2>/dev/null | grep -vE 'allow-bypass-B3' || true)
if [ -n "$B3" ]; then report "B3: .copy(alpha = X) in screens — use tokens.colors.divider or add allow-bypass-B3 inline comment:" "$B3"; fi

# B4: .copy(fontSize/fontWeight/fontFamily = …)
B4=$(grep -rEn '\.copy\((fontSize|fontWeight|fontFamily) = ' "$SCREENS" 2>/dev/null | grep -vE 'allow-bypass-B4' || true)
if [ -n "$B4" ]; then report "B4: .copy(fontSize|fontWeight|fontFamily) override in screens — use tokens.type.X directly:" "$B4"; fi

# B5: raw M3 form primitives. Need word-boundary so we don't match AppCard / SmallFloatingActionButton, etc.
B5=$(grep -rEn '(^|[^a-zA-Z])(Card|Switch|Slider|Checkbox|RadioButton)\(' "$SCREENS" 2>/dev/null \
     | grep -vE 'App(Card|Switch|Slider|Checkbox|RadioButton)|allow-bypass-B5|//|\\*' \
     || true)
if [ -n "$B5" ]; then report "B5: raw M3 primitive in screens — use App* wrappers:" "$B5"; fi

# B6: raw OutlinedTextField. File-level allow: // allow-bypass-B6: <reason> on first line.
b6_files=$(grep -rEln 'OutlinedTextField\(' "$SCREENS" 2>/dev/null || true)
B6=""
for f in $b6_files; do
    if ! head -1 "$f" | grep -q 'allow-bypass-B6'; then
        hits=$(grep -nE 'OutlinedTextField\(' "$f" | sed "s|^|$f:|")
        if [ -n "$hits" ]; then B6="$B6$hits"$'\n'; fi
    fi
done
if [ -n "$B6" ]; then report "B6: raw OutlinedTextField in screens — use AppTextField, or add file-level allow-bypass-B6 header:" "$B6"; fi

if [ "$errors" -gt 0 ]; then
    echo ""
    echo "💥 $errors token-discipline violation(s). Use app tokens and App* wrappers in screen code."
    exit 1
fi
echo "✅ Token discipline (B1-B6) clean."

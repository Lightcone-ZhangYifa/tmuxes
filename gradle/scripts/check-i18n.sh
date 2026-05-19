#!/usr/bin/env bash
#
# I18n gate — enforces managed UI copy for the Kotlin UI surface.
#
# I1  No direct user-visible string literals in Compose Text(...).
# I2  No direct Snackbar / Toast / ViewModel error literals.
# I3  No direct string literal contentDescription in raw Icon(...).
# I4  No split/concatenated managed-copy keys in t(...) calls.
# I5  No duplicate keys inside any managed copy catalog map.
#
# This gate only checks mechanical i18n discipline. It does not validate
# translation wording and does not require a particular locale-to-source
# mapping policy.

set -euo pipefail

ROOT="${ROOT:-app/src/main/java/com/tmuxes}"

python3 - "$ROOT" <<'PYEOF'
import ast
import re
import sys
from collections import defaultdict
from pathlib import Path

root = Path(sys.argv[1]).resolve()
project_root = Path.cwd()
catalog_path = project_root / "app/src/main/java/com/tmuxes/i18n/AppI18n.kt"

errors: list[tuple[str, str]] = []

def rel(path: Path) -> str:
    return str(path.relative_to(project_root))

def add(rule: str, path: Path, line_no: int, line: str) -> None:
    errors.append((rule, f"{rel(path)}:{line_no}: {line.strip()}"))

def decode_kotlin_string(raw: str) -> str:
    # Python rejects Kotlin's escaped dollar; it is only needed to suppress
    # Kotlin string interpolation, so convert it before normal escape decoding.
    raw = raw.replace(r"\$", "$")
    try:
        return ast.literal_eval(f'"{raw}"')
    except Exception:
        return raw.encode("utf-8").decode("unicode_escape", errors="ignore")

def is_allowed(path: Path, line: str, rule: str) -> bool:
    if f"allow-bypass-{rule}" in line:
        return True
    first_lines = "\n".join(path.read_text().splitlines()[:3])
    if f"allow-bypass-{rule}" in first_lines:
        return True
    # User data display, not application copy.
    return path.name == "AddEditServerScreen.kt" and 'Text("${server.displayName}' in line

def kt_files() -> list[Path]:
    return sorted(p for p in root.rglob("*.kt") if p.is_file())

TEXT_CALL_START = re.compile(r"(?<![A-Za-z0-9_])Text\s*\(")
RAW_TEXT_SAME_LINE = re.compile(r"(?<![A-Za-z0-9_])Text\s*\(\s*(?:text\s*=\s*)?\"[A-Za-z]")
RAW_TEXT_NAMED_ALPHA = re.compile(r"\btext\s*=\s*\"[A-Za-z]")
RAW_TEXT_NAMED_IF_ALPHA = re.compile(r"\btext\s*=\s*if\s*\([^)]*\)\s*\"[A-Za-z]")
RAW_TEXT_POSITIONAL_ALPHA = re.compile(r"^\s*\"[A-Za-z]")
TEXT_ARGUMENT_LINE = re.compile(r"\btext\s*=")

RAW_SNACKBAR = re.compile(r"showSnackbar\s*\(\s*\"|showSnackbar\s*\(\s*message\s*=\s*\"")
RAW_TOAST = re.compile(r"Toast\.makeText\s*\([^,]+,\s*\"")
RAW_VM_ERROR = re.compile(r"_(errorMessage|loadError|systemInfoError)\.value\s*=\s*\"")

ICON_CALL_START = re.compile(r"(?<![A-Za-z0-9_])Icon\s*\(")
RAW_ICON_CONTENT_DESCRIPTION = re.compile(r"\bcontentDescription\s*=\s*\"[A-Za-z]")

SPLIT_T_KEY = re.compile(r"\b(?:I18nRuntime\.)?t\s*\(\s*\"(?:\\.|[^\"\\])*\"\s*\+")
CATALOG_KEY = re.compile(r"^\s*\"((?:\\.|[^\"\\])*)\"\s+to\b")

def check_text_literals(path: Path, lines: list[str]) -> None:
    for index, line in enumerate(lines):
        if RAW_TEXT_SAME_LINE.search(line) and not is_allowed(path, line, "I1"):
            add("I1", path, index + 1, line)
            continue

        if not TEXT_CALL_START.search(line):
            continue

        end = min(index + 6, len(lines) - 1)
        for candidate_index in range(index, end + 1):
            candidate = lines[candidate_index]
            raw_named = RAW_TEXT_NAMED_ALPHA.search(candidate) or RAW_TEXT_NAMED_IF_ALPHA.search(candidate)
            raw_positional = candidate_index == index + 1 and RAW_TEXT_POSITIONAL_ALPHA.search(candidate)
            if (raw_named or raw_positional) and not is_allowed(path, candidate, "I1"):
                add("I1", path, candidate_index + 1, candidate)
                break
            if candidate_index > index and TEXT_ARGUMENT_LINE.search(candidate):
                break
            if candidate_index == index + 1 and candidate.lstrip().startswith("t("):
                break

def check_feedback_literals(path: Path, lines: list[str]) -> None:
    for index, line in enumerate(lines, start=1):
        if (RAW_SNACKBAR.search(line) or RAW_TOAST.search(line) or RAW_VM_ERROR.search(line)) and not is_allowed(path, line, "I2"):
            add("I2", path, index, line)

def check_icon_content_descriptions(path: Path, lines: list[str]) -> None:
    for index, line in enumerate(lines):
        if not ICON_CALL_START.search(line):
            continue
        end = min(index + 8, len(lines) - 1)
        for candidate_index in range(index, end + 1):
            candidate = lines[candidate_index]
            if RAW_ICON_CONTENT_DESCRIPTION.search(candidate) and not is_allowed(path, candidate, "I3"):
                add("I3", path, candidate_index + 1, candidate)
                break
            if candidate_index > index and candidate.strip() == ")":
                break

def check_split_t_keys(path: Path, lines: list[str]) -> None:
    for index, line in enumerate(lines, start=1):
        if SPLIT_T_KEY.search(line) and not is_allowed(path, line, "I4"):
            add("I4", path, index, line)

def catalog_keyspaces() -> dict[str, dict[str, list[int]]]:
    keyspaces: dict[str, dict[str, list[int]]] = {}
    active_name: str | None = None
    entries: dict[str, list[int]] | None = None
    for line_no, line in enumerate(catalog_path.read_text().splitlines(), start=1):
        start = re.search(r"\bval\s+([A-Za-z0-9_]+)\s*=\s*mapOf\s*\(", line)
        if start:
            active_name = start.group(1)
            entries = defaultdict(list)
            keyspaces[active_name] = entries
            continue
        if active_name is not None and line.strip() == ")":
            active_name = None
            entries = None
            continue
        if entries is None:
            continue
        match = CATALOG_KEY.search(line)
        if match:
            entries[decode_kotlin_string(match.group(1))].append(line_no)
    return keyspaces

for path in kt_files():
    text = path.read_text()
    lines = text.splitlines()
    check_text_literals(path, lines)
    check_feedback_literals(path, lines)
    check_icon_content_descriptions(path, lines)
    check_split_t_keys(path, lines)

for name, entries in sorted(catalog_keyspaces().items()):
    for key, lines in sorted(entries.items()):
        if len(lines) > 1:
            errors.append(("I5", f"{rel(catalog_path)}:{','.join(map(str, lines))}: duplicate key in catalog map {name}: {key}"))

if errors:
    by_rule: dict[str, list[str]] = defaultdict(list)
    for rule, message in errors:
        by_rule[rule].append(message)
    for rule in sorted(by_rule):
        print(f"❌ {rule}:")
        for message in by_rule[rule]:
            print(message)
    print()
    print(f"💥 {len(errors)} i18n violation(s). Route user-visible copy through t(...) and keep catalog keys literal.")
    sys.exit(1)

print("✅ I18n discipline (I1-I5) clean.")
PYEOF

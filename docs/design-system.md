# tmuxes design system

> Single source of truth for every visual decision in the app. All screens,
> components, and dialogs read styling from this system; nothing is
> allowed to hardcode colors, sizes, shapes, or typography.

---

## 1. Architecture

```
ui/design/                        ← token definitions (single source of truth)
  AppTokens.kt                    ← top-level @Immutable container
  ColorTokens.kt                  ← M3 roles + accent / danger / divider / dimmer + onColor
  TypeTokens.kt                   ← 18 type styles incl. sectionHeader, mono, monoSmall
  ShapeTokens.kt                  ← Sharp / Rounded / Pill style families
  SpaceTokens.kt                  ← 8 dp grid, scaled by density
  ElevationTokens.kt              ← level0..level5
  MotionTokens.kt                 ← duration tiers + emphasized/standard easings
  StatusTokens.kt                 ← semantic status colors (connected/error/warning/...)
  AppTheme.kt                     ← AppTheme(...) Composable + LocalAppTokens

ui/components/app/                ← App component library — every primitive
  AppScaffold / AppTopBar / AppFab / AppBottomBar / AppNavigationDrawer / AppNavigationRail
  AppCard / AppListItem / AppSection / AppSectionHeader
  AppButton (Primary/Secondary/Tonal/Text/Outlined/Danger) / AppIconButton
  AppTextField / AppSwitch / AppSlider / AppCheckbox / AppRadioButton
  AppDialog / AppDeleteDialog / AppBottomSheet
  AppEmptyState / AppErrorState / AppLoadingState
  AppFilterChip / AppAssistChip / AppHorizontalDivider / AppVerticalSpacer
  StatusDot / StatusIndicator / SeverityBadge

ui/components/                    ← interaction primitives (NOT styling)
  DragToSelectModifier / MultiSelectState / MultiSelectTopBar
  ColorPickerDialog / AlgorithmSelector / CrashDetectedDialog
  (swipe-action lives at ui/components/app/AppRowSwipe.kt — single component, all 9 callers)
```

---

## 2. The 7 user-controlled appearance axes

Defined in `Settings.kt` under `app.*`:

| Setting | Type | Options | Effect |
|---|---|---|---|
| `app.theme` | enum | dark / light / system | chooses dark vs light tokens |
| `app.color_palette` | enum | mocha / material_you / custom | source for color tokens |
| `app.accent_color` | ARGB int | 0 = palette default | accent seed when palette = custom |
| `app.density` | enum | compact / comfortable / spacious | scales `SpaceTokens` (×0.75 / ×1.0 / ×1.25) |
| `app.type_scale` | enum | small / default / large | scales `TypeTokens` (×0.82 / ×0.9 / ×1.05) |
| `app.corner_style` | enum | sharp / rounded / pill | switches `ShapeTokens` family |
| `app.status_bar_style` | enum | auto / surface / transparent | window status/nav bar tint |
| `app.navigation_style` | enum | bottom_bar / rail / drawer | three real navigation modes |

All eight settings produce visible, app-wide changes.

---

## 3. Token usage cheat sheet

Inside any `@Composable`:

```kotlin
val tokens = MaterialTheme.appTokens

// Colors
Modifier.background(tokens.colors.surfaceContainer)
Text(text, color = tokens.colors.onSurface)
StatusDot(color = tokens.status.connected)

// Typography
Text(text, style = tokens.type.titleMedium)
Text(label, style = tokens.type.sectionHeader)        // uppercase headers
Text(code, style = tokens.type.mono)                  // monospace body
Text(small, style = tokens.type.monoSmall)            // monospace small

// Shapes
Modifier.clip(tokens.shape.lg)                        // card / dialog
Modifier.clip(tokens.shape.md)                        // list-item
Modifier.clip(tokens.shape.pill)                      // FAB / circular icon

// Spacing (8 dp grid)
Modifier.padding(tokens.space.lg)                     // 16 dp at Comfortable density
Spacer(Modifier.height(tokens.space.sm))              // 8 dp
Arrangement.spacedBy(tokens.space.md)                 // 12 dp

// Elevation
Modifier.shadow(tokens.elevation.level3, tokens.shape.lg)
```

---

## 4. Component cheat sheet

### Screens

```kotlin
AppScaffold(
    title = "Servers",
    onBack = onNavigateBack,
    actions = { AppIconButton(Icons.Filled.Refresh, onRefresh) },
    fab = { AppFab(Icons.Filled.Add, onAdd) }
) { padding ->
    LazyColumn(modifier = Modifier.padding(padding)) { ... }
}
```

### Lists

```kotlin
AppCard {
    AppListItem(
        title = server.name,
        subtitle = "${server.user}@${server.host}",
        leadingIcon = Icons.Filled.Dns,
        onClick = { onClick(server.id) },
        trailing = { StatusDot(color = tokens.status.forServerStatus(s)) }
    )
}
```

### Forms

```kotlin
AppTextField(value = name, onValueChange = ::name, label = "Name")
AppSwitch(checked = enabled, onCheckedChange = ::enabled)
AppSlider(value = port.toFloat(), onValueChange = { port = it.toInt() }, valueRange = 1f..65535f)
AppButton(text = "Save", onClick = ::save, style = AppButtonStyle.Primary)
AppButton(text = "Delete", onClick = ::delete, style = AppButtonStyle.Danger)
```

### Dialogs / sheets

```kotlin
if (showConfirm) {
    AppDeleteDialog(
        title = "Delete server?",
        message = "This cannot be undone.",
        onConfirm = ::confirmDelete,
        onDismiss = { showConfirm = false }
    )
}
if (showSettings) {
    AppBottomSheet(onDismiss = { showSettings = false }) { ... }
}
```

### Empty / error / loading

```kotlin
when {
    isLoading -> AppLoadingState(message = "Connecting…")
    error != null -> AppErrorState(title = "Failed to load", subtitle = error.message, onRetry = ::retry)
    items.isEmpty() -> AppEmptyState(icon = Icons.Filled.Inbox, title = "No items", subtitle = "Tap + to add one")
    else -> ContentList(items)
}
```

---

## 5. Hard rules

Hard rules across A-I categories are enforced by the `checkDesignRules`
Gradle task (wired into `./gradlew check`). Build fails if any rule
fires. The rule catalogue is implemented by the scripts under `gradle/scripts`.

| Category | Scripts | Rules |
|---|---|---|
| **A** Appearance | `check-no-hardcoded-styles.sh` | A1-A4: no `Color(0x…)`, `RoundedCornerShape(N.dp)`, `fontSize=N.sp`, raw `TopAppBar`/`FAB`/`AlertDialog` in screens |
| **B** Token discipline | `check-token-discipline.sh` | B1-B6: no `MaterialTheme.colorScheme/typography` reads in screens, no `.copy(alpha)` / `.copy(font*)` overrides, no raw M3 form primitives, no raw `OutlinedTextField` |
| **C** Settings registry | `check-settings-registry.sh` | C1-C3: no `getSharedPreferences`, no string-keyed `yamlConfig` API, `preferences.X(...)` must take `Settings.X` |
| **D** Logging | `check-logging.sh` | D1-D3: no raw `android.util.Log` outside `AppLogger`, no `println` |
| **E** Concurrency | `check-concurrency.sh` | E1-E2: no `GlobalScope`; `runBlocking` only in `HostKeyVerifier.kt` |
| **F** Architecture layering | `check-architecture-layers.sh` | F1-F4: `ui/screens` ⊥ `data.db` / `ssh.internal`; `data` and `ssh` ⊥ `ui` |
| **G+H** Misc | `check-misc-discipline.sh` | G1, H1: no `/sdcard` paths, no wildcard imports |
| **I** i18n discipline | `check-i18n.sh` | I1-I5: no unmanaged visible copy, no split `t(...)` keys, no duplicate managed catalog keys; does not validate translation wording |

Run individually with `./gradlew checkTokenDiscipline`, `checkSettingsRegistry`, etc., or all at once with `./gradlew checkDesignRules`.

### Allow-list mechanism

- **Inline (per-line):** add `// allow-bypass-<rule-id>: <reason>` at end of the violating line. Used for B3 translucent-overlay shading at 2 sites in `TerminalScreen.kt`.
- **File-level (per-file):** add `// allow-bypass-<rule-id>: <reason>` as the FIRST line of the file (before `package`). Used for B6 `ExposedDropdownMenuBox` anchors in 3 files (`SnippetsScreen.kt`, `SessionPickerScreen.kt`, `AddEditServerScreen.kt`).
- **Hard-coded (script-level):** scripts can hard-code single-file allow-listing only for platform bridges that have no other choice (e.g. E2 allows `runBlocking` solely in `HostKeyVerifier.kt` because SSHJ's transport thread invokes the verifier synchronously). Adding new entries requires PR-level justification + doc update.

When introducing a new exception, document the product and implementation reason
in the pull request and update the relevant rule script or tests when needed.

---

## 6. Adding a new setting that affects appearance

1. Declare it in `Settings.kt` with the right `Setting<T>` type and `SettingUi(...)`.
2. Add it to the relevant group in `SettingScreens.appAppearance`.
3. Read it in `MainActivity.setContent` and pass to `AppTheme(...)` if it's a token-driving axis (theme/palette/density/typeScale/cornerStyle/statusBarStyle).
4. If it's NOT a token axis (e.g., an interaction toggle), expose it via a CompositionLocal at the AppTheme layer and read in the consuming screen.

Never:
- read user appearance settings directly inside a leaf component
- introduce new top-level visual state outside `AppTokens`

---

## 7. Adding a new App* component

1. New file in `ui/components/app/AppFoo.kt`.
2. Body reads `MaterialTheme.appTokens` only — no `Color(0x…)` / `RoundedCornerShape(N.dp)` / `.sp` / `.dp` literals (except via `Int.dpUnit()` helper for element-physical sizes that aren't in the spacing scale).
3. Variants go through enums (`AppFooStyle.{Primary,Secondary,…}`), not raw color / size parameters.
4. Document the component above in §4.

---

## 8. Quick start for new screens

```kotlin
@Composable
fun MyScreen(onBack: () -> Unit) {
    val tokens = MaterialTheme.appTokens
    AppScaffold(
        title = "My Screen",
        onBack = onBack,
        fab = { AppFab(Icons.Filled.Add, onClick = ::add) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding)
                .padding(horizontal = tokens.space.lg),
            verticalArrangement = Arrangement.spacedBy(tokens.space.sm)
        ) {
            item { AppSectionHeader(text = "Section A") }
            items(rows) { row ->
                AppCard {
                    AppListItem(
                        title = row.title,
                        subtitle = row.subtitle,
                        leadingIcon = Icons.Filled.Folder,
                        onClick = { open(row) }
                    )
                }
            }
        }
    }
}
```

That's the canonical shape. If your screen needs anything more elaborate (custom drawing, terminal grid, etc.), keep the SHELL on App* components and let the custom region read its own colors as needed (with comments explaining why).

---

## 9. Logging (cross-cutting)

Logging is a first-class concern, not a debugging afterthought. Every screen, ViewModel, repository, and SSH path goes through one API:

```kotlin
import com.tmuxes.util.AppLogger
import com.tmuxes.util.AppLogger.Category

AppLogger.i(Category.SESSION) { "vm.attachSession id=$id name='$name'" }
AppLogger.timed(Category.NET, "probe.tcp $host:$port") { /* work */ }
```

Hard rules (enforced by `gradle/scripts/check-logging.sh` — see [`docs/design-rules-2026-05-05.md`](design-rules-2026-05-05.md) §3.D):

- D1/D2: never `import android.util.Log` or call `Log.X(...)` outside `util/AppLogger.kt`.
- D3: never `println(...)`.
- D4: always lambda form `{ "..." }` — eager strings allocate even when the level is disabled.
- D5: silent `catch (_: Throwable\|Exception) {}` must either contain a recognized cleanup verb (close/cancel/disconnect/release/.../destroy), call `AppLogger.X`, or carry an explicit `// allow-bypass-D5: <reason>` (file-level header line 1, or inline same-line comment).
- D6: `Category.valueOf(...)` only in `AppLogger.kt` + `DebugLogReceiver.kt`.

For the full surface (17 categories, 5 levels, breadcrumb ring, debug ADB tuner, bundle export), see [`docs/debug-logging-system-2026-05-05.md`](debug-logging-system-2026-05-05.md).

For the symptom→trace cookbook ("widget shows red", "host key changed", "session keeps reconnecting"), see [`docs/debugging-playbook.md`](debugging-playbook.md).

---

## 10. References

- [Implementation history `docs/appearance-redesign-2026-05-05.md`](appearance-redesign-2026-05-05.md) — context, audit findings, 11-stage execution log.
- [Settings registry `docs/settings-registry-2026-05-05.md`](settings-registry-2026-05-05.md) — the typed-Setting registry that drives both YAML and the appearance UI.
- [Debug logging system `docs/debug-logging-system-2026-05-05.md`](debug-logging-system-2026-05-05.md) — AppLogger spec, categories, breadcrumb buffer, ADB tuner.
- [Design rules `docs/design-rules-2026-05-05.md`](design-rules-2026-05-05.md) — 30 enforced gates including D1–D6.
- [Debugging playbook `docs/debugging-playbook.md`](debugging-playbook.md) — symptom → logcat filter → root-cause cookbook.

# Contributing To tmuxes

Thanks for helping make tmuxes better. This project has a fairly strict
engineering style because it touches SSH credentials, persistent sessions,
Android lifecycle edges, and a custom terminal surface.

## Before You Start

- Search existing issues and discussions.
- For small fixes, open a focused pull request.
- For architectural changes, open an issue first with the problem, evidence,
  affected modules, and validation plan.
- Never attach private keys, passwords, hostnames, production logs, or release
  signing files.

## Development Setup

Install:

- JDK 17
- Android SDK with API 36
- Android platform-tools on `PATH`

Run the main local gate:

```bash
./gradlew compileDebugKotlin testDebugUnitTest checkDesignRules lintDebug
```

Build and launch the debug app:

```bash
./gradlew assembleDebug
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.tmuxes.debug/com.tmuxes.MainActivity
```

More details are in [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).

## Engineering Principles

- Fix the root cause. Avoid bolt-on state, hidden fallback branches, and new
  global trackers unless the architecture truly requires them.
- Keep external behavior stable unless the pull request explicitly documents a
  behavior change.
- Prefer existing packages, component APIs, settings registry entries, and
  design tokens.
- Do not add compatibility shims for internal rewrites. Remove old names and
  migrate callers in the same change.
- Keep UI behavior aligned with mainstream Android, VS Code, and JetBrains IDE
  conventions where those conventions apply.
- Let test coverage follow risk: JVM tests for pure logic, emulator validation
  for Android lifecycle, UI, widget, SSH, or terminal side effects.

## Pull Request Shape

Good pull requests are small enough to review carefully and complete enough to
prove the behavior. Prefer one product or architecture concern per PR. If a
change needs several steps, land the mechanical preparation separately from the
behavioral change.

Every PR should make clear:

- The user-visible or maintainer-visible problem.
- The smallest reproduction or supporting evidence.
- The behavior that intentionally stays unchanged.
- The exact validation commands and device or emulator evidence.

## Code Style

- Kotlin style is the official Kotlin style.
- New visible UI text must go through the i18n catalog.
- UI screens should use `ui/components/app` primitives and
  `MaterialTheme.appTokens`.
- Do not use raw Android `Log` calls outside `AppLogger`.
- Do not introduce wildcard imports.
- Do not commit generated build output.

The Gradle task `checkDesignRules` enforces many of these constraints.

## Pull Request Checklist

Before opening a PR:

```bash
./gradlew compileDebugKotlin testDebugUnitTest checkDesignRules lintDebug
```

For UI, terminal, widget, SSH, process lifecycle, or Android component changes,
also validate on an emulator and include the relevant `uiautomator` or `logcat`
evidence in the PR.

If validation is skipped, explain why it was not possible and what risk remains.

## Release Signing

Release signing material is private operational state. Do not commit keystores,
passwords, or generated release APKs. See [docs/RELEASING.md](docs/RELEASING.md)
for the supported local and CI signing inputs.

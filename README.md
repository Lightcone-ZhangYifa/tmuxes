# tmuxes

<p align="center">
  <strong>An Android SSH and tmux workspace for persistent remote work.</strong>
</p>

<p align="center">
  <a href="https://github.com/Lightcone-ZhangYifa/tmuxes/actions/workflows/android-ci.yml"><img alt="Android CI" src="https://github.com/Lightcone-ZhangYifa/tmuxes/actions/workflows/android-ci.yml/badge.svg"></a>
  <a href="https://github.com/Lightcone-ZhangYifa/tmuxes/actions/workflows/codeql.yml"><img alt="CodeQL" src="https://github.com/Lightcone-ZhangYifa/tmuxes/actions/workflows/codeql.yml/badge.svg"></a>
  <a href="LICENSE"><img alt="License: GPL-3.0-only" src="https://img.shields.io/badge/license-GPL--3.0--only-blue.svg"></a>
  <a href="app/build.gradle.kts"><img alt="Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84.svg"></a>
  <a href="build.gradle.kts"><img alt="Kotlin 2.1" src="https://img.shields.io/badge/Kotlin-2.1-7F52FF.svg"></a>
</p>

<p align="center">
  <img src="docs/assets/readme/tmuxes-readme-screens.png" alt="tmuxes Android screenshots showing servers, htop terminal monitoring, and widget YAML configuration" width="100%">
</p>

tmuxes turns Android into a serious control surface for tmux-backed remote
work. SSH provides the transport, tmux owns the durable workspace, and the app
adds native Android sessions, widgets, snippets, YAML configuration, and a
terminal UI built for long-running work.

This project is independent software and is not affiliated with tmux.

## Why Developers Pick It

- **tmux-first workflow**: keep build jobs, shells, logs, and editors alive on
  remote machines instead of tying them to an Android Activity.
- **Live Android surfaces**: inspect terminal state from launcher widgets and
  use a Quick Settings tile for connection control.
- **Native terminal depth**: custom emulator, scrollback, wide-character
  coverage, bracketed paste, copy mode, gestures, extra keys, and volume-key
  actions.
- **Inspectable configuration**: manage servers, widgets, snippets, settings,
  and terminal behavior through YAML-backed repositories and an in-app editor.
- **Security-aware SSH**: password and private-key auth, persisted known-host
  records, changed-key handling, keepalive, and local or remote forwarding.
- **Contributor-grade codebase**: Compose UI, Room, DataStore, SSHJ, strict
  architecture gates, lint, JVM tests, CodeQL, and Dependabot.

## Quick Start

Requirements:

- JDK 17
- Android SDK with API 36
- Android platform-tools on `PATH`

Build and validate the debug app:

```bash
./gradlew compileDebugKotlin testDebugUnitTest checkDesignRules lintDebug
./gradlew assembleDebug
```

Install it on a connected device or emulator:

```bash
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.tmuxes.debug/com.tmuxes.MainActivity
```

Release signing material is intentionally private. Unsigned release builds work
from source; signed releases use local or CI-injected properties described in
[docs/RELEASING.md](docs/RELEASING.md).

## What Is Inside

| Area | What it owns |
| --- | --- |
| `ssh` | SSH configuration, host-key verification, connection pool, forwarding, keepalive |
| `tmux` | tmux command construction, session creation, attach, rename, kill, picker flows |
| `terminal` | Emulator state, renderer, gestures, input, copy/paste, modifier handling |
| `widget` | Launcher widgets, bitmap terminal previews, Quick Settings integration |
| `data` | Room, DataStore, YAML repositories, settings registry, app models |
| `editor` | YAML editor commands, diagnostics, completion, keybar, editing bubbles |
| `ui` | Compose screens, app components, navigation, design tokens |

Start with [docs/README.md](docs/README.md) for the public documentation map
and [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the package boundaries.

## Quality Bar

The main local gate is:

```bash
./gradlew compileDebugKotlin testDebugUnitTest checkDesignRules lintDebug
```

`checkDesignRules` enforces project-specific contracts for UI tokens, i18n,
logging, settings access, coroutine boundaries, package layering, import
hygiene, and storage paths. These checks are part of the architecture, not a
style preference.

CI runs compilation, JVM tests, design-rule gates, lint, CodeQL analysis, and
dependency automation. Changes that touch UI, terminal rendering, widgets, SSH,
services, or Android lifecycle should include emulator or device evidence.

## Project Status

tmuxes is source-buildable today and under active development. The app already
contains the core SSH/tmux workflow, terminal surface, widgets, YAML editor,
settings system, and release-signing hooks. Public binary release cadence and
store distribution are still being established.

Useful entry points:

- [Roadmap](docs/ROADMAP.md)
- [FAQ](docs/FAQ.md)
- [Development guide](docs/DEVELOPMENT.md)
- [Security policy](SECURITY.md)
- [Privacy model](docs/PRIVACY.md)

## Contributing

Issues and pull requests are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md)
before starting non-trivial work.

Good contributions are focused, reproducible, and easy to review: explain the
problem, keep the change scoped, preserve external behavior unless the PR says
otherwise, add tests where risk justifies them, and run the quality gate before
opening the pull request.

Never commit private keys, passwords, hostnames, production logs, generated
APKs, app bundles, keystores, or release-signing material.

## Security And Privacy

tmuxes manages SSH credentials, host fingerprints, configuration, and debug
logs. Report sensitive vulnerabilities through the private process in
[SECURITY.md](SECURITY.md), and review [docs/PRIVACY.md](docs/PRIVACY.md) for
the current data-handling model.

## Third-Party Software

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for the primary runtime
libraries, bundled assets, and license notes.

## License

tmuxes is licensed under the [GNU General Public License v3.0 only](LICENSE)
(`GPL-3.0-only`), except for bundled third-party assets and libraries that
retain their own licenses.

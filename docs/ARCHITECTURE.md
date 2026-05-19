# Architecture

tmuxes is currently a single Android application module. The package layout is
the source of truth for architecture boundaries.

## Product Model

The app is tmux-first:

- SSH establishes transport to a configured server.
- tmux owns durable remote sessions.
- The Android Activity, widgets, and Quick Settings tile are control surfaces.
- Terminal rendering is local Android UI over remote tmux-backed streams.

This means long-running work should survive Activity recreation and normal app
navigation. UI components should not become owners of SSH or tmux lifecycle
state.

## Main Packages

| Package | Responsibility |
| --- | --- |
| `com.tmuxes.ssh` | SSH configuration, connection pool, host-key verification, keepalive, forwarding, and connection triggers |
| `com.tmuxes.tmux` | tmux command construction and session orchestration |
| `com.tmuxes.session` | Managed session state and resize propagation |
| `com.tmuxes.terminal.emulator` | Terminal buffer, cell encoding, escape handling, scrollback, resize behavior |
| `com.tmuxes.terminal.view` | Android terminal view, renderer, gestures, input, copy/paste, modifier keys |
| `com.tmuxes.widget` | Launcher widgets, bitmap terminal previews, widget configuration, Quick Settings tile |
| `com.tmuxes.data` | Room entities/DAOs, DataStore preferences, YAML repositories, settings registry |
| `com.tmuxes.editor` | YAML editor commands, diagnostics, completion, keybar, and editor bubbles |
| `com.tmuxes.ui.design` | App theme and design tokens |
| `com.tmuxes.ui.components` | Reusable UI primitives and interaction helpers |
| `com.tmuxes.ui.screens` | Screen-level Compose UI |
| `com.tmuxes.util` | Logging, crash bundles, small platform utilities |

## Layering Rules

The Gradle task `checkDesignRules` enforces package-level constraints:

- Screens must not import database internals.
- Data and SSH packages must not import UI packages.
- UI screens use app components and design tokens instead of raw Material
  primitives and hardcoded style values.
- Logging goes through `AppLogger`.
- Settings access goes through the settings registry.
- Wildcard imports, unmanaged visible text, `GlobalScope`, and raw `/sdcard`
  paths are rejected.

These checks are intentionally part of CI because they prevent architecture
drift that is hard to recover from later.

## SSH And tmux Lifecycle

`ConnectionSupervisor`, `ConnectionTrigger`, and `SshConnectionPool` coordinate
when connections should exist. New connection-starting behavior should go
through this path rather than starting ad hoc SSH clients from UI code.

The whole app assumes tmux-backed remote sessions. Free-shell behavior should
not be introduced as an incidental fallback. If a feature needs a different
execution model, document the product and lifecycle implications first.

## Terminal Resize

Terminal sizing crosses UI, emulator, SSH, tmux, and widget surfaces. Resize
events should go through `SessionResizeBus` and the existing session
coordination path. Direct calls that bypass the bus can recreate stale-size and
race bugs.

## Editor Model

The YAML editor uses patch-like document commands for text mutations. Editor
actions should avoid tracking independent selection state outside the editor
unless the code proves why that state cannot be derived at execution time.

## UI System

The visual system is described in [design-system.md](design-system.md). Screens
compose app-owned primitives and read `MaterialTheme.appTokens`. Adding new
style axes or bypasses requires a doc update and, when appropriate, a
design-rule update.

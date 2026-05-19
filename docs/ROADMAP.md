# Roadmap

tmuxes is built around one product bet: tmux is the durable remote workspace,
and Android should be a serious native control surface for it.

This roadmap is intentionally public and practical. It avoids promising dates,
but it should make project direction clear enough for contributors to choose
useful work.

## Now

- Keep the current Android app buildable from a fresh clone.
- Prepare the first public signed APK release with checksums, release notes,
  and a clear security note.
- Improve first-run server setup, host-key trust decisions, and empty states.
- Tighten terminal behavior around paste, modifiers, resizing, scrollback, and
  copy mode.
- Improve debug bundle redaction guidance before broader public testing.
- Keep design-rule gates strict so the Compose UI does not drift into one-off
  styling.

## Next

- Public demo media: current screenshots, short videos, and a GitHub social
  preview that show widgets, terminal sessions, snippets, and YAML editing.
- More focused emulator/instrumentation coverage for widgets and Activity
  lifecycle flows.
- Issue backlog curation with `good first issue`, `help wanted`, `security`,
  `terminal`, `ssh`, `widget`, `docs`, and `design-system` labels.
- More tmux-aware navigation for switching servers, sessions, panes, and
  common remote workflows.

## Later

- App bundle / store distribution workflow.
- Reproducible release notes and binary provenance.
- Optional import/export flows with explicit redaction and backup semantics.
- More tmux-aware navigation and workspace management.
- Deeper terminal compatibility testing against common CLI applications.
- Contributor-owned docs for real-world setups, snippets, and workflows.

## Non-Goals For Now

- Becoming a generic local shell app.
- Replacing tmux as the remote session owner.
- Adding telemetry.
- Storing maintainer signing keys or release passwords in the repository.
- Accepting large rewrites that are not tied to a reproduced problem,
  measurable quality improvement, or documented product direction.

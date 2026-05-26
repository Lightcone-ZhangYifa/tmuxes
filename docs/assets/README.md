# Media Assets

This directory contains public-facing images for the repository, release pages,
and project documentation.

Tracked assets:

- `readme/tmuxes-mark.svg`: project mark used in the README hero.
- `readme/tmuxes-readme-screens.png`: horizontally stitched emulator
  screenshots for the README.
- `readme/01-agent-widgets.png`: real tmuxes widgets attached to Claude Code
  and Codex CLI sessions.
- `readme/02-htop-widget.png`: full-screen tmuxes widget attached to a real
  htop tmux session.
- `readme/03-forwarding-effect.png`: Android browser loading a private preview
  through a tmuxes SSH local forward.
- `readme/04-parent-child-servers.png`: server list showing parent-child
  ProxyJump topology and drag-to-reparent handles.
- `readme/04-home-widget.png`: Pixel Launcher home screen with a
  semi-transparent tmuxes widget rendering CLI output.
- `readme/tmuxes-widget-layouts.png`: horizontally stitched launcher widget
  layouts showing full-screen, app-icon coexistence, stacked, and dense
  mixed-size semi-transparent widgets.
- `readme/widgets-01-fullscreen.png`: full home-screen command-center widget
  layout.
- `readme/widgets-02-coexist.png`: widget layout coexisting with ordinary
  launcher app icons.
- `readme/widgets-03-stacked.png`: two vertically stacked widget layout.
- `readme/widgets-04-board.png`: dense board with several different-size widget
  regions.

Rules for media added here:

- Do not show private hostnames, usernames, IP addresses, passwords, keys, or
  production terminal output.
- Screenshots and videos should come from current app builds.
- Demo terminal output must stay sanitized and must not expose account
  identifiers, tokens, or production data.
- Authentication material must not appear in public screenshots.
- Illustrations must be clearly distinguishable from screenshots.
- Prefer demo data that communicates the tmux-first workflow: terminal-native
  coding tools, working SSH forwarding, and CLI-rendered widgets.
- Keep editable source files readable and optimize raster images before
  committing.

Useful future assets:

- GitHub social preview image.
- Current app screenshot or short demo clip.
- Widget preview screenshot.
- Terminal session screenshot with harmless demo output.

# Media Assets

This directory contains public-facing images for the repository, release pages,
and project documentation.

Tracked assets:

- `readme/tmuxes-readme-screens.png`: horizontally stitched emulator
  screenshots for the README.
- `readme/01-servers.png`: configured SSH server list.
- `readme/04-terminal-htop.png`: terminal view attached to the `htop` demo
  tmux session.
- `readme/05-yaml-editor.png`: widget YAML configuration in the in-app editor.

Rules for media added here:

- Do not show private hostnames, usernames, IP addresses, passwords, keys, or
  production terminal output.
- Screenshots and videos should come from current app builds.
- Demo terminal output must stay synthetic or sanitized.
- Authentication material must not appear in public screenshots.
- Illustrations must be clearly distinguishable from screenshots.
- Prefer demo data that communicates the tmux-first workflow: servers,
  sessions, terminal, widgets, snippets, and YAML editing.
- Keep editable source files readable and optimize raster images before
  committing.

Useful future assets:

- GitHub social preview image.
- Current app screenshot or short demo clip.
- Widget preview screenshot.
- Terminal session screenshot with harmless demo output.
- YAML editor screenshot with fake demo configuration.

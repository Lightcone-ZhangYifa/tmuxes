# Changelog

All notable public changes should be recorded here.

This project follows a pragmatic changelog format inspired by Keep a
Changelog. Versioning will follow SemVer once public binary releases begin.

## [Unreleased]

### Changed

- Updated the Android build toolchain to Gradle 9.5.1, Android Gradle Plugin
  9.2.1, Kotlin 2.3.21, KSP 2.3.8, and Compose BOM 2026.05.01.
- Migrated the app module to AGP 9 built-in Kotlin support.

## [1.0.1] - 2026-05-26

### Added

- Chinese README content and refreshed public README presentation.
- Curated preset snippet libraries for Codex CLI, Claude Code, tmux, SSH, Git,
  Docker, Python, Slurm, APT, and Conda.

### Changed

- Default terminal and widget terminal color scheme is now Dracula.
- New widgets now use a 50% terminal background opacity while keeping overall
  widget foreground opacity at 100%.
- CodeQL now uses a clean uncached analysis build, and Dependabot groups Gradle
  wrapper updates with Android build-tool updates.

## [1.0.0] - 2026-05-19

### Added

- Open-source project documentation, contribution policy, security policy,
  support policy, license, notices, GitHub templates, CI, CodeQL, and
  Dependabot configuration.
- Public README, documentation index, emulator screenshot montage,
  media-asset guidance, and citation metadata for the GPL-3.0-only project
  license.

### Changed

- Release signing is now supplied from local or CI configuration instead of
  repository-tracked secrets.

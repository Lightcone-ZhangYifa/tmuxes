# FAQ

## Is tmuxes affiliated with tmux?

No. tmuxes is independent software. It uses tmux as the remote session layer
because tmux already solves durable terminal workspaces well.

## Why focus on tmux instead of a generic shell?

Mobile devices are transient: Activities recreate, networks change, and users
leave the app often. tmux gives remote work a durable owner that survives those
events. tmuxes builds Android-native controls around that model.

## Does tmuxes send telemetry?

No telemetry is currently designed into the app. See [PRIVACY.md](PRIVACY.md)
for the data-handling model.

## Where are SSH keys stored?

Imported or generated keys are stored in app-private Android storage. Users
should still treat device backups, lock-screen settings, and debug bundles as
security-sensitive.

## Can I build without the maintainer release key?

Yes. Debug builds work normally. Release builds work without signing inputs and
produce an unsigned release APK. Signed releases require local or CI-injected
signing values; see [RELEASING.md](RELEASING.md).

## Why are there strict design-rule checks?

The app has many screens and settings. The checks prevent slow drift into raw
colors, ad hoc typography, unmanaged visible text, direct logging, settings
string keys, and package-layer shortcuts.

## Why GPL-3.0-only?

tmuxes is built as community software for developers who rely on remote
workflows. The project license keeps derivative versions open under the same
terms while remaining compatible with the Android and Kotlin libraries used by
the app.

## Where should I start if I want to contribute?

Read [README.md](../README.md), [ARCHITECTURE.md](ARCHITECTURE.md), and
[DEVELOPMENT.md](DEVELOPMENT.md). Then pick a scoped issue or open a new issue
with the problem, evidence, and validation plan.

## How should I report a bug?

Use the bug template and include the smallest reproduction steps. Remove
private keys, passwords, tokens, hostnames, IPs, and production command output
before posting logs.

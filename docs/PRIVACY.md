# Privacy

This document describes the intended data-handling model for tmuxes.

## Data The App Stores Locally

tmuxes may store:

- server profiles
- SSH usernames and host information
- imported or generated private keys
- known-host fingerprints
- app preferences and terminal settings
- snippet libraries and YAML configuration
- recent debug and crash logs

Data is stored on the user's Android device using app-private storage, Room,
DataStore, and app-private files.

## Network Activity

tmuxes connects to SSH servers configured by the user. The app is not designed
to send telemetry, analytics, crash reports, server lists, private keys, or
terminal contents to a tmuxes-operated service.

Third-party libraries may perform normal protocol operations required by SSH,
Android, or dependency functionality.

## Debug Logs And Crash Bundles

Debug logs and crash bundles are for local diagnosis and user-directed support.
They may include hostnames, usernames, commands, terminal state, stack traces,
or configuration context depending on the failure.

Before sharing logs publicly, remove:

- hostnames and IP addresses
- usernames
- passwords and tokens
- private keys and public keys if sensitive
- customer, employer, or production data
- command output that reveals private information

## Backups

Android backup behavior can affect app-private data depending on platform and
device policy. Users who store sensitive SSH material should understand their
device backup settings and lock-screen protections.

## Security Reports

Report vulnerabilities through [SECURITY.md](../SECURITY.md), not public
issues.

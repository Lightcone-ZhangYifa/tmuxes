# Security Policy

tmuxes handles SSH connections, private keys, host fingerprints, local
configuration, and debug logs. Treat security reports as sensitive.

## Supported Versions

The project is pre-1.0 and under active development. Security fixes target the
main branch first. Release support windows will be documented once public
binary releases begin.

## Reporting A Vulnerability

Please do not open a public issue for a sensitive vulnerability.

Use GitHub private vulnerability reporting:

https://github.com/Lightcone-ZhangYifa/tmuxes/security/advisories/new

If private advisories are unavailable, contact the maintainers through the
repository owner's GitHub profile and include only the minimum non-sensitive
summary needed to establish contact.

## What To Include

- Affected commit, tag, or APK version.
- Android version and device or emulator details.
- Reproduction steps.
- Impact and attacker requirements.
- Relevant logs with private keys, passwords, tokens, hostnames, and IPs
  removed.

## Scope

In scope:

- SSH credential handling.
- Host-key trust decisions.
- Private-key import, export, storage, and passphrase handling.
- Debug bundle leakage.
- Intent, FileProvider, widget, Quick Settings tile, and foreground-service
  abuse.
- Build, dependency, and release-signing supply-chain issues.

Out of scope:

- Vulnerabilities in servers you connect to.
- Reports that require access to a user's private SSH keys without an app bug.
- Social engineering against maintainers or users.

## Maintainer Handling

Security fixes should avoid public exploit details until a fix is available.
When needed, publish a GitHub security advisory and credit reporters who want
credit.

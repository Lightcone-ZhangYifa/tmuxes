# Third-Party Notices

tmuxes itself is licensed under the GNU General Public License v3.0 only
(`GPL-3.0-only`). Third-party software and assets retain their own licenses.
The table below uses standard license names and SPDX identifiers where an SPDX
identifier exists.

| Component | Standard license name | SPDX identifier | Scope |
| --- | --- | --- | --- |
| AndroidX, Jetpack Compose, Material 3, Material Icons, Room, DataStore, Navigation, Lifecycle, Activity, Core, SavedState, Startup, ProfileInstaller | Apache License 2.0 | `Apache-2.0` | Runtime Android framework and UI libraries |
| Kotlin standard library, kotlinx.coroutines, kotlinx.serialization | Apache License 2.0 | `Apache-2.0` | Runtime language and async libraries |
| SSHJ | Apache License 2.0 | `Apache-2.0` | SSH protocol implementation |
| ASN.1 parser used by SSHJ (`com.hierynomus:asn-one`) | Apache License 2.0 | `Apache-2.0` | SSHJ transitive dependency |
| Bouncy Castle | Bouncy Castle Licence | `LicenseRef-Bouncy-Castle` | Cryptography and key handling |
| SnakeYAML and SnakeYAML Engine | Apache License 2.0 | `Apache-2.0` | YAML parsing |
| Sora Editor | GNU Lesser General Public License v2.1 only | `LGPL-2.1-only` | In-app YAML editor |
| Gson | Apache License 2.0 | `Apache-2.0` | Transitive data serialization dependency |
| Okio | Apache License 2.0 | `Apache-2.0` | Transitive I/O dependency |
| Guava ListenableFuture | Apache License 2.0 | `Apache-2.0` | AndroidX transitive dependency |
| JSpecify | Apache License 2.0 | `Apache-2.0` | Static nullness annotations |
| JetBrains annotations | Apache License 2.0 | `Apache-2.0` | Static annotations |
| SLF4J API | MIT License | `MIT` | Logging API used transitively |
| JRuby JCodings and Joni | MIT License | `MIT` | TextMate grammar runtime transitive dependencies |
| Eclipse JDT Annotation | Eclipse Public License 2.0 | `EPL-2.0` | Static annotations used transitively |
| JetBrains Mono | SIL Open Font License 1.1 | `OFL-1.1` | Bundled monospace font |
| VS Code YAML TextMate grammar | MIT License | `MIT` | Bundled YAML syntax grammar asset |
| Catppuccin TextMate themes | MIT License | `MIT` | Bundled editor theme assets |
| JUnit | Eclipse Public License 1.0 | `EPL-1.0` | JVM tests |
| AndroidX Test, Espresso, Compose UI test | Apache License 2.0 | `Apache-2.0` | Android instrumentation and UI tests |
| tmux | ISC License | `ISC` | External project interoperated with by tmuxes; not bundled |

## Sora Editor Note

Sora Editor is licensed under the GNU Lesser General Public License v2.1 only.
Downstream distributors should review their obligations when redistributing APKs
that include Sora Editor binaries.

## Bouncy Castle Note

Bouncy Castle publishes its license under the name "Bouncy Castle Licence" in
Maven metadata. SPDX does not publish a dedicated short identifier for it, so
this notice uses the local identifier `LicenseRef-Bouncy-Castle`.

## Asset Notes

Bundled fonts, grammar files, icons, and generated Android resources may carry
licenses separate from the tmuxes source code license. Keep upstream license
text and attribution when adding new bundled assets.

# Development

This guide is for contributors building tmuxes locally.

## Requirements

- JDK 17
- Android SDK with API 36 installed
- Android platform-tools on `PATH`
- A device or emulator for UI, widget, SSH, lifecycle, and instrumentation
  validation

Useful environment variables:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
```

## Main Commands

Fast local validation:

```bash
./gradlew compileDebugKotlin testDebugUnitTest checkDesignRules lintDebug
```

Debug APK:

```bash
./gradlew assembleDebug
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.tmuxes.debug/com.tmuxes.MainActivity
```

Full Gradle check:

```bash
./gradlew check
```

Instrumented tests require a running device or emulator:

```bash
./gradlew connectedDebugAndroidTest
```

## Validation Expectations

Use JVM unit tests for pure logic:

- terminal buffer and emulator algorithms
- YAML serialization and diagnostics
- SSH config parsing and shell escaping
- settings and design-token derivation

Use emulator validation for behavior involving:

- Compose UI
- Android lifecycle
- foreground service behavior
- widgets and Quick Settings tile
- terminal view input and rendering
- SSH side effects

Prefer `uiautomator` XML, focused `logcat`, and file/state inspection over
visual screenshot comparison for repeatable validation.

## Debug Package

Debug builds use:

- package: `com.tmuxes.debug`
- activity: `com.tmuxes.MainActivity`

Crash and app logs can be inspected with:

```bash
adb logcat -d -s tmuxes.SSH:V tmuxes.TERMINAL:V tmuxes.TMUX:V tmuxes.UI:V tmuxes.DB:V AndroidRuntime:E
adb shell pidof com.tmuxes.debug
```

## Design Rules

Run all project-specific gates:

```bash
./gradlew checkDesignRules
```

Individual tasks are defined in [app/build.gradle.kts](../app/build.gradle.kts)
and implemented by scripts in [gradle/scripts](../gradle/scripts).

## Release Signing

Release signing is optional for normal development. Without signing inputs,
`assembleRelease` creates an unsigned release artifact. To create a signed APK,
configure the values documented in [RELEASING.md](RELEASING.md).

Never commit keystores or signing passwords.

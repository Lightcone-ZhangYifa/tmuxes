# Releasing

Release signing material is not stored in this repository. Public contributors
should be able to build, test, and inspect the project without receiving
maintainer private keys.

## Versioning

Version values live in [app/build.gradle.kts](../app/build.gradle.kts):

```kotlin
versionCode = 1
versionName = "1.0.0"
```

Before a public release:

1. Update `versionCode` and `versionName`.
2. Update [CHANGELOG.md](../CHANGELOG.md).
3. Run the validation checklist below.
4. Create a signed release APK or app bundle from a clean tree.
5. Create a GitHub release with generated notes reviewed by a maintainer.

## Signing Inputs

The Android Gradle build reads these values from Gradle properties,
environment variables, `TMUXES_`-prefixed environment variables, or the ignored
root `local.properties` file:

```properties
RELEASE_STORE_FILE=/absolute/path/to/tmuxes-release.jks
RELEASE_STORE_PASSWORD=change-me
RELEASE_KEY_ALIAS=tmuxes
RELEASE_KEY_PASSWORD=change-me
```

`RELEASE_STORE_FILE` may be absolute or relative to the `app/` module.

Example local command:

```bash
RELEASE_STORE_FILE=/secure/path/tmuxes-release.jks \
RELEASE_STORE_PASSWORD=... \
RELEASE_KEY_ALIAS=tmuxes \
RELEASE_KEY_PASSWORD=... \
./gradlew clean assembleRelease
```

Do not put real values in committed files. Use repository or environment
secrets in CI.

## Validation Checklist

Run from a clean checkout:

```bash
./gradlew clean compileDebugKotlin testDebugUnitTest checkDesignRules lintDebug
./gradlew assembleDebug
./gradlew clean assembleRelease
```

For changes touching UI, terminal, widgets, SSH, services, boot behavior, or
Android lifecycle, also validate on an emulator and inspect focused logs:

```bash
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb logcat -c
adb shell am start -n com.tmuxes.debug/com.tmuxes.MainActivity
adb shell uiautomator dump /sdcard/ui.xml
adb shell cat /sdcard/ui.xml
adb logcat -d -s tmuxes.SSH:V tmuxes.TERMINAL:V tmuxes.TMUX:V tmuxes.UI:V tmuxes.DB:V AndroidRuntime:E
```

## Release Artifacts

Expected APK paths:

- debug: `app/build/outputs/apk/debug/app-debug.apk`
- signed release: `app/build/outputs/apk/release/app-release.apk`
- unsigned release: `app/build/outputs/apk/release/app-release-unsigned.apk`

Generated APKs, bundles, idsig files, keystores, and passwords must remain out
of version control.

# Support

Use the right channel so issues stay actionable.

- Bugs: open a bug report with reproduction steps and sanitized logs.
- Feature ideas: open a feature request that describes the workflow need.
- Questions: use GitHub Discussions once enabled for the repository.
- Security issues: follow [SECURITY.md](SECURITY.md), not public issues.

Before posting logs or YAML, remove:

- private keys
- passwords and tokens
- hostnames and IP addresses
- usernames
- production command output
- customer or employer data

For local diagnosis, start with:

```bash
./gradlew compileDebugKotlin testDebugUnitTest checkDesignRules lintDebug
adb logcat -d -s tmuxes.SSH:V tmuxes.TERMINAL:V tmuxes.TMUX:V tmuxes.UI:V tmuxes.DB:V AndroidRuntime:E
```

package com.tmuxes.data.settings

import androidx.compose.runtime.Composable

/**
 * One row in a [SettingScreenSpec]. Either a registered [Setting] (rendered
 * via the generic SettingItemRenderer) or a custom Composable for things
 * that don't fit the pattern (color scheme picker, font preview, etc.).
 */
sealed interface SettingItem {
    /** Standard registered setting — renderer picks the right control. */
    data class Reg(val setting: Setting<*>) : SettingItem

    /**
     * Bespoke Composable embedded in the same card slot. Use sparingly,
     * only when the setting needs a control that doesn't generalise
     * (e.g. ANSI palette preview, color-scheme dropdown with thumbnails).
     */
    class Custom(val render: @Composable () -> Unit) : SettingItem
}

data class SettingGroup(
    val title: String,
    val items: List<SettingItem>
)

data class SettingScreenSpec(
    val id: String,
    val title: String,
    val groups: List<SettingGroup>
)

/**
 * Static catalog of every settings screen. Each screen is a list of groups,
 * each group is a list of registered settings (or rare custom widgets).
 *
 * Adding a setting = adding one declaration in [Settings] AND one line in
 * the relevant group below. UI rendering, YAML schema, completion and
 * error checking all derive from those two declarations automatically.
 */
object SettingScreens {

    object TerminalCatalog {
        val typography = SettingGroup("Typography", listOf(
            SettingItem.Reg(Settings.terminalFontFamily),
            SettingItem.Reg(Settings.terminalFontSize),
            SettingItem.Reg(Settings.terminalFontWeight),
            SettingItem.Reg(Settings.terminalLineSpacing)
        ))

        val color = SettingGroup("Color", listOf(
            SettingItem.Reg(Settings.terminalColorScheme),
            SettingItem.Reg(Settings.terminalCustomSchemes)
        ))

        val cursor = SettingGroup("Cursor", listOf(
            SettingItem.Reg(Settings.terminalCursorStyle),
            SettingItem.Reg(Settings.terminalCursorBlink),
            SettingItem.Reg(Settings.terminalCursorBlinkSpeed),
            SettingItem.Reg(Settings.terminalCursorColor)
        ))

        val rendering = SettingGroup("Rendering", listOf(
            SettingItem.Reg(Settings.terminalSelectionColor),
            SettingItem.Reg(Settings.terminalBackgroundOpacity),
            SettingItem.Reg(Settings.terminalBoldIsBright),
            SettingItem.Reg(Settings.terminalUnderlineStyle),
            SettingItem.Reg(Settings.terminalPadding)
        ))

        val keyBar = SettingGroup("Extra Keys", listOf(
            SettingItem.Reg(Settings.extraKeysEnabled),
            SettingItem.Reg(Settings.terminalExtraKeysHeight)
        ))

        val hardwareKeys = SettingGroup("Hardware Keys", listOf(
            SettingItem.Reg(Settings.volumeKeysAction)
        ))

        val scrolling = SettingGroup("Scrolling & History", listOf(
            SettingItem.Reg(Settings.terminalScrollbackLines),
            SettingItem.Reg(Settings.autoScrollOutput),
            SettingItem.Reg(Settings.scrollOnKeystroke)
        ))

        val gestures = SettingGroup("Gestures", listOf(
            SettingItem.Reg(Settings.doubleTapWordSelect),
            SettingItem.Reg(Settings.verticalSwipeMode),
            SettingItem.Reg(Settings.swipeLinesPerArrow),
            SettingItem.Reg(Settings.terminalPinchZoomEnabled)
        ))

        val tmux = SettingGroup("Tmux", listOf(
            SettingItem.Reg(Settings.tmuxPrefixKey)
        ))

        val appearanceGroups = listOf(typography, color, cursor, rendering)
        val inputGroups = listOf(keyBar, hardwareKeys, scrolling, gestures, tmux)

    }

    object SshCatalog {
        val ptyEnvironment = SettingGroup("PTY & Environment", listOf(
            SettingItem.Reg(Settings.sshDefaultTerm),
            SettingItem.Reg(Settings.sshEnvVars)
        ))

        val timeouts = SettingGroup("Timeouts", listOf(
            SettingItem.Reg(Settings.sshConnectionTimeout),
            SettingItem.Reg(Settings.sshTransportTimeout),
            SettingItem.Reg(Settings.sshReadTimeout)
        ))

        val keepaliveRecovery = SettingGroup("Keepalive & Recovery", listOf(
            SettingItem.Reg(Settings.sshKeepaliveInterval),
            SettingItem.Reg(Settings.sshKeepaliveMaxCount),
            SettingItem.Reg(Settings.sshReconnectIntervalSeconds)
        ))

        val transport = SettingGroup("Transport", listOf(
            SettingItem.Reg(Settings.sshCompression)
        ))

        val hostSecurity = SettingGroup("Host Key Security", listOf(
            SettingItem.Reg(Settings.sshStrictHostKey)
        ))

        val algorithms = SettingGroup("Algorithms", listOf(
            SettingItem.Reg(Settings.sshPreferredCiphers),
            SettingItem.Reg(Settings.sshPreferredKex),
            SettingItem.Reg(Settings.sshPreferredMacs),
            SettingItem.Reg(Settings.sshPreferredHostKeyAlgs)
        ))

        val globalGroups = listOf(
            ptyEnvironment,
            timeouts,
            keepaliveRecovery,
            transport,
            hostSecurity,
            algorithms
        )

        val serverOverrideSettings = listOf(
            Settings.sshDefaultTerm,
            Settings.sshConnectionTimeout,
            Settings.sshTransportTimeout,
            Settings.sshReadTimeout,
            Settings.sshKeepaliveInterval,
            Settings.sshKeepaliveMaxCount,
            Settings.sshCompression,
            Settings.sshStrictHostKey,
            Settings.sshEnvVars,
            Settings.sshPreferredCiphers,
            Settings.sshPreferredKex,
            Settings.sshPreferredMacs,
            Settings.sshPreferredHostKeyAlgs
        )
    }

    val appAppearance = SettingScreenSpec(
        id = "app_appearance",
        title = "App Appearance",
        groups = listOf(
            SettingGroup("Language", listOf(
                SettingItem.Reg(Settings.appLanguage)
            )),
            SettingGroup("Theme", listOf(
                SettingItem.Reg(Settings.theme)
            )),
            SettingGroup("Color", listOf(
                SettingItem.Reg(Settings.appColorPalette),
                SettingItem.Reg(Settings.appAccentColor)
            )),
            SettingGroup("Layout & Sizing", listOf(
                SettingItem.Reg(Settings.appDensity),
                SettingItem.Reg(Settings.appTypeScale),
                SettingItem.Reg(Settings.appStatusBarStyle)
            )),
            SettingGroup("Floating UI", listOf(
                SettingItem.Reg(Settings.bubbleOpacity),
                SettingItem.Reg(Settings.fabOpacity)
            )),
            SettingGroup("Startup", listOf(
                SettingItem.Reg(Settings.startScreen)
            ))
        )
    )

    val terminalAppearance = SettingScreenSpec(
        id = "terminal_appearance",
        title = "Terminal Appearance",
        groups = TerminalCatalog.appearanceGroups
    )

    val terminalInput = SettingScreenSpec(
        id = "terminal_input",
        title = "Input & Interaction",
        groups = TerminalCatalog.inputGroups
    )

    val notifications = SettingScreenSpec(
        id = "notifications",
        title = "Notifications & Feedback",
        groups = listOf(
            SettingGroup("Bell", listOf(
                SettingItem.Reg(Settings.bellEnabled),
                SettingItem.Reg(Settings.vibrationEnabled),
                SettingItem.Reg(Settings.visualBell)
            )),
            SettingGroup("Screen", listOf(
                SettingItem.Reg(Settings.keepScreenOn)
            ))
        )
    )

    val sshConnection = SettingScreenSpec(
        id = "ssh_connection",
        title = "SSH Defaults",
        groups = SshCatalog.globalGroups
    )

    val shellSession = SettingScreenSpec(
        id = "shell_session",
        title = "Tmux & Session",
        groups = listOf(
            SettingGroup("Tmux", listOf(
                SettingItem.Reg(Settings.tmuxPrefixKey)
            ))
        )
    )

    val editor = SettingScreenSpec(
        id = "editor",
        title = "Editor",
        groups = listOf(
            SettingGroup("Display", listOf(
                SettingItem.Reg(Settings.editorFontFamily),
                SettingItem.Reg(Settings.editorFontSize),
                SettingItem.Reg(Settings.editorLineHeight),
                SettingItem.Reg(Settings.editorTheme),
                SettingItem.Reg(Settings.editorShowLineNumbers),
                SettingItem.Reg(Settings.editorCurrentLineHighlight),
                SettingItem.Reg(Settings.editorIndentGuides)
            )),
            SettingGroup("Editing", listOf(
                SettingItem.Reg(Settings.editorTabWidth),
                SettingItem.Reg(Settings.editorAutoIndent),
                SettingItem.Reg(Settings.editorAutoComplete),
                SettingItem.Reg(Settings.editorBracketPairing),
                SettingItem.Reg(Settings.editorWordWrap),
                SettingItem.Reg(Settings.editorPinchZoom),
                SettingItem.Reg(Settings.editorReadOnly)
            )),
            SettingGroup("Save", listOf(
                SettingItem.Reg(Settings.editorAutoSave),
                SettingItem.Reg(Settings.editorAutoSaveDelay)
            )),
            SettingGroup("Diagnostics", listOf(
                SettingItem.Reg(Settings.errorLensEnabled),
                SettingItem.Reg(Settings.errorLensLevel),
                SettingItem.Reg(Settings.errorLensPosition),
                SettingItem.Reg(Settings.errorLensFontSize),
                SettingItem.Reg(Settings.errorLensBgIntensity),
                SettingItem.Reg(Settings.errorLensTruncation),
                SettingItem.Reg(Settings.errorLensDebounce),
                SettingItem.Reg(Settings.errorLensHideWhileTyping)
            ))
        )
    )

    val all: List<SettingScreenSpec> = listOf(
        appAppearance, terminalAppearance, terminalInput, notifications,
        sshConnection, shellSession, editor
    )
}

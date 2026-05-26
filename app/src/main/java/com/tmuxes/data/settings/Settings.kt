package com.tmuxes.data.settings

import com.tmuxes.i18n.AppLanguage

/**
 * The single registry of every persisted setting in the app.
 *
 * Adding a setting is one declaration here, then adding the property to a
 * [SettingScreenSpec] group in [SettingScreens] (if it should appear in the
 * UI). Everything else — YAML schema, completion, error checking,
 * AppPreferences flow/setter, the ViewModel binding — is driven from this
 * file automatically.
 *
 * The [all] list is built incrementally as the object initialises: every
 * `register(...)` call inserts the setting into a private mutable list and
 * checks for duplicate `(fileType, flatPath)` pairs. There is no reflection
 * dependency.
 */
object Settings {

    private val _all = mutableListOf<Setting<*>>()
    val all: List<Setting<*>> get() = _all.toList()

    private fun <S : Setting<*>> register(setting: S): S {
        require(_all.none { it.key.fileType == setting.key.fileType && it.key.flatPath == setting.key.flatPath }) {
            "Duplicate Setting registration: ${setting.key.fileType} ${setting.key.flatPath}"
        }
        _all.add(setting)
        return setting
    }

    private fun gk(vararg parts: String) = SettingKey(parts.toList(), SettingFile.GLOBAL)

    // =========================================================================
    // Pre-defined enum option lists (reused across settings)
    // =========================================================================

    private val themeOptions = listOf(
        EnumOption("dark", "Dark"),
        EnumOption("light", "Light"),
        EnumOption("system", "Follow system")
    )

    private val languageOptions = AppLanguage.entries.map { language ->
        EnumOption(language.settingValue, language.displayName)
    }

    private val fontFamilyOptions = listOf(
        EnumOption("jetbrains_mono", "JetBrains Mono"),
        EnumOption("monospace", "Monospace (System)"),
        EnumOption("sans_serif", "Sans Serif"),
        EnumOption("serif", "Serif")
    )

    private val fontWeightOptions = listOf(
        EnumOption("thin", "Thin"),
        EnumOption("light", "Light"),
        EnumOption("normal", "Normal"),
        EnumOption("medium", "Medium"),
        EnumOption("bold", "Bold")
    )

    private val terminalColorSchemes = listOf(
        EnumOption("catppuccin", "Catppuccin Mocha"),
        EnumOption("monokai", "Monokai"),
        EnumOption("dracula", "Dracula"),
        EnumOption("nord", "Nord"),
        EnumOption("solarized", "Solarized Dark"),
        EnumOption("gruvbox", "Gruvbox Dark"),
        EnumOption("one_dark", "One Dark")
    )

    private val cursorStyleOptions = listOf(
        EnumOption("block", "Block"),
        EnumOption("underline", "Underline"),
        EnumOption("bar", "Bar")
    )

    private val underlineStyleOptions = listOf(
        EnumOption("solid", "Solid"),
        EnumOption("double", "Double"),
        EnumOption("dotted", "Dotted"),
        EnumOption("dashed", "Dashed")
    )

    private val terminalStatusBarBehaviorOptions = listOf(
        EnumOption("reserve", "Reserve safe area"),
        EnumOption("draw_behind", "Draw behind status bar")
    )

    private val statusBarStyleOptions = listOf(
        EnumOption("auto", "Auto (follow theme)"),
        EnumOption("surface", "Surface tinted"),
        EnumOption("transparent", "Transparent")
    )

    private val navigationStyleOptions = listOf(
        EnumOption("bottom_bar", "Bottom Bar"),
        EnumOption("rail", "Rail"),
        EnumOption("drawer", "Drawer")
    )

    private val startScreenOptions = listOf(
        EnumOption("last_session", "Last Session"),
        EnumOption("sessions", "Sessions List"),
        EnumOption("servers", "Server List")
    )

    private val colorPaletteOptions = listOf(
        EnumOption("default", "Default"),
        EnumOption("material_you", "Dynamic color"),
        EnumOption("custom", "Custom color")
    )

    private val densityOptions = listOf(
        EnumOption("compact", "Compact"),
        EnumOption("comfortable", "Comfortable"),
        EnumOption("spacious", "Spacious")
    )

    private val typeScaleOptions = listOf(
        EnumOption("small", "Small"),
        EnumOption("default", "Default"),
        EnumOption("large", "Large")
    )

    private val cornerStyleOptions = listOf(
        EnumOption("sharp", "Sharp"),
        EnumOption("rounded", "Rounded"),
        EnumOption("pill", "Pill")
    )

    private val strictHostKeyOptions = listOf(
        EnumOption("accept-new", "Accept new (default)"),
        EnumOption("yes", "Strict (reject unknown)"),
        EnumOption("no", "Disabled (insecure)")
    )

    private val volumeKeysOptions = listOf(
        EnumOption("volume", "Adjust system volume (default)"),
        EnumOption("esc_tab", "Vol-Down = Esc, Vol-Up = Tab"),
        EnumOption("disabled", "Disabled (swallow keys)")
    )

    private val editorThemeOptions = listOf(
        EnumOption("follow_app", "Follow app"),
        EnumOption("catppuccin_mocha", "Catppuccin Mocha")
    )

    private val errorLensLevelOptions = listOf(
        EnumOption("error_only", "Errors only"),
        EnumOption("error_warning", "Errors + warnings"),
        EnumOption("all", "All diagnostics")
    )

    private val errorLensPositionOptions = listOf(
        EnumOption("end_of_line", "End of line"),
        EnumOption("inline", "Inline"),
        EnumOption("hover", "Hover only")
    )

    private val errorLensFontSizeOptions = listOf(
        EnumOption("follow_editor", "Follow editor"),
        EnumOption("smaller", "Smaller"),
        EnumOption("larger", "Larger")
    )

    private val tabWidthOptions = listOf(
        EnumOption(2, "2"),
        EnumOption(4, "4"),
        EnumOption(8, "8")
    )

    // =========================================================================
    // app.* — app-level appearance / start screen / runtime state
    // =========================================================================

    val theme = register(StringEnumSetting(
        key = gk("app", "theme"),
        default = "dark",
        options = themeOptions,
        ui = SettingUi(title = "Theme", iconId = "dark_mode")
    ))

    val appLanguage = register(StringEnumSetting(
        key = gk("app", "language"),
        default = "system",
        options = languageOptions,
        ui = SettingUi(
            title = "Language",
            description = "Follow the device language unless a language is selected here",
            iconId = "translate"
        )
    ))

    val startScreen = register(StringEnumSetting(
        key = gk("app", "start_screen"),
        default = "last_session",
        options = startScreenOptions,
        ui = SettingUi(title = "Start Screen", iconId = "phone_android")
    ))

    val appColorPalette = register(StringEnumSetting(
        key = gk("app", "color_palette"),
        default = "default",
        options = colorPaletteOptions,
        ui = SettingUi(title = "Color Palette",
            description = "Source for app colors",
            iconId = "palette")
    ))

    val appAccentColor = register(ColorSetting(
        key = gk("app", "accent_color"),
        default = 0xFF8AB4F8.toInt(),
        ui = SettingUi(title = "Theme Color",
            description = "Custom palette color family",
            iconId = "color_lens")
    ))

    val appDensity = register(StringEnumSetting(
        key = gk("app", "density"),
        default = "comfortable",
        options = densityOptions,
        ui = SettingUi(title = "Density",
            description = "How tightly elements pack on every screen",
            iconId = "view_compact")
    ))

    val appTypeScale = register(StringEnumSetting(
        key = gk("app", "type_scale"),
        default = "default",
        options = typeScaleOptions,
        ui = SettingUi(title = "Type Scale",
            description = "Overall text size (does not affect terminal)",
            iconId = "format_size")
    ))

    val appCornerStyle = register(StringEnumSetting(
        key = gk("app", "corner_style"),
        default = "rounded",
        options = cornerStyleOptions
    ))

    val appStatusBarStyle = register(StringEnumSetting(
        key = gk("app", "status_bar_style"),
        default = "auto",
        options = statusBarStyleOptions,
        ui = SettingUi(title = "Status Bar Style", iconId = "settings_brightness")
    ))

    val appNavigationStyle = register(StringEnumSetting(
        key = gk("app", "navigation_style"),
        default = "bottom_bar",
        options = navigationStyleOptions
    ))

    val bubbleOpacity = register(IntSetting(
        key = gk("app", "bubble_opacity"),
        default = 75,
        range = 30..100,
        ui = SettingUi(
            title = "Bubble Opacity",
            description = "Translucency of floating panels (command panel, find / output / problems)",
            unit = "%",
            iconId = "opacity"
        )
    ))

    val fabOpacity = register(IntSetting(
        key = gk("app", "fab_opacity"),
        default = 70,
        range = 50..95,
        ui = SettingUi(
            title = "FAB Opacity",
            description = "Translucency of floating action buttons",
            unit = "%",
            iconId = "opacity"
        )
    ))

    // Runtime state (no UI)
    val lastSessionServerId = register(LongSetting(
        key = gk("app", "last_session_server_id"),
        default = 0L
    ))
    val lastSessionName = register(StringSetting(
        key = gk("app", "last_session_name"),
        default = ""
    ))
    val favoriteSessions = register(StringSetSetting(
        key = gk("app", "favorite_sessions"),
        default = emptySet()
    ))
    val sessionColors = register(StringSetSetting(
        key = gk("app", "session_colors"),
        default = emptySet()
    ))

    // =========================================================================
    // behavior.* — interaction behavior toggles
    // =========================================================================

    val extraKeysEnabled = register(BooleanSetting(
        key = gk("behavior", "extra_keys_enabled"),
        default = true,
        ui = SettingUi(title = "Extra Keys",
            description = "Show extra key row above the keyboard",
            iconId = "keyboard")
    ))

    val bellEnabled = register(BooleanSetting(
        key = gk("behavior", "bell_enabled"),
        default = true,
        ui = SettingUi(title = "Audio Bell",
            description = "Play a tone on terminal bell",
            iconId = "notifications_active")
    ))

    val vibrationEnabled = register(BooleanSetting(
        key = gk("behavior", "vibration_enabled"),
        default = true,
        ui = SettingUi(title = "Vibration",
            description = "Vibrate on terminal bell",
            iconId = "vibration")
    ))

    val keepScreenOn = register(BooleanSetting(
        key = gk("behavior", "keep_screen_on"),
        default = true,
        ui = SettingUi(title = "Keep Screen On",
            description = "Don't dim the screen while a session is open",
            iconId = "lightbulb")
    ))

    val visualBell = register(BooleanSetting(
        key = gk("behavior", "visual_bell"),
        default = false,
        ui = SettingUi(title = "Visual Bell",
            description = "Flash the screen on terminal bell",
            iconId = "flash_on")
    ))

    val autoScrollOutput = register(BooleanSetting(
        key = gk("behavior", "auto_scroll_output"),
        default = true,
        ui = SettingUi(title = "Auto-scroll on Output",
            description = "Scroll to bottom when new output arrives",
            iconId = "keyboard_double_arrow_down")
    ))

    val scrollOnKeystroke = register(BooleanSetting(
        key = gk("behavior", "scroll_on_keystroke"),
        default = true,
        ui = SettingUi(title = "Scroll on Keystroke",
            description = "Jump to bottom when a key is pressed",
            iconId = "keyboard_arrow_down")
    ))

    val doubleTapWordSelect = register(BooleanSetting(
        key = gk("behavior", "double_tap_word_select"),
        default = true,
        ui = SettingUi(title = "Double-tap Word Select",
            description = "Select a word by double-tapping on it",
            iconId = "touch_app")
    ))

    val verticalSwipeMode = register(StringEnumSetting(
        key = gk("behavior", "vertical_swipe_mode"),
        default = "auto",
        options = listOf(
            EnumOption("auto", "Auto (arrows in copy-mode, scroll otherwise)"),
            EnumOption("scroll", "Always scroll local history"),
            EnumOption("arrow_keys", "Always send arrow keys")
        ),
        ui = SettingUi(
            title = "Vertical Swipe",
            description = "What 1-finger up/down drag does. Auto follows the Edit-mode FAB state.",
            iconId = "swipe_vertical"
        )
    ))

    val swipeLinesPerArrow = register(IntSetting(
        key = gk("behavior", "swipe_lines_per_arrow"),
        default = 1,
        range = 1..5,
        ui = SettingUi(
            title = "Swipe Sensitivity",
            description = "Arrow keys per cell of swipe (when Vertical Swipe = arrow keys)",
            unit = "×",
            iconId = "speed"
        )
    ))

    val terminalPinchZoomEnabled = register(BooleanSetting(
        key = gk("behavior", "terminal_pinch_zoom"),
        default = true,
        ui = SettingUi(
            title = "Pinch to Zoom",
            description = "Two-finger pinch on the terminal grid changes font size",
            iconId = "zoom_in"
        )
    ))

    // =========================================================================
    // ssh.* — SSH connection defaults
    // =========================================================================

    val sshDefaultTerm = register(StringSetting(
        key = gk("ssh", "default_term"),
        default = "xterm-256color",
        ui = SettingUi(title = "Default TERM Type",
            description = "Terminal type reported to the remote host",
            iconId = "terminal")
    ))

    val sshConnectionTimeout = register(IntSetting(
        key = gk("ssh", "connection_timeout"),
        default = 30,
        range = 5..120,
        ui = SettingUi(title = "Connection Timeout", unit = "s", iconId = "timer")
    ))

    val sshTransportTimeout = register(IntSetting(
        key = gk("ssh", "transport_timeout"),
        default = 120,
        range = 30..600,
        ui = SettingUi(title = "Transport Timeout", unit = "s", iconId = "schedule")
    ))

    val sshReadTimeout = register(IntSetting(
        key = gk("ssh", "read_timeout"),
        default = 0,
        range = 0..600,
        ui = SettingUi(title = "Read Timeout", unit = "s", iconId = "schedule",
            description = "0 = no timeout (recommended for interactive shells)")
    ))

    val sshKeepaliveInterval = register(IntSetting(
        key = gk("ssh", "keepalive_interval"),
        default = 60,
        range = 0..300,
        ui = SettingUi(title = "Keepalive Interval", unit = "s", iconId = "favorite",
            description = "0 to disable")
    ))

    val sshKeepaliveMaxCount = register(IntSetting(
        key = gk("ssh", "keepalive_max_count"),
        default = 3,
        range = 1..30,
        ui = SettingUi(title = "Keepalive Max Misses", iconId = "warning")
    ))

    val sshCompression = register(BooleanSetting(
        key = gk("ssh", "compression"),
        default = false,
        ui = SettingUi(title = "Compression",
            description = "Enable zlib compression on the SSH transport",
            iconId = "compress")
    ))

    val sshStrictHostKey = register(StringEnumSetting(
        key = gk("ssh", "strict_host_key"),
        default = "accept-new",
        options = strictHostKeyOptions,
        ui = SettingUi(title = "Host Key Verification", iconId = "lock")
    ))

    val sshEnvVars = register(StringSetting(
        key = gk("ssh", "env_vars"),
        default = "",
        ui = SettingUi(title = "Environment Variables",
            description = "One KEY=VALUE per line, sent to the remote host",
            placeholder = "LANG=en_US.UTF-8\nEDITOR=vim",
            iconId = "tune")
    ))

    val sshPreferredCiphers = register(StringSetting(
        key = gk("ssh", "preferred_ciphers"),
        default = "",
        ui = SettingUi(title = "Preferred Ciphers",
            description = "Comma-separated, leave empty to use library defaults",
            iconId = "vpn_lock")
    ))

    val sshPreferredKex = register(StringSetting(
        key = gk("ssh", "preferred_kex"),
        default = "",
        ui = SettingUi(title = "Preferred Key Exchange",
            description = "Comma-separated, leave empty to use library defaults",
            iconId = "key")
    ))

    val sshPreferredMacs = register(StringSetting(
        key = gk("ssh", "preferred_macs"),
        default = "",
        ui = SettingUi(title = "Preferred MACs",
            description = "Comma-separated, leave empty to use library defaults",
            iconId = "verified_user")
    ))

    val sshPreferredHostKeyAlgs = register(StringSetting(
        key = gk("ssh", "preferred_host_key_algs"),
        default = "",
        ui = SettingUi(title = "Preferred Host-Key Algorithms",
            description = "Comma-separated, leave empty to use library defaults",
            iconId = "fingerprint")
    ))

    val sshReconnectIntervalSeconds = register(IntSetting(
        key = gk("ssh", "reconnect_interval_seconds"),
        default = 60,
        range = 10..600,
        ui = SettingUi(title = "Reconnect Speed", unit = "s", iconId = "visibility",
            description = "Backoff cap for automatic reconnect attempts")
    ))

    // =========================================================================
    // terminal.* — terminal display + behavior
    // =========================================================================

    val terminalFontFamily = register(StringEnumSetting(
        key = gk("terminal", "font_family"),
        default = "jetbrains_mono",
        options = fontFamilyOptions,
        ui = SettingUi(title = "Font Family", iconId = "text_format")
    ))

    val terminalFontSize = register(IntSetting(
        key = gk("terminal", "font_size"),
        default = 14,
        range = 6..36,
        ui = SettingUi(title = "Font Size", unit = "sp", iconId = "format_size")
    ))

    val terminalFontWeight = register(StringEnumSetting(
        key = gk("terminal", "font_weight"),
        default = "normal",
        options = fontWeightOptions,
        ui = SettingUi(title = "Font Weight", iconId = "format_bold")
    ))

    val terminalColorScheme = register(StringEnumSetting(
        key = gk("terminal", "color_scheme"),
        default = "dracula",
        options = terminalColorSchemes,
        ui = SettingUi(title = "Color Scheme", iconId = "color_lens")
    ))

    val terminalCursorStyle = register(StringEnumSetting(
        key = gk("terminal", "cursor_style"),
        default = "block",
        options = cursorStyleOptions,
        ui = SettingUi(title = "Cursor Style", iconId = "edit")
    ))

    val terminalCursorBlink = register(BooleanSetting(
        key = gk("terminal", "cursor_blink"),
        default = true,
        ui = SettingUi(title = "Cursor Blink",
            description = "Animate the cursor with a blink effect",
            iconId = "timer")
    ))

    val terminalCursorBlinkSpeed = register(IntSetting(
        key = gk("terminal", "cursor_blink_speed"),
        default = 530,
        range = 100..2000,
        ui = SettingUi(title = "Cursor Blink Speed", unit = "ms", iconId = "speed")
    ))

    val terminalCursorColor = register(ColorSetting(
        key = gk("terminal", "cursor_color"),
        default = 0,
        ui = SettingUi(title = "Cursor Color",
            description = "Default inherits from color scheme", iconId = "edit")
    ))

    val terminalSelectionColor = register(ColorSetting(
        key = gk("terminal", "selection_color"),
        default = 0x6033B5E5,
        ui = SettingUi(title = "Selection Color", iconId = "highlight")
    ))

    val terminalBackgroundOpacity = register(IntSetting(
        key = gk("terminal", "background_opacity"),
        default = 100,
        range = 0..100,
        ui = SettingUi(title = "Background Opacity", unit = "%", iconId = "opacity")
    ))

    val terminalSystemStatusBarVisible = register(BooleanSetting(
        key = gk("terminal", "status_bar_visible"),
        default = true,
        ui = SettingUi(
            title = "System Status Bar",
            description = "Show the Android status bar while the terminal is open",
            iconId = "visibility"
        )
    ))

    val terminalStatusBarBehavior = register(StringEnumSetting(
        key = gk("terminal", "status_bar_behavior"),
        default = "reserve",
        options = terminalStatusBarBehaviorOptions,
        ui = SettingUi(
            title = "Status Bar Area",
            description = "How the terminal uses the top status bar and camera cutout area",
            iconId = "phone_android"
        )
    ))

    val terminalBoldIsBright = register(BooleanSetting(
        key = gk("terminal", "bold_is_bright"),
        default = false,
        ui = SettingUi(title = "Bold is Bright",
            description = "Render bold text using bright ANSI colors",
            iconId = "format_bold")
    ))

    val terminalUnderlineStyle = register(StringEnumSetting(
        key = gk("terminal", "underline_style"),
        default = "solid",
        options = underlineStyleOptions,
        ui = SettingUi(title = "Underline Style", iconId = "format_underlined")
    ))

    val terminalLineSpacing = register(IntSetting(
        key = gk("terminal", "line_spacing"),
        default = 100,
        range = 100..150,
        ui = SettingUi(title = "Line Spacing", unit = "%", iconId = "format_line_spacing")
    ))

    val terminalPadding = register(IntSetting(
        key = gk("terminal", "padding"),
        default = 0,
        range = 0..24,
        ui = SettingUi(title = "Terminal Padding", unit = "dp", iconId = "padding")
    ))

    val terminalScrollbackLines = register(IntSetting(
        key = gk("terminal", "scrollback_lines"),
        default = 10000,
        range = 100..1_000_000,
        ui = SettingUi(title = "Scrollback Lines",
            description = "Maximum lines kept in scroll history",
            iconId = "terminal")
    ))

    val tmuxPrefixKey = register(StringEnumSetting(
        key = gk("terminal", "tmux_prefix_key"),
        default = "ctrl_b",
        options = listOf(
            EnumOption("ctrl_b", "Ctrl-B (tmux default)"),
            EnumOption("ctrl_a", "Ctrl-A (screen-style)"),
            EnumOption("ctrl_g", "Ctrl-G"),
            EnumOption("ctrl_space", "Ctrl-Space"),
            EnumOption("ctrl_x", "Ctrl-X")
        ),
        ui = SettingUi(
            title = "Tmux Prefix Key",
            description = "Used by Edit-mode FAB and detach action. Must match your tmux config's prefix.",
            iconId = "keyboard"
        )
    ))

    val terminalCustomSchemes = register(StringSetting(
        key = gk("terminal", "custom_schemes"),
        default = "",
        ui = SettingUi(title = "Custom Color Schemes",
            description = "Custom-defined color schemes (JSON)",
            iconId = "code")
    ))

    val terminalExtraKeysHeight = register(IntSetting(
        key = gk("terminal", "extra_keys_height"),
        default = 32,
        range = 24..64,
        ui = SettingUi(title = "Extra Keys Height", unit = "dp", iconId = "height",
            description = "Height of each extra key row")
    ))

    val volumeKeysAction = register(StringEnumSetting(
        key = gk("terminal", "volume_keys"),
        default = "volume",
        options = volumeKeysOptions,
        ui = SettingUi(title = "Volume Keys",
            description = "What hardware Volume Up / Down do in the terminal",
            iconId = "volume_up")
    ))

    // =========================================================================
    // editor.* — YAML / config editor preferences
    // =========================================================================

    val editorFontFamily = register(StringEnumSetting(
        key = gk("editor", "font_family"),
        default = "jetbrains_mono",
        options = fontFamilyOptions,
        ui = SettingUi(title = "Font Family", iconId = "text_format")
    ))

    val editorFontSize = register(IntSetting(
        key = gk("editor", "font_size"),
        default = 14,
        range = 8..24,
        ui = SettingUi(title = "Font Size", unit = "sp", iconId = "format_size")
    ))

    val editorLineHeight = register(IntSetting(
        key = gk("editor", "line_height"),
        default = 120,
        range = 100..200,
        ui = SettingUi(title = "Line Height", unit = "%", iconId = "format_line_spacing")
    ))

    val editorTheme = register(StringEnumSetting(
        key = gk("editor", "theme"),
        default = "follow_app",
        options = editorThemeOptions,
        ui = SettingUi(title = "Editor Theme", iconId = "color_lens")
    ))

    val editorShowLineNumbers = register(BooleanSetting(
        key = gk("editor", "show_line_numbers"),
        default = true,
        ui = SettingUi(title = "Show Line Numbers", iconId = "format_list_numbered")
    ))

    val editorCurrentLineHighlight = register(BooleanSetting(
        key = gk("editor", "current_line_highlight"),
        default = true,
        ui = SettingUi(title = "Current Line Highlight", iconId = "highlight")
    ))

    val editorIndentGuides = register(BooleanSetting(
        key = gk("editor", "indent_guides"),
        default = true,
        ui = SettingUi(title = "Indent Guides", iconId = "format_indent_increase")
    ))

    val editorTabWidth = register(IntEnumSetting(
        key = gk("editor", "tab_width"),
        default = 2,
        options = tabWidthOptions,
        ui = SettingUi(title = "Tab Width", iconId = "format_indent_increase")
    ))

    val editorAutoIndent = register(BooleanSetting(
        key = gk("editor", "auto_indent"),
        default = true,
        ui = SettingUi(title = "Auto Indent", iconId = "format_indent_increase")
    ))

    val editorAutoComplete = register(BooleanSetting(
        key = gk("editor", "auto_complete"),
        default = true,
        ui = SettingUi(title = "Auto Complete", iconId = "auto_fix_high")
    ))

    val editorBracketPairing = register(BooleanSetting(
        key = gk("editor", "bracket_pairing"),
        default = true,
        ui = SettingUi(title = "Bracket Pairing", iconId = "data_array")
    ))

    val editorWordWrap = register(BooleanSetting(
        key = gk("editor", "word_wrap"),
        default = true,
        ui = SettingUi(title = "Word Wrap", iconId = "wrap_text")
    ))

    val editorPinchZoom = register(BooleanSetting(
        key = gk("editor", "pinch_zoom"),
        default = true,
        ui = SettingUi(title = "Pinch Zoom", iconId = "zoom_in")
    ))

    val editorReadOnly = register(BooleanSetting(
        key = gk("editor", "read_only"),
        default = false,
        ui = SettingUi(title = "Read Only", iconId = "lock")
    ))

    val editorAutoSave = register(BooleanSetting(
        key = gk("editor", "auto_save"),
        default = false,
        ui = SettingUi(title = "Auto Save", iconId = "save")
    ))

    val editorAutoSaveDelay = register(IntSetting(
        key = gk("editor", "auto_save_delay"),
        default = 5,
        range = 1..30,
        ui = SettingUi(title = "Auto Save Delay", unit = "s", iconId = "timer")
    ))

    // editor.output_panel_enabled removed — bottom area is now a non-toggleable
    // FAB cluster (Problems / Output / Find bubbles, always available).

    // editor.errorlens.* — nested section, used to be invisible in YAML schema
    val errorLensEnabled = register(BooleanSetting(
        key = gk("editor", "errorlens", "enabled"),
        default = true,
        ui = SettingUi(title = "ErrorLens",
            description = "Highlight errors and warnings inline",
            iconId = "error_outline")
    ))

    val errorLensLevel = register(StringEnumSetting(
        key = gk("editor", "errorlens", "level"),
        default = "error_warning",
        options = errorLensLevelOptions,
        ui = SettingUi(title = "Level", iconId = "filter_alt")
    ))

    val errorLensPosition = register(StringEnumSetting(
        key = gk("editor", "errorlens", "position"),
        default = "end_of_line",
        options = errorLensPositionOptions,
        ui = SettingUi(title = "Position", iconId = "format_align_right")
    ))

    val errorLensBgIntensity = register(IntSetting(
        key = gk("editor", "errorlens", "bg_intensity"),
        default = 15,
        range = 0..100,
        ui = SettingUi(title = "Background Intensity", unit = "%", iconId = "opacity")
    ))

    val errorLensFontSize = register(StringEnumSetting(
        key = gk("editor", "errorlens", "font_size"),
        default = "follow_editor",
        options = errorLensFontSizeOptions,
        ui = SettingUi(title = "Font Size", iconId = "format_size")
    ))

    val errorLensTruncation = register(IntSetting(
        key = gk("editor", "errorlens", "truncation"),
        default = 80,
        range = 20..400,
        ui = SettingUi(title = "Truncation", unit = "chars", iconId = "short_text")
    ))

    val errorLensDebounce = register(IntSetting(
        key = gk("editor", "errorlens", "debounce"),
        default = 300,
        range = 0..2000,
        ui = SettingUi(title = "Debounce", unit = "ms", iconId = "timer")
    ))

    val errorLensHideWhileTyping = register(BooleanSetting(
        key = gk("editor", "errorlens", "hide_while_typing"),
        default = true,
        ui = SettingUi(title = "Hide While Typing", iconId = "visibility_off")
    ))

    // =========================================================================
    // Lookup helpers
    // =========================================================================

    fun byPath(flatPath: String, fileType: SettingFile = SettingFile.GLOBAL): Setting<*>? =
        _all.firstOrNull { it.key.fileType == fileType && it.key.flatPath == flatPath }

    init {
        // Confirm registry initialised cleanly. Without this line, a
        // duplicate-key throw in `register()` (which is fatal because it
        // happens during object class-init) leaves no breadcrumb in
        // logcat — the app just refuses to launch with no diagnostic.
        com.tmuxes.util.AppLogger.i(com.tmuxes.util.AppLogger.Category.CONFIG) {
            "settings.registry ← ${_all.size} entries (global=${_all.count { it.key.fileType == SettingFile.GLOBAL }})"
        }
    }
}


/**
 * Convert the [Settings.tmuxPrefixKey] enum value to the corresponding
 * Ctrl-byte. Used by both the Edit-mode FAB (sends `[prefix, '[']` to
 * enter copy-mode) and the detach action (sends `[prefix, 'd']`). The
 * single source of truth so users with a non-default tmux prefix only
 * change one Setting.
 */
fun tmuxPrefixByteFor(key: String): Byte = when (key) {
    "ctrl_a" -> 0x01
    "ctrl_b" -> 0x02
    "ctrl_g" -> 0x07
    "ctrl_space" -> 0x00
    "ctrl_x" -> 0x18
    else -> 0x02 // ctrl_b fallback
}

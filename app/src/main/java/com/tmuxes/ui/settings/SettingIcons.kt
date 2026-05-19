package com.tmuxes.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.automirrored.filled.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.ShortText
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DataArray
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatLineSpacing
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Padding
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.ViewCompact
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Maps the string `iconId` carried in [com.tmuxes.data.settings.SettingUi]
 * into a Compose [ImageVector]. Keeping this mapping out of the registry
 * means the registry stays Compose-free and unit-testable on pure JVM.
 *
 * Unknown ids fall through to [Icons.Filled.Settings] — never null — so a
 * typo in a registry declaration cannot crash the settings UI.
 */
object SettingIcons {

    private val map: Map<String, ImageVector> = mapOf(
        "auto_fix_high" to Icons.Filled.AutoFixHigh,
        "code" to Icons.Filled.Code,
        "color_lens" to Icons.Filled.ColorLens,
        "compress" to Icons.Filled.Compress,
        "dark_mode" to Icons.Filled.DarkMode,
        "data_array" to Icons.Filled.DataArray,
        "edit" to Icons.Filled.Edit,
        "error_outline" to Icons.Filled.ErrorOutline,
        "favorite" to Icons.Filled.Favorite,
        "filter_alt" to Icons.Filled.FilterAlt,
        "fingerprint" to Icons.Filled.Fingerprint,
        "flash_on" to Icons.Filled.FlashOn,
        "format_align_right" to Icons.AutoMirrored.Filled.FormatAlignRight,
        "format_bold" to Icons.Filled.FormatBold,
        "format_indent_increase" to Icons.AutoMirrored.Filled.FormatIndentIncrease,
        "format_line_spacing" to Icons.Filled.FormatLineSpacing,
        "format_list_numbered" to Icons.Filled.FormatListNumbered,
        "format_size" to Icons.Filled.FormatSize,
        "format_underlined" to Icons.Filled.FormatUnderlined,
        "height" to Icons.Filled.Height,
        "highlight" to Icons.Filled.Highlight,
        "key" to Icons.Filled.Key,
        "keyboard" to Icons.Filled.Keyboard,
        "keyboard_arrow_down" to Icons.Filled.KeyboardArrowDown,
        "keyboard_double_arrow_down" to Icons.Filled.KeyboardDoubleArrowDown,
        "lightbulb" to Icons.Filled.Lightbulb,
        "list_alt" to Icons.AutoMirrored.Filled.ListAlt,
        "lock" to Icons.Filled.Lock,
        "navigation" to Icons.Filled.Navigation,
        "notifications_active" to Icons.Filled.NotificationsActive,
        "opacity" to Icons.Filled.Opacity,
        "padding" to Icons.Filled.Padding,
        "palette" to Icons.Filled.Palette,
        "phone_android" to Icons.Filled.PhoneAndroid,
        "save" to Icons.Filled.Save,
        "schedule" to Icons.Filled.Schedule,
        "settings_brightness" to Icons.Filled.SettingsBrightness,
        "short_text" to Icons.AutoMirrored.Filled.ShortText,
        "speed" to Icons.Filled.Speed,
        "terminal" to Icons.Filled.Terminal,
        "text_format" to Icons.Filled.TextFormat,
        "timer" to Icons.Filled.Timer,
        "touch_app" to Icons.Filled.TouchApp,
        "translate" to Icons.Filled.Translate,
        "tune" to Icons.Filled.Tune,
        "verified_user" to Icons.Filled.VerifiedUser,
        "vibration" to Icons.Filled.Vibration,
        "view_compact" to Icons.Filled.ViewCompact,
        "visibility" to Icons.Filled.Visibility,
        "visibility_off" to Icons.Filled.VisibilityOff,
        "volume_up" to Icons.AutoMirrored.Filled.VolumeUp,
        "vpn_lock" to Icons.Filled.VpnLock,
        "warning" to Icons.Filled.Warning,
        "wrap_text" to Icons.AutoMirrored.Filled.WrapText,
        "zoom_in" to Icons.Filled.ZoomIn
    )

    operator fun get(id: String?): ImageVector =
        map[id] ?: Icons.Filled.Settings
}

package com.tmuxes.terminal.view

/**
 * Shared Ctrl/Alt/Shift/Meta modifier key application logic.
 * Used by both ExtraKeysBar (on-screen keys) and TerminalView (physical keyboard + IME).
 *
 * ## Single-byte data (printable characters)
 * - Ctrl: char & 0x1F (standard terminal control character)
 * - Alt/Meta: ESC prefix
 * - Shift: uppercase
 * - Special cases: Shift+Tab=\e[Z, Ctrl+Backspace=0x08, Ctrl+Space=0x00, Ctrl+digit
 * - Combinations: Shift applied first (uppercase), then Ctrl, then Alt/Meta prefix
 *
 * ## Multi-byte escape sequences (arrows, F-keys, Home/End, etc.)
 * Standard xterm modifier encoding: insert `;modifier` before the final byte.
 * Modifier codes: 1 + shift*1 + alt*2 + ctrl*4 + meta*8 → range 2..16
 *
 * CSI sequences `\e[...X` → `\e[...;modX`
 * SS3 sequences `\eOX` → `\e[1;modX`
 * Tilde sequences `\e[N~` → `\e[N;mod~`
 */
object ModifierKeys {

    /** Ctrl+digit mapping per xterm spec. */
    private val CTRL_DIGIT = mapOf(
        0x32 to 0x00, // Ctrl+2 → NUL
        0x33 to 0x1B, // Ctrl+3 → ESC
        0x34 to 0x1C, // Ctrl+4 → FS
        0x35 to 0x1D, // Ctrl+5 → GS
        0x36 to 0x1E, // Ctrl+6 → RS
        0x37 to 0x1F, // Ctrl+7 → US
        0x38 to 0x7F, // Ctrl+8 → DEL
    )

    fun apply(data: ByteArray, ctrl: Boolean, alt: Boolean, shift: Boolean, meta: Boolean = false): ByteArray {
        if (!ctrl && !alt && !shift && !meta) return data
        if (data.isEmpty()) return data

        // Multi-byte: escape sequences (CSI, SS3, tilde)
        if (data.size >= 3 && data[0] == 0x1B.toByte()) {
            return applyToEscapeSequence(data, ctrl, alt, shift, meta)
        }

        // Single-byte: printable characters and control keys
        if (data.size == 1) {
            return applyToChar(data[0], ctrl, alt, shift, meta)
        }

        // Multi-byte non-escape (e.g., UTF-8): apply Alt/Meta prefix only
        if (alt || meta) return byteArrayOf(0x1B) + data
        return data
    }

    private fun applyToChar(byte: Byte, ctrl: Boolean, alt: Boolean, shift: Boolean, meta: Boolean): ByteArray {
        var ch = byte.toInt() and 0xFF

        // Special: Shift+Tab → backtab \e[Z
        if (shift && ch == 0x09) {
            val base = "\u001b[Z".toByteArray(Charsets.ISO_8859_1)
            val hasOtherMods = ctrl || alt || meta
            if (!hasOtherMods) return base
            // Apply xterm modifier encoding to the CSI Z sequence
            return applyToEscapeSequence(base, ctrl, alt, shift, meta)
        }

        // Special: Ctrl+Backspace → 0x08 (BS)
        if (ctrl && ch == 0x7F) {
            val result = byteArrayOf(0x08)
            return if (alt || meta) byteArrayOf(0x1B) + result else result
        }

        // Special: Ctrl+Space → 0x00 (NUL)
        if (ctrl && ch == 0x20) {
            val result = byteArrayOf(0x00)
            return if (alt || meta) byteArrayOf(0x1B) + result else result
        }

        // Special: Ctrl+digit
        if (ctrl && ch in 0x30..0x39) {
            val mapped = CTRL_DIGIT[ch]
            if (mapped != null) {
                val result = byteArrayOf(mapped.toByte())
                return if (alt || meta) byteArrayOf(0x1B) + result else result
            }
            // Ctrl+0, Ctrl+1, Ctrl+9: no standard mapping, fall through
        }

        // Shift: uppercase letters
        if (shift && ch in 0x61..0x7A) {
            ch -= 32
        }

        // Ctrl: mask to control character range
        var result = if (ctrl && ch in 0x40..0x7E) {
            byteArrayOf((ch and 0x1F).toByte())
        } else {
            byteArrayOf(ch.toByte())
        }

        // Alt/Meta: ESC prefix
        if (alt || meta) {
            result = byteArrayOf(0x1B) + result
        }

        return result
    }

    /**
     * Compute xterm modifier parameter: 1 + shift*1 + alt*2 + ctrl*4 + meta*8
     */
    private fun modifierCode(ctrl: Boolean, alt: Boolean, shift: Boolean, meta: Boolean): Int {
        var code = 1
        if (shift) code += 1
        if (alt) code += 2
        if (ctrl) code += 4
        if (meta) code += 8
        return code
    }

    private fun applyToEscapeSequence(data: ByteArray, ctrl: Boolean, alt: Boolean, shift: Boolean, meta: Boolean): ByteArray {
        val mod = modifierCode(ctrl, alt, shift, meta)
        val str = String(data, Charsets.ISO_8859_1)

        // SS3 sequences: \eOX → \e[1;modX
        if (data.size == 3 && data[1] == 'O'.code.toByte()) {
            val finalChar = data[2].toInt().toChar()
            return "\u001b[1;${mod}${finalChar}".toByteArray(Charsets.ISO_8859_1)
        }

        // Tilde sequences: \e[N~ → \e[N;mod~
        if (str.endsWith("~") && str.startsWith("\u001b[")) {
            val inner = str.substring(2, str.length - 1)
            return "\u001b[${inner};${mod}~".toByteArray(Charsets.ISO_8859_1)
        }

        // CSI sequences: \e[X → \e[1;modX (e.g., \e[A → \e[1;5A for Ctrl+Up)
        if (str.startsWith("\u001b[") && str.length >= 3) {
            val finalChar = str.last()
            val inner = str.substring(2, str.length - 1)
            val param = inner.ifEmpty { "1" }
            return "\u001b[${param};${mod}${finalChar}".toByteArray(Charsets.ISO_8859_1)
        }

        // Unknown escape: return as-is
        return data
    }
}

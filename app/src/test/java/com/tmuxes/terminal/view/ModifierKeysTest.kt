package com.tmuxes.terminal.view

import org.junit.Assert.assertArrayEquals
import org.junit.Test

/**
 * Exhaustive test suite for [ModifierKeys.apply].
 *
 * Verifies correct xterm modifier encoding for every key on ExtraKeysBar,
 * all printable ASCII input from IME, and special key combinations — each
 * tested against all 15 non-trivial modifier states (7 base + 8 meta).
 *
 * Reference: xterm ctlseqs (invisible-island.net/xterm/ctlseqs/ctlseqs.html)
 *
 * Modifier encoding formula: parameter = 1 + shift*1 + alt*2 + ctrl*4 + meta*8
 *
 *   Code | Modifiers              Code | Modifiers
 *   -----+------------------      -----+---------------------------
 *     2  | Shift                    9  | Meta
 *     3  | Alt                     10  | Meta+Shift
 *     4  | Alt+Shift               11  | Meta+Alt
 *     5  | Ctrl                    12  | Meta+Alt+Shift
 *     6  | Ctrl+Shift              13  | Meta+Ctrl
 *     7  | Ctrl+Alt                14  | Meta+Ctrl+Shift
 *     8  | Ctrl+Alt+Shift          15  | Meta+Ctrl+Alt
 *        |                         16  | Meta+Ctrl+Alt+Shift
 */
class ModifierKeysTest {

    // ===================================================================
    // Test Infrastructure
    // ===================================================================

    private fun apply(
        data: ByteArray,
        ctrl: Boolean = false,
        alt: Boolean = false,
        shift: Boolean = false,
        meta: Boolean = false,
    ) = ModifierKeys.apply(data, ctrl, alt, shift, meta)

    private fun esc(s: String) = s.toByteArray(Charsets.ISO_8859_1)

    data class Mods(
        val ctrl: Boolean,
        val alt: Boolean,
        val shift: Boolean,
        val meta: Boolean,
        val code: Int,
        val label: String,
    )

    /** All 15 modifier combos, sorted by xterm code 2..16. */
    private val allMods = listOf(
        Mods(ctrl = false, alt = false, shift = true,  meta = false, code = 2,  label = "Shift"),
        Mods(ctrl = false, alt = true,  shift = false, meta = false, code = 3,  label = "Alt"),
        Mods(ctrl = false, alt = true,  shift = true,  meta = false, code = 4,  label = "Alt+Shift"),
        Mods(ctrl = true,  alt = false, shift = false, meta = false, code = 5,  label = "Ctrl"),
        Mods(ctrl = true,  alt = false, shift = true,  meta = false, code = 6,  label = "Ctrl+Shift"),
        Mods(ctrl = true,  alt = true,  shift = false, meta = false, code = 7,  label = "Ctrl+Alt"),
        Mods(ctrl = true,  alt = true,  shift = true,  meta = false, code = 8,  label = "Ctrl+Alt+Shift"),
        Mods(ctrl = false, alt = false, shift = false, meta = true,  code = 9,  label = "Meta"),
        Mods(ctrl = false, alt = false, shift = true,  meta = true,  code = 10, label = "Meta+Shift"),
        Mods(ctrl = false, alt = true,  shift = false, meta = true,  code = 11, label = "Meta+Alt"),
        Mods(ctrl = false, alt = true,  shift = true,  meta = true,  code = 12, label = "Meta+Alt+Shift"),
        Mods(ctrl = true,  alt = false, shift = false, meta = true,  code = 13, label = "Meta+Ctrl"),
        Mods(ctrl = true,  alt = false, shift = true,  meta = true,  code = 14, label = "Meta+Ctrl+Shift"),
        Mods(ctrl = true,  alt = true,  shift = false, meta = true,  code = 15, label = "Meta+Ctrl+Alt"),
        Mods(ctrl = true,  alt = true,  shift = true,  meta = true,  code = 16, label = "Meta+Ctrl+Alt+Shift"),
    )

    /** Ctrl+digit mapping per xterm spec. */
    private val ctrlDigitMap = mapOf(
        '2' to 0x00.toByte(), // NUL
        '3' to 0x1B.toByte(), // ESC
        '4' to 0x1C.toByte(), // FS
        '5' to 0x1D.toByte(), // GS
        '6' to 0x1E.toByte(), // RS
        '7' to 0x1F.toByte(), // US
        '8' to 0x7F.toByte(), // DEL
    )

    // -- Assertion helpers --

    /** CSI sequence \e[X → \e[1;{mod}X for all 15 modifier combos. */
    private fun assertCsiKey(keyName: String, data: ByteArray) {
        val finalChar = data.last().toInt().toChar()
        for (m in allMods) {
            assertArrayEquals(
                "$keyName + ${m.label}",
                esc("\u001b[1;${m.code}$finalChar"),
                apply(data, m.ctrl, m.alt, m.shift, m.meta),
            )
        }
    }

    /** SS3 sequence \eOX → \e[1;{mod}X for all 15 modifier combos. */
    private fun assertSs3Key(keyName: String, data: ByteArray) {
        val finalChar = data[2].toInt().toChar()
        for (m in allMods) {
            assertArrayEquals(
                "$keyName + ${m.label}",
                esc("\u001b[1;${m.code}$finalChar"),
                apply(data, m.ctrl, m.alt, m.shift, m.meta),
            )
        }
    }

    /** Tilde sequence \e[N~ → \e[N;{mod}~ for all 15 modifier combos. */
    private fun assertTildeKey(keyName: String, data: ByteArray) {
        val inner = String(data, Charsets.ISO_8859_1).let { it.substring(2, it.length - 1) }
        for (m in allMods) {
            assertArrayEquals(
                "$keyName + ${m.label}",
                esc("\u001b[${inner};${m.code}~"),
                apply(data, m.ctrl, m.alt, m.shift, m.meta),
            )
        }
    }

    /** Single-byte passthrough: only Alt/Meta produces ESC prefix, Ctrl/Shift no effect. */
    private fun assertPassthroughKey(keyName: String, byte: Byte) {
        for (m in allMods) {
            assertArrayEquals(
                "$keyName + ${m.label}",
                if (m.alt || m.meta) byteArrayOf(0x1B, byte) else byteArrayOf(byte),
                apply(byteArrayOf(byte), m.ctrl, m.alt, m.shift, m.meta),
            )
        }
    }

    // ===================================================================
    // Section 1: Escape Sequence Modifier Encoding
    // xterm ctlseqs §PC-Style Function Keys
    //
    // Modifier parameter inserted before the final byte:
    //   CSI X       → CSI 1;mod X
    //   SS3 X       → CSI 1;mod X  (SS3 converted to CSI)
    //   CSI N ~     → CSI N;mod ~
    //
    // Each key × 15 modifier combos.
    // ===================================================================

    // -- 1.1 CSI arrow keys: \e[A-D --
    @Test fun `CSI Up x all modifiers`()    = assertCsiKey("Up",    esc("\u001b[A"))
    @Test fun `CSI Down x all modifiers`()  = assertCsiKey("Down",  esc("\u001b[B"))
    @Test fun `CSI Right x all modifiers`() = assertCsiKey("Right", esc("\u001b[C"))
    @Test fun `CSI Left x all modifiers`()  = assertCsiKey("Left",  esc("\u001b[D"))

    // -- 1.2 CSI navigation: \e[H, \e[F --
    @Test fun `CSI Home x all modifiers`() = assertCsiKey("Home", esc("\u001b[H"))
    @Test fun `CSI End x all modifiers`()  = assertCsiKey("End",  esc("\u001b[F"))

    // -- 1.3 SS3 arrow keys (application cursor mode): \eOA-D → CSI 1;mod A-D --
    @Test fun `SS3 Up x all modifiers`()    = assertSs3Key("Up-SS3",    esc("\u001bOA"))
    @Test fun `SS3 Down x all modifiers`()  = assertSs3Key("Down-SS3",  esc("\u001bOB"))
    @Test fun `SS3 Right x all modifiers`() = assertSs3Key("Right-SS3", esc("\u001bOC"))
    @Test fun `SS3 Left x all modifiers`()  = assertSs3Key("Left-SS3",  esc("\u001bOD"))

    // -- 1.4 SS3 F-keys (F1-F4): \eOP-S → CSI 1;mod P-S --
    @Test fun `SS3 F1 x all modifiers`() = assertSs3Key("F1", esc("\u001bOP"))
    @Test fun `SS3 F2 x all modifiers`() = assertSs3Key("F2", esc("\u001bOQ"))
    @Test fun `SS3 F3 x all modifiers`() = assertSs3Key("F3", esc("\u001bOR"))
    @Test fun `SS3 F4 x all modifiers`() = assertSs3Key("F4", esc("\u001bOS"))

    // -- 1.5 Tilde navigation: \e[N~ → \e[N;mod~ --
    @Test fun `tilde Ins x all modifiers`()  = assertTildeKey("Ins",  esc("\u001b[2~"))
    @Test fun `tilde Del x all modifiers`()  = assertTildeKey("Del",  esc("\u001b[3~"))
    @Test fun `tilde PgUp x all modifiers`() = assertTildeKey("PgUp", esc("\u001b[5~"))
    @Test fun `tilde PgDn x all modifiers`() = assertTildeKey("PgDn", esc("\u001b[6~"))

    // -- 1.6 Tilde F-keys (F5-F12): \e[N~ → \e[N;mod~ --
    @Test fun `tilde F5 x all modifiers`()  = assertTildeKey("F5",  esc("\u001b[15~"))
    @Test fun `tilde F6 x all modifiers`()  = assertTildeKey("F6",  esc("\u001b[17~"))
    @Test fun `tilde F7 x all modifiers`()  = assertTildeKey("F7",  esc("\u001b[18~"))
    @Test fun `tilde F8 x all modifiers`()  = assertTildeKey("F8",  esc("\u001b[19~"))
    @Test fun `tilde F9 x all modifiers`()  = assertTildeKey("F9",  esc("\u001b[20~"))
    @Test fun `tilde F10 x all modifiers`() = assertTildeKey("F10", esc("\u001b[21~"))
    @Test fun `tilde F11 x all modifiers`() = assertTildeKey("F11", esc("\u001b[23~"))
    @Test fun `tilde F12 x all modifiers`() = assertTildeKey("F12", esc("\u001b[24~"))

    // ===================================================================
    // Section 2: Single-Byte Processing
    // xterm ctlseqs §Alt and Meta Keys, §Other Keys
    //
    // Processing order: Shift (uppercase) → Ctrl (& 0x1F) → Alt/Meta (ESC prefix)
    // ===================================================================

    // -- 2.1 Letters a-z × 15 combos = 390 assertions --
    @Test fun `letters a-z x all 15 modifier combos`() {
        for (ch in 'a'..'z') {
            for (m in allMods) {
                var c = ch.code
                if (m.shift) c -= 32 // Shift: a→A
                val byte = if (m.ctrl && c in 0x40..0x7E) (c and 0x1F).toByte() else c.toByte()
                val expected = if (m.alt || m.meta) byteArrayOf(0x1B, byte) else byteArrayOf(byte)
                assertArrayEquals(
                    "$ch + ${m.label}",
                    expected, apply(byteArrayOf(ch.code.toByte()), m.ctrl, m.alt, m.shift, m.meta),
                )
            }
        }
    }

    // -- 2.2 Digits 0-9 × 15 combos = 150 assertions --
    // Ctrl+digit: 2→NUL, 3→ESC, 4→FS, 5→GS, 6→RS, 7→US, 8→DEL; 0,1,9 no mapping
    @Test fun `digits 0-9 x all 15 modifier combos`() {
        for (ch in '0'..'9') {
            for (m in allMods) {
                val byte = if (m.ctrl) ctrlDigitMap[ch] ?: ch.code.toByte() else ch.code.toByte()
                val expected = if (m.alt || m.meta) byteArrayOf(0x1B, byte) else byteArrayOf(byte)
                assertArrayEquals(
                    "$ch + ${m.label}",
                    expected, apply(byteArrayOf(ch.code.toByte()), m.ctrl, m.alt, m.shift, m.meta),
                )
            }
        }
    }

    // -- 2.3 Full Ctrl range 0x40-0x7E × 15 combos = 945 assertions --
    // Mechanical verification: Ctrl masks any byte in 0x40..0x7E to ch & 0x1F.
    // Shift uppercases the lowercase subset 0x61-0x7A before Ctrl masking.
    @Test fun `Ctrl range 0x40-0x7E x all 15 modifier combos`() {
        for (code in 0x40..0x7E) {
            val isLower = code in 0x61..0x7A
            for (m in allMods) {
                var ch = code
                if (m.shift && isLower) ch -= 32
                val byte = if (m.ctrl && ch in 0x40..0x7E) (ch and 0x1F).toByte() else ch.toByte()
                val expected = if (m.alt || m.meta) byteArrayOf(0x1B, byte) else byteArrayOf(byte)
                assertArrayEquals(
                    "0x${code.toString(16)} + ${m.label}",
                    expected, apply(byteArrayOf(code.toByte()), m.ctrl, m.alt, m.shift, m.meta),
                )
            }
        }
    }

    // -- 2.4 UTF-8 multi-byte: only Alt/Meta ESC prefix, 15 assertions --
    @Test fun `UTF-8 multi-byte x all modifiers`() {
        val utf8 = "中".toByteArray(Charsets.UTF_8)
        for (m in allMods) {
            assertArrayEquals(
                "中 + ${m.label}",
                if (m.alt || m.meta) byteArrayOf(0x1B) + utf8 else utf8,
                apply(utf8, m.ctrl, m.alt, m.shift, m.meta),
            )
        }
    }

    // ===================================================================
    // Section 3: Special Key Combinations
    // Per xterm/Termux convention
    // ===================================================================

    // -- 3.1 Tab (0x09) × 15 combos --
    // Shift+Tab → backtab \e[Z; with additional mods → xterm encoding \e[1;{code}Z
    // Without Shift: passthrough (only Alt/Meta adds ESC prefix)
    @Test fun `Tab x all modifiers`() {
        for (m in allMods) {
            val expected = if (m.shift) {
                if (!(m.ctrl || m.alt || m.meta)) esc("\u001b[Z")
                else esc("\u001b[1;${m.code}Z")
            } else {
                if (m.alt || m.meta) byteArrayOf(0x1B, 0x09) else byteArrayOf(0x09)
            }
            assertArrayEquals("Tab + ${m.label}", expected, apply(byteArrayOf(0x09), m.ctrl, m.alt, m.shift, m.meta))
        }
    }

    // -- 3.2 Backspace (0x7F) × 15 combos --
    // Ctrl+Backspace → 0x08 (BS); Alt/Meta adds ESC prefix
    @Test fun `Backspace x all modifiers`() {
        for (m in allMods) {
            val base = if (m.ctrl) 0x08.toByte() else 0x7F.toByte()
            val expected = if (m.alt || m.meta) byteArrayOf(0x1B, base) else byteArrayOf(base)
            assertArrayEquals("BS + ${m.label}", expected, apply(byteArrayOf(0x7F), m.ctrl, m.alt, m.shift, m.meta))
        }
    }

    // -- 3.3 Space (0x20) × 15 combos --
    // Ctrl+Space → 0x00 (NUL); Alt/Meta adds ESC prefix
    @Test fun `Space x all modifiers`() {
        for (m in allMods) {
            val base = if (m.ctrl) 0x00.toByte() else 0x20.toByte()
            val expected = if (m.alt || m.meta) byteArrayOf(0x1B, base) else byteArrayOf(base)
            assertArrayEquals("Space + ${m.label}", expected, apply(byteArrayOf(0x20), m.ctrl, m.alt, m.shift, m.meta))
        }
    }

    // -- 3.4 Enter (0x0D) × 15 combos --
    // Passthrough: Ctrl/Shift have no standard terminal effect on Enter
    @Test fun `Enter x all modifiers`() = assertPassthroughKey("Enter", 0x0D)

    // -- 3.5 ESC (0x1B) × 15 combos --
    // Passthrough: only Alt/Meta adds ESC prefix (producing ESC ESC)
    @Test fun `ESC x all modifiers`() = assertPassthroughKey("ESC", 0x1B)

    // ===================================================================
    // Section 4: Edge Cases
    // ===================================================================

    @Test fun `no modifiers returns identity for all key types`() {
        val keys = listOf(
            "a".toByteArray(),
            byteArrayOf(0x1B), byteArrayOf(0x09), byteArrayOf(0x0D),
            byteArrayOf(0x7F), byteArrayOf(0x20),
            esc("\u001b[A"), esc("\u001bOP"), esc("\u001b[15~"),
            "中".toByteArray(Charsets.UTF_8),
        )
        for (key in keys) assertArrayEquals(key, apply(key))
    }

    @Test fun `empty input x all modifiers`() {
        for (m in allMods) {
            assertArrayEquals("empty + ${m.label}", byteArrayOf(), apply(byteArrayOf(), m.ctrl, m.alt, m.shift, m.meta))
        }
    }

    @Test fun `unknown escape OSC x all modifiers`() {
        val osc = esc("\u001b]0;")
        for (m in allMods) {
            assertArrayEquals("OSC + ${m.label}", osc, apply(osc, m.ctrl, m.alt, m.shift, m.meta))
        }
    }

    @Test fun `unknown escape DEC x all modifiers`() {
        val dec = esc("\u001b#8")
        for (m in allMods) {
            assertArrayEquals("DEC + ${m.label}", dec, apply(dec, m.ctrl, m.alt, m.shift, m.meta))
        }
    }

    @Test fun `2-byte ESC sequence x all modifiers`() {
        val data = byteArrayOf(0x1B, 'x'.code.toByte())
        for (m in allMods) {
            assertArrayEquals(
                "\\ex + ${m.label}",
                if (m.alt || m.meta) byteArrayOf(0x1B) + data else data,
                apply(data, m.ctrl, m.alt, m.shift, m.meta),
            )
        }
    }
}

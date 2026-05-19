package com.tmuxes.ssh

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for [shellEscape], which is used in security-critical paths
 * (forwarded SSH command composition and tmux session name interpolation).
 *
 * The escaping rule is POSIX-shell single-quoting: wrap the value in single
 * quotes, escaping any embedded single quotes as `'\''` (close-quote, escaped
 * literal quote, re-open-quote).
 */
class ShellEscapeTest {

    @Test
    fun `empty string produces empty single-quoted string`() {
        assertEquals("''", shellEscape(""))
    }

    @Test
    fun `plain string is wrapped in single quotes`() {
        assertEquals("'hello'", shellEscape("hello"))
    }

    @Test
    fun `single quote in middle is escaped`() {
        // input: it's
        // output: 'it'\''s'
        assertEquals("'it'\\''s'", shellEscape("it's"))
    }

    @Test
    fun `multiple single quotes`() {
        // input: a'b'c
        // output: 'a'\''b'\''c'
        assertEquals("'a'\\''b'\\''c'", shellEscape("a'b'c"))
    }

    @Test
    fun `leading single quote`() {
        // input: 'hello
        // output: ''\''hello'
        assertEquals("''\\''hello'", shellEscape("'hello"))
    }

    @Test
    fun `trailing single quote`() {
        // input: hello'
        // output: 'hello'\'''
        assertEquals("'hello'\\'''", shellEscape("hello'"))
    }

    @Test
    fun `string with spaces is wrapped correctly`() {
        assertEquals("'hello world'", shellEscape("hello world"))
    }

    @Test
    fun `string with backslash is preserved as-is in single quotes`() {
        // Backslash is not special inside single quotes — pass through verbatim.
        assertEquals("'path\\to\\file'", shellEscape("path\\to\\file"))
    }

    @Test
    fun `string with newline is preserved`() {
        assertEquals("'\nhello'", shellEscape("\nhello"))
    }

    @Test
    fun `string with dollar sign is not expanded`() {
        // Dollar sign is not special inside single quotes.
        assertEquals("'\$HOME'", shellEscape("\$HOME"))
    }

    @Test
    fun `string with backtick is not executed`() {
        // Command substitution is not active inside single quotes.
        assertEquals("'`rm -rf`'", shellEscape("`rm -rf`"))
    }

    @Test
    fun `tmux session name with hyphens and dots`() {
        assertEquals("'my-session.1'", shellEscape("my-session.1"))
    }

    @Test
    fun `tmux session name with embedded quote`() {
        assertEquals("'it'\\''s a session'", shellEscape("it's a session"))
    }

    @Test
    fun `injection attempt is neutralised`() {
        // A naive concatenation would let `$()` execute. Single quotes block it.
        val malicious = "harmless'; rm -rf / #"
        val escaped = shellEscape(malicious)
        // Verify the close-quote in the input was escaped, not left bare.
        assertEquals("'harmless'\\''; rm -rf / #'", escaped)
    }

    @Test
    fun `only single quotes`() {
        // input: '' (two single quotes)
        // output: ''\'''\'''
        assertEquals("''\\'''\\'''", shellEscape("''"))
    }
}

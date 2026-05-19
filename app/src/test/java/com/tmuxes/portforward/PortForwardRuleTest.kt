package com.tmuxes.portforward

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [PortForwardRule.parse] / [PortForwardRule.parseAll] /
 * [PortForwardRule.serialize] / [PortForwardRule.serializeAll].
 *
 * These pure functions are part of the persistence path: the YAML
 * `local_forwards` field is parsed via these methods on app start and
 * serialized back when the user adds/removes a forward.
 */
class PortForwardRuleTest {

    // ---------------------------------------------------------------------
    // parse(text)
    // ---------------------------------------------------------------------

    @Test
    fun `parse three-part format produces explicit remoteHost`() {
        val r = PortForwardRule.parse("8080:db.internal:5432")
        assertEquals(PortForwardRule(8080, "db.internal", 5432), r)
    }

    @Test
    fun `parse two-part format defaults remoteHost to 127_0_0_1`() {
        val r = PortForwardRule.parse("3000:3000")
        assertEquals(PortForwardRule(3000, "127.0.0.1", 3000), r)
    }

    @Test
    fun `parse trims whitespace around fields`() {
        val r = PortForwardRule.parse("  8080 :  db.internal  :  5432  ")
        assertEquals(PortForwardRule(8080, "db.internal", 5432), r)
    }

    @Test
    fun `parse non-numeric local port returns null`() {
        assertNull(PortForwardRule.parse("abc:host:80"))
    }

    @Test
    fun `parse non-numeric remote port returns null`() {
        assertNull(PortForwardRule.parse("8080:host:notaport"))
    }

    @Test
    fun `parse single-part returns null`() {
        assertNull(PortForwardRule.parse("notaport"))
    }

    @Test
    fun `parse empty string returns null`() {
        assertNull(PortForwardRule.parse(""))
    }

    @Test
    fun `parse four-part returns null`() {
        assertNull(PortForwardRule.parse("8080:host:80:extra"))
    }

    // ---------------------------------------------------------------------
    // parseAll(text)
    // ---------------------------------------------------------------------

    @Test
    fun `parseAll null returns empty list`() {
        assertTrue(PortForwardRule.parseAll(null).isEmpty())
    }

    @Test
    fun `parseAll blank returns empty list`() {
        assertTrue(PortForwardRule.parseAll("   \n   ").isEmpty())
    }

    @Test
    fun `parseAll skips invalid lines silently`() {
        val rules = PortForwardRule.parseAll("8080:db:5432\nbad\n9090:3000")
        assertEquals(2, rules.size)
        assertEquals(PortForwardRule(8080, "db", 5432), rules[0])
        assertEquals(PortForwardRule(9090, "127.0.0.1", 3000), rules[1])
    }

    @Test
    fun `parseAll preserves order`() {
        val rules = PortForwardRule.parseAll("1000:3000\n2000:3000\n3000:3000")
        assertEquals(listOf(1000, 2000, 3000), rules.map { it.localPort })
    }

    // ---------------------------------------------------------------------
    // serialize / serializeAll round-trip
    // ---------------------------------------------------------------------

    @Test
    fun `serialize round-trips through parse for explicit host`() {
        val rule = PortForwardRule(8080, "db.internal", 5432)
        assertEquals(rule, PortForwardRule.parse(rule.serialize()))
    }

    @Test
    fun `serialize round-trips through parse for default host`() {
        val rule = PortForwardRule(8080, "127.0.0.1", 80)
        // When host is the default 127.0.0.1, serialize still emits the explicit form
        // but parse should produce an equal rule
        assertEquals(rule, PortForwardRule.parse(rule.serialize()))
    }

    @Test
    fun `serializeAll empty list returns null`() {
        assertNull(PortForwardRule.serializeAll(emptyList()))
    }

    @Test
    fun `serializeAll then parseAll round-trip`() {
        val rules = listOf(
            PortForwardRule(8080, "host1", 80),
            PortForwardRule(9090, "127.0.0.1", 3000),
            PortForwardRule(443, "internal.example.com", 8443)
        )
        val text = PortForwardRule.serializeAll(rules)
        assertEquals(rules, PortForwardRule.parseAll(text))
    }

    @Test
    fun `serializeAll uses newline separator`() {
        val text = PortForwardRule.serializeAll(
            listOf(
                PortForwardRule(8080, "a", 80),
                PortForwardRule(9090, "b", 90)
            )
        )
        assertEquals("8080:a:80\n9090:b:90", text)
    }
}

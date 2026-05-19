package com.tmuxes.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SshConfigParserTest {

    @Test
    fun `parse basic host block`() {
        val config = """
            Host myserver
                HostName 192.168.1.100
                User admin
                Port 2222
        """.trimIndent()

        val hosts = SshConfigParser.parse(config)
        assertEquals(1, hosts.size)
        assertEquals("myserver", hosts[0].host)
        assertEquals("192.168.1.100", hosts[0].hostName)
        assertEquals("admin", hosts[0].user)
        assertEquals(2222, hosts[0].port)
    }

    @Test
    fun `parse multiple host blocks`() {
        val config = """
            Host server1
                HostName 10.0.0.1
                User root

            Host server2
                HostName 10.0.0.2
                User deploy
                Port 22
        """.trimIndent()

        val hosts = SshConfigParser.parse(config)
        assertEquals(2, hosts.size)
        assertEquals("server1", hosts[0].host)
        assertEquals("10.0.0.1", hosts[0].hostName)
        assertEquals("root", hosts[0].user)
        assertEquals("server2", hosts[1].host)
        assertEquals("10.0.0.2", hosts[1].hostName)
        assertEquals("deploy", hosts[1].user)
        assertEquals(22, hosts[1].port)
    }

    @Test
    fun `skip wildcard host`() {
        val config = """
            Host *
                ServerAliveInterval 60
                ServerAliveCountMax 3

            Host myserver
                HostName example.com
                User me
        """.trimIndent()

        val hosts = SshConfigParser.parse(config)
        assertEquals(1, hosts.size)
        assertEquals("myserver", hosts[0].host)
    }

    @Test
    fun `skip wildcard star-dot-star`() {
        val config = """
            Host *.*
                ProxyJump gateway

            Host compute1
                HostName 10.0.0.5
                User hpc
        """.trimIndent()

        val hosts = SshConfigParser.parse(config)
        assertEquals(1, hosts.size)
        assertEquals("compute1", hosts[0].host)
    }

    @Test
    fun `parse host with ProxyJump`() {
        val config = """
            Host gateway
                HostName gw.example.com
                User admin

            Host internal
                HostName 10.0.0.1
                User root
                ProxyJump gateway
        """.trimIndent()

        val hosts = SshConfigParser.parse(config)
        assertEquals(2, hosts.size)
        assertNull(hosts[0].proxyJump)
        assertEquals("gateway", hosts[1].proxyJump)
    }

    @Test
    fun `parse host with IdentityFile`() {
        val config = """
            Host myserver
                HostName example.com
                User deploy
                IdentityFile ~/.ssh/id_ed25519
        """.trimIndent()

        val hosts = SshConfigParser.parse(config)
        assertEquals(1, hosts.size)
        assertEquals("~/.ssh/id_ed25519", hosts[0].identityFile)
    }

    @Test
    fun `skip comments and empty lines`() {
        val config = """
            # This is a comment

            Host myserver
                # Another comment
                HostName example.com
                User admin

            # End of config
        """.trimIndent()

        val hosts = SshConfigParser.parse(config)
        assertEquals(1, hosts.size)
        assertEquals("myserver", hosts[0].host)
        assertEquals("example.com", hosts[0].hostName)
    }

    @Test
    fun `case insensitive directives`() {
        val config = """
            host myserver
                hostname example.com
                USER admin
                PORT 2222
        """.trimIndent()

        val hosts = SshConfigParser.parse(config)
        assertEquals(1, hosts.size)
        assertEquals("myserver", hosts[0].host)
        assertEquals("example.com", hosts[0].hostName)
        assertEquals("admin", hosts[0].user)
        assertEquals(2222, hosts[0].port)
    }

    @Test
    fun `empty config returns empty list`() {
        val hosts = SshConfigParser.parse("")
        assertTrue(hosts.isEmpty())
    }

    @Test
    fun `config with only comments returns empty list`() {
        val config = """
            # Just comments
            # Nothing else
        """.trimIndent()

        val hosts = SshConfigParser.parse(config)
        assertTrue(hosts.isEmpty())
    }

    @Test
    fun `config with only wildcard returns empty list`() {
        val config = """
            Host *
                ServerAliveInterval 60
        """.trimIndent()

        val hosts = SshConfigParser.parse(config)
        assertTrue(hosts.isEmpty())
    }

    @Test
    fun `host with no directives`() {
        val config = """
            Host emptyhost
            Host realhost
                HostName example.com
        """.trimIndent()

        val hosts = SshConfigParser.parse(config)
        assertEquals(2, hosts.size)
        assertEquals("emptyhost", hosts[0].host)
        assertNull(hosts[0].hostName)
        assertEquals("realhost", hosts[1].host)
        assertEquals("example.com", hosts[1].hostName)
    }

    @Test
    fun `skip Match blocks`() {
        val config = """
            Host myserver
                HostName example.com
                User admin

            Match host *.internal
                ProxyJump bastion

            Host other
                HostName other.com
                User root
        """.trimIndent()

        val hosts = SshConfigParser.parse(config)
        assertEquals(2, hosts.size)
        assertEquals("myserver", hosts[0].host)
        assertEquals("other", hosts[1].host)
    }

    @Test
    fun `host with multiple patterns uses first non-negation`() {
        val config = """
            Host web-* !web-test
                HostName webcluster.example.com
                User deploy
        """.trimIndent()

        val hosts = SshConfigParser.parse(config)
        assertEquals(1, hosts.size)
        assertEquals("web-*", hosts[0].host)
    }

    @Test
    fun `skip negation-only host patterns`() {
        val config = """
            Host !private
                User public

            Host realhost
                HostName example.com
        """.trimIndent()

        val hosts = SshConfigParser.parse(config)
        assertEquals(1, hosts.size)
        assertEquals("realhost", hosts[0].host)
    }

    @Test
    fun `invalid port is null`() {
        val config = """
            Host myserver
                HostName example.com
                Port notanumber
        """.trimIndent()

        val hosts = SshConfigParser.parse(config)
        assertEquals(1, hosts.size)
        assertNull(hosts[0].port)
    }

    @Test
    fun `hash comments do not crash`() {
        val config = """
            # ignored host comment
            Host myserver
                HostName example.com
                User admin
                # ignored port comment
                Port 22
        """.trimIndent()

        val hosts = SshConfigParser.parse(config)
        assertEquals(1, hosts.size)
        assertEquals("myserver", hosts[0].host)
    }

    @Test
    fun `realistic HPC config`() {
        val config = """
            Host *
                ServerAliveInterval 60
                ServerAliveCountMax 3
                AddKeysToAgent yes

            Host login
                HostName login.hpc.example.edu
                User student01
                IdentityFile ~/.ssh/id_ed25519

            Host compute1
                HostName node001
                User student01
                ProxyJump login

            Host compute2
                HostName node002
                User student01
                ProxyJump login

            Host gpu1
                HostName gpu-node01
                User student01
                Port 2222
                ProxyJump login
        """.trimIndent()

        val hosts = SshConfigParser.parse(config)
        assertEquals(4, hosts.size)

        assertEquals("login", hosts[0].host)
        assertEquals("login.hpc.example.edu", hosts[0].hostName)
        assertNull(hosts[0].proxyJump)

        assertEquals("compute1", hosts[1].host)
        assertEquals("node001", hosts[1].hostName)
        assertEquals("login", hosts[1].proxyJump)

        assertEquals("compute2", hosts[2].host)
        assertEquals("node002", hosts[2].hostName)
        assertEquals("login", hosts[2].proxyJump)

        assertEquals("gpu1", hosts[3].host)
        assertEquals("gpu-node01", hosts[3].hostName)
        assertEquals(2222, hosts[3].port)
        assertEquals("login", hosts[3].proxyJump)
    }

    @Test
    fun `summary format`() {
        val host = SshConfigHost(
            host = "myserver",
            hostName = "example.com",
            user = "admin",
            port = 2222
        )
        assertEquals("myserver → admin@example.com:2222", host.summary)
    }

    @Test
    fun `summary without user and default port`() {
        val host = SshConfigHost(
            host = "myserver",
            hostName = "example.com",
            port = 22
        )
        assertEquals("myserver → example.com", host.summary)
    }

    @Test
    fun `summary without hostname`() {
        val host = SshConfigHost(host = "myserver")
        assertEquals("myserver", host.summary)
    }
}

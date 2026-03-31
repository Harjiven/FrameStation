// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.streaming

import com.limelight.nvstream.mdns.MdnsComputer
import java.net.Inet6Address
import java.net.InetAddress
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [DiscoveryManager] callback handling and state management.
 *
 * These tests exercise the [MdnsDiscoveryListener] implementation directly
 * without starting actual jmDNS network discovery.
 */
class DiscoveryManagerTest {

    private lateinit var manager: DiscoveryManager

    @Before
    fun setUp() {
        manager = DiscoveryManager()
    }

    @After
    fun tearDown() {
        manager.stopDiscovery()
    }

    // -- notifyComputerAdded --

    @Test
    fun `notifyComputerAdded adds host to discovered list`() {
        val computer = createMdnsComputer("MyPC", "192.168.1.50", port = 47989)

        manager.notifyComputerAdded(computer)

        val hosts = manager.discoveredHosts.value
        assertEquals(1, hosts.size)
        assertEquals("MyPC", hosts[0].name)
        assertEquals("192.168.1.50", hosts[0].address)
        assertEquals(47989, hosts[0].port)
    }

    @Test
    fun `notifyComputerAdded deduplicates same name and address`() {
        val computer1 = createMdnsComputer("MyPC", "192.168.1.50", port = 47989)
        val computer2 = createMdnsComputer("MyPC", "192.168.1.50", port = 47989)

        manager.notifyComputerAdded(computer1)
        manager.notifyComputerAdded(computer2)

        val hosts = manager.discoveredHosts.value
        assertEquals(1, hosts.size)
    }

    @Test
    fun `notifyComputerAdded allows different addresses for same name`() {
        val computer1 = createMdnsComputer("MyPC", "192.168.1.50", port = 47989)
        val computer2 = createMdnsComputer("MyPC", "192.168.1.51", port = 47989)

        manager.notifyComputerAdded(computer1)
        manager.notifyComputerAdded(computer2)

        val hosts = manager.discoveredHosts.value
        assertEquals(2, hosts.size)
    }

    @Test
    fun `notifyComputerAdded allows different names for same address`() {
        val computer1 = createMdnsComputer("PC-A", "192.168.1.50", port = 47989)
        val computer2 = createMdnsComputer("PC-B", "192.168.1.50", port = 47989)

        manager.notifyComputerAdded(computer1)
        manager.notifyComputerAdded(computer2)

        val hosts = manager.discoveredHosts.value
        assertEquals(2, hosts.size)
    }

    @Test
    fun `notifyComputerAdded captures IPv6 address when available`() {
        val v6Addr = Inet6Address.getByName("fe80::1") as Inet6Address
        val computer = MdnsComputer(
            "MyPC",
            InetAddress.getByName("192.168.1.50"),
            v6Addr,
            47989,
        )

        manager.notifyComputerAdded(computer)

        val hosts = manager.discoveredHosts.value
        assertEquals(1, hosts.size)
        assertTrue(hosts[0].ipv6Address != null)
    }

    @Test
    fun `notifyComputerAdded handles null IPv6 address`() {
        val computer = createMdnsComputer("MyPC", "192.168.1.50", port = 47989)

        manager.notifyComputerAdded(computer)

        val hosts = manager.discoveredHosts.value
        assertEquals(1, hosts.size)
        assertNull(hosts[0].ipv6Address)
    }

    // -- notifyDiscoveryFailure --

    @Test
    fun `notifyDiscoveryFailure sets error message`() {
        assertNull(manager.discoveryError.value)

        manager.notifyDiscoveryFailure(RuntimeException("Network unavailable"))

        assertEquals("Network unavailable", manager.discoveryError.value)
    }

    @Test
    fun `notifyDiscoveryFailure with null message uses fallback`() {
        manager.notifyDiscoveryFailure(RuntimeException())

        assertEquals("Network discovery failed", manager.discoveryError.value)
    }

    // -- Start/Stop lifecycle --

    @Test
    fun `initial state is not scanning`() {
        assertFalse(manager.isScanning.value)
    }

    @Test
    fun `initial discovered hosts list is empty`() {
        assertTrue(manager.discoveredHosts.value.isEmpty())
    }

    @Test
    fun `initial discovery error is null`() {
        assertNull(manager.discoveryError.value)
    }

    @Test
    fun `stopDiscovery sets isScanning to false`() {
        // Even without starting, stopDiscovery should be safe
        manager.stopDiscovery()
        assertFalse(manager.isScanning.value)
    }

    @Test
    fun `refreshDiscovery clears discovered hosts`() {
        val computer = createMdnsComputer("MyPC", "192.168.1.50", port = 47989)
        manager.notifyComputerAdded(computer)
        assertEquals(1, manager.discoveredHosts.value.size)

        // refreshDiscovery clears the list (and tries to start, which may fail in test env)
        try {
            manager.refreshDiscovery()
        } catch (_: Exception) {
            // jmDNS may throw in test environment — that's fine, we're testing the clear
        }

        assertTrue(manager.discoveredHosts.value.isEmpty())
    }

    @Test
    fun `multiple computers accumulate in discovered hosts`() {
        manager.notifyComputerAdded(createMdnsComputer("PC-1", "192.168.1.10", 47989))
        manager.notifyComputerAdded(createMdnsComputer("PC-2", "192.168.1.11", 47989))
        manager.notifyComputerAdded(createMdnsComputer("PC-3", "192.168.1.12", 47989))

        assertEquals(3, manager.discoveredHosts.value.size)
    }

    // -- Helpers --

    private fun createMdnsComputer(
        name: String,
        address: String,
        port: Int,
    ): MdnsComputer {
        return MdnsComputer(
            name,
            InetAddress.getByName(address),
            null, // no IPv6
            port,
        )
    }
}

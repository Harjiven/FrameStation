// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HostConfigTest {

    @Test
    fun `default id is generated`() {
        val host = HostConfig(name = "Test", address = "192.168.1.1")
        assertNotNull(host.id)
        assert(host.id.isNotBlank())
    }

    @Test
    fun `two hosts get different ids`() {
        val host1 = HostConfig(name = "Host1", address = "192.168.1.1")
        val host2 = HostConfig(name = "Host2", address = "192.168.1.2")
        assertNotEquals(host1.id, host2.id)
    }

    @Test
    fun `default values are correct`() {
        val host = HostConfig(name = "Test", address = "10.0.0.1")
        assertNull(host.macAddress)
        assertFalse(host.isPaired)
        assertNull(host.gpuType)
        assertNull(host.certFileName)
        assertEquals(0L, host.lastConnected)
    }

    @Test
    fun `explicit id is preserved`() {
        val host = HostConfig(id = "custom-id", name = "Test", address = "10.0.0.1")
        assertEquals("custom-id", host.id)
    }

    @Test
    fun `copy preserves all fields`() {
        val host = HostConfig(
            id = "abc",
            name = "MyPC",
            address = "192.168.1.50",
            macAddress = "AA:BB:CC:DD:EE:FF",
            isPaired = true,
            gpuType = "RTX 4090",
            certFileName = "server_abc.crt",
            lastConnected = 1234567890L,
        )
        val copy = host.copy(name = "NewName")
        assertEquals("NewName", copy.name)
        assertEquals("abc", copy.id)
        assertEquals("192.168.1.50", copy.address)
        assertEquals("AA:BB:CC:DD:EE:FF", copy.macAddress)
        assertEquals(true, copy.isPaired)
        assertEquals("RTX 4090", copy.gpuType)
        assertEquals("server_abc.crt", copy.certFileName)
        assertEquals(1234567890L, copy.lastConnected)
    }
}

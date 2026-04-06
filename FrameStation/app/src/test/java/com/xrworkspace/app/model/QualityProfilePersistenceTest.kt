// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class QualityProfilePersistenceTest {

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var manager: HostConfigManager

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
        manager = HostConfigManager(prefs)
    }

    @Test
    fun `saveHosts and loadHosts round-trips qualityProfile`() {
        val profile = StreamSettings(
            resolution = Resolution.RES_1440P,
            fps = 120,
            bitrateKbps = 50000,
            codec = VideoCodec.H265,
        )
        val host = HostConfig(
            id = "qp1",
            name = "Gaming PC",
            address = "10.0.0.1",
            qualityProfile = profile,
        )
        manager.saveHosts(listOf(host))
        val loaded = manager.loadHosts()

        assertEquals(1, loaded.size)
        val loadedProfile = loaded[0].qualityProfile
        assertEquals(Resolution.RES_1440P, loadedProfile?.resolution)
        assertEquals(120, loadedProfile?.fps)
        assertEquals(50000, loadedProfile?.bitrateKbps)
        assertEquals(VideoCodec.H265, loadedProfile?.codec)
    }

    @Test
    fun `saveHosts with null qualityProfile loads as null`() {
        val host = HostConfig(
            id = "qp2",
            name = "Office PC",
            address = "10.0.0.2",
            qualityProfile = null,
        )
        manager.saveHosts(listOf(host))
        val loaded = manager.loadHosts()

        assertEquals(1, loaded.size)
        assertNull(loaded[0].qualityProfile)
    }

    @Test
    fun `qualityProfile uses correct resolution enum`() {
        Resolution.entries.forEach { res ->
            val host = HostConfig(
                id = "res-${res.name}",
                name = "Test ${res.label}",
                address = "1.1.1.1",
                qualityProfile = StreamSettings(resolution = res),
            )
            manager.saveHosts(listOf(host))
            val loaded = manager.loadHosts()
            assertEquals(
                "Resolution ${res.name} should round-trip",
                res,
                loaded[0].qualityProfile?.resolution,
            )
        }
    }

    @Test
    fun `qualityProfile uses correct codec enum`() {
        VideoCodec.entries.forEach { codec ->
            val host = HostConfig(
                id = "codec-${codec.name}",
                name = "Test ${codec.label}",
                address = "2.2.2.2",
                qualityProfile = StreamSettings(codec = codec),
            )
            manager.saveHosts(listOf(host))
            val loaded = manager.loadHosts()
            assertEquals(
                "Codec ${codec.name} should round-trip",
                codec,
                loaded[0].qualityProfile?.codec,
            )
        }
    }

    @Test
    fun `loadHosts without qualityProfile field returns null`() {
        // Manually write JSON without qualityProfile key to simulate old data
        val json = """[{"id":"old1","name":"Old PC","address":"3.3.3.3","macAddress":"","isPaired":false,"gpuType":"","certFileName":"","lastConnected":0}]"""
        prefs.edit().putString("host_configs_json", json).apply()

        val loaded = manager.loadHosts()
        assertEquals(1, loaded.size)
        assertEquals("old1", loaded[0].id)
        assertEquals("Old PC", loaded[0].name)
        assertNull(loaded[0].qualityProfile)
    }

    @Test
    fun `updateHost preserves qualityProfile when not changing it`() {
        val profile = StreamSettings(
            resolution = Resolution.RES_4K,
            fps = 60,
            bitrateKbps = 80000,
            codec = VideoCodec.H264,
        )
        val host = HostConfig(
            id = "up1",
            name = "My PC",
            address = "4.4.4.4",
            qualityProfile = profile,
        )
        manager.saveHosts(listOf(host))

        // Update name only, keep same qualityProfile
        val updated = host.copy(name = "Renamed PC")
        manager.updateHost(updated)

        val loaded = manager.loadHosts()
        assertEquals(1, loaded.size)
        assertEquals("Renamed PC", loaded[0].name)
        assertEquals(Resolution.RES_4K, loaded[0].qualityProfile?.resolution)
        assertEquals(60, loaded[0].qualityProfile?.fps)
        assertEquals(80000, loaded[0].qualityProfile?.bitrateKbps)
        assertEquals(VideoCodec.H264, loaded[0].qualityProfile?.codec)
    }
}

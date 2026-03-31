// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.model

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [StreamSettingsManager] JSON serialization round-trip and defaults.
 * Uses [FakeSharedPreferences] from HostConfigManagerTest to avoid Android framework dependency.
 */
class StreamSettingsManagerTest {

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var manager: StreamSettingsManager

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
        manager = StreamSettingsManager(prefs)
    }

    @Test
    fun `loadStreamSettings returns defaults when no data saved`() {
        val settings = manager.loadStreamSettings()
        assertEquals(Resolution.RES_1080P, settings.resolution)
        assertEquals(60, settings.fps)
        assertEquals(20000, settings.bitrateKbps)
        assertEquals(VideoCodec.AUTO, settings.codec)
    }

    @Test
    fun `saveStreamSettings and loadStreamSettings round-trip`() {
        val original = StreamSettings(
            resolution = Resolution.RES_4K,
            fps = 120,
            bitrateKbps = 50000,
            codec = VideoCodec.H265,
        )
        manager.saveStreamSettings(original)
        val loaded = manager.loadStreamSettings()
        assertEquals(original, loaded)
    }

    @Test
    fun `round-trip preserves all Resolution values`() {
        Resolution.entries.forEach { resolution ->
            val settings = StreamSettings(resolution = resolution)
            manager.saveStreamSettings(settings)
            val loaded = manager.loadStreamSettings()
            assertEquals(resolution, loaded.resolution)
        }
    }

    @Test
    fun `round-trip preserves all VideoCodec values`() {
        VideoCodec.entries.forEach { codec ->
            val settings = StreamSettings(codec = codec)
            manager.saveStreamSettings(settings)
            val loaded = manager.loadStreamSettings()
            assertEquals(codec, loaded.codec)
        }
    }

    @Test
    fun `loadStreamSettings returns defaults on corrupted JSON`() {
        prefs.edit().putString("stream_settings_json", "not valid json").apply()
        val settings = manager.loadStreamSettings()
        assertEquals(StreamSettings(), settings)
    }

    @Test
    fun `loadStreamSettings returns default resolution for unknown resolution name`() {
        val json = JSONObject().apply {
            put("resolution", "RES_8K")
            put("fps", 60)
            put("bitrateKbps", 20000)
            put("codec", "AUTO")
        }
        prefs.edit().putString("stream_settings_json", json.toString()).apply()
        val settings = manager.loadStreamSettings()
        assertEquals(Resolution.RES_1080P, settings.resolution)
    }

    @Test
    fun `loadStreamSettings returns default codec for unknown codec name`() {
        val json = JSONObject().apply {
            put("resolution", "RES_1080P")
            put("fps", 60)
            put("bitrateKbps", 20000)
            put("codec", "AV1")
        }
        prefs.edit().putString("stream_settings_json", json.toString()).apply()
        val settings = manager.loadStreamSettings()
        assertEquals(VideoCodec.AUTO, settings.codec)
    }

    @Test
    fun `loadStreamSettings uses defaults for missing fields`() {
        val json = JSONObject() // empty object — all fields missing
        prefs.edit().putString("stream_settings_json", json.toString()).apply()
        val settings = manager.loadStreamSettings()
        assertEquals(Resolution.RES_1080P, settings.resolution)
        assertEquals(60, settings.fps)
        assertEquals(20000, settings.bitrateKbps)
        assertEquals(VideoCodec.AUTO, settings.codec)
    }

    @Test
    fun `saveStreamSettings writes to correct SharedPreferences key`() {
        manager.saveStreamSettings(StreamSettings())
        val raw = prefs.getString("stream_settings_json", null)
        assertNotNull("Expected stream_settings_json to be saved", raw)
        val obj = JSONObject(raw!!)
        assertEquals("RES_1080P", obj.getString("resolution"))
    }

    @Test
    fun `overwriting settings replaces previous values`() {
        manager.saveStreamSettings(StreamSettings(resolution = Resolution.RES_720P))
        manager.saveStreamSettings(StreamSettings(resolution = Resolution.RES_4K))
        val loaded = manager.loadStreamSettings()
        assertEquals(Resolution.RES_4K, loaded.resolution)
    }

    @Test
    fun `round-trip preserves custom fps values`() {
        listOf(30, 60, 120).forEach { fps ->
            val settings = StreamSettings(fps = fps)
            manager.saveStreamSettings(settings)
            val loaded = manager.loadStreamSettings()
            assertEquals(fps, loaded.fps)
        }
    }

    @Test
    fun `round-trip preserves bitrate values`() {
        listOf(1000, 5000, 20000, 50000, 100000).forEach { bitrate ->
            val settings = StreamSettings(bitrateKbps = bitrate)
            manager.saveStreamSettings(settings)
            val loaded = manager.loadStreamSettings()
            assertEquals(bitrate, loaded.bitrateKbps)
        }
    }
}

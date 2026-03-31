// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.model

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [AudioSettingsManager] JSON serialization round-trip and defaults.
 * Uses [FakeSharedPreferences] from HostConfigManagerTest to avoid Android framework dependency.
 */
class AudioSettingsManagerTest {

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var manager: AudioSettingsManager

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
        manager = AudioSettingsManager(prefs)
    }

    @Test
    fun `loadAudioSettings returns defaults when no data saved`() {
        val settings = manager.loadAudioSettings()
        assertEquals(AudioMode.STREAM_AUDIO, settings.audioMode)
        assertEquals(AudioChannels.STEREO, settings.audioChannels)
        assertFalse(settings.enableAudioFx)
    }

    @Test
    fun `save and load round-trip preserves all fields`() {
        val original = AudioSettings(
            audioMode = AudioMode.MUTED,
            audioChannels = AudioChannels.SURROUND_71,
            enableAudioFx = true,
        )
        manager.saveAudioSettings(original)
        val loaded = manager.loadAudioSettings()
        assertEquals(original, loaded)
    }

    @Test
    fun `save and load round-trip with default settings`() {
        val original = AudioSettings()
        manager.saveAudioSettings(original)
        val loaded = manager.loadAudioSettings()
        assertEquals(original, loaded)
    }

    @Test
    fun `save and load round-trip with 5_1 surround`() {
        val original = AudioSettings(
            audioMode = AudioMode.STREAM_AUDIO,
            audioChannels = AudioChannels.SURROUND_51,
            enableAudioFx = false,
        )
        manager.saveAudioSettings(original)
        val loaded = manager.loadAudioSettings()
        assertEquals(original, loaded)
    }

    @Test
    fun `round-trip preserves all AudioMode values`() {
        AudioMode.entries.forEach { mode ->
            val settings = AudioSettings(audioMode = mode)
            manager.saveAudioSettings(settings)
            val loaded = manager.loadAudioSettings()
            assertEquals(mode, loaded.audioMode)
        }
    }

    @Test
    fun `round-trip preserves all AudioChannels values`() {
        AudioChannels.entries.forEach { channels ->
            val settings = AudioSettings(audioChannels = channels)
            manager.saveAudioSettings(settings)
            val loaded = manager.loadAudioSettings()
            assertEquals(channels, loaded.audioChannels)
        }
    }

    @Test
    fun `loadAudioSettings returns defaults for corrupted JSON`() {
        prefs.edit().putString("audio_settings_json", "not valid json").apply()
        val settings = manager.loadAudioSettings()
        assertEquals(AudioSettings(), settings)
    }

    @Test
    fun `loadAudioSettings returns defaults for unknown enum values`() {
        val json = JSONObject().apply {
            put("audioMode", "NONEXISTENT_MODE")
            put("audioChannels", "NONEXISTENT_CHANNELS")
            put("enableAudioFx", true)
        }
        prefs.edit().putString("audio_settings_json", json.toString()).apply()
        val settings = manager.loadAudioSettings()
        assertEquals(AudioMode.STREAM_AUDIO, settings.audioMode)
        assertEquals(AudioChannels.STEREO, settings.audioChannels)
        // enableAudioFx should still be parsed correctly
        assertEquals(true, settings.enableAudioFx)
    }

    @Test
    fun `loadAudioSettings handles missing fields gracefully`() {
        val json = JSONObject().apply {
            put("audioMode", AudioMode.MUTED.name)
            // audioChannels and enableAudioFx are missing
        }
        prefs.edit().putString("audio_settings_json", json.toString()).apply()
        val settings = manager.loadAudioSettings()
        assertEquals(AudioMode.MUTED, settings.audioMode)
        assertEquals(AudioChannels.STEREO, settings.audioChannels)
        assertFalse(settings.enableAudioFx)
    }

    @Test
    fun `saveAudioSettings writes to correct SharedPreferences key`() {
        manager.saveAudioSettings(AudioSettings())
        val raw = prefs.getString("audio_settings_json", null)
        assertNotNull("Expected audio_settings_json to be saved", raw)
        val obj = JSONObject(raw!!)
        assertEquals("STREAM_AUDIO", obj.getString("audioMode"))
    }

    @Test
    fun `saveAudioSettings writes valid JSON with all fields`() {
        val settings = AudioSettings(
            audioMode = AudioMode.MUTED,
            audioChannels = AudioChannels.SURROUND_51,
            enableAudioFx = true,
        )
        manager.saveAudioSettings(settings)
        val raw = prefs.getString("audio_settings_json", null)!!
        val obj = JSONObject(raw)
        assertEquals("MUTED", obj.getString("audioMode"))
        assertEquals("SURROUND_51", obj.getString("audioChannels"))
        assertEquals(true, obj.getBoolean("enableAudioFx"))
    }

    @Test
    fun `overwriting settings replaces previous values`() {
        manager.saveAudioSettings(AudioSettings(audioMode = AudioMode.MUTED))
        manager.saveAudioSettings(AudioSettings(audioMode = AudioMode.STREAM_AUDIO))
        val loaded = manager.loadAudioSettings()
        assertEquals(AudioMode.STREAM_AUDIO, loaded.audioMode)
    }
}

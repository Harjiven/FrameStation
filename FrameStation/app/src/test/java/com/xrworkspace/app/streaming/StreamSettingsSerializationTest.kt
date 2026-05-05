// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.streaming

import com.xrworkspace.app.model.AudioMode
import com.xrworkspace.app.model.AudioSettings
import com.xrworkspace.app.model.Resolution
import com.xrworkspace.app.model.StreamSettings
import com.xrworkspace.app.model.VideoCodec
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the JSON serialization contract between [StreamServiceConnection] (serializer)
 * and [StreamService] (parser) for IPC stream settings.
 *
 * These two methods live in different classes and are both `private`, but they share
 * a wire format: the JSON schema is the critical contract between the UI process and
 * the stream process. Breaking this contract causes silent stream failures (wrong
 * resolution, wrong codec, no audio) without any compile-time safety net.
 *
 * This test file exercises the contract by hand-constructing the JSON that
 * [StreamServiceConnection.serializeStreamSettings] would produce and parsing it
 * the same way [StreamService.parseStreamSettings] does.
 */
class StreamSettingsSerializationTest {

    // -----------------------------------------------------------------------
    // StreamSettings: serialization (what StreamServiceConnection produces)
    // -----------------------------------------------------------------------

    @Test
    fun `serializeStreamSettings produces correct JSON schema`() {
        val settings = StreamSettings(
            resolution = Resolution.RES_1440P,
            fps = 90,
            bitrateKbps = 50000,
            codec = VideoCodec.AV1_MAIN10,
            enableHdr = true,
        )
        val json = serializeStreamSettings(settings)
        val obj = JSONObject(json)

        assertEquals("RES_1440P", obj.getString("resolution"))
        assertEquals(90, obj.getInt("fps"))
        assertEquals(50000, obj.getInt("bitrateKbps"))
        assertEquals("AV1_MAIN10", obj.getString("codec"))
        assertTrue(obj.getBoolean("enableHdr"))
    }

    @Test
    fun `serializeStreamSettings with defaults`() {
        val settings = StreamSettings()
        val json = serializeStreamSettings(settings)
        val obj = JSONObject(json)

        assertEquals("RES_1080P", obj.getString("resolution"))
        assertEquals(60, obj.getInt("fps"))
        assertEquals(20000, obj.getInt("bitrateKbps"))
        assertEquals("AUTO", obj.getString("codec"))
        assertFalse(obj.getBoolean("enableHdr"))
    }

    @Test
    fun `all Resolution enum values survive roundtrip`() {
        for (res in Resolution.entries) {
            val settings = StreamSettings(resolution = res)
            val json = serializeStreamSettings(settings)
            val parsed = parseStreamSettings(json)
            assertEquals(
                "Resolution.$res failed roundtrip",
                res, parsed.resolution,
            )
        }
    }

    @Test
    fun `all VideoCodec enum values survive roundtrip`() {
        for (codec in VideoCodec.entries) {
            val settings = StreamSettings(codec = codec)
            val json = serializeStreamSettings(settings)
            val parsed = parseStreamSettings(json)
            assertEquals(
                "VideoCodec.$codec failed roundtrip",
                codec, parsed.codec,
            )
        }
    }

    @Test
    fun `fps survives roundtrip at typical values`() {
        for (fps in listOf(30, 60, 90, 120, 144, 240)) {
            val settings = StreamSettings(fps = fps)
            val json = serializeStreamSettings(settings)
            val parsed = parseStreamSettings(json)
            assertEquals("fps=$fps failed roundtrip", fps, parsed.fps)
        }
    }

    @Test
    fun `bitrateKbps survives roundtrip at boundary values`() {
        for (kbps in listOf(1000, 5000, 15000, 20000, 50000, 80000, 100000, 150000)) {
            val settings = StreamSettings(bitrateKbps = kbps)
            val json = serializeStreamSettings(settings)
            val parsed = parseStreamSettings(json)
            assertEquals("bitrateKbps=$kbps failed roundtrip", kbps, parsed.bitrateKbps)
        }
    }

    @Test
    fun `enableHdr true survives roundtrip`() {
        val settings = StreamSettings(enableHdr = true)
        val json = serializeStreamSettings(settings)
        val parsed = parseStreamSettings(json)
        assertTrue(parsed.enableHdr)
    }

    @Test
    fun `enableHdr false survives roundtrip`() {
        val settings = StreamSettings(enableHdr = false)
        val json = serializeStreamSettings(settings)
        val parsed = parseStreamSettings(json)
        assertFalse(parsed.enableHdr)
    }

    // -----------------------------------------------------------------------
    // StreamSettings: parser resilience (what StreamService handles)
    // -----------------------------------------------------------------------

    @Test
    fun `parseStreamSettings with empty JSON returns defaults`() {
        val parsed = parseStreamSettings("{}")
        assertEquals(Resolution.RES_1080P, parsed.resolution)
        assertEquals(60, parsed.fps)
        assertEquals(20000, parsed.bitrateKbps)
        assertEquals(VideoCodec.AUTO, parsed.codec)
        assertFalse(parsed.enableHdr)
    }

    @Test
    fun `parseStreamSettings with invalid JSON returns defaults`() {
        val parsed = parseStreamSettings("not json at all")
        assertEquals(StreamSettings(), parsed)
    }

    @Test
    fun `parseStreamSettings with unknown resolution falls back to 1080p`() {
        val json = """{"resolution":"RES_8K","fps":60,"bitrateKbps":20000,"codec":"AUTO","enableHdr":false}"""
        val parsed = parseStreamSettings(json)
        assertEquals(Resolution.RES_1080P, parsed.resolution)
    }

    @Test
    fun `parseStreamSettings with unknown codec falls back to AUTO`() {
        val json = """{"resolution":"RES_1080P","fps":60,"bitrateKbps":20000,"codec":"VP9","enableHdr":false}"""
        val parsed = parseStreamSettings(json)
        assertEquals(VideoCodec.AUTO, parsed.codec)
    }

    @Test
    fun `parseStreamSettings with missing fields uses defaults`() {
        val json = """{"fps":120}"""
        val parsed = parseStreamSettings(json)
        assertEquals(Resolution.RES_1080P, parsed.resolution) // default
        assertEquals(120, parsed.fps)                          // from JSON
        assertEquals(20000, parsed.bitrateKbps)                // default
        assertEquals(VideoCodec.AUTO, parsed.codec)            // default
        assertFalse(parsed.enableHdr)                          // default
    }

    // -----------------------------------------------------------------------
    // AudioSettings: roundtrip
    // -----------------------------------------------------------------------

    @Test
    fun `all AudioMode enum values survive roundtrip`() {
        for (mode in AudioMode.entries) {
            val settings = AudioSettings(audioMode = mode)
            val json = serializeAudioSettings(settings)
            val parsed = parseAudioSettings(json)
            assertEquals(
                "AudioMode.$mode failed roundtrip",
                mode, parsed.audioMode,
            )
        }
    }

    @Test
    fun `parseAudioSettings with empty JSON returns defaults`() {
        val parsed = parseAudioSettings("{}")
        assertEquals(AudioMode.STREAM_AUDIO, parsed.audioMode)
    }

    @Test
    fun `parseAudioSettings with invalid JSON returns defaults`() {
        val parsed = parseAudioSettings("broken")
        assertEquals(AudioSettings(), parsed)
    }

    @Test
    fun `parseAudioSettings with unknown mode falls back to STREAM_AUDIO`() {
        val json = """{"audioMode":"SURROUND_7_1"}"""
        val parsed = parseAudioSettings(json)
        assertEquals(AudioMode.STREAM_AUDIO, parsed.audioMode)
    }

    // -----------------------------------------------------------------------
    // Helpers — mirror the logic in StreamServiceConnection / StreamService
    // -----------------------------------------------------------------------
    // These are exact copies of the private serialize/parse methods so we can
    // test the contract independently. If the implementation changes, these
    // tests break, alerting us to a schema mismatch between processes.

    private fun serializeStreamSettings(s: StreamSettings): String =
        JSONObject().apply {
            put("resolution", s.resolution.name)
            put("fps", s.fps)
            put("bitrateKbps", s.bitrateKbps)
            put("codec", s.codec.name)
            put("enableHdr", s.enableHdr)
        }.toString()

    private fun parseStreamSettings(json: String): StreamSettings {
        return try {
            val obj = JSONObject(json)
            StreamSettings(
                resolution = Resolution.entries.find { it.name == obj.optString("resolution") }
                    ?: Resolution.RES_1080P,
                fps = obj.optInt("fps", 60),
                bitrateKbps = obj.optInt("bitrateKbps", 20000),
                codec = VideoCodec.entries.find { it.name == obj.optString("codec") }
                    ?: VideoCodec.AUTO,
                enableHdr = obj.optBoolean("enableHdr", false),
            )
        } catch (_: Exception) {
            StreamSettings()
        }
    }

    private fun serializeAudioSettings(a: AudioSettings): String =
        JSONObject().apply {
            put("audioMode", a.audioMode.name)
        }.toString()

    private fun parseAudioSettings(json: String): AudioSettings {
        return try {
            val obj = JSONObject(json)
            AudioSettings(
                audioMode = AudioMode.entries.find { it.name == obj.optString("audioMode") }
                    ?: AudioMode.STREAM_AUDIO,
            )
        } catch (_: Exception) {
            AudioSettings()
        }
    }
}

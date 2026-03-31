// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.streaming

import com.xrworkspace.app.model.AudioChannels
import com.xrworkspace.app.model.AudioMode
import com.xrworkspace.app.model.AudioSettings
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Integration tests verifying that [AudioChannels] stores the correct
 * channel count and mask values matching the MoonBridge audio configuration
 * constants (stereo=2/0x3, 5.1=6/0x3F, 7.1=8/0x63F).
 *
 * These tests run on the JVM without loading the MoonBridge native library.
 * The [AudioChannels.toMoonBridgeConfig] method is exercised only in
 * instrumented Android tests where the native library is available.
 */
class AudioIntegrationTest {

    @Test
    fun `STEREO has correct channel count and mask`() {
        assertEquals(2, AudioChannels.STEREO.channelCount)
        assertEquals(0x3, AudioChannels.STEREO.channelMask)
    }

    @Test
    fun `SURROUND_51 has correct channel count and mask`() {
        assertEquals(6, AudioChannels.SURROUND_51.channelCount)
        assertEquals(0x3F, AudioChannels.SURROUND_51.channelMask)
    }

    @Test
    fun `SURROUND_71 has correct channel count and mask`() {
        assertEquals(8, AudioChannels.SURROUND_71.channelCount)
        assertEquals(0x63F, AudioChannels.SURROUND_71.channelMask)
    }

    @Test
    fun `all AudioChannels entries have distinct channel counts`() {
        val counts = AudioChannels.entries.map { it.channelCount }
        assertEquals(counts.size, counts.toSet().size)
    }

    @Test
    fun `channel values match MAKE_AUDIO_CONFIGURATION formula`() {
        // Verify the MoonBridge MAKE_AUDIO_CONFIGURATION formula:
        // (channelMask << 16) | (channelCount << 8) | 0xCA
        val expected = (0x3 shl 16) or (2 shl 8) or 0xCA
        val actual = (AudioChannels.STEREO.channelMask shl 16) or
            (AudioChannels.STEREO.channelCount shl 8) or 0xCA
        assertEquals(expected, actual)
    }

    @Test
    fun `default AudioSettings produces stereo channel values`() {
        val settings = AudioSettings()
        assertEquals(2, settings.audioChannels.channelCount)
        assertEquals(0x3, settings.audioChannels.channelMask)
    }

    @Test
    fun `MUTED mode does not affect channel configuration`() {
        val settings = AudioSettings(
            audioMode = AudioMode.MUTED,
            audioChannels = AudioChannels.SURROUND_51,
        )
        // Channel config is still 5.1 even when muted — muting is handled
        // by the stream manager (null audio renderer), not by channel config
        assertEquals(6, settings.audioChannels.channelCount)
        assertEquals(0x3F, settings.audioChannels.channelMask)
    }

    @Test
    fun `all AudioChannels entries have distinct channel masks`() {
        val masks = AudioChannels.entries.map { it.channelMask }
        assertEquals(masks.size, masks.toSet().size)
    }
}

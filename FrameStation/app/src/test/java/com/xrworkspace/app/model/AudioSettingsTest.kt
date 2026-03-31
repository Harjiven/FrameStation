// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Unit tests for [AudioSettings], [AudioMode], and [AudioChannels].
 */
class AudioSettingsTest {

    @Test
    fun `default AudioSettings uses STREAM_AUDIO mode`() {
        val settings = AudioSettings()
        assertEquals(AudioMode.STREAM_AUDIO, settings.audioMode)
    }

    @Test
    fun `default AudioSettings uses STEREO channels`() {
        val settings = AudioSettings()
        assertEquals(AudioChannels.STEREO, settings.audioChannels)
    }

    @Test
    fun `default AudioSettings has audio effects disabled`() {
        val settings = AudioSettings()
        assertFalse(settings.enableAudioFx)
    }

    @Test
    fun `AudioMode entries have correct labels`() {
        assertEquals("Stream Audio Only", AudioMode.STREAM_AUDIO.label)
        assertEquals("Muted", AudioMode.MUTED.label)
    }

    @Test
    fun `AudioChannels entries have correct labels`() {
        assertEquals("Stereo", AudioChannels.STEREO.label)
        assertEquals("5.1 Surround", AudioChannels.SURROUND_51.label)
        assertEquals("7.1 Surround", AudioChannels.SURROUND_71.label)
    }

    @Test
    fun `AudioChannels STEREO has correct channel count and mask`() {
        assertEquals(2, AudioChannels.STEREO.channelCount)
        assertEquals(0x3, AudioChannels.STEREO.channelMask)
    }

    @Test
    fun `AudioChannels SURROUND_51 has correct channel count and mask`() {
        assertEquals(6, AudioChannels.SURROUND_51.channelCount)
        assertEquals(0x3F, AudioChannels.SURROUND_51.channelMask)
    }

    @Test
    fun `AudioChannels SURROUND_71 has correct channel count and mask`() {
        assertEquals(8, AudioChannels.SURROUND_71.channelCount)
        assertEquals(0x63F, AudioChannels.SURROUND_71.channelMask)
    }

    @Test
    fun `AudioChannels entries have correct channel counts`() {
        assertEquals(2, AudioChannels.STEREO.channelCount)
        assertEquals(6, AudioChannels.SURROUND_51.channelCount)
        assertEquals(8, AudioChannels.SURROUND_71.channelCount)
    }

    @Test
    fun `AudioSettings copy preserves unmodified fields`() {
        val original = AudioSettings(
            audioMode = AudioMode.STREAM_AUDIO,
            audioChannels = AudioChannels.SURROUND_51,
            enableAudioFx = true,
        )
        val modified = original.copy(audioMode = AudioMode.MUTED)
        assertEquals(AudioMode.MUTED, modified.audioMode)
        assertEquals(AudioChannels.SURROUND_51, modified.audioChannels)
        assertEquals(true, modified.enableAudioFx)
    }

    @Test
    fun `all AudioMode entries are accounted for`() {
        assertEquals(2, AudioMode.entries.size)
    }

    @Test
    fun `all AudioChannels entries are accounted for`() {
        assertEquals(3, AudioChannels.entries.size)
    }
}

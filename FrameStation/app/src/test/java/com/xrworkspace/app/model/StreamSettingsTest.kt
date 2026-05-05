// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [StreamSettings], [Resolution], [VideoCodec], and [recommendedBitrateKbps].
 */
class StreamSettingsTest {

    @Test
    fun `default StreamSettings uses 1080p 60fps 20Mbps Auto`() {
        val defaults = StreamSettings()
        assertEquals(Resolution.RES_1080P, defaults.resolution)
        assertEquals(60, defaults.fps)
        assertEquals(20000, defaults.bitrateKbps)
        assertEquals(VideoCodec.AUTO, defaults.codec)
    }

    @Test
    fun `Resolution enum has correct dimensions for 720p`() {
        assertEquals(1280, Resolution.RES_720P.width)
        assertEquals(720, Resolution.RES_720P.height)
        assertEquals("720p", Resolution.RES_720P.label)
    }

    @Test
    fun `Resolution enum has correct dimensions for 1080p`() {
        assertEquals(1920, Resolution.RES_1080P.width)
        assertEquals(1080, Resolution.RES_1080P.height)
        assertEquals("1080p", Resolution.RES_1080P.label)
    }

    @Test
    fun `Resolution enum has correct dimensions for 1440p`() {
        assertEquals(2560, Resolution.RES_1440P.width)
        assertEquals(1440, Resolution.RES_1440P.height)
        assertEquals("1440p", Resolution.RES_1440P.label)
    }

    @Test
    fun `Resolution enum has correct dimensions for 4K`() {
        assertEquals(3840, Resolution.RES_4K.width)
        assertEquals(2160, Resolution.RES_4K.height)
        assertEquals("4K", Resolution.RES_4K.label)
    }

    @Test
    fun `Resolution enum has exactly 4 entries`() {
        assertEquals(4, Resolution.entries.size)
    }

    @Test
    fun `VideoCodec enum has correct labels`() {
        assertEquals("Auto (best available)", VideoCodec.AUTO.label)
        assertEquals("H.264 only", VideoCodec.H264.label)
        assertEquals("H.265 only", VideoCodec.H265.label)
        assertEquals("AV1 SDR (Android 10+)", VideoCodec.AV1_MAIN8.label)
        assertEquals("AV1 10-bit (Android 10+)", VideoCodec.AV1_MAIN10.label)
    }

    @Test
    fun `VideoCodec enum has exactly 5 entries`() {
        assertEquals(5, VideoCodec.entries.size)
    }

    @Test
    fun `recommendedBitrateKbps for 1080p at 60fps`() {
        // 1920 * 1080 * 60 * 0.03 / 1000 = 3732 (AUTO assumes H.265 efficiency)
        val bitrate = recommendedBitrateKbps(Resolution.RES_1080P, 60)
        assertEquals(3732, bitrate)
    }

    @Test
    fun `recommendedBitrateKbps for 4K at 60fps`() {
        // 3840 * 2160 * 60 * 0.03 / 1000 = 14929
        val bitrate = recommendedBitrateKbps(Resolution.RES_4K, 60)
        assertEquals(14929, bitrate)
    }

    @Test
    fun `recommendedBitrateKbps for 720p at 30fps`() {
        // 1280 * 720 * 30 * 0.03 / 1000 = 829 → clamped to minimum 1000
        val bitrate = recommendedBitrateKbps(Resolution.RES_720P, 30)
        assertEquals(1000, bitrate)
    }

    @Test
    fun `recommendedBitrateKbps for 4K at 120fps`() {
        // 3840 * 2160 * 120 * 0.03 / 1000 = 29859 (under 100000 cap)
        val bitrate = recommendedBitrateKbps(Resolution.RES_4K, 120)
        assertEquals(29859, bitrate)
    }

    @Test
    fun `recommendedBitrateKbps never goes below 1000`() {
        // Even with smallest resolution and lowest fps, should be >= 1000
        val bitrate = recommendedBitrateKbps(Resolution.RES_720P, 30)
        assert(bitrate >= 1000) { "Expected bitrate >= 1000, got $bitrate" }
    }

    @Test
    fun `StreamSettings data class equality`() {
        val a = StreamSettings(Resolution.RES_1080P, 60, 20000, VideoCodec.AUTO)
        val b = StreamSettings(Resolution.RES_1080P, 60, 20000, VideoCodec.AUTO)
        assertEquals(a, b)
    }

    @Test
    fun `StreamSettings copy preserves unchanged fields`() {
        val original = StreamSettings()
        val modified = original.copy(fps = 30)
        assertEquals(Resolution.RES_1080P, modified.resolution)
        assertEquals(30, modified.fps)
        assertEquals(20000, modified.bitrateKbps)
        assertEquals(VideoCodec.AUTO, modified.codec)
    }
}

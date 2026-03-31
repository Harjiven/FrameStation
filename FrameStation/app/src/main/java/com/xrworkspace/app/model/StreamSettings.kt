// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.model

/**
 * User-configurable stream quality settings for Moonlight streaming.
 */
data class StreamSettings(
    val resolution: Resolution = Resolution.RES_1080P,
    val fps: Int = 60,
    val bitrateKbps: Int = 20000,
    val codec: VideoCodec = VideoCodec.AUTO,
)

/**
 * Supported stream resolutions with pixel dimensions and display labels.
 */
enum class Resolution(val width: Int, val height: Int, val label: String) {
    RES_720P(1280, 720, "720p"),
    RES_1080P(1920, 1080, "1080p"),
    RES_1440P(2560, 1440, "1440p"),
    RES_4K(3840, 2160, "4K"),
}

/**
 * Video codec preference for the stream.
 */
enum class VideoCodec(val label: String) {
    AUTO("Auto (H.264/H.265)"),
    H264("H.264 only"),
    H265("H.265 only"),
}

/**
 * Calculates a recommended bitrate (kbps) for the given resolution and FPS.
 * Formula: width * height * fps * 0.04 / 1000, clamped to [1000, 100000].
 */
fun recommendedBitrateKbps(resolution: Resolution, fps: Int): Int {
    val raw = (resolution.width.toLong() * resolution.height * fps * 0.04 / 1000).toInt()
    return raw.coerceIn(1000, 100000)
}

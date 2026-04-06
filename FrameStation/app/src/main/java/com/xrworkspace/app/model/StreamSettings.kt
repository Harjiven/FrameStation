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
    /** Enable 10-bit HDR streaming (H.265 Main10 / AV1 Main10, BT.2020 color space). */
    val enableHdr: Boolean = false,
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
    AUTO("Auto (best available)"),
    H264("H.264 only"),
    H265("H.265 only"),
    AV1_MAIN8("AV1 SDR (Android 10+)"),
    AV1_MAIN10("AV1 10-bit (Android 10+)"),
}

/**
 * Calculates a recommended bitrate (kbps) for the given resolution, FPS, and codec.
 * AV1 is ~40% more efficient than H.264; H.265 is ~30% more efficient than H.264.
 * Formula: width * height * fps * multiplier / 1000, clamped to [1000, 100000].
 */
fun recommendedBitrateKbps(
    resolution: Resolution,
    fps: Int,
    codec: VideoCodec = VideoCodec.AUTO,
): Int {
    val multiplier = when (codec) {
        VideoCodec.H264 -> 0.04
        VideoCodec.AUTO, VideoCodec.H265 -> 0.03       // assume H.265 for AUTO
        VideoCodec.AV1_MAIN8, VideoCodec.AV1_MAIN10 -> 0.024
    }
    val raw = (resolution.width.toLong() * resolution.height * fps * multiplier / 1000).toInt()
    return raw.coerceIn(1000, 100000)
}

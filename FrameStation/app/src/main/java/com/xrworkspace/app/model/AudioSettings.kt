// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.model

import com.limelight.nvstream.jni.MoonBridge

/**
 * User-configurable audio settings for the Moonlight stream.
 */
data class AudioSettings(
    val audioMode: AudioMode = AudioMode.STREAM_AUDIO,
    val audioChannels: AudioChannels = AudioChannels.STEREO,
    val enableAudioFx: Boolean = false,
)

/**
 * Controls how audio is handled during streaming.
 */
enum class AudioMode(val label: String) {
    /** Default — play audio from the Moonlight stream. */
    STREAM_AUDIO("Stream Audio Only"),
    /** Mute all stream audio. */
    MUTED("Muted"),
}

/**
 * Audio channel configuration storing primitive channel count and mask values.
 *
 * The enum intentionally avoids holding a [MoonBridge.AudioConfiguration] reference
 * so that it can be loaded in JVM unit tests where the MoonBridge native library is
 * unavailable. Use [toMoonBridgeConfig] at runtime to obtain the MoonBridge object.
 */
enum class AudioChannels(
    val label: String,
    val channelCount: Int,
    val channelMask: Int,
) {
    STEREO("Stereo", 2, 0x3),
    SURROUND_51("5.1 Surround", 6, 0x3F),
    SURROUND_71("7.1 Surround", 8, 0x63F),
    ;

    /** Convert to [MoonBridge.AudioConfiguration]. Call only from Android runtime. */
    fun toMoonBridgeConfig(): MoonBridge.AudioConfiguration {
        return MoonBridge.AudioConfiguration(channelCount, channelMask)
    }
}

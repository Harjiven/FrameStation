// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.streaming

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.Spatializer
import android.os.Build
import android.util.Log
import com.limelight.nvstream.av.audio.AudioRenderer
import com.limelight.nvstream.jni.MoonBridge

/**
 * An [AudioRenderer] that creates an [AudioTrack] configured for Android XR
 * panel-anchored spatial audio.
 *
 * On Android XR, the platform spatializer automatically positions audio from a
 * `SpatialPanel`'s content at the panel's world-space location — but only when:
 * 1. The audio uses `USAGE_MEDIA` (not `USAGE_GAME`, which is head-locked).
 * 2. The performance mode allows spatialization processing (not `LOW_LATENCY`,
 *    which bypasses the spatializer pipeline).
 * 3. Spatialization behavior is set to AUTO (API 32+).
 *
 * This renderer satisfies all three conditions so the stream audio tracks the
 * panel position as the user moves it around the XR space.
 */
class SpatialAudioRenderer(
    private val context: Context,
) : AudioRenderer {

    companion object {
        private const val TAG = "SpatialAudioRenderer"
    }

    private var track: AudioTrack? = null

    /** When `true`, [playDecodedAudio] silently drops all samples. Thread-safe via volatile. */
    @Volatile
    var isMuted: Boolean = false

    override fun setup(
        audioConfiguration: MoonBridge.AudioConfiguration,
        sampleRate: Int,
        samplesPerFrame: Int,
    ): Int {
        val channelConfig = when (audioConfiguration.channelCount) {
            2 -> AudioFormat.CHANNEL_OUT_STEREO
            4 -> AudioFormat.CHANNEL_OUT_QUAD
            6 -> AudioFormat.CHANNEL_OUT_5POINT1
            8 -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2) {
                AudioFormat.CHANNEL_OUT_7POINT1_SURROUND
            } else {
                0x000018fc // CHANNEL_OUT_7POINT1_SURROUND pre-API 32
            }
            else -> {
                Log.e(TAG, "Unhandled channel count: ${audioConfiguration.channelCount}")
                return -1
            }
        }

        val bytesPerFrame = audioConfiguration.channelCount * samplesPerFrame * 2
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(channelConfig)
            .build()

        // USAGE_GAME tells the XR runtime to spatialize this audio relative to
        // the app's spatial panels. On Android XR in Full Space mode, USAGE_GAME
        // audio is positioned at the panel that produced it.
        // We avoid PERFORMANCE_MODE_LOW_LATENCY (set below) so the spatializer
        // pipeline can actually process positional rendering.
        val attributesBuilder = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)

        // Request automatic spatialization on API 32+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2) {
            attributesBuilder.setSpatializationBehavior(1) // SPATIALIZER_BEHAVIOR_AUTO
        }

        val attributes = attributesBuilder.build()
        logSpatializerState()

        // Try progressively larger buffers until one works.
        // Use PERFORMANCE_MODE_NONE (not LOW_LATENCY) so the platform spatializer
        // can process the audio for positional rendering.
        for (attempt in 0 until 4) {
            val bufferSize = when (attempt) {
                0 -> bytesPerFrame * 4       // Small but spatializer-compatible
                1 -> bytesPerFrame * 8
                else -> {
                    val minBuffer = AudioTrack.getMinBufferSize(
                        sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT,
                    )
                    if (attempt == 2) minBuffer else minBuffer * 2
                }
            }

            try {
                val newTrack = AudioTrack.Builder()
                    .setAudioFormat(format)
                    .setAudioAttributes(attributes)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(bufferSize)
                    // Use NONE instead of LOW_LATENCY so the spatializer pipeline
                    // can intercept and position the audio in 3D space.
                    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_NONE)
                    .build()

                newTrack.play()
                track = newTrack
                Log.i(TAG, "AudioTrack created: ${audioConfiguration.channelCount}ch, " +
                    "${sampleRate}Hz, buf=$bufferSize, perfMode=NONE (spatializer-compatible)")
                return 0
            } catch (e: Exception) {
                Log.w(TAG, "AudioTrack creation attempt $attempt failed", e)
            }
        }

        Log.e(TAG, "Failed to create AudioTrack after all attempts")
        return -2
    }

    override fun start() {
        // No-op — track.play() is called in setup()
    }

    override fun stop() {
        // No-op — cleanup handles release
    }

    override fun playDecodedAudio(audioData: ShortArray) {
        if (isMuted) return

        val t = track ?: return
        // Only queue up to 60ms of pending audio data.
        // Slightly higher than the 40ms used with low-latency mode to account for
        // the spatializer processing overhead.
        if (MoonBridge.getPendingAudioDuration() < 60) {
            t.write(audioData, 0, audioData.size)
        }
    }

    override fun cleanup() {
        track?.let {
            try {
                it.pause()
                it.flush()
                it.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error cleaning up AudioTrack", e)
            }
        }
        track = null
    }

    private fun logSpatializerState() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2) {
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                val spatializer = audioManager?.spatializer
                if (spatializer != null) {
                    val level = spatializer.immersiveAudioLevel
                    val enabled = spatializer.isEnabled
                    Log.i(TAG, "Spatializer: enabled=$enabled, immersiveLevel=$level")
                } else {
                    Log.i(TAG, "Spatializer: not available")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to query Spatializer", e)
            }
        }
    }
}

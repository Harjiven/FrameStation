// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.streaming

import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteCallbackList
import android.util.Log
import android.view.Surface
import com.xrworkspace.app.model.AudioMode
import com.xrworkspace.app.model.AudioSettings
import com.xrworkspace.app.model.Resolution
import com.xrworkspace.app.model.StreamSettings
import com.xrworkspace.app.model.VideoCodec
import org.json.JSONObject

/**
 * Android Service that runs in an isolated process (:stream0 or :stream1).
 *
 * Each process gets its own copy of libmoonlight-core.so with independent C globals
 * (StreamConfig, VideoCallbacks, AudioDecoder, etc.), enabling two simultaneous
 * Moonlight streams without native library conflicts.
 *
 * The UI process binds to this service via [IStreamService] AIDL and passes a
 * [Surface] obtained from SpatialExternalSurface. MediaCodec in this process renders
 * video directly to that Surface across the Binder boundary.
 */
open class StreamService : Service() {

    companion object {
        private const val TAG = "FrameStation-StreamSvc"
    }

    private lateinit var prefs: SharedPreferences
    @Volatile private var streamManager: MoonlightStreamManager? = null
    private val managerLock = Any()

    // Reused across startStream() calls so we don't allocate (and leak) a Handler
    // every time the UI process kicks off a new stream.
    private val mainHandler = Handler(Looper.getMainLooper())

    // RemoteCallbackList safely handles death of client processes
    private val clients = RemoteCallbackList<IStreamServiceClient>()

    private val binder = object : IStreamService.Stub() {

        override fun startStream(
            serverAddress: String,
            surface: Surface,
            streamSettingsJson: String,
            audioSettingsJson: String,
        ) {
            synchronized(managerLock) {
                if (streamManager != null) {
                    Log.w(TAG, "startStream() called while already streaming — ignoring")
                    return
                }
            }
            Log.i(TAG, "startStream: $serverAddress (pid=${android.os.Process.myPid()})")
            val streamSettings = parseStreamSettings(streamSettingsJson)
            val audioSettings = parseAudioSettings(audioSettingsJson)

            // MoonlightStreamManager must be created on the main thread (Android API requirement)
            mainHandler.post {
                synchronized(managerLock) {
                    if (streamManager != null) return@post  // stopStream() raced with us
                    val manager = MoonlightStreamManager(applicationContext, prefs)
                    manager.onStageChanged = { stage -> broadcastStageChanged(stage) }
                    manager.onConnectionStarted = { broadcastConnectionStarted() }
                    manager.onConnectionTerminated = { reason -> broadcastConnectionTerminated(reason) }
                    manager.applyStreamSettings(streamSettings)
                    manager.applyAudioSettings(audioSettings)
                    streamManager = manager
                    manager.startStream(serverAddress, surface)
                }
            }
        }

        override fun stopStream() {
            Log.i(TAG, "stopStream")
            val manager = synchronized(managerLock) {
                val m = streamManager
                streamManager = null
                m
            }
            manager?.stopStream()
        }

        override fun sendMousePosition(x: Int, y: Int, streamWidth: Int, streamHeight: Int) {
            streamManager?.sendMousePosition(
                x.toShort(), y.toShort(),
                streamWidth.toShort(), streamHeight.toShort(),
            )
        }

        override fun sendMouseButtonDown(button: Int) {
            streamManager?.sendMouseButtonDown(button.toByte())
        }

        override fun sendMouseButtonUp(button: Int) {
            streamManager?.sendMouseButtonUp(button.toByte())
        }

        override fun sendMouseScroll(amount: Int) {
            streamManager?.sendMouseScroll(amount.toByte())
        }

        override fun sendKeyboardInput(keyMap: Int, direction: Int, modifiers: Int) {
            streamManager?.sendKeyboardInput(
                keyMap.toShort(), direction.toByte(), modifiers.toByte(), 0.toByte(),
            )
        }

        override fun sendControllerInput(
            buttonFlags: Int,
            leftTrigger: Int,
            rightTrigger: Int,
            leftStickX: Int,
            leftStickY: Int,
            rightStickX: Int,
            rightStickY: Int,
        ) {
            streamManager?.sendControllerInput(
                buttonFlags,
                (leftTrigger and 0xFF).toByte(),   // mask before cast: 200 stays 200, not -56
                (rightTrigger and 0xFF).toByte(),
                leftStickX.toShort(),
                leftStickY.toShort(),
                rightStickX.toShort(),
                rightStickY.toShort(),
            )
        }

        override fun registerClient(client: IStreamServiceClient) {
            clients.register(client)
        }

        override fun unregisterClient(client: IStreamServiceClient) {
            clients.unregister(client)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("framestation_prefs", MODE_PRIVATE)
        Log.i(TAG, "StreamService created in process ${android.os.Process.myPid()}")
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        // Drain any pending startStream callbacks before tearing down so they don't
        // try to create a manager against a dead service.
        mainHandler.removeCallbacksAndMessages(null)
        streamManager?.stopStream()
        streamManager = null
        clients.kill()
        Log.i(TAG, "StreamService destroyed")
    }

    // --- Broadcast helpers ---

    private fun broadcastStageChanged(stage: String) {
        val n = clients.beginBroadcast()
        for (i in 0 until n) {
            try { clients.getBroadcastItem(i).onStageChanged(stage) } catch (_: Exception) {}
        }
        clients.finishBroadcast()
    }

    private fun broadcastConnectionStarted() {
        val n = clients.beginBroadcast()
        for (i in 0 until n) {
            try { clients.getBroadcastItem(i).onConnectionStarted() } catch (_: Exception) {}
        }
        clients.finishBroadcast()
    }

    private fun broadcastConnectionTerminated(reason: String?) {
        val n = clients.beginBroadcast()
        for (i in 0 until n) {
            try { clients.getBroadcastItem(i).onConnectionTerminated(reason ?: "") } catch (_: Exception) {}
        }
        clients.finishBroadcast()
    }

    // --- JSON deserializers ---

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

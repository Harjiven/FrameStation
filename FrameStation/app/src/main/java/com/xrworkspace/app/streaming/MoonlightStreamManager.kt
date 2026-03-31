// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.streaming

import android.app.Activity
import android.content.SharedPreferences
import android.util.Log
import android.view.SurfaceHolder
import com.limelight.binding.PlatformBinding
import com.limelight.binding.audio.AndroidAudioRenderer
import com.limelight.binding.video.CrashListener
import com.limelight.binding.video.MediaCodecDecoderRenderer
import com.limelight.binding.video.MediaCodecHelper
import com.limelight.binding.video.PerfOverlayListener
import com.limelight.nvstream.NvConnection
import com.limelight.nvstream.NvConnectionListener
import com.limelight.nvstream.StreamConfiguration
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.jni.MoonBridge
import com.limelight.preferences.PreferenceConfiguration
import java.lang.ref.WeakReference
import java.security.cert.X509Certificate

/**
 * Manages the full Moonlight streaming lifecycle.
 * Bridges the XR UI layer and the Moonlight core library.
 */
class MoonlightStreamManager(
    activity: Activity,
    private val prefs: SharedPreferences,
) : NvConnectionListener {
    companion object {
        private const val TAG = "FrameStation-Stream"
        private const val HTTP_PORT = 47989    // GameStream HTTP control port
        private const val HTTPS_PORT = 47984   // GameStream HTTPS control port
    }

    private val activityRef = WeakReference(activity)
    private val dataDir = activity.filesDir
    private val streamLock = Any()

    private var connection: NvConnection? = null
    private var videoRenderer: MediaCodecDecoderRenderer? = null
    private var audioRenderer: AndroidAudioRenderer? = null

    var onStageChanged: ((String) -> Unit)? = null
    var onConnectionStarted: (() -> Unit)? = null
    var onConnectionTerminated: ((String?) -> Unit)? = null

    // Stream config defaults
    var streamWidth: Int = 1920
    var streamHeight: Int = 1080
    var streamFps: Int = 60
    var streamBitrate: Int = 20000 // kbps

    /**
     * Start streaming to the given server address.
     */
    fun startStream(
        serverAddress: String,
        surfaceHolder: SurfaceHolder,
        serverCert: X509Certificate? = null,
    ) {
        Thread {
            synchronized(streamLock) {
                try {
                    Log.i(TAG, "Starting stream to $serverAddress")

                    val activity = activityRef.get() ?: run {
                        Log.w(TAG, "Activity gone — aborting stream start")
                        return@synchronized
                    }

                    // Initialize MediaCodecHelper (must be called before creating the renderer)
                    MediaCodecHelper.initialize(activity, "")

                    // Create crypto provider
                    val cryptoProvider = PlatformBinding.getCryptoProvider(dataDir)

                    // HTTP port for the base URL, HTTPS port for secure operations
                    val addressTuple = ComputerDetails.AddressTuple(serverAddress, HTTP_PORT)

                    // Load saved server cert from disk (saved after pairing by ServerManager)
                    val serverManager = ServerManager(dataDir, prefs)
                    val cert: X509Certificate? = serverCert ?: serverManager.loadServerCert()
                    Log.i(TAG, "Server cert: ${if (cert != null) "loaded (${cert.subjectDN})" else "NOT FOUND — pair first via the Pair button"}")

                    // Get the app list and find "Desktop" or use the first app
                    Log.i(TAG, "Fetching app list...")
                    activity.runOnUiThread { onStageChanged?.invoke("Fetching apps...") }
                    val uniqueId = ServerManager.getUniqueId(prefs)
                    val nvhttp = NvHTTP(addressTuple, HTTPS_PORT, uniqueId, cert, cryptoProvider)
                    val serverInfo = nvhttp.getServerInfo(true)
                    val appList = nvhttp.getAppList()

                    val desktopApp = appList.firstOrNull { it.appName.equals("Desktop", ignoreCase = true) }
                        ?: appList.firstOrNull()

                    if (desktopApp == null) {
                        Log.e(TAG, "No apps found on server!")
                        activity.runOnUiThread {
                            onConnectionTerminated?.invoke("No apps found on server")
                        }
                        return@synchronized
                    }
                    Log.i(TAG, "Streaming app: ${desktopApp.appName} (ID: ${desktopApp.appId})")

                    // Build stream configuration WITH the app
                    val streamConfig = StreamConfiguration.Builder()
                        .setResolution(streamWidth, streamHeight)
                        .setRefreshRate(streamFps)
                        .setLaunchRefreshRate(streamFps)
                        .setBitrate(streamBitrate)
                        .setApp(desktopApp)
                        .setMaxPacketSize(1392)
                        .setRemoteConfiguration(StreamConfiguration.STREAM_CFG_AUTO)
                        .setAudioConfiguration(MoonBridge.AUDIO_CONFIGURATION_STEREO)
                        .setSupportedVideoFormats(
                            MoonBridge.VIDEO_FORMAT_H264 or MoonBridge.VIDEO_FORMAT_H265
                        )
                        .build()

                    // Create connection
                    connection = NvConnection(
                        addressTuple,
                        HTTPS_PORT,
                        uniqueId,
                        streamConfig,
                        cryptoProvider,
                        cert,
                    )

                    // Build preferences for the video renderer
                    val rendererPrefs = PreferenceConfiguration().apply {
                        width = streamWidth
                        height = streamHeight
                        fps = streamFps
                        bitrate = streamBitrate
                        videoFormat = PreferenceConfiguration.FormatOption.AUTO
                        framePacing = PreferenceConfiguration.FRAME_PACING_BALANCED
                        enableHdr = false
                        enablePerfOverlay = false
                        absoluteMouseMode = true
                        audioConfiguration = MoonBridge.AUDIO_CONFIGURATION_STEREO
                        enableAudioFx = false
                    }

                    // Create video decoder/renderer
                    videoRenderer = MediaCodecDecoderRenderer(
                        activity,
                        rendererPrefs,
                        object : CrashListener {
                            override fun notifyCrash(e: Exception) {
                                Log.e(TAG, "Renderer crash", e)
                            }
                        },
                        0,     // consecutiveCrashCount
                        false, // meteredData
                        false, // requestedHdr
                        "",    // glRenderer
                        object : PerfOverlayListener {
                            override fun onPerfUpdate(text: String) {
                                Log.d(TAG, "Perf: $text")
                            }
                        },
                    )

                    // Set the SurfaceHolder as the render target
                    videoRenderer?.setRenderTarget(surfaceHolder)

                    // Create audio renderer
                    audioRenderer = AndroidAudioRenderer(activity, rendererPrefs.enableAudioFx)

                    // Start the connection — this drives the streaming pipeline
                    Log.i(TAG, "Starting NvConnection...")
                    activity.runOnUiThread { onStageChanged?.invoke("Connecting...") }
                    connection?.start(audioRenderer, videoRenderer, this)

                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start stream", e)
                    activityRef.get()?.runOnUiThread {
                        onConnectionTerminated?.invoke("Failed: ${e.message}")
                    }
                }
            }
        }.start()
    }

    fun stopStream() {
        synchronized(streamLock) {
            Log.i(TAG, "Stopping stream")
            try {
                connection?.stop()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping connection", e)
            }
            connection = null
            videoRenderer = null
            audioRenderer = null
        }
    }

    // --- Input forwarding ---

    fun sendMousePosition(x: Short, y: Short, refWidth: Short, refHeight: Short) {
        connection?.sendMousePosition(x, y, refWidth, refHeight)
    }

    fun sendMouseButtonDown(button: Byte) {
        connection?.sendMouseButtonDown(button)
    }

    fun sendMouseButtonUp(button: Byte) {
        connection?.sendMouseButtonUp(button)
    }

    fun sendMouseScroll(scrollClicks: Byte) {
        connection?.sendMouseScroll(scrollClicks)
    }

    fun sendKeyboardInput(keyMap: Short, keyDirection: Byte, modifier: Byte, flags: Byte) {
        connection?.sendKeyboardInput(keyMap, keyDirection, modifier, flags)
    }

    fun sendUtf8Text(text: String) {
        connection?.sendUtf8Text(text)
    }

    // --- NvConnectionListener implementation ---

    override fun stageStarting(stage: String) {
        Log.i(TAG, "Stage starting: $stage")
        activityRef.get()?.runOnUiThread { onStageChanged?.invoke("Starting: $stage") }
    }

    override fun stageComplete(stage: String) {
        Log.i(TAG, "Stage complete: $stage")
    }

    override fun stageFailed(stage: String, portFlags: Int, errorCode: Int) {
        Log.e(TAG, "Stage FAILED: $stage (port=$portFlags, error=$errorCode)")
        activityRef.get()?.runOnUiThread {
            onConnectionTerminated?.invoke("Failed at stage: $stage (error $errorCode)")
        }
    }

    override fun connectionStarted() {
        Log.i(TAG, "Connection started — streaming!")
        activityRef.get()?.runOnUiThread { onConnectionStarted?.invoke() }
    }

    override fun connectionTerminated(errorCode: Int) {
        Log.i(TAG, "Connection terminated: $errorCode")
        activityRef.get()?.runOnUiThread {
            onConnectionTerminated?.invoke(
                if (errorCode != 0) "Connection lost (error $errorCode)" else null,
            )
        }
    }

    override fun connectionStatusUpdate(connectionStatus: Int) {
        Log.d(TAG, "Connection status: $connectionStatus")
    }

    override fun displayMessage(message: String) {
        Log.i(TAG, "Display message: $message")
        activityRef.get()?.runOnUiThread { onStageChanged?.invoke(message) }
    }

    override fun displayTransientMessage(message: String) {
        Log.i(TAG, "Transient message: $message")
    }

    override fun rumble(controllerNumber: Short, lowFreqMotor: Short, highFreqMotor: Short) {}
    override fun rumbleTriggers(controllerNumber: Short, leftTrigger: Short, rightTrigger: Short) {}
    override fun setHdrMode(enabled: Boolean, hdrMetadata: ByteArray?) {}
    override fun setMotionEventState(controllerNumber: Short, motionType: Byte, reportRateHz: Short) {}
    override fun setControllerLED(controllerNumber: Short, r: Byte, g: Byte, b: Byte) {}
}

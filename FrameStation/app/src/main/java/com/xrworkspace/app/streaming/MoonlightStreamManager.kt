// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.streaming

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import com.limelight.binding.PlatformBinding
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
import com.xrworkspace.app.model.AudioMode
import com.xrworkspace.app.model.AudioSettings
import com.xrworkspace.app.model.ServerApp
import com.xrworkspace.app.model.StreamSettings
import com.xrworkspace.app.model.VideoCodec
import java.security.cert.X509Certificate

/**
 * Manages the full Moonlight streaming lifecycle.
 * Bridges the XR UI layer and the Moonlight core library.
 */
class MoonlightStreamManager(
    context: Context,
    private val prefs: SharedPreferences,
) : NvConnectionListener {
    companion object {
        private const val TAG = "FrameStation-Stream"
        private const val HTTP_PORT = 47989    // GameStream HTTP control port
        private const val HTTPS_PORT = 47984   // GameStream HTTPS control port
    }

    // applicationContext is a process singleton — it will never be GC'd, so a strong reference is safe.
    // WeakReference here was overly defensive and would cause spurious "Context gone" aborts.
    private val appContext = context.applicationContext
    private val dataDir = context.filesDir
    private val mainHandler = Handler(Looper.getMainLooper())
    private val streamLock = Any()

    private var connection: NvConnection? = null
    private var videoRenderer: MediaCodecDecoderRenderer? = null
    private var audioRenderer: SpatialAudioRenderer? = null

    /** Reference to the stream startup thread so it can be interrupted on cleanup. */
    @Volatile private var streamThread: Thread? = null

    var onStageChanged: ((String) -> Unit)? = null
    var onConnectionStarted: (() -> Unit)? = null
    var onConnectionTerminated: ((String?) -> Unit)? = null

    // Stream config defaults
    var streamWidth: Int = 1920
    var streamHeight: Int = 1080
    var streamFps: Int = 60
    var streamBitrate: Int = 20000 // kbps
    var streamCodec: VideoCodec = VideoCodec.AUTO

    // Audio config — applied via applyAudioSettings() before starting a stream
    private var audioSettings: AudioSettings = AudioSettings()

    // Last connection parameters — used for reconnection
    private var lastServerAddress: String? = null
    private var lastSurface: Surface? = null
    private var lastServerCert: X509Certificate? = null

    /** Whether a stream is currently active (connected and not terminated). */
    var isStreamingActive: Boolean = false
        private set

    /**
     * Set to `true` when the user intentionally stops the stream.
     * Allows [connectionTerminated] to distinguish intentional stops from network drops.
     */
    private var intentionalStop: Boolean = false

    /**
     * Apply user-configured stream settings before starting a stream.
     */
    fun applyStreamSettings(settings: StreamSettings) {
        streamWidth = settings.resolution.width
        streamHeight = settings.resolution.height
        streamFps = settings.fps
        streamBitrate = settings.bitrateKbps
        streamCodec = settings.codec
    }

    /**
     * Apply user-configured audio settings before starting a stream.
     */
    fun applyAudioSettings(settings: AudioSettings) {
        audioSettings = settings
    }

    /**
     * Fetch the list of apps available on the server without starting a stream.
     * Must be called from a background thread (e.g. [Dispatchers.IO]).
     */
    fun fetchAppList(serverAddress: String): List<ServerApp> {
        val serverManager = ServerManager(dataDir, prefs)
        return serverManager.getAppList(serverAddress).getOrThrow()
    }

    /**
     * Start streaming to the given server address.
     * @param appId If provided, streams the app with this ID. If null, falls back to "Desktop" or the first app.
     */
    fun startStream(
        serverAddress: String,
        surface: Surface,
        serverCert: X509Certificate? = null,
        appId: Int? = null,
    ) {
        // Store connection parameters for potential reconnection
        lastServerAddress = serverAddress
        lastSurface = surface
        lastServerCert = serverCert
        intentionalStop = false

        val thread = Thread {
            synchronized(streamLock) {
                try {
                    Log.i(TAG, "Starting stream to $serverAddress")

                    val context = appContext

                    // Initialize MediaCodecHelper (must be called before creating the renderer)
                    MediaCodecHelper.initialize(context, "")

                    // Create crypto provider
                    val cryptoProvider = PlatformBinding.getCryptoProvider(dataDir)

                    // HTTP port for the base URL, HTTPS port for secure operations
                    val addressTuple = ComputerDetails.AddressTuple(serverAddress, HTTP_PORT)

                    // Load saved server cert from disk (saved after pairing by ServerManager)
                    val serverManager = ServerManager(dataDir, prefs)
                    val cert: X509Certificate? = serverCert ?: serverManager.loadServerCert()
                    Log.i(TAG, "Server cert: ${if (cert != null) "loaded (${cert.subjectDN})" else "NOT FOUND — pair first via the Pair button"}")

                    // Get the app list and resolve which app to stream
                    Log.i(TAG, "Fetching app list...")
                    mainHandler.post { onStageChanged?.invoke("Fetching apps...") }
                    val uniqueId = ServerManager.getUniqueId(prefs)
                    val nvhttp = NvHTTP(addressTuple, HTTPS_PORT, uniqueId, cert, cryptoProvider)
                    val serverInfo = nvhttp.getServerInfo(true)
                    val appList = nvhttp.getAppList()

                    // If an appId was specified, find that app; otherwise fall back to "Desktop" or first
                    val desktopApp = if (appId != null) {
                        appList.firstOrNull { it.appId == appId }
                            ?: appList.firstOrNull { it.appName.equals("Desktop", ignoreCase = true) }
                            ?: appList.firstOrNull()
                    } else {
                        appList.firstOrNull { it.appName.equals("Desktop", ignoreCase = true) }
                            ?: appList.firstOrNull()
                    }

                    if (desktopApp == null) {
                         Log.e(TAG, "No apps found on server!")
                         mainHandler.post {
                             onConnectionTerminated?.invoke("No apps found on server")
                         }
                         return@synchronized
                     }
                    Log.i(TAG, "Streaming app: ${desktopApp.appName} (ID: ${desktopApp.appId})")

                    // Map codec preference to Moonlight video format flags
                    val videoFormats = when (streamCodec) {
                        VideoCodec.AUTO -> MoonBridge.VIDEO_FORMAT_H264 or MoonBridge.VIDEO_FORMAT_H265
                        VideoCodec.H264 -> MoonBridge.VIDEO_FORMAT_H264
                        VideoCodec.H265 -> MoonBridge.VIDEO_FORMAT_H265
                    }

                    // Resolve audio configuration from user settings
                    val audioConfig = audioSettings.audioChannels.toMoonBridgeConfig()

                    // Build stream configuration WITH the app
                    val streamConfig = StreamConfiguration.Builder()
                        .setResolution(streamWidth, streamHeight)
                        .setRefreshRate(streamFps)
                        .setLaunchRefreshRate(streamFps)
                        .setBitrate(streamBitrate)
                        .setApp(desktopApp)
                        .setMaxPacketSize(1392)
                        .setRemoteConfiguration(StreamConfiguration.STREAM_CFG_AUTO)
                        .setAudioConfiguration(audioConfig)
                        .setSupportedVideoFormats(videoFormats)
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
                        videoFormat = when (streamCodec) {
                            VideoCodec.AUTO -> PreferenceConfiguration.FormatOption.AUTO
                            VideoCodec.H264 -> PreferenceConfiguration.FormatOption.FORCE_H264
                            VideoCodec.H265 -> PreferenceConfiguration.FormatOption.FORCE_HEVC
                        }
                        framePacing = PreferenceConfiguration.FRAME_PACING_BALANCED
                        enableHdr = false
                        enablePerfOverlay = false
                        absoluteMouseMode = true
                        audioConfiguration = audioConfig
                        enableAudioFx = audioSettings.enableAudioFx
                    }

                    // Create video decoder/renderer
                    videoRenderer = MediaCodecDecoderRenderer(
                        context,
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
                                Log.v(TAG, "Perf: $text")
                            }
                        },
                    )

                    // Set the Surface as the render target (SpatialExternalSurface path)
                    videoRenderer?.setRenderTarget(surface)

                    // Create spatial audio renderer with runtime mute toggle.
                    // Uses USAGE_MEDIA + CONTENT_TYPE_MOVIE so the XR runtime spatializes
                    // audio relative to the panel's world position, not head-locked.
                    val isMuted = audioSettings.audioMode == AudioMode.MUTED
                    Log.i(TAG, "Audio mode: ${audioSettings.audioMode.label}, channels: ${audioSettings.audioChannels.label}, fx: ${audioSettings.enableAudioFx}")
                    audioRenderer = SpatialAudioRenderer(context).apply {
                        this.isMuted = isMuted
                    }

                    // Start the connection — this drives the streaming pipeline
                    Log.i(TAG, "Starting NvConnection...")
                    mainHandler.post { onStageChanged?.invoke("Connecting...") }
                    connection?.start(audioRenderer, videoRenderer, this)

                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start stream", e)
                    mainHandler.post {
                        onConnectionTerminated?.invoke("Failed: ${e.message}")
                    }
                } finally {
                    streamThread = null
                }
            }
        }
        thread.name = "FrameStation-StreamThread"
        streamThread = thread
        thread.start()
    }

    /**
     * Toggle audio mute state at runtime without restarting the stream.
     * When muted, audio samples are silently discarded.
     */
    fun setMuted(muted: Boolean) {
        val renderer = audioRenderer
        if (renderer != null) {
            renderer.isMuted = muted
            Log.i(TAG, "Audio muted: $muted")
        } else {
            Log.v(TAG, "setMuted($muted) — audio renderer not initialized yet")
        }
    }

    fun stopStream() {
        intentionalStop = true
        isStreamingActive = false
        // Interrupt the startup thread if it is still running (e.g. stuck on app-list fetch).
        streamThread?.let {
            Log.i(TAG, "Interrupting stream startup thread")
            it.interrupt()
        }
        synchronized(streamLock) {
            Log.i(TAG, "Stopping stream (intentional)")
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

    /**
     * Attempt to reconnect using the last known connection parameters.
     * Returns `true` if the reconnect was initiated, `false` if parameters are missing.
     */
    fun reconnect(): Boolean {
        val address = lastServerAddress ?: return false
        val surface = lastSurface ?: return false
        Log.i(TAG, "Reconnecting to $address")
        startStream(address, surface, lastServerCert)
        return true
    }

    /**
     * Whether the last disconnection was intentional (user-initiated stop).
     */
    fun wasIntentionalStop(): Boolean = intentionalStop

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
        mainHandler.post { onStageChanged?.invoke("Starting: $stage") }
    }

    override fun stageComplete(stage: String) {
        Log.i(TAG, "Stage complete: $stage")
    }

    override fun stageFailed(stage: String, portFlags: Int, errorCode: Int) {
        Log.e(TAG, "Stage FAILED: $stage (port=$portFlags, error=$errorCode)")
        mainHandler.post {
            onConnectionTerminated?.invoke("Failed at stage: $stage (error $errorCode)")
        }
    }

    override fun connectionStarted() {
        Log.i(TAG, "Connection started — streaming!")
        isStreamingActive = true
        intentionalStop = false
        mainHandler.post { onConnectionStarted?.invoke() }
    }

    override fun connectionTerminated(errorCode: Int) {
        val wasIntentional = intentionalStop
        isStreamingActive = false
        Log.i(TAG, "Connection terminated: errorCode=$errorCode, intentional=$wasIntentional")
        mainHandler.post {
            onConnectionTerminated?.invoke(
                if (errorCode != 0) "Connection lost (error $errorCode)" else null,
            )
        }
    }

    override fun connectionStatusUpdate(connectionStatus: Int) {
        Log.v(TAG, "Connection status: $connectionStatus")
    }

    override fun displayMessage(message: String) {
        Log.i(TAG, "Display message: $message")
        mainHandler.post { onStageChanged?.invoke(message) }
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

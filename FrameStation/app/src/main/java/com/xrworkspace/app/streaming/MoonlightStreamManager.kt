// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.streaming

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
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

    // applicationContext is a process singleton — strong reference is safe here.
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
    var streamHdr: Boolean = false

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
        streamHdr = settings.enableHdr
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

                    // Map codec preference to Moonlight video format flags.
                    // AV1 requires both API 29+ AND a hardware AV1 decoder. Probe MediaCodecList
                    // to confirm the decoder exists before advertising AV1 support to the server,
                    // otherwise the server may negotiate AV1 and the stream will fail.
                    val av1Supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                        hasAv1Decoder()
                    val videoFormats = when (streamCodec) {
                        VideoCodec.AUTO -> {
                            var mask = MoonBridge.VIDEO_FORMAT_H264 or MoonBridge.VIDEO_FORMAT_H265
                            if (av1Supported) mask = mask or MoonBridge.VIDEO_FORMAT_AV1_MAIN8
                            mask
                        }
                        VideoCodec.H264 -> MoonBridge.VIDEO_FORMAT_H264
                        VideoCodec.H265 -> MoonBridge.VIDEO_FORMAT_H265
                        VideoCodec.AV1_MAIN8 -> if (av1Supported) MoonBridge.VIDEO_FORMAT_AV1_MAIN8
                            else MoonBridge.VIDEO_FORMAT_H265
                        VideoCodec.AV1_MAIN10 -> if (av1Supported) MoonBridge.VIDEO_FORMAT_AV1_MAIN10
                            else MoonBridge.VIDEO_FORMAT_H265_MAIN10
                    }

                    // If HDR is requested, include 10-bit format variants
                    val finalVideoFormats = if (streamHdr) {
                        videoFormats or MoonBridge.VIDEO_FORMAT_H265_MAIN10 or
                            (if (av1Supported) MoonBridge.VIDEO_FORMAT_AV1_MAIN10 else 0)
                    } else {
                        videoFormats
                    }

                    // Resolve audio configuration from user settings
                    val audioConfig = audioSettings.audioChannels.toMoonBridgeConfig()

                    // Build stream configuration WITH the app
                    val streamConfigBuilder = StreamConfiguration.Builder()
                        .setResolution(streamWidth, streamHeight)
                        .setRefreshRate(streamFps)
                        .setLaunchRefreshRate(streamFps)
                        .setBitrate(streamBitrate)
                        .setApp(desktopApp)
                        .setMaxPacketSize(1392)
                        // Detect local vs remote network based on server address.
                        // NvConnection.detectServerConnectionType() is a stub that always
                        // returns STREAM_CFG_AUTO, which causes RTSP_ERROR_MALFORMED (-2)
                        // during video stream setup. Detect explicitly here.
                        .setRemoteConfiguration(
                            if (isLocalAddress(serverAddress)) StreamConfiguration.STREAM_CFG_LOCAL
                            else StreamConfiguration.STREAM_CFG_REMOTE
                        )
                        .setAudioConfiguration(audioConfig)
                        .setSupportedVideoFormats(finalVideoFormats)
                    if (streamHdr) {
                        streamConfigBuilder.setColorSpace(MoonBridge.COLORSPACE_REC_2020)
                    }
                    val streamConfig = streamConfigBuilder.build()

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
                            VideoCodec.AV1_MAIN8, VideoCodec.AV1_MAIN10 ->
                                if (av1Supported) PreferenceConfiguration.FormatOption.FORCE_AV1
                                else PreferenceConfiguration.FormatOption.FORCE_HEVC
                        }
                        framePacing = PreferenceConfiguration.FRAME_PACING_BALANCED
                        enableHdr = streamHdr
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
                    val errorMsg = e.message ?: "Unknown error"
                    // Mark auth/cert errors as intentional so auto-reconnect doesn't retry
                    // (retrying without pairing first will never succeed)
                    val isAuthError = errorMsg.contains("401") ||
                        errorMsg.contains("not authorized", ignoreCase = true) ||
                        errorMsg.contains("certificate", ignoreCase = true)
                    if (isAuthError) {
                        intentionalStop = true  // prevents auto-reconnect
                    }
                    mainHandler.post {
                        val userMsg = if (isAuthError) {
                            "Not paired — use the Pair button first"
                        } else {
                            "Failed: $errorMsg"
                        }
                        onConnectionTerminated?.invoke(userMsg)
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
     * Forward gamepad state to the host PC.
     *
     * @param buttonFlags Bitmask of pressed buttons using [ControllerPacket] flag constants.
     * @param leftTrigger Left trigger pressure (0–255).
     * @param rightTrigger Right trigger pressure (0–255).
     * @param leftStickX Left stick horizontal (-32767 left, 32767 right).
     * @param leftStickY Left stick vertical (-32767 up, 32767 down in Moonlight convention).
     * @param rightStickX Right stick horizontal.
     * @param rightStickY Right stick vertical.
     */
    fun sendControllerInput(
        buttonFlags: Int,
        leftTrigger: Byte,
        rightTrigger: Byte,
        leftStickX: Short,
        leftStickY: Short,
        rightStickX: Short,
        rightStickY: Short,
    ) {
        connection?.sendControllerInput(
            /* controllerNumber = */ 0,
            /* activeGamepadMask = */ 1,
            buttonFlags,
            leftTrigger,
            rightTrigger,
            leftStickX,
            leftStickY,
            rightStickX,
            rightStickY,
        )
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
        // Join with a timeout to ensure the old thread exits before we clear the connection
        // state — prevents two decoder instances if stopStream/startStream are called rapidly.
        streamThread?.let { t ->
            Log.i(TAG, "Interrupting stream startup thread")
            t.interrupt()
            t.join(3_000L)
            if (t.isAlive) Log.w(TAG, "Stream thread did not exit within 3s after interrupt")
            streamThread = null
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
     * Probes MediaCodecList for a hardware AV1 decoder. API 29+ doesn't guarantee
     * AV1 decoder availability — Samsung Galaxy XR runs API 34 but has no AV1 decoder.
     */
    private fun hasAv1Decoder(): Boolean {
        return try {
            val list = android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS)
            list.codecInfos.any { codec ->
                !codec.isEncoder && codec.supportedTypes.any { it.equals("video/av01", ignoreCase = true) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to probe MediaCodecList for AV1", e)
            false
        }
    }

    /**
     * Returns true if [address] is a private/LAN IPv4 address (RFC 1918) or localhost.
     * Used to choose STREAM_CFG_LOCAL vs STREAM_CFG_REMOTE for the streaming config,
     * because NvConnection.detectServerConnectionType() is a non-functional stub on XR.
     */
    private fun isLocalAddress(address: String): Boolean {
        // Strip port if present
        val host = address.substringBeforeLast(':').trim()
        // IPv4 private ranges:
        //   10.0.0.0/8
        //   172.16.0.0/12  (172.16.x.x - 172.31.x.x)
        //   192.168.0.0/16
        //   127.0.0.0/8 (loopback)
        return host.startsWith("10.") ||
            host.startsWith("192.168.") ||
            host.startsWith("127.") ||
            host == "localhost" ||
            (host.startsWith("172.") && host.split('.').getOrNull(1)?.toIntOrNull()?.let { it in 16..31 } == true)
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
        // Mark as intentional so auto-reconnect doesn't trigger.
        // Stage failures during initial connection are hard errors (cert mismatch,
        // network rejection, server-side issues) — retrying won't help.
        intentionalStop = true
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
    override fun setHdrMode(enabled: Boolean, hdrMetadata: ByteArray?) {
        Log.i(TAG, "HDR mode: enabled=$enabled, metadata=${hdrMetadata?.size ?: 0} bytes")
    }
    override fun setMotionEventState(controllerNumber: Short, motionType: Byte, reportRateHz: Short) {}
    override fun setControllerLED(controllerNumber: Short, r: Byte, g: Byte, b: Byte) {}
}

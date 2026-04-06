// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.ui.panels

import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import com.limelight.nvstream.input.ControllerPacket
import androidx.xr.compose.subspace.SpatialExternalSurface
import androidx.xr.compose.subspace.StereoMode
import androidx.xr.compose.subspace.layout.InteractionPolicy
import androidx.xr.compose.subspace.layout.SpatialInputEvent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.limelight.nvstream.input.KeyboardPacket
import com.limelight.nvstream.input.MouseButtonPacket
import com.xrworkspace.app.model.AudioMode
import com.xrworkspace.app.model.AudioSettings
import com.xrworkspace.app.model.StreamSettings
import com.xrworkspace.app.streaming.AutoReconnectManager
import com.xrworkspace.app.streaming.StreamServiceConnection
import com.xrworkspace.app.streaming.MoonlightStreamManager
import com.xrworkspace.app.streaming.NetworkMonitor
import com.xrworkspace.app.streaming.ReconnectState
import com.xrworkspace.app.viewmodel.WolState
import java.security.cert.X509Certificate

/**
 * Compose panel that renders native Moonlight video streaming via [SpatialExternalSurface].
 * Supports touch-to-mouse mapping via [SpatialInputEvent], keyboard input forwarding,
 * and full connection lifecycle management including auto-reconnect.
 */
@Suppress("DEPRECATION") // LocalLifecycleOwner moved to lifecycle-compose in newer versions
@Composable
fun NativeStreamPanel(
    serverAddress: String,
    modifier: Modifier = Modifier,
    serverCert: X509Certificate? = null,
    streamSettings: StreamSettings = StreamSettings(),
    audioSettings: AudioSettings = AudioSettings(),
    autoReconnectEnabled: Boolean = true,
    selectedAppId: Int? = null,
    selectedAppName: String = "Desktop",
    onAppSelectorClick: (() -> Unit)? = null,
    hasMacAddress: Boolean = false,
    wolState: WolState = WolState.Idle,
    onWakeClick: (() -> Unit)? = null,
    onStreamingStateChanged: ((Boolean) -> Unit)? = null,
    streamController: StreamController? = null,
    /** When set, stream runs in an isolated service process. Null = local MoonlightStreamManager. */
    streamServiceConnection: StreamServiceConnection? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("framestation_prefs", android.content.Context.MODE_PRIVATE) }

    var statusText by remember { mutableStateOf("Ready to connect") }
    var isConnected by remember { mutableStateOf(false) }
    var isConnecting by remember { mutableStateOf(false) }
    var hasDisconnected by remember { mutableStateOf(false) }
    var surfaceRef by remember { mutableStateOf<Surface?>(null) }
    var reconnectAttemptNumber by remember { mutableIntStateOf(0) }

    // Gamepad state — accumulated and sent on every change
    var gamepadButtons by remember { mutableIntStateOf(0) }
    var gamepadLT by remember { mutableStateOf(0f) }
    var gamepadRT by remember { mutableStateOf(0f) }
    var gamepadLX by remember { mutableStateOf(0f) }
    var gamepadLY by remember { mutableStateOf(0f) }
    var gamepadRX by remember { mutableStateOf(0f) }
    var gamepadRY by remember { mutableStateOf(0f) }

    // Network monitor — tracks Wi-Fi connectivity state
    val networkMonitor = remember { NetworkMonitor(context) }

    // Auto-reconnect manager — handles reconnection logic with exponential backoff
    val autoReconnectManager = remember {
        AutoReconnectManager(networkMonitor, coroutineScope).apply {
            isEnabled = autoReconnectEnabled
            onReconnectAttempt = { attempt ->
                reconnectAttemptNumber = attempt
                statusText = "Reconnecting (attempt $attempt/$maxRetries)..."
            }
            onReconnectSuccess = {
                reconnectAttemptNumber = 0
            }
            onReconnectFailed = { reason ->
                statusText = "Reconnect failed: $reason"
            }
            onReconnectGaveUp = {
                reconnectAttemptNumber = 0
                statusText = "Auto-reconnect failed — tap Reconnect to try manually"
            }
        }
    }

    // Observe reconnect state for UI updates
    val reconnectState by autoReconnectManager.reconnectState.collectAsState()

    val streamManager = remember {
        run {
            MoonlightStreamManager(context, prefs).apply {
                onStageChanged = { stage -> statusText = stage }
                onConnectionStarted = {
                    isConnected = true
                    isConnecting = false
                    hasDisconnected = false  // reset so button shows "Start Stream" again after reconnect
                    statusText = "Connected"
                    autoReconnectManager.cancelReconnect()
                    onStreamingStateChanged?.invoke(true)
                }
                onConnectionTerminated = { reason ->
                    val wasIntentional = streamManager?.wasIntentionalStop() ?: false
                    isConnected = false
                    isConnecting = false
                    hasDisconnected = true
                    statusText = reason ?: "Disconnected"
                    onStreamingStateChanged?.invoke(false)

                    // Trigger auto-reconnect if the drop was not intentional
                    if (!wasIntentional && autoReconnectEnabled) {
                        autoReconnectManager.onStreamTerminated()
                    }
                }
            }
        }
    }

    // Capture analog gamepad axes (sticks, triggers, d-pad hat) via onGenericMotionListener
    val localView = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(localView, isConnected) {
        val listener = android.view.View.OnGenericMotionListener { _, event ->
            if (!isConnected) return@OnGenericMotionListener false
            val isJoystick = event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
            if (!isJoystick) return@OnGenericMotionListener false
            val device = InputDevice.getDevice(event.deviceId) ?: return@OnGenericMotionListener false
            val flat = device.getMotionRange(MotionEvent.AXIS_X, event.source)?.flat ?: 0.1f
            fun deadzone(v: Float) = if (kotlin.math.abs(v) > flat) v else 0f
            gamepadLX = deadzone(event.getAxisValue(MotionEvent.AXIS_X))
            gamepadLY = deadzone(event.getAxisValue(MotionEvent.AXIS_Y))
            gamepadRX = deadzone(event.getAxisValue(MotionEvent.AXIS_Z))
            gamepadRY = deadzone(event.getAxisValue(MotionEvent.AXIS_RZ))
            gamepadLT = event.getAxisValue(MotionEvent.AXIS_LTRIGGER)
            gamepadRT = event.getAxisValue(MotionEvent.AXIS_RTRIGGER)
            val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            gamepadButtons = gamepadButtons
                .let { if (hatX < -0.5f) it or ControllerPacket.LEFT_FLAG else it and ControllerPacket.LEFT_FLAG.inv() }
                .let { if (hatX > 0.5f) it or ControllerPacket.RIGHT_FLAG else it and ControllerPacket.RIGHT_FLAG.inv() }
                .let { if (hatY < -0.5f) it or ControllerPacket.UP_FLAG else it and ControllerPacket.UP_FLAG.inv() }
                .let { if (hatY > 0.5f) it or ControllerPacket.DOWN_FLAG else it and ControllerPacket.DOWN_FLAG.inv() }
            sendGamepadState()
            true
        }
        localView.setOnGenericMotionListener(listener)
        onDispose { localView.setOnGenericMotionListener(null) }
    }

    // Lifecycle observer — pause/resume streaming on app background/foreground
    DisposableEffect(lifecycleOwner) {
        networkMonitor.startMonitoring()

        // Start auto-reconnect monitoring with the stream manager's reconnect method
        if (autoReconnectEnabled && streamManager != null) {
            autoReconnectManager.startMonitoring {
                streamManager.reconnect()
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    if (isConnected) {
                        Log.i("NativeStreamPanel", "App going to background — stopping stream")
                        streamManager?.stopStream()
                    }
                }
                else -> { /* no-op for other lifecycle events */ }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            autoReconnectManager.stopMonitoring()
            networkMonitor.stopMonitoring()
            streamManager?.stopStream()
            onStreamingStateChanged?.invoke(false)
        }
    }

    /** Send current accumulated gamepad state to the host PC. */
    fun sendGamepadState() {
        if (!isConnected) return
        val lt = (gamepadLT * 255f).toInt().coerceIn(0, 255).toByte()
        val rt = (gamepadRT * 255f).toInt().coerceIn(0, 255).toByte()
        val lx = (gamepadLX * 32767f).toInt().coerceIn(-32767, 32767).toShort()
        val ly = (-gamepadLY * 32767f).toInt().coerceIn(-32767, 32767).toShort() // Y-axis inverted
        val rx = (gamepadRX * 32767f).toInt().coerceIn(-32767, 32767).toShort()
        val ry = (-gamepadRY * 32767f).toInt().coerceIn(-32767, 32767).toShort()
        streamServiceConnection?.sendControllerInput(gamepadButtons, lt, rt, lx, ly, rx, ry)
            ?: streamManager?.sendControllerInput(gamepadButtons, lt, rt, lx, ly, rx, ry)
    }

    /** Map a KEYCODE_BUTTON_* keycode to its ControllerPacket flag, or 0 if unknown. */
    fun mapGamepadKeyToFlag(keyCode: Int): Int = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> ControllerPacket.A_FLAG
        KeyEvent.KEYCODE_BUTTON_B -> ControllerPacket.B_FLAG
        KeyEvent.KEYCODE_BUTTON_X -> ControllerPacket.X_FLAG
        KeyEvent.KEYCODE_BUTTON_Y -> ControllerPacket.Y_FLAG
        KeyEvent.KEYCODE_BUTTON_L1 -> ControllerPacket.LB_FLAG
        KeyEvent.KEYCODE_BUTTON_R1 -> ControllerPacket.RB_FLAG
        KeyEvent.KEYCODE_BUTTON_THUMBL -> ControllerPacket.LS_CLK_FLAG
        KeyEvent.KEYCODE_BUTTON_THUMBR -> ControllerPacket.RS_CLK_FLAG
        KeyEvent.KEYCODE_BUTTON_START -> ControllerPacket.PLAY_FLAG
        KeyEvent.KEYCODE_BUTTON_SELECT -> ControllerPacket.BACK_FLAG
        KeyEvent.KEYCODE_BUTTON_MODE -> ControllerPacket.SPECIAL_BUTTON_FLAG
        KeyEvent.KEYCODE_DPAD_UP -> ControllerPacket.UP_FLAG
        KeyEvent.KEYCODE_DPAD_DOWN -> ControllerPacket.DOWN_FLAG
        KeyEvent.KEYCODE_DPAD_LEFT -> ControllerPacket.LEFT_FLAG
        KeyEvent.KEYCODE_DPAD_RIGHT -> ControllerPacket.RIGHT_FLAG
        else -> 0
    }

    fun startStreaming() {
        // Cancel any pending auto-reconnect when user manually starts a stream
        autoReconnectManager.cancelReconnect()
        val surface = surfaceRef
        if (surface == null) {
            statusText = "Surface not ready — wait a moment and try again"
            return
        }
        isConnecting = true
        statusText = "Connecting..."
        if (streamServiceConnection != null) {
            // IPC path — wire callbacks so UI state updates when service connects/disconnects
            streamServiceConnection.onConnectionStarted = {
                isConnected = true
                isConnecting = false
                hasDisconnected = false  // reset so button shows "Start Stream" again after reconnect
                statusText = "Connected"
                autoReconnectManager.cancelReconnect()
                onStreamingStateChanged?.invoke(true)
            }
            streamServiceConnection.onConnectionTerminated = { reason ->
                // IPC path: treat any unexpected termination as non-intentional (service has
                // no wasIntentionalStop() equivalent yet; intentional stops go via stopStreaming())
                val wasIntentional = !isConnected && !isConnecting  // already stopped = intentional
                isConnected = false
                isConnecting = false
                hasDisconnected = true
                statusText = reason ?: "Disconnected"
                onStreamingStateChanged?.invoke(false)
                if (!wasIntentional && autoReconnectEnabled) {
                    autoReconnectManager.onStreamTerminated()
                }
            }
            streamServiceConnection.startStream(serverAddress, surface, streamSettings, audioSettings)
        } else {
            // Local path — stream runs in-process (main desktop panel)
            streamManager?.applyStreamSettings(streamSettings)
            streamManager?.applyAudioSettings(audioSettings)
            streamManager?.startStream(serverAddress, surface, serverCert, selectedAppId)
        }
    }

    fun stopStreaming() {
        autoReconnectManager.cancelReconnect()
        streamServiceConnection?.stopStream()
        streamManager?.stopStream()
        isConnected = false
        isConnecting = false
        hasDisconnected = true
        statusText = "Disconnected"
        onStreamingStateChanged?.invoke(false)
    }

    // Apply audio mute state changes to the active stream in real-time
    val currentMuted = audioSettings.audioMode == AudioMode.MUTED
    LaunchedEffect(currentMuted) {
        if (isConnected) {
            streamManager?.setMuted(currentMuted)
        }
    }

    var showKeyboardBar by remember { mutableStateOf(false) }

    fun showKeyboard() {
        showKeyboardBar = !showKeyboardBar
    }

    // Wire stream controller for external stop/keyboard/monitor triggers (toolbar)
    DisposableEffect(streamController) {
        streamController?.onStopStream = { stopStreaming() }
        streamController?.onShowKeyboard = { showKeyboard() }
        onDispose {
            streamController?.onStopStream = null
            streamController?.onShowKeyboard = null
        }
    }
    // Monitor switching is handled by the MonitorPickerPanel popup via SpatialWorkspace

    Box(
        modifier = modifier
            .fillMaxSize()
            .onKeyEvent { keyEvent ->
                val isGamepad = keyEvent.nativeKeyEvent.source and
                    InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD
                if (!isGamepad || !isConnected) return@onKeyEvent false
                val flag = mapGamepadKeyToFlag(keyEvent.nativeKeyEvent.keyCode)
                if (flag == 0) return@onKeyEvent false
                gamepadButtons = if (keyEvent.type == KeyEventType.KeyDown) {
                    gamepadButtons or flag
                } else {
                    gamepadButtons and flag.inv()
                }
                sendGamepadState()
                true
            },
    ) {
        // SpatialExternalSurface for low-latency video rendering (bypasses AndroidView compositing)
        SpatialExternalSurface(
            stereoMode = StereoMode.Mono,
            interactionPolicy = object : InteractionPolicy {
                override val isEnabled: Boolean = true
                override fun onInputEvent(event: SpatialInputEvent) {
                    if (!isConnected) return

                    val hitPos = event.hitPosition ?: return
                    // hitPosition is pixel offset from surface CENTER.
                    // Convert to top-left origin, normalize to [0,1], scale to stream res.
                    // Panel size: 1400x900dp (main) or 1200x750dp (arc panel).
                    val panelHalfW = 700f
                    val panelHalfH = 450f
                    val normX = ((hitPos.x + panelHalfW) / (panelHalfW * 2f)).coerceIn(0f, 1f)
                    val normY = ((hitPos.y + panelHalfH) / (panelHalfH * 2f)).coerceIn(0f, 1f)
                    val streamW = (streamManager?.streamWidth ?: 1920).toShort()
                    val streamH = (streamManager?.streamHeight ?: 1080).toShort()
                    val streamX = (normX * (streamManager?.streamWidth ?: 1920).toFloat()).toInt()
                        .coerceIn(0, (streamManager?.streamWidth ?: 1920) - 1).toShort()
                    val streamY = (normY * (streamManager?.streamHeight ?: 1080).toFloat()).toInt()
                        .coerceIn(0, (streamManager?.streamHeight ?: 1080) - 1).toShort()

                    if (streamServiceConnection != null) {
                        // IPC path — forward input to service process
                        when (event.action) {
                            SpatialInputEvent.Action.DOWN -> {
                                streamServiceConnection.sendMousePosition(streamX, streamY, streamW, streamH)
                                streamServiceConnection.sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT)
                            }
                            SpatialInputEvent.Action.MOVE ->
                                streamServiceConnection.sendMousePosition(streamX, streamY, streamW, streamH)
                            SpatialInputEvent.Action.UP -> {
                                streamServiceConnection.sendMousePosition(streamX, streamY, streamW, streamH)
                                streamServiceConnection.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT)
                            }
                            else -> {}
                        }
                    } else if (streamManager != null) {
                        // Local path — direct call
                        when (event.action) {
                            SpatialInputEvent.Action.DOWN -> {
                                streamManager.sendMousePosition(streamX, streamY, streamW, streamH)
                                streamManager.sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT)
                            }
                            SpatialInputEvent.Action.MOVE ->
                                streamManager.sendMousePosition(streamX, streamY, streamW, streamH)
                            SpatialInputEvent.Action.UP -> {
                                streamManager.sendMousePosition(streamX, streamY, streamW, streamH)
                                streamManager.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT)
                            }
                            else -> {}
                        }
                    }
                }
            },
        ) {
            onSurfaceCreated { surface ->
                Log.i("NativeStreamPanel", "SpatialExternalSurface created")
                surfaceRef = surface
                // Surface is ready — stream will start when user taps "Start Stream"
                // (or immediately if connection overlay shows and user acts)
            }
            onSurfaceDestroyed { _ ->
                Log.i("NativeStreamPanel", "SpatialExternalSurface destroyed")
                surfaceRef = null
                streamServiceConnection?.stopStream()
                streamManager?.stopStream()
            }
        }

        // Keyboard FAB — visible when connected
        if (isConnected) {
            FloatingActionButton(
                onClick = { showKeyboard() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Icon(
                    Icons.Default.Keyboard,
                    contentDescription = "Show keyboard",
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        // Typing bar — appears at bottom when keyboard mode is active
        if (showKeyboardBar && isConnected) {
            var typingText by remember { mutableStateOf("") }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = typingText,
                    onValueChange = { newValue ->
                        if (streamManager != null && isConnected) {
                            if (newValue.length > typingText.length) {
                                // Characters added — send only the new ones
                                val added = newValue.substring(typingText.length)
                                streamManager.sendUtf8Text(added)
                            } else if (newValue.length < typingText.length) {
                                // Characters removed — send backspaces
                                val deletedCount = typingText.length - newValue.length
                                for (i in 0 until deletedCount) {
                                    streamManager.sendKeyboardInput(
                                        0x08.toShort(), KeyboardPacket.KEY_DOWN, 0, 0
                                    )
                                    streamManager.sendKeyboardInput(
                                        0x08.toShort(), KeyboardPacket.KEY_UP, 0, 0
                                    )
                                }
                            }
                        }
                        typingText = newValue
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type here...") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (typingText.isNotBlank()) {
                                streamManager?.sendUtf8Text(typingText)
                                typingText = ""
                            }
                        }
                    ),
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Enter button
                Button(
                    onClick = {
                        if (typingText.isNotBlank()) {
                            streamManager?.sendUtf8Text(typingText)
                            typingText = ""
                        }
                    }
                ) {
                    Text("Send")
                }
                Spacer(modifier = Modifier.width(4.dp))
                // Close keyboard bar
                Button(
                    onClick = { showKeyboardBar = false }
                ) {
                    Text("Close")
                }
            }
        }

        // Overlay when not connected
        if (!isConnected) {
            val isAutoReconnecting = reconnectState == ReconnectState.WaitingForNetwork ||
                reconnectState == ReconnectState.Reconnecting

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Status text — shows reconnect state when auto-reconnecting
                    Text(
                        text = when (reconnectState) {
                            ReconnectState.WaitingForNetwork -> "Network lost... waiting for Wi-Fi"
                            ReconnectState.Reconnecting -> "Reconnecting (attempt $reconnectAttemptNumber/${autoReconnectManager.maxRetries})..."
                            ReconnectState.Failed -> "Auto-reconnect failed"
                            ReconnectState.Idle -> statusText
                        },
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )

                    if (serverAddress.isNotBlank()) {
                        Text(
                            text = "Server: $serverAddress",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    // Cancel button — visible during auto-reconnect
                    if (isAutoReconnecting) {
                        OutlinedButton(
                            onClick = {
                                autoReconnectManager.cancelReconnect()
                                statusText = "Disconnected"
                                reconnectAttemptNumber = 0
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White,
                            ),
                        ) {
                            Text("Cancel")
                        }
                    }

                    // App selector chip — shows currently selected app
                    if (!isConnecting && !isAutoReconnecting) {
                        FilterChip(
                            selected = true,
                            onClick = { onAppSelectorClick?.invoke() },
                            label = { Text("App: $selectedAppName") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Apps,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    }

                    // Manual reconnect/start button — hidden during auto-reconnect
                    if (!isConnecting && !isAutoReconnecting) {
                        Button(
                            onClick = { startStreaming() },
                            enabled = serverAddress.isNotBlank(),
                        ) {
                            Text(if (hasDisconnected) "Reconnect" else "Start Stream")
                        }
                    }

                    // Wake-on-LAN button — shown when MAC is configured and not connecting
                    if (hasMacAddress && !isConnecting && !isAutoReconnecting) {
                        OutlinedButton(
                            onClick = { onWakeClick?.invoke() },
                            enabled = wolState == WolState.Idle || wolState == WolState.Failed,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White,
                            ),
                        ) {
                            Text(
                                when (wolState) {
                                    WolState.Idle -> "Wake PC"
                                    WolState.Sending -> "Sending wake packet..."
                                    WolState.Sent -> "Wake packet sent!"
                                    WolState.Failed -> "Failed to send wake packet"
                                }
                            )
                        }
                        if (wolState == WolState.Sent) {
                            Text(
                                text = "Wait ~30 seconds, then try connecting",
                                color = Color.White.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

// Expose stop function via a remembered callback holder
// This allows the toolbar to trigger disconnect
@Composable
fun rememberStreamController(): StreamController {
    return remember { StreamController() }
}

class StreamController {
    var onStopStream: (() -> Unit)? = null
    var onShowKeyboard: (() -> Unit)? = null
    var onSwitchMonitor: (() -> Unit)? = null

    fun stopStream() { onStopStream?.invoke() }
    fun showKeyboard() { onShowKeyboard?.invoke() }
    fun switchMonitor() { onSwitchMonitor?.invoke() }
}

/**
 * Translate Android KeyEvent keycodes to Windows virtual key codes.
 */
private fun translateKeyCode(keyCode: Int): Short = when (keyCode) {
    // Letters A-Z → 0x41-0x5A
    KeyEvent.KEYCODE_A -> 0x41
    KeyEvent.KEYCODE_B -> 0x42
    KeyEvent.KEYCODE_C -> 0x43
    KeyEvent.KEYCODE_D -> 0x44
    KeyEvent.KEYCODE_E -> 0x45
    KeyEvent.KEYCODE_F -> 0x46
    KeyEvent.KEYCODE_G -> 0x47
    KeyEvent.KEYCODE_H -> 0x48
    KeyEvent.KEYCODE_I -> 0x49
    KeyEvent.KEYCODE_J -> 0x4A
    KeyEvent.KEYCODE_K -> 0x4B
    KeyEvent.KEYCODE_L -> 0x4C
    KeyEvent.KEYCODE_M -> 0x4D
    KeyEvent.KEYCODE_N -> 0x4E
    KeyEvent.KEYCODE_O -> 0x4F
    KeyEvent.KEYCODE_P -> 0x50
    KeyEvent.KEYCODE_Q -> 0x51
    KeyEvent.KEYCODE_R -> 0x52
    KeyEvent.KEYCODE_S -> 0x53
    KeyEvent.KEYCODE_T -> 0x54
    KeyEvent.KEYCODE_U -> 0x55
    KeyEvent.KEYCODE_V -> 0x56
    KeyEvent.KEYCODE_W -> 0x57
    KeyEvent.KEYCODE_X -> 0x58
    KeyEvent.KEYCODE_Y -> 0x59
    KeyEvent.KEYCODE_Z -> 0x5A

    // Numbers 0-9 → 0x30-0x39
    KeyEvent.KEYCODE_0 -> 0x30
    KeyEvent.KEYCODE_1 -> 0x31
    KeyEvent.KEYCODE_2 -> 0x32
    KeyEvent.KEYCODE_3 -> 0x33
    KeyEvent.KEYCODE_4 -> 0x34
    KeyEvent.KEYCODE_5 -> 0x35
    KeyEvent.KEYCODE_6 -> 0x36
    KeyEvent.KEYCODE_7 -> 0x37
    KeyEvent.KEYCODE_8 -> 0x38
    KeyEvent.KEYCODE_9 -> 0x39

    // Control keys
    KeyEvent.KEYCODE_ENTER -> 0x0D
    KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BACK -> 0x1B
    KeyEvent.KEYCODE_DEL -> 0x08           // Backspace
    KeyEvent.KEYCODE_FORWARD_DEL -> 0x2E   // Delete
    KeyEvent.KEYCODE_TAB -> 0x09
    KeyEvent.KEYCODE_SPACE -> 0x20

    // Arrow keys
    KeyEvent.KEYCODE_DPAD_LEFT -> 0x25
    KeyEvent.KEYCODE_DPAD_UP -> 0x26
    KeyEvent.KEYCODE_DPAD_RIGHT -> 0x27
    KeyEvent.KEYCODE_DPAD_DOWN -> 0x28

    // Modifier keys
    KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> 0x10
    KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> 0x11
    KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> 0x12
    KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT -> 0x5B  // Windows key

    // Punctuation
    KeyEvent.KEYCODE_COMMA -> 0xBC
    KeyEvent.KEYCODE_PERIOD -> 0xBE
    KeyEvent.KEYCODE_SLASH -> 0xBF
    KeyEvent.KEYCODE_SEMICOLON -> 0xBA
    KeyEvent.KEYCODE_APOSTROPHE -> 0xDE
    KeyEvent.KEYCODE_LEFT_BRACKET -> 0xDB
    KeyEvent.KEYCODE_RIGHT_BRACKET -> 0xDD
    KeyEvent.KEYCODE_BACKSLASH -> 0xDC
    KeyEvent.KEYCODE_MINUS -> 0xBD
    KeyEvent.KEYCODE_EQUALS -> 0xBB
    KeyEvent.KEYCODE_GRAVE -> 0xC0

    // Function keys F1-F12
    KeyEvent.KEYCODE_F1 -> 0x70
    KeyEvent.KEYCODE_F2 -> 0x71
    KeyEvent.KEYCODE_F3 -> 0x72
    KeyEvent.KEYCODE_F4 -> 0x73
    KeyEvent.KEYCODE_F5 -> 0x74
    KeyEvent.KEYCODE_F6 -> 0x75
    KeyEvent.KEYCODE_F7 -> 0x76
    KeyEvent.KEYCODE_F8 -> 0x77
    KeyEvent.KEYCODE_F9 -> 0x78
    KeyEvent.KEYCODE_F10 -> 0x79
    KeyEvent.KEYCODE_F11 -> 0x7A
    KeyEvent.KEYCODE_F12 -> 0x7B

    // Navigation keys
    KeyEvent.KEYCODE_INSERT -> 0x2D
    KeyEvent.KEYCODE_MOVE_HOME -> 0x24
    KeyEvent.KEYCODE_MOVE_END -> 0x23
    KeyEvent.KEYCODE_PAGE_UP -> 0x21
    KeyEvent.KEYCODE_PAGE_DOWN -> 0x22

    // Lock keys
    KeyEvent.KEYCODE_CAPS_LOCK -> 0x14
    KeyEvent.KEYCODE_NUM_LOCK -> 0x90
    KeyEvent.KEYCODE_SCROLL_LOCK -> 0x91

    else -> 0
}.toShort()

/**
 * Translate Android KeyEvent modifier state to Moonlight modifier flags.
 */
private fun translateModifiers(event: KeyEvent): Byte {
    var modifiers = 0
    if (event.isShiftPressed) modifiers = modifiers or KeyboardPacket.MODIFIER_SHIFT.toInt()
    if (event.isCtrlPressed) modifiers = modifiers or KeyboardPacket.MODIFIER_CTRL.toInt()
    if (event.isAltPressed) modifiers = modifiers or KeyboardPacket.MODIFIER_ALT.toInt()
    if (event.isMetaPressed) modifiers = modifiers or KeyboardPacket.MODIFIER_META.toInt()
    return modifiers.toByte()
}

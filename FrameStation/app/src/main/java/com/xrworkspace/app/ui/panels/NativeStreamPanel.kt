// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.ui.panels

import android.app.Activity
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.limelight.nvstream.input.KeyboardPacket
import com.limelight.nvstream.input.MouseButtonPacket
import com.xrworkspace.app.streaming.MoonlightStreamManager
import java.security.cert.X509Certificate

/**
 * Compose panel that hosts a SurfaceView for native Moonlight streaming.
 * Supports touch-to-mouse mapping, keyboard input forwarding, and connection lifecycle.
 */
@Suppress("DEPRECATION") // LocalLifecycleOwner moved to lifecycle-compose in newer versions
@Composable
fun NativeStreamPanel(
    serverAddress: String,
    modifier: Modifier = Modifier,
    serverCert: X509Certificate? = null,
    onStreamingStateChanged: ((Boolean) -> Unit)? = null,
    streamController: StreamController? = null,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = remember { context.getSharedPreferences("framestation_prefs", android.content.Context.MODE_PRIVATE) }

    var statusText by remember { mutableStateOf("Ready to connect") }
    var isConnected by remember { mutableStateOf(false) }
    var isConnecting by remember { mutableStateOf(false) }
    var hasDisconnected by remember { mutableStateOf(false) }
    var surfaceHolderRef by remember { mutableStateOf<SurfaceHolder?>(null) }
    var surfaceViewRef by remember { mutableStateOf<SurfaceView?>(null) }

    val streamManager = remember {
        activity?.let {
            MoonlightStreamManager(it, prefs).apply {
                onStageChanged = { stage -> statusText = stage }
                onConnectionStarted = {
                    isConnected = true
                    isConnecting = false
                    statusText = "Connected"
                    onStreamingStateChanged?.invoke(true)
                }
                onConnectionTerminated = { reason ->
                    isConnected = false
                    isConnecting = false
                    hasDisconnected = true
                    statusText = reason ?: "Disconnected"
                    onStreamingStateChanged?.invoke(false)
                }
            }
        }
    }

    // Lifecycle observer — pause/resume streaming on app background/foreground
    DisposableEffect(lifecycleOwner) {
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
            streamManager?.stopStream()
            onStreamingStateChanged?.invoke(false)
        }
    }

    fun startStreaming() {
        val holder = surfaceHolderRef
        if (holder != null && activity != null) {
            isConnecting = true
            statusText = "Connecting..."
            streamManager?.startStream(serverAddress, holder, serverCert)
        } else {
            statusText = "Surface not ready — wait a moment and try again"
        }
    }

    fun stopStreaming() {
        streamManager?.stopStream()
        isConnected = false
        isConnecting = false
        hasDisconnected = true
        statusText = "Disconnected"
        onStreamingStateChanged?.invoke(false)
    }

    // Keyboard mode — shows a typing bar at the bottom of the panel
    var showKeyboardBar by remember { mutableStateOf(false) }

    fun showKeyboard() {
        showKeyboardBar = !showKeyboardBar
    }

    // Wire stream controller for external stop/keyboard triggers (toolbar)
    streamController?.onStopStream = { stopStreaming() }
    streamController?.onShowKeyboard = { showKeyboard() }

    Box(modifier = modifier.fillMaxSize()) {
        // SurfaceView for video rendering
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )

                        isFocusable = true
                        isFocusableInTouchMode = true

                        // Touch-to-mouse coordinate mapping
                        setOnTouchListener { v, event ->
                            if (streamManager == null || !isConnected) return@setOnTouchListener false

                            val streamW = streamManager.streamWidth.toShort()
                            val streamH = streamManager.streamHeight.toShort()
                            val streamX = (event.x / v.width * streamManager.streamWidth).toInt()
                                .coerceIn(0, streamManager.streamWidth - 1).toShort()
                            val streamY = (event.y / v.height * streamManager.streamHeight).toInt()
                                .coerceIn(0, streamManager.streamHeight - 1).toShort()

                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN -> {
                                    streamManager.sendMousePosition(streamX, streamY, streamW, streamH)
                                    streamManager.sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT)
                                }
                                MotionEvent.ACTION_MOVE -> {
                                    streamManager.sendMousePosition(streamX, streamY, streamW, streamH)
                                }
                                MotionEvent.ACTION_UP -> {
                                    streamManager.sendMousePosition(streamX, streamY, streamW, streamH)
                                    streamManager.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT)
                                }
                                MotionEvent.ACTION_SCROLL -> {
                                    val scrollY = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                                    if (scrollY != 0f) {
                                        streamManager.sendMouseScroll(scrollY.toInt().toByte())
                                    }
                                }
                            }
                            true
                        }

                        // Hardware keyboard forwarding
                        setOnKeyListener { _, keyCode, event ->
                            if (streamManager == null || !isConnected) return@setOnKeyListener false
                            val keyMap = translateKeyCode(keyCode)
                            if (keyMap != 0.toShort()) {
                                val direction: Byte = if (event.action == KeyEvent.ACTION_DOWN)
                                    KeyboardPacket.KEY_DOWN else KeyboardPacket.KEY_UP
                                val modifierFlags: Byte = translateModifiers(event)
                                streamManager.sendKeyboardInput(keyMap, direction, modifierFlags, 0.toByte())
                                true
                            } else false
                        }

                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) {
                                Log.i("NativeStreamPanel", "Surface created (${holder.surfaceFrame.width()}x${holder.surfaceFrame.height()})")
                                surfaceHolderRef = holder
                            }
                            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                                Log.i("NativeStreamPanel", "Surface changed: ${width}x${height}")
                            }
                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                                Log.i("NativeStreamPanel", "Surface destroyed")
                                surfaceHolderRef = null
                                streamManager?.stopStream()
                            }
                        })

                        surfaceViewRef = this
                    }
            },
            onRelease = {
                surfaceViewRef = null
                surfaceHolderRef = null
            },
            modifier = Modifier.fillMaxSize(),
        )

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
                            // Send Enter key
                            streamManager?.sendKeyboardInput(
                                0x0D.toShort(), KeyboardPacket.KEY_DOWN, 0, 0
                            )
                            streamManager?.sendKeyboardInput(
                                0x0D.toShort(), KeyboardPacket.KEY_UP, 0, 0
                            )
                            typingText = ""
                        }
                    ),
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Enter button
                Button(
                    onClick = {
                        streamManager?.sendKeyboardInput(
                            0x0D.toShort(), KeyboardPacket.KEY_DOWN, 0, 0
                        )
                        streamManager?.sendKeyboardInput(
                            0x0D.toShort(), KeyboardPacket.KEY_UP, 0, 0
                        )
                        typingText = ""
                    }
                ) {
                    Text("Enter")
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
                    Text(
                        text = statusText,
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

                    if (!isConnecting) {
                        Button(
                            onClick = { startStreaming() },
                            enabled = serverAddress.isNotBlank(),
                        ) {
                            Text(if (hasDisconnected) "Reconnect" else "Start Stream")
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

    fun stopStream() { onStopStream?.invoke() }
    fun showKeyboard() { onShowKeyboard?.invoke() }
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

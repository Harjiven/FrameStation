// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.streaming

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.view.Surface
import com.xrworkspace.app.model.AudioSettings
import com.xrworkspace.app.model.StreamSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.security.cert.X509Certificate

/**
 * Owns the **main desktop panel's** Moonlight stream *outside* the Compose hierarchy.
 *
 * This exists to fix the A1 "orphaned lifecycle" bug: today the main stream is owned by a
 * `remember { MoonlightStreamManager() }` inside [com.xrworkspace.app.ui.panels.NativeStreamPanel],
 * which is removed from composition the moment streaming starts — so the toolbar's Stop / Mute /
 * Keyboard controls (and the typing bar and gamepad capture) end up wired to a disposed composable.
 *
 * Held by `WorkspaceViewModel`, this session survives recomposition. It drives the pure
 * [StreamSessionState] machine ([reduce]) and applies the resulting [StreamEffect]s to a real
 * [MoonlightStreamManager] and [AutoReconnectManager]. The UI observes [state] and forwards
 * intents (start/stop/mute/text/input); it no longer owns the connection.
 *
 * Threading: [start] is invoked from the main thread (UI/ViewModel), so the [MoonlightStreamManager]
 * is created there, matching the "create on main thread" requirement the service path also honors.
 */
class MainStreamSession(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val scope: CoroutineScope,
    /** Propagates the streaming flag to the rest of the app (WorkspaceViewModel.setStreamingState). */
    private val onStreamingChanged: (Boolean) -> Unit,
) {
    companion object {
        private const val TAG = "MainStreamSession"
    }

    private val _state = MutableStateFlow(StreamSessionState())
    val state: StateFlow<StreamSessionState> = _state.asStateFlow()

    private val networkMonitor = NetworkMonitor(context)
    private val autoReconnect = AutoReconnectManager(networkMonitor, scope)

    private var manager: MoonlightStreamManager? = null
    private var surface: Surface? = null

    // Connection parameters captured via [configure] before [start].
    private var serverAddress: String = ""
    private var serverCert: X509Certificate? = null
    private var appId: Int? = null
    private var streamSettings: StreamSettings = StreamSettings()
    private var audioSettings: AudioSettings = AudioSettings()
    private var autoReconnectEnabled: Boolean = true

    /** The video Surface (from StreamVideoSurface). May arrive before or after [configure]/[start]. */
    fun setSurface(newSurface: Surface?) {
        surface = newSurface
    }

    /** Capture the parameters for the next [start]. Cheap; safe to call whenever inputs change. */
    fun configure(
        serverAddress: String,
        serverCert: X509Certificate?,
        appId: Int?,
        streamSettings: StreamSettings,
        audioSettings: AudioSettings,
        autoReconnectEnabled: Boolean,
    ) {
        this.serverAddress = serverAddress
        this.serverCert = serverCert
        this.appId = appId
        this.streamSettings = streamSettings
        this.audioSettings = audioSettings
        this.autoReconnectEnabled = autoReconnectEnabled
        autoReconnect.isEnabled = autoReconnectEnabled
    }

    // --- Intents (called by the UI / ViewModel) ---

    fun start() {
        if (surface == null) {
            _state.update { it.copy(statusText = "Surface not ready — try again in a moment") }
            return
        }
        dispatch(StreamEvent.StartRequested)
    }

    fun stop() = dispatch(StreamEvent.StopRequested)

    /** Called when the main desktop panel is hidden — stops the stream (A1 leak fix). */
    fun onDesktopPanelHidden() = dispatch(StreamEvent.DesktopPanelHidden)

    fun setMuted(muted: Boolean) {
        manager?.setMuted(muted)
    }

    fun sendUtf8Text(text: String) {
        manager?.sendUtf8Text(text)
    }

    fun sendKeyboardInput(keyMap: Short, keyDirection: Byte, modifier: Byte, flags: Byte) {
        manager?.sendKeyboardInput(keyMap, keyDirection, modifier, flags)
    }

    fun sendMousePosition(x: Short, y: Short, refWidth: Short, refHeight: Short) {
        manager?.sendMousePosition(x, y, refWidth, refHeight)
    }

    fun sendMouseButtonDown(button: Byte) {
        manager?.sendMouseButtonDown(button)
    }

    fun sendMouseButtonUp(button: Byte) {
        manager?.sendMouseButtonUp(button)
    }

    fun sendMouseScroll(scrollClicks: Byte) {
        manager?.sendMouseScroll(scrollClicks)
    }

    fun sendControllerInput(
        buttonFlags: Int,
        leftTrigger: Byte,
        rightTrigger: Byte,
        leftStickX: Short,
        leftStickY: Short,
        rightStickX: Short,
        rightStickY: Short,
    ) {
        manager?.sendControllerInput(
            buttonFlags, leftTrigger, rightTrigger,
            leftStickX, leftStickY, rightStickX, rightStickY,
        )
    }

    /** Release all resources. Call from WorkspaceViewModel.onCleared(). */
    fun release() {
        autoReconnect.stopMonitoring()
        networkMonitor.stopMonitoring()
        try {
            manager?.stopStream()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping manager during release", e)
        }
        manager = null
    }

    // --- State machine plumbing ---

    private fun dispatch(event: StreamEvent) {
        val reduction = _state.value.reduce(event, autoReconnectEnabled)
        _state.value = reduction.state
        reduction.effects.forEach { applyEffect(it) }
    }

    private fun applyEffect(effect: StreamEffect) {
        when (effect) {
            StreamEffect.StartStream -> {
                val s = surface
                if (s == null) {
                    Log.w(TAG, "StartStream effect with no surface — ignoring")
                    return
                }
                ensureManager().apply {
                    applyStreamSettings(streamSettings)
                    applyAudioSettings(audioSettings)
                    startStream(serverAddress, s, serverCert, appId)
                }
            }
            StreamEffect.StopStream -> manager?.stopStream()
            StreamEffect.CancelReconnect -> autoReconnect.cancelReconnect()
            StreamEffect.TriggerAutoReconnect -> autoReconnect.onStreamTerminated()
            is StreamEffect.NotifyStreamingChanged -> onStreamingChanged(effect.streaming)
        }
    }

    /** Lazily create the [MoonlightStreamManager] and start network/reconnect monitoring once. */
    private fun ensureManager(): MoonlightStreamManager {
        manager?.let { return it }
        val m = MoonlightStreamManager(context, prefs)
        m.onStageChanged = { stage -> dispatch(StreamEvent.StageChanged(stage)) }
        m.onConnectionStarted = { dispatch(StreamEvent.ConnectionStarted) }
        m.onConnectionTerminated = { reason ->
            dispatch(StreamEvent.ConnectionTerminated(reason, m.wasIntentionalStop()))
        }
        manager = m

        networkMonitor.startMonitoring()
        autoReconnect.isEnabled = autoReconnectEnabled
        autoReconnect.onReconnectGaveUp = { dispatch(StreamEvent.ReconnectGaveUp) }
        // Task 1.5 will make this await an actual connection result; today it mirrors the
        // existing "initiated == success" behavior of the panel-owned reconnect.
        autoReconnect.startMonitoring { m.reconnect() }
        return m
    }
}

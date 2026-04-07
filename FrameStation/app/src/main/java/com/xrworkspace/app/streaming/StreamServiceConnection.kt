// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.streaming

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import android.view.Surface
import com.xrworkspace.app.model.AudioSettings
import com.xrworkspace.app.model.StreamSettings
import org.json.JSONObject

/**
 * UI-process wrapper around a [StreamService] running in an isolated process.
 *
 * Binds to the service, forwards stream control calls over Binder, and receives
 * connection status callbacks via [IStreamServiceClient].
 *
 * Usage:
 * ```
 * val conn = StreamServiceConnection(context, processName = ":stream0")
 * conn.bind()
 * // When surface is ready:
 * conn.startStream(serverAddress, surface, streamSettings, audioSettings)
 * // To stop:
 * conn.stopStream()
 * // On cleanup:
 * conn.unbind()
 * ```
 */
class StreamServiceConnection(
    private val context: Context,
    /** ":stream0" or ":stream1" — must match android:process in manifest */
    val processName: String,
    /** Concrete service class to bind (StreamService0 or StreamService1). */
    private val serviceClass: Class<out StreamService>,
) {
    companion object {
        private const val TAG = "FrameStation-SvcConn"
    }

    // --- Callbacks (set by the caller before bind()) ---
    var onStageChanged: ((String) -> Unit)? = null
    var onConnectionStarted: (() -> Unit)? = null
    var onConnectionTerminated: ((String?) -> Unit)? = null
    /** Called when the service process dies unexpectedly. */
    var onServiceDied: (() -> Unit)? = null

    private var service: IStreamService? = null
    private var _isBound = false

    val isBound: Boolean get() = _isBound
    val isServiceAlive: Boolean get() = service != null

    // --- AIDL client stub (receives callbacks from the service process) ---

    private val clientCallback = object : IStreamServiceClient.Stub() {
        override fun onStageChanged(stage: String) {
            onStageChanged?.invoke(stage)
        }
        override fun onConnectionStarted() {
            onConnectionStarted?.invoke()
        }
        override fun onConnectionTerminated(reason: String) {
            onConnectionTerminated?.invoke(reason.ifBlank { null })
        }
    }

    // --- ServiceConnection ---

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            Log.i(TAG, "Connected to $processName")
            val svc = IStreamService.Stub.asInterface(binder)
            synchronized(this@StreamServiceConnection) {
                service = svc
                binderRef = binder
                _isBound = true
            }
            // Register death recipient so we know if the service process crashes
            try {
                binder.linkToDeath(deathRecipient, 0)
            } catch (e: Exception) {
                Log.w(TAG, "Could not link to death for $processName", e)
            }
            svc.registerClient(clientCallback)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Log.w(TAG, "Service $processName disconnected")
            synchronized(this@StreamServiceConnection) {
                service = null
                binderRef = null
                _isBound = false
            }
        }
    }

    private var binderRef: IBinder? = null  // held so we can unlinkToDeath on unbind

    // Forward declaration so the lambda can reference itself when unlinking.
    private lateinit var deathRecipient: IBinder.DeathRecipient
    init {
        deathRecipient = IBinder.DeathRecipient {
            Log.e(TAG, "StreamService process $processName died unexpectedly")
            // Unlink the death recipient FIRST so the same instance isn't fired twice
            // (e.g., if the app rebinds and the same binder gets a second crash event).
            synchronized(this@StreamServiceConnection) {
                try { binderRef?.unlinkToDeath(deathRecipient, 0) } catch (_: Exception) {}
                service = null
                binderRef = null
                _isBound = false
            }
            onServiceDied?.invoke()
        }
    }

    // --- Lifecycle ---

    fun bind() {
        val intent = Intent(context, serviceClass)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        Log.i(TAG, "Binding to $processName (${serviceClass.simpleName})")
    }

    fun unbind() {
        // Snapshot fields under the lock and clear them in one atomic step. This avoids a
        // race where deathRecipient nulls `service` between our null-check and our use of it.
        val (svcSnapshot, binderSnapshot) = synchronized(this) {
            if (!_isBound) return
            val s = service
            val b = binderRef
            service = null
            binderRef = null
            _isBound = false
            s to b
        }
        try {
            svcSnapshot?.unregisterClient(clientCallback)
            binderSnapshot?.let {
                try { it.unlinkToDeath(deathRecipient, 0) } catch (_: Exception) {}
            }
            context.unbindService(serviceConnection)
        } catch (e: Exception) {
            Log.w(TAG, "Error unbinding from $processName", e)
        }
    }

    // --- Stream control ---

    fun startStream(
        serverAddress: String,
        surface: Surface,
        streamSettings: StreamSettings,
        audioSettings: AudioSettings,
    ) {
        val svc = service ?: run {
            Log.w(TAG, "startStream called but service not bound ($processName) — call bind() first")
            return
        }
        svc.startStream(
            serverAddress,
            surface,
            serializeStreamSettings(streamSettings),
            serializeAudioSettings(audioSettings),
        )
    }

    fun stopStream() {
        service?.stopStream()
    }

    fun sendMousePosition(x: Short, y: Short, streamWidth: Short, streamHeight: Short) {
        service?.sendMousePosition(
            x.toInt(), y.toInt(), streamWidth.toInt(), streamHeight.toInt(),
        )
    }

    fun sendMouseButtonDown(button: Byte) {
        service?.sendMouseButtonDown(button.toInt())
    }

    fun sendMouseButtonUp(button: Byte) {
        service?.sendMouseButtonUp(button.toInt())
    }

    fun sendMouseScroll(amount: Byte) {
        service?.sendMouseScroll(amount.toInt())
    }

    fun sendKeyboardInput(keyMap: Short, direction: Byte, modifiers: Byte) {
        service?.sendKeyboardInput(keyMap.toInt(), direction.toInt(), modifiers.toInt())
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
        service?.sendControllerInput(
            buttonFlags,
            leftTrigger.toInt(),
            rightTrigger.toInt(),
            leftStickX.toInt(),
            leftStickY.toInt(),
            rightStickX.toInt(),
            rightStickY.toInt(),
        )
    }

    // --- JSON serializers ---

    private fun serializeStreamSettings(s: StreamSettings): String =
        JSONObject().apply {
            put("resolution", s.resolution.name)
            put("fps", s.fps)
            put("bitrateKbps", s.bitrateKbps)
            put("codec", s.codec.name)
            put("enableHdr", s.enableHdr)
        }.toString()

    private fun serializeAudioSettings(a: AudioSettings): String =
        JSONObject().apply {
            put("audioMode", a.audioMode.name)
        }.toString()
}

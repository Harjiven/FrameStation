# MoonBridge Process Isolation — True Multi-Stream Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Enable true simultaneous Moonlight streams by running each stream in a separate Android Service process, giving each its own isolated copy of `libmoonlight-core.so` and all its C globals.

**Architecture:** `moonlight-common-c` stores 30+ global C variables (`StreamConfig`, `VideoCallbacks`, `Decoder`, etc.) that prevent two `LiStartConnection()` calls in the same process. The fix: each stream runs in a dedicated `StreamService` Android process (`android:process=":stream0"`, `":stream1"`). The UI process binds to these services via AIDL (`IStreamService`). `Surface` objects (from `SpatialExternalSurface`) are passed over Binder — they are `Parcelable` and MediaCodec in the service process can render directly to them. The existing `MoonlightStreamManager` moves into the service and is called via IPC. The UI process lifts the `openStream()` 1-stream gate once multiple service processes are available.

**Tech Stack:** Kotlin, AIDL, Android Bound Services, `android:process`, `Surface` Binder passing, `IBinder.DeathRecipient`, `WorkspaceViewModel`

---

## Context — What Exists Today

- `MoonlightStreamManager.kt` — wraps `NvConnection`, `MediaCodecDecoderRenderer`, `SpatialAudioRenderer`. Lives in the UI process today. Will move to service process.
- `WorkspaceViewModel.openStream()` — gated to 1 stream with a comment referencing this plan.
- `SpatialWorkspace.kt` — renders stream panels from `uiState.activeStreamHostIds`.
- `NativeStreamPanel.kt` — creates `MoonlightStreamManager` internally via `rememberStreamController()`.
- `AndroidManifest.xml` — currently only declares `MainActivity`. Will add two `StreamService` entries.

**The IPC contract we're building:**
```
UI Process                          Service Process (:stream0 / :stream1)
─────────────────────────────────   ─────────────────────────────────────
IStreamServiceClient (callback) ←── StreamService (holds MoonlightStreamManager)
    ↓ bindService()
IStreamService ──────────────────→ startStream(serverAddress, surface, config)
                                    stopStream()
                                    sendMousePosition(x, y, w, h)
                                    sendMouseButtonDown/Up(button)
                                    sendKeyboardInput(keyMap, direction, modifiers)
                                    sendMouseScroll(amount)
```

---

## Task 1: Define the AIDL interfaces

AIDL files go in `app/src/main/aidl/com/xrworkspace/app/streaming/`.

**Files:**
- Create: `app/src/main/aidl/com/xrworkspace/app/streaming/IStreamServiceClient.aidl`
- Create: `app/src/main/aidl/com/xrworkspace/app/streaming/IStreamService.aidl`

**Step 1: Create the callback interface (service → UI)**

Write `IStreamServiceClient.aidl`:

```aidl
// SPDX-License-Identifier: GPL-3.0-or-later
package com.xrworkspace.app.streaming;

/**
 * Callbacks from a StreamService process to the UI process.
 * All methods are oneway — they don't block the service caller.
 */
oneway interface IStreamServiceClient {
    void onStageChanged(String stage);
    void onConnectionStarted();
    void onConnectionTerminated(String reason);
}
```

**Step 2: Create the service interface (UI → service)**

Write `IStreamService.aidl`:

```aidl
// SPDX-License-Identifier: GPL-3.0-or-later
package com.xrworkspace.app.streaming;

import com.xrworkspace.app.streaming.IStreamServiceClient;

/**
 * Interface exposed by StreamService to the UI process.
 * Lets the UI process control a stream running in an isolated process.
 */
interface IStreamService {
    /** Start streaming. surface must be a valid Surface from SpatialExternalSurface. */
    void startStream(
        String serverAddress,
        in Surface surface,
        String streamSettingsJson,
        String audioSettingsJson
    );

    /** Stop the active stream. */
    void stopStream();

    /** Send mouse position. Coordinates are in stream resolution space. */
    void sendMousePosition(int x, int y, int streamWidth, int streamHeight);
    void sendMouseButtonDown(int button);
    void sendMouseButtonUp(int button);
    void sendMouseScroll(int amount);
    void sendKeyboardInput(int keyMap, int direction, int modifiers);

    /** Register callback. Call immediately after binding. */
    void registerClient(IStreamServiceClient client);
    void unregisterClient(IStreamServiceClient client);
}
```

**Step 3: Verify build picks up AIDL files**

After creating both files, check the directory exists:
```
app/src/main/aidl/com/xrworkspace/app/streaming/IStreamService.aidl
app/src/main/aidl/com/xrworkspace/app/streaming/IStreamServiceClient.aidl
```

The Gradle build will auto-generate Java stubs in `build/generated/aidl_source_output_dir/`.

---

## Task 2: Implement `StreamService.kt`

This is the Android Service that runs in its own process and owns a `MoonlightStreamManager`.

**Files:**
- Create: `app/src/main/java/com/xrworkspace/app/streaming/StreamService.kt`

**Step 1: Write `StreamService.kt`**

```kotlin
// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.streaming

import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.os.IBinder
import android.os.RemoteCallbackList
import android.util.Log
import android.view.Surface
import com.xrworkspace.app.model.AudioSettings
import com.xrworkspace.app.model.StreamSettings
import org.json.JSONObject

/**
 * Android Service that runs in an isolated process (:stream0 or :stream1).
 *
 * Each process gets its own copy of libmoonlight-core.so with independent C globals,
 * enabling two simultaneous Moonlight streams without native library conflicts.
 */
class StreamService : Service() {

    companion object {
        private const val TAG = "FrameStation-StreamSvc"
    }

    private lateinit var prefs: SharedPreferences
    private var streamManager: MoonlightStreamManager? = null

    // RemoteCallbackList safely handles death of client processes
    private val clients = RemoteCallbackList<IStreamServiceClient>()

    private val binder = object : IStreamService.Stub() {

        override fun startStream(
            serverAddress: String,
            surface: Surface,
            streamSettingsJson: String,
            audioSettingsJson: String,
        ) {
            Log.i(TAG, "startStream: $serverAddress")
            val streamSettings = parseStreamSettings(streamSettingsJson)
            val audioSettings = parseAudioSettings(audioSettingsJson)

            // MoonlightStreamManager needs an Activity for some context — use applicationContext
            // wrapped in a thin ActivityWrapper that provides what the manager needs.
            val manager = MoonlightStreamManager(applicationContext, prefs)
            manager.onStageChanged = { stage -> broadcastStageChanged(stage) }
            manager.onConnectionStarted = { broadcastConnectionStarted() }
            manager.onConnectionTerminated = { reason -> broadcastConnectionTerminated(reason) }
            manager.applyStreamSettings(streamSettings)
            manager.applyAudioSettings(audioSettings)
            streamManager = manager
            manager.startStream(serverAddress, surface)
        }

        override fun stopStream() {
            Log.i(TAG, "stopStream")
            streamManager?.stopStream()
            streamManager = null
        }

        override fun sendMousePosition(x: Int, y: Int, streamWidth: Int, streamHeight: Int) {
            streamManager?.sendMousePosition(
                x.toShort(), y.toShort(),
                streamWidth.toShort(), streamHeight.toShort()
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
                keyMap.toShort(), direction.toByte(), modifiers.toByte(), 0.toByte()
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
        streamManager?.stopStream()
        clients.kill()
    }

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

    private fun parseStreamSettings(json: String): StreamSettings {
        return try {
            val obj = JSONObject(json)
            StreamSettings(
                resolution = com.xrworkspace.app.model.Resolution.entries
                    .find { it.name == obj.optString("resolution", "HD_1080") }
                    ?: com.xrworkspace.app.model.Resolution.HD_1080,
                fps = obj.optInt("fps", 60),
                bitrateKbps = obj.optInt("bitrateKbps", 20000),
                codec = com.xrworkspace.app.model.VideoCodec.entries
                    .find { it.name == obj.optString("codec", "AUTO") }
                    ?: com.xrworkspace.app.model.VideoCodec.AUTO,
            )
        } catch (_: Exception) {
            StreamSettings()
        }
    }

    private fun parseAudioSettings(json: String): AudioSettings {
        return try {
            val obj = JSONObject(json)
            AudioSettings(
                isMuted = obj.optBoolean("isMuted", false),
                mode = com.xrworkspace.app.model.AudioMode.entries
                    .find { it.name == obj.optString("mode", "STEREO") }
                    ?: com.xrworkspace.app.model.AudioMode.STEREO,
            )
        } catch (_: Exception) {
            AudioSettings()
        }
    }
}
```

**Step 2: Note — MoonlightStreamManager currently requires `Activity`**

`MoonlightStreamManager(activity: Activity, ...)` uses `activity.filesDir` and `activity.runOnUiThread`. In the service process there is no Activity.

These need to be changed to accept `Context` instead of `Activity`. This is Task 3.

---

## Task 3: Update `MoonlightStreamManager` to accept `Context` instead of `Activity`

**Files:**
- Modify: `app/src/main/java/com/xrworkspace/app/streaming/MoonlightStreamManager.kt`

**Step 1: Change constructor parameter from `Activity` to `Context`**

Find:
```kotlin
class MoonlightStreamManager(
    activity: Activity,
    private val prefs: SharedPreferences,
) : NvConnectionListener {
```
Replace with:
```kotlin
class MoonlightStreamManager(
    context: Context,
    private val prefs: SharedPreferences,
) : NvConnectionListener {
```

**Step 2: Replace `activity` field usages**

The class stores `activityRef = WeakReference(activity)` and uses it for:
- `dataDir = activity.filesDir` — replace with `context.filesDir` (stored as `private val dataDir`)
- `activity.runOnUiThread { ... }` — replace with `Handler(Looper.getMainLooper()).post { ... }`

Find:
```kotlin
    private val activityRef = WeakReference(activity)
    private val dataDir = activity.filesDir
```
Replace with:
```kotlin
    private val contextRef = WeakReference(context)
    private val dataDir = context.filesDir
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
```

**Step 3: Replace all `activityRef.get()?.runOnUiThread { ... }` calls**

Find every pattern:
```kotlin
activityRef.get()?.runOnUiThread {
```
Replace with:
```kotlin
mainHandler.post {
```

Find every direct `activity.runOnUiThread { ... }` (lines 153, 171, 264):
```kotlin
activity.runOnUiThread {
```
Replace with:
```kotlin
mainHandler.post {
```

**Step 4: Fix import — add `Context`, remove `Activity`**

Remove: `import android.app.Activity`
Add: `import android.content.Context`

**Step 5: Update all call sites in the UI process**

`NativeStreamPanel.kt` creates `MoonlightStreamManager(activity, prefs)`. Change to `MoonlightStreamManager(context, prefs)` — `LocalContext.current` is available inside a composable.

Search for all `MoonlightStreamManager(` call sites:
```
Grep: MoonlightStreamManager(
Expected: found in NativeStreamPanel.kt and StreamService.kt
```

Update `NativeStreamPanel.kt` call site — change `activity` to `context` (or `LocalContext.current`).

---

## Task 4: Declare `StreamService` in `AndroidManifest.xml`

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

**Step 1: Add two StreamService declarations inside `<application>`**

Find:
```xml
        <activity
            android:name=".MainActivity"
```
Add before it:
```xml
        <!-- Stream service processes — each runs in its own process for libmoonlight-core.so isolation -->
        <service
            android:name=".streaming.StreamService"
            android:process=":stream0"
            android:exported="false" />

        <service
            android:name=".streaming.StreamService"
            android:process=":stream1"
            android:exported="false" />

        <activity
            android:name=".MainActivity"
```

**Note:** Two `<service>` entries with the same `android:name` but different `android:process` values IS valid — Android creates separate processes for each unique process name. The same `StreamService` class handles both; the process name is the only differentiator.

---

## Task 5: Implement `StreamServiceConnection.kt` in the UI process

This is the UI-side wrapper that binds to a `StreamService` process and exposes a clean Kotlin API.

**Files:**
- Create: `app/src/main/java/com/xrworkspace/app/streaming/StreamServiceConnection.kt`

**Step 1: Write `StreamServiceConnection.kt`**

```kotlin
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
 * UI-process wrapper around a bound [StreamService] running in an isolated process.
 *
 * Usage:
 * ```
 * val conn = StreamServiceConnection(context, processName = ":stream0")
 * conn.bind()
 * conn.startStream(address, surface, settings, audio)
 * conn.stopStream()
 * conn.unbind()
 * ```
 */
class StreamServiceConnection(
    private val context: Context,
    private val processName: String, // ":stream0" or ":stream1"
) {
    companion object {
        private const val TAG = "FrameStation-SvcConn"
    }

    var onStageChanged: ((String) -> Unit)? = null
    var onConnectionStarted: (() -> Unit)? = null
    var onConnectionTerminated: ((String?) -> Unit)? = null
    var onServiceDied: (() -> Unit)? = null

    private var service: IStreamService? = null
    private var bound = false

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

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            Log.i(TAG, "Connected to $processName")
            val svc = IStreamService.Stub.asInterface(binder)
            service = svc
            bound = true
            // Register death recipient so we know if the service process crashes
            binder.linkToDeath(deathRecipient, 0)
            svc.registerClient(clientCallback)
        }
        override fun onServiceDisconnected(name: ComponentName) {
            Log.w(TAG, "Disconnected from $processName")
            service = null
            bound = false
        }
    }

    private val deathRecipient = IBinder.DeathRecipient {
        Log.e(TAG, "StreamService process $processName died")
        service = null
        bound = false
        onServiceDied?.invoke()
    }

    fun bind() {
        val intent = Intent(context, StreamService::class.java).apply {
            // Disambiguate which process to bind to via the process name as an extra.
            // Android starts the correct process based on the declared android:process in manifest.
            // We use the same class for both services; the process separation is manifest-driven.
            putExtra("processName", processName)
        }
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        Log.i(TAG, "Binding to $processName")
    }

    fun unbind() {
        if (bound) {
            service?.unregisterClient(clientCallback)
            context.unbindService(serviceConnection)
            bound = false
            service = null
        }
    }

    fun startStream(
        serverAddress: String,
        surface: Surface,
        streamSettings: StreamSettings,
        audioSettings: AudioSettings,
    ) {
        val svc = service ?: run {
            Log.w(TAG, "startStream called but service not bound ($processName)")
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

    fun sendMousePosition(x: Short, y: Short, w: Short, h: Short) {
        service?.sendMousePosition(x.toInt(), y.toInt(), w.toInt(), h.toInt())
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

    val isBound: Boolean get() = bound

    private fun serializeStreamSettings(s: StreamSettings): String =
        JSONObject().apply {
            put("resolution", s.resolution.name)
            put("fps", s.fps)
            put("bitrateKbps", s.bitrateKbps)
            put("codec", s.codec.name)
        }.toString()

    private fun serializeAudioSettings(a: AudioSettings): String =
        JSONObject().apply {
            put("isMuted", a.isMuted)
            put("mode", a.mode.name)
        }.toString()
}
```

---

## Task 6: Update `WorkspaceViewModel` to use `StreamServiceConnection`

Replace the in-process `openStream()` gate with service-backed connection management.

**Files:**
- Modify: `app/src/main/java/com/xrworkspace/app/viewmodel/WorkspaceViewModel.kt`

**Step 1: Add service connection fields**

In `WorkspaceViewModel`, add after the existing manager fields (around line 100):

```kotlin
    // Stream service connections — one per process slot
    // Initialized lazily on first openStream() call.
    private val streamSlots = mapOf(
        ":stream0" to StreamServiceConnection(application, ":stream0"),
        ":stream1" to StreamServiceConnection(application, ":stream1"),
    )
    private val hostToSlot = mutableMapOf<String, String>() // hostId → processName

    init {
        // Bind both service slots on ViewModel creation so they're ready when needed.
        streamSlots.values.forEach { it.bind() }
    }
```

**Step 2: Rewrite `openStream()`**

Replace:
```kotlin
    fun openStream(hostId: String) {
        _uiState.value.hostConfigs.find { it.id == hostId } ?: return
        // MoonBridge (the native JNI layer) holds static audioRenderer/videoRenderer/
        // connectionListener fields that are overwritten by each NvConnection instance.
        // Until MoonBridge is refactored to be instance-aware, only one stream can be
        // active at a time. Attempting a second stream would corrupt the first.
        if (_uiState.value.activeStreamHostIds.isNotEmpty()) {
            Log.w("WorkspaceViewModel", "Cannot open second stream — MoonBridge is single-instance. Stop the active stream first.")
            return
        }
        _uiState.update { state ->
            state.copy(
                activeStreamHostIds = state.activeStreamHostIds + hostId,
                showHostManager = false,
            )
        }
    }
```

With:
```kotlin
    fun openStream(hostId: String) {
        val host = _uiState.value.hostConfigs.find { it.id == hostId } ?: return
        // Find a free service slot (not currently in use by another host)
        val freeSlot = streamSlots.entries
            .firstOrNull { (processName, _) -> !hostToSlot.values.contains(processName) }
        if (freeSlot == null) {
            Log.w(TAG, "No free stream slots available (max ${streamSlots.size} simultaneous streams)")
            return
        }
        hostToSlot[hostId] = freeSlot.key
        _uiState.update { state ->
            state.copy(
                activeStreamHostIds = state.activeStreamHostIds + hostId,
                showHostManager = false,
            )
        }
        Log.i(TAG, "Assigned host $hostId to slot ${freeSlot.key}")
    }
```

**Step 3: Rewrite `closeStream()`**

Replace:
```kotlin
    fun closeStream(hostId: String) {
        _uiState.update { state ->
            state.copy(activeStreamHostIds = state.activeStreamHostIds - hostId)
        }
    }
```

With:
```kotlin
    fun closeStream(hostId: String) {
        val slotName = hostToSlot.remove(hostId)
        val slot = slotName?.let { streamSlots[it] }
        slot?.stopStream()
        _uiState.update { state ->
            state.copy(activeStreamHostIds = state.activeStreamHostIds - hostId)
        }
    }
```

**Step 4: Clean up on `onCleared()`**

Add to `onCleared()`:
```kotlin
        streamSlots.values.forEach { it.unbind() }
```

**Step 5: Expose `getStreamSlot(hostId)` for `NativeStreamPanel`**

`NativeStreamPanel` needs to call the right slot for input events. Add:
```kotlin
    fun getStreamSlot(hostId: String): StreamServiceConnection? =
        hostToSlot[hostId]?.let { streamSlots[it] }
```

And add `activeStreamHostIds` → `getStreamSlot` lookup to `WorkspaceUiState` or pass the slot directly to `NativeStreamPanel` via `SpatialWorkspace`.

---

## Task 7: Update `NativeStreamPanel` to use `StreamServiceConnection`

The panel currently creates its own `MoonlightStreamManager` directly. For process-isolated panels, it receives a `StreamServiceConnection` instead.

**Files:**
- Modify: `app/src/main/java/com/xrworkspace/app/ui/panels/NativeStreamPanel.kt`

**Step 1: Add optional `streamServiceConnection` parameter**

Add to `NativeStreamPanel` parameters:
```kotlin
    streamServiceConnection: StreamServiceConnection? = null,
```

**Step 2: Use `streamServiceConnection` when provided**

In `startStreaming()`, if `streamServiceConnection != null`, call through it instead of the local `streamManager`. Wire the `SpatialExternalSurface.onSurfaceCreated` callback to `streamServiceConnection.startStream(...)` when set.

**Step 3: Wire input events through `streamServiceConnection`**

In the `SpatialExternalSurface` `InteractionPolicy.onInputEvent`, route input to `streamServiceConnection` if provided, otherwise fall back to local `streamManager`.

---

## Task 8: Pass `StreamServiceConnection` through `SpatialWorkspace`

**Files:**
- Modify: `app/src/main/java/com/xrworkspace/app/ui/SpatialWorkspace.kt`
- Modify: `app/src/main/java/com/xrworkspace/app/ui/XRWorkspaceApp.kt`

**Step 1: Add `getStreamSlot` callback to `SpatialWorkspace`**

Add parameter:
```kotlin
    onGetStreamSlot: (hostId: String) -> StreamServiceConnection? = { null },
```

**Step 2: Pass slot to each `NativeStreamPanel` in the multi-stream block**

```kotlin
NativeStreamPanel(
    serverAddress = host.address,
    streamSettings = host.qualityProfile ?: uiState.streamSettings,
    audioSettings = uiState.audioSettings,
    autoReconnectEnabled = uiState.autoReconnectEnabled,
    onStreamingStateChanged = {},
    streamController = hostController,
    streamServiceConnection = onGetStreamSlot(host.id),
)
```

**Step 3: Wire in `XRWorkspaceApp.kt`**

```kotlin
onGetStreamSlot = { hostId -> viewModel.getStreamSlot(hostId) },
```

---

## Task 9: Verify and commit

**Step 1: Grep for `Activity` import in `MoonlightStreamManager.kt`**
```
Expected: not present (replaced by Context)
```

**Step 2: Verify AIDL files are in the right location**
```
app/src/main/aidl/com/xrworkspace/app/streaming/IStreamService.aidl    ✓
app/src/main/aidl/com/xrworkspace/app/streaming/IStreamServiceClient.aidl ✓
```

**Step 3: Verify manifest has both service declarations**
```
Grep: android:process=":stream0"
Grep: android:process=":stream1"
Expected: found in AndroidManifest.xml
```

**Step 4: Commit**

```bash
git add -A
git commit -m "feat: process isolation for true simultaneous Moonlight streams

Run each stream in a separate Android Service process (:stream0, :stream1)
to give each its own copy of libmoonlight-core.so with independent C
globals (StreamConfig, VideoCallbacks, AudioDecoder, etc.).

- IStreamService / IStreamServiceClient AIDL interfaces for IPC
- StreamService: holds MoonlightStreamManager in isolated process
- StreamServiceConnection: UI-process wrapper around bound StreamService
- MoonlightStreamManager: Activity -> Context, runOnUiThread -> mainHandler
- WorkspaceViewModel: openStream() assigns hostId to free service slot,
  closeStream() stops stream and releases slot; max 2 simultaneous streams
- NativeStreamPanel: accepts optional StreamServiceConnection for IPC path
- Manifest: two StreamService declarations with :stream0 / :stream1 processes
- Remove openStream() single-stream gate (MoonBridge limitation resolved)"
```

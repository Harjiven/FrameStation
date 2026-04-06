# SpatialExternalSurface Migration Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Migrate the Moonlight video renderer from `AndroidView(SurfaceView)` to `SpatialExternalSurface` to eliminate AndroidView compositing overhead and reduce streaming latency.

**Architecture:** `SpatialExternalSurface` gives us a raw `android.view.Surface` via an `onSurfaceCreated` callback. `MediaCodecDecoderRenderer` currently requires a `SurfaceHolder` and calls `.getSurface()` internally. We add a `setRenderTarget(Surface)` overload to the Java decoder, update `MoonlightStreamManager` to accept a `Surface` instead of a `SurfaceHolder`, and replace the `AndroidView(SurfaceView)` block in `NativeStreamPanel` with a `SpatialExternalSurface`. Input (touch-to-mouse) moves from `MotionEvent` on the SurfaceView to `SpatialInputEvent.hitPosition` on the `SpatialExternalSurface`. The `SpatialExternalSurface` does not support `.alpha()`, so the fade-in animation is removed from the stream panel (content starts black until the first frame, which is natural).

**Tech Stack:** Kotlin, Jetpack Compose for XR 1.0.0-alpha10, `androidx.xr.compose.subspace.SpatialExternalSurface`, `StereoMode.Mono`, Java `MediaCodecDecoderRenderer` (moonlight-core)

---

## Context

**The current pipeline (to be replaced in Tasks 1–3):**
```
SurfaceView (AndroidView) → SurfaceHolder → SurfaceHolder.getSurface() → MediaCodec.configure()
```

**The target pipeline:**
```
SpatialExternalSurface.onSurfaceCreated(surface) → MediaCodec.configure(surface)
```

**Files to touch:**
- Modify: `moonlight-core/src/main/java/com/limelight/binding/video/MediaCodecDecoderRenderer.java` — add `setRenderTarget(Surface)` overload
- Modify: `app/src/main/java/com/xrworkspace/app/streaming/MoonlightStreamManager.kt` — replace `SurfaceHolder` with `Surface` throughout
- Modify: `app/src/main/java/com/xrworkspace/app/ui/panels/NativeStreamPanel.kt` — replace `AndroidView(SurfaceView)` with `SpatialExternalSurface`

**Key constraints:**
- `SpatialExternalSurface` is in `androidx.xr.compose.subspace.SpatialExternalSurface` (already in deps)
- `StereoMode` is in `androidx.xr.compose.subspace.StereoMode`
- `SpatialExternalSurface` does NOT support `.alpha()` SubspaceModifier — do not attempt to animate it
- `SpatialExternalSurface` must live inside `Subspace { }` — it already does (in `SpatialWorkspace.kt`)
- The `onSurfaceCreated` callback fires exactly once; the Surface is valid until `onSurfaceDestroyed`
- Input via `SpatialInputEvent` — `hitPosition` is pixel offset from surface center (not top-left)
- Touch coordinate mapping must convert from center-origin to top-left-origin before scaling to stream coords

**Existing behavior to preserve (all must still work):**
- Connect/disconnect overlay when not streaming
- Status text updates during connection stages
- Auto-reconnect on network drop
- Wake-on-LAN button
- App selector chip
- Stop stream button (toolbar)
- Soft keyboard / typing bar
- Hardware keyboard forwarding (still via physical keyboard events — no change needed there)
- Audio mute toggle

---

## Task 1: Add `setRenderTarget(Surface)` overload to `MediaCodecDecoderRenderer.java`

This is the only change to moonlight-core. We add a second `setRenderTarget` that accepts a plain `Surface`, and update `configureAndStartDecoder` to use whichever was set.

**Files:**
- Modify: `moonlight-core/src/main/java/com/limelight/binding/video/MediaCodecDecoderRenderer.java`

**Step 1: Find the existing `setRenderTarget` method**

Search for `setRenderTarget` in `MediaCodecDecoderRenderer.java`. It looks like:
```java
public void setRenderTarget(SurfaceHolder renderTarget) {
    this.renderTarget = renderTarget;
}
```
Note the field name `renderTarget` and its type `SurfaceHolder`.

**Step 2: Add a `directSurface` field and the new overload**

Immediately after the existing `setRenderTarget(SurfaceHolder)` method, add:

```java
// Set when using SpatialExternalSurface — bypasses SurfaceHolder.getSurface()
private android.view.Surface directSurface;

public void setRenderTarget(android.view.Surface surface) {
    this.directSurface = surface;
}
```

**Step 3: Update `configureAndStartDecoder` to use `directSurface` when set**

Find the line in `configureAndStartDecoder` that reads:
```java
videoDecoder.configure(format, renderTarget.getSurface(), null, 0);
```

Replace it with:
```java
android.view.Surface outputSurface = (directSurface != null)
    ? directSurface
    : renderTarget.getSurface();
videoDecoder.configure(format, outputSurface, null, 0);
```

**Step 4: Verify no compile errors are introduced**

Search for all other uses of `renderTarget.getSurface()` in the file. If any exist, apply the same pattern — prefer `directSurface` if set.

---

## Task 2: Update `MoonlightStreamManager.kt` to accept `Surface` instead of `SurfaceHolder`

Replace all `SurfaceHolder` references in the manager with `android.view.Surface`.

**Files:**
- Modify: `app/src/main/java/com/xrworkspace/app/streaming/MoonlightStreamManager.kt`

**Step 1: Replace the import**

Remove:
```kotlin
import android.view.SurfaceHolder
```
Add:
```kotlin
import android.view.Surface
```

**Step 2: Update `startStream` signature**

Change (line 117):
```kotlin
fun startStream(
    serverAddress: String,
    surfaceHolder: SurfaceHolder,
    serverCert: X509Certificate? = null,
    appId: Int? = null,
)
```
To:
```kotlin
fun startStream(
    serverAddress: String,
    surface: Surface,
    serverCert: X509Certificate? = null,
    appId: Int? = null,
)
```

**Step 3: Update `lastSurfaceHolder` field**

Change (line 71):
```kotlin
private var lastSurfaceHolder: SurfaceHolder? = null
```
To:
```kotlin
private var lastSurface: Surface? = null
```

**Step 4: Update all uses inside `startStream`**

Replace the two uses of `surfaceHolder`:
- Line 123: `lastSurfaceHolder = surfaceHolder` → `lastSurface = surface`
- Line 251: `videoRenderer?.setRenderTarget(surfaceHolder)` → `videoRenderer?.setRenderTarget(surface)`

**Step 5: Update `reconnect()`**

Change (lines 317–321):
```kotlin
fun reconnect(): Boolean {
    val address = lastServerAddress ?: return false
    val holder = lastSurfaceHolder ?: return false
    Log.i(TAG, "Reconnecting to $address")
    startStream(address, holder, lastServerCert)
    return true
}
```
To:
```kotlin
fun reconnect(): Boolean {
    val address = lastServerAddress ?: return false
    val surface = lastSurface ?: return false
    Log.i(TAG, "Reconnecting to $address")
    startStream(address, surface, lastServerCert)
    return true
}
```

---

## Task 3: Replace `AndroidView(SurfaceView)` with `SpatialExternalSurface` in `NativeStreamPanel.kt`

This is the main UI change. The `AndroidView` block (lines 246–326) is replaced with `SpatialExternalSurface`. Input handling moves from `MotionEvent` to `SpatialInputEvent`.

**Files:**
- Modify: `app/src/main/java/com/xrworkspace/app/ui/panels/NativeStreamPanel.kt`

**Step 1: Update imports**

Remove these imports (no longer needed):
```kotlin
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import androidx.compose.ui.viewinterop.AndroidView
```

Add these imports:
```kotlin
import android.view.Surface
import androidx.xr.compose.subspace.SpatialExternalSurface
import androidx.xr.compose.subspace.StereoMode
import androidx.xr.compose.subspace.layout.InteractionPolicy
import androidx.xr.compose.subspace.layout.SpatialInputEvent
```

**Step 2: Replace the state variables**

Remove (lines 103–104):
```kotlin
var surfaceHolderRef by remember { mutableStateOf<SurfaceHolder?>(null) }
var surfaceViewRef by remember { mutableStateOf<SurfaceView?>(null) }
```

Replace with:
```kotlin
var surfaceRef by remember { mutableStateOf<Surface?>(null) }
```

**Step 3: Update `startStreaming()` to use `Surface`**

Change (lines 194–207):
```kotlin
fun startStreaming() {
    autoReconnectManager.cancelReconnect()
    val holder = surfaceHolderRef
    if (holder != null && activity != null) {
        isConnecting = true
        statusText = "Connecting..."
        streamManager?.applyStreamSettings(streamSettings)
        streamManager?.applyAudioSettings(audioSettings)
        streamManager?.startStream(serverAddress, holder, serverCert, selectedAppId)
    } else {
        statusText = "Surface not ready — wait a moment and try again"
    }
}
```

To:
```kotlin
fun startStreaming() {
    autoReconnectManager.cancelReconnect()
    val surface = surfaceRef
    if (surface != null && activity != null) {
        isConnecting = true
        statusText = "Connecting..."
        streamManager?.applyStreamSettings(streamSettings)
        streamManager?.applyAudioSettings(audioSettings)
        streamManager?.startStream(serverAddress, surface, serverCert, selectedAppId)
    } else {
        statusText = "Surface not ready — wait a moment and try again"
    }
}
```

**Step 4: Replace the `AndroidView(SurfaceView)` block with `SpatialExternalSurface`**

Remove the entire `AndroidView` block (lines 246–326):
```kotlin
AndroidView(
    factory = { ctx ->
        SurfaceView(ctx).apply {
            // ... all of this ...
        }
    },
    onRelease = {
        surfaceViewRef = null
        surfaceHolderRef = null
    },
    modifier = Modifier.fillMaxSize(),
)
```

Replace with:

```kotlin
SpatialExternalSurface(
    stereoMode = StereoMode.Mono,
    interactionPolicy = object : InteractionPolicy {
        override val isEnabled: Boolean = true
        override fun onInputEvent(event: SpatialInputEvent) {
            if (streamManager == null || !isConnected) return

            // hitPosition is pixel offset from surface CENTER.
            // Convert to top-left origin by adding half the panel size.
            // Panel is 1400x900dp; assume 1:1 dp-to-pixel mapping for coordinate math.
            // The stream resolution is in streamManager.streamWidth/Height.
            val hitPos = event.hitPosition ?: return
            val panelHalfW = 700f  // half of 1400dp panel width
            val panelHalfH = 450f  // half of 900dp panel height
            val normX = ((hitPos.x + panelHalfW) / (panelHalfW * 2f)).coerceIn(0f, 1f)
            val normY = ((hitPos.y + panelHalfH) / (panelHalfH * 2f)).coerceIn(0f, 1f)
            val streamX = (normX * streamManager.streamWidth).toInt()
                .coerceIn(0, streamManager.streamWidth - 1).toShort()
            val streamY = (normY * streamManager.streamHeight).toInt()
                .coerceIn(0, streamManager.streamHeight - 1).toShort()
            val streamW = streamManager.streamWidth.toShort()
            val streamH = streamManager.streamHeight.toShort()

            when (event.action) {
                SpatialInputEvent.Action.DOWN -> {
                    streamManager.sendMousePosition(streamX, streamY, streamW, streamH)
                    streamManager.sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT)
                }
                SpatialInputEvent.Action.MOVE -> {
                    streamManager.sendMousePosition(streamX, streamY, streamW, streamH)
                }
                SpatialInputEvent.Action.UP -> {
                    streamManager.sendMousePosition(streamX, streamY, streamW, streamH)
                    streamManager.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT)
                }
                else -> { /* hover / other events — ignore */ }
            }
        }
    },
) {
    onSurfaceCreated { surface ->
        Log.i("NativeStreamPanel", "SpatialExternalSurface created")
        surfaceRef = surface
    }
    onSurfaceDestroyed { _ ->
        Log.i("NativeStreamPanel", "SpatialExternalSurface destroyed")
        surfaceRef = null
        streamManager?.stopStream()
    }
}
```

**Step 5: Remove the `SurfaceHolder` cleanup from `DisposableEffect`**

In the `DisposableEffect` `onDispose` block, remove any reference to `surfaceHolderRef` or `surfaceViewRef`.

**Step 6: Verify `SpatialInputEvent.Action` import is correct**

Check whether the action constants are accessed as `SpatialInputEvent.Action.DOWN` or `SpatialInputEvent.Action.ACTION_DOWN`. Adjust if needed based on the actual API.

---

## Task 4: Verify and commit

**Step 1: Grep for stray `SurfaceHolder` references in app source**

```
Grep pattern: SurfaceHolder
Path: FrameStation/app/src/
Expected: zero matches (moonlight-core still uses it internally — that's fine)
```

**Step 2: Grep for stray `surfaceHolderRef` / `surfaceViewRef` references**

```
Grep pattern: surfaceHolderRef|surfaceViewRef
Path: FrameStation/app/src/
Expected: zero matches
```

**Step 3: Verify `SpatialExternalSurface` import resolves**

Check that `SpatialExternalSurface` and `StereoMode` are accessible from `androidx.xr.compose.subspace` — they are in `androidx.xr.compose:compose:1.0.0-alpha10` (already a dependency).

**Step 4: Commit**

```bash
git add -A
git commit -m "feat: migrate streaming panel to SpatialExternalSurface

Replace AndroidView(SurfaceView) with SpatialExternalSurface for
lower-latency video rendering. Add setRenderTarget(Surface) overload to
MediaCodecDecoderRenderer to accept a plain Surface directly. Update
MoonlightStreamManager.startStream() to accept Surface instead of
SurfaceHolder. Migrate touch input from MotionEvent to SpatialInputEvent
with hitPosition coordinate mapping. Remove alpha fade-in from stream
panel (not supported by SpatialExternalSurface)."
```

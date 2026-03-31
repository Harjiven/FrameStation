# XR Workspace v0.2 — Native Moonlight Streaming Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the WebView desktop streaming panel with native Moonlight streaming (embedded moonlight-android core) for ~20-40ms latency. The app becomes GPLv3 open-source.

**Architecture:** Extract moonlight-android's streaming core into a `:moonlight-core` Gradle module. Pre-build `libmoonlight-core.so` from moonlight-android's existing ndk-build. Render decoded video via `SpatialPanel + AndroidView(SurfaceView)` (gives free touch input). XR hand tracking, controller, and BT keyboard/mouse all route to `MoonBridge.sendMousePosition()` / `sendKeyboardInput()`. Server is Apollo (GameStream-compatible). Manual IP entry for connection — no mDNS discovery (deferred to v0.3).

**Tech Stack:** Kotlin, Java (moonlight core), C (moonlight-common-c), Jetpack XR SDK alpha10/11, NDK 27, ndk-build, BouncyCastle 1.77, OkHttp 4.12.0, jmdns 3.5.9, jcodec 0.2.5. GPLv3 license.

---

## Prerequisites

1. Apollo (or Sunshine) running on your gaming PC, accessible at a known IP
2. Apollo paired with at least one client (for initial testing, pair via Apollo's web UI)
3. NDK 27 installed via Android Studio SDK Manager (SDK Tools → NDK Side by side)
4. Galaxy XR on same WiFi network as gaming PC

---

## Phase 1: Foundation (Week 1)

### Task 1: Create `:moonlight-core` Library Module

**Files to create:**
- `moonlight-core/build.gradle.kts` — Android library module with moonlight dependencies
- `moonlight-core/src/main/AndroidManifest.xml` — Minimal manifest
- Update `settings.gradle.kts` — Add `include(":moonlight-core")`
- Update `app/build.gradle.kts` — Add `implementation(project(":moonlight-core"))`

**Steps:**
1. Create `G:\OpenCode\XR\xr-workspace\moonlight-core\` directory
2. Create `moonlight-core/build.gradle.kts`:
   ```kotlin
   plugins {
       id("com.android.library")
   }
   android {
       namespace = "com.limelight"
       compileSdk = 36
       defaultConfig { minSdk = 34 }
   }
   java {
       toolchain { languageVersion = JavaLanguageVersion.of(17) }
   }
   dependencies {
       implementation("org.bouncycastle:bcprov-jdk18on:1.77")
       implementation("org.bouncycastle:bcpkix-jdk18on:1.77")
       implementation("com.squareup.okhttp3:okhttp:4.12.0")
       implementation("org.jmdns:jmdns:3.5.9")
       implementation("org.jcodec:jcodec:0.2.5")
   }
   ```
3. Create minimal `moonlight-core/src/main/AndroidManifest.xml`
4. Add `include(":moonlight-core")` to `settings.gradle.kts`
5. Add `implementation(project(":moonlight-core"))` to `app/build.gradle.kts`
6. Build to verify module resolves: `.\gradlew assembleDebug`

**QA:** Build succeeds with new module.

### Task 2: Clone moonlight-android and Pre-build Native Library

**Steps:**
1. Clone moonlight-android repo to a SEPARATE directory (not inside xr-workspace):
   ```powershell
   cd G:\OpenCode\XR
   git clone --recursive https://github.com/moonlight-stream/moonlight-android.git
   ```
   The `--recursive` flag pulls in `moonlight-common-c` submodule.

2. Open the cloned moonlight-android in Android Studio and build it once to ensure the native library compiles. This validates NDK + ndk-build + all prebuilt dependencies (OpenSSL, opus) are working.

3. After successful build, locate the pre-built `.so` files at:
   `moonlight-android/app/build/intermediates/ndkBuild/debug/obj/local/arm64-v8a/libmoonlight-core.so`
   (or similar path under build intermediates)

4. Copy the arm64-v8a `.so` into our project:
   ```powershell
   mkdir -p xr-workspace/moonlight-core/src/main/jniLibs/arm64-v8a
   cp moonlight-android/app/build/.../arm64-v8a/libmoonlight-core.so xr-workspace/moonlight-core/src/main/jniLibs/arm64-v8a/
   ```

5. Build xr-workspace to verify the .so is packaged: `.\gradlew assembleDebug`

**QA:** APK contains `lib/arm64-v8a/libmoonlight-core.so`. Verify with:
```bash
adb install -r app-debug.apk
adb shell "run-as com.xrworkspace.app ls lib/arm64/"
```

### Task 3: Copy Moonlight Java Streaming Core

**Copy these packages from moonlight-android into `moonlight-core/src/main/java/`:**

```
com/limelight/nvstream/
├── NvConnection.java
├── NvConnectionListener.java
├── StreamConfiguration.java
├── ConnectionContext.java
├── av/
│   ├── audio/AudioRenderer.java
│   ├── video/VideoDecoderRenderer.java
│   └── video/VideoStats.java
├── http/
│   ├── NvHTTP.java
│   ├── NvApp.java
│   ├── PairingManager.java
│   ├── ComputerDetails.java
│   ├── LimelightCryptoProvider.java
│   └── ... (all files in http/)
├── jni/
│   └── MoonBridge.java
├── input/
│   ├── ControllerPacket.java
│   ├── KeyboardPacket.java
│   └── MouseButtonPacket.java (if exists as separate class)

com/limelight/binding/
├── PlatformBinding.java
├── video/
│   ├── MediaCodecDecoderRenderer.java
│   ├── MediaCodecHelper.java
│   └── CrashListener.java
├── audio/
│   └── AndroidAudioRenderer.java
├── crypto/
│   └── AndroidCryptoProvider.java

com/limelight/
├── LimeLog.java
├── preferences/PreferenceConfiguration.java (or create minimal adapter)
```

**IMPORTANT:** Do NOT copy the UI files (Game.java, activities, fragments) or the 29-file input package (`com.limelight.binding.input.*`). We write XR-native input from scratch.

**Steps:**
1. Copy the files listed above
2. Fix any missing imports — some files may reference classes we didn't copy. Create stub/adapter classes as needed.
3. Build to verify compilation: `.\gradlew :moonlight-core:compileDebugJavaWithJavac`
4. This WILL have compilation errors — fix them iteratively by:
   - Creating stub classes for missing UI references
   - Removing methods that reference Android UI classes we don't need
   - Creating a minimal `PreferenceConfiguration` adapter if the full class has too many UI dependencies

**QA:** `:moonlight-core` module compiles with no errors.

### Task 4: Verify MoonBridge Native Library Loads

**Steps:**
1. In `:moonlight-core`, add a simple test or a public init method:
   ```java
   // In MoonBridge.java or a new MoonlightCore.java
   public static boolean init() {
       try {
           System.loadLibrary("moonlight-core");
           return true;
       } catch (UnsatisfiedLinkError e) {
           Log.e("MoonlightCore", "Failed to load native library", e);
           return false;
       }
   }
   ```

2. Call this from the XR app's `MainActivity.onCreate()` and log the result.

3. Deploy to Galaxy XR and check logcat:
   ```bash
   adb -s 192.168.1.226:37425 logcat -s MoonlightCore:D
   ```

**QA:** Logcat shows "moonlight-core loaded successfully" (or similar). No `UnsatisfiedLinkError`.

**CHECKPOINT: MoonBridge.init() succeeds on physical Galaxy XR device.**

---

## Phase 2: First Frame (Week 2)

### Task 5: Create Surface Adapter for MediaCodecDecoderRenderer

`MediaCodecDecoderRenderer.setRenderTarget()` expects a `SurfaceHolder`. We need it to accept a raw `Surface` since `AndroidView(SurfaceView)` gives us a `SurfaceHolder` naturally — so this actually works out of the box.

**Steps:**
1. Verify that `MediaCodecDecoderRenderer` only calls `renderTarget.getSurface()` and nothing else on the SurfaceHolder. Search all usages.
2. If only `getSurface()` is called, add an overload:
   ```java
   // In MediaCodecDecoderRenderer.java
   private Surface directSurface;

   public void setRenderTarget(Surface surface) {
       this.directSurface = surface;
   }

   // Modify the configure call to use directSurface if set:
   // videoDecoder.configure(format, directSurface != null ? directSurface : renderTarget.getSurface(), null, 0);
   ```
3. If `SurfaceHolder`-specific methods ARE called, create a `FakeSurfaceHolder` wrapper.

**QA:** Build succeeds. Surface adapter compiles.

### Task 6: Create NativeStreamPanel Composable

**Create:** `app/src/main/java/com/xrworkspace/app/ui/panels/NativeStreamPanel.kt`

This replaces the WebView-based `DesktopStreamPanel` with a native SurfaceView that receives decoded video from Moonlight.

```kotlin
@Composable
fun NativeStreamPanel(
    serverAddress: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as? Activity

    AndroidView(
        factory = { ctx ->
            SurfaceView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        // Start Moonlight streaming connection
                        startMoonlightStream(activity, holder, serverAddress)
                    }
                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        // Stop streaming
                        stopMoonlightStream()
                    }
                })
            }
        },
        modifier = modifier.fillMaxSize(),
    )
}
```

The `startMoonlightStream()` function:
1. Creates `StreamConfiguration` with resolution/bitrate/codec
2. Creates `NvConnection` with server address
3. Creates `MediaCodecDecoderRenderer` with the SurfaceHolder
4. Creates `AndroidAudioRenderer`
5. Calls `conn.start(audioRenderer, videoRenderer, connectionListener)`

**Steps:**
1. Create `NativeStreamPanel.kt` with the pattern above
2. Create a `MoonlightStreamManager` class that encapsulates the connection lifecycle
3. Wire it up but DON'T replace DesktopStreamPanel yet — add it as a SECOND option for testing

**QA:** Build succeeds.

### Task 7: First Streaming Connection — Hardcoded

**Steps:**
1. Ensure Apollo is running on your PC and has "Desktop" as a launchable app
2. Ensure your PC is already paired (pair via Apollo's web UI if needed)
3. Hardcode the PC's IP address in `NativeStreamPanel`
4. Set stream config: 1080p, 60fps, 20Mbps bitrate, H.264
5. Deploy to Galaxy XR
6. The panel should show your PC desktop streaming

**Debugging if it doesn't work:**
- Check logcat for `NvConnectionListener` callbacks (connectionStarted, stageFailed, etc.)
- Verify network connectivity: `adb shell ping <pc-ip>`
- Verify Apollo is accessible: `adb shell curl https://<pc-ip>:47984/serverinfo` (ignore TLS errors)

**QA:** PC desktop is visible in the XR streaming panel. Video is smooth at 60fps. Audio plays through headset.

**CHECKPOINT: Video frames visible on Galaxy XR panel from Apollo/Sunshine streaming.**

---

## Phase 3: Input Handling (Week 3)

### Task 8: Touch-to-Mouse Coordinate Mapping

**Steps:**
1. Add `OnTouchListener` to the SurfaceView in `NativeStreamPanel`
2. Map touch coordinates to stream coordinates:
   ```kotlin
   view.setOnTouchListener { v, event ->
       val streamX = (event.x / v.width * streamWidth).toInt().toShort()
       val streamY = (event.y / v.height * streamHeight).toInt().toShort()
       when (event.action) {
           MotionEvent.ACTION_DOWN -> {
               MoonBridge.sendMousePosition(streamX, streamY, streamWidth, streamHeight)
               MoonBridge.sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT)
           }
           MotionEvent.ACTION_MOVE -> {
               MoonBridge.sendMousePosition(streamX, streamY, streamWidth, streamHeight)
           }
           MotionEvent.ACTION_UP -> {
               MoonBridge.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT)
           }
       }
       true
   }
   ```
3. This handles hand tracking ray-cast AND controller ray-cast — both generate Android touch events when they hit a SpatialPanel's content.

**QA:** Touch on the streaming panel moves the mouse cursor on the remote PC. Click opens items.

### Task 9: Keyboard Input Forwarding

**Steps:**
1. Add a "keyboard" toggle button to the streaming panel's Orbiter
2. When toggled, show Android's soft keyboard (IME)
3. Capture key events and forward to `MoonBridge.sendKeyboardInput()`
4. Also handle hardware BT keyboard via `onKeyDown` / `onKeyUp` on the SurfaceView (set it focusable)

**QA:** Type text in Notepad on the remote PC via XR keyboard and BT keyboard.

### Task 10: Connection Lifecycle Management

**Steps:**
1. Handle `NvConnectionListener` callbacks:
   - `connectionStarted()` → update UI state
   - `connectionTerminated()` → show reconnection UI
   - `stageFailed()` → show error message with stage name
2. Handle Android lifecycle (pause/resume/destroy):
   - `onPause` → keep streaming (background audio)
   - `onDestroy` → disconnect gracefully
3. Handle network changes (WiFi disconnect → reconnect)

**QA:** Disconnect WiFi → app shows "Connection lost". Reconnect WiFi → app reconnects automatically.

**CHECKPOINT: Can control remote desktop via touch + keyboard. Connection recovers from network interruption.**

---

## Phase 4: Pairing & Settings (Week 4)

### Task 11: NvHTTP Client + Server Info

**Steps:**
1. Create a `ServerManager` class that wraps `NvHTTP`
2. Given an IP address, call `NvHTTP.getServerInfo()` to verify server is reachable
3. Display server name, GPU, running app in the settings panel

**QA:** Enter PC IP → app shows "Connected to [PC Name] (NVIDIA RTX XXXX)"

### Task 12: Pairing Flow UI

**Steps:**
1. Create a `PairingDialog` composable (separate SpatialPanel popup, like SettingsPanel)
2. When user enters an unpaired server IP:
   - App generates a 4-digit PIN
   - Shows PIN in the popup: "Enter this PIN on your PC: XXXX"
   - Calls `PairingManager.pair()` which sends the PIN to Apollo's pairing endpoint
   - Apollo shows the PIN in its UI — user confirms on PC
   - On success, dismiss popup and start streaming

**QA:** Enter new PC IP → pairing PIN shown → enter on Apollo → pairing succeeds → streaming starts.

### Task 13: Integrate with Settings + Replace WebView Panel

**Steps:**
1. Update `WorkspaceViewModel` to store: server IP, paired status, stream config (resolution, bitrate)
2. Update `SettingsPanel` to include: server IP field, resolution dropdown, bitrate slider
3. Replace `DesktopStreamPanel` (WebView) with `NativeStreamPanel` in `SpatialWorkspace.kt`
4. Remove moonlight-web-stream WebView code (keep as fallback option if needed)

**QA:** Full flow: Open app → enter IP in settings → pair → stream desktop → control with touch + keyboard. Kill app → reopen → reconnects automatically using saved IP.

**CHECKPOINT: WebView streaming fully replaced with native Moonlight. Pairing works. Settings persist.**

---

## Phase 5: GPL Compliance & Polish (Week 5)

### Task 14: GPL Compliance

**Steps:**
1. Add `LICENSE` file at repo root with full GPLv3 text
2. Add `NOTICE` file documenting:
   - moonlight-android origin (commit SHA, URL)
   - All modifications made to moonlight source files
3. Add SPDX header to every new file: `// SPDX-License-Identifier: GPL-3.0-or-later`
4. Create `MODIFICATIONS.md` listing every change to moonlight files
5. Ensure build scripts are included in source distribution

### Task 15: Polish & Stability

**Steps:**
1. Error states UI (connection failed, server unreachable, pairing rejected)
2. Stream statistics overlay (latency, fps, bitrate — optional)
3. Graceful handling of Apollo/Sunshine not running
4. 30-minute stability test on Galaxy XR
5. Thermal profiling

**QA:** All error states show user-friendly messages. 30-minute stability test passes.

---

## v0.2 Completion Checklist

- [ ] `:moonlight-core` library module created with moonlight streaming core
- [ ] `libmoonlight-core.so` pre-built and loading on Galaxy XR
- [ ] Native video streaming at ~20-40ms latency
- [ ] Audio playback through headset
- [ ] Touch-to-mouse input mapping (hand tracking + controller)
- [ ] Keyboard input forwarding (XR keyboard + BT keyboard)
- [ ] Manual IP entry for server connection
- [ ] Pairing flow with PIN entry
- [ ] Settings persistence (server IP, stream config)
- [ ] WebView panel replaced with native streaming
- [ ] Connection lifecycle management (disconnect/reconnect)
- [ ] GPLv3 license and compliance documentation
- [ ] 30-minute stability test on Galaxy XR

---

## What Comes Next (v0.3 — Not In This Plan)

- mDNS PC discovery (automatic find servers on LAN)
- App selection UI (choose which app to stream, not just Desktop)
- SpatialExternalSurface migration (lower latency rendering, deferred because it lacks built-in input)
- Custom XR ray-cast input (for SpatialExternalSurface — replaces touch events)
- Gamepad input forwarding
- HDR streaming support
- Multi-monitor support

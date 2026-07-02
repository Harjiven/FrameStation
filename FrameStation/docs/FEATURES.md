# FramesXR -- Features

> Note: README.md is the canonical feature list. This file is a summary and may lag.

## Current Features

### Spatial Workspace

- **[Working] Full Space Mode** -- Launches directly into an immersive spatial environment on Samsung Galaxy XR.
- **[Working] Curved Panel Layout** -- Bookmark panels arc in a native `SpatialCurvedRow` (radius user-configurable in Settings via `CurvedPanelSettings`). Note: curving the *streaming* panel itself is still Planned; the bookmark/secondary-stream arc is implemented.
- **[Working] Drag & Reposition** -- All panels can be grabbed and moved freely in 3D space.
- **[Working] Resize** -- Main panel and bookmark panels are resizable by grabbing their edges.
- **[Working] Fade-In Animation** -- Panels smoothly animate from 50% to 100% opacity on open.
- **[Working] Popup Panels** -- Settings, Pairing, Host Manager, and Bookmark Manager float in front of the workspace as separate spatial panels.

### PC Desktop Streaming (Native)

- **[Working] Embedded Moonlight Core** -- Native streaming via an extracted and embedded `moonlight-core` library module with a pre-built `libmoonlight-core.so` (arm64-v8a).
- **[Working] Low-Latency Video via SpatialExternalSurface** -- Video is decoded via `MediaCodecDecoderRenderer` and rendered directly to a `SpatialExternalSurface` (`StreamVideoSurface.kt`), bypassing AndroidView compositing. Latency target ~20-40ms is unverified on-device. (The README's "Planned: migrate to SpatialExternalSurface" note is stale — the migration is already done.)
- **[Working] H.264, H.265, and AV1 Codec Support** -- All three codecs supported; AUTO mode advertises all available formats and lets the server pick the best. AV1 is advertised/negotiated in AUTO mode when a hardware AV1 decoder is present; falls back to H.265 otherwise.
- **[Partial] HDR Streaming** -- 10-bit HDR stream configuration (H.265 Main10 / AV1 Main10, BT.2020 color space via `COLORSPACE_REC_2020`) is implemented in the stream path (`MoonlightStreamManager`). End-to-end HDR output is unverified and requires an HDR-capable server + display; H.264 streams remain SDR.
- **[Working] Per-Host Quality Profiles** -- Resolution, framerate, bitrate, and codec are configurable per host PC via a quality-profile editor in the Host Manager (`HostConfig.qualityProfile`, applied on host select). Global Stream Quality Controls also work.
- **[Working] Spatial Audio Playback** -- Stream audio rendered via `SpatialAudioRenderer` using `USAGE_GAME` + `SPATIALIZER_BEHAVIOR_AUTO` (API 32+) so audio tracks the streaming panel's world-space position as the user moves it.
- **[Working] Surround Sound (Stereo / 5.1 / 7.1)** -- Full multi-channel support via Opus multistream. Channel configuration selectable in Settings; `SpatialAudioRenderer` creates the appropriate `AudioTrack` channel mask for 2, 6, or 8 channels. The PC's full audio output (games, desktop, system sounds) is encoded and routed through the headset.
- **[Working] Audio Mute** -- Toggle to silence stream audio at runtime without stopping the stream.
- **[Working] Connection Overlay** -- Displays server address, connection status, and Start Stream / Reconnect controls when not actively streaming.
- **[Partial] Multiple Simultaneous Streams** -- Up to 3 host PCs can stream simultaneously, each in its own isolated process (`:stream0`, `:stream1`, `:stream2`) with independent `libmoonlight-core.so` state. A multi-stream/IPC path exists, but this is listed as Planned in the canonical README.

### Connection & Pairing

- **[Working] Multiple Host Configurations** -- Store and manage multiple host PC profiles (name, IP, pairing credentials, quality profile) via the Host Manager panel.
- **[Working] Manual IP Entry** -- Enter the IP address of a PC running Apollo or Sunshine.
- **[Working] mDNS / Network Discovery** -- Automatically discover host PCs on the local network via `DiscoveryManager`; discovered hosts appear in the Host Manager.
- **[Working] Wake-on-LAN** -- Send a magic packet to wake a sleeping host PC directly from the headset toolbar.
- **[Working] PIN-Based Pairing** -- Full pairing flow: server check, 4-digit PIN display, certificate exchange, and confirmation.
- **[Working] Server Info Display** -- Shows hostname, GPU type, and paired status after connecting.
- **[Working] Certificate Persistence** -- X.509 server certificates and a unique device ID are saved to disk for reconnection.
- **[Working] Auto-Reconnect on Network Recovery** -- Automatically resumes the stream when Wi-Fi reconnects after a temporary drop, with exponential backoff via `AutoReconnectManager`.

### Input

- **[Partial] Touch-to-Mouse Mapping** -- Touch/ray input (from hand tracking or controllers) via `SpatialInputEvent` translated to mouse position and click events, with coordinate mapping from panel-center-origin to stream resolution. Main desktop panel touch input is not yet wired (planned in the streaming-lifecycle refactor); only the IPC/multi-stream path forwards touch today.
- **[Planned] Hardware Keyboard Forwarding** -- Physical (Bluetooth) keyboard input translated to Windows virtual keycodes covering A-Z, 0-9, F1-F12, arrows, modifiers (Shift, Ctrl, Alt, Meta), punctuation, navigation keys, and lock keys. Keycode translation exists but is not yet wired to an input handler.
- **[Working] Soft Keyboard / Typing Bar** -- Toggleable on-screen text field that sends each typed character as UTF-8 text and handles Backspace/Enter as key events.
- **[Working] Gamepad Input Forwarding** -- Physical Bluetooth/USB gamepads and XR controllers forwarded to the host PC via `ControllerPacket`. Full button mapping (A/B/X/Y, bumpers, triggers, sticks, d-pad, start/select) plus analog stick and trigger axes with per-device deadzone. Implemented in `NativeStreamPanel`; still needs on-device verification.
- **[Working] App Selection UI** -- Choose which application to stream from the host PC (games, specific apps) rather than always streaming the Desktop.
- **[Working] XR Hand Tracking & Controllers** -- Work natively through the XR spatial input system without requiring special handling.

### Bookmark System

- **[Working] Dynamic Bookmarks** -- Add, remove, and manage web bookmarks from a dedicated Bookmark Manager panel.
- **[Working] Default Bookmarks** -- Ships with Spotify, YouTube, Google, and Discord out of the box.
- **[Working] Per-Bookmark WebView Panels** -- Each open bookmark gets its own spatial panel with a header bar and close button.
- **[Working] DRM Content Support** -- WebView panels grant DRM permissions for protected media playback (required for Spotify, etc.).
- **[Working] Third-Party Cookies** -- Enabled per-panel so services like Spotify can maintain login sessions.
- **[Working] State Persistence** -- Open bookmark set and bookmark list are saved to SharedPreferences and restored on app restart.

### Toolbar

- **[Working] Orbiter Toolbar** -- Docked to the bottom edge of the main panel, always accessible.
- **[Working] Desktop Toggle** -- Show/hide the main streaming panel.
- **[Working] Bookmark Chips** -- Quick toggle for each open bookmark panel.
- **[Working] Streaming Controls** -- Audio mute (shows channel mode), Stop Stream, Monitor Switch, and Keyboard toggle (all appear while streaming).
- **[Planned] Passthrough Toggle** -- Eye-icon chip toggles between see-through (passthrough) and virtual environment modes. Only shown on devices where `isPassthroughControlEnabled` is true. Listed as Planned in the canonical README (this summary may lag).
- **[Working] Management Actions** -- Chips for Bookmarks Manager, Host Manager, Discover, Pair, Layout Presets, and Settings.

### Settings & Compliance

- **[Planned] Curved Panel Settings** -- Enable/disable arc layout, configure arc radius (400-1600dp). Tied to curved panel rendering, which is Planned in the canonical README.
- **[Planned] Workspace Layout Presets** -- Save and recall panel arrangements (positions, sizes, open bookmarks) as named workspace layouts via the Layout Presets panel (toolbar icon). Listed as Planned in the canonical README (this summary may lag).
- **[Partial] Non-XR Device Fallback** -- Flat Material3 UI (`FallbackWorkspace`) shown when `isSpatialUiEnabled` is false. Displays device compatibility warning, connection info, stream settings, and bookmarks. XR feature declared `android:required="false"` so the app installs on any Android 14+ device. A non-XR flat UI exists and renders; pairing/streaming from it is not yet complete.
- **[Working] GPLv3 About Dialog** -- In-app "About & License" panel with copyright notice, warranty disclaimer, source availability, and third-party notices as required by GPLv3 Section 4(d).
- **[Working] Persistence** -- All settings, layout state, bookmarks, and device ID saved across sessions.

---

## Planned Features

### Long-Term

- **Custom XR Ray-Cast Input** -- Further precision improvements for `SpatialExternalSurface` pointer input (e.g. sub-pixel ray-hit refinement). Current input uses `SpatialInputEvent.hitPosition` with dynamic panel size tracking for accurate coordinate mapping after resize.
- **True N-Stream Support** -- Refactor `moonlight-common-c` to be instance-aware (instance-scoped C globals instead of process-globals) to support N>3 simultaneous streams without process overhead (~2MB per stream vs ~25MB per process).

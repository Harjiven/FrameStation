# FrameStation
<img width="640" height="360" alt="framexr_logo_transparent" src="https://github.com/user-attachments/assets/2b809d4e-7158-495b-a1ff-82587e2f54c9" />

A multi-panel spatial workspace app for Android XR that streams your PC desktop into an immersive XR environment at ~20-40ms latency by using Moonlight streaming into curved (eventually) spatial panels. Includes the ability to open many browser tabs (such for Spotify, Youtube and web browsing) to seamlessly add to your workflow. Built with Jetpack Compose for XR and Kotlin. GPLv3 licensed.

# FrameStation -- Features

## Current Features

### Spatial Workspace

- **Full Space Mode** -- Launches directly into an immersive spatial environment on Samsung Galaxy XR.
- **Multi-Panel Layout** -- Main streaming panel (1400x900dp) and side bookmark panels (500x430dp) arranged in a `SpatialRow` with a nested `SpatialColumn`.
- **Drag & Reposition** -- All panels can be grabbed and moved freely in 3D space.
- **Resize** -- Main panel and bookmark panels are resizable by grabbing their edges.
- **Fade-In Animation** -- Panels smoothly animate from 50% to 100% opacity on open.
- **Popup Panels** -- Settings, Pairing, and Bookmark Manager float in front of the workspace as separate spatial panels.

### PC Desktop Streaming (Native)

- **Embedded Moonlight Core** -- Native streaming via an extracted and embedded `moonlight-core` library module with a pre-built `libmoonlight-core.so` (arm64-v8a).
- **Low-Latency Video** -- Hardware-accelerated decode via `MediaCodecDecoderRenderer` targeting ~20-40ms latency.
- **H.264 & H.265 Support** -- Both codecs enabled by default for broad server compatibility.
- **Default Stream Config** -- 1920x1080 @ 60fps, 20 Mbps bitrate, stereo audio.
- **Audio Playback** -- Stream audio plays through the headset via `AndroidAudioRenderer`.
- **WebView Fallback** -- Optional browser-based streaming path via moonlight-web-stream for situations where native streaming is unavailable.
- **Connection Overlay** -- Displays server address, connection status, and Start Stream / Reconnect controls when not actively streaming.

### Connection & Pairing

- **Manual IP Entry** -- Enter the IP address of a PC running Apollo or Sunshine.
- **PIN-Based Pairing** -- Full pairing flow: server check, 4-digit PIN display, certificate exchange, and confirmation.
- **Server Info Display** -- Shows hostname, GPU type, and paired status after connecting.
- **Certificate Persistence** -- X.509 server certificates and a unique device ID are saved to disk for reconnection.
- **Multi-Step Pairing UI** -- Guided flow with states for entering IP, checking server reachability, showing PIN, pairing, success, and error handling with retry.

### Input

- **Touch-to-Mouse Mapping** -- Touch input (from hand tracking or controllers) is translated to mouse position and left-click events, with coordinate mapping from view space to stream resolution.
- **Scroll Support** -- Vertical scroll gestures forwarded as mouse scroll events.
- **Hardware Keyboard Forwarding** -- Physical (Bluetooth) keyboard input translated to Windows virtual keycodes covering A-Z, 0-9, F1-F12, arrows, modifiers (Shift, Ctrl, Alt, Meta), punctuation, navigation keys, and lock keys.
- **Soft Keyboard / Typing Bar** -- Toggleable on-screen text field that sends each typed character as UTF-8 text and handles Backspace/Enter as key events.
- **XR Hand Tracking & Controllers** -- Work natively through Android's touch event system on spatial panels without requiring special handling.

### Bookmark System

- **Dynamic Bookmarks** -- Add, remove, and manage web bookmarks from a dedicated Bookmark Manager panel.
- **Default Bookmarks** -- Ships with Spotify, YouTube, Google, and Discord out of the box.
- **Per-Bookmark WebView Panels** -- Each open bookmark gets its own spatial panel with a header bar and close button.
- **DRM Content Support** -- WebView panels grant DRM permissions for protected media playback (required for Spotify, etc.).
- **Third-Party Cookies** -- Enabled per-panel so services like Spotify can maintain login sessions.
- **State Persistence** -- Open bookmark set and bookmark list are saved to SharedPreferences and restored on app restart.

### Toolbar

- **Orbiter Toolbar** -- Docked to the bottom edge of the main panel, always accessible.
- **Desktop Toggle** -- Show/hide the main streaming panel.
- **Bookmark Chips** -- Quick toggle for each open bookmark panel.
- **Streaming Controls** -- Keyboard toggle and Stop Stream button (appears while streaming).
- **Management Actions** -- Chips for Bookmarks Manager, Pair, and Settings.

### Settings

- **Server Address** -- Editable IP address field with numeric keyboard.
- **Persistence** -- Server address, stream URL, layout state, bookmarks, and device ID all saved across sessions.

---

## Planned Features

### Short-Term

- **PC-to-Headset Audio Passthrough** -- Route PC system audio directly to the headset as a dedicated audio stream, independent of the game/desktop stream audio. This would allow listening to PC media or calls through the headset while multitasking.
- **Save Multiple PC/Host Configurations** -- Store and switch between multiple host PC profiles (IP address, pairing credentials, preferred stream settings) instead of being limited to a single saved server.
- **Stream Resolution & Bitrate Controls** -- Expose resolution (360p through 4K), refresh rate, and bitrate settings in the Settings UI. The core library already supports these; they just need a user-facing interface.
- **Auto-Reconnect on Network Recovery** -- Automatically resume the stream when Wi-Fi reconnects after a temporary drop, instead of requiring a manual reconnect.
- **App Selection UI** -- Choose which application to stream from the host PC (games, specific apps) rather than always streaming the Desktop. The server app list API is already integrated.
- **mDNS/Network Discovery** -- Automatically discover host PCs on the local network instead of requiring manual IP entry. Discovery agent code (`NsdManagerDiscoveryAgent`, `JmDNSDiscoveryAgent`) exists in moonlight-core but is not yet wired into the app UI.
- **Wake-on-LAN** -- Send a magic packet to wake a sleeping host PC from the headset. `WakeOnLanSender` exists in moonlight-core and needs UI integration.

### Medium-Term

- **Multiple Simultaneous Streams** -- Open multiple streaming panels from a single host PC or from different host PCs at the same time, enabling a true multi-monitor spatial workspace.
- **AV1 Codec Support** -- Enable AV1 decoding for improved quality at lower bitrates. The core library defines AV1 constants but the stream config does not currently enable them.
- **HDR Streaming** -- Enable 10-bit HDR output (H.265 Main10, AV1 Main10, BT.2020 color space). The core library has full HDR support; it is currently disabled.
- **Surround Sound (5.1 / 7.1)** -- Upgrade from stereo to multi-channel audio. The core library supports 5.1 and 7.1 configurations.
- **Gamepad Input Forwarding** -- Forward XR controller or connected Bluetooth gamepad inputs as gamepad events to the host PC. `ControllerPacket` exists in moonlight-core but no gamepad handling is implemented in the app.
- **SpatialExternalSurface Rendering** -- Migrate from `AndroidView(SurfaceView)` to `SpatialExternalSurface` for lower-latency video rendering that bypasses AndroidView compositing overhead.

### Long-Term

- **Passthrough / Environment Toggle** -- Switch between full passthrough (see-through) and virtual environment modes from within the workspace.
- **Non-XR Device Fallback** -- Graceful fallback UI for non-spatial Android devices, checking `isSpatialUiEnabled` before launching into Full Space mode.
- **Custom XR Ray-Cast Input** -- Direct ray-casting and coordinate mapping for `SpatialExternalSurface` panels, replacing the current touch event pipeline for more precise pointer control.
- **Workspace Layout Presets** -- Save and recall panel arrangements (positions, sizes, which bookmarks are open) as named workspace layouts.
- **Per-Stream Quality Profiles** -- Tie resolution, bitrate, and codec preferences to specific host PCs or applications for automatic optimization.

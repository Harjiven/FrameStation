# FramesXR -- Features

## Current Features

### Spatial Workspace

- **Full Space Mode** -- Launches directly into an immersive spatial environment on Samsung Galaxy XR.
- **Curved Panel Layout** -- Bookmark panels arc in a native `SpatialCurvedRow` (825dp radius) for immersive surround layout. Radius is user-configurable in Settings.
- **Drag & Reposition** -- All panels can be grabbed and moved freely in 3D space.
- **Resize** -- Main panel and bookmark panels are resizable by grabbing their edges.
- **Fade-In Animation** -- Panels smoothly animate from 50% to 100% opacity on open.
- **Popup Panels** -- Settings, Pairing, Host Manager, and Bookmark Manager float in front of the workspace as separate spatial panels.

### PC Desktop Streaming (Native)

- **Embedded Moonlight Core** -- Native streaming via an extracted and embedded `moonlight-core` library module with a pre-built `libmoonlight-core.so` (arm64-v8a).
- **Low-Latency Video via SpatialExternalSurface** -- Video decoded via `MediaCodecDecoderRenderer` and rendered directly to `SpatialExternalSurface`, bypassing AndroidView compositing for ~20-40ms latency.
- **H.264, H.265, and AV1 Codec Support** -- All three codecs supported; AUTO mode advertises all available formats and lets the server pick the best. AV1 requires Android 10 (API 29); falls back to H.265 on older devices.
- **HDR Streaming** -- Enable 10-bit HDR (H.265 Main10 / AV1 Main10, BT.2020 color space) via toggle in Settings. Requires an HDR-capable server and display. H.264 streams remain SDR.
- **Per-Host Quality Profiles** -- Resolution (360p–4K), framerate, bitrate, codec, and HDR configurable per host PC with a quality profile editor in the Host Manager.
- **Spatial Audio Playback** -- Stream audio rendered via `SpatialAudioRenderer` using `USAGE_GAME` + `SPATIALIZER_BEHAVIOR_AUTO` (API 32+) so audio tracks the streaming panel's world-space position as the user moves it.
- **Surround Sound (Stereo / 5.1 / 7.1)** -- Full multi-channel support via Opus multistream. Channel configuration selectable in Settings; `SpatialAudioRenderer` creates the appropriate `AudioTrack` channel mask for 2, 6, or 8 channels. The PC's full audio output (games, desktop, system sounds) is encoded and routed through the headset.
- **Audio Mute** -- Toggle to silence stream audio at runtime without stopping the stream.
- **Connection Overlay** -- Displays server address, connection status, and Start Stream / Reconnect controls when not actively streaming.
- **Multiple Simultaneous Streams** -- Up to 2 host PCs can stream simultaneously, each in its own isolated process (`:stream0`, `:stream1`) with independent `libmoonlight-core.so` state.

### Connection & Pairing

- **Multiple Host Configurations** -- Store and manage multiple host PC profiles (name, IP, pairing credentials, quality profile) via the Host Manager panel.
- **Manual IP Entry** -- Enter the IP address of a PC running Apollo or Sunshine.
- **mDNS / Network Discovery** -- Automatically discover host PCs on the local network via `DiscoveryManager`; discovered hosts appear in the Host Manager.
- **Wake-on-LAN** -- Send a magic packet to wake a sleeping host PC directly from the headset toolbar.
- **PIN-Based Pairing** -- Full pairing flow: server check, 4-digit PIN display, certificate exchange, and confirmation.
- **Server Info Display** -- Shows hostname, GPU type, and paired status after connecting.
- **Certificate Persistence** -- X.509 server certificates and a unique device ID are saved to disk for reconnection.
- **Auto-Reconnect on Network Recovery** -- Automatically resumes the stream when Wi-Fi reconnects after a temporary drop, with exponential backoff via `AutoReconnectManager`.

### Input

- **Touch-to-Mouse Mapping** -- Touch/ray input (from hand tracking or controllers) via `SpatialInputEvent` translated to mouse position and click events, with coordinate mapping from panel-center-origin to stream resolution.
- **Hardware Keyboard Forwarding** -- Physical (Bluetooth) keyboard input translated to Windows virtual keycodes covering A-Z, 0-9, F1-F12, arrows, modifiers (Shift, Ctrl, Alt, Meta), punctuation, navigation keys, and lock keys.
- **Soft Keyboard / Typing Bar** -- Toggleable on-screen text field that sends each typed character as UTF-8 text and handles Backspace/Enter as key events.
- **Gamepad Input Forwarding** -- Physical Bluetooth/USB gamepads and XR controllers forwarded to the host PC via `ControllerPacket`. Full button mapping (A/B/X/Y, bumpers, triggers, sticks, d-pad, start/select) plus analog stick and trigger axes with per-device deadzone.
- **App Selection UI** -- Choose which application to stream from the host PC (games, specific apps) rather than always streaming the Desktop.
- **XR Hand Tracking & Controllers** -- Work natively through the XR spatial input system without requiring special handling.

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
- **Management Actions** -- Chips for Bookmarks Manager, Host Manager, Pair, and Settings.

### Settings & Compliance

- **Curved Panel Settings** -- Enable/disable arc layout, configure arc radius (400-1600dp).
- **Non-XR Device Fallback** -- Flat Material3 UI (`FallbackWorkspace`) shown when `isSpatialUiEnabled` is false. Displays device compatibility warning, connection info, stream settings, and bookmarks. XR feature declared `android:required="false"` so the app installs on any Android 14+ device.
- **GPLv3 About Dialog** -- In-app "About & License" panel with copyright notice, warranty disclaimer, source availability, and third-party notices as required by GPLv3 Section 4(d).
- **Persistence** -- All settings, layout state, bookmarks, and device ID saved across sessions.

---

## Planned Features

### Long-Term

- **Passthrough / Environment Toggle** -- Switch between full passthrough (see-through) and virtual environment modes from within the workspace.

- **Custom XR Ray-Cast Input** -- Direct ray-casting and coordinate mapping for `SpatialExternalSurface` panels, replacing the current touch event pipeline for more precise pointer control.
- **Workspace Layout Presets** -- Save and recall panel arrangements (positions, sizes, which bookmarks are open) as named workspace layouts.
- **True N-Stream Support** -- Refactor `moonlight-common-c` to be instance-aware (instance-scoped C globals instead of process-globals) to support N>2 simultaneous streams without process overhead.

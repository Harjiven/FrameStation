# FrameStation
<img width="320" height="180" alt="framexr_logo_transparent" src="https://github.com/user-attachments/assets/2b809d4e-7158-495b-a1ff-82587e2f54c9" />

A multi-panel spatial workspace app for Android XR that streams your PC desktop into an immersive XR environment at ~20-40ms latency by using Moonlight streaming into curved (eventually) spatial panels. Includes the ability to open many browser tabs (such for Spotify, Youtube and web browsing) to seamlessly add to your workflow. Built with Jetpack Compose for XR and Kotlin. GPLv3 licensed.

# FrameStation -- Features

## Current Features

### Spatial Workspace

- **Full Space Mode** -- Launches directly into an immersive spatial environment on Samsung Galaxy XR.
- **Multi-Panel Layout** -- Main streaming panel (1400x900dp) and side bookmark panels (500x430dp) arranged in a `SpatialRow` with a nested `SpatialColumn`.
- **Drag & Reposition** -- All panels can be grabbed and moved freely in 3D space.
- **Resize** -- Main panel and bookmark panels are resizable by grabbing their edges.
- **Fade-In Animation** -- Panels smoothly animate from 50% to 100% opacity on open.
- **Popup Panels** -- Settings, Pairing, Bookmark Manager, Host Manager, Discovery, App Selector, and Monitor Picker float in front of the workspace as separate draggable spatial panels.

### PC Desktop Streaming (Native)

- **Embedded Moonlight Core** -- Native streaming via an extracted and embedded `moonlight-core` library module with a pre-built `libmoonlight-core.so` (arm64-v8a).
- **Low-Latency Video** -- Hardware-accelerated decode via `MediaCodecDecoderRenderer` targeting ~20-40ms latency.
- **H.264 & H.265 Support** -- Both codecs enabled; user-selectable per stream. Auto mode negotiates the best available codec with the server.
- **Stream Quality Controls** -- User-configurable resolution (720p/1080p/1440p/4K), frame rate (30/60/90/120 fps), bitrate (1–100 Mbps), and codec (Auto/H.264/H.265) via the Settings panel.
- **Spatial Audio** -- Custom `SpatialAudioRenderer` using `USAGE_GAME` + `PERFORMANCE_MODE_NONE` so the Android XR platform spatializes stream audio to the panel's world-space position. Supports stereo, 5.1, and 7.1 channel output.
- **Runtime Audio Mute** -- Mute/unmute stream audio instantly from the toolbar without restarting the stream.
- **Connection Overlay** -- Displays server address, connection status, and Start Stream / Reconnect controls when not actively streaming.
- **App Selection** -- Browse and select which application to stream from the host (games, apps, or Desktop), with HDR badges and running-app indicators.
- **Monitor Selection** -- Switch which host monitor to capture via the Sunshine/Apollo web API (requires Sunshine admin credentials stored locally).

### Connection & Pairing

- **Manual IP Entry** -- Enter the IP address of a PC running Apollo or Sunshine.
- **mDNS Network Discovery** -- Automatically discover host PCs on the local network via jmDNS. Auto-scans for 10 seconds on launch; manual scan available from the Discover panel. Stops scanning when the panel is closed to save battery.
- **PIN-Based Pairing** -- Full pairing flow: server check, 4-digit PIN display, certificate exchange, and confirmation.
- **Multi-Host Management** -- Store and switch between multiple PC profiles. Each profile holds its IP address, pairing certificate, MAC address, and last-connected timestamp. Automatically migrates from the legacy single-server config on first launch.
- **Per-Host Certificates** -- X.509 server certificates stored per host (`server_{hostId}.crt`) alongside a shared device ID.
- **Wake-on-LAN** -- Send a magic packet to wake a sleeping host PC from the toolbar or stream overlay. MAC address stored per host config. Supports both colon and dash-separated formats.
- **Auto-Reconnect** -- Automatically resumes the stream when Wi-Fi recovers after a drop, using exponential backoff (3s → 48s, up to 5 retries). Cancel button available during reconnection; falls back to manual reconnect if all retries fail.

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
- **Per-Bookmark User-Agent** -- Toggle between Desktop Chrome and Mobile Chrome UA per bookmark. Desktop UA is required for sites like Plex that serve degraded mobile players by default.
- **New Tab** -- Open ephemeral browser tabs (not saved to bookmarks) with a full URL bar: back button, refresh, address field with automatic `https://` normalization and Google search fallback. Ephemeral tabs are never persisted across restarts.
- **DRM Content Support** -- WebView panels grant DRM permissions for protected media playback (required for Spotify, Plex, etc.).
- **Third-Party Cookies** -- Enabled per-panel so services like Spotify and Plex can maintain login sessions.
- **Hardware-Accelerated Video** -- `LAYER_TYPE_HARDWARE` set on all WebView panels for GPU-composited video decoding (required for Plex, YouTube, etc.).
- **Network Security** -- Cleartext traffic permitted globally (required for HTTP navigation), user-installed CAs trusted (required for Plex `plex.direct` certs and Sunshine self-signed HTTPS).
- **State Persistence** -- Open bookmark set, bookmark list, and per-bookmark UA preferences are saved to SharedPreferences and restored on app restart.

### Toolbar

- **Orbiter Toolbar** -- Docked to the bottom edge of the main panel, always accessible.
- **Desktop Toggle** -- Show/hide the main streaming panel.
- **Bookmark Chips** -- Quick toggle for each open bookmark panel (including ephemeral tabs).
- **Streaming Controls** -- Audio mute toggle (shows current channel mode), Stop Stream button, and Keyboard button (all appear while streaming).
- **Monitor Switch** -- TV icon opens the Monitor Picker panel when streaming.
- **Management Icons** -- Icon-only chips for Bookmarks (⭐), Hosts, Discover, Pair, Settings, and Keyboard for a clean, compact toolbar.
- **Wake-on-LAN** -- Wake chip appears in the toolbar when a MAC address is configured and no stream is active.

### Settings

- **Server Connection** -- Editable IP address field, MAC address field for Wake-on-LAN, and Auto-Reconnect toggle.
- **Stream Quality** -- Resolution, FPS, bitrate, and codec dropdowns/sliders with a "Use Recommended" bitrate button.
- **Audio** -- Channel configuration (Stereo / 5.1 / 7.1), audio mode (Stream / Muted), and audio effects toggle.
- **Persistence** -- All settings (server address, stream quality, audio preferences, layout state, bookmarks, host configs, Sunshine credentials) saved across sessions.

---

## Planned Features

### Short-Term

- **Curved Panel Rendering** -- Wrap streaming and bookmark panels along a cylindrical arc for a more immersive workspace feel.
- **Per-Stream Quality Profiles** -- Tie resolution, bitrate, and codec preferences to specific host PCs or applications for automatic optimization on connect.
- **Workspace Layout Presets** -- Save and recall panel arrangements (positions, sizes, which bookmarks and streams are open) as named workspace layouts.
- **Non-XR Device Fallback** -- Graceful fallback UI for non-spatial Android devices, checking `isSpatialUiEnabled` before launching into Full Space mode.

### Medium-Term

- **Multiple Simultaneous Streams** -- Open multiple streaming panels from a single host PC or from different host PCs at the same time, enabling a true multi-monitor spatial workspace.
- **SpatialExternalSurface Rendering** -- Migrate from `AndroidView(SurfaceView)` to `SpatialExternalSurface` for lower-latency video rendering that bypasses AndroidView compositing overhead.
- **Custom XR Ray-Cast Input** -- Direct ray-casting and coordinate mapping for `SpatialExternalSurface` panels, replacing the current touch event pipeline for more precise pointer control.
- **AV1 Codec Support** -- Enable AV1 decoding for improved quality at lower bitrates. The core library defines AV1 constants but the stream config does not currently enable them.
- **HDR Streaming** -- Enable 10-bit HDR output (H.265 Main10, AV1 Main10, BT.2020 color space). The core library has full HDR support; it is currently disabled.
- **Apollo Virtual Display Integration** -- Wire the client-side virtual display toggle to Apollo's HTTP API so the headset can programmatically create/destroy a virtual monitor on stream start/stop.

### Long-Term

- **Passthrough / Environment Toggle** -- Switch between full passthrough (see-through) and virtual environment modes from within the workspace.
- **Gamepad Input Forwarding** -- Forward XR controller or connected Bluetooth gamepad inputs as gamepad events to the host PC. `ControllerPacket` exists in moonlight-core but no gamepad handling is implemented in the app.
- **Panel-Anchored Spatial Audio via SceneCore** -- Use `SpatialAudioTrack.setPointSourceParams()` with explicit panel Entity references to achieve true per-panel audio positioning, rather than relying on the platform spatializer's automatic inference.
- **Surround Sound Upmixing** -- Upmix stereo stream audio to virtual 5.1/7.1 for a more immersive sound field when the host does not natively output surround.

---

## Building from Source

### Prerequisites

- **Android Studio** ( Hedgehog 2023.1.1 or later recommended)
- **Android NDK** (r25 or later)
- **CMake** (included with NDK)
- **Git** (with LFS support for submodules)
- **Java 17** or later (JDK)

### Build Steps

1. **Clone the repository with submodules:**
   ```bash
   git clone --recursive https://github.com/harjiven/FrameStationXR.git
   cd FrameStationXR
   ```
   
   If you already cloned without submodules:
   ```bash
   git submodule update --init --recursive
   ```

2. **Configure NDK path:**
   
   In the `FrameStation/` directory, create a `local.properties` file:
   ```properties
   ndk.dir=C\:\\Users\\<YourUsername>\\AppData\\Local\\Android\\Sdk\\ndk\\25.2.9519653
   ```
   
   Adjust the path to match your NDK installation:
   - **Windows**: `C:\Users\<Username>\AppData\Local\Android\Sdk\ndk\<version>`
   - **macOS**: `/Users/<username>/Library/Android/sdk/ndk/<version>`
   - **Linux**: `~/Android/Sdk/ndk/<version>`

3. **Open in Android Studio:**
   - Open Android Studio
   - File → Open → Select the `FrameStation/` directory
   - Wait for Gradle sync to complete

4. **Build the APK:**
   
   Via Android Studio:
   - Build → Build Bundle(s) / APK(s) → Build APK(s)
   - Output location: `FrameStation/app/build/outputs/apk/debug/app-debug.apk`
   
   Via command line:
   ```bash
   cd FrameStation
   ./gradlew assembleDebug
   ```

5. **Install on device:**
   
   Via ADB:
   ```bash
   adb install FrameStation/app/build/outputs/apk/debug/app-debug.apk
   ```
   
   Or transfer the APK to your Samsung Galaxy XR headset and install manually.

### Installation Requirements

- **Device**: Samsung Galaxy XR (or compatible Android XR headset)
- **Android Version**: Android 14+ with XR support
- **Network**: Wi-Fi 5 (802.11ac) or later recommended for low-latency streaming
- **Host PC**: Windows PC running Sunshine or NVIDIA GameStream (Apollo)

### Installing the Host PC Server

**Option 1: Sunshine (Recommended, Open Source)**
1. Download from: https://github.com/LizardByte/Sunshine
2. Install on your Windows PC
3. Configure streaming settings in Sunshine web UI (http://localhost:47990)

**Option 2: NVIDIA GameStream (Apollo)**
1. Requires NVIDIA GPU with GameStream support
2. Uses GeForce Experience (legacy) or Apollo fork
3. See: https://github.com/cg1882/Apollo

### Corresponding Source Availability

As required by GPLv3 Section 6, the complete corresponding source is available at:
- **Repository**: https://github.com/harjiven/FrameStationXR
- **Includes**: All source code, build scripts, and moonlight-core submodule
- **License**: GNU General Public License v3.0 or later

To rebuild the exact binary:
```bash
git clone --recursive https://github.com/harjiven/FrameStationXR.git
cd FrameStationXR/FrameStation
./gradlew assembleDebug
```

### Installation Information (GPLv3 User Product Requirement)

Modified versions can be installed by:
1. Building a modified APK as described above
2. Sideloading via ADB: `adb install your-modified-apk.apk`
3. No special keys or authorization required - standard Android debugging permissions suffice

The application does not enforce signature verification or DRM that would prevent installation of modified versions.

---

## License

FrameStation is licensed under the **GNU General Public License v3.0** (or later).

See the [COPYING](COPYING) file for the full license text.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

---

## Credits

- **Moonlight Game Streaming Team** -- Core streaming library extracted from [moonlight-android](https://github.com/moonlight-stream/moonlight-android) and [moonlight-common-c](https://github.com/moonlight-stream/moonlight-common-c)
- **Jetpack Compose for XR** -- UI framework by Google
- **Samsung XR** -- Platform and hardware support

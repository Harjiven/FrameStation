# Modifications to Moonlight Source Code

This document lists all changes made to code extracted from
[moonlight-android](https://github.com/moonlight-stream/moonlight-android).

## Files Extracted

The following packages were copied from moonlight-android into the
`moonlight-core` module (`moonlight-core/src/main/java/com/limelight/`):

- `nvstream/` — Connection management, stream configuration, JNI bridge
- `nvstream/http/` — NvHTTP client, pairing manager, server discovery
- `nvstream/jni/` — MoonBridge JNI wrapper
- `nvstream/input/` — Input packet definitions (keyboard, mouse, controller)
- `nvstream/av/` — Audio/video renderer interfaces
- `nvstream/mdns/` — mDNS discovery (for future use)
- `nvstream/wol/` — Wake-on-LAN
- `binding/video/` — MediaCodecDecoderRenderer, MediaCodecHelper
- `binding/audio/` — AndroidAudioRenderer
- `binding/crypto/` — AndroidCryptoProvider
- `binding/PlatformBinding.java`
- `preferences/PreferenceConfiguration.java`, `GlPreferences.java`
- `LimeLog.java`

## Modifications Made

All modifications are marked with `// XR-REMOVED:` or `// XR-MODIFIED:` comments.

### NvHTTP.java
- Modified `checkServerTrusted()` to accept self-signed certificates when no
  server cert is pinned (trust-on-first-use for initial pairing)
- Modified hostname verifier to accept unpinned server certs (GameStream servers
  use self-signed certs without proper SAN)
- Modified `getServerInfo()` to use HTTP directly when no server cert is pinned
- Increased connection and read timeouts for XR headset WiFi reliability
- Replaced `getHttpsUrl()` HTTPS-first fallback with direct HTTP for unpinned certs

### MediaCodecDecoderRenderer.java
- Replaced `R.string.*` resource references with hardcoded format strings for
  the performance overlay (removed dependency on Android resources)
- Removed `import com.limelight.R`

### PreferenceConfiguration.java
- Stripped to data fields only — removed SharedPreferences UI dependencies
- Removed methods that reference Android preference framework classes

### PlatformBinding.java
- Simplified to only provide `getCryptoProvider()` — removed references to
  UI classes and input handlers not present in the XR app

### NvConnection.java
- Removed `Context` parameter from constructor (not needed for XR integration)
- Stubbed `detectServerConnectionType()` to return `STREAM_CFG_AUTO`
  (removed dependency on Android ConnectivityManager)

### Various files
- Removed references to `Game.java`, `AppView.java`, and other UI activity classes
- Removed references to `com.limelight.binding.input.*` controller handler classes
- Removed references to `BuildConfig` — replaced with sensible defaults
- Created stub classes where needed to satisfy compilation

## Native Library

`libmoonlight-core.so` (arm64-v8a) was pre-built from the moonlight-android
repository's ndk-build system and included as a prebuilt JNI library. No
modifications were made to the native C code.

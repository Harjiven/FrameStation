# HDR Streaming Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Enable 10-bit HDR streaming (H.265 Main10 / AV1 Main10, BT.2020 color space) by adding an HDR toggle to `StreamSettings`, passing it through to `PreferenceConfiguration.enableHdr`, and implementing the `setHdrMode()` callback to log HDR activation.

**Architecture:** `StreamConfiguration.setColorSpace()`, `setColorRange()`, and `PreferenceConfiguration.enableHdr` already exist in moonlight-core. We add `enableHdr: Boolean` to `StreamSettings`, replace the hardcoded `enableHdr = false` in `MoonlightStreamManager`, include `VIDEO_FORMAT_H265_MAIN10` in the video format mask when HDR is enabled, implement the empty `setHdrMode()` stub, and add an HDR toggle to `SettingsDialog`. The `MoonBridge.COLORSPACE_REC_2020 = 2` and `VIDEO_FORMAT_MASK_10BIT` constants handle the rest.

**Tech Stack:** Kotlin, `PreferenceConfiguration.enableHdr`, `MoonBridge.VIDEO_FORMAT_H265_MAIN10`, `MoonBridge.VIDEO_FORMAT_AV1_MAIN10`, `StreamConfiguration.setColorSpace/setColorRange`

---

## Context

**Files to touch:**
- Modify: `app/src/main/java/com/xrworkspace/app/model/StreamSettings.kt`
- Modify: `app/src/main/java/com/xrworkspace/app/streaming/MoonlightStreamManager.kt` (lines 180–200, 213–229, 412)
- Modify: `app/src/main/java/com/xrworkspace/app/ui/components/SettingsDialog.kt`
- Modify: `app/src/main/java/com/xrworkspace/app/streaming/StreamServiceConnection.kt` (serializer)
- Modify: `app/src/main/java/com/xrworkspace/app/streaming/StreamService.kt` (deserializer)

**Key constants available in moonlight-core:**
- `MoonBridge.VIDEO_FORMAT_H265_MAIN10 = 0x0200`
- `MoonBridge.VIDEO_FORMAT_AV1_MAIN10 = 0x2000`
- `MoonBridge.VIDEO_FORMAT_MASK_10BIT = 0x2200`
- `MoonBridge.COLORSPACE_REC_2020 = 2`
- `PreferenceConfiguration.enableHdr: Boolean`
- `StreamConfiguration.Builder.setColorSpace(int)` — 0=default, 1=REC_601, 2=REC_709, 2=REC_2020
- `StreamConfiguration.Builder.setColorRange(int)`

---

## Task 1: Add `enableHdr` field to `StreamSettings`

**Files:**
- Modify: `app/src/main/java/com/xrworkspace/app/model/StreamSettings.kt`

**Step 1: Add field to `StreamSettings`**

Find:
```kotlin
data class StreamSettings(
    val resolution: Resolution = Resolution.RES_1080P,
    val fps: Int = 60,
    val bitrateKbps: Int = 20000,
    val codec: VideoCodec = VideoCodec.AUTO,
)
```
Replace with:
```kotlin
data class StreamSettings(
    val resolution: Resolution = Resolution.RES_1080P,
    val fps: Int = 60,
    val bitrateKbps: Int = 20000,
    val codec: VideoCodec = VideoCodec.AUTO,
    /** Enable 10-bit HDR streaming (H.265 Main10 / AV1 Main10, BT.2020 color space). */
    val enableHdr: Boolean = false,
)
```

---

## Task 2: Wire HDR through `MoonlightStreamManager`

**Files:**
- Modify: `app/src/main/java/com/xrworkspace/app/streaming/MoonlightStreamManager.kt`

**Step 1: Add `streamHdr` field alongside other stream config fields**

Find:
```kotlin
    var streamCodec: VideoCodec = VideoCodec.AUTO
```
Replace with:
```kotlin
    var streamCodec: VideoCodec = VideoCodec.AUTO
    var streamHdr: Boolean = false
```

**Step 2: Update `applyStreamSettings()` to store HDR setting**

Find:
```kotlin
    fun applyStreamSettings(settings: StreamSettings) {
        streamWidth = settings.resolution.width
        streamHeight = settings.resolution.height
        streamFps = settings.fps
        streamBitrate = settings.bitrateKbps
        streamCodec = settings.codec
    }
```
Replace with:
```kotlin
    fun applyStreamSettings(settings: StreamSettings) {
        streamWidth = settings.resolution.width
        streamHeight = settings.resolution.height
        streamFps = settings.fps
        streamBitrate = settings.bitrateKbps
        streamCodec = settings.codec
        streamHdr = settings.enableHdr
    }
```

**Step 3: Add 10-bit formats to video format mask when HDR is enabled**

Find:
```kotlin
                    val videoFormats = when (streamCodec) {
                        VideoCodec.AUTO -> {
                            var mask = MoonBridge.VIDEO_FORMAT_H264 or MoonBridge.VIDEO_FORMAT_H265
                            if (av1Supported) mask = mask or MoonBridge.VIDEO_FORMAT_AV1_MAIN8
                            mask
                        }
                        VideoCodec.H264 -> MoonBridge.VIDEO_FORMAT_H264
                        VideoCodec.H265 -> MoonBridge.VIDEO_FORMAT_H265
                        VideoCodec.AV1_MAIN8 -> if (av1Supported) MoonBridge.VIDEO_FORMAT_AV1_MAIN8
                            else MoonBridge.VIDEO_FORMAT_H265
                        VideoCodec.AV1_MAIN10 -> if (av1Supported) MoonBridge.VIDEO_FORMAT_AV1_MAIN10
                            else MoonBridge.VIDEO_FORMAT_H265_MAIN10
                    }
```

NOTE: If the AV1 plan has NOT been applied yet, the `when` block will still have H264/H265/AUTO only.
In either case, add 10-bit formats after the `when` block. Add these lines immediately after it:

```kotlin
                    // If HDR is requested, include 10-bit format variants so the server
                    // can negotiate Main10 decode.
                    val finalVideoFormats = if (streamHdr) {
                        videoFormats or MoonBridge.VIDEO_FORMAT_H265_MAIN10 or
                            (if (av1Supported) MoonBridge.VIDEO_FORMAT_AV1_MAIN10 else 0)
                    } else {
                        videoFormats
                    }
```

Then change `setSupportedVideoFormats(videoFormats)` to `setSupportedVideoFormats(finalVideoFormats)`:

Find:
```kotlin
                        .setSupportedVideoFormats(videoFormats)
```
Replace with:
```kotlin
                        .setSupportedVideoFormats(finalVideoFormats)
```

**Step 4: Replace hardcoded `enableHdr = false` with the field**

Find:
```kotlin
                        enableHdr = false
```
Replace with:
```kotlin
                        enableHdr = streamHdr
```

**Step 5: Add color space to StreamConfiguration when HDR enabled**

Find:
```kotlin
                    val streamConfig = StreamConfiguration.Builder()
                        .setResolution(streamWidth, streamHeight)
                        .setRefreshRate(streamFps)
                        .setLaunchRefreshRate(streamFps)
                        .setBitrate(streamBitrate)
                        .setApp(desktopApp)
                        .setMaxPacketSize(1392)
                        .setRemoteConfiguration(StreamConfiguration.STREAM_CFG_AUTO)
                        .setAudioConfiguration(audioConfig)
                        .setSupportedVideoFormats(finalVideoFormats)
                        .build()
```
Replace with:
```kotlin
                    val streamConfigBuilder = StreamConfiguration.Builder()
                        .setResolution(streamWidth, streamHeight)
                        .setRefreshRate(streamFps)
                        .setLaunchRefreshRate(streamFps)
                        .setBitrate(streamBitrate)
                        .setApp(desktopApp)
                        .setMaxPacketSize(1392)
                        .setRemoteConfiguration(StreamConfiguration.STREAM_CFG_AUTO)
                        .setAudioConfiguration(audioConfig)
                        .setSupportedVideoFormats(finalVideoFormats)
                    if (streamHdr) {
                        streamConfigBuilder.setColorSpace(MoonBridge.COLORSPACE_REC_2020)
                    }
                    val streamConfig = streamConfigBuilder.build()
```

**Step 6: Implement `setHdrMode()` callback stub**

Find:
```kotlin
    override fun setHdrMode(enabled: Boolean, hdrMetadata: ByteArray?) {}
```
Replace with:
```kotlin
    override fun setHdrMode(enabled: Boolean, hdrMetadata: ByteArray?) {
        Log.i(TAG, "HDR mode: enabled=$enabled, metadata=${hdrMetadata?.size ?: 0} bytes")
        // HDR is handled at the decoder/surface level; this callback is informational.
        // Future: could expose HDR state to UI via onHdrStateChanged callback.
    }
```

---

## Task 3: Add HDR toggle to `SettingsDialog`

**Files:**
- Modify: `app/src/main/java/com/xrworkspace/app/ui/components/SettingsDialog.kt`

**Step 1: Add `enableHdr` state variable**

Find where the other stream settings state vars are declared (near `selectedCodec`, around line 87). Add:
```kotlin
    var enableHdr by remember { mutableStateOf(currentStreamSettings.enableHdr) }
```

**Step 2: Add HDR toggle row in the stream settings section**

Find the codec dropdown closing section (right after the codec dropdown block, around line 355):
```kotlin
                // Codec dropdown
```
Add after the codec dropdown's closing `}` and before the next section:

```kotlin
                Spacer(modifier = Modifier.height(8.dp))
                // HDR toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "HDR Streaming",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "H.265 Main10 / AV1 Main10 (server must support HDR)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = enableHdr,
                        onCheckedChange = { enableHdr = it },
                    )
                }
```

**Step 3: Include `enableHdr` in the saved settings**

Find the settings save block where the `StreamSettings` is constructed (near `codec = selectedCodec`):
```kotlin
                            codec = selectedCodec,
```
Add after it:
```kotlin
                            enableHdr = enableHdr,
```

---

## Task 4: Update IPC JSON serialization

**Files:**
- Modify: `app/src/main/java/com/xrworkspace/app/streaming/StreamServiceConnection.kt`
- Modify: `app/src/main/java/com/xrworkspace/app/streaming/StreamService.kt`

**Step 1: Add `enableHdr` to serializer in `StreamServiceConnection.kt`**

Find:
```kotlin
    private fun serializeStreamSettings(s: StreamSettings): String =
        JSONObject().apply {
            put("resolution", s.resolution.name)
            put("fps", s.fps)
            put("bitrateKbps", s.bitrateKbps)
            put("codec", s.codec.name)
        }.toString()
```
Replace with:
```kotlin
    private fun serializeStreamSettings(s: StreamSettings): String =
        JSONObject().apply {
            put("resolution", s.resolution.name)
            put("fps", s.fps)
            put("bitrateKbps", s.bitrateKbps)
            put("codec", s.codec.name)
            put("enableHdr", s.enableHdr)
        }.toString()
```

**Step 2: Add `enableHdr` to deserializer in `StreamService.kt`**

Find:
```kotlin
            StreamSettings(
                resolution = Resolution.entries.find { it.name == obj.optString("resolution") }
                    ?: Resolution.HD_1080,
                fps = obj.optInt("fps", 60),
                bitrateKbps = obj.optInt("bitrateKbps", 20000),
                codec = VideoCodec.entries.find { it.name == obj.optString("codec") }
                    ?: VideoCodec.AUTO,
            )
```
Replace with:
```kotlin
            StreamSettings(
                resolution = Resolution.entries.find { it.name == obj.optString("resolution") }
                    ?: Resolution.HD_1080,
                fps = obj.optInt("fps", 60),
                bitrateKbps = obj.optInt("bitrateKbps", 20000),
                codec = VideoCodec.entries.find { it.name == obj.optString("codec") }
                    ?: VideoCodec.AUTO,
                enableHdr = obj.optBoolean("enableHdr", false),
            )
```

---

## Task 5: Verify and commit

**Step 1: Grep for hardcoded enableHdr**
```
Grep: enableHdr = false
Expected: zero matches (all replaced with streamHdr field)
```

**Step 2: Commit**
```bash
git add -A
git commit -m "feat: enable HDR streaming (H.265 Main10 / AV1 Main10)

Add enableHdr: Boolean to StreamSettings (default false). When enabled:
- VIDEO_FORMAT_H265_MAIN10 and VIDEO_FORMAT_AV1_MAIN10 added to format mask
- StreamConfiguration.setColorSpace(COLORSPACE_REC_2020) set
- PreferenceConfiguration.enableHdr = true
Implement setHdrMode() callback with logging. Add HDR toggle to
SettingsDialog with server requirement note. Wire through IPC
serialization (StreamServiceConnection + StreamService JSON)."
```

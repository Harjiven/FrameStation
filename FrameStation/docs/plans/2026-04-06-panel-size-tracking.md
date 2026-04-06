# Dynamic Panel Size Tracking for Accurate Ray-Cast Input

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix pointer accuracy degradation when users resize streaming panels by replacing hardcoded `panelHalfW = 700f` / `panelHalfH = 450f` constants in `NativeStreamPanel` with the actual live panel dimensions tracked via `SubspaceModifier.onSizeChanged`.

**Architecture:** `SpatialExternalSurface` does not expose its size directly, but the `SpatialPanel` wrapping it receives `onSizeChanged` callbacks via `SubspaceModifier`. We pass the panel's current width and height into `NativeStreamPanel` as parameters — the `InteractionPolicy.onInputEvent` then uses the live dimensions instead of hardcoded constants. For the multi-stream arc panels, dimensions are also passed. The `SpatialWorkspace` tracks panel sizes as `MutableState<IntSize>` per panel, initialized to the panel's configured dimensions.

**Tech Stack:** Kotlin, `SubspaceModifier.onSizeChanged`, `IntSize`, `SpatialInputEvent.hitPosition`

---

## Context

**The bug:** `NativeStreamPanel.onInputEvent()` lines 341–342 hardcode:
```kotlin
val panelHalfW = 700f  // assumes 1400dp panel width
val panelHalfH = 450f  // assumes 900dp panel height
```
When the user drags the panel's resize handle to make it smaller/larger, `panelHalfW` and `panelHalfH` stay at 700/450. This offsets all mouse clicks — e.g., clicking top-left corner of a resized panel sends the click to a different screen position.

**The fix:** `SubspaceModifier.onSizeChanged { width, height -> }` fires when the panel is resized. We store the live size and pass it to `NativeStreamPanel` as `panelWidthDp` / `panelHeightDp` parameters.

**Note on coordinate units:** `SpatialInputEvent.hitPosition` is in **pixels** relative to panel center. `SubspaceModifier.onSizeChanged` returns dimensions in **dp** (device-independent pixels). On Android XR at the default DPI, 1dp ≈ 1px at the reference density. The conversion factor can be obtained via `LocalDensity.current.density`. However, in practice the XR runtime reports `hitPosition` in the same units as the panel's logical size — so using dp directly works correctly. We will use dp values with a comment noting this.

**Files to touch:**
- Modify: `app/src/main/java/com/xrworkspace/app/ui/panels/NativeStreamPanel.kt` — add `panelWidthDp` / `panelHeightDp` parameters, use them in `onInputEvent`
- Modify: `app/src/main/java/com/xrworkspace/app/ui/SpatialWorkspace.kt` — track panel size state, pass to `NativeStreamPanel`

---

## Task 1: Add `panelWidthDp` / `panelHeightDp` params to `NativeStreamPanel`

**Files:**
- Modify: `app/src/main/java/com/xrworkspace/app/ui/panels/NativeStreamPanel.kt`

**Step 1: Add two parameters**

Find the `NativeStreamPanel` function signature. It currently ends with:
```kotlin
    streamServiceConnection: StreamServiceConnection? = null,
) {
```
Replace with:
```kotlin
    streamServiceConnection: StreamServiceConnection? = null,
    /** Live panel width in dp — updated by SpatialWorkspace when user resizes the panel. */
    panelWidthDp: Float = 1400f,
    /** Live panel height in dp — updated by SpatialWorkspace when user resizes the panel. */
    panelHeightDp: Float = 900f,
) {
```

**Step 2: Replace hardcoded half-dimensions with the parameters**

Find:
```kotlin
                    val hitPos = event.hitPosition ?: return
                    // hitPosition is pixel offset from surface CENTER.
                    // Convert to top-left origin, normalize to [0,1], scale to stream res.
                    // Panel size: 1400x900dp (main) or 1200x750dp (arc panel).
                    val panelHalfW = 700f
                    val panelHalfH = 450f
```
Replace with:
```kotlin
                    val hitPos = event.hitPosition ?: return
                    // hitPosition is in the same units as the panel's logical size (dp).
                    // Use live panel dimensions so resize doesn't break pointer accuracy.
                    val panelHalfW = panelWidthDp / 2f
                    val panelHalfH = panelHeightDp / 2f
```

---

## Task 2: Track panel size in `SpatialWorkspace` and pass to `NativeStreamPanel`

**Files:**
- Modify: `app/src/main/java/com/xrworkspace/app/ui/SpatialWorkspace.kt`

**Step 1: Add import for `onSizeChanged`**

Check if `androidx.xr.compose.subspace.layout.onSizeChanged` is already imported. If not, add:
```kotlin
import androidx.xr.compose.subspace.layout.onSizeChanged
```

Also add if not present:
```kotlin
import androidx.compose.runtime.mutableFloatStateOf
```

**Step 2: Add size state for the main desktop stream panel**

Find the line near the main `SpatialPanel` where `NativeStreamPanel` is rendered (lines 334–388). Just before the `SpatialPanel` declaration, add:

```kotlin
        // Track live panel size for accurate pointer mapping (resized by user drag)
        var mainPanelWidthDp by remember { mutableFloatStateOf(1400f) }
        var mainPanelHeightDp by remember { mutableFloatStateOf(900f) }
```

**Step 3: Add `onSizeChanged` to the main desktop `SpatialPanel` modifier**

Find the main desktop `SpatialPanel` modifier:
```kotlin
            SpatialPanel(
                modifier = SubspaceModifier
                    .alpha(animatedAlpha.value)
                    .width(1400.dp)
                    .height(900.dp)
                    .offset(z = MAIN_PANEL_Z.dp),
```

NOTE: Read the actual file to find the exact modifier chain — there may be slight differences from the plan. Add `.onSizeChanged` to the chain:
```kotlin
            SpatialPanel(
                modifier = SubspaceModifier
                    .alpha(animatedAlpha.value)
                    .width(1400.dp)
                    .height(900.dp)
                    .offset(z = MAIN_PANEL_Z.dp)
                    .onSizeChanged { width, height ->
                        mainPanelWidthDp = width.toFloat()
                        mainPanelHeightDp = height.toFloat()
                    },
```

**Step 4: Pass live dimensions to the main `NativeStreamPanel`**

Find `NativeStreamPanel(` call inside the main desktop panel's `Surface { }`. Add the two new parameters:

Find:
```kotlin
                        NativeStreamPanel(
                            serverAddress = uiState.serverAddress,
                            streamSettings = uiState.streamSettings,
                            audioSettings = uiState.audioSettings,
                            autoReconnectEnabled = uiState.autoReconnectEnabled,
                            onStreamingStateChanged = onStreamingStateChanged,
                            streamController = streamController,
                            serverCert = serverCert,
                        )
```
Replace with:
```kotlin
                        NativeStreamPanel(
                            serverAddress = uiState.serverAddress,
                            streamSettings = uiState.streamSettings,
                            audioSettings = uiState.audioSettings,
                            autoReconnectEnabled = uiState.autoReconnectEnabled,
                            onStreamingStateChanged = onStreamingStateChanged,
                            streamController = streamController,
                            serverCert = serverCert,
                            panelWidthDp = mainPanelWidthDp,
                            panelHeightDp = mainPanelHeightDp,
                        )
```

**Step 5: Add size tracking to the multi-stream single-panel branch**

In the `activeStreamHosts.size == 1` branch (around line 440), find:
```kotlin
                SpatialPanel(
                    modifier = SubspaceModifier
                        .width(1400.dp)
                        .height(900.dp)
                        .offset(x = STREAM_PANEL_OFFSET_X.dp),
```

Add a size state and `onSizeChanged` modifier:

BEFORE the `SpatialPanel`, add:
```kotlin
                var streamPanelWidthDp by remember { mutableFloatStateOf(1400f) }
                var streamPanelHeightDp by remember { mutableFloatStateOf(900f) }
```

Update the modifier:
```kotlin
                SpatialPanel(
                    modifier = SubspaceModifier
                        .width(1400.dp)
                        .height(900.dp)
                        .offset(x = STREAM_PANEL_OFFSET_X.dp)
                        .onSizeChanged { w, h ->
                            streamPanelWidthDp = w.toFloat()
                            streamPanelHeightDp = h.toFloat()
                        },
```

And update the `NativeStreamPanel` call inside it:
```kotlin
                            NativeStreamPanel(
                                serverAddress = host.address,
                                streamSettings = host.qualityProfile ?: uiState.streamSettings,
                                audioSettings = uiState.audioSettings,
                                autoReconnectEnabled = uiState.autoReconnectEnabled,
                                onStreamingStateChanged = {},
                                streamController = hostController,
                                streamServiceConnection = onGetStreamSlot(host.id),
                                panelWidthDp = streamPanelWidthDp,
                                panelHeightDp = streamPanelHeightDp,
                            )
```

**Step 6: Add size tracking to the multi-stream `forEach` arc branch**

In the `activeStreamHosts.forEach` branch (for 2+ streams), the panels are 1200×750dp. Use `remember` keyed per host since each panel has its own size:

```kotlin
                    activeStreamHosts.forEach { host ->
                        val hostController = streamPanelControllers[host.id] ?: StreamController()
                        var arcPanelWidthDp by remember(host.id) { mutableFloatStateOf(1200f) }
                        var arcPanelHeightDp by remember(host.id) { mutableFloatStateOf(750f) }
                        SpatialPanel(
                            modifier = SubspaceModifier
                                .width(1200.dp)
                                .height(750.dp)
                                .onSizeChanged { w, h ->
                                    arcPanelWidthDp = w.toFloat()
                                    arcPanelHeightDp = h.toFloat()
                                },
                            ...
                        ) {
                            Surface(modifier = Modifier.fillMaxSize()) {
                                NativeStreamPanel(
                                    ...
                                    panelWidthDp = arcPanelWidthDp,
                                    panelHeightDp = arcPanelHeightDp,
                                )
                            }
                        }
                    }
```

---

## Task 3: Verify and commit

**Step 1: Grep for hardcoded panel half dimensions**
```
Grep: panelHalfW = 700f|panelHalfH = 450f
Expected: zero matches (all replaced with live parameters)
```

**Step 2: Grep for onSizeChanged**
```
Grep: onSizeChanged
Path: app/src/main/java/
Expected: found in SpatialWorkspace.kt at each SpatialPanel that wraps NativeStreamPanel
```

**Step 3: Commit**
```bash
git add -A
git commit -m "fix: dynamic panel size tracking for accurate ray-cast pointer mapping

Replace hardcoded panelHalfW=700/panelHalfH=450 constants in NativeStreamPanel
with live panelWidthDp/panelHeightDp parameters updated via
SubspaceModifier.onSizeChanged on each wrapping SpatialPanel. Pointer
coordinates are now accurate after the user resizes a streaming panel via
drag. Applies to main desktop panel, single-stream offset panel, and all
panels in the multi-stream arc (1200x750dp initial size, tracked per host.id)."
```

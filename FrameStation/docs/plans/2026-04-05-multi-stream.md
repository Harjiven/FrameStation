# Multiple Simultaneous Streams Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Allow multiple host PCs to stream simultaneously, each in its own `SpatialExternalSurface` panel, laid out in a `SpatialCurvedRow` alongside bookmark panels.

**Architecture:** Replace the single `activeHostId`/`isStreaming` pattern in `WorkspaceUiState` with a `Set<String>` called `activeStreamHostIds` — the set of host IDs that currently have open stream panels. `SpatialWorkspace` renders one `SpatialPanel` per active stream host, grouped in a `SpatialCurvedRow` together with bookmark panels (mirrors the existing bookmark curved layout). `NativeStreamPanel` becomes fully self-contained per-host: it creates its own `MoonlightStreamManager` from the host config. The `HostManagerPanel` gains a "Stream" button per host to add/remove it from `activeStreamHostIds`.

**Tech Stack:** Kotlin, Jetpack Compose for XR 1.0.0-alpha10, `SpatialCurvedRow`, `SpatialExternalSurface`, `WorkspaceUiState`, `WorkspaceViewModel`

---

## Context

**The single-stream assumptions to replace:**
- `WorkspaceUiState.isStreaming: Boolean` — replaced by deriving from `activeStreamHostIds.isNotEmpty()`
- `WorkspaceUiState.activeHostId: String?` — kept for "which host is the Settings/App Selector/Monitor Picker focused on", but no longer controls whether a stream is open
- `WorkspaceUiState.serverAddress: String` — kept as the settings-focused host address (not the streaming address)
- `SpatialWorkspace`: single `NativeStreamPanel` inside main `SpatialPanel` replaced by a loop over `activeStreamHostIds`

**Files to touch:**
- Modify: `app/src/main/java/com/xrworkspace/app/viewmodel/WorkspaceViewModel.kt` — add `activeStreamHostIds` to state + `openStream`/`closeStream` methods
- Modify: `app/src/main/java/com/xrworkspace/app/ui/SpatialWorkspace.kt` — render N stream panels in `SpatialCurvedRow`
- Modify: `app/src/main/java/com/xrworkspace/app/ui/components/HostManagerPanel.kt` — add Stream/Stop button per host
- Modify: `app/src/main/java/com/xrworkspace/app/ui/components/WorkspaceToolbar.kt` — show active stream chips

**Existing patterns to follow:**
- Bookmark panel loop (lines 417–471 in `SpatialWorkspace.kt`) — near-identical pattern for stream panels
- `SpatialCurvedRow` usage added in the curved-panel migration
- `NativeStreamPanel` already accepts `serverAddress`, `streamSettings`, `audioSettings` — no internal changes needed

**Constraint — `NativeStreamPanel` parameter `serverCert`:**
`NativeStreamPanel` accepts an optional `serverCert: X509Certificate?`. The cert is loaded from disk per-host via `ServerManager`. In `SpatialWorkspace`, pass `null` for serverCert — `MoonlightStreamManager` already falls back to loading the cert from disk via `ServerManager.loadServerCert()` when `serverCert` is null.

---

## Task 1: Add `activeStreamHostIds` to `WorkspaceUiState` and ViewModel

**Files:**
- Modify: `app/src/main/java/com/xrworkspace/app/viewmodel/WorkspaceViewModel.kt`

**Step 1: Add `activeStreamHostIds` field to `WorkspaceUiState`**

In `WorkspaceUiState` (currently lines 53–93), add one field after `activeHostId`:

Find:
```kotlin
    val activeHostId: String? = null,
    val showHostManager: Boolean = false,
```
Replace with:
```kotlin
    val activeHostId: String? = null,
    /** Host IDs that currently have an open stream panel. */
    val activeStreamHostIds: Set<String> = emptySet(),
    val showHostManager: Boolean = false,
```

**Step 2: Add `openStream` and `closeStream` methods to `WorkspaceViewModel`**

Find the `// --- Host config management ---` section (around line 416). Add these two methods directly after `toggleHostManager()`:

```kotlin
    /**
     * Open a stream panel for the given host.
     * No-op if the host is already streaming or does not exist.
     */
    fun openStream(hostId: String) {
        val host = _uiState.value.hostConfigs.find { it.id == hostId } ?: return
        _uiState.update { state ->
            state.copy(
                activeStreamHostIds = state.activeStreamHostIds + hostId,
                showHostManager = false,
            )
        }
    }

    /**
     * Close the stream panel for the given host.
     * No-op if the host is not currently streaming.
     */
    fun closeStream(hostId: String) {
        _uiState.update { state ->
            state.copy(activeStreamHostIds = state.activeStreamHostIds - hostId)
        }
    }
```

**Step 3: Update `isStreaming` derivation in uiState**

Search for where `isStreaming` is set in the ViewModel (it's updated by `onStreamingStateChanged` callback from `NativeStreamPanel`). The field will become derived from `activeStreamHostIds` implicitly — we do NOT remove `isStreaming` from `WorkspaceUiState` yet (the toolbar still uses it). Leave it as-is for now; the toolbar will still receive `isStreaming` from the focused panel.

---

## Task 2: Add "Stream" / "Stop" buttons to `HostManagerPanel`

**Files:**
- Modify: `app/src/main/java/com/xrworkspace/app/ui/components/HostManagerPanel.kt`

**Step 1: Add `activeStreamHostIds` and `onStreamHost`/`onStopStreamHost` parameters**

Find the function signature:
```kotlin
fun HostManagerPanel(
    hostConfigs: List<HostConfig>,
    activeHostId: String?,
    onSelectHost: (String) -> Unit,
    onAddHost: (String, String) -> Unit,
    onRemoveHost: (String) -> Unit,
    onDismiss: () -> Unit,
    onUpdateHostProfile: (hostId: String, profile: StreamSettings?) -> Unit = { _, _ -> },
)
```
Replace with:
```kotlin
fun HostManagerPanel(
    hostConfigs: List<HostConfig>,
    activeHostId: String?,
    activeStreamHostIds: Set<String> = emptySet(),
    onSelectHost: (String) -> Unit,
    onStreamHost: (String) -> Unit = {},
    onStopStreamHost: (String) -> Unit = {},
    onAddHost: (String, String) -> Unit,
    onRemoveHost: (String) -> Unit,
    onDismiss: () -> Unit,
    onUpdateHostProfile: (hostId: String, profile: StreamSettings?) -> Unit = { _, _ -> },
)
```

**Step 2: Add Stream/Stop chip to each host row**

In the host row (inside `items(hostConfigs)`, after the Delete chip block ending around line 225), add a Stream chip. The chips are in a `Row` with `Arrangement.spacedBy(8.dp)`. Add the Stream chip BEFORE the Delete chip:

Find:
```kotlin
                            // Delete with confirmation
                            if (isConfirmingDelete) {
```
Replace with:
```kotlin
                            // Stream toggle chip
                            val isStreaming = host.id in activeStreamHostIds
                            FilterChip(
                                selected = isStreaming,
                                onClick = {
                                    if (isStreaming) onStopStreamHost(host.id)
                                    else onStreamHost(host.id)
                                },
                                label = { Text(if (isStreaming) "Streaming" else "Stream") },
                                colors = if (isStreaming) FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ) else FilterChipDefaults.filterChipColors(),
                            )

                            // Delete with confirmation
                            if (isConfirmingDelete) {
```

---

## Task 3: Update `SpatialWorkspace` to wire new callbacks and render N stream panels

**Files:**
- Modify: `app/src/main/java/com/xrworkspace/app/ui/SpatialWorkspace.kt`

**Step 1: Add `onOpenStream` and `onCloseStream` callback parameters**

Find the `SpatialWorkspace` function signature (around line 65). It has many named callbacks. Add two more after `onToggleHostManager`:

Find:
```kotlin
    onToggleHostManager: () -> Unit = {},
    onAddHost: (String, String) -> Unit = { _, _ -> },
```
Replace with:
```kotlin
    onToggleHostManager: () -> Unit = {},
    onOpenStream: (String) -> Unit = {},
    onCloseStream: (String) -> Unit = {},
    onAddHost: (String, String) -> Unit = { _, _ -> },
```

**Step 2: Pass new params into `HostManagerPanel`**

Find the `HostManagerPanel(` call inside `SpatialWorkspace`. It currently passes `hostConfigs`, `activeHostId`, `onSelectHost`, `onAddHost`, `onRemoveHost`, `onDismiss`, `onUpdateHostProfile`. Add the three new params:

Find:
```kotlin
                HostManagerPanel(
                    hostConfigs = uiState.hostConfigs,
                    activeHostId = uiState.activeHostId,
                    onSelectHost = onSelectHost,
                    onAddHost = onAddHost,
                    onRemoveHost = onRemoveHost,
                    onDismiss = onToggleHostManager,
                    onUpdateHostProfile = onUpdateHostProfile,
                )
```
Replace with:
```kotlin
                HostManagerPanel(
                    hostConfigs = uiState.hostConfigs,
                    activeHostId = uiState.activeHostId,
                    activeStreamHostIds = uiState.activeStreamHostIds,
                    onSelectHost = onSelectHost,
                    onStreamHost = onOpenStream,
                    onStopStreamHost = onCloseStream,
                    onAddHost = onAddHost,
                    onRemoveHost = onRemoveHost,
                    onDismiss = onToggleHostManager,
                    onUpdateHostProfile = onUpdateHostProfile,
                )
```

**Step 3: Replace the single main desktop panel with a multi-stream loop**

The current main desktop `SpatialPanel` (lines 334–415) is a single panel showing one `NativeStreamPanel`. We keep it as-is for the "active host" concept (settings focus, WoL, app selector). **We do NOT remove it.** Instead we add a NEW `SpatialCurvedRow` section below it for the multi-stream panels.

Find the section comment and the start of the dynamic bookmark panels section:
```kotlin
        // Dynamic bookmark panels — flat grid or native cylindrical arc via SpatialCurvedRow
        val curvedSettings = uiState.curvedPanelSettings
```
Replace with:
```kotlin
        // Active stream panels — one SpatialPanel per streaming host in a curved arc
        val activeStreamHosts = uiState.hostConfigs.filter { it.id in uiState.activeStreamHostIds }
        if (activeStreamHosts.isNotEmpty()) {
            if (activeStreamHosts.size == 1) {
                // Single stream: flat offset to left of main panel
                val host = activeStreamHosts.first()
                SpatialPanel(
                    modifier = SubspaceModifier
                        .alpha(animatedAlpha.value)
                        .width(1400.dp)
                        .height(900.dp)
                        .offset(x = (-1450).dp),
                    dragPolicy = MovePolicy(isEnabled = true),
                    resizePolicy = ResizePolicy(isEnabled = true),
                ) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        NativeStreamPanel(
                            serverAddress = host.address,
                            streamSettings = host.qualityProfile ?: uiState.streamSettings,
                            audioSettings = uiState.audioSettings,
                            autoReconnectEnabled = uiState.autoReconnectEnabled,
                            onStreamingStateChanged = {},
                        )
                    }
                }
            } else {
                // Multiple streams: spread in a SpatialCurvedRow to the left of main panel
                SpatialCurvedRow(
                    modifier = SubspaceModifier.offset(x = (-1450).dp),
                    curveRadius = uiState.curvedPanelSettings.radiusDp.dp,
                ) {
                    activeStreamHosts.forEach { host ->
                        SpatialPanel(
                            modifier = SubspaceModifier
                                .alpha(animatedAlpha.value)
                                .width(1200.dp)
                                .height(750.dp),
                            dragPolicy = MovePolicy(isEnabled = true),
                            resizePolicy = ResizePolicy(isEnabled = true),
                        ) {
                            Surface(modifier = Modifier.fillMaxSize()) {
                                NativeStreamPanel(
                                    serverAddress = host.address,
                                    streamSettings = host.qualityProfile ?: uiState.streamSettings,
                                    audioSettings = uiState.audioSettings,
                                    autoReconnectEnabled = uiState.autoReconnectEnabled,
                                    onStreamingStateChanged = {},
                                )
                            }
                        }
                    }
                }
            }
        }

        // Dynamic bookmark panels — flat grid or native cylindrical arc via SpatialCurvedRow
        val curvedSettings = uiState.curvedPanelSettings
```

**Step 4: Verify `SpatialCurvedRow` import is present**

The import `import androidx.xr.compose.subspace.SpatialCurvedRow` should already be present from the curved panel migration. Confirm it's there; add if missing.

---

## Task 4: Wire `openStream`/`closeStream` in `XRWorkspaceApp` / the root composition

**Files:**
- Modify: `app/src/main/java/com/xrworkspace/app/ui/XRWorkspaceApp.kt` (or wherever `SpatialWorkspace` is called with the ViewModel)

**Step 1: Find where `SpatialWorkspace` is called**

Search for `SpatialWorkspace(` in the app source. It will be in `XRWorkspaceApp.kt` or `MainActivity.kt`, receiving `uiState` and all the `on*` callbacks.

**Step 2: Add `onOpenStream` and `onCloseStream` wiring**

Find the `SpatialWorkspace(` call. Add the two new callbacks (they delegate to `viewModel.openStream` / `viewModel.closeStream`):

```kotlin
onOpenStream = { hostId -> viewModel.openStream(hostId) },
onCloseStream = { hostId -> viewModel.closeStream(hostId) },
```

Add them in the same position as they appear in the `SpatialWorkspace` signature (after `onToggleHostManager`).

---

## Task 5: Verify and commit

**Step 1: Grep for compilation errors**

```
Grep: activeStreamHostIds
Path: FrameStation/app/src/
Expected: found in WorkspaceUiState, WorkspaceViewModel, SpatialWorkspace, HostManagerPanel, XRWorkspaceApp
```

**Step 2: Verify HostManagerPanel call sites all compile**

```
Grep: HostManagerPanel(
Path: FrameStation/app/src/
Expected: found in SpatialWorkspace.kt only — verify all new params are passed
```

**Step 3: Commit**

```bash
git add -A
git commit -m "feat: multiple simultaneous streams from different host PCs

Add activeStreamHostIds: Set<String> to WorkspaceUiState to track which
hosts have open stream panels. Add openStream()/closeStream() to
WorkspaceViewModel. Add Stream/Stop chip to each host row in
HostManagerPanel. Render one SpatialPanel per active stream host in a
SpatialCurvedRow to the left of the main panel in SpatialWorkspace.
Single active stream uses a flat offset; multiple use SpatialCurvedRow."
```

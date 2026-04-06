# Passthrough / Environment Toggle Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a toolbar button that toggles the Samsung Galaxy XR headset between fully virtual environment and full passthrough (see-through) mode.

**Architecture:** `session.scene.spatialEnvironment.preferredPassthroughOpacity` is the SceneCore API for controlling passthrough (0.0 = fully virtual, 1.0 = fully see-through). We access the XR `Session` via `LocalSession.current` inside a Compose composable. State lives in `WorkspaceUiState.isPassthroughActive: Boolean`. The toolbar gains an eye-icon `FilterChip` that calls `onTogglePassthrough`. The toggle is gated on `LocalSpatialCapabilities.current.isPassthroughControlEnabled` — on non-XR or unsupported devices it is simply hidden.

**Tech Stack:** Kotlin, `androidx.xr.scenecore.Session`, `SpatialEnvironment.preferredPassthroughOpacity`, `LocalSpatialCapabilities`, `LocalSession`

---

## Context

**Files to touch:**
- Modify: `app/src/main/java/com/xrworkspace/app/viewmodel/WorkspaceViewModel.kt` — add `isPassthroughActive` to state + `togglePassthrough()` method
- Modify: `app/src/main/java/com/xrworkspace/app/ui/components/WorkspaceToolbar.kt` — add passthrough toggle chip
- Modify: `app/src/main/java/com/xrworkspace/app/ui/SpatialWorkspace.kt` — read XR session, apply opacity, wire toolbar callback
- Modify: `app/src/main/java/com/xrworkspace/app/ui/XRWorkspaceApp.kt` — pass `onTogglePassthrough` through

**Key API:**
```kotlin
import androidx.xr.scenecore.Session
import androidx.xr.compose.platform.LocalSession
import androidx.xr.compose.platform.LocalSpatialCapabilities

// Get session inside composable:
val session = LocalSession.current

// Set passthrough (call from LaunchedEffect or side effect, NOT directly in composition):
session?.scene?.spatialEnvironment?.preferredPassthroughOpacity = 1.0f  // full passthrough
session?.scene?.spatialEnvironment?.preferredPassthroughOpacity = 0.0f  // fully virtual

// Check support:
val supported = LocalSpatialCapabilities.current.isPassthroughControlEnabled
```

---

## Task 1: Add `isPassthroughActive` to `WorkspaceUiState` and ViewModel

**Files:**
- Modify: `app/src/main/java/com/xrworkspace/app/viewmodel/WorkspaceViewModel.kt`

**Step 1: Add field to `WorkspaceUiState`**

Find:
```kotlin
    val showHostManager: Boolean = false,
```
Add after it:
```kotlin
    /** Whether the headset is currently in passthrough (see-through) mode. */
    val isPassthroughActive: Boolean = false,
```

**Step 2: Add `togglePassthrough()` to ViewModel**

Find the end of the public ViewModel methods (near `updateCurvedPanelSettings`). Add:

```kotlin
    /** Toggle between passthrough (see-through) and virtual environment. */
    fun togglePassthrough() {
        _uiState.update { it.copy(isPassthroughActive = !it.isPassthroughActive) }
    }
```

The actual SceneCore call happens in `SpatialWorkspace` as a side effect (SceneCore calls must be made from a composable context). The ViewModel only owns the state.

---

## Task 2: Add passthrough chip to `WorkspaceToolbar`

**Files:**
- Modify: `app/src/main/java/com/xrworkspace/app/ui/components/WorkspaceToolbar.kt`

**Step 1: Add import for RemoveRedEye icon**

In the imports block, add:
```kotlin
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.VisibilityOff
```

**Step 2: Add parameters to `WorkspaceToolbar`**

Find:
```kotlin
    onSwitchMonitor: (() -> Unit)? = null,
) {
```
Replace with:
```kotlin
    onSwitchMonitor: (() -> Unit)? = null,
    isPassthroughActive: Boolean = false,
    isPassthroughSupported: Boolean = false,
    onTogglePassthrough: (() -> Unit)? = null,
) {
```

**Step 3: Add passthrough chip in the toolbar Row**

Add it immediately before the `// Desktop toggle` section (around line 82). It should only appear when supported:

Find:
```kotlin
            // Desktop toggle
            FilterChip(
                selected = showDesktop,
                onClick = onToggleDesktop,
                label = { Text("Desktop") },
```
Add BEFORE this block:
```kotlin
            // Passthrough toggle — only shown when device supports passthrough control
            if (isPassthroughSupported) {
                FilterChip(
                    selected = isPassthroughActive,
                    onClick = { onTogglePassthrough?.invoke() },
                    label = {
                        Icon(
                            imageVector = if (isPassthroughActive)
                                Icons.Default.RemoveRedEye
                            else
                                Icons.Default.VisibilityOff,
                            contentDescription = if (isPassthroughActive) "Disable passthrough" else "Enable passthrough",
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    colors = if (isPassthroughActive) FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) else FilterChipDefaults.filterChipColors(),
                )
            }

            // Desktop toggle
            FilterChip(
                selected = showDesktop,
                onClick = onToggleDesktop,
                label = { Text("Desktop") },
```

---

## Task 3: Wire passthrough in `SpatialWorkspace`

This is where the actual SceneCore API call happens. The composable reads `LocalSession` and applies the opacity as a `LaunchedEffect` whenever `isPassthroughActive` changes.

**Files:**
- Modify: `app/src/main/java/com/xrworkspace/app/ui/SpatialWorkspace.kt`

**Step 1: Add imports**

Find the existing `LocalSpatialCapabilities` import:
```kotlin
import androidx.xr.compose.platform.LocalSpatialCapabilities
```
Add after it:
```kotlin
import androidx.xr.compose.platform.LocalSession
```

**Step 2: Add passthrough callback parameter to `SpatialWorkspace`**

Find:
```kotlin
    onUpdateCurvedPanelSettings: (CurvedPanelSettings) -> Unit = {},
```
Add after it:
```kotlin
    onTogglePassthrough: () -> Unit = {},
```

**Step 3: Read session and apply opacity as side effect**

Find the `val animatedAlpha` line near the top of the composable body. Add AFTER it:

```kotlin
    // Passthrough control — apply opacity to XR environment when state changes
    val xrSession = LocalSession.current
    val isPassthroughSupported = LocalSpatialCapabilities.current.isPassthroughControlEnabled
    LaunchedEffect(uiState.isPassthroughActive) {
        val targetOpacity = if (uiState.isPassthroughActive) 1.0f else 0.0f
        xrSession?.scene?.spatialEnvironment?.preferredPassthroughOpacity = targetOpacity
    }
```

**Step 4: Pass new params into `WorkspaceToolbar`**

Find the `WorkspaceToolbar(` call (around line 396). Add these three params at the end before the closing `)`:

Find:
```kotlin
                    onPresetsClick = onPresetsClick,
                )
```
Replace with:
```kotlin
                    onPresetsClick = onPresetsClick,
                    isPassthroughActive = uiState.isPassthroughActive,
                    isPassthroughSupported = isPassthroughSupported,
                    onTogglePassthrough = onTogglePassthrough,
                )
```

---

## Task 4: Wire through `XRWorkspaceApp`

**Files:**
- Modify: `app/src/main/java/com/xrworkspace/app/ui/XRWorkspaceApp.kt`

**Step 1: Add `onTogglePassthrough` to the `SpatialWorkspace` call**

Find:
```kotlin
            onUpdateCurvedPanelSettings = viewModel::updateCurvedPanelSettings,
```
Add after it:
```kotlin
            onTogglePassthrough = viewModel::togglePassthrough,
```

---

## Task 5: Verify and commit

**Step 1: Grep for new state field and callback**
```
Grep: isPassthroughActive
Expected: found in WorkspaceUiState, WorkspaceViewModel, SpatialWorkspace, WorkspaceToolbar
```

**Step 2: Grep for LocalSession**
```
Grep: LocalSession
Expected: found in SpatialWorkspace.kt only
```

**Step 3: Commit**
```bash
git add -A
git commit -m "feat: passthrough / environment toggle in toolbar

Add eye-icon FilterChip to WorkspaceToolbar that toggles between fully
virtual environment and full passthrough (see-through) mode. Chip is only
shown when isPassthroughControlEnabled is true. Uses LocalSession to access
session.scene.spatialEnvironment.preferredPassthroughOpacity (0.0=virtual,
1.0=passthrough) via LaunchedEffect. State tracked in WorkspaceUiState.
isPassthroughActive. Toggle wired through SpatialWorkspace and XRWorkspaceApp
to WorkspaceViewModel.togglePassthrough()."
```

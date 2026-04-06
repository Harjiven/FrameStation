# Curved Panel Rendering — Native `SpatialCurvedRow` Migration

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the manual sin/cos arc math in `SpatialWorkspace.kt` with the native `SpatialCurvedRow` composable from Compose for XR, which handles arc layout and per-panel Y-rotation automatically.

**Architecture:** Bookmark panels currently use `CurvedPanelGeometry` (manual sin/cos) to compute x/z offsets, but never apply `rotationYDeg`. `SpatialCurvedRow(curveRadius = ...)` does all of this natively. We keep `CurvedPanelSettings` (isEnabled, radiusDp) as the user-visible model — only the layout implementation in `SpatialWorkspace.kt` changes. `CurvedPanelGeometry.kt` is deleted (dead code once migration is complete). `angleStepDeg` is removed from settings since `SpatialCurvedRow` auto-distributes panels by their size.

**Tech Stack:** Kotlin, Jetpack Compose for XR 1.0.0-alpha10, `androidx.xr.compose.subspace.SpatialCurvedRow`

---

## Context

**Files to touch:**
- Modify: `app/src/main/java/com/xrworkspace/app/ui/SpatialWorkspace.kt` (lines 418–463 — the bookmark panel loop)
- Modify: `app/src/main/java/com/xrworkspace/app/model/CurvedPanelSettings.kt` (drop `angleStepDeg`)
- Modify: `app/src/main/java/com/xrworkspace/app/model/CurvedPanelSettingsManager.kt` (stop reading/writing `angleStepDeg`)
- Modify: `app/src/main/java/com/xrworkspace/app/ui/components/SettingsDialog.kt` (remove angle step slider)
- Delete: `app/src/main/java/com/xrworkspace/app/model/CurvedPanelGeometry.kt`
- Modify: `app/src/test/java/com/xrworkspace/app/model/CurvedPanelGeometryTest.kt` (delete — test of deleted class)

**Key constraint:** `SpatialCurvedRow` is imported from `androidx.xr.compose.subspace.SpatialCurvedRow`. It already ships in `androidx.xr.compose:compose:1.0.0-alpha10` (already in deps — no new dependency needed).

**Existing behavior to preserve:**
- Single bookmark panel → no curve (flat, at x=980dp offset from main panel)
- Zero bookmarks → nothing rendered
- Flat grid fallback when `curvedPanelSettings.isEnabled == false`
- Drag and resize still enabled on each panel
- Fade-in alpha animation still applied

**`SpatialCurvedRow` behavior:**
- Wraps children in a cylindrical arc around the user
- `curveRadius: Dp` controls the arc radius (use `settings.radiusDp.dp`)
- Panels are auto-distributed along the arc by their natural size
- No manual x/z math needed — remove all offset calculations inside the arc branch
- The `SpatialCurvedRow` itself needs a `SubspaceModifier.offset(x = 980.dp)` to position it beside the main panel (same base x as before)

---

## Task 1: Remove `angleStepDeg` from `CurvedPanelSettings`

`SpatialCurvedRow` auto-distributes panels; the angle step concept is gone.

**Files:**
- Modify: `app/src/main/java/com/xrworkspace/app/model/CurvedPanelSettings.kt`
- Modify: `app/src/main/java/com/xrworkspace/app/model/CurvedPanelSettingsManager.kt`

**Step 1: Update `CurvedPanelSettings.kt`**

Replace the entire file with:

```kotlin
// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.model

/**
 * User-configurable settings for curved panel rendering.
 *
 * @property isEnabled Whether panels are arranged along a cylindrical arc.
 * @property radiusDp Radius of the arc in dp (passed to SpatialCurvedRow).
 */
data class CurvedPanelSettings(
    val isEnabled: Boolean = false,
    val radiusDp: Float = 825f,
)
```

Note the default radius changes from `1200f` to `825f` — this is the Google-recommended value for surrounding the user.

**Step 2: Update `CurvedPanelSettingsManager.kt`**

Remove all `angleStepDeg` reads/writes. Replace the entire file with:

```kotlin
// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.model

import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Persists [CurvedPanelSettings] to SharedPreferences as JSON.
 * Falls back to defaults when no saved data exists or parsing fails.
 */
class CurvedPanelSettingsManager(private val prefs: SharedPreferences) {
    companion object {
        private const val KEY = "curved_panel_settings_json"
    }

    fun loadCurvedPanelSettings(): CurvedPanelSettings {
        val json = prefs.getString(KEY, null) ?: return CurvedPanelSettings()
        return try {
            val obj = JSONObject(json)
            CurvedPanelSettings(
                isEnabled = obj.optBoolean("isEnabled", false),
                radiusDp = obj.optDouble("radiusDp", 825.0).toFloat().coerceIn(400f, 2400f),
            )
        } catch (_: Exception) {
            CurvedPanelSettings()
        }
    }

    fun saveCurvedPanelSettings(settings: CurvedPanelSettings) {
        val obj = JSONObject().apply {
            put("isEnabled", settings.isEnabled)
            put("radiusDp", settings.radiusDp.toDouble())
        }
        prefs.edit().putString(KEY, obj.toString()).apply()
    }
}
```

**Step 3: Check for compile errors from removed field**

Search the entire codebase for `angleStepDeg` and fix any remaining references:

```
Grep: angleStepDeg
Expected: Only found in SettingsDialog.kt (handled in Task 2). Zero references elsewhere.
```

---

## Task 2: Remove the angle step slider from `SettingsDialog.kt`

**Files:**
- Modify: `app/src/main/java/com/xrworkspace/app/ui/components/SettingsDialog.kt`

**Step 1: Find the angle step slider block**

Search for `angleStepDeg` in `SettingsDialog.kt`. It will be a slider section approximately:

```kotlin
// Panel Spacing slider — angleStepDeg (10–60°)
Slider(
    value = localCurvedSettings.angleStepDeg,
    onValueChange = { localCurvedSettings = localCurvedSettings.copy(angleStepDeg = it) },
    valueRange = 10f..60f,
    ...
)
```

**Step 2: Delete the entire angle step slider block**

Remove from the label text above the slider through to the end of the slider composable (including any `Text` showing the current value). Keep the radius slider and the isEnabled toggle — do not touch them.

**Step 3: Fix the `localCurvedSettings` initialization**

If `localCurvedSettings` was initialized with `angleStepDeg`, update any `.copy(angleStepDeg = ...)` calls. After the data class change in Task 1, these will be compile errors — remove them.

---

## Task 3: Replace the manual arc loop with `SpatialCurvedRow` in `SpatialWorkspace.kt`

This is the core change. The flat grid branch stays; only the curved branch changes.

**Files:**
- Modify: `app/src/main/java/com/xrworkspace/app/ui/SpatialWorkspace.kt`

**Step 1: Add the `SpatialCurvedRow` import**

In the imports block (around line 28), add:

```kotlin
import androidx.xr.compose.subspace.SpatialCurvedRow
```

**Step 2: Replace lines 417–463 (the bookmark panel loop)**

Current code (lines 417–463):
```kotlin
// Dynamic bookmark panels — positioned in a flat grid or curved arc
openBookmarks.forEachIndexed { index, bookmark ->
    val curvedSettings = uiState.curvedPanelSettings
    val xOffsetDp: Float
    val yOffsetDp: Float
    val zOffsetDp: Float

    if (curvedSettings.isEnabled && openBookmarks.size > 1) {
        // Curved arc: spread panels horizontally along the arc, single row
        val panelOffset = CurvedPanelGeometry.calculateOffset(
            index = index,
            count = openBookmarks.size,
            settings = curvedSettings,
        )
        xOffsetDp = 980f + panelOffset.xDp
        yOffsetDp = 0f
        zOffsetDp = panelOffset.zDp
    } else {
        // Flat grid: columns of 2 panels
        val column = index / 2
        val row = index % 2
        xOffsetDp = (980 + column * 520).toFloat()
        yOffsetDp = (if (row == 0) 220 else -220).toFloat()
        zOffsetDp = 0f
    }

    SpatialPanel(
        modifier = SubspaceModifier
            .alpha(animatedAlpha.value)
            .width(500.dp)
            .height(430.dp)
            .offset(
                x = xOffsetDp.dp,
                y = yOffsetDp.dp,
                z = zOffsetDp.dp,
            ),
        dragPolicy = MovePolicy(isEnabled = true),
        resizePolicy = ResizePolicy(isEnabled = true),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            BookmarkWebViewPanel(
                bookmark = bookmark,
                onClose = { onToggleBookmark(bookmark.id) },
            )
        }
    }
}
```

Replace with:

```kotlin
// Dynamic bookmark panels — flat grid or native cylindrical arc via SpatialCurvedRow
val curvedSettings = uiState.curvedPanelSettings
if (curvedSettings.isEnabled && openBookmarks.size > 1) {
    // Native curved arc: SpatialCurvedRow handles all arc math and panel rotation
    SpatialCurvedRow(
        modifier = SubspaceModifier.offset(x = 980.dp),
        curveRadius = curvedSettings.radiusDp.dp,
    ) {
        openBookmarks.forEach { bookmark ->
            SpatialPanel(
                modifier = SubspaceModifier
                    .alpha(animatedAlpha.value)
                    .width(500.dp)
                    .height(430.dp),
                dragPolicy = MovePolicy(isEnabled = true),
                resizePolicy = ResizePolicy(isEnabled = true),
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BookmarkWebViewPanel(
                        bookmark = bookmark,
                        onClose = { onToggleBookmark(bookmark.id) },
                    )
                }
            }
        }
    }
} else {
    // Flat grid: columns of 2 panels
    openBookmarks.forEachIndexed { index, bookmark ->
        val column = index / 2
        val row = index % 2
        val xOffsetDp = (980 + column * 520).toFloat()
        val yOffsetDp = (if (row == 0) 220 else -220).toFloat()

        SpatialPanel(
            modifier = SubspaceModifier
                .alpha(animatedAlpha.value)
                .width(500.dp)
                .height(430.dp)
                .offset(
                    x = xOffsetDp.dp,
                    y = yOffsetDp.dp,
                ),
            dragPolicy = MovePolicy(isEnabled = true),
            resizePolicy = ResizePolicy(isEnabled = true),
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                BookmarkWebViewPanel(
                    bookmark = bookmark,
                    onClose = { onToggleBookmark(bookmark.id) },
                )
            }
        }
    }
}
```

**Step 3: Remove the now-unused `CurvedPanelGeometry` import**

Delete line:
```kotlin
import com.xrworkspace.app.model.CurvedPanelGeometry
```

---

## Task 4: Delete `CurvedPanelGeometry.kt` and its test

The class is now dead code.

**Files:**
- Delete: `app/src/main/java/com/xrworkspace/app/model/CurvedPanelGeometry.kt`
- Delete: `app/src/test/java/com/xrworkspace/app/model/CurvedPanelGeometryTest.kt` (if it exists)

**Step 1: Verify no remaining references**

```
Grep: CurvedPanelGeometry
Expected: zero results across all .kt files
```

**Step 2: Delete the files**

```bash
rm app/src/main/java/com/xrworkspace/app/model/CurvedPanelGeometry.kt
rm app/src/test/java/com/xrworkspace/app/model/CurvedPanelGeometryTest.kt  # if exists
```

---

## Task 5: Update the Settings radius slider range and label

The slider in `SettingsDialog.kt` currently ranges 400–2400dp with label "Arc Radius". Update its default and label to reflect the new recommended range.

**Files:**
- Modify: `app/src/main/java/com/xrworkspace/app/ui/components/SettingsDialog.kt`

**Step 1: Find the radius slider**

Search for `radiusDp` in `SettingsDialog.kt`. It will look like:

```kotlin
Slider(
    value = localCurvedSettings.radiusDp,
    onValueChange = { localCurvedSettings = localCurvedSettings.copy(radiusDp = it) },
    valueRange = 400f..2400f,
    ...
)
```

**Step 2: Update range and label**

Change the `valueRange` to `400f..1600f` and update any label displaying the value to show `"${localCurvedSettings.radiusDp.toInt()} dp"`. The range 400–1600 is appropriate for `SpatialCurvedRow` — 825dp is the recommended midpoint.

---

## Task 6: Verify — LSP diagnostics clean, no stray references

**Step 1: Run LSP diagnostics on all changed files**

Check for errors in:
- `SpatialWorkspace.kt`
- `CurvedPanelSettings.kt`
- `CurvedPanelSettingsManager.kt`
- `SettingsDialog.kt`

Expected: zero errors or warnings related to this change.

**Step 2: Grep for stray references**

```
Grep: CurvedPanelGeometry
Grep: angleStepDeg
Expected: zero matches in any .kt file
```

**Step 3: Commit**

```bash
git add -A
git commit -m "feat: migrate bookmark panels to native SpatialCurvedRow

Replace manual sin/cos arc math (CurvedPanelGeometry) with the native
SpatialCurvedRow composable from Compose for XR. This applies correct
Y-axis panel rotation automatically, which was previously calculated but
never applied. Remove angleStepDeg from CurvedPanelSettings since
SpatialCurvedRow auto-distributes panels. Update default radius to 825dp
(Google recommended). Delete CurvedPanelGeometry.kt (dead code)."
```

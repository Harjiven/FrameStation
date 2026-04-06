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

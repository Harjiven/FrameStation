// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.model

/**
 * Represents a display output available on the streaming host.
 *
 * @param systemName  Platform-specific identifier used by Sunshine (e.g. `\\.\DISPLAY1`).
 * @param displayName Human-readable label shown in the UI (e.g. "Display 1").
 * @param isActive    Whether this is the display currently being captured by Sunshine.
 * @param index       Zero-based ordering index for display in the list.
 */
data class MonitorInfo(
    val systemName: String,
    val displayName: String,
    val isActive: Boolean = false,
    val index: Int = 0,
)

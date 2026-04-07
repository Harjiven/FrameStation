// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.util

/**
 * Shared user-facing formatters for streaming-related values.
 *
 * Centralizing these here keeps the same string format across SettingsPanel,
 * HostManagerPanel, FallbackWorkspace, and any future surface that needs to
 * display a bitrate to the user.
 */

/**
 * Format a bitrate (in kilobits per second) for display.
 *
 * Examples:
 *   1500   -> "1500 kbps"
 *   20000  -> "20 Mbps"
 *   25500  -> "25.5 Mbps"
 *   25050  -> "25.1 Mbps"  (rounded to one decimal place)
 */
fun formatBitrate(kbps: Int): String =
    if (kbps >= 1000) "${"%.1f".format(kbps / 1000.0).trimEnd('0').trimEnd('.')} Mbps"
    else "$kbps kbps"

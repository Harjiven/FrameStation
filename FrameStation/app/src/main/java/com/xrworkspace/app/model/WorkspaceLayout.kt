// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.model

import java.util.UUID

/**
 * A named snapshot of the workspace state that can be saved and recalled.
 * Captures which panels are visible and which bookmarks are open.
 * Panel 3D positions are NOT captured (they are managed by the XR drag system).
 */
data class WorkspaceLayout(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val showDesktopPanel: Boolean = true,
    val openBookmarkIds: Set<String> = emptySet(),
    val createdAt: Long = System.currentTimeMillis(),
)

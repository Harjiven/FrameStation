// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.model

import java.util.UUID

data class Bookmark(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String,
    val icon: String? = null,
    /** When true, the WebView sends a desktop Chrome UA instead of the default mobile one. */
    val useDesktopUa: Boolean = false,
    /**
     * Ephemeral tabs are opened via "New Tab" and are never persisted to the bookmark list.
     * They show a URL bar so the user can navigate freely.
     */
    val isEphemeral: Boolean = false,
)

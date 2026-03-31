// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.model

/**
 * Kotlin-friendly mirror of [com.limelight.nvstream.http.NvApp].
 * Keeps the UI layer decoupled from moonlight-core types.
 */
data class ServerApp(
    val appId: Int,
    val appName: String,
    val isHdrSupported: Boolean = false,
    val isRunning: Boolean = false,
)

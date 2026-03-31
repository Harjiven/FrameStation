// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.model

import java.util.UUID

data class HostConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val address: String,
    val macAddress: String? = null,
    val isPaired: Boolean = false,
    val gpuType: String? = null,
    val certFileName: String? = null,
    val lastConnected: Long = 0L,
)

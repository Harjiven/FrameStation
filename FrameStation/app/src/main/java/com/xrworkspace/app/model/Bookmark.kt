// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.model

import java.util.UUID

data class Bookmark(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String,
    val icon: String? = null,
)

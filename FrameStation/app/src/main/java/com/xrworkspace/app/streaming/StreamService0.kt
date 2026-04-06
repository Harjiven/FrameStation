// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.streaming

/**
 * Process-isolated stream service slot 0 (android:process=":stream0").
 *
 * Distinct class name required by Android — two <service> entries with the same
 * android:name are invalid; only the first would be registered.
 *
 * @see StreamService for the implementation.
 * @see StreamService1 for the second slot.
 */
class StreamService0 : StreamService()

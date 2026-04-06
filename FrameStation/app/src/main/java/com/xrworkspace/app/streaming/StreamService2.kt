// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.streaming

/**
 * Process-isolated stream service slot 2 (android:process=":stream2").
 *
 * Distinct class name required: Android rejects duplicate android:name entries.
 *
 * @see StreamService for the implementation.
 * @see StreamService0 for slot 0.
 * @see StreamService1 for slot 1.
 */
class StreamService2 : StreamService()

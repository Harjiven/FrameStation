// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ServerApp] data class defaults and equality.
 */
class ServerAppTest {

    @Test
    fun `default values are correct`() {
        val app = ServerApp(appId = 1, appName = "Desktop")
        assertEquals(1, app.appId)
        assertEquals("Desktop", app.appName)
        assertFalse(app.isHdrSupported)
        assertFalse(app.isRunning)
    }

    @Test
    fun `explicit values override defaults`() {
        val app = ServerApp(
            appId = 42,
            appName = "Steam Big Picture",
            isHdrSupported = true,
            isRunning = true,
        )
        assertEquals(42, app.appId)
        assertEquals("Steam Big Picture", app.appName)
        assertTrue(app.isHdrSupported)
        assertTrue(app.isRunning)
    }

    @Test
    fun `data class equality works by value`() {
        val a = ServerApp(appId = 1, appName = "Desktop")
        val b = ServerApp(appId = 1, appName = "Desktop")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `data class inequality on different appId`() {
        val a = ServerApp(appId = 1, appName = "Desktop")
        val b = ServerApp(appId = 2, appName = "Desktop")
        assertFalse(a == b)
    }

    @Test
    fun `copy preserves unchanged fields`() {
        val original = ServerApp(appId = 5, appName = "Game", isHdrSupported = true)
        val updated = original.copy(isRunning = true)
        assertEquals(5, updated.appId)
        assertEquals("Game", updated.appName)
        assertTrue(updated.isHdrSupported)
        assertTrue(updated.isRunning)
    }
}

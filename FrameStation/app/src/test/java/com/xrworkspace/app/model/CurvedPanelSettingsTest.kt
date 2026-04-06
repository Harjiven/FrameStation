// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [CurvedPanelSettings] data class: defaults, equality, and copy.
 */
class CurvedPanelSettingsTest {

    @Test
    fun `defaults are disabled with radius 825`() {
        val settings = CurvedPanelSettings()
        assertFalse(settings.isEnabled)
        assertEquals(825f, settings.radiusDp, 0f)
    }

    @Test
    fun `data class equality with same values`() {
        val a = CurvedPanelSettings(isEnabled = true, radiusDp = 800f)
        val b = CurvedPanelSettings(isEnabled = true, radiusDp = 800f)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `data class inequality when isEnabled differs`() {
        val a = CurvedPanelSettings(isEnabled = true)
        val b = CurvedPanelSettings(isEnabled = false)
        assertNotEquals(a, b)
    }

    @Test
    fun `data class inequality when radiusDp differs`() {
        val a = CurvedPanelSettings(radiusDp = 1000f)
        val b = CurvedPanelSettings(radiusDp = 2000f)
        assertNotEquals(a, b)
    }

    @Test
    fun `copy preserves unchanged fields`() {
        val original = CurvedPanelSettings(isEnabled = true, radiusDp = 900f)
        val copied = original.copy(radiusDp = 1500f)
        assertTrue(copied.isEnabled)
        assertEquals(1500f, copied.radiusDp, 0f)
    }

    @Test
    fun `copy can change isEnabled`() {
        val original = CurvedPanelSettings(isEnabled = false)
        val copied = original.copy(isEnabled = true)
        assertTrue(copied.isEnabled)
        assertEquals(original.radiusDp, copied.radiusDp, 0f)
    }

    @Test
    fun `copy can change radiusDp`() {
        val original = CurvedPanelSettings(radiusDp = 825f)
        val copied = original.copy(radiusDp = 1200f)
        assertEquals(1200f, copied.radiusDp, 0f)
    }

    @Test
    fun `all fields can be set via constructor`() {
        val settings = CurvedPanelSettings(
            isEnabled = true,
            radiusDp = 500f,
        )
        assertTrue(settings.isEnabled)
        assertEquals(500f, settings.radiusDp, 0f)
    }
}

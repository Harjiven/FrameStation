// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FallbackWorkspaceTest {

    @Test
    fun `shouldUseFallbackWorkspace returns true when spatial UI is disabled`() {
        assertTrue(shouldUseFallbackWorkspace(isSpatialUiEnabled = false))
    }

    @Test
    fun `shouldUseFallbackWorkspace returns false when spatial UI is enabled`() {
        assertFalse(shouldUseFallbackWorkspace(isSpatialUiEnabled = true))
    }

    @Test
    fun `non-XR device always shows fallback`() {
        // Simulate multiple device states
        listOf(false).forEach { enabled ->
            assertTrue("Expected fallback for enabled=$enabled", shouldUseFallbackWorkspace(enabled))
        }
    }

    @Test
    fun `XR device never shows fallback`() {
        listOf(true).forEach { enabled ->
            assertFalse("Expected no fallback for enabled=$enabled", shouldUseFallbackWorkspace(enabled))
        }
    }
}

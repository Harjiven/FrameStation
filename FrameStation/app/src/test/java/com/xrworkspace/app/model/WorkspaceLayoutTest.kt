// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceLayoutTest {

    @Test
    fun `WorkspaceLayout has unique id by default`() {
        val a = WorkspaceLayout(name = "Layout A")
        val b = WorkspaceLayout(name = "Layout B")
        assertNotEquals(a.id, b.id)
    }

    @Test
    fun `WorkspaceLayout copy preserves unchanged fields`() {
        val original = WorkspaceLayout(
            name = "My Layout",
            showDesktopPanel = false,
            openBookmarkIds = setOf("b1", "b2"),
        )
        val copy = original.copy(name = "Renamed")
        assertEquals(original.id, copy.id)
        assertEquals("Renamed", copy.name)
        assertEquals(false, copy.showDesktopPanel)
        assertEquals(setOf("b1", "b2"), copy.openBookmarkIds)
        assertEquals(original.createdAt, copy.createdAt)
    }

    @Test
    fun `WorkspaceLayout default showDesktopPanel is true`() {
        val layout = WorkspaceLayout(name = "Test")
        assertEquals(true, layout.showDesktopPanel)
    }

    @Test
    fun `WorkspaceLayout default openBookmarkIds is empty`() {
        val layout = WorkspaceLayout(name = "Test")
        assertTrue(layout.openBookmarkIds.isEmpty())
    }

    @Test
    fun `WorkspaceLayout equality is structural`() {
        val a = WorkspaceLayout(
            id = "same-id",
            name = "Same Name",
            showDesktopPanel = true,
            openBookmarkIds = setOf("b1"),
            createdAt = 1000L,
        )
        val b = WorkspaceLayout(
            id = "same-id",
            name = "Same Name",
            showDesktopPanel = true,
            openBookmarkIds = setOf("b1"),
            createdAt = 1000L,
        )
        assertEquals(a, b)
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WorkspaceLayoutManagerTest {

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var manager: WorkspaceLayoutManager

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
        manager = WorkspaceLayoutManager(prefs)
    }

    @Test
    fun `loadLayouts returns empty list when no data`() {
        val layouts = manager.loadLayouts()
        assertTrue(layouts.isEmpty())
    }

    @Test
    fun `saveLayouts and loadLayouts round-trip`() {
        val layouts = listOf(
            WorkspaceLayout(
                id = "l1",
                name = "Coding",
                showDesktopPanel = true,
                openBookmarkIds = setOf("b1", "b2"),
                createdAt = 1700000000L,
            ),
            WorkspaceLayout(
                id = "l2",
                name = "Browsing",
                showDesktopPanel = false,
                openBookmarkIds = emptySet(),
                createdAt = 1700001000L,
            ),
        )
        manager.saveLayouts(layouts)
        val loaded = manager.loadLayouts()

        assertEquals(2, loaded.size)
        assertEquals("l1", loaded[0].id)
        assertEquals("Coding", loaded[0].name)
        assertEquals(true, loaded[0].showDesktopPanel)
        assertEquals(setOf("b1", "b2"), loaded[0].openBookmarkIds)
        assertEquals(1700000000L, loaded[0].createdAt)

        assertEquals("l2", loaded[1].id)
        assertEquals("Browsing", loaded[1].name)
        assertEquals(false, loaded[1].showDesktopPanel)
        assertTrue(loaded[1].openBookmarkIds.isEmpty())
        assertEquals(1700001000L, loaded[1].createdAt)
    }

    @Test
    fun `addLayout appends to existing list`() {
        val layout1 = WorkspaceLayout(id = "l1", name = "Layout 1")
        manager.addLayout(layout1)
        assertEquals(1, manager.loadLayouts().size)

        val layout2 = WorkspaceLayout(id = "l2", name = "Layout 2")
        manager.addLayout(layout2)
        val layouts = manager.loadLayouts()
        assertEquals(2, layouts.size)
        assertEquals("l1", layouts[0].id)
        assertEquals("l2", layouts[1].id)
    }

    @Test
    fun `removeLayout removes by id`() {
        manager.saveLayouts(listOf(
            WorkspaceLayout(id = "l1", name = "Layout 1"),
            WorkspaceLayout(id = "l2", name = "Layout 2"),
            WorkspaceLayout(id = "l3", name = "Layout 3"),
        ))
        manager.removeLayout("l2")
        val layouts = manager.loadLayouts()
        assertEquals(2, layouts.size)
        assertEquals("l1", layouts[0].id)
        assertEquals("l3", layouts[1].id)
    }

    @Test
    fun `updateLayout replaces matching layout`() {
        manager.saveLayouts(listOf(
            WorkspaceLayout(id = "l1", name = "Original", showDesktopPanel = true),
            WorkspaceLayout(id = "l2", name = "Other"),
        ))
        val updated = WorkspaceLayout(id = "l1", name = "Updated", showDesktopPanel = false)
        manager.updateLayout(updated)

        val layouts = manager.loadLayouts()
        assertEquals(2, layouts.size)
        assertEquals("Updated", layouts[0].name)
        assertEquals(false, layouts[0].showDesktopPanel)
        // l2 unchanged
        assertEquals("Other", layouts[1].name)
    }

    @Test
    fun `loadLayouts handles corrupted JSON gracefully`() {
        prefs.edit().putString("workspace_layouts_json", "not valid json!!!").apply()
        val layouts = manager.loadLayouts()
        assertTrue(layouts.isEmpty())
    }

    @Test
    fun `saveLayouts with empty list clears data`() {
        manager.addLayout(WorkspaceLayout(id = "l1", name = "Layout 1"))
        assertEquals(1, manager.loadLayouts().size)

        manager.saveLayouts(emptyList())
        assertTrue(manager.loadLayouts().isEmpty())
    }

    @Test
    fun `openBookmarkIds round-trips as a Set`() {
        val ids = setOf("bookmark-alpha", "bookmark-beta", "bookmark-gamma")
        val layout = WorkspaceLayout(id = "l1", name = "With Bookmarks", openBookmarkIds = ids)
        manager.saveLayouts(listOf(layout))

        val loaded = manager.loadLayouts()
        assertEquals(1, loaded.size)
        assertEquals(ids, loaded[0].openBookmarkIds)
    }

    @Test
    fun `createdAt round-trips correctly`() {
        val timestamp = 1700123456789L
        val layout = WorkspaceLayout(id = "l1", name = "Timed", createdAt = timestamp)
        manager.saveLayouts(listOf(layout))

        val loaded = manager.loadLayouts()
        assertEquals(1, loaded.size)
        assertEquals(timestamp, loaded[0].createdAt)
    }
}

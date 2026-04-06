// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.model

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class WorkspaceLayoutStabilityTest {

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var manager: WorkspaceLayoutManager

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
        manager = WorkspaceLayoutManager(prefs)
    }

    @Test
    fun `preset with empty name round-trips correctly`() {
        val layout = WorkspaceLayout(id = "x1", name = "")
        manager.addLayout(layout)
        val loaded = manager.loadLayouts()
        assertEquals("", loaded[0].name)
    }

    @Test
    fun `preset with very long name survives serialization`() {
        val longName = "A".repeat(200)
        val layout = WorkspaceLayout(id = "x1", name = longName)
        manager.addLayout(layout)
        val loaded = manager.loadLayouts()
        assertEquals(longName, loaded[0].name)
    }

    @Test
    fun `preset with double-quote in name is JSON-escaped correctly`() {
        val nameWithQuotes = """He said "hello" to me"""
        val layout = WorkspaceLayout(id = "x1", name = nameWithQuotes)
        manager.addLayout(layout)
        val loaded = manager.loadLayouts()
        assertEquals(nameWithQuotes, loaded[0].name)
    }

    @Test
    fun `preset with backslash in name is JSON-escaped correctly`() {
        val nameWithBackslash = "Path\\to\\something"
        val layout = WorkspaceLayout(id = "x1", name = nameWithBackslash)
        manager.addLayout(layout)
        val loaded = manager.loadLayouts()
        assertEquals(nameWithBackslash, loaded[0].name)
    }

    @Test
    fun `preset with unicode and emoji name round-trips`() {
        val unicodeName = "Layout \uD83D\uDE80 \u65E5\u672C\u8A9E"
        val layout = WorkspaceLayout(id = "x1", name = unicodeName)
        manager.addLayout(layout)
        val loaded = manager.loadLayouts()
        assertEquals(unicodeName, loaded[0].name)
    }

    @Test
    fun `createdAt zero round-trips as zero`() {
        val layout = WorkspaceLayout(id = "x1", name = "test", createdAt = 0L)
        manager.addLayout(layout)
        val loaded = manager.loadLayouts()
        assertEquals(0L, loaded[0].createdAt)
    }

    @Test
    fun `preset with max Long createdAt round-trips`() {
        val layout = WorkspaceLayout(id = "x1", name = "test", createdAt = Long.MAX_VALUE)
        manager.addLayout(layout)
        val loaded = manager.loadLayouts()
        assertEquals(Long.MAX_VALUE, loaded[0].createdAt)
    }

    @Test
    fun `preset with 50 bookmark IDs round-trips correctly`() {
        val ids = (1..50).map { "bookmark-id-$it" }.toSet()
        val layout = WorkspaceLayout(id = "x1", name = "big", openBookmarkIds = ids)
        manager.addLayout(layout)
        val loaded = manager.loadLayouts()
        assertEquals(ids, loaded[0].openBookmarkIds)
    }

    @Test
    fun `duplicate preset names are both saved as separate entries`() {
        manager.addLayout(WorkspaceLayout(id = "a1", name = "Work"))
        manager.addLayout(WorkspaceLayout(id = "a2", name = "Work"))
        val loaded = manager.loadLayouts()
        assertEquals(2, loaded.size)
        assertEquals("a1", loaded[0].id)
        assertEquals("a2", loaded[1].id)
    }

    @Test
    fun `removeLayout with non-existent id is a no-op`() {
        manager.addLayout(WorkspaceLayout(id = "a1", name = "Layout1"))
        manager.removeLayout("non-existent-id")
        assertEquals(1, manager.loadLayouts().size)
    }

    @Test
    fun `updateLayout with non-existent id is a no-op`() {
        manager.addLayout(WorkspaceLayout(id = "a1", name = "Original"))
        manager.updateLayout(WorkspaceLayout(id = "not-exist", name = "Updated"))
        assertEquals("Original", manager.loadLayouts()[0].name)
    }
}

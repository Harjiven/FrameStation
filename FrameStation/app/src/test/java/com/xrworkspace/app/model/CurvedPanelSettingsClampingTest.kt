// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.model

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CurvedPanelSettingsClampingTest {

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var manager: CurvedPanelSettingsManager

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
        manager = CurvedPanelSettingsManager(prefs)
    }

    @Test
    fun `radiusDp below minimum is clamped to 400`() {
        val json = JSONObject().apply {
            put("isEnabled", true)
            put("radiusDp", 0.0)
        }.toString()
        prefs.edit().putString("curved_panel_settings_json", json).apply()

        val loaded = manager.loadCurvedPanelSettings()
        assertTrue("radiusDp should be >= 400, was ${loaded.radiusDp}", loaded.radiusDp >= 400f)
    }

    @Test
    fun `radiusDp above maximum is clamped to 2400`() {
        val json = JSONObject().apply {
            put("isEnabled", false)
            put("radiusDp", 99999.0)
        }.toString()
        prefs.edit().putString("curved_panel_settings_json", json).apply()

        val loaded = manager.loadCurvedPanelSettings()
        assertTrue("radiusDp should be <= 2400, was ${loaded.radiusDp}", loaded.radiusDp <= 2400f)
    }

    @Test
    fun `valid values within range are NOT clamped`() {
        val json = JSONObject().apply {
            put("isEnabled", true)
            put("radiusDp", 800.0)
        }.toString()
        prefs.edit().putString("curved_panel_settings_json", json).apply()

        val loaded = manager.loadCurvedPanelSettings()
        assertEquals(800f, loaded.radiusDp, 0.01f)
        assertTrue(loaded.isEnabled)
    }

    @Test
    fun `normal save and load round-trips without clamping`() {
        val settings = CurvedPanelSettings(isEnabled = true, radiusDp = 1500f)
        manager.saveCurvedPanelSettings(settings)
        val loaded = manager.loadCurvedPanelSettings()
        assertEquals(settings.isEnabled, loaded.isEnabled)
        assertEquals(settings.radiusDp, loaded.radiusDp, 0.01f)
    }
}

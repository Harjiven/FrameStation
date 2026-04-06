// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.model

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [CurvedPanelSettingsManager] JSON serialization round-trip and defaults.
 * Uses [FakeSharedPreferences] from HostConfigManagerTest to avoid Android framework dependency.
 */
class CurvedPanelSettingsManagerTest {

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var manager: CurvedPanelSettingsManager

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
        manager = CurvedPanelSettingsManager(prefs)
    }

    @Test
    fun `loadCurvedPanelSettings returns defaults when no data saved`() {
        val settings = manager.loadCurvedPanelSettings()
        assertFalse(settings.isEnabled)
        assertEquals(825f, settings.radiusDp, 0f)
    }

    @Test
    fun `saveCurvedPanelSettings and loadCurvedPanelSettings round-trip`() {
        val original = CurvedPanelSettings(
            isEnabled = true,
            radiusDp = 800f,
        )
        manager.saveCurvedPanelSettings(original)
        val loaded = manager.loadCurvedPanelSettings()
        assertEquals(original, loaded)
    }

    @Test
    fun `round-trip preserves disabled state`() {
        val original = CurvedPanelSettings(
            isEnabled = false,
            radiusDp = 1500f,
        )
        manager.saveCurvedPanelSettings(original)
        val loaded = manager.loadCurvedPanelSettings()
        assertEquals(original, loaded)
    }

    @Test
    fun `round-trip preserves enabled state`() {
        val original = CurvedPanelSettings(
            isEnabled = true,
            radiusDp = 600f,
        )
        manager.saveCurvedPanelSettings(original)
        val loaded = manager.loadCurvedPanelSettings()
        assertTrue(loaded.isEnabled)
        assertEquals(600f, loaded.radiusDp, 0f)
    }

    @Test
    fun `loadCurvedPanelSettings returns defaults on corrupted JSON`() {
        prefs.edit().putString("curved_panel_settings_json", "not valid json").apply()
        val settings = manager.loadCurvedPanelSettings()
        assertEquals(CurvedPanelSettings(), settings)
    }

    @Test
    fun `loadCurvedPanelSettings returns defaults for empty JSON object`() {
        val json = JSONObject() // empty — all fields missing
        prefs.edit().putString("curved_panel_settings_json", json.toString()).apply()
        val settings = manager.loadCurvedPanelSettings()
        assertFalse(settings.isEnabled)
        assertEquals(825f, settings.radiusDp, 0f)
    }

    @Test
    fun `saveCurvedPanelSettings writes to correct SharedPreferences key`() {
        manager.saveCurvedPanelSettings(CurvedPanelSettings())
        val raw = prefs.getString("curved_panel_settings_json", null)
        assertNotNull("Expected curved_panel_settings_json to be saved", raw)
        val obj = JSONObject(raw!!)
        assertEquals(false, obj.getBoolean("isEnabled"))
        assertEquals(825.0, obj.getDouble("radiusDp"), 0.01)
    }

    @Test
    fun `overwriting settings replaces previous values`() {
        manager.saveCurvedPanelSettings(CurvedPanelSettings(radiusDp = 500f))
        manager.saveCurvedPanelSettings(CurvedPanelSettings(radiusDp = 2000f))
        val loaded = manager.loadCurvedPanelSettings()
        assertEquals(2000f, loaded.radiusDp, 0f)
    }

    @Test
    fun `round-trip preserves various radius values`() {
        listOf(400f, 600f, 825f, 1200f, 1600f).forEach { radius ->
            val settings = CurvedPanelSettings(radiusDp = radius)
            manager.saveCurvedPanelSettings(settings)
            val loaded = manager.loadCurvedPanelSettings()
            assertEquals(radius, loaded.radiusDp, 0.01f)
        }
    }
}

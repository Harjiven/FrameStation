// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.model

import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Persists [CurvedPanelSettings] to SharedPreferences as JSON.
 * Falls back to defaults when no saved data exists or parsing fails.
 */
class CurvedPanelSettingsManager(private val prefs: SharedPreferences) {
    companion object {
        private const val KEY = "curved_panel_settings_json"
    }

    fun loadCurvedPanelSettings(): CurvedPanelSettings {
        val json = prefs.getString(KEY, null) ?: return CurvedPanelSettings()
        return try {
            val obj = JSONObject(json)
            CurvedPanelSettings(
                isEnabled = obj.optBoolean("isEnabled", false),
                radiusDp = obj.optDouble("radiusDp", 825.0).toFloat().coerceIn(400f, 2400f),
            )
        } catch (_: Exception) {
            CurvedPanelSettings()
        }
    }

    fun saveCurvedPanelSettings(settings: CurvedPanelSettings) {
        val obj = JSONObject().apply {
            put("isEnabled", settings.isEnabled)
            put("radiusDp", settings.radiusDp.toDouble())
        }
        prefs.edit().putString(KEY, obj.toString()).apply()
    }
}

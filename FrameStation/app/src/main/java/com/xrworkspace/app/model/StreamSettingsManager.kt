// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.model

import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Persists [StreamSettings] to SharedPreferences as JSON.
 * Falls back to defaults when no saved data exists or parsing fails.
 */
class StreamSettingsManager(private val prefs: SharedPreferences) {
    companion object {
        private const val KEY = "stream_settings_json"
    }

    fun loadStreamSettings(): StreamSettings {
        val json = prefs.getString(KEY, null) ?: return StreamSettings()
        return try {
            val obj = JSONObject(json)
            StreamSettings(
                resolution = obj.optString("resolution", Resolution.RES_1080P.name)
                    .let { name -> Resolution.entries.firstOrNull { it.name == name } ?: Resolution.RES_1080P },
                fps = obj.optInt("fps", 60),
                bitrateKbps = obj.optInt("bitrateKbps", 20000),
                codec = obj.optString("codec", VideoCodec.AUTO.name)
                    .let { name -> VideoCodec.entries.firstOrNull { it.name == name } ?: VideoCodec.AUTO },
            )
        } catch (_: Exception) {
            StreamSettings()
        }
    }

    fun saveStreamSettings(settings: StreamSettings) {
        val obj = JSONObject().apply {
            put("resolution", settings.resolution.name)
            put("fps", settings.fps)
            put("bitrateKbps", settings.bitrateKbps)
            put("codec", settings.codec.name)
        }
        prefs.edit().putString(KEY, obj.toString()).apply()
    }
}

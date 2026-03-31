// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.model

import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Persists [AudioSettings] to SharedPreferences as JSON.
 * Falls back to defaults when no saved data exists or parsing fails.
 * Follows the same pattern as [StreamSettingsManager].
 */
class AudioSettingsManager(private val prefs: SharedPreferences) {
    companion object {
        private const val KEY = "audio_settings_json"
    }

    fun loadAudioSettings(): AudioSettings {
        val json = prefs.getString(KEY, null) ?: return AudioSettings()
        return try {
            val obj = JSONObject(json)
            AudioSettings(
                audioMode = obj.optString("audioMode", AudioMode.STREAM_AUDIO.name)
                    .let { name -> AudioMode.entries.firstOrNull { it.name == name } ?: AudioMode.STREAM_AUDIO },
                audioChannels = obj.optString("audioChannels", AudioChannels.STEREO.name)
                    .let { name -> AudioChannels.entries.firstOrNull { it.name == name } ?: AudioChannels.STEREO },
                enableAudioFx = obj.optBoolean("enableAudioFx", false),
            )
        } catch (_: Exception) {
            AudioSettings()
        }
    }

    fun saveAudioSettings(settings: AudioSettings) {
        val obj = JSONObject().apply {
            put("audioMode", settings.audioMode.name)
            put("audioChannels", settings.audioChannels.name)
            put("enableAudioFx", settings.enableAudioFx)
        }
        prefs.edit().putString(KEY, obj.toString()).apply()
    }
}

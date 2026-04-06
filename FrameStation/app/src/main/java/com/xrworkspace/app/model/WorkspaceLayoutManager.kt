// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.model

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages persistence of workspace layout presets via SharedPreferences JSON.
 * Follows the same pattern as [HostConfigManager].
 */
class WorkspaceLayoutManager(private val prefs: SharedPreferences) {
    companion object {
        private const val KEY = "workspace_layouts_json"
    }

    fun loadLayouts(): List<WorkspaceLayout> {
        val json = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val bookmarkIds = mutableSetOf<String>()
                val idsArr = obj.optJSONArray("openBookmarkIds")
                if (idsArr != null) {
                    for (j in 0 until idsArr.length()) {
                        bookmarkIds.add(idsArr.getString(j))
                    }
                }
                WorkspaceLayout(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    showDesktopPanel = obj.optBoolean("showDesktopPanel", true),
                    openBookmarkIds = bookmarkIds,
                    createdAt = obj.optLong("createdAt", 0L),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveLayouts(layouts: List<WorkspaceLayout>) {
        val arr = JSONArray()
        layouts.forEach { layout ->
            arr.put(JSONObject().apply {
                put("id", layout.id)
                put("name", layout.name)
                put("showDesktopPanel", layout.showDesktopPanel)
                put("openBookmarkIds", JSONArray(layout.openBookmarkIds.toList()))
                put("createdAt", layout.createdAt)
            })
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    fun addLayout(layout: WorkspaceLayout) {
        val layouts = loadLayouts().toMutableList()
        layouts.add(layout)
        saveLayouts(layouts)
    }

    fun removeLayout(id: String) {
        val layouts = loadLayouts().filter { it.id != id }
        saveLayouts(layouts)
    }

    fun updateLayout(layout: WorkspaceLayout) {
        val layouts = loadLayouts().map { if (it.id == layout.id) layout else it }
        saveLayouts(layouts)
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.model

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class BookmarkManager(private val prefs: SharedPreferences) {
    companion object {
        private const val KEY = "bookmarks_json"
    }

    fun loadBookmarks(): List<Bookmark> {
        val json = prefs.getString(KEY, null) ?: return defaultBookmarks()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Bookmark(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    url = obj.getString("url"),
                    icon = obj.optString("icon", "").ifEmpty { null },
                    useDesktopUa = obj.optBoolean("useDesktopUa", false),
                )
            }
        } catch (e: Exception) {
            defaultBookmarks()
        }
    }

    fun saveBookmarks(bookmarks: List<Bookmark>) {
        val arr = JSONArray()
        // Never persist ephemeral tabs — they exist only in memory
        bookmarks.filter { !it.isEphemeral }.forEach { b ->
            arr.put(JSONObject().apply {
                put("id", b.id)
                put("name", b.name)
                put("url", b.url)
                put("icon", b.icon ?: "")
                put("useDesktopUa", b.useDesktopUa)
            })
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private fun defaultBookmarks(): List<Bookmark> = listOf(
        Bookmark(name = "Spotify", url = "https://open.spotify.com", icon = "music"),
        Bookmark(name = "YouTube", url = "https://youtube.com", icon = "video"),
        Bookmark(name = "Google", url = "https://google.com", icon = "search"),
        Bookmark(name = "Discord", url = "https://discord.com/app", icon = "chat"),
    )
}

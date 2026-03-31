// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.xrworkspace.app.model.Bookmark
import com.xrworkspace.app.model.BookmarkManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class WorkspaceUiState(
    val showDesktopPanel: Boolean = true,
    val desktopStreamUrl: String = "",
    val serverAddress: String = "192.168.1.100",
    val isPaired: Boolean = false,
    val showPairing: Boolean = false,
    val isStreaming: Boolean = false,
    val bookmarks: List<Bookmark> = emptyList(),
    val openBookmarkIds: Set<String> = emptySet(),
    val showBookmarkManager: Boolean = false,
)

class WorkspaceViewModel(application: Application) : AndroidViewModel(application) {
    private val sharedPreferences = application.getSharedPreferences("framestation_prefs", Context.MODE_PRIVATE)
    private val defaultServerAddress = "192.168.1.100"
    private val bookmarkManager = BookmarkManager(sharedPreferences)

    private val _uiState: MutableStateFlow<WorkspaceUiState>

    init {
        val bookmarks = bookmarkManager.loadBookmarks()
        val savedOpenIds = sharedPreferences.getStringSet("open_bookmark_ids", emptySet()) ?: emptySet()
        // Only restore IDs that still exist in the bookmark list
        val validOpenIds = savedOpenIds.filter { id -> bookmarks.any { it.id == id } }.toSet()

        _uiState = MutableStateFlow(
            WorkspaceUiState(
                desktopStreamUrl = sharedPreferences.getString("desktop_stream_url", "") ?: "",
                serverAddress = sharedPreferences.getString("server_address", defaultServerAddress) ?: defaultServerAddress,
                showDesktopPanel = sharedPreferences.getBoolean("layout_desktop", true),
                bookmarks = bookmarks,
                openBookmarkIds = validOpenIds,
            )
        )

        // Persist default bookmarks on first run
        if (sharedPreferences.getString("bookmarks_json", null) == null) {
            bookmarkManager.saveBookmarks(bookmarks)
        }
    }

    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    fun toggleDesktopPanel() = _uiState.update {
        if (it.showDesktopPanel) {
            it.copy(showDesktopPanel = false, isStreaming = false)
        } else {
            it.copy(showDesktopPanel = true)
        }
    }

    fun toggleBookmark(id: String) {
        _uiState.update { state ->
            val newIds = if (id in state.openBookmarkIds)
                state.openBookmarkIds - id
            else
                state.openBookmarkIds + id
            state.copy(openBookmarkIds = newIds)
        }
        saveOpenBookmarks()
    }

    fun addBookmark(name: String, url: String) {
        val bookmark = Bookmark(name = name, url = url)
        _uiState.update { state ->
            val newBookmarks = state.bookmarks + bookmark
            state.copy(bookmarks = newBookmarks)
        }
        bookmarkManager.saveBookmarks(_uiState.value.bookmarks)
    }

    fun removeBookmark(id: String) {
        _uiState.update { state ->
            state.copy(
                bookmarks = state.bookmarks.filter { it.id != id },
                openBookmarkIds = state.openBookmarkIds - id,
            )
        }
        bookmarkManager.saveBookmarks(_uiState.value.bookmarks)
        saveOpenBookmarks()
    }

    fun toggleBookmarkManager() {
        _uiState.update { it.copy(showBookmarkManager = !it.showBookmarkManager) }
    }

    fun updateDesktopStreamUrl(url: String) {
        sharedPreferences.edit().putString("desktop_stream_url", url).apply()
        _uiState.update { it.copy(desktopStreamUrl = url) }
    }

    fun updateServerAddress(address: String) {
        sharedPreferences.edit().putString("server_address", address).apply()
        _uiState.update { it.copy(serverAddress = address) }
    }

    fun setIsPaired(paired: Boolean) {
        _uiState.update { it.copy(isPaired = paired) }
    }

    fun togglePairingDialog() {
        _uiState.update { it.copy(showPairing = !it.showPairing) }
    }

    fun setStreamingState(streaming: Boolean) {
        _uiState.update { it.copy(isStreaming = streaming) }
    }

    private fun saveOpenBookmarks() {
        val ids = _uiState.value.openBookmarkIds
        Log.i("WorkspaceVM", "Auto-saving open bookmarks: $ids")
        sharedPreferences.edit().putStringSet("open_bookmark_ids", ids).apply()
    }
}

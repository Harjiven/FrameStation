// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.xr.compose.spatial.ContentEdge
import androidx.xr.compose.spatial.Orbiter
import androidx.xr.compose.spatial.Subspace
import androidx.xr.compose.subspace.MovePolicy
import androidx.xr.compose.subspace.ResizePolicy
import androidx.xr.compose.subspace.SpatialColumn
import androidx.xr.compose.subspace.SpatialPanel
import androidx.xr.compose.subspace.SpatialRow
import androidx.xr.compose.subspace.layout.SubspaceModifier
import androidx.xr.compose.subspace.layout.alpha
import androidx.xr.compose.subspace.layout.height
import androidx.xr.compose.subspace.layout.padding
import androidx.xr.compose.subspace.layout.offset
import androidx.xr.compose.subspace.layout.width
import com.xrworkspace.app.model.Bookmark
import com.xrworkspace.app.ui.components.BookmarkManagerPanel
import com.xrworkspace.app.ui.components.PairingPanel
import com.xrworkspace.app.ui.components.SettingsPanel
import com.xrworkspace.app.ui.components.WorkspaceToolbar
import com.xrworkspace.app.ui.panels.BookmarkWebViewPanel
import com.xrworkspace.app.ui.panels.DesktopStreamPanel
import com.xrworkspace.app.ui.panels.NativeStreamPanel
import com.xrworkspace.app.ui.panels.rememberStreamController
import com.xrworkspace.app.viewmodel.WorkspaceUiState
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun SpatialWorkspace(
    uiState: WorkspaceUiState,
    onToggleDesktop: () -> Unit,
    onToggleBookmark: (String) -> Unit,
    onAddBookmark: (String, String) -> Unit,
    onRemoveBookmark: (String) -> Unit,
    onToggleBookmarkManager: () -> Unit,
    onUpdateStreamUrl: (String) -> Unit,
    onUpdateServerAddress: (String) -> Unit,
    onTogglePairing: () -> Unit,
    onStreamingStateChanged: (Boolean) -> Unit,
    dataDir: File,
) {
    val animatedAlpha = remember { Animatable(0.5f) }
    val showSettings = remember { mutableStateOf(false) }
    // Toggle between native Moonlight streaming and WebView fallback
    val useNativeStreaming = remember { mutableStateOf(true) }
    val streamController = rememberStreamController()

    // Resolve which bookmarks are currently open
    val openBookmarks = remember(uiState.bookmarks, uiState.openBookmarkIds) {
        uiState.bookmarks.filter { it.id in uiState.openBookmarkIds }
    }
    
    LaunchedEffect(Unit) {
        launch {
            animatedAlpha.animateTo(
                1.0f,
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
            )
        }
    }
    
    Subspace {
        // Settings popup — separate SpatialPanel floating in front
        if (showSettings.value) {
            SpatialPanel(
                modifier = SubspaceModifier
                    .width(500.dp)
                    .height(350.dp)
                    .offset(z = 100.dp),
                dragPolicy = MovePolicy(isEnabled = true),
            ) {
                SettingsPanel(
                    currentServerAddress = uiState.serverAddress,
                    onSave = { ip ->
                        onUpdateServerAddress(ip)
                        showSettings.value = false
                    },
                    onDismiss = { showSettings.value = false },
                )
            }
        }

        // Pairing popup — separate SpatialPanel floating in front
        if (uiState.showPairing) {
            SpatialPanel(
                modifier = SubspaceModifier
                    .width(600.dp)
                    .height(500.dp)
                    .offset(z = 100.dp),
                dragPolicy = MovePolicy(isEnabled = true),
            ) {
                PairingPanel(
                    serverAddress = uiState.serverAddress,
                    onConnect = { ip ->
                        // Save the paired IP and dismiss
                        onUpdateServerAddress(ip)
                        onTogglePairing()
                    },
                    onAddressChanged = { ip -> onUpdateServerAddress(ip) },
                    onDismiss = { onTogglePairing() },
                    dataDir = dataDir,
                )
            }
        }

        // Bookmark manager popup — separate SpatialPanel floating in front
        if (uiState.showBookmarkManager) {
            SpatialPanel(
                modifier = SubspaceModifier
                    .width(700.dp)
                    .height(500.dp)
                    .offset(z = 100.dp),
                dragPolicy = MovePolicy(isEnabled = true),
            ) {
                BookmarkManagerPanel(
                    bookmarks = uiState.bookmarks,
                    openBookmarkIds = uiState.openBookmarkIds,
                    onToggleBookmark = onToggleBookmark,
                    onAddBookmark = onAddBookmark,
                    onRemoveBookmark = onRemoveBookmark,
                    onDismiss = onToggleBookmarkManager,
                )
            }
        }

        // Main desktop panel — standalone, not in a SpatialRow
        SpatialPanel(
            modifier = SubspaceModifier
                .alpha(animatedAlpha.value)
                .width(1400.dp)
                .height(900.dp),
            dragPolicy = MovePolicy(isEnabled = true),
            resizePolicy = ResizePolicy(isEnabled = true),
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                if (uiState.showDesktopPanel) {
                    if (useNativeStreaming.value) {
                        NativeStreamPanel(
                            serverAddress = uiState.serverAddress,
                            onStreamingStateChanged = onStreamingStateChanged,
                            streamController = streamController,
                        )
                    } else {
                        DesktopStreamPanel(streamUrl = uiState.desktopStreamUrl)
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No panel selected",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Toolbar orbiter — always visible at bottom of main panel
            Orbiter(
                position = ContentEdge.Bottom,
                offset = 48.dp,
                alignment = Alignment.CenterHorizontally,
            ) {
                WorkspaceToolbar(
                    showDesktop = uiState.showDesktopPanel,
                    openBookmarks = openBookmarks,
                    isStreaming = uiState.isStreaming,
                    onToggleDesktop = onToggleDesktop,
                    onToggleBookmark = onToggleBookmark,
                    onBookmarksClick = onToggleBookmarkManager,
                    onPairingClick = onTogglePairing,
                    onSettingsClick = { showSettings.value = true },
                    onStopStream = { streamController.stopStream() },
                    onShowKeyboard = { streamController.showKeyboard() },
                )
            }
        }

        // Dynamic bookmark panels — each is an independent SpatialPanel with explicit offset
        // Positioned to the right of the main panel, stacked in columns of 2
        openBookmarks.forEachIndexed { index, bookmark ->
            val column = index / 2   // which column (0, 1, 2, ...)
            val row = index % 2      // position within column (0 = top, 1 = bottom)
            SpatialPanel(
                modifier = SubspaceModifier
                    .alpha(animatedAlpha.value)
                    .width(500.dp)
                    .height(430.dp)
                    .offset(
                        x = (750 + column * 520).dp,  // 750dp right of center + 520dp per additional column
                        y = (if (row == 0) 220 else -220).dp, // top half or bottom half
                    ),
                dragPolicy = MovePolicy(isEnabled = true),
                resizePolicy = ResizePolicy(isEnabled = true),
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BookmarkWebViewPanel(
                        bookmark = bookmark,
                        onClose = { onToggleBookmark(bookmark.id) },
                    )
                }
            }
        }
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xrworkspace.app.viewmodel.WorkspaceViewModel

@Composable
fun XRWorkspaceApp(viewModel: WorkspaceViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    SpatialWorkspace(
        uiState = uiState,
        onToggleDesktop = viewModel::toggleDesktopPanel,
        onToggleBookmark = viewModel::toggleBookmark,
        onAddBookmark = viewModel::addBookmark,
        onRemoveBookmark = viewModel::removeBookmark,
        onToggleBookmarkManager = viewModel::toggleBookmarkManager,
        onUpdateStreamUrl = viewModel::updateDesktopStreamUrl,
        onUpdateServerAddress = viewModel::updateServerAddress,
        onTogglePairing = viewModel::togglePairingDialog,
        onStreamingStateChanged = viewModel::setStreamingState,
        dataDir = context.filesDir,
    )
}

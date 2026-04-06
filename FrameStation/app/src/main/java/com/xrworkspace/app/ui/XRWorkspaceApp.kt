// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.xr.compose.platform.LocalSpatialCapabilities
import com.xrworkspace.app.viewmodel.WorkspaceViewModel

@Composable
fun XRWorkspaceApp(viewModel: WorkspaceViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val isSpatialUiEnabled = LocalSpatialCapabilities.current.isSpatialUiEnabled

    if (isSpatialUiEnabled) {
        SpatialWorkspace(
            uiState = uiState,
            onToggleDesktop = viewModel::toggleDesktopPanel,
            onToggleBookmark = viewModel::toggleBookmark,
            onAddBookmark = viewModel::addBookmark,
            onRemoveBookmark = viewModel::removeBookmark,
            onToggleBookmarkManager = viewModel::toggleBookmarkManager,
            onUpdateBookmarkUa = viewModel::updateBookmarkUa,
            onOpenNewTab = viewModel::openNewTab,
            onUpdateStreamUrl = viewModel::updateDesktopStreamUrl,
            onUpdateServerAddress = viewModel::updateServerAddress,
            onTogglePairing = viewModel::togglePairingDialog,
            onStreamingStateChanged = viewModel::setStreamingState,
            onToggleHostManager = viewModel::toggleHostManager,
            onOpenStream = viewModel::openStream,
            onCloseStream = viewModel::closeStream,
            onAddHost = viewModel::addHost,
            onRemoveHost = viewModel::removeHost,
            onSelectHost = viewModel::setActiveHost,
            onUpdateAutoReconnect = viewModel::updateAutoReconnect,
            onToggleDiscovery = viewModel::toggleDiscoveryPanel,
            onStartDiscovery = viewModel::startDiscovery,
            onStopDiscovery = viewModel::stopDiscovery,
            onSelectDiscoveredHost = viewModel::selectDiscoveredHost,
            onUpdateStreamSettings = viewModel::updateStreamSettings,
            onUpdateAudioSettings = viewModel::updateAudioSettings,
            onUpdateMacAddress = viewModel::updateMacAddress,
            onSendWakeOnLan = viewModel::sendWakeOnLan,
            onToggleAppSelector = viewModel::toggleAppSelector,
            onFetchApps = viewModel::fetchApps,
            onSelectApp = viewModel::selectApp,
            onToggleMonitorPicker = viewModel::toggleMonitorPicker,
            onFetchMonitors = viewModel::fetchMonitors,
            onSelectMonitor = viewModel::setActiveMonitor,
            onSunshineCredentialsChanged = viewModel::updateSunshineCredentials,
            onUpdateHostProfile = viewModel::updateHostQualityProfile,
            onUpdateCurvedPanelSettings = viewModel::updateCurvedPanelSettings,
            onToggleLayoutPresets = viewModel::toggleLayoutPresets,
            onSaveLayoutPreset = viewModel::saveLayoutPreset,
            onLoadLayoutPreset = viewModel::loadLayoutPreset,
            onDeleteLayoutPreset = viewModel::deleteLayoutPreset,
            onPresetsClick = viewModel::toggleLayoutPresets,
            dataDir = context.filesDir,
        )
    } else {
        FallbackWorkspace(
            uiState = uiState,
            onUpdateServerAddress = viewModel::updateServerAddress,
        )
    }
}

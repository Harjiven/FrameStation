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
import androidx.xr.compose.subspace.SpatialCurvedRow
import androidx.xr.compose.subspace.SpatialPanel
import androidx.xr.compose.subspace.SpatialRow
import androidx.xr.compose.subspace.layout.SubspaceModifier
import androidx.xr.compose.subspace.layout.alpha
import androidx.xr.compose.subspace.layout.height
import androidx.xr.compose.subspace.layout.offset
import androidx.xr.compose.subspace.layout.padding
import androidx.xr.compose.subspace.layout.width
import com.xrworkspace.app.model.AudioMode
import com.xrworkspace.app.model.AudioSettings
import com.xrworkspace.app.model.Bookmark
import com.xrworkspace.app.model.CurvedPanelSettings
import com.xrworkspace.app.model.HostConfig
import com.xrworkspace.app.model.ServerApp
import com.xrworkspace.app.model.StreamSettings
import com.xrworkspace.app.model.WorkspaceLayout
import com.xrworkspace.app.streaming.DiscoveredHost
import com.xrworkspace.app.ui.components.AboutDialog
import com.xrworkspace.app.ui.components.AppSelectorPanel
import com.xrworkspace.app.ui.components.BookmarkManagerPanel
import com.xrworkspace.app.ui.components.DiscoveryPanel
import com.xrworkspace.app.ui.components.HostManagerPanel
import com.xrworkspace.app.ui.components.LayoutPresetsPanel
import com.xrworkspace.app.ui.components.MonitorPickerPanel
import com.xrworkspace.app.ui.components.PairingPanel
import com.xrworkspace.app.ui.components.SettingsPanel
import com.xrworkspace.app.ui.components.WorkspaceToolbar
import com.xrworkspace.app.ui.panels.BookmarkWebViewPanel
import com.xrworkspace.app.ui.panels.DesktopStreamPanel
import com.xrworkspace.app.ui.panels.NativeStreamPanel
import com.xrworkspace.app.ui.panels.rememberStreamController
import com.xrworkspace.app.viewmodel.WolState
import com.xrworkspace.app.viewmodel.WorkspaceUiState
import java.io.File
import kotlinx.coroutines.launch

// --- Layout constants (dp) ---
private const val STREAM_PANEL_OFFSET_X = -1450   // stream panels arc to the left of main
private const val BOOKMARK_PANEL_OFFSET_X = 980   // bookmark panels arc to the right of main
private const val BOOKMARK_GRID_COL_SPACING = 520 // flat-grid column pitch
private const val BOOKMARK_GRID_ROW_OFFSET = 220  // flat-grid row ±offset from center

@Composable
fun SpatialWorkspace(
    uiState: WorkspaceUiState,
    onToggleDesktop: () -> Unit,
    onToggleBookmark: (String) -> Unit,
    onAddBookmark: (String, String) -> Unit,
    onRemoveBookmark: (String) -> Unit,
    onToggleBookmarkManager: () -> Unit,
    onUpdateBookmarkUa: (id: String, useDesktopUa: Boolean) -> Unit = { _, _ -> },
    onOpenNewTab: () -> Unit = {},
    onUpdateStreamUrl: (String) -> Unit,
    onUpdateServerAddress: (String) -> Unit,
    onTogglePairing: () -> Unit,
    onStreamingStateChanged: (Boolean) -> Unit,
    onToggleHostManager: () -> Unit = {},
    onOpenStream: (String) -> Unit = {},
    onCloseStream: (String) -> Unit = {},
    onAddHost: (String, String) -> Unit = { _, _ -> },
    onRemoveHost: (String) -> Unit = {},
    onSelectHost: (String) -> Unit = {},
    onUpdateAutoReconnect: (Boolean) -> Unit = {},
    onToggleDiscovery: () -> Unit = {},
    onStartDiscovery: () -> Unit = {},
    onStopDiscovery: () -> Unit = {},
    onSelectDiscoveredHost: (DiscoveredHost) -> Unit = {},
    onUpdateStreamSettings: (StreamSettings) -> Unit = {},
    onUpdateAudioSettings: (AudioSettings) -> Unit = {},
    onUpdateMacAddress: (String) -> Unit = {},
    onSendWakeOnLan: () -> Unit = {},
    onToggleAppSelector: () -> Unit = {},
    onFetchApps: () -> Unit = {},
    onSelectApp: (ServerApp) -> Unit = {},
    onToggleMonitorPicker: () -> Unit = {},
    onFetchMonitors: () -> Unit = {},
    onSelectMonitor: (com.xrworkspace.app.model.MonitorInfo) -> Unit = {},
    onSunshineCredentialsChanged: (String, String) -> Unit = { _, _ -> },
    onUpdateHostProfile: (hostId: String, profile: StreamSettings?) -> Unit = { _, _ -> },
    onUpdateCurvedPanelSettings: (CurvedPanelSettings) -> Unit = {},
    onToggleLayoutPresets: () -> Unit = {},
    onSaveLayoutPreset: (String) -> Unit = {},
    onLoadLayoutPreset: (WorkspaceLayout) -> Unit = {},
    onDeleteLayoutPreset: (String) -> Unit = {},
    onPresetsClick: () -> Unit = {},
    dataDir: File,
) {
    val animatedAlpha = remember { Animatable(0.5f) }
    val showSettings = remember { mutableStateOf(false) }
    val showAbout = remember { mutableStateOf(false) }
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
                    .height(700.dp)
                    .offset(z = 100.dp),
                dragPolicy = MovePolicy(isEnabled = true),
            ) {
                SettingsPanel(
                    currentServerAddress = uiState.serverAddress,
                    currentMacAddress = uiState.macAddress,
                    currentStreamSettings = uiState.streamSettings,
                    currentAudioSettings = uiState.audioSettings,
                    currentAutoReconnect = uiState.autoReconnectEnabled,
                    activeHost = uiState.hostConfigs.find { it.id == uiState.activeHostId },
                    currentCurvedPanelSettings = uiState.curvedPanelSettings,
                    onSave = { ip, mac, streamSettings, audioSettings, autoReconnect, curvedPanelSettings ->
                        onUpdateServerAddress(ip)
                        onUpdateMacAddress(mac)
                        onUpdateStreamSettings(streamSettings)
                        onUpdateAudioSettings(audioSettings)
                        onUpdateAutoReconnect(autoReconnect)
                        onUpdateCurvedPanelSettings(curvedPanelSettings)
                        showSettings.value = false
                    },
                    onDismiss = { showSettings.value = false },
                    onShowAbout = { 
                        showSettings.value = false
                        showAbout.value = true
                    },
                )
            }
        }

        // About dialog — separate SpatialPanel floating in front
        if (showAbout.value) {
            SpatialPanel(
                modifier = SubspaceModifier
                    .width(600.dp)
                    .height(800.dp)
                    .offset(z = 200.dp),
                dragPolicy = MovePolicy(isEnabled = true),
            ) {
                AboutDialog(
                    onDismiss = { showAbout.value = false },
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
                    onScanNetwork = onToggleDiscovery,
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
                    bookmarks = uiState.bookmarks.filter { !it.isEphemeral },
                    openBookmarkIds = uiState.openBookmarkIds,
                    onToggleBookmark = onToggleBookmark,
                    onAddBookmark = onAddBookmark,
                    onRemoveBookmark = onRemoveBookmark,
                    onUpdateBookmarkUa = onUpdateBookmarkUa,
                    onNewTab = onOpenNewTab,
                    onDismiss = onToggleBookmarkManager,
                )
            }
        }

        // Host manager popup — separate SpatialPanel floating in front
        if (uiState.showHostManager) {
            SpatialPanel(
                modifier = SubspaceModifier
                    .width(750.dp)
                    .height(500.dp)
                    .offset(z = 100.dp),
                dragPolicy = MovePolicy(isEnabled = true),
            ) {
                HostManagerPanel(
                    hostConfigs = uiState.hostConfigs,
                    activeHostId = uiState.activeHostId,
                    activeStreamHostIds = uiState.activeStreamHostIds,
                    onSelectHost = onSelectHost,
                    onStreamHost = onOpenStream,
                    onStopStreamHost = onCloseStream,
                    onAddHost = onAddHost,
                    onRemoveHost = onRemoveHost,
                    onDismiss = onToggleHostManager,
                    onUpdateHostProfile = onUpdateHostProfile,
                )
            }
        }

        // Discovery popup — separate SpatialPanel floating in front
        if (uiState.showDiscovery) {
            SpatialPanel(
                modifier = SubspaceModifier
                    .width(600.dp)
                    .height(450.dp)
                    .offset(z = 100.dp),
                dragPolicy = MovePolicy(isEnabled = true),
            ) {
                DiscoveryPanel(
                    discoveredHosts = uiState.discoveredHosts,
                    isScanning = uiState.isScanning,
                    discoveryError = uiState.discoveryError,
                    onSelectHost = onSelectDiscoveredHost,
                    onStartScan = onStartDiscovery,
                    onStopScan = onStopDiscovery,
                    onRefresh = {
                        onStopDiscovery()
                        onStartDiscovery()
                    },
                    onDismiss = onToggleDiscovery,
                )
            }
        }

        // App selector popup — separate SpatialPanel floating in front
        if (uiState.showAppSelector) {
            SpatialPanel(
                modifier = SubspaceModifier
                    .width(600.dp)
                    .height(500.dp)
                    .offset(z = 100.dp),
                dragPolicy = MovePolicy(isEnabled = true),
            ) {
                AppSelectorPanel(
                    apps = uiState.availableApps,
                    selectedApp = uiState.selectedApp,
                    isLoading = uiState.isLoadingApps,
                    error = uiState.appListError,
                    onSelectApp = onSelectApp,
                    onRefresh = onFetchApps,
                    onDismiss = onToggleAppSelector,
                )
            }
        }

        // Monitor picker popup — separate SpatialPanel floating in front
        if (uiState.showMonitorPicker) {
            SpatialPanel(
                modifier = SubspaceModifier
                    .width(560.dp)
                    .height(520.dp)
                    .offset(z = 100.dp),
                dragPolicy = MovePolicy(isEnabled = true),
            ) {
                MonitorPickerPanel(
                    monitors = uiState.monitors,
                    isLoading = uiState.isLoadingMonitors,
                    error = uiState.monitorError,
                    sunshineUsername = uiState.sunshineUsername,
                    sunshinePassword = uiState.sunshinePassword,
                    onCredentialsChanged = onSunshineCredentialsChanged,
                    onFetchMonitors = onFetchMonitors,
                    onSelectMonitor = onSelectMonitor,
                    onDismiss = onToggleMonitorPicker,
                )
            }
        }

        // Layout presets popup — separate SpatialPanel floating in front
        if (uiState.showLayoutPresets) {
            SpatialPanel(
                modifier = SubspaceModifier
                    .width(600.dp)
                    .height(500.dp)
                    .offset(z = 100.dp),
                dragPolicy = MovePolicy(isEnabled = true),
            ) {
                LayoutPresetsPanel(
                    layouts = uiState.layoutPresets,
                    onLoadPreset = onLoadLayoutPreset,
                    onDeletePreset = onDeleteLayoutPreset,
                    onSavePreset = onSaveLayoutPreset,
                    onDismiss = onToggleLayoutPresets,
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
                            streamSettings = uiState.streamSettings,
                            audioSettings = uiState.audioSettings,
                            autoReconnectEnabled = uiState.autoReconnectEnabled,
                            selectedAppId = uiState.selectedApp?.appId,
                            selectedAppName = uiState.selectedApp?.appName ?: "Desktop",
                            onAppSelectorClick = onToggleAppSelector,
                            hasMacAddress = uiState.macAddress.isNotBlank(),
                            wolState = uiState.wolState,
                            onWakeClick = onSendWakeOnLan,
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
                    onHostsClick = onToggleHostManager,
                    onDiscoverClick = onToggleDiscovery,
                    isDiscoveryActive = uiState.showDiscovery || uiState.isScanning,
                    activeHostName = uiState.hostConfigs.find { it.id == uiState.activeHostId }?.name,
                    hasMacAddress = uiState.macAddress.isNotBlank(),
                    wolState = uiState.wolState,
                    onWakeClick = onSendWakeOnLan,
                    audioSettings = uiState.audioSettings,
                    onToggleMute = {
                        val current = uiState.audioSettings
                        val toggled = if (current.audioMode == AudioMode.MUTED) {
                            current.copy(audioMode = AudioMode.STREAM_AUDIO)
                        } else {
                            current.copy(audioMode = AudioMode.MUTED)
                        }
                        onUpdateAudioSettings(toggled)
                    },
                    onStopStream = { streamController.stopStream() },
                    onShowKeyboard = { streamController.showKeyboard() },
                    onSwitchMonitor = onToggleMonitorPicker,
                    onPresetsClick = onPresetsClick,
                )
            }
        }

        // Active stream panels — one SpatialPanel per streaming host.
        // NOTE: currently limited to 1 simultaneous stream due to MoonBridge static JNI state.
        // The multi-stream (forEach) branch is preserved for future use once MoonBridge is
        // refactored to be instance-aware.
        val activeStreamHosts = uiState.hostConfigs.filter { it.id in uiState.activeStreamHostIds }
        if (activeStreamHosts.isNotEmpty()) {
            // Each stream panel needs its own StreamController for toolbar integration.
            val streamPanelControllers = remember(activeStreamHosts.map { it.id }) {
                activeStreamHosts.associate { host -> host.id to StreamController() }
            }
            if (activeStreamHosts.size == 1) {
                // Single stream: flat offset to left of main panel
                val host = activeStreamHosts.first()
                val hostController = streamPanelControllers[host.id] ?: StreamController()
                SpatialPanel(
                    modifier = SubspaceModifier
                        .width(1400.dp)
                        .height(900.dp)
                        .offset(x = STREAM_PANEL_OFFSET_X.dp),
                    dragPolicy = MovePolicy(isEnabled = true),
                    resizePolicy = ResizePolicy(isEnabled = true),
                ) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        NativeStreamPanel(
                            serverAddress = host.address,
                            streamSettings = host.qualityProfile ?: uiState.streamSettings,
                            audioSettings = uiState.audioSettings,
                            autoReconnectEnabled = uiState.autoReconnectEnabled,
                            onStreamingStateChanged = {},
                            streamController = hostController,
                        )
                    }
                }
            } else {
                // Multiple streams: arc in SpatialCurvedRow to the left of main panel
                SpatialCurvedRow(
                    modifier = SubspaceModifier.offset(x = STREAM_PANEL_OFFSET_X.dp),
                    curveRadius = uiState.curvedPanelSettings.radiusDp.dp,
                ) {
                    activeStreamHosts.forEach { host ->
                        val hostController = streamPanelControllers[host.id] ?: StreamController()
                        SpatialPanel(
                            modifier = SubspaceModifier
                                .width(1200.dp)
                                .height(750.dp),
                            dragPolicy = MovePolicy(isEnabled = true),
                            resizePolicy = ResizePolicy(isEnabled = true),
                        ) {
                            Surface(modifier = Modifier.fillMaxSize()) {
                                NativeStreamPanel(
                                    serverAddress = host.address,
                                    streamSettings = host.qualityProfile ?: uiState.streamSettings,
                                    audioSettings = uiState.audioSettings,
                                    autoReconnectEnabled = uiState.autoReconnectEnabled,
                                    onStreamingStateChanged = {},
                                    streamController = hostController,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Dynamic bookmark panels — flat grid or native cylindrical arc via SpatialCurvedRow
        val curvedSettings = uiState.curvedPanelSettings
        if (curvedSettings.isEnabled && openBookmarks.size > 1) {
            // Native curved arc: SpatialCurvedRow handles all arc math and panel rotation
            SpatialCurvedRow(
                modifier = SubspaceModifier.offset(x = BOOKMARK_PANEL_OFFSET_X.dp),
                curveRadius = curvedSettings.radiusDp.dp,
            ) {
                openBookmarks.forEach { bookmark ->
                    SpatialPanel(
                        modifier = SubspaceModifier
                            .alpha(animatedAlpha.value)
                            .width(500.dp)
                            .height(430.dp),
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
        } else {
            // Flat grid: columns of 2 panels
            openBookmarks.forEachIndexed { index, bookmark ->
                val column = index / 2
                val row = index % 2
                val xOffsetDp = (BOOKMARK_PANEL_OFFSET_X + column * BOOKMARK_GRID_COL_SPACING).toFloat()
                val yOffsetDp = (if (row == 0) BOOKMARK_GRID_ROW_OFFSET else -BOOKMARK_GRID_ROW_OFFSET).toFloat()

                SpatialPanel(
                    modifier = SubspaceModifier
                        .alpha(animatedAlpha.value)
                        .width(500.dp)
                        .height(430.dp)
                        .offset(
                            x = xOffsetDp.dp,
                            y = yOffsetDp.dp,
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
}

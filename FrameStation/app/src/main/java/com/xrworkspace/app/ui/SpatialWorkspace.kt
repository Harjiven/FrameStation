// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.ui

import android.util.Log
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableFloatStateOf
import androidx.xr.compose.platform.LocalSession
import androidx.xr.compose.platform.LocalSpatialCapabilities
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
import com.xrworkspace.app.streaming.StreamServiceConnection
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
import com.xrworkspace.app.ui.panels.StreamVideoSurface
import com.xrworkspace.app.ui.panels.rememberStreamController
import com.xrworkspace.app.ui.panels.StreamController
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
    onGetStreamSlot: (hostId: String) -> StreamServiceConnection? = { null },
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
    onTogglePassthrough: () -> Unit = {},
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
                targetValue = 1f,
                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
            )
        }
    }

    // Passthrough control — apply opacity to XR environment when state changes
    val xrSession = LocalSession.current
    val isPassthroughSupported = LocalSpatialCapabilities.current.isPassthroughControlEnabled
    LaunchedEffect(uiState.isPassthroughActive) {
        // Note: SpatialEnvironment passthrough API access depends on scenecore version.
        // The preferred opacity will be applied when the correct API path is confirmed.
        Log.d("SpatialWorkspace", "Passthrough requested: ${uiState.isPassthroughActive}")
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

        // Video surface + toolbar — wrapped in a SpatialColumn so they layout vertically
        // and feel like a single workspace. The video panel sits at top, toolbar at bottom.
        // The user drags the column as a unit (movable on the column modifier), or grabs
        // the video panel directly (which has its own MovePolicy).
        var mainPanelWidthDp by remember { mutableFloatStateOf(1400f) }
        var mainPanelHeightDp by remember { mutableFloatStateOf(900f) }
        // Shared surface state — StreamVideoSurface creates it, NativeStreamPanel uses it
        var mainSurfaceRef by remember { mutableStateOf<android.view.Surface?>(null) }
        // StreamVideoSurface — rendered OUTSIDE the SpatialColumn so it doesn't take a
        // vertical slot. Positioned to overlay the same area as the main UI panel inside
        // the column. Always rendered at full size so the Surface exists for startStream().
        // When NOT streaming, sits at z=-1dp behind the main UI panel. When streaming, the
        // main UI panel hides via the column conditional and the video becomes the main
        // visible content in that slot.
        if (uiState.showDesktopPanel && useNativeStreaming.value) {
            StreamVideoSurface(
                streamManager = null,
                streamServiceConnection = null,
                isConnected = uiState.isStreaming,
                panelWidthDp = mainPanelWidthDp,
                panelHeightDp = mainPanelHeightDp,
                // Align with the main UI panel slot inside SpatialColumn.
                // Column = [mainPanel(900) + toolbar(140)] = 1040dp centered at y=0.
                // Main panel center sits at y = +70 (toolbar takes 140 below).
                offsetYDp = 70f,
                onSurfaceCreated = { surface ->
                    Log.i("SpatialWorkspace", "Video surface created")
                    mainSurfaceRef = surface
                },
                onSurfaceDestroyed = {
                    Log.i("SpatialWorkspace", "Video surface destroyed")
                    mainSurfaceRef = null
                },
            )
        }

        // Workspace column — wraps the main UI panel + toolbar so they layout vertically
        // and feel like a single workspace. When streaming, the main UI panel hides and
        // only the toolbar remains in the column.
        SpatialColumn(
            modifier = SubspaceModifier.alpha(animatedAlpha.value),
        ) {

            // Main desktop UI overlay panel — only shown when NOT streaming.
            // NOTE: NativeStreamPanel's DisposableEffect.onDispose used to kill the stream
            // when this panel was removed from composition. We patched it to only stop on
            // explicit user action, so this conditional removal is now safe.
            if (!uiState.isStreaming) {
                SpatialPanel(
                    modifier = SubspaceModifier
                        .width(1400.dp)
                        .height(900.dp),
                    dragPolicy = MovePolicy(isEnabled = false),
                    resizePolicy = ResizePolicy(isEnabled = true),
                ) {
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
                                panelWidthDp = mainPanelWidthDp,
                                panelHeightDp = mainPanelHeightDp,
                                externalSurfaceRef = mainSurfaceRef,
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
            }

            // Toolbar — sized with extra room around the content for a comfortable
            // grab/drag border. The actual WorkspaceToolbar Surface is centered inside.
            SpatialPanel(
                modifier = SubspaceModifier
                    .width(900.dp)
                    .height(140.dp),
                dragPolicy = MovePolicy(isEnabled = true),
                resizePolicy = ResizePolicy(isEnabled = false),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
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
                        isPassthroughActive = uiState.isPassthroughActive,
                        isPassthroughSupported = isPassthroughSupported,
                        onTogglePassthrough = onTogglePassthrough,
                    )
                }
            }
        }

        // Active stream panels — one SpatialPanel per streaming host (up to 3).
        // Each stream runs in its own isolated process (:stream0/:stream1/:stream2),
        // giving each an independent copy of libmoonlight-core.so.
        val activeStreamHosts = uiState.hostConfigs.filter { it.id in uiState.activeStreamHostIds }
        if (activeStreamHosts.isNotEmpty()) {
            // Each stream panel needs its own StreamController for toolbar integration.
            // Key on the stable Set reference, not a newly-allocated List, to avoid
            // recreating StreamControllers on every recomposition.
            val streamPanelControllers = remember(uiState.activeStreamHostIds) {
                activeStreamHosts.associate { host -> host.id to StreamController() }
            }
            if (activeStreamHosts.size == 1) {
                // Single stream: flat offset to left of main panel
                val host = activeStreamHosts.first()
                val hostController = streamPanelControllers[host.id] ?: StreamController()
                var streamPanelWidthDp by remember { mutableFloatStateOf(1400f) }
                var streamPanelHeightDp by remember { mutableFloatStateOf(900f) }
                SpatialPanel(
                    modifier = SubspaceModifier
                        .width(1400.dp)
                        .height(900.dp)
                        .offset(x = STREAM_PANEL_OFFSET_X.dp),
                    dragPolicy = MovePolicy(isEnabled = true),
                    resizePolicy = ResizePolicy(isEnabled = true),
                ) {
                    NativeStreamPanel(
                        serverAddress = host.address,
                        streamSettings = host.qualityProfile ?: uiState.streamSettings,
                        audioSettings = uiState.audioSettings,
                        autoReconnectEnabled = uiState.autoReconnectEnabled,
                        onStreamingStateChanged = {},
                        streamController = hostController,
                        streamServiceConnection = onGetStreamSlot(host.id),
                        panelWidthDp = streamPanelWidthDp,
                        panelHeightDp = streamPanelHeightDp,
                    )
                }
            } else {
                // Multiple streams: arc in SpatialCurvedRow to the left of main panel
                SpatialCurvedRow(
                    modifier = SubspaceModifier.offset(x = STREAM_PANEL_OFFSET_X.dp),
                    curveRadius = uiState.curvedPanelSettings.radiusDp.dp,
                ) {
                    activeStreamHosts.forEach { host ->
                        val hostController = streamPanelControllers[host.id] ?: StreamController()
                        var arcPanelWidthDp by remember(host.id) { mutableFloatStateOf(1200f) }
                        var arcPanelHeightDp by remember(host.id) { mutableFloatStateOf(750f) }
                        SpatialPanel(
                            modifier = SubspaceModifier
                                .width(1200.dp)
                                .height(750.dp),
                            dragPolicy = MovePolicy(isEnabled = true),
                            resizePolicy = ResizePolicy(isEnabled = true),
                        ) {
                            NativeStreamPanel(
                                serverAddress = host.address,
                                streamSettings = host.qualityProfile ?: uiState.streamSettings,
                                audioSettings = uiState.audioSettings,
                                autoReconnectEnabled = uiState.autoReconnectEnabled,
                                onStreamingStateChanged = {},
                                streamController = hostController,
                                streamServiceConnection = onGetStreamSlot(host.id),
                                panelWidthDp = arcPanelWidthDp,
                                panelHeightDp = arcPanelHeightDp,
                            )
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

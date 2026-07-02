// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xrworkspace.app.model.AudioSettings
import com.xrworkspace.app.model.AudioSettingsManager
import com.xrworkspace.app.model.Bookmark
import com.xrworkspace.app.model.BookmarkManager
import com.xrworkspace.app.model.CurvedPanelSettings
import com.xrworkspace.app.model.CurvedPanelSettingsManager
import com.xrworkspace.app.model.HostConfig
import com.xrworkspace.app.model.HostConfigManager
import com.xrworkspace.app.model.MonitorInfo
import com.xrworkspace.app.model.ServerApp
import com.xrworkspace.app.model.StreamSettings
import com.xrworkspace.app.model.StreamSettingsManager
import com.xrworkspace.app.model.WorkspaceLayout
import com.xrworkspace.app.model.WorkspaceLayoutManager
import com.xrworkspace.app.streaming.DiscoveredHost
import com.xrworkspace.app.streaming.DiscoveryManager
import com.xrworkspace.app.streaming.MainStreamSession
import com.xrworkspace.app.streaming.ServerManager
import com.xrworkspace.app.streaming.StreamSessionState
import com.xrworkspace.app.streaming.StreamService0
import com.xrworkspace.app.streaming.StreamService1
import com.xrworkspace.app.streaming.StreamService2
import com.xrworkspace.app.streaming.StreamServiceConnection
import com.xrworkspace.app.streaming.SunshineApiManager
import com.xrworkspace.app.streaming.WolManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * State of a Wake-on-LAN request lifecycle.
 */
enum class WolState {
    /** No WoL action in progress. */
    Idle,
    /** Magic packet is being sent. */
    Sending,
    /** Magic packet was sent successfully (fire-and-forget). */
    Sent,
    /** Failed to send the magic packet. */
    Failed,
}

data class WorkspaceUiState(
    val showDesktopPanel: Boolean = true,
    val desktopStreamUrl: String = "",
    val serverAddress: String = "192.168.1.100", // mirrors WorkspaceViewModel.DEFAULT_SERVER_ADDRESS
    val isPaired: Boolean = false,
    val showPairing: Boolean = false,
    val isStreaming: Boolean = false,
    /** Main desktop panel stream lifecycle state (owned by MainStreamSession). */
    val mainStream: StreamSessionState = StreamSessionState(),
    val bookmarks: List<Bookmark> = emptyList(),
    val openBookmarkIds: Set<String> = emptySet(),
    val showBookmarkManager: Boolean = false,
    val streamSettings: StreamSettings = StreamSettings(),
    val hostConfigs: List<HostConfig> = emptyList(),
    val activeHostId: String? = null,
    /** Host IDs that currently have an open stream panel. */
    val activeStreamHostIds: Set<String> = emptySet(),
    val showHostManager: Boolean = false,
    val discoveredHosts: List<DiscoveredHost> = emptyList(),
    val isScanning: Boolean = false,
    val discoveryError: String? = null,
    val availableApps: List<ServerApp> = emptyList(),
    val selectedApp: ServerApp? = null,
    val showAppSelector: Boolean = false,
    val isLoadingApps: Boolean = false,
    val appListError: String? = null,
    val autoReconnectEnabled: Boolean = true,
    val audioSettings: AudioSettings = AudioSettings(),
    val macAddress: String = "",
    val wolState: WolState = WolState.Idle,
    val wolError: String? = null,
    // Monitor picker
    val showMonitorPicker: Boolean = false,
    val monitors: List<MonitorInfo> = emptyList(),
    val isLoadingMonitors: Boolean = false,
    val monitorError: String? = null,
    val sunshineUsername: String = "",
    val sunshinePassword: String = "",
    // Curved panel rendering
    val curvedPanelSettings: CurvedPanelSettings = CurvedPanelSettings(),
    // Workspace layout presets
    val layoutPresets: List<WorkspaceLayout> = emptyList(),
    val showLayoutPresets: Boolean = false,
    /** Whether the headset is currently in passthrough (see-through) mode. */
    val isPassthroughActive: Boolean = false,
)

class WorkspaceViewModel(application: Application) : AndroidViewModel(application) {
    private val sharedPreferences = application.getSharedPreferences("framestation_prefs", Context.MODE_PRIVATE)
    private val bookmarkManager = BookmarkManager(sharedPreferences)
    private val streamSettingsManager = StreamSettingsManager(sharedPreferences)
    private val audioSettingsManager = AudioSettingsManager(sharedPreferences)
    private val hostConfigManager = HostConfigManager(sharedPreferences)
    private val curvedPanelSettingsManager = CurvedPanelSettingsManager(sharedPreferences)
    private val workspaceLayoutManager = WorkspaceLayoutManager(sharedPreferences)

    private val discoveryManager = DiscoveryManager(application)
    private val wolManager = WolManager()

    /**
     * Owns the main desktop panel's stream outside the composition so toolbar controls
     * (Stop/Mute/Keyboard) survive the panel leaving composition when streaming starts.
     */
    private val mainStreamSession = MainStreamSession(
        context = application,
        prefs = sharedPreferences,
        scope = viewModelScope,
        onStreamingChanged = { streaming -> setStreamingState(streaming) },
    )

    // --- Process-isolated stream service slots ---
    // Each slot runs in its own Android process (:stream0, :stream1, :stream2), giving each
    // an independent copy of libmoonlight-core.so with separate C globals.
    private val streamSlots = linkedMapOf(
        ":stream0" to StreamServiceConnection(application, ":stream0", StreamService0::class.java),
        ":stream1" to StreamServiceConnection(application, ":stream1", StreamService1::class.java),
        ":stream2" to StreamServiceConnection(application, ":stream2", StreamService2::class.java),
    )
    /** Maps hostId → processName for currently active streams. Thread-safe for Binder callbacks. */
    private val hostToSlot = java.util.concurrent.ConcurrentHashMap<String, String>()

    private val _uiState: MutableStateFlow<WorkspaceUiState>

    init {
        val bookmarks = bookmarkManager.loadBookmarks()
        val savedOpenIds = sharedPreferences.getStringSet("open_bookmark_ids", emptySet()) ?: emptySet()
        // Only restore IDs that still exist in the bookmark list
        val validOpenIds = savedOpenIds.filter { id -> bookmarks.any { it.id == id } }.toSet()
        val streamSettings = streamSettingsManager.loadStreamSettings()
        val audioSettings = audioSettingsManager.loadAudioSettings()
        val curvedPanelSettings = curvedPanelSettingsManager.loadCurvedPanelSettings()
        val layoutPresets = workspaceLayoutManager.loadLayouts()

        // Migrate single-server config to multi-host on first run
        var hostConfigs = hostConfigManager.loadHosts()
        if (hostConfigs.isEmpty()) {
            hostConfigs = hostConfigManager.migrateFromSingleServer()
        }
        val activeHostId = hostConfigManager.getActiveHostId()
        val activeHost = hostConfigs.find { it.id == activeHostId }
        val serverAddress = activeHost?.address
            ?: sharedPreferences.getString("server_address", DEFAULT_SERVER_ADDRESS)
            ?: DEFAULT_SERVER_ADDRESS

        // Load MAC address: prefer active host config, fall back to standalone pref
        val macAddress = activeHost?.macAddress
            ?: sharedPreferences.getString("server_mac_address", "") ?: ""

        val autoReconnect = sharedPreferences.getBoolean("auto_reconnect_enabled", true)
        val sunshineUsername = sharedPreferences.getString("sunshine_username", "") ?: ""
        val sunshinePassword = sharedPreferences.getString("sunshine_password", "") ?: ""

        _uiState = MutableStateFlow(
            WorkspaceUiState(
                desktopStreamUrl = sharedPreferences.getString("desktop_stream_url", "") ?: "",
                serverAddress = serverAddress,
                showDesktopPanel = sharedPreferences.getBoolean("layout_desktop", true),
                bookmarks = bookmarks,
                openBookmarkIds = validOpenIds,
                streamSettings = streamSettings,
                audioSettings = audioSettings,
                hostConfigs = hostConfigs,
                activeHostId = activeHostId,
                macAddress = macAddress,
                autoReconnectEnabled = autoReconnect,
                sunshineUsername = sunshineUsername,
                sunshinePassword = sunshinePassword,
                curvedPanelSettings = curvedPanelSettings,
                layoutPresets = layoutPresets,
                isPassthroughActive = sharedPreferences.getBoolean("passthrough_active", false),
            )
        )

        // Persist default bookmarks on first run
        if (sharedPreferences.getString("bookmarks_json", null) == null) {
            bookmarkManager.saveBookmarks(bookmarks)
        }

        // Collect discovery flows and mirror into UI state.
        // Each collector is individually guarded: an exception in one must not
        // silently kill the others or leave the ViewModel in a broken state.
        viewModelScope.launch {
            try {
                discoveryManager.discoveredHosts.collect { hosts ->
                    _uiState.update { it.copy(discoveredHosts = hosts) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "discoveredHosts collector failed", e)
            }
        }
        viewModelScope.launch {
            try {
                discoveryManager.isScanning.collect { scanning ->
                    _uiState.update { it.copy(isScanning = scanning) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "isScanning collector failed", e)
            }
        }
        viewModelScope.launch {
            try {
                discoveryManager.discoveryError.collect { error ->
                    _uiState.update { it.copy(discoveryError = error) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "discoveryError collector failed", e)
            }
        }

        // Mirror the main stream session's lifecycle state into UI state.
        viewModelScope.launch {
            try {
                mainStreamSession.state.collect { s -> _uiState.update { it.copy(mainStream = s) } }
            } catch (e: Exception) {
                Log.e(TAG, "mainStream state collector failed", e)
            }
        }

        // Auto-start discovery briefly on launch (10 seconds) to populate host list.
        viewModelScope.launch {
            try {
                discoveryManager.startDiscovery()
                delay(10_000L)
                // Only stop if the user hasn't opened the Connect (pairing) panel
                if (!_uiState.value.showPairing) {
                    discoveryManager.stopDiscovery()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Auto-discovery startup failed", e)
            }
        }

        // Pre-bind all stream service slots so they're ready when the user taps Stream
        streamSlots.values.forEach { slot ->
            slot.onServiceDied = {
                // If a service process crashes, remove its host from active streams
                val affectedHost = hostToSlot.entries.find { it.value == slot.processName }?.key
                if (affectedHost != null) {
                    hostToSlot.remove(affectedHost)
                    _uiState.update { state ->
                        state.copy(activeStreamHostIds = state.activeStreamHostIds - affectedHost)
                    }
                }
            }
            try {
                slot.bind()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to pre-bind stream slot ${slot.processName}", e)
                // Non-fatal: the slot will be unavailable; openStream() will skip it
            }
        }
    }

    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    fun toggleDesktopPanel() {
        // If we're hiding the panel while a stream is active, actually stop the stream.
        // (Fixes the A1 leak where isStreaming was flipped false without stopping the connection.)
        if (_uiState.value.showDesktopPanel) {
            mainStreamSession.onDesktopPanelHidden()
        }
        _uiState.update {
            if (it.showDesktopPanel) {
                it.copy(showDesktopPanel = false, isStreaming = false)
            } else {
                it.copy(showDesktopPanel = true)
            }
        }
    }

    // --- Main desktop panel stream (owned by MainStreamSession) ---

    /** Provide the video Surface (from StreamVideoSurface) to the main stream session. */
    fun setMainStreamSurface(surface: android.view.Surface?) = mainStreamSession.setSurface(surface)

    /** Start the main desktop panel stream to the active host. */
    fun startMainStream() {
        val state = _uiState.value
        val activeHost = state.hostConfigs.find { it.id == state.activeHostId }
        mainStreamSession.configure(
            serverAddress = state.serverAddress,
            // Per-host cert wiring is Task 1.3; ServerManager still loads the legacy cert from disk.
            serverCert = null,
            appId = state.selectedApp?.appId,
            streamSettings = activeHost?.qualityProfile ?: state.streamSettings,
            audioSettings = state.audioSettings,
            autoReconnectEnabled = state.autoReconnectEnabled,
        )
        mainStreamSession.start()
    }

    /** Stop the main desktop panel stream (toolbar Stop button). */
    fun stopMainStream() = mainStreamSession.stop()

    /** Toggle mute on the active main stream (toolbar mute chip). */
    fun setMainStreamMuted(muted: Boolean) = mainStreamSession.setMuted(muted)

    /** Send typed text to the main stream (typing bar). */
    fun sendMainStreamText(text: String) = mainStreamSession.sendUtf8Text(text)

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

    /**
     * Open a new ephemeral browser tab. Ephemeral tabs appear as panels but are
     * never saved to the bookmark list. They start on an empty page so the user
     * can type a URL in the panel's URL bar.
     */
    fun openNewTab() {
        val tab = Bookmark(
            name = "New Tab",
            url = "about:blank",
            isEphemeral = true,
        )
        _uiState.update { state ->
            state.copy(
                bookmarks = state.bookmarks + tab,
                openBookmarkIds = state.openBookmarkIds + tab.id,
            )
        }
        // Do NOT save — ephemeral tab is filtered out by BookmarkManager
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

    fun updateBookmarkUa(id: String, useDesktopUa: Boolean) {
        _uiState.update { state ->
            state.copy(
                bookmarks = state.bookmarks.map {
                    if (it.id == id) it.copy(useDesktopUa = useDesktopUa) else it
                },
            )
        }
        bookmarkManager.saveBookmarks(_uiState.value.bookmarks)
    }

    /**
     * Returns a [WorkspaceUiState] with all popup menu panels closed.
     * Used by every `toggle*` panel function so opening one menu auto-closes any other.
     * Does NOT touch `showDesktopPanel` (that's the main streaming panel, not a popup).
     *
     * NOTE: This is pure state. Callers should also call [stopPanelSideEffects] to release
     * resources owned by panels that are being closed (e.g., mDNS discovery).
     */
    private fun WorkspaceUiState.withAllPanelsClosed(): WorkspaceUiState = copy(
        showPairing = false,
        showBookmarkManager = false,
        showHostManager = false,
        showAppSelector = false,
        showMonitorPicker = false,
        showLayoutPresets = false,
    )

    /**
     * Stop side effects (mDNS discovery, etc.) owned by panels that are about to close.
     * Called from every `toggle*` function and from [closeAllPanels] before opening a new panel.
     */
    private fun stopPanelSideEffectsBeforeClose() {
        val state = _uiState.value
        if (state.showPairing) {
            try { discoveryManager.stopDiscovery() } catch (e: Exception) {
                Log.w(TAG, "stopDiscovery failed during panel close", e)
            }
        }
    }

    /** Public method for callers (e.g. Settings dialog) to close all panels. */
    fun closeAllPanels() {
        stopPanelSideEffectsBeforeClose()
        _uiState.update { it.withAllPanelsClosed() }
    }

    fun toggleBookmarkManager() {
        stopPanelSideEffectsBeforeClose()
        _uiState.update {
            val newValue = !it.showBookmarkManager
            it.withAllPanelsClosed().copy(showBookmarkManager = newValue)
        }
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

    /**
     * Mark the host with the given address as paired and persist the change.
     * Called by PairingPanel when pairing succeeds (or when checkServer reports already paired).
     * If no matching host config exists yet, automatically creates one with this address as
     * both name and address (the user can rename it later in the Host Manager).
     */
    fun markHostPaired(address: String) {
        Log.d(TAG, "markHostPaired: $address")
        val existing = _uiState.value.hostConfigs.find { it.address == address }
        if (existing != null) {
            if (existing.isPaired) return  // already marked
            val updated = existing.copy(isPaired = true)
            hostConfigManager.updateHost(updated)
            _uiState.update { state ->
                state.copy(hostConfigs = state.hostConfigs.map { if (it.id == existing.id) updated else it })
            }
            return
        }

        // No existing host — auto-create one and mark it paired in a single step
        Log.d(TAG, "markHostPaired: auto-creating host config for $address")
        val certFileName = hostConfigManager.certFileNameForHost(
            java.util.UUID.randomUUID().toString()
        )
        val newHost = HostConfig(
            name = address,            // use IP as initial name; user can rename later
            address = address,
            certFileName = certFileName,
            isPaired = true,
        )
        hostConfigManager.addHost(newHost)
        val updatedHosts = hostConfigManager.loadHosts()
        _uiState.update { state ->
            // If this is the first host, make it active
            val newActiveId = state.activeHostId ?: newHost.id
            if (newActiveId == newHost.id) {
                hostConfigManager.setActiveHostId(newHost.id)
                sharedPreferences.edit().putString("server_address", newHost.address).apply()
            }
            state.copy(
                hostConfigs = updatedHosts,
                activeHostId = newActiveId,
                serverAddress = if (newActiveId == newHost.id) newHost.address else state.serverAddress,
            )
        }
    }

    fun togglePairingDialog() {
        val willOpen = !_uiState.value.showPairing
        // If we're closing (or were already showing), stop discovery (started by previous open).
        // If we're opening, the start call below will (re)start it cleanly.
        if (!willOpen || _uiState.value.showPairing) {
            try { discoveryManager.stopDiscovery() } catch (e: Exception) {
                Log.w(TAG, "stopDiscovery failed in togglePairingDialog", e)
            }
        }
        _uiState.update {
            it.withAllPanelsClosed().copy(showPairing = willOpen)
        }
        if (willOpen) {
            try { discoveryManager.startDiscovery() } catch (e: Exception) {
                Log.w(TAG, "startDiscovery failed in togglePairingDialog", e)
            }
        }
    }

    fun updateStreamSettings(settings: StreamSettings) {
        _uiState.update { it.copy(streamSettings = settings) }
        streamSettingsManager.saveStreamSettings(settings)
    }

    fun updateAudioSettings(settings: AudioSettings) {
        _uiState.update { it.copy(audioSettings = settings) }
        audioSettingsManager.saveAudioSettings(settings)
    }

    fun setStreamingState(streaming: Boolean) {
        Log.d(TAG, "setStreamingState: $streaming")
        _uiState.update { it.copy(isStreaming = streaming) }
    }

    fun updateAutoReconnect(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("auto_reconnect_enabled", enabled).apply()
        _uiState.update { it.copy(autoReconnectEnabled = enabled) }
    }

    // --- App selector ---

    fun toggleAppSelector() {
        stopPanelSideEffectsBeforeClose()
        _uiState.update {
            val newValue = !it.showAppSelector
            it.withAllPanelsClosed().copy(showAppSelector = newValue)
        }
    }

    /**
     * Fetch the list of apps from the current server.
     * Runs the network call on [Dispatchers.IO] and updates UI state.
     */
    fun fetchApps() {
        val address = _uiState.value.serverAddress
        if (address.isBlank()) return

        _uiState.update { it.copy(isLoadingApps = true, appListError = null) }

        viewModelScope.launch {
            try {
                val dataDir = getApplication<Application>().filesDir
                val serverManager = ServerManager(dataDir, sharedPreferences)
                val apps = withContext(Dispatchers.IO) {
                    serverManager.getAppList(address).getOrThrow()
                }
                val savedAppId = sharedPreferences.getInt("selected_app_id", -1)
                val restoredApp = apps.find { it.appId == savedAppId }
                _uiState.update {
                    it.copy(
                        availableApps = apps,
                        isLoadingApps = false,
                        selectedApp = restoredApp
                            ?: it.selectedApp?.let { sel -> apps.find { a -> a.appId == sel.appId } }
                            ?: apps.find { a -> a.appName.equals("Desktop", ignoreCase = true) },
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch app list", e)
                _uiState.update {
                    it.copy(
                        isLoadingApps = false,
                        appListError = e.message ?: "Failed to fetch apps",
                    )
                }
            }
        }
    }

    /**
     * Select an app to stream. Persists the choice in SharedPreferences.
     */
    fun selectApp(app: ServerApp) {
        sharedPreferences.edit().putInt("selected_app_id", app.appId).apply()
        _uiState.update { it.copy(selectedApp = app, showAppSelector = false) }
    }

    // --- Network discovery ---
    // mDNS discovery is owned by the unified Connect panel (togglePairingDialog).
    // It auto-starts when the panel opens and stops when the panel closes (either via
    // togglePairingDialog itself or via mutual exclusion from another toggle*() function
    // through stopPanelSideEffectsBeforeClose).

    override fun onCleared() {
        super.onCleared()
        mainStreamSession.release()
        discoveryManager.stopDiscovery()
        // Stop all active streams and unbind service connections
        streamSlots.values.forEach { slot ->
            try { slot.stopStream() } catch (_: Exception) {}
            slot.unbind()
        }
        // SunshineApiManager caches an OkHttpClient between calls; explicitly shut
        // it down so the connection pool and dispatcher executor are released
        // immediately rather than waiting for the JVM to reclaim them.
        sunshineApiManager.shutdown()
    }

    // --- Host config management ---

    fun toggleHostManager() {
        stopPanelSideEffectsBeforeClose()
        _uiState.update {
            val newValue = !it.showHostManager
            it.withAllPanelsClosed().copy(showHostManager = newValue)
        }
    }

    /**
     * Open a stream panel for the given host in an isolated service process.
     * Assigns the host to a free slot (:stream0, :stream1, or :stream2).
     * No-op if the host is already streaming, doesn't exist, or no slots are free.
     */
    fun openStream(hostId: String) {
        _uiState.value.hostConfigs.find { it.id == hostId } ?: return
        if ((hostToSlot as Map<*, *>).containsKey(hostId)) return // already streaming

        // Find a free slot (not currently assigned to another host)
        val freeSlot = streamSlots.entries
            .firstOrNull { (name, _) -> !hostToSlot.containsValue(name) }
        if (freeSlot == null) {
            Log.w(TAG, "No free stream slots (max ${streamSlots.size} simultaneous streams)")
            return
        }

        hostToSlot[hostId] = freeSlot.key
        Log.i(TAG, "Assigned host $hostId to slot ${freeSlot.key}")

        // Only close the host manager on successful slot assignment (not on failure/no-op paths)
        _uiState.update { state ->
            state.copy(
                activeStreamHostIds = state.activeStreamHostIds + hostId,
                showHostManager = false,
            )
        }
    }


    /**
     * Close the stream panel for the given host and stop the service process stream.
     * No-op if the host is not currently streaming.
     */
    fun closeStream(hostId: String) {
        val slotName = hostToSlot.remove(hostId)
        slotName?.let { streamSlots[it] }?.stopStream()
        _uiState.update { state ->
            state.copy(activeStreamHostIds = state.activeStreamHostIds - hostId)
        }
    }

    /**
     * Returns the [StreamServiceConnection] assigned to [hostId] if it is bound and alive,
     * or null if not streaming or the service process hasn't connected yet.
     */
    fun getStreamSlot(hostId: String): StreamServiceConnection? =
        hostToSlot[hostId]?.let { streamSlots[it] }?.takeIf { it.isBound }

    fun addHost(name: String, address: String) {
        val certFileName = hostConfigManager.certFileNameForHost(
            java.util.UUID.randomUUID().toString()
        )
        val host = HostConfig(
            name = name.ifBlank { address },
            address = address,
            certFileName = certFileName,
        )
        hostConfigManager.addHost(host)
        val updatedHosts = hostConfigManager.loadHosts()
        _uiState.update { state ->
            // If this is the first host, make it active
            val newActiveId = state.activeHostId ?: host.id
            if (newActiveId == host.id) {
                hostConfigManager.setActiveHostId(host.id)
                sharedPreferences.edit().putString("server_address", host.address).apply()
            }
            state.copy(
                hostConfigs = updatedHosts,
                activeHostId = newActiveId,
                serverAddress = if (newActiveId == host.id) host.address else state.serverAddress,
            )
        }
    }

    fun removeHost(id: String) {
        hostConfigManager.removeHost(id)
        val updatedHosts = hostConfigManager.loadHosts()
        _uiState.update { state ->
            val newActiveId = if (state.activeHostId == id) {
                updatedHosts.firstOrNull()?.id
            } else {
                state.activeHostId
            }
            val newAddress = updatedHosts.find { it.id == newActiveId }?.address ?: DEFAULT_SERVER_ADDRESS
            if (newActiveId != state.activeHostId) {
                hostConfigManager.setActiveHostId(newActiveId)
                sharedPreferences.edit().putString("server_address", newAddress).apply()
            }
            state.copy(
                hostConfigs = updatedHosts,
                activeHostId = newActiveId,
                serverAddress = newAddress,
            )
        }
    }

    fun setActiveHost(id: String) {
        hostConfigManager.setActiveHostId(id)
        val host = hostConfigManager.loadHosts().find { it.id == id } ?: return
        sharedPreferences.edit().putString("server_address", host.address).apply()
        val mac = host.macAddress ?: ""
        sharedPreferences.edit().putString("server_mac_address", mac).apply()
        _uiState.update { state ->
            state.copy(
                activeHostId = id,
                serverAddress = host.address,
                macAddress = mac,
                streamSettings = host.qualityProfile ?: state.streamSettings,
            )
        }
        // Persist the applied stream settings so they survive app restart
        host.qualityProfile?.let { streamSettingsManager.saveStreamSettings(it) }
    }

    fun updateHost(host: HostConfig) {
        hostConfigManager.updateHost(host)
        val updatedHosts = hostConfigManager.loadHosts()
        _uiState.update { state ->
            val newAddress = if (state.activeHostId == host.id) {
                sharedPreferences.edit().putString("server_address", host.address).apply()
                host.address
            } else {
                state.serverAddress
            }
            state.copy(
                hostConfigs = updatedHosts,
                serverAddress = newAddress,
            )
        }
    }

    /**
     * Update (or clear) a host's per-host quality profile.
     * Pass null to remove the profile.
     */
    fun updateHostQualityProfile(hostId: String, profile: StreamSettings?) {
        val hosts = hostConfigManager.loadHosts()
        val host = hosts.find { it.id == hostId } ?: return
        hostConfigManager.updateHost(host.copy(qualityProfile = profile))
        val updatedHosts = hostConfigManager.loadHosts()
        _uiState.update { state ->
            state.copy(hostConfigs = updatedHosts)
        }
    }

    // --- Wake-on-LAN ---

    /**
     * Update the MAC address for the current server.
     * Persists to both the standalone pref and the active host config (if any).
     */
    fun updateMacAddress(mac: String) {
        sharedPreferences.edit().putString("server_mac_address", mac).apply()
        _uiState.update { state ->
            // Also persist MAC to the active host config if one exists
            val updatedHostConfigs = state.hostConfigs.map { host ->
                if (host.id == state.activeHostId) host.copy(macAddress = mac) else host
            }
            if (updatedHostConfigs != state.hostConfigs) {
                hostConfigManager.saveHosts(updatedHostConfigs)
            }
            state.copy(macAddress = mac, hostConfigs = updatedHostConfigs)
        }
    }

    /**
     * Send a Wake-on-LAN magic packet to the current server.
     * Transitions through [WolState] lifecycle: Idle → Sending → Sent/Failed.
     * Automatically resets to Idle after a delay so the UI can show transient feedback.
     */
    fun sendWakeOnLan() {
        val state = _uiState.value
        val mac = state.macAddress
        if (mac.isBlank() || !wolManager.isValidMacAddress(mac)) {
            _uiState.update { it.copy(wolState = WolState.Failed, wolError = "Invalid MAC address") }
            resetWolStateAfterDelay()
            return
        }

        _uiState.update { it.copy(wolState = WolState.Sending, wolError = null) }

        viewModelScope.launch {
            val result = wolManager.sendWakePacket(
                address = state.serverAddress,
                macAddress = mac,
            )
            result.fold(
                onSuccess = {
                    Log.i(TAG, "WoL packet sent to ${state.serverAddress} ($mac)")
                    _uiState.update { it.copy(wolState = WolState.Sent, wolError = null) }
                },
                onFailure = { e ->
                    Log.e(TAG, "WoL failed: ${e.message}", e)
                    _uiState.update { it.copy(wolState = WolState.Failed, wolError = e.message) }
                },
            )
            resetWolStateAfterDelay()
        }
    }

    /**
     * Reset WoL state to Idle after a brief delay for UI feedback.
     */
    private fun resetWolStateAfterDelay() {
        viewModelScope.launch {
            delay(WOL_FEEDBACK_DURATION_MS)
            _uiState.update { it.copy(wolState = WolState.Idle, wolError = null) }
        }
    }

    private fun saveOpenBookmarks() {
        // Never persist ephemeral tab IDs — they are not in bookmarks after restart
        val ids = _uiState.value.openBookmarkIds.filter { id ->
            _uiState.value.bookmarks.any { it.id == id && !it.isEphemeral }
        }.toSet()
        Log.d(TAG, "Auto-saving open bookmarks: $ids")
        sharedPreferences.edit().putStringSet("open_bookmark_ids", ids).apply()
    }

    // -----------------------------------------------------------------------
    // Monitor picker
    // -----------------------------------------------------------------------

    private val sunshineApiManager = SunshineApiManager()

    fun toggleMonitorPicker() {
        stopPanelSideEffectsBeforeClose()
        val showing = _uiState.value.showMonitorPicker
        _uiState.update { it.withAllPanelsClosed().copy(showMonitorPicker = !showing) }
        if (!showing) {
            // Auto-fetch monitors when opening the panel
            fetchMonitors()
        }
    }

    fun updateSunshineCredentials(username: String, password: String) {
        _uiState.update { it.copy(sunshineUsername = username, sunshinePassword = password) }
        sharedPreferences.edit()
            .putString("sunshine_username", username)
            .putString("sunshine_password", password)
            .apply()
    }

    /**
     * Fetch the list of displays from Sunshine's web API.
     * Requires valid Sunshine admin credentials stored in [WorkspaceUiState].
     */
    fun fetchMonitors() {
        val state = _uiState.value
        val address = state.serverAddress
        val username = state.sunshineUsername
        val password = state.sunshinePassword

        _uiState.update { it.copy(isLoadingMonitors = true, monitorError = null) }

        viewModelScope.launch {
            sunshineApiManager.fetchMonitors(address, username, password)
                .onSuccess { monitors ->
                    Log.i(TAG, "Loaded ${monitors.size} monitors from $address")
                    _uiState.update { it.copy(monitors = monitors, isLoadingMonitors = false) }
                }
                .onFailure { e ->
                    Log.e(TAG, "fetchMonitors failed: ${e.message}")
                    _uiState.update {
                        it.copy(
                            isLoadingMonitors = false,
                            monitorError = "Failed to load displays: ${e.message}",
                        )
                    }
                }
        }
    }

    /**
     * Switch the active monitor on Sunshine by updating `output_name` via the web API.
     * The change takes effect on the next stream session.
     */
    fun setActiveMonitor(monitor: MonitorInfo) {
        val state = _uiState.value
        _uiState.update { it.copy(isLoadingMonitors = true, monitorError = null) }

        viewModelScope.launch {
            sunshineApiManager.setActiveMonitor(
                address = state.serverAddress,
                username = state.sunshineUsername,
                password = state.sunshinePassword,
                systemName = monitor.systemName,
            ).onSuccess {
                Log.i(TAG, "Switched to monitor ${monitor.displayName}")
                // Update local list to reflect new active state
                val updated = state.monitors.map { m ->
                    m.copy(isActive = m.systemName == monitor.systemName)
                }
                _uiState.update {
                    it.copy(
                        monitors = updated,
                        isLoadingMonitors = false,
                        showMonitorPicker = false,
                    )
                }
            }.onFailure { e ->
                Log.e(TAG, "setActiveMonitor failed: ${e.message}")
                _uiState.update {
                    it.copy(
                        isLoadingMonitors = false,
                        monitorError = "Failed to switch display: ${e.message}",
                    )
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Curved panel rendering
    // -----------------------------------------------------------------------

    /**
     * Update curved panel settings and persist them.
     */
    fun updateCurvedPanelSettings(settings: CurvedPanelSettings) {
        curvedPanelSettingsManager.saveCurvedPanelSettings(settings)
        _uiState.update { it.copy(curvedPanelSettings = settings) }
    }

    // -----------------------------------------------------------------------
    // Workspace layout presets
    // -----------------------------------------------------------------------

    /**
     * Toggle the layout presets panel open/closed.
     */
    fun toggleLayoutPresets() {
        stopPanelSideEffectsBeforeClose()
        _uiState.update {
            val newValue = !it.showLayoutPresets
            it.withAllPanelsClosed().copy(showLayoutPresets = newValue)
        }
    }

    /** Toggle between passthrough (see-through) and virtual environment. Persisted across restarts. */
    fun togglePassthrough() {
        val newValue = !_uiState.value.isPassthroughActive
        _uiState.update { it.copy(isPassthroughActive = newValue) }
        sharedPreferences.edit().putBoolean("passthrough_active", newValue).apply()
    }

    /**
     * Save the current workspace state as a named layout preset.
     */
    fun saveLayoutPreset(name: String) {
        val state = _uiState.value
        // Exclude ephemeral tab IDs — they are session-only and won't survive restart
        val persistentOpenIds = state.openBookmarkIds.filter { id ->
            state.bookmarks.any { it.id == id && !it.isEphemeral }
        }.toSet()
        val layout = WorkspaceLayout(
            name = name,
            showDesktopPanel = state.showDesktopPanel,
            openBookmarkIds = persistentOpenIds,
        )
        workspaceLayoutManager.addLayout(layout)
        _uiState.update { it.copy(layoutPresets = workspaceLayoutManager.loadLayouts()) }
    }

    /**
     * Restore a previously saved layout preset.
     * Updates which panels are visible and which bookmarks are open.
     */
    fun loadLayoutPreset(layout: WorkspaceLayout) {
        val state = _uiState.value
        // Validate that bookmark IDs still exist in the current bookmark list
        val validIds = layout.openBookmarkIds.filter { id ->
            state.bookmarks.any { it.id == id }
        }.toSet()
        _uiState.update { it.copy(
            showDesktopPanel = layout.showDesktopPanel,
            openBookmarkIds = validIds,
            showLayoutPresets = false,
        )}
        sharedPreferences.edit()
            .putBoolean("layout_desktop", layout.showDesktopPanel)
            .putStringSet("open_bookmark_ids", validIds)
            .apply()
    }

    /**
     * Delete a saved layout preset by id.
     */
    fun deleteLayoutPreset(id: String) {
        workspaceLayoutManager.removeLayout(id)
        _uiState.update { it.copy(layoutPresets = workspaceLayoutManager.loadLayouts()) }
    }

    companion object {
        private const val TAG = "WorkspaceViewModel"
        /** How long to show WoL feedback (Sent/Failed) before resetting to Idle. */
        private const val WOL_FEEDBACK_DURATION_MS = 5000L
        /**
         * Fallback server address used when no host has been configured.
         * Also surfaced in UI placeholders so users know the expected format.
         */
        const val DEFAULT_SERVER_ADDRESS = "192.168.1.100"
    }
}

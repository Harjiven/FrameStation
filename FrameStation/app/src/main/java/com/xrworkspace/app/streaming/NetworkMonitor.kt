// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.streaming

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitors network connectivity state using [ConnectivityManager.NetworkCallback].
 * Emits [NetworkState] changes via a [StateFlow] for reactive consumers.
 *
 * Production usage: `NetworkMonitor(context)` — uses real ConnectivityManager.
 * Test usage: `NetworkMonitor(fakeStateFlow)` — uses an externally-controlled flow.
 */
class NetworkMonitor {

    companion object {
        private const val TAG = "FrameStation-Network"
    }

    private val connectivityManager: ConnectivityManager?
    private val _networkState: MutableStateFlow<NetworkState>

    val networkState: StateFlow<NetworkState>

    private var isRegistered = false

    /**
     * Production constructor — uses real [ConnectivityManager] to monitor network state.
     */
    constructor(context: Context) {
        connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        _networkState = MutableStateFlow(determineInitialState())
        networkState = _networkState.asStateFlow()
    }

    /**
     * Test constructor — uses an externally-controlled [StateFlow] for network state.
     * No real [ConnectivityManager] is used; [startMonitoring] and [stopMonitoring] are no-ops.
     */
    internal constructor(fakeState: StateFlow<NetworkState>) {
        connectivityManager = null
        _networkState = fakeState as? MutableStateFlow<NetworkState>
            ?: MutableStateFlow(fakeState.value)
        networkState = fakeState
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.i(TAG, "Network available: $network")
            _networkState.value = NetworkState.Connected
        }

        override fun onLost(network: Network) {
            Log.i(TAG, "Network lost: $network")
            _networkState.value = NetworkState.Disconnected
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities,
        ) {
            val hasInternet = networkCapabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET,
            )
            val hasWifi = networkCapabilities.hasTransport(
                NetworkCapabilities.TRANSPORT_WIFI,
            )
            Log.d(TAG, "Capabilities changed: internet=$hasInternet, wifi=$hasWifi")

            if (hasInternet) {
                _networkState.value = NetworkState.Connected
            }
        }
    }

    /**
     * Start monitoring network state. Safe to call multiple times — only registers once.
     * No-op when using the test constructor.
     */
    fun startMonitoring() {
        if (connectivityManager == null || isRegistered) return
        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
            isRegistered = true
            Log.i(TAG, "Network monitoring started")
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing ACCESS_NETWORK_STATE permission", e)
        }
    }

    /**
     * Stop monitoring network state and release the callback.
     * No-op when using the test constructor.
     */
    fun stopMonitoring() {
        if (connectivityManager == null || !isRegistered) return
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            isRegistered = false
            Log.i(TAG, "Network monitoring stopped")
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Callback was already unregistered", e)
        }
    }

    /**
     * Determine the initial network state by checking the active network.
     */
    private fun determineInitialState(): NetworkState {
        val cm = connectivityManager ?: return NetworkState.Disconnected
        val activeNetwork = cm.activeNetwork ?: return NetworkState.Disconnected
        val capabilities = cm.getNetworkCapabilities(activeNetwork)
            ?: return NetworkState.Disconnected
        return if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            NetworkState.Connected
        } else {
            NetworkState.Disconnected
        }
    }
}

/**
 * Represents the current network connectivity state.
 */
enum class NetworkState {
    /** Device has an active network connection with internet capability. */
    Connected,

    /** Device has no active network connection. */
    Disconnected,

    /** Network was lost and the system is attempting to reconnect. */
    Reconnecting,
}

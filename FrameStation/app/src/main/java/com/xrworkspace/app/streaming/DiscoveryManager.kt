// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.streaming

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.limelight.nvstream.mdns.JmDNSDiscoveryAgent
import com.limelight.nvstream.mdns.MdnsComputer
import com.limelight.nvstream.mdns.MdnsDiscoveryListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * A host discovered via mDNS network scanning.
 */
data class DiscoveredHost(
    val name: String,
    val address: String,
    val ipv6Address: String?,
    val port: Int,
    val discoveredAt: Long = System.currentTimeMillis(),
)

/**
 * Wraps [JmDNSDiscoveryAgent] and exposes discovered hosts as reactive [StateFlow]s.
 *
 * jmDNS handles its own background threading for multicast queries. Callbacks
 * ([notifyComputerAdded], [notifyDiscoveryFailure]) arrive on jmDNS worker threads,
 * but [MutableStateFlow.update] is thread-safe, so downstream collectors on the main
 * thread will see consistent snapshots.
 */
class DiscoveryManager(private val context: Context?) : MdnsDiscoveryListener {

    /** Test constructor — skips multicast lock (no real Context needed for unit tests). */
    internal constructor() : this(null)

    companion object {
        private const val TAG = "DiscoveryManager"
        private const val DISCOVERY_INTERVAL_MS = 5000
    }

    private val _discoveredHosts = MutableStateFlow<List<DiscoveredHost>>(emptyList())
    val discoveredHosts: StateFlow<List<DiscoveredHost>> = _discoveredHosts.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _discoveryError = MutableStateFlow<String?>(null)
    val discoveryError: StateFlow<String?> = _discoveryError.asStateFlow()

    private var agent: JmDNSDiscoveryAgent? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    /**
     * Create a [JmDNSDiscoveryAgent] and begin multicast discovery.
     * Safe to call multiple times — previous agents are stopped first.
     */
    fun startDiscovery() {
        stopDiscovery()
        _discoveryError.value = null
        try {
            val wifi = context?.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("FrameStation.mDNS")?.apply {
                setReferenceCounted(true)
                acquire()
            }
            Log.i(TAG, "Multicast lock acquired")
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire multicast lock", e)
        }
        try {
            val newAgent = JmDNSDiscoveryAgent(this)
            agent = newAgent
            newAgent.startDiscovery(DISCOVERY_INTERVAL_MS)
            _isScanning.value = true
            Log.i(TAG, "Discovery started (interval=${DISCOVERY_INTERVAL_MS}ms)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start discovery", e)
            _discoveryError.value = e.message ?: "Failed to start network discovery"
            _isScanning.value = false
        }
    }

    /**
     * Stop the active discovery agent and release resources.
     */
    fun stopDiscovery() {
        agent?.let {
            try {
                it.stopDiscovery()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping discovery", e)
            }
        }
        agent = null
        try { multicastLock?.release() } catch (e: Exception) { Log.w(TAG, "Multicast lock release failed", e) }
        multicastLock = null
        _isScanning.value = false
        Log.i(TAG, "Discovery stopped")
    }

    /**
     * Clear the discovered hosts list, stop, and restart discovery.
     */
    fun refreshDiscovery() {
        _discoveredHosts.value = emptyList()
        startDiscovery()
    }

    // -- MdnsDiscoveryListener callbacks (called on jmDNS worker threads) --

    override fun notifyComputerAdded(computer: MdnsComputer) {
        val host = DiscoveredHost(
            name = computer.name,
            address = computer.localAddress?.hostAddress ?: return,
            ipv6Address = computer.ipv6Address?.hostAddress,
            port = computer.port,
        )
        Log.i(TAG, "Computer discovered: ${host.name} @ ${host.address}")

        _discoveredHosts.update { current ->
            // Deduplicate by name + address
            if (current.any { it.name == host.name && it.address == host.address }) {
                current
            } else {
                current + host
            }
        }
    }

    override fun notifyDiscoveryFailure(e: Exception) {
        Log.e(TAG, "Discovery failure", e)
        _discoveryError.value = e.message ?: "Network discovery failed"
    }
}

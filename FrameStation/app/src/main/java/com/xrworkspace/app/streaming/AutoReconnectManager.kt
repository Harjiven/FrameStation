// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.streaming

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages automatic reconnection when a stream drops due to network loss.
 *
 * Workflow:
 * 1. Stream drops AND network is lost → enters [ReconnectState.WaitingForNetwork]
 * 2. Network recovers → starts reconnect attempts with exponential backoff
 * 3. After [maxRetries] failures → gives up and enters [ReconnectState.Failed]
 * 4. User can cancel at any time via [cancelReconnect]
 */
class AutoReconnectManager(
    private val networkMonitor: NetworkMonitor,
    private val coroutineScope: CoroutineScope,
) {
    companion object {
        private const val TAG = "FrameStation-Reconnect"
    }

    // Configuration
    var isEnabled: Boolean = true
    var maxRetries: Int = 5
    var retryDelayMs: Long = 3000L

    // Callbacks
    var onReconnectAttempt: ((attempt: Int) -> Unit)? = null
    var onReconnectSuccess: (() -> Unit)? = null
    var onReconnectFailed: ((reason: String) -> Unit)? = null
    var onReconnectGaveUp: (() -> Unit)? = null

    // State
    private val _reconnectState = MutableStateFlow(ReconnectState.Idle)
    val reconnectState: StateFlow<ReconnectState> = _reconnectState.asStateFlow()

    private var monitorJob: Job? = null
    private var reconnectJob: Job? = null
    private var reconnectAction: (suspend () -> Boolean)? = null
    private var currentAttempt = 0

    /**
     * Begin monitoring for stream disconnections that should trigger auto-reconnect.
     *
     * @param reconnectAction Suspend function that attempts to reconnect the stream.
     *   Returns `true` on success, `false` on failure.
     */
    fun startMonitoring(reconnectAction: suspend () -> Boolean) {
        if (!isEnabled) return
        this.reconnectAction = reconnectAction

        monitorJob?.cancel()
        monitorJob = coroutineScope.launch {
            try {
                networkMonitor.networkState.collect { networkState ->
                    handleNetworkStateChange(networkState)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Network monitor flow terminated unexpectedly", e)
            }
        }
        Log.i(TAG, "Auto-reconnect monitoring started")
    }

    /**
     * Stop all monitoring and cancel any pending reconnect attempts.
     */
    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAction = null
        currentAttempt = 0
        _reconnectState.value = ReconnectState.Idle
        Log.i(TAG, "Auto-reconnect monitoring stopped")
    }

    /**
     * Cancel an in-progress reconnection attempt and return to [ReconnectState.Idle].
     */
    fun cancelReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        currentAttempt = 0
        _reconnectState.value = ReconnectState.Idle
        Log.i(TAG, "Auto-reconnect cancelled by user")
    }

    /**
     * Notify the manager that the stream was terminated.
     * If the network is currently down, enters [ReconnectState.WaitingForNetwork].
     * If the network is up, starts reconnect attempts immediately.
     */
    fun onStreamTerminated() {
        if (!isEnabled) return

        val currentNetworkState = networkMonitor.networkState.value
        Log.i(TAG, "Stream terminated, network state: $currentNetworkState")

        when (currentNetworkState) {
            NetworkState.Disconnected, NetworkState.Reconnecting -> {
                _reconnectState.value = ReconnectState.WaitingForNetwork
                Log.i(TAG, "Waiting for network recovery before reconnecting")
            }
            NetworkState.Connected -> {
                startReconnectAttempts()
            }
        }
    }

    private fun handleNetworkStateChange(networkState: NetworkState) {
        val currentReconnectState = _reconnectState.value

        when {
            // Network recovered while waiting → start reconnect attempts
            networkState == NetworkState.Connected &&
                currentReconnectState == ReconnectState.WaitingForNetwork -> {
                Log.i(TAG, "Network recovered — starting reconnect attempts")
                startReconnectAttempts()
            }
            // Network lost during reconnect attempts → go back to waiting
            networkState == NetworkState.Disconnected &&
                currentReconnectState == ReconnectState.Reconnecting -> {
                Log.i(TAG, "Network lost during reconnect — waiting for recovery")
                reconnectJob?.cancel()
                reconnectJob = null
                currentAttempt = 0
                _reconnectState.value = ReconnectState.WaitingForNetwork
            }
        }
    }

    private fun startReconnectAttempts() {
        reconnectJob?.cancel()
        currentAttempt = 0
        _reconnectState.value = ReconnectState.Reconnecting

        reconnectJob = coroutineScope.launch {
            while (currentAttempt < maxRetries) {
                currentAttempt++
                val delayMs = retryDelayMs * (1L shl (currentAttempt - 1).coerceAtMost(5))
                Log.i(TAG, "Reconnect attempt $currentAttempt/$maxRetries (delay: ${delayMs}ms)")

                onReconnectAttempt?.invoke(currentAttempt)

                val action = reconnectAction ?: break
                try {
                    val success = action()
                    if (success) {
                        Log.i(TAG, "Reconnect succeeded on attempt $currentAttempt")
                        _reconnectState.value = ReconnectState.Idle
                        currentAttempt = 0
                        onReconnectSuccess?.invoke()
                        return@launch
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Reconnect attempt $currentAttempt failed", e)
                    onReconnectFailed?.invoke(e.message ?: "Unknown error")
                }

                // Wait before next attempt (exponential backoff)
                if (currentAttempt < maxRetries) {
                    delay(delayMs)
                }
            }

            // Exhausted all retries
            Log.w(TAG, "Auto-reconnect gave up after $maxRetries attempts")
            _reconnectState.value = ReconnectState.Failed
            currentAttempt = 0
            onReconnectGaveUp?.invoke()
        }
    }
}

/**
 * Represents the current state of the auto-reconnect process.
 */
enum class ReconnectState {
    /** No reconnection in progress. */
    Idle,

    /** Stream dropped and waiting for network to recover. */
    WaitingForNetwork,

    /** Actively attempting to reconnect the stream. */
    Reconnecting,

    /** All reconnect attempts exhausted. */
    Failed,
}

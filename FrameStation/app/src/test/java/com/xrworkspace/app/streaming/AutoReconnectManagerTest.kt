// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.streaming

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [AutoReconnectManager].
 *
 * Uses the internal test constructor of [NetworkMonitor] backed by a [MutableStateFlow]
 * to simulate network state changes without requiring a real Android Context.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AutoReconnectManagerTest {

    private lateinit var fakeNetworkState: MutableStateFlow<NetworkState>
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var testScope: TestScope
    private lateinit var manager: AutoReconnectManager

    private var reconnectAttempts: Int = 0
    private var reconnectShouldSucceed: Boolean = false
    private var gaveUp: Boolean = false
    private var succeeded: Boolean = false
    private var lastFailReason: String? = null

    @Before
    fun setUp() {
        fakeNetworkState = MutableStateFlow(NetworkState.Connected)
        networkMonitor = NetworkMonitor(fakeNetworkState)
        testScope = TestScope(StandardTestDispatcher())

        reconnectAttempts = 0
        reconnectShouldSucceed = false
        gaveUp = false
        succeeded = false
        lastFailReason = null

        // Use backgroundScope so monitoring coroutines auto-cancel when runTest completes,
        // avoiding UncompletedCoroutinesError from the forever-collecting networkState flow.
        manager = AutoReconnectManager(networkMonitor, testScope.backgroundScope).apply {
            isEnabled = true
            maxRetries = 5
            retryDelayMs = 3000L

            onReconnectAttempt = { reconnectAttempts = it }
            onReconnectSuccess = { succeeded = true }
            onReconnectFailed = { reason -> lastFailReason = reason }
            onReconnectGaveUp = { gaveUp = true }
        }
    }

    @After
    fun tearDown() {
        manager.stopMonitoring()
    }

    @Test
    fun `initial state is Idle`() {
        assertEquals(ReconnectState.Idle, manager.reconnectState.value)
    }

    @Test
    fun `stream terminated with network down enters WaitingForNetwork`() = testScope.runTest {
        fakeNetworkState.value = NetworkState.Disconnected

        manager.startMonitoring { reconnectShouldSucceed }
        advanceUntilIdle()

        manager.onStreamTerminated()
        advanceUntilIdle()

        assertEquals(ReconnectState.WaitingForNetwork, manager.reconnectState.value)
    }

    @Test
    fun `network recovery triggers reconnect attempts`() = testScope.runTest {
        fakeNetworkState.value = NetworkState.Disconnected

        manager.startMonitoring { reconnectShouldSucceed }
        advanceUntilIdle()

        manager.onStreamTerminated()
        advanceUntilIdle()
        assertEquals(ReconnectState.WaitingForNetwork, manager.reconnectState.value)

        // Simulate network recovery — use runCurrent() to process the flow emission
        // without advancing virtual time past retry delays.
        fakeNetworkState.value = NetworkState.Connected
        runCurrent()

        assertEquals(ReconnectState.Reconnecting, manager.reconnectState.value)
    }

    @Test
    fun `successful reconnect returns to Idle`() = testScope.runTest {
        reconnectShouldSucceed = true
        fakeNetworkState.value = NetworkState.Disconnected

        manager.startMonitoring { reconnectShouldSucceed }
        advanceUntilIdle()

        manager.onStreamTerminated()
        advanceUntilIdle()

        // Simulate network recovery — use runCurrent() first to start reconnect,
        // then advanceUntilIdle() to let the successful attempt complete.
        fakeNetworkState.value = NetworkState.Connected
        runCurrent()
        advanceUntilIdle()

        assertEquals(ReconnectState.Idle, manager.reconnectState.value)
        assertTrue("Reconnect should have succeeded", succeeded)
        assertEquals(1, reconnectAttempts)
    }

    @Test
    fun `gives up after max retries`() = testScope.runTest {
        reconnectShouldSucceed = false
        fakeNetworkState.value = NetworkState.Disconnected

        manager.startMonitoring { reconnectShouldSucceed }
        advanceUntilIdle()

        manager.onStreamTerminated()
        advanceUntilIdle()

        // Simulate network recovery
        fakeNetworkState.value = NetworkState.Connected

        // Advance through all retry delays with exponential backoff:
        // Attempt 1 (immediate), delay 3s, Attempt 2, delay 6s, Attempt 3, delay 12s, Attempt 4, delay 24s, Attempt 5
        // Total: 3000 + 6000 + 12000 + 24000 = 45000ms between attempts
        advanceTimeBy(200_000L)
        advanceUntilIdle()

        assertEquals(ReconnectState.Failed, manager.reconnectState.value)
        assertTrue("Manager should have given up", gaveUp)
        assertEquals(5, reconnectAttempts)
    }

    @Test
    fun `exponential backoff timing between attempts`() = testScope.runTest {
        reconnectShouldSucceed = false
        fakeNetworkState.value = NetworkState.Connected

        val attemptTimestamps = mutableListOf<Long>()
        manager.onReconnectAttempt = { attempt ->
            reconnectAttempts = attempt
            attemptTimestamps.add(testScheduler.currentTime)
        }

        manager.startMonitoring { reconnectShouldSucceed }
        advanceUntilIdle()

        manager.onStreamTerminated()

        // Advance enough time for all 5 attempts
        advanceTimeBy(200_000L)
        advanceUntilIdle()

        assertEquals("Should have 5 attempts", 5, attemptTimestamps.size)

        // Verify exponential backoff between attempts:
        // Gap 1→2: >= 3000ms (3s * 2^0 = 3s)
        // Gap 2→3: >= 6000ms (3s * 2^1 = 6s)
        // Gap 3→4: >= 12000ms (3s * 2^2 = 12s)
        // Gap 4→5: >= 24000ms (3s * 2^3 = 24s)
        if (attemptTimestamps.size >= 2) {
            val gap1 = attemptTimestamps[1] - attemptTimestamps[0]
            assertTrue("Gap 1→2 should be >= 3000ms, was ${gap1}ms", gap1 >= 3000L)
        }
        if (attemptTimestamps.size >= 3) {
            val gap2 = attemptTimestamps[2] - attemptTimestamps[1]
            assertTrue("Gap 2→3 should be >= 6000ms, was ${gap2}ms", gap2 >= 6000L)
        }
        if (attemptTimestamps.size >= 4) {
            val gap3 = attemptTimestamps[3] - attemptTimestamps[2]
            assertTrue("Gap 3→4 should be >= 12000ms, was ${gap3}ms", gap3 >= 12000L)
        }
        if (attemptTimestamps.size >= 5) {
            val gap4 = attemptTimestamps[4] - attemptTimestamps[3]
            assertTrue("Gap 4→5 should be >= 24000ms, was ${gap4}ms", gap4 >= 24000L)
        }
    }

    @Test
    fun `cancel stops reconnection and returns to Idle`() = testScope.runTest {
        reconnectShouldSucceed = false
        fakeNetworkState.value = NetworkState.Disconnected

        manager.startMonitoring { reconnectShouldSucceed }
        advanceUntilIdle()

        manager.onStreamTerminated()
        advanceUntilIdle()
        assertEquals(ReconnectState.WaitingForNetwork, manager.reconnectState.value)

        // Simulate network recovery to start reconnecting — use runCurrent() to process
        // the flow emission without advancing past retry delays.
        fakeNetworkState.value = NetworkState.Connected
        runCurrent()
        assertEquals(ReconnectState.Reconnecting, manager.reconnectState.value)

        // Cancel
        manager.cancelReconnect()
        advanceUntilIdle()

        assertEquals(ReconnectState.Idle, manager.reconnectState.value)
        assertEquals("Should not have given up (was cancelled)", false, gaveUp)
    }

    @Test
    fun `cancel during WaitingForNetwork returns to Idle`() = testScope.runTest {
        fakeNetworkState.value = NetworkState.Disconnected

        manager.startMonitoring { reconnectShouldSucceed }
        advanceUntilIdle()

        manager.onStreamTerminated()
        advanceUntilIdle()
        assertEquals(ReconnectState.WaitingForNetwork, manager.reconnectState.value)

        manager.cancelReconnect()
        advanceUntilIdle()

        assertEquals(ReconnectState.Idle, manager.reconnectState.value)
    }

    @Test
    fun `disabled manager does not auto-reconnect`() = testScope.runTest {
        manager.isEnabled = false
        fakeNetworkState.value = NetworkState.Disconnected

        manager.startMonitoring { true }
        advanceUntilIdle()

        manager.onStreamTerminated()
        advanceUntilIdle()

        assertEquals(ReconnectState.Idle, manager.reconnectState.value)
    }

    @Test
    fun `stopMonitoring cancels everything and resets to Idle`() = testScope.runTest {
        fakeNetworkState.value = NetworkState.Disconnected

        manager.startMonitoring { reconnectShouldSucceed }
        advanceUntilIdle()

        manager.onStreamTerminated()
        advanceUntilIdle()
        assertEquals(ReconnectState.WaitingForNetwork, manager.reconnectState.value)

        manager.stopMonitoring()
        advanceUntilIdle()

        assertEquals(ReconnectState.Idle, manager.reconnectState.value)
    }

    @Test
    fun `network lost during reconnect goes back to WaitingForNetwork`() = testScope.runTest {
        reconnectShouldSucceed = false
        fakeNetworkState.value = NetworkState.Disconnected

        manager.startMonitoring { reconnectShouldSucceed }
        advanceUntilIdle()

        manager.onStreamTerminated()
        advanceUntilIdle()

        // Network recovers → starts reconnecting — use runCurrent() to process
        // the flow emission without advancing past retry delays.
        fakeNetworkState.value = NetworkState.Connected
        runCurrent()
        assertEquals(ReconnectState.Reconnecting, manager.reconnectState.value)

        // Network drops again during reconnect
        fakeNetworkState.value = NetworkState.Disconnected
        runCurrent()

        assertEquals(ReconnectState.WaitingForNetwork, manager.reconnectState.value)
    }

    @Test
    fun `stream terminated with network up starts reconnecting immediately`() = testScope.runTest {
        reconnectShouldSucceed = false
        fakeNetworkState.value = NetworkState.Connected

        manager.startMonitoring { reconnectShouldSucceed }
        advanceUntilIdle()

        // Use runCurrent() to process the stream termination without advancing
        // past retry delays — we want to observe the Reconnecting intermediate state.
        manager.onStreamTerminated()
        runCurrent()

        assertEquals(ReconnectState.Reconnecting, manager.reconnectState.value)
    }

    @Test
    fun `manual stream start cancels auto-reconnect`() = testScope.runTest {
        reconnectShouldSucceed = false
        fakeNetworkState.value = NetworkState.Disconnected

        manager.startMonitoring { reconnectShouldSucceed }
        advanceUntilIdle()

        manager.onStreamTerminated()
        advanceUntilIdle()
        assertEquals(ReconnectState.WaitingForNetwork, manager.reconnectState.value)

        // Simulate user manually starting a new stream by cancelling auto-reconnect
        manager.cancelReconnect()
        advanceUntilIdle()

        assertEquals(ReconnectState.Idle, manager.reconnectState.value)
        assertEquals(false, gaveUp)
        assertEquals(false, succeeded)
    }
}

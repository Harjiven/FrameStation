// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.streaming

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Unit tests for [SunshineApiManager] lifecycle and shutdown behavior.
 *
 * These tests verify the public contract of the manager's lifecycle methods
 * without requiring a running Sunshine server. Network integration tests
 * would require OkHttp MockWebServer (not currently in test dependencies).
 *
 * Android framework stubs return defaults (`unitTests.isReturnDefaultValues = true`),
 * so `android.util.Log` calls inside the manager no-op rather than crashing.
 */
class SunshineApiManagerTest {

    // --- shutdown() safety ---

    @Test
    fun `shutdown on fresh manager does not crash`() {
        val manager = SunshineApiManager()
        // No client has been built yet — shutdown must tolerate null cachedClient
        manager.shutdown()
    }

    @Test
    fun `shutdown called twice does not crash`() {
        val manager = SunshineApiManager()
        manager.shutdown()
        manager.shutdown()
    }

    @Test
    fun `shutdown called three times does not crash`() {
        val manager = SunshineApiManager()
        manager.shutdown()
        manager.shutdown()
        manager.shutdown()
    }

    // --- fetchMonitors failure paths ---

    @Test
    fun `fetchMonitors with unreachable host returns failure`() = runTest {
        val manager = SunshineApiManager()
        // Use an RFC-5737 TEST-NET address that will never route
        val result = manager.fetchMonitors(
            address = "192.0.2.1",
            username = "admin",
            password = "wrong",
        )
        assertTrue(
            "Expected Result.failure for unreachable host, got: $result",
            result.isFailure,
        )
        // Cleanup
        manager.shutdown()
    }

    @Test
    fun `fetchMonitors with empty address returns failure`() = runTest {
        val manager = SunshineApiManager()
        val result = manager.fetchMonitors(
            address = "",
            username = "admin",
            password = "pass",
        )
        assertTrue(
            "Expected Result.failure for empty address, got: $result",
            result.isFailure,
        )
        manager.shutdown()
    }

    @Test
    fun `setActiveMonitor with unreachable host returns failure`() = runTest {
        val manager = SunshineApiManager()
        val result = manager.setActiveMonitor(
            address = "192.0.2.1",
            username = "admin",
            password = "wrong",
            systemName = "\\\\.\\DISPLAY1",
        )
        assertTrue(
            "Expected Result.failure for unreachable host, got: $result",
            result.isFailure,
        )
        manager.shutdown()
    }

    // --- shutdown after partial use ---

    @Test
    fun `shutdown after failed fetchMonitors does not crash`() = runTest {
        val manager = SunshineApiManager()
        // This will fail (unreachable address) but will have built a client internally
        manager.fetchMonitors("192.0.2.1", "admin", "pass")
        // Now shutdown should release the client that was built
        manager.shutdown()
    }

    @Test
    fun `shutdown after credential change does not crash`() = runTest {
        val manager = SunshineApiManager()
        // Build a client with one set of credentials
        manager.fetchMonitors("192.0.2.1", "user1", "pass1")
        // Build a different client (triggers releaseClient on old one)
        manager.fetchMonitors("192.0.2.1", "user2", "pass2")
        // Explicit shutdown releases the current client
        manager.shutdown()
    }

    // --- Error message quality ---

    @Test
    fun `fetchMonitors failure has a meaningful exception message`() = runTest {
        val manager = SunshineApiManager()
        val result = manager.fetchMonitors("192.0.2.1", "admin", "pass")
        val exception = result.exceptionOrNull()
        assertTrue(
            "Expected a non-null exception from Result.failure",
            exception != null,
        )
        val message = exception!!.message ?: ""
        assertTrue(
            "Expected exception message to be non-empty, got: '$message'",
            message.isNotBlank(),
        )
        manager.shutdown()
    }
}

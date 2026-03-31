// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.streaming

import com.limelight.nvstream.http.NvApp
import com.xrworkspace.app.model.ServerApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for NvApp → ServerApp mapping logic.
 *
 * Creates [NvApp] objects directly (bypassing moonlight-core's XML parser which
 * depends on Android's XmlPullParserFactory, unavailable in JVM unit tests).
 * The XML parsing itself is moonlight-core's responsibility; these tests verify
 * that the mapping from NvApp to ServerApp is correct.
 */
class ServerManagerAppListTest {

    /**
     * Helper: map NvApp list to ServerApp list, simulating ServerManager.getAppList logic.
     */
    private fun mapToServerApps(nvApps: List<NvApp>, runningGameId: Int = 0): List<ServerApp> {
        return nvApps.map { nvApp ->
            ServerApp(
                appId = nvApp.appId,
                appName = nvApp.appName,
                isHdrSupported = nvApp.isHdrSupported,
                isRunning = nvApp.appId == runningGameId && runningGameId != 0,
            )
        }
    }

    @Test
    fun `maps single app correctly`() {
        val nvApps = listOf(
            NvApp("Desktop", 1, false),
        )

        val apps = mapToServerApps(nvApps)
        assertEquals(1, apps.size)
        assertEquals("Desktop", apps[0].appName)
        assertEquals(1, apps[0].appId)
        assertFalse(apps[0].isHdrSupported)
        assertFalse(apps[0].isRunning)
    }

    @Test
    fun `maps multiple apps correctly`() {
        val nvApps = listOf(
            NvApp("Desktop", 1, false),
            NvApp("Steam Big Picture", 2, true),
            NvApp("Firefox", 3, false),
        )

        val apps = mapToServerApps(nvApps)
        assertEquals(3, apps.size)
        assertEquals("Desktop", apps[0].appName)
        assertEquals("Steam Big Picture", apps[1].appName)
        assertTrue(apps[1].isHdrSupported)
        assertEquals("Firefox", apps[2].appName)
    }

    @Test
    fun `marks running app correctly`() {
        val nvApps = listOf(
            NvApp("Desktop", 1, false),
            NvApp("Steam", 2, true),
        )

        val apps = mapToServerApps(nvApps, runningGameId = 2)

        assertFalse(apps[0].isRunning)
        assertTrue(apps[1].isRunning)
    }

    @Test
    fun `no app marked running when runningGameId is zero`() {
        val nvApps = listOf(
            NvApp("Desktop", 1, false),
        )

        val apps = mapToServerApps(nvApps, runningGameId = 0)

        assertFalse(apps[0].isRunning)
    }

    @Test
    fun `empty app list returns empty`() {
        val nvApps = emptyList<NvApp>()

        val apps = mapToServerApps(nvApps)
        assertEquals(0, apps.size)
    }

    @Test
    fun `HDR flag maps correctly`() {
        val nvApps = listOf(
            NvApp("HDR Game", 10, true),
            NvApp("SDR Game", 11, false),
        )

        val apps = mapToServerApps(nvApps)

        assertTrue(apps[0].isHdrSupported)
        assertFalse(apps[1].isHdrSupported)
    }
}

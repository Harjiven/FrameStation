// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.streaming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterization tests for [StreamSessionState.reduce].
 *
 * These pin the connect / terminate / reconnect / stop decisions currently smeared across
 * NativeStreamPanel + WorkspaceViewModel, so the Wave 3 lifecycle hoist can move ownership
 * without changing behavior. The one deliberate divergence from current production behavior
 * is [StreamEvent.DesktopPanelHidden], which fixes the toggleDesktopPanel stream leak.
 */
class StreamSessionStateTest {

    private val initial = StreamSessionState()

    /** Convenience: drive a fresh session to the Connected phase. */
    private fun connected(): StreamSessionState =
        initial.reduce(StreamEvent.StartRequested, autoReconnectEnabled = true).state
            .reduce(StreamEvent.ConnectionStarted, autoReconnectEnabled = true).state

    @Test
    fun `initial state is Idle and not streaming`() {
        assertEquals(StreamPhase.Idle, initial.phase)
        assertFalse(initial.isStreaming)
        assertFalse(initial.hasDisconnected)
    }

    @Test
    fun `StartRequested moves to Connecting and starts the stream`() {
        val r = initial.reduce(StreamEvent.StartRequested, autoReconnectEnabled = true)
        assertEquals(StreamPhase.Connecting, r.state.phase)
        assertFalse(r.state.isStreaming)
        assertTrue(r.effects.contains(StreamEffect.StartStream))
        assertTrue(r.effects.contains(StreamEffect.CancelReconnect))
    }

    @Test
    fun `ConnectionStarted moves to Connected and signals streaming`() {
        val connecting = initial.reduce(StreamEvent.StartRequested, true).state
        val r = connecting.reduce(StreamEvent.ConnectionStarted, true)
        assertEquals(StreamPhase.Connected, r.state.phase)
        assertTrue(r.state.isStreaming)
        assertFalse(r.state.hasDisconnected)
        assertTrue(r.effects.contains(StreamEffect.NotifyStreamingChanged(true)))
        assertTrue(r.effects.contains(StreamEffect.CancelReconnect))
    }

    @Test
    fun `StageChanged updates status text without changing phase or emitting effects`() {
        val connecting = initial.reduce(StreamEvent.StartRequested, true).state
        val r = connecting.reduce(StreamEvent.StageChanged("Fetching apps..."), true)
        assertEquals("Fetching apps...", r.state.statusText)
        assertEquals(StreamPhase.Connecting, r.state.phase)
        assertTrue(r.effects.isEmpty())
    }

    @Test
    fun `intentional termination goes to Disconnected without reconnect`() {
        val r = connected().reduce(
            StreamEvent.ConnectionTerminated(reason = "Stopped", intentional = true),
            autoReconnectEnabled = true,
        )
        assertEquals(StreamPhase.Disconnected, r.state.phase)
        assertFalse(r.state.isStreaming)
        assertTrue(r.state.hasDisconnected)
        assertFalse(r.effects.contains(StreamEffect.TriggerAutoReconnect))
        assertTrue(r.effects.contains(StreamEffect.NotifyStreamingChanged(false)))
    }

    @Test
    fun `unintentional termination with auto-reconnect enabled triggers reconnect`() {
        val r = connected().reduce(
            StreamEvent.ConnectionTerminated(reason = "Connection lost", intentional = false),
            autoReconnectEnabled = true,
        )
        assertEquals(StreamPhase.Reconnecting, r.state.phase)
        assertTrue(r.effects.contains(StreamEffect.TriggerAutoReconnect))
        assertTrue(r.effects.contains(StreamEffect.NotifyStreamingChanged(false)))
    }

    @Test
    fun `unintentional termination with auto-reconnect disabled does not reconnect`() {
        val r = connected().reduce(
            StreamEvent.ConnectionTerminated(reason = null, intentional = false),
            autoReconnectEnabled = false,
        )
        assertEquals(StreamPhase.Disconnected, r.state.phase)
        assertFalse(r.effects.contains(StreamEffect.TriggerAutoReconnect))
    }

    @Test
    fun `termination sets hasDisconnected so the button shows Reconnect`() {
        val r = connected().reduce(
            StreamEvent.ConnectionTerminated("x", intentional = true), true,
        )
        assertTrue(r.state.hasDisconnected)
    }

    @Test
    fun `StopRequested stops stream cancels reconnect and marks intentional`() {
        val r = connected().reduce(StreamEvent.StopRequested, true)
        assertEquals(StreamPhase.Disconnected, r.state.phase)
        assertTrue(r.state.intentionalStop)
        assertFalse(r.state.isStreaming)
        assertTrue(r.effects.contains(StreamEffect.StopStream))
        assertTrue(r.effects.contains(StreamEffect.CancelReconnect))
        assertTrue(r.effects.contains(StreamEffect.NotifyStreamingChanged(false)))
    }

    @Test
    fun `hiding desktop panel while connected stops the stream (leak fix)`() {
        val r = connected().reduce(StreamEvent.DesktopPanelHidden, true)
        assertEquals(StreamPhase.Disconnected, r.state.phase)
        assertFalse(r.state.isStreaming)
        assertTrue(r.state.intentionalStop)
        assertTrue(r.effects.contains(StreamEffect.StopStream))
        assertTrue(r.effects.contains(StreamEffect.CancelReconnect))
    }

    @Test
    fun `hiding desktop panel while idle does nothing`() {
        val r = initial.reduce(StreamEvent.DesktopPanelHidden, true)
        assertEquals(StreamPhase.Idle, r.state.phase)
        assertFalse(r.state.isStreaming)
        assertTrue(r.effects.isEmpty())
    }

    @Test
    fun `reconnect success returns to Connected from Reconnecting`() {
        val reconnecting = connected().reduce(
            StreamEvent.ConnectionTerminated("lost", intentional = false), true,
        ).state
        assertEquals(StreamPhase.Reconnecting, reconnecting.phase)
        val r = reconnecting.reduce(StreamEvent.ConnectionStarted, true)
        assertEquals(StreamPhase.Connected, r.state.phase)
        assertTrue(r.state.isStreaming)
    }

    @Test
    fun `reconnect give-up moves to Disconnected`() {
        val reconnecting = connected().reduce(
            StreamEvent.ConnectionTerminated("lost", intentional = false), true,
        ).state
        val r = reconnecting.reduce(StreamEvent.ReconnectGaveUp, true)
        assertEquals(StreamPhase.Disconnected, r.state.phase)
        assertFalse(r.state.isStreaming)
    }
}

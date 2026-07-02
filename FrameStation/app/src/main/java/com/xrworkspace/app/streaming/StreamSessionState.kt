// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.streaming

/**
 * Pure, side-effect-free state machine for a single Moonlight stream session.
 *
 * Today the connect / terminate / reconnect / `isStreaming` decisions are smeared across
 * [com.xrworkspace.app.ui.panels.NativeStreamPanel] callbacks and
 * [com.xrworkspace.app.viewmodel.WorkspaceViewModel]. This class lifts those decisions into
 * one testable place *before* the stream lifecycle is hoisted out of the composable
 * (implementation plan Wave 3 / Task 1.1).
 *
 * IMPORTANT: this is not yet wired into production. It is the characterization + target of
 * the refactor: its transitions mirror the current behavior, except [StreamEvent.DesktopPanelHidden],
 * which intentionally fixes the `WorkspaceViewModel.toggleDesktopPanel` leak (see that event).
 *
 * Usage pattern (once wired in Wave 3):
 * ```
 * val reduction = state.reduce(event, autoReconnectEnabled)
 * state = reduction.state
 * reduction.effects.forEach { applyEffect(it) } // start/stop connection, (cancel|trigger) reconnect, notify UI
 * ```
 */

/** Lifecycle phase of a single stream session. */
enum class StreamPhase {
    /** Never connected / fresh. */
    Idle,

    /** Start requested; awaiting [StreamEvent.ConnectionStarted]. */
    Connecting,

    /** Actively streaming. */
    Connected,

    /** Dropped unintentionally; auto-reconnect in progress. */
    Reconnecting,

    /** Stopped or terminated and not reconnecting (button shows "Reconnect"). */
    Disconnected,
}

/** Immutable snapshot of a stream session. */
data class StreamSessionState(
    val phase: StreamPhase = StreamPhase.Idle,
    val statusText: String = "Ready to connect",
    /**
     * True once the session has connected at least once and then dropped/stopped.
     * Drives the "Reconnect" vs "Start Stream" button label in the connection overlay.
     */
    val hasDisconnected: Boolean = false,
    /** Mirrors the app-wide isStreaming flag — true only while [phase] == [StreamPhase.Connected]. */
    val isStreaming: Boolean = false,
    /**
     * True when the most recent stop/termination was user- or error-initiated
     * (user Stop, auth/cert error, or hard stage failure), so auto-reconnect must NOT fire.
     */
    val intentionalStop: Boolean = false,
)

/** Inputs that drive the session state machine. */
sealed interface StreamEvent {
    /** User pressed Start / Reconnect. */
    data object StartRequested : StreamEvent

    /** The underlying connection reported that it is live. */
    data object ConnectionStarted : StreamEvent

    /** A human-readable stage/status update from the connection (e.g. "Fetching apps..."). */
    data class StageChanged(val stage: String) : StreamEvent

    /**
     * The connection ended.
     * @param reason human-readable reason, or null for a clean stop.
     * @param intentional true for user stop, auth/cert errors, or hard stage failures
     *   (Moonlight marks these via `wasIntentionalStop()`); such drops must NOT auto-reconnect.
     */
    data class ConnectionTerminated(val reason: String?, val intentional: Boolean) : StreamEvent

    /** User pressed Stop. */
    data object StopRequested : StreamEvent

    /**
     * The main desktop panel was hidden while a stream was active or starting.
     * FIX: `WorkspaceViewModel.toggleDesktopPanel` currently flips `isStreaming = false`
     * WITHOUT stopping the connection, orphaning the native stream. Hiding the panel while
     * active must actually stop it.
     */
    data object DesktopPanelHidden : StreamEvent

    /** Auto-reconnect exhausted all retries. */
    data object ReconnectGaveUp : StreamEvent
}

/** Side effects the caller must perform after a [reduce]. The machine itself does nothing. */
sealed interface StreamEffect {
    /** Start the real Moonlight connection. */
    data object StartStream : StreamEffect

    /** Stop the real Moonlight connection. */
    data object StopStream : StreamEffect

    /** Cancel any pending/active auto-reconnect. */
    data object CancelReconnect : StreamEffect

    /** Ask the auto-reconnect manager to begin reconnect attempts. */
    data object TriggerAutoReconnect : StreamEffect

    /** Propagate the streaming flag to the rest of the app (e.g. ViewModel `setStreamingState`). */
    data class NotifyStreamingChanged(val streaming: Boolean) : StreamEffect
}

/** Result of a reduction: the next state plus effects to apply. */
data class StreamReduction(
    val state: StreamSessionState,
    val effects: List<StreamEffect> = emptyList(),
)

/**
 * Pure transition function. Given the current state, an [event], and whether auto-reconnect is
 * enabled, returns the next state and the side effects the caller should perform.
 */
fun StreamSessionState.reduce(
    event: StreamEvent,
    autoReconnectEnabled: Boolean,
): StreamReduction = when (event) {
    StreamEvent.StartRequested -> StreamReduction(
        copy(phase = StreamPhase.Connecting, statusText = "Connecting...", intentionalStop = false),
        listOf(StreamEffect.CancelReconnect, StreamEffect.StartStream),
    )

    StreamEvent.ConnectionStarted -> StreamReduction(
        copy(
            phase = StreamPhase.Connected,
            statusText = "Connected",
            hasDisconnected = false,
            isStreaming = true,
            intentionalStop = false,
        ),
        listOf(StreamEffect.CancelReconnect, StreamEffect.NotifyStreamingChanged(true)),
    )

    is StreamEvent.StageChanged -> StreamReduction(
        copy(statusText = event.stage),
    )

    is StreamEvent.ConnectionTerminated -> {
        val base = copy(
            statusText = event.reason ?: "Disconnected",
            hasDisconnected = true,
            isStreaming = false,
            intentionalStop = event.intentional,
        )
        if (!event.intentional && autoReconnectEnabled) {
            StreamReduction(
                base.copy(phase = StreamPhase.Reconnecting),
                listOf(StreamEffect.NotifyStreamingChanged(false), StreamEffect.TriggerAutoReconnect),
            )
        } else {
            StreamReduction(
                base.copy(phase = StreamPhase.Disconnected),
                listOf(StreamEffect.NotifyStreamingChanged(false)),
            )
        }
    }

    StreamEvent.StopRequested -> StreamReduction(
        copy(
            phase = StreamPhase.Disconnected,
            statusText = "Disconnected",
            hasDisconnected = true,
            isStreaming = false,
            intentionalStop = true,
        ),
        listOf(
            StreamEffect.CancelReconnect,
            StreamEffect.StopStream,
            StreamEffect.NotifyStreamingChanged(false),
        ),
    )

    StreamEvent.DesktopPanelHidden -> {
        val active = phase == StreamPhase.Connecting ||
            phase == StreamPhase.Connected ||
            phase == StreamPhase.Reconnecting
        if (active) {
            StreamReduction(
                copy(
                    phase = StreamPhase.Disconnected,
                    statusText = "Disconnected",
                    isStreaming = false,
                    intentionalStop = true,
                ),
                listOf(
                    StreamEffect.CancelReconnect,
                    StreamEffect.StopStream,
                    StreamEffect.NotifyStreamingChanged(false),
                ),
            )
        } else {
            StreamReduction(this)
        }
    }

    StreamEvent.ReconnectGaveUp -> StreamReduction(
        copy(
            phase = StreamPhase.Disconnected,
            statusText = "Auto-reconnect failed — tap Reconnect to try manually",
            isStreaming = false,
        ),
    )
}

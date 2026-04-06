// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.ui.panels

import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.xr.compose.subspace.MovePolicy
import androidx.xr.compose.subspace.ResizePolicy
import androidx.xr.compose.subspace.SpatialPanel
import androidx.xr.compose.subspace.SpatialExternalSurface
import androidx.xr.compose.subspace.StereoMode
import androidx.xr.compose.subspace.SubspaceComposable
import androidx.xr.compose.subspace.layout.InteractionPolicy
import androidx.xr.compose.subspace.layout.SpatialInputEvent
import androidx.xr.compose.subspace.layout.SubspaceModifier
import androidx.xr.compose.subspace.layout.height
import androidx.xr.compose.subspace.layout.offset
import androidx.xr.compose.subspace.layout.width
import androidx.xr.scenecore.InputEvent
import com.limelight.nvstream.input.MouseButtonPacket
import com.xrworkspace.app.streaming.MoonlightStreamManager
import com.xrworkspace.app.streaming.StreamServiceConnection

/**
 * Subspace-only composable that renders the video surface in its own SpatialPanel.
 * Positioned identically to the main UI panel so the video appears behind/inside it.
 *
 * The video panel sits at z=-1dp (slightly behind the main UI panel) so the UI overlay
 * (Start Stream button, status text) renders on top.
 */
@SubspaceComposable
@Composable
fun StreamVideoSurface(
    streamManager: MoonlightStreamManager?,
    streamServiceConnection: StreamServiceConnection?,
    isConnected: Boolean,
    panelWidthDp: Float,
    panelHeightDp: Float,
    onSurfaceCreated: (Surface) -> Unit,
    onSurfaceDestroyed: () -> Unit,
) {
    // Wrap SpatialExternalSurface in a SpatialPanel so it has size and position.
    SpatialPanel(
        modifier = SubspaceModifier
            .width(panelWidthDp.dp)
            .height(panelHeightDp.dp)
            .offset(z = (-1).dp), // sit just behind the main UI panel
        dragPolicy = MovePolicy(isEnabled = false),
        resizePolicy = ResizePolicy(isEnabled = false),
    ) {
        SpatialExternalSurface(
            stereoMode = StereoMode.Mono,
            interactionPolicy = InteractionPolicy(isEnabled = true) { event ->
                if (!isConnected) return@InteractionPolicy
                val hitPos = event.hitPosition ?: return@InteractionPolicy

                val panelHalfW = panelWidthDp / 2f
                val panelHalfH = panelHeightDp / 2f
                val normX = ((hitPos.x + panelHalfW) / (panelHalfW * 2f)).coerceIn(0f, 1f)
                val normY = ((hitPos.y + panelHalfH) / (panelHalfH * 2f)).coerceIn(0f, 1f)
                val streamW = (streamManager?.streamWidth ?: 1920).toShort()
                val streamH = (streamManager?.streamHeight ?: 1080).toShort()
                val streamX = (normX * (streamManager?.streamWidth ?: 1920).toFloat()).toInt()
                    .coerceIn(0, (streamManager?.streamWidth ?: 1920) - 1).toShort()
                val streamY = (normY * (streamManager?.streamHeight ?: 1080).toFloat()).toInt()
                    .coerceIn(0, (streamManager?.streamHeight ?: 1080) - 1).toShort()

                if (streamServiceConnection != null) {
                    when (event.action) {
                        InputEvent.Action.DOWN -> {
                            streamServiceConnection.sendMousePosition(streamX, streamY, streamW, streamH)
                            streamServiceConnection.sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT)
                        }
                        InputEvent.Action.MOVE ->
                            streamServiceConnection.sendMousePosition(streamX, streamY, streamW, streamH)
                        InputEvent.Action.UP -> {
                            streamServiceConnection.sendMousePosition(streamX, streamY, streamW, streamH)
                            streamServiceConnection.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT)
                        }
                        else -> {}
                    }
                } else if (streamManager != null) {
                    when (event.action) {
                        InputEvent.Action.DOWN -> {
                            streamManager.sendMousePosition(streamX, streamY, streamW, streamH)
                            streamManager.sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT)
                        }
                        InputEvent.Action.MOVE ->
                            streamManager.sendMousePosition(streamX, streamY, streamW, streamH)
                        InputEvent.Action.UP -> {
                            streamManager.sendMousePosition(streamX, streamY, streamW, streamH)
                            streamManager.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT)
                        }
                        else -> {}
                    }
                }
            },
        ) {
            onSurfaceCreated { surface ->
                onSurfaceCreated(surface)
            }
            onSurfaceDestroyed {
                onSurfaceDestroyed()
            }
        }
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.ui.panels

import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.xr.compose.subspace.MovePolicy
import androidx.xr.compose.subspace.ResizePolicy
import androidx.xr.compose.subspace.SpatialExternalSurface
import androidx.xr.compose.subspace.StereoMode
import androidx.xr.compose.subspace.SubspaceComposable
import androidx.xr.compose.subspace.layout.InteractionPolicy
import androidx.xr.compose.subspace.layout.SubspaceModifier
import androidx.xr.compose.subspace.layout.height
import androidx.xr.compose.subspace.layout.offset
import androidx.xr.compose.subspace.layout.width
import androidx.xr.scenecore.InputEvent
import com.limelight.nvstream.input.MouseButtonPacket
import com.xrworkspace.app.streaming.StreamServiceConnection

/**
 * Subspace-only composable that renders a Moonlight video surface using SubspaceModifier
 * for size and position. Sits at z=-1dp behind the main UI panel.
 *
 * Must be called from within a Subspace { } composition context (NOT from inside a
 * SpatialPanel — SpatialPanel content is a regular Compose context, not a Subspace one).
 *
 * Touch input is forwarded via [streamServiceConnection] (the IPC service path used by
 * multi-stream panels). The main desktop panel passes `streamServiceConnection = null` and
 * relies on its own [NativeStreamPanel]-owned input pipeline; the InteractionPolicy here
 * silently no-ops in that case.
 */
@SubspaceComposable
@Composable
fun StreamVideoSurface(
    streamServiceConnection: StreamServiceConnection?,
    isConnected: Boolean,
    panelWidthDp: Float,
    panelHeightDp: Float,
    /** Y offset in dp — used to align with the main UI panel slot inside SpatialColumn. */
    offsetYDp: Float = 0f,
    /** Stream resolution for accurate touch-to-stream coordinate mapping. */
    streamWidth: Int = 1920,
    streamHeight: Int = 1080,
    onSurfaceCreated: (Surface) -> Unit,
    onSurfaceDestroyed: () -> Unit,
) {
    SpatialExternalSurface(
        modifier = SubspaceModifier
            .width(panelWidthDp.dp)
            .height(panelHeightDp.dp)
            .offset(y = offsetYDp.dp, z = (-1).dp),
        stereoMode = StereoMode.Mono,
        dragPolicy = MovePolicy(isEnabled = true),
        resizePolicy = ResizePolicy(isEnabled = true),
        interactionPolicy = InteractionPolicy(isEnabled = true) { event ->
            // Only the IPC path forwards input here. Main panel input is owned by
            // NativeStreamPanel; for it streamServiceConnection is null and we no-op.
            val ipc = streamServiceConnection ?: return@InteractionPolicy
            if (!isConnected) return@InteractionPolicy
            val hitPos = event.hitPosition ?: return@InteractionPolicy

            val panelHalfW = panelWidthDp / 2f
            val panelHalfH = panelHeightDp / 2f
            val normX = ((hitPos.x + panelHalfW) / (panelHalfW * 2f)).coerceIn(0f, 1f)
            val normY = ((hitPos.y + panelHalfH) / (panelHalfH * 2f)).coerceIn(0f, 1f)
            val streamW = streamWidth.toShort()
            val streamH = streamHeight.toShort()
            val streamX = (normX * streamWidth.toFloat()).toInt()
                .coerceIn(0, streamWidth - 1).toShort()
            val streamY = (normY * streamHeight.toFloat()).toInt()
                .coerceIn(0, streamHeight - 1).toShort()

            when (event.action) {
                InputEvent.Action.DOWN -> {
                    ipc.sendMousePosition(streamX, streamY, streamW, streamH)
                    ipc.sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT)
                }
                InputEvent.Action.MOVE ->
                    ipc.sendMousePosition(streamX, streamY, streamW, streamH)
                InputEvent.Action.UP -> {
                    ipc.sendMousePosition(streamX, streamY, streamW, streamH)
                    ipc.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT)
                }
                else -> {}
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

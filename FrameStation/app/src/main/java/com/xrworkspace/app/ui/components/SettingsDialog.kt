// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.xrworkspace.app.model.AudioSettings
import com.xrworkspace.app.model.CurvedPanelSettings
import com.xrworkspace.app.model.HostConfig
import com.xrworkspace.app.streaming.WolManager
import com.xrworkspace.app.model.Resolution
import com.xrworkspace.app.model.StreamSettings
import com.xrworkspace.app.model.VideoCodec
import com.xrworkspace.app.model.recommendedBitrateKbps
import android.os.Build
import kotlin.math.roundToInt

/**
 * Settings panel content — rendered inside its own SpatialPanel as a popup.
 * Includes server connection settings and stream quality controls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPanel(
    currentServerAddress: String,
    currentMacAddress: String = "",
    currentStreamSettings: StreamSettings = StreamSettings(),
    currentAudioSettings: AudioSettings = AudioSettings(),
    currentAutoReconnect: Boolean = true,
    activeHost: HostConfig? = null,
    currentCurvedPanelSettings: CurvedPanelSettings = CurvedPanelSettings(),
    onSave: (serverAddress: String, macAddress: String, streamSettings: StreamSettings, audioSettings: AudioSettings, autoReconnect: Boolean, curvedPanelSettings: CurvedPanelSettings) -> Unit,
    onDismiss: () -> Unit,
    onShowAbout: () -> Unit = {},
) {
    val scrollState = rememberScrollState()
    var ipState by remember { mutableStateOf(currentServerAddress) }
    var macState by remember { mutableStateOf(currentMacAddress) }
    var autoReconnectState by remember { mutableStateOf(currentAutoReconnect) }
    var audioSettingsState by remember { mutableStateOf(currentAudioSettings) }
    val wolManager = remember { WolManager() }
    val macError = remember(macState) {
        val mac = macState.trim()
        if (mac.isEmpty() || wolManager.isValidMacAddress(mac)) null
        else "Invalid format. Use AA:BB:CC:DD:EE:FF or AA-BB-CC-DD-EE-FF"
    }

    // Stream settings local state
    var selectedResolution by remember { mutableStateOf(currentStreamSettings.resolution) }
    var selectedFps by remember { mutableStateOf(currentStreamSettings.fps) }
    var bitrateKbps by remember { mutableFloatStateOf(currentStreamSettings.bitrateKbps.toFloat()) }
    var selectedCodec by remember { mutableStateOf(currentStreamSettings.codec) }
    var enableHdr by remember { mutableStateOf(currentStreamSettings.enableHdr) }

    // Dropdown expanded states
    var resolutionExpanded by remember { mutableStateOf(false) }
    var codecExpanded by remember { mutableStateOf(false) }
    var curvedEnabled by remember { mutableStateOf(currentCurvedPanelSettings.isEnabled) }
    var curvedRadiusDp by remember { mutableFloatStateOf(currentCurvedPanelSettings.radiusDp) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        tonalElevation = 4.dp,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Scrollable content area
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState),
            ) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineSmall,
                )

                Spacer(modifier = Modifier.height(24.dp))

                // --- Server Connection Section ---
                Text(
                    text = "Server Connection",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = ipState,
                    onValueChange = { ipState = it },
                    label = { Text("Server IP Address") },
                    placeholder = { Text("192.168.1.100") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Enter the IP address of your PC running Apollo/Sunshine",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(16.dp))

                // MAC address field for Wake-on-LAN
                OutlinedTextField(
                    value = macState,
                    onValueChange = { macState = it },
                    label = { Text("MAC Address") },
                    placeholder = { Text("AA:BB:CC:DD:EE:FF") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = macError != null,
                    supportingText = {
                        if (macError != null) {
                            Text(macError)
                        } else {
                            Text("Required for Wake-on-LAN. Find in your PC's network settings.")
                        }
                    },
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Auto-reconnect toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Reconnect",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = "Automatically reconnect when network recovers after a stream drop",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = autoReconnectState,
                        onCheckedChange = { autoReconnectState = it },
                    )
                }

                // Active host info
                if (activeHost != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Active Host",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${activeHost.name} (${activeHost.address})",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (activeHost.gpuType != null) {
                        Text(
                            text = "GPU: ${activeHost.gpuType}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = if (activeHost.isPaired) "Paired" else "Not paired",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (activeHost.isPaired) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(24.dp))

                // --- Stream Quality Section ---
                Text(
                    text = "Stream Quality",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Resolution dropdown
                ExposedDropdownMenuBox(
                    expanded = resolutionExpanded,
                    onExpandedChange = { resolutionExpanded = it },
                ) {
                    OutlinedTextField(
                        value = selectedResolution.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Resolution") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = resolutionExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = resolutionExpanded,
                        onDismissRequest = { resolutionExpanded = false },
                    ) {
                        Resolution.entries.forEach { resolution ->
                            DropdownMenuItem(
                                text = { Text("${resolution.label} (${resolution.width}x${resolution.height})") },
                                onClick = {
                                    selectedResolution = resolution
                                    resolutionExpanded = false
                                },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // FPS selector using FilterChips
                Text(
                    text = "Frame Rate",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(30, 60, 90, 120).forEach { fps ->
                        FilterChip(
                            selected = selectedFps == fps,
                            onClick = { selectedFps = fps },
                            label = { Text("${fps} FPS") },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bitrate slider
                Text(
                    text = "Bitrate: ${formatBitrate(bitrateKbps.roundToInt())}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Slider(
                    value = bitrateKbps,
                    onValueChange = { bitrateKbps = it },
                    valueRange = 1000f..100000f,
                    steps = 0,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "1 Mbps",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "100 Mbps",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Recommended bitrate button
                TextButton(
                    onClick = {
                        bitrateKbps = recommendedBitrateKbps(selectedResolution, selectedFps).toFloat()
                    },
                ) {
                    Text("Use Recommended (${formatBitrate(recommendedBitrateKbps(selectedResolution, selectedFps))})")
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Codec dropdown
                ExposedDropdownMenuBox(
                    expanded = codecExpanded,
                    onExpandedChange = { codecExpanded = it },
                ) {
                    OutlinedTextField(
                        value = selectedCodec.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Video Codec") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = codecExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = codecExpanded,
                        onDismissRequest = { codecExpanded = false },
                    ) {
                        VideoCodec.entries.forEach { codec ->
                            DropdownMenuItem(
                                text = { Text(codec.label) },
                                onClick = {
                                    selectedCodec = codec
                                    codecExpanded = false
                                },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "H.265 offers better quality at lower bitrates but requires hardware support",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Warn when AV1 is selected on pre-API-29 devices (will silently fall back to H.265)
                val isAv1Selected = selectedCodec == VideoCodec.AV1_MAIN8 ||
                    selectedCodec == VideoCodec.AV1_MAIN10
                val av1Available = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                if (isAv1Selected && !av1Available) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "AV1 requires Android 10 (API 29) — will fall back to H.265 on this device",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                // HDR requires H.265 or AV1 Main10 — disable when H.264 is forced
                val hdrSupported = selectedCodec != VideoCodec.H264
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "HDR Streaming",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (hdrSupported) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        )
                        Text(
                            text = if (hdrSupported) "H.265 Main10 / AV1 Main10, BT.2020 — server must support HDR"
                                   else "H.264 does not support HDR — select H.265 or AV1",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = enableHdr && hdrSupported,
                        onCheckedChange = { if (hdrSupported) enableHdr = it },
                        enabled = hdrSupported,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(24.dp))

                // --- Audio Section ---
                AudioSettingsSection(
                    audioSettings = audioSettingsState,
                    onAudioSettingsChanged = { audioSettingsState = it },
                )

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(24.dp))

                // --- Workspace Section ---
                Text(
                    text = "Workspace",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Curved Panels",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = "Wrap bookmark panels along a cylindrical arc",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = curvedEnabled,
                        onCheckedChange = { curvedEnabled = it },
                    )
                }

                if (curvedEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Arc Radius: ${curvedRadiusDp.roundToInt()} dp",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = curvedRadiusDp,
                        onValueChange = { curvedRadiusDp = it },
                        valueRange = 400f..1600f,
                        modifier = Modifier.fillMaxWidth(),
                    )

                }

                // Bottom spacing for scroll
                Spacer(modifier = Modifier.height(16.dp))
                
                // About button
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedButton(
                    onClick = onShowAbout,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("About & License")
                }
            }

            // Action buttons — always visible at bottom
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, alignment = Alignment.End),
            ) {
                OutlinedButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        val newSettings = StreamSettings(
                            resolution = selectedResolution,
                            fps = selectedFps,
                            bitrateKbps = bitrateKbps.roundToInt(),
                            codec = selectedCodec,
                            enableHdr = enableHdr,
                        )
                        val newCurvedSettings = CurvedPanelSettings(
                            isEnabled = curvedEnabled,
                            radiusDp = curvedRadiusDp,
                        )
                        onSave(ipState.trim(), macState.trim(), newSettings, audioSettingsState, autoReconnectState, newCurvedSettings)
                    },
                    enabled = macError == null,
                ) {
                    Text("Save")
                }
            }
        }
    }
}

/**
 * Formats a bitrate in kbps to a human-readable string (e.g., "20 Mbps" or "1500 kbps").
 */
private fun formatBitrate(kbps: Int): String {
    return if (kbps >= 1000) {
        val mbps = kbps / 1000.0
        if (mbps == mbps.toLong().toDouble()) {
            "${mbps.toLong()} Mbps"
        } else {
            "${"%.1f".format(mbps)} Mbps"
        }
    } else {
        "$kbps kbps"
    }
}

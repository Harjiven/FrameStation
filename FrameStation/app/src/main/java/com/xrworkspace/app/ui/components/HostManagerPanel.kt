// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xrworkspace.app.model.HostConfig
import com.xrworkspace.app.model.Resolution
import com.xrworkspace.app.model.StreamSettings
import com.xrworkspace.app.model.VideoCodec
import com.xrworkspace.app.model.recommendedBitrateKbps
import com.xrworkspace.app.util.formatBitrate
import kotlin.math.roundToInt

@Composable
fun HostManagerPanel(
    hostConfigs: List<HostConfig>,
    activeHostId: String?,
    activeStreamHostIds: Set<String> = emptySet(),
    onSelectHost: (String) -> Unit,
    onStreamHost: (String) -> Unit = {},
    onStopStreamHost: (String) -> Unit = {},
    onAddHost: (String, String) -> Unit,
    onRemoveHost: (String) -> Unit,
    onDismiss: () -> Unit,
    onUpdateHostProfile: (hostId: String, profile: StreamSettings?) -> Unit = { _, _ -> },
) {
    val newName = remember { mutableStateOf("") }
    val newAddress = remember { mutableStateOf("") }
    val confirmDeleteId = remember { mutableStateOf<String?>(null) }
    // Host ID whose quality profile editor dialog is currently open
    var editingProfileHostId by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        tonalElevation = 4.dp,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Host PCs",
                    style = MaterialTheme.typography.headlineSmall,
                )
                OutlinedButton(onClick = onDismiss) {
                    Text("Close")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Host list
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (hostConfigs.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "No hosts configured yet.\nAdd your first host below.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
                items(hostConfigs, key = { it.id }) { host ->
                    val isActive = host.id == activeHostId
                    val isConfirmingDelete = confirmDeleteId.value == host.id

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // Status icon
                            Icon(
                                imageVector = if (host.isPaired) Icons.Default.CheckCircle else Icons.Default.LinkOff,
                                contentDescription = if (host.isPaired) "Paired" else "Not paired",
                                modifier = Modifier.size(20.dp),
                                tint = if (host.isPaired) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )

                            // Host info
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = host.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = host.address,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    host.gpuType?.let { gpu ->
                                        Text(
                                            text = gpu,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                if (host.lastConnected > 0L) {
                                    Text(
                                        text = formatLastConnected(host.lastConnected),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    )
                                }
                            }

                            // Select as active
                            FilterChip(
                                selected = isActive,
                                onClick = { onSelectHost(host.id) },
                                label = { Text(if (isActive) "Active" else "Select") },
                                leadingIcon = if (isActive) {
                                    {
                                        Icon(
                                            Icons.Default.Computer,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                } else null,
                            )

                            // Stream toggle chip — only available for paired hosts with free slots
                            val isStreaming = host.id in activeStreamHostIds
                            val slotsAvailable = activeStreamHostIds.size < 3
                            val canStream = host.isPaired && (isStreaming || slotsAvailable)
                            FilterChip(
                                selected = isStreaming,
                                enabled = canStream,
                                onClick = {
                                    if (isStreaming) onStopStreamHost(host.id)
                                    else onStreamHost(host.id)
                                },
                                label = { Text(if (isStreaming) "Streaming" else "Stream") },
                                colors = if (isStreaming) FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ) else FilterChipDefaults.filterChipColors(),
                            )
                            // Explain why the chip is disabled
                            if (!isStreaming) {
                                val hint = when {
                                    !host.isPaired -> "Pair this host first"
                                    !slotsAvailable -> "Max 3 streams active"
                                    else -> null
                                }
                                if (hint != null) {
                                    Text(
                                        text = hint,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }

                            // Delete with confirmation
                            if (isConfirmingDelete) {
                                FilterChip(
                                    selected = true,
                                    onClick = {
                                        onRemoveHost(host.id)
                                        confirmDeleteId.value = null
                                    },
                                    label = { Text("Confirm") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.error,
                                        selectedLabelColor = MaterialTheme.colorScheme.onError,
                                    ),
                                )
                                FilterChip(
                                    selected = false,
                                    onClick = { confirmDeleteId.value = null },
                                    label = { Text("Cancel") },
                                )
                            } else {
                                FilterChip(
                                    selected = false,
                                    onClick = { confirmDeleteId.value = host.id },
                                    label = { Text("Delete") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                        labelColor = MaterialTheme.colorScheme.onErrorContainer,
                                    ),
                                )
                            }
                        }

                        // Quality profile row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 28.dp, top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = host.qualityProfile?.let { p ->
                                    "Profile: ${p.resolution.label}/${p.fps}fps/${formatCodecShort(p.codec)}"
                                } ?: "No profile",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (host.qualityProfile != null) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                },
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = { editingProfileHostId = host.id },
                            ) {
                                Text("Edit Profile", style = MaterialTheme.typography.labelSmall)
                            }
                            if (host.qualityProfile != null) {
                                TextButton(
                                    onClick = { onUpdateHostProfile(host.id, null) },
                                ) {
                                    Text(
                                        "Clear Profile",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Add host section
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            Text(
                text = "Add Host",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = newName.value,
                    onValueChange = { newName.value = it },
                    label = { Text("Name (optional)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = newAddress.value,
                    onValueChange = { newAddress.value = it },
                    label = { Text("IP Address") },
                    placeholder = { Text("192.168.1.100") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Button(
                    onClick = {
                        if (newAddress.value.isNotBlank()) {
                            onAddHost(newName.value.trim(), newAddress.value.trim())
                            newName.value = ""
                            newAddress.value = ""
                        }
                    },
                    enabled = newAddress.value.isNotBlank(),
                ) {
                    Text("Add")
                }
            }
        }
    }

    // Quality profile editor dialog
    editingProfileHostId?.let { hostId ->
        val host = hostConfigs.find { it.id == hostId }
        if (host != null) {
            QualityProfileEditorDialog(
                currentProfile = host.qualityProfile,
                hostName = host.name,
                onSave = { profile ->
                    onUpdateHostProfile(hostId, profile)
                    editingProfileHostId = null
                },
                onDismiss = { editingProfileHostId = null },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QualityProfileEditorDialog(
    currentProfile: StreamSettings?,
    hostName: String,
    onSave: (StreamSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val defaults = currentProfile ?: StreamSettings()
    var selectedResolution by remember { mutableStateOf(defaults.resolution) }
    var selectedFps by remember { mutableStateOf(defaults.fps) }
    var bitrateKbps by remember { mutableFloatStateOf(defaults.bitrateKbps.toFloat()) }
    var selectedCodec by remember { mutableStateOf(defaults.codec) }
    var resolutionExpanded by remember { mutableStateOf(false) }
    var codecExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quality Profile: $hostName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

                // FPS selector
                Text(text = "Frame Rate", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30, 60, 90, 120).forEach { fps ->
                        FilterChip(
                            selected = selectedFps == fps,
                            onClick = { selectedFps = fps },
                            label = { Text("$fps") },
                        )
                    }
                }

                // Bitrate slider
                Text(
                                            text = "Bitrate: ${formatBitrate(bitrateKbps.roundToInt())}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = bitrateKbps,
                    onValueChange = { bitrateKbps = it },
                    valueRange = 1000f..100000f,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = {
                        bitrateKbps = recommendedBitrateKbps(selectedResolution, selectedFps).toFloat()
                    },
                ) {
                                            Text("Use Recommended (${formatBitrate(recommendedBitrateKbps(selectedResolution, selectedFps))})")
                }

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
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        StreamSettings(
                            resolution = selectedResolution,
                            fps = selectedFps,
                            bitrateKbps = bitrateKbps.roundToInt(),
                            codec = selectedCodec,
                        )
                    )
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private fun formatCodecShort(codec: VideoCodec): String = when (codec) {
    VideoCodec.AUTO -> "Auto"
    VideoCodec.H264 -> "H.264"
    VideoCodec.H265 -> "H.265"
    VideoCodec.AV1_MAIN8 -> "AV1"
    VideoCodec.AV1_MAIN10 -> "AV1 10-bit"
}

// formatBitrate moved to com.xrworkspace.app.util.FormatUtils so SettingsDialog,
// HostManagerPanel, and FallbackWorkspace can share a single implementation.

private fun formatLastConnected(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000L -> "Just now"
        diff < 3_600_000L -> "${diff / 60_000L}m ago"
        diff < 86_400_000L -> "${diff / 3_600_000L}h ago"
        diff < 604_800_000L -> "${diff / 86_400_000L}d ago"
        else -> {
            val sdf = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
            sdf.format(java.util.Date(timestamp))
        }
    }
}

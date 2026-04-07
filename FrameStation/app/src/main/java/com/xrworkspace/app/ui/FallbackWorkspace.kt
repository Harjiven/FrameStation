// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xrworkspace.app.util.formatBitrate
import com.xrworkspace.app.viewmodel.WorkspaceUiState

/**
 * Returns true if the fallback (non-XR) workspace should be shown.
 * Extracted as a pure function for testability.
 */
fun shouldUseFallbackWorkspace(isSpatialUiEnabled: Boolean): Boolean = !isSpatialUiEnabled

/**
 * Flat Material3 workspace shown on non-XR Android devices where spatial UI is unavailable.
 * Displays device compatibility warning, connection info, stream settings, and bookmarks
 * in a scrollable card layout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FallbackWorkspace(
    uiState: WorkspaceUiState,
    onUpdateServerAddress: (String) -> Unit = {},
) {
    var showEditServer by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FrameStation") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
        bottomBar = {
            BottomAppBar(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (uiState.isPaired) "Status: Paired" else "Status: Not paired",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Device Compatibility section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = "Warning",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Device Compatibility",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Spatial UI is not available on this device. " +
                                "FrameStation requires an Android XR headset for full functionality.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            // Connection section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Connection",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = "Server Address: ${uiState.serverAddress}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(onClick = { showEditServer = true }) {
                        Text("Configure")
                    }
                }
            }

            // Stream Settings section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Stream Settings",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = "Resolution: ${uiState.streamSettings.resolution.label}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "FPS: ${uiState.streamSettings.fps}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                            text = "Bitrate: ${formatBitrate(uiState.streamSettings.bitrateKbps)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "Codec: ${uiState.streamSettings.codec.label}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            // Bookmarks section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Bookmarks",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    val savedBookmarks = uiState.bookmarks.filter { !it.isEphemeral }
                    if (savedBookmarks.isEmpty()) {
                        Text(
                            text = "No bookmarks configured.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        savedBookmarks.forEach { bookmark ->
                            Text(
                                text = "${bookmark.name} — ${bookmark.url}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }

    if (showEditServer) {
        var addressInput by remember { mutableStateOf(uiState.serverAddress) }
        AlertDialog(
            onDismissRequest = { showEditServer = false },
            title = { Text("Server Address") },
            text = {
                OutlinedTextField(
                    value = addressInput,
                    onValueChange = { addressInput = it },
                    label = { Text("IP Address") },
                    placeholder = { Text("192.168.1.100") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateServerAddress(addressInput.trim())
                    showEditServer = false
                }) { Text("Save") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showEditServer = false }) { Text("Cancel") }
            },
        )
    }
}

// formatBitrate moved to com.xrworkspace.app.util.FormatUtils so SettingsDialog,
// HostManagerPanel, and FallbackWorkspace can share a single implementation.

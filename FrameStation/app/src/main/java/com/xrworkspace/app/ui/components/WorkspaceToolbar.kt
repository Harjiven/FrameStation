// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ConnectedTv
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.ViewQuilt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xrworkspace.app.model.AudioMode
import com.xrworkspace.app.model.AudioSettings
import com.xrworkspace.app.model.Bookmark
import com.xrworkspace.app.viewmodel.WolState

@Composable
fun WorkspaceToolbar(
    showDesktop: Boolean,
    openBookmarks: List<Bookmark>,
    isStreaming: Boolean = false,
    onToggleDesktop: () -> Unit,
    onToggleBookmark: (String) -> Unit,
    onBookmarksClick: () -> Unit,
    onPairingClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onPresetsClick: () -> Unit = {},
    onHostsClick: () -> Unit = {},
    onDiscoverClick: () -> Unit = {},
    isDiscoveryActive: Boolean = false,
    activeHostName: String? = null,
    hasMacAddress: Boolean = false,
    wolState: WolState = WolState.Idle,
    onWakeClick: (() -> Unit)? = null,
    audioSettings: AudioSettings = AudioSettings(),
    onToggleMute: (() -> Unit)? = null,
    onStopStream: (() -> Unit)? = null,
    onShowKeyboard: (() -> Unit)? = null,
    onSwitchMonitor: (() -> Unit)? = null,
) {
    Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 4.dp) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Switch monitor — sends Ctrl+Shift+Right to cycle displays
            if (isStreaming) {
                FilterChip(
                    selected = false,
                    onClick = { onSwitchMonitor?.invoke() },
                    label = {
                        Icon(Icons.Default.ConnectedTv, contentDescription = "Switch Monitor", modifier = Modifier.size(18.dp))
                    },
                )
            }

            // Desktop toggle
            FilterChip(
                selected = showDesktop,
                onClick = onToggleDesktop,
                label = { Text("Desktop") },
                leadingIcon = {
                    Icon(Icons.Default.Computer, contentDescription = null, modifier = Modifier.size(18.dp))
                },
            )

            // Currently open bookmarks — quick toggle chips
            if (openBookmarks.isNotEmpty()) {
                VerticalDivider(
                    modifier = Modifier.height(32.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                )
                openBookmarks.forEach { bookmark ->
                    FilterChip(
                        selected = true,
                        onClick = { onToggleBookmark(bookmark.id) },
                        label = { Text(bookmark.name) },
                    )
                }
            }

            // Streaming controls
            if (isStreaming) {
                VerticalDivider(
                    modifier = Modifier.height(32.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                )

                // Audio state indicator with quick mute toggle
                val isMuted = audioSettings.audioMode == AudioMode.MUTED
                FilterChip(
                    selected = isMuted,
                    onClick = { onToggleMute?.invoke() },
                    label = {
                        Text(
                            if (isMuted) "Muted" else audioSettings.audioChannels.label,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (isMuted) "Audio muted" else "Audio active",
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )

                FilterChip(
                    selected = true,
                    onClick = { onStopStream?.invoke() },
                    label = { Text("Stop") },
                    leadingIcon = {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                )
            }

            // Wake-on-LAN — shown when MAC is configured and not currently streaming
            if (hasMacAddress && !isStreaming) {
                VerticalDivider(
                    modifier = Modifier.height(32.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                )
                FilterChip(
                    selected = wolState == WolState.Sending || wolState == WolState.Sent,
                    onClick = { onWakeClick?.invoke() },
                    enabled = wolState == WolState.Idle || wolState == WolState.Failed,
                    label = {
                        Text(
                            when (wolState) {
                                WolState.Idle -> "Wake"
                                WolState.Sending -> "Sending..."
                                WolState.Sent -> "Sent!"
                                WolState.Failed -> "Failed"
                            }
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    colors = when (wolState) {
                        WolState.Sent -> FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        WolState.Failed -> FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            labelColor = MaterialTheme.colorScheme.onErrorContainer,
                            iconColor = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        else -> FilterChipDefaults.filterChipColors()
                    },
                )
            }

            // Bookmarks manager, pairing, settings
            VerticalDivider(
                modifier = Modifier.height(32.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            )
            FilterChip(
                selected = false,
                onClick = onBookmarksClick,
                label = {
                    Icon(Icons.Default.Star, contentDescription = "Bookmarks", modifier = Modifier.size(18.dp))
                },
            )
            FilterChip(
                selected = false,
                onClick = onPresetsClick,
                label = {
                    Icon(Icons.AutoMirrored.Filled.ViewQuilt, contentDescription = "Layout Presets", modifier = Modifier.size(18.dp))
                },
            )
            FilterChip(
                selected = false,
                onClick = onHostsClick,
                label = {
                    Icon(Icons.Default.Dns, contentDescription = "Hosts", modifier = Modifier.size(18.dp))
                },
            )
            FilterChip(
                selected = isDiscoveryActive,
                onClick = onDiscoverClick,
                label = {
                    Icon(Icons.Default.WifiFind, contentDescription = "Discover", modifier = Modifier.size(18.dp))
                },
            )
            FilterChip(
                selected = false,
                onClick = onPairingClick,
                label = {
                    Icon(Icons.Default.Link, contentDescription = "Pair", modifier = Modifier.size(18.dp))
                },
            )
            FilterChip(
                selected = false,
                onClick = onSettingsClick,
                label = {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(18.dp))
                },
            )

            // Keyboard — icon-only, placed after Settings
            if (isStreaming) {
                FilterChip(
                    selected = false,
                    onClick = { onShowKeyboard?.invoke() },
                    label = {
                        Icon(Icons.Default.Keyboard, contentDescription = "Keyboard", modifier = Modifier.size(18.dp))
                    },
                )
            }
        }
    }
}

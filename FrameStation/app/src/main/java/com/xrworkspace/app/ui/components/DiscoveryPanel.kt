// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xrworkspace.app.streaming.DiscoveredHost

/**
 * Panel showing hosts discovered via mDNS network scanning.
 * Tap a host to select it as the active server.
 */
@Composable
fun DiscoveryPanel(
    discoveredHosts: List<DiscoveredHost>,
    isScanning: Boolean,
    discoveryError: String?,
    onSelectHost: (DiscoveredHost) -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
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
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.WifiFind,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Network Discovery",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
                if (isScanning) {
                    ScanningIndicator()
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Scanning for Apollo/Sunshine servers on your network",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Content area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when {
                    // Error state
                    discoveryError != null && discoveredHosts.isEmpty() -> {
                        ErrorState(
                            message = discoveryError,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    // Empty state — scanning but nothing found yet
                    discoveredHosts.isEmpty() && isScanning -> {
                        EmptyScanning(modifier = Modifier.align(Alignment.Center))
                    }
                    // Empty state — not scanning, nothing found
                    discoveredHosts.isEmpty() -> {
                        EmptyState(modifier = Modifier.align(Alignment.Center))
                    }
                    // Host list
                    else -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(discoveredHosts, key = { "${it.name}:${it.address}" }) { host ->
                                HostRow(
                                    host = host,
                                    onClick = { onSelectHost(host) },
                                )
                            }
                            // Show error below the list if there is one
                            if (discoveryError != null) {
                                item {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = discoveryError,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isScanning) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = onRefresh,
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Refresh")
                        }
                        Button(
                            onClick = onStopScan,
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Stop Scan")
                        }
                    }
                } else {
                    Button(
                        onClick = onStartScan,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Scan")
                    }
                }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
private fun HostRow(
    host: DiscoveredHost,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Computer,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = host.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = host.address,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                host.ipv6Address?.let { v6 ->
                    Text(
                        text = "IPv6: $v6",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = formatDiscoveredTime(host.discoveredAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ScanningIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "scanning")
    val alpha = infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.alpha(alpha.value),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Scanning...",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun EmptyScanning(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Searching for servers...",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Make sure Apollo/Sunshine is running on your PC",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.WifiFind,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No hosts found",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tap \"Start Scan\" to search your network",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Discovery Error",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Format a discovery timestamp as a relative time string.
 */
private fun formatDiscoveredTime(timestamp: Long): String {
    val elapsed = System.currentTimeMillis() - timestamp
    return when {
        elapsed < 60_000 -> "just now"
        elapsed < 3_600_000 -> "${elapsed / 60_000}m ago"
        else -> "${elapsed / 3_600_000}h ago"
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HdrOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xrworkspace.app.model.ServerApp

/**
 * Panel that displays available apps from the host PC.
 * Users can select an app to stream, refresh the list, and start streaming.
 */
@Composable
fun AppSelectorPanel(
    apps: List<ServerApp>,
    selectedApp: ServerApp?,
    isLoading: Boolean,
    error: String?,
    onSelectApp: (ServerApp) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Auto-fetch on first display if the list is empty
    LaunchedEffect(Unit) {
        if (apps.isEmpty() && !isLoading) {
            onRefresh()
        }
    }

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
            // Header with refresh button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Select App",
                    style = MaterialTheme.typography.headlineSmall,
                )
                IconButton(
                    onClick = onRefresh,
                    enabled = !isLoading,
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh app list",
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Content area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isLoading -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Fetching apps...",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }

                    error != null -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Failed to load apps",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = onRefresh) {
                                Text("Retry")
                            }
                        }
                    }

                    apps.isEmpty() -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                "No apps found",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Make sure your PC is running and paired.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = onRefresh) {
                                Text("Refresh")
                            }
                        }
                    }

                    else -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(apps, key = { it.appId }) { app ->
                                AppRow(
                                    app = app,
                                    isSelected = selectedApp?.appId == app.appId,
                                    isDesktop = app.appName.equals("Desktop", ignoreCase = true),
                                    onClick = { onSelectApp(app) },
                                )
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
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text("Close")
                }
            }
        }
    }
}

/**
 * A single row in the app list.
 * Shows app name, HDR badge, running indicator, and selection state.
 */
@Composable
private fun AppRow(
    app: ServerApp,
    isSelected: Boolean,
    isDesktop: Boolean,
    onClick: () -> Unit,
) {
    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        isDesktop -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // App icon
        Icon(
            imageVector = if (isDesktop) Icons.Default.Computer else Icons.Default.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )

        Spacer(modifier = Modifier.width(12.dp))

        // App name and badges
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isDesktop) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f, fill = false),
                )

                // "Recommended" label for Desktop
                if (isDesktop) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Recommended",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Status row: HDR badge + running indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (app.isHdrSupported) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.HdrOn,
                            contentDescription = "HDR supported",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.tertiary,
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "HDR",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }

                if (app.isRunning) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Running",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        // Selection indicator
        if (isSelected) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Selected",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

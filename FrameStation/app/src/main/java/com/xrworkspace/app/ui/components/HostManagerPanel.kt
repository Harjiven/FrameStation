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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xrworkspace.app.model.HostConfig

@Composable
fun HostManagerPanel(
    hostConfigs: List<HostConfig>,
    activeHostId: String?,
    onSelectHost: (String) -> Unit,
    onAddHost: (String, String) -> Unit,
    onRemoveHost: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val newName = remember { mutableStateOf("") }
    val newAddress = remember { mutableStateOf("") }
    val confirmDeleteId = remember { mutableStateOf<String?>(null) }

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
                items(hostConfigs, key = { it.id }) { host ->
                    val isActive = host.id == activeHostId
                    val isConfirmingDelete = confirmDeleteId.value == host.id

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
}

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

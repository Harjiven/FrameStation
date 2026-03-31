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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.xrworkspace.app.model.MonitorInfo

/**
 * Popup panel for selecting which monitor the Sunshine/Apollo host should stream.
 *
 * Communicates with Sunshine's web API (port 47990) using Basic Auth credentials.
 * If credentials are not yet set, shows a credential entry form first.
 *
 * Flow:
 *   1. If credentials blank → show credential form
 *   2. User fills credentials and taps "Connect" → triggers [onFetchMonitors]
 *   3. Monitor list appears (or error + Retry)
 *   4. User taps a monitor → [onSelectMonitor] called, panel closes
 */
@Composable
fun MonitorPickerPanel(
    monitors: List<MonitorInfo>,
    isLoading: Boolean,
    error: String?,
    sunshineUsername: String,
    sunshinePassword: String,
    onCredentialsChanged: (username: String, password: String) -> Unit,
    onFetchMonitors: () -> Unit,
    onSelectMonitor: (MonitorInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    var usernameState by remember(sunshineUsername) { mutableStateOf(sunshineUsername) }
    var passwordState by remember(sunshinePassword) { mutableStateOf(sunshinePassword) }
    val credentialsSet = sunshineUsername.isNotBlank() && sunshinePassword.isNotBlank()

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Select Monitor",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Row {
                    if (credentialsSet) {
                        IconButton(onClick = onFetchMonitors, enabled = !isLoading) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            if (!credentialsSet) {
                // Credential entry form
                Text(
                    text = "Enter your Sunshine admin credentials to list available displays.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = usernameState,
                    onValueChange = { usernameState = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = passwordState,
                    onValueChange = { passwordState = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        onCredentialsChanged(usernameState.trim(), passwordState)
                        onFetchMonitors()
                    },
                    enabled = usernameState.isNotBlank() && passwordState.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Connect")
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Credentials are stored locally on the headset and never transmitted elsewhere.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // Monitor list or loading/error states
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Fetching displays…", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    error != null && monitors.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = onFetchMonitors) { Text("Retry") }
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = {
                                    // Clear credentials so user can re-enter them
                                    onCredentialsChanged("", "")
                                },
                            ) {
                                Text("Change Credentials")
                            }
                        }
                    }

                    monitors.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("No displays found. Tap refresh to try again.")
                        }
                    }

                    else -> {
                        if (error != null) {
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(monitors, key = { it.systemName }) { monitor ->
                                MonitorRow(
                                    monitor = monitor,
                                    onClick = { onSelectMonitor(monitor) },
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { onCredentialsChanged("", "") },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Change Credentials", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun MonitorRow(
    monitor: MonitorInfo,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors = if (monitor.isActive) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Monitor,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (monitor.isActive) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = monitor.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (monitor.isActive) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = monitor.systemName,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (monitor.isActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (monitor.isActive) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Active",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

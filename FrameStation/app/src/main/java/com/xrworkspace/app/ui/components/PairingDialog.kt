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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.limelight.nvstream.http.PairingManager
import com.xrworkspace.app.streaming.ServerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class PairingStep {
    ENTER_IP,
    CHECKING,
    SERVER_REACHABLE,
    SERVER_UNREACHABLE,
    SHOW_PIN,
    PAIRING,
    PAIRED,
    PAIR_FAILED,
}

/**
 * Connect panel — unified entry for both network discovery and server pairing.
 * The user sees a list of discovered hosts at the top (auto-refreshed via mDNS),
 * can tap one to pre-fill the IP, or can manually enter an IP. The same panel
 * walks through pairing inline.
 */
@Composable
fun PairingPanel(
    serverAddress: String,
    onConnect: (String) -> Unit,
    onAddressChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    /** Called with the paired server's IP when pairing succeeds. ViewModel uses this
     *  to find the matching HostConfig by address and flip its isPaired flag. */
    onPairingSuccess: (String) -> Unit = {},
    /** Hosts discovered via mDNS while this panel is open. */
    discoveredHosts: List<com.xrworkspace.app.streaming.DiscoveredHost> = emptyList(),
    /** True when network discovery scan is currently running. */
    isScanning: Boolean = false,
    dataDir: File,
) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("framestation_prefs", android.content.Context.MODE_PRIVATE) }
    val serverManager = remember { ServerManager(dataDir, prefs) }

    var step by remember { mutableStateOf(PairingStep.ENTER_IP) }
    var ipAddress by remember { mutableStateOf(serverAddress) }
    var serverInfo by remember { mutableStateOf<ServerManager.ServerInfo?>(null) }
    var pin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun checkServer() {
        if (ipAddress.isBlank()) return
        onAddressChanged(ipAddress) // Persist the IP
        scope.launch {
            step = PairingStep.CHECKING
            errorMessage = null
            withContext(Dispatchers.IO) {
                serverManager.checkServer(ipAddress)
            }.onSuccess { info ->
                serverInfo = info
                if (info.isPaired) {
                    step = PairingStep.PAIRED
                    // Server reports already paired — sync the host config flag
                    onPairingSuccess(ipAddress)
                } else {
                    step = PairingStep.SERVER_REACHABLE
                }
            }.onFailure { e ->
                errorMessage = e.message ?: "Connection failed"
                step = PairingStep.SERVER_UNREACHABLE
            }
        }
    }

    fun startPairing() {
        val generatedPin = serverManager.generatePin()
        pin = generatedPin
        step = PairingStep.SHOW_PIN

        scope.launch {
            step = PairingStep.PAIRING
            withContext(Dispatchers.IO) {
                serverManager.pair(ipAddress, generatedPin)
            }.onSuccess { pairState ->
                when (pairState) {
                    PairingManager.PairState.PAIRED -> {
                        step = PairingStep.PAIRED
                        // Notify ViewModel so it can update the matching HostConfig.isPaired flag
                        onPairingSuccess(ipAddress)
                    }
                    PairingManager.PairState.PIN_WRONG -> {
                        errorMessage = "PIN was incorrect. Try again."
                        step = PairingStep.PAIR_FAILED
                    }
                    PairingManager.PairState.ALREADY_IN_PROGRESS -> {
                        errorMessage = "Another device is already pairing. Wait and try again."
                        step = PairingStep.PAIR_FAILED
                    }
                    else -> {
                        errorMessage = "Pairing failed: $pairState"
                        step = PairingStep.PAIR_FAILED
                    }
                }
            }.onFailure { e ->
                errorMessage = e.message ?: "Pairing failed"
                step = PairingStep.PAIR_FAILED
            }
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
            // Header
            Text(
                text = "Server Pairing",
                style = MaterialTheme.typography.headlineSmall,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Content area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                when (step) {
                    PairingStep.ENTER_IP -> {
                        // Discovered hosts list — auto-populated via mDNS while panel is open
                        if (discoveredHosts.isNotEmpty() || isScanning) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Discovered on Network",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f),
                                )
                                if (isScanning) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            if (discoveredHosts.isEmpty()) {
                                Text(
                                    text = "Searching for Apollo/Sunshine servers...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                discoveredHosts.forEach { host ->
                                    Surface(
                                        shape = MaterialTheme.shapes.medium,
                                        tonalElevation = 2.dp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(host.name, style = MaterialTheme.typography.bodyMedium)
                                                Text(
                                                    host.address,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            Button(onClick = {
                                                ipAddress = host.address
                                                checkServer()
                                            }) {
                                                Text("Connect")
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        Text(
                            text = "Or enter your PC's IP address",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = ipAddress,
                            onValueChange = { ipAddress = it },
                            label = { Text("PC IP Address") },
                            placeholder = { Text("192.168.1.100") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { checkServer() }),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Make sure Apollo/Sunshine is running on your PC",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    PairingStep.CHECKING -> {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Checking $ipAddress ...", style = MaterialTheme.typography.bodyLarge)
                    }

                    PairingStep.SERVER_REACHABLE -> {
                        Icon(
                            Icons.Default.CheckCircle, contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Server Found!", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        serverInfo?.let { info ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(info.hostname, style = MaterialTheme.typography.bodyMedium)
                            info.gpuType?.let { gpu ->
                                Text("GPU: $gpu", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Not paired. Tap Pair to begin.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    PairingStep.SERVER_UNREACHABLE -> {
                        Icon(
                            Icons.Default.Error, contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Server Unreachable", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                        errorMessage?.let { msg ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        // Let user edit the IP and retry
                        OutlinedTextField(
                            value = ipAddress,
                            onValueChange = { ipAddress = it },
                            label = { Text("PC IP Address") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { checkServer() }),
                        )
                    }

                    PairingStep.SHOW_PIN, PairingStep.PAIRING -> {
                        Text("Enter this PIN on your PC:", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = pin,
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 12.sp,
                            ),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        if (step == PairingStep.PAIRING) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Waiting for pairing response...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    PairingStep.PAIRED -> {
                        Icon(
                            Icons.Default.CheckCircle, contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Paired!", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        Text("Ready to stream.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        serverInfo?.let { info ->
                            info.gpuType?.let { gpu ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("${info.hostname} — $gpu", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    PairingStep.PAIR_FAILED -> {
                        Icon(
                            Icons.Default.Error, contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Pairing Failed", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                        errorMessage?.let { msg ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons — use fillMaxWidth buttons for reliable XR touch targets
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (step) {
                    PairingStep.ENTER_IP -> {
                        Button(
                            onClick = { checkServer() },
                            enabled = ipAddress.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            Text("Check Server")
                        }
                    }
                    PairingStep.SERVER_UNREACHABLE, PairingStep.PAIR_FAILED -> {
                        Button(
                            onClick = { checkServer() },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            Text("Retry")
                        }
                    }
                    PairingStep.SERVER_REACHABLE -> {
                        Button(
                            onClick = { startPairing() },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            Text("Pair")
                        }
                    }
                    PairingStep.PAIRED -> {
                        Button(
                            onClick = {
                                onConnect(ipAddress)
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            Text("Connect & Stream")
                        }
                    }
                    else -> {}
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

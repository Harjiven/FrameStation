// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * About dialog displaying GPLv3 compliance information as required by Section 4(d).
 * Shows copyright notice, license information, and appropriate legal notices.
 */
@Composable
fun AboutDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        tonalElevation = 4.dp,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Title
            Text(
                text = "About FrameStation",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            
            // Copyright notice (GPLv3 Section 4 requirement)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "FrameStation",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Copyright (C) 2026 Harjiven Dodd",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "An Android XR Desktop Streaming & Multi-Panel Workspace App",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            
            // License information (GPLv3 Section 4 requirement)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "License",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "This program is free software: you can redistribute it and/or modify " +
                                "it under the terms of the GNU General Public License as published by " +
                                "the Free Software Foundation, either version 3 of the License, or " +
                                "(at your option) any later version.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "This program is distributed in the hope that it will be useful, " +
                                "but WITHOUT ANY WARRANTY; without even the implied warranty of " +
                                "MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the " +
                                "GNU General Public License for more details.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "You should have received a copy of the GNU General Public License " +
                                "along with this program. If not, see https://www.gnu.org/licenses/",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    
                    // Link to full license
                    Text(
                        text = "📄 View Full GPLv3 License",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable {
                                // License text is in COPYING file - show it in an external viewer or web
                                val licenseUrl = "https://www.gnu.org/licenses/gpl-3.0.html"
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(licenseUrl))
                                context.startActivity(intent)
                            }
                            .padding(vertical = 4.dp),
                    )
                }
            }
            
            // No warranty disclaimer (GPLv3 Section 4 requirement)
            Card(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "No Warranty",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "THIS PROGRAM COMES WITH ABSOLUTELY NO WARRANTY; for details see " +
                                "the GNU General Public License. This is free software, and you are " +
                                "welcome to redistribute it under certain conditions.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            
            // Source code availability notice (GPLv3 Section 6 requirement)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Source Code Availability",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "The complete source code for this program is available at:",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "https://github.com/harjiven/FrameStationXR",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            val repoUrl = "https://github.com/harjiven/FrameStationXR"
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(repoUrl))
                            context.startActivity(intent)
                        },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "The source code includes all files needed to generate, install, and " +
                                "modify this application, including the moonlight-core submodule.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            
            // Third-party notices
            Card(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Third-Party Components",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "This application includes modified code from:",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "• Moonlight Game Streaming (moonlight-android) - GPLv3\n" +
                                "  https://github.com/moonlight-stream/moonlight-android\n" +
                                "• moonlight-common-c - GPLv3\n" +
                                "  https://github.com/moonlight-stream/moonlight-common-c",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Other dependencies under permissive licenses (Apache 2.0, MIT, BSD):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "• BouncyCastle • OkHttp • jmDNS • jcodec\n" +
                                "• Jetpack Compose • Jetpack XR SDK • Android SDK",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            
            // Modified work notice (GPLv3 Section 5 requirement)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Modified Work Notice",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "This work has been modified from the original Moonlight Android " +
                                "project. Modifications include extraction of the streaming core library, " +
                                "removal of UI dependencies, and addition of XR-specific adaptations " +
                                "for spatial workspace functionality.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = "Date of modification: 2026",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    }
}

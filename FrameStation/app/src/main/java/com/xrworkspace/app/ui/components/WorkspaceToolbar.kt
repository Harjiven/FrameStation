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
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
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
import com.xrworkspace.app.model.Bookmark

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
    onStopStream: (() -> Unit)? = null,
    onShowKeyboard: (() -> Unit)? = null,
) {
    Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 4.dp) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
                FilterChip(
                    selected = false,
                    onClick = { onShowKeyboard?.invoke() },
                    label = { Text("Keyboard") },
                    leadingIcon = {
                        Icon(Icons.Default.Keyboard, contentDescription = null, modifier = Modifier.size(18.dp))
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

            // Bookmarks manager, pairing, settings
            VerticalDivider(
                modifier = Modifier.height(32.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            )
            FilterChip(
                selected = false,
                onClick = onBookmarksClick,
                label = { Text("Bookmarks") },
                leadingIcon = {
                    Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                },
            )
            FilterChip(
                selected = false,
                onClick = onPairingClick,
                label = { Text("Pair") },
                leadingIcon = {
                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                },
            )
            FilterChip(
                selected = false,
                onClick = onSettingsClick,
                label = { Text("Settings") },
                leadingIcon = {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                },
            )
        }
    }
}

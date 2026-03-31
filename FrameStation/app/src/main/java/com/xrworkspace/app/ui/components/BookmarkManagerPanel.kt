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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
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
import com.xrworkspace.app.model.Bookmark

@Composable
fun BookmarkManagerPanel(
    bookmarks: List<Bookmark>,
    openBookmarkIds: Set<String>,
    onToggleBookmark: (String) -> Unit,
    onAddBookmark: (String, String) -> Unit,
    onRemoveBookmark: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val newName = remember { mutableStateOf("") }
    val newUrl = remember { mutableStateOf("") }

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
                    text = "Bookmarks",
                    style = MaterialTheme.typography.headlineSmall,
                )
                OutlinedButton(onClick = onDismiss) {
                    Text("Close")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bookmark list
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(bookmarks, key = { it.id }) { bookmark ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Bookmark info
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = bookmark.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = bookmark.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        // Toggle open/close
                        FilterChip(
                            selected = bookmark.id in openBookmarkIds,
                            onClick = { onToggleBookmark(bookmark.id) },
                            label = {
                                Text(if (bookmark.id in openBookmarkIds) "Open" else "Closed")
                            },
                        )

                        // Delete
                        FilterChip(
                            selected = false,
                            onClick = { onRemoveBookmark(bookmark.id) },
                            label = { Text("Delete") },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                labelColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                        )
                    }
                }
            }

            // Add bookmark section
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            Text(
                text = "Add Bookmark",
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
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = newUrl.value,
                    onValueChange = { newUrl.value = it },
                    label = { Text("URL") },
                    singleLine = true,
                    modifier = Modifier.weight(2f),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Button(
                    onClick = {
                        if (newName.value.isNotBlank() && newUrl.value.isNotBlank()) {
                            val url = if (newUrl.value.startsWith("http://") ||
                                newUrl.value.startsWith("https://")
                            ) {
                                newUrl.value.trim()
                            } else {
                                "https://${newUrl.value.trim()}"
                            }
                            onAddBookmark(newName.value.trim(), url)
                            newName.value = ""
                            newUrl.value = ""
                        }
                    },
                    enabled = newName.value.isNotBlank() && newUrl.value.isNotBlank(),
                ) {
                    Text("Add")
                }
            }
        }
    }
}

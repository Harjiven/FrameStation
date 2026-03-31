// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xrworkspace.app.model.AudioChannels
import com.xrworkspace.app.model.AudioMode
import com.xrworkspace.app.model.AudioSettings

/**
 * Audio settings section for the Settings panel.
 * Provides controls for audio mode, channel configuration, and audio effects.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioSettingsSection(
    audioSettings: AudioSettings,
    onAudioSettingsChanged: (AudioSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Audio",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Audio mode dropdown
        val modeExpanded = remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = modeExpanded.value,
            onExpandedChange = { modeExpanded.value = it },
        ) {
            OutlinedTextField(
                value = audioSettings.audioMode.label,
                onValueChange = {},
                readOnly = true,
                label = { Text("Audio Mode") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modeExpanded.value) },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = modeExpanded.value,
                onDismissRequest = { modeExpanded.value = false },
            ) {
                AudioMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.label) },
                        onClick = {
                            onAudioSettingsChanged(audioSettings.copy(audioMode = mode))
                            modeExpanded.value = false
                        },
                    )
                }
            }
        }
        Text(
            text = when (audioSettings.audioMode) {
                AudioMode.STREAM_AUDIO -> "Audio from the Moonlight stream will play through the headset."
                AudioMode.MUTED -> "All stream audio is muted."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Channel configuration dropdown
        val channelExpanded = remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = channelExpanded.value,
            onExpandedChange = { channelExpanded.value = it },
        ) {
            OutlinedTextField(
                value = audioSettings.audioChannels.label,
                onValueChange = {},
                readOnly = true,
                label = { Text("Audio Channels") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = channelExpanded.value) },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = channelExpanded.value,
                onDismissRequest = { channelExpanded.value = false },
            ) {
                AudioChannels.entries.forEach { channels ->
                    DropdownMenuItem(
                        text = { Text(channels.label) },
                        onClick = {
                            onAudioSettingsChanged(audioSettings.copy(audioChannels = channels))
                            channelExpanded.value = false
                        },
                    )
                }
            }
        }
        Text(
            text = "Channel layout sent to the streaming server. Stereo is recommended for most headsets.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Audio effects toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Audio Effects",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "Enable audio post-processing effects (may increase latency).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = audioSettings.enableAudioFx,
                onCheckedChange = { enabled ->
                    onAudioSettingsChanged(audioSettings.copy(enableAudioFx = enabled))
                },
            )
        }
    }
}

package com.example.myapplication.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.example.myapplication.domain.model.Playlist

/**
 * Lets the user pick a playlist for the selected songs.
 *
 * Besides the existing playlists a new one can be created on the spot, and the songs can be
 * sent to the queue instead of a playlist.
 */
@Composable
fun PlaylistPickerDialog(
    playlists: List<Playlist>,
    songCount: Int,
    onPickPlaylist: (Long) -> Unit,
    onAddToQueue: () -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = {
            Text(if (songCount == 1) "Add to playlist" else "Add $songCount songs to playlist")
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("New playlist") },
                )
                TextButton(
                    onClick = {
                        val trimmed = name.trim()
                        if (trimmed.isNotEmpty()) {
                            onCreatePlaylist(trimmed)
                            name = ""
                        }
                    },
                    enabled = name.isNotBlank(),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Text("  Create playlist")
                }
                HorizontalDivider()
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(playlists, key = { it.id }) { playlist ->
                        TextButton(
                            onClick = { onPickPlaylist(playlist.id) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = playlist.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
                HorizontalDivider()
                TextButton(
                    onClick = onAddToQueue,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null)
                    Text("  Add to queue")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

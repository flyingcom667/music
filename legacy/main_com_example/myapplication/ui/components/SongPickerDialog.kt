package com.example.myapplication.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.domain.SongQueries
import com.example.myapplication.domain.model.Song
import com.example.myapplication.ui.MetadataTextDirection

/**
 * Lets the user pick songs out of the library.
 *
 * It is used by the button that adds songs to a playlist; several songs can be picked at once.
 */
@Composable
fun SongPickerDialog(
    songs: List<Song>,
    onConfirm: (List<Song>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    val visible = remember(songs, query) { SongQueries.search(songs, query) }

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text("Add songs") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search") },
                )
                Spacer(Modifier.width(4.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(visible, key = { it.mediaId }) { song ->
                        val isSelected = song.mediaId in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (isSelected) {
                                        selected - song.mediaId
                                    } else {
                                        selected + song.mediaId
                                    }
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SongArtwork(
                                mediaId = song.mediaId,
                                albumId = song.albumId,
                                modifier = Modifier.size(40.dp),
                                iconSize = 18.dp,
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = song.artist.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        textDirection = MetadataTextDirection,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    selected = if (checked) {
                                        selected + song.mediaId
                                    } else {
                                        selected - song.mediaId
                                    }
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(songs.filter { it.mediaId in selected }) },
                enabled = selected.isNotEmpty(),
            ) {
                Text(if (selected.size == 1) "Add song" else "Add ${selected.size} songs")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

package com.example.myapplication.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.domain.model.Song
import com.example.myapplication.domain.model.SongMetadata
import com.example.myapplication.ui.MetadataTextDirection
import com.example.myapplication.ui.viewmodel.SongActionsViewModel
import com.example.myapplication.util.Formatters

@Composable
fun ActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongActionsSheet(
    state: SongActionsViewModel.DetailsState?,
    onDismiss: () -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onShare: (Song) -> Unit,
    onDeleteFile: (Song) -> Unit,
    onEditMetadata: (Song) -> Unit,
    onResetMetadata: (Song) -> Unit,
    onRemove: (Song) -> Unit,
) {
    if (state == null) return
    val song = state.song
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SongArtwork(
                    mediaId = song.mediaId,
                    albumId = song.albumId,
                    modifier = Modifier.size(56.dp),
                    cornerRadius = 12.dp,
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium.copy(textDirection = MetadataTextDirection),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOfNotNull(song.artist, song.album).joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall.copy(textDirection = MetadataTextDirection),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            ActionRow(
                icon = if (song.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                label = if (song.isFavorite) "Remove from favourites" else "Add to favourites",
                tint = if (song.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                onClick = { onToggleFavorite(song) },
            )
            ActionRow(
                icon = Icons.Rounded.PlaylistPlay,
                label = "Play next",
                onClick = { onPlayNext(song) },
            )
            ActionRow(
                icon = Icons.Rounded.QueueMusic,
                label = "Add to queue",
                onClick = { onAddToQueue(song) },
            )
            ActionRow(
                icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                label = "Add to playlist",
                onClick = { onAddToPlaylist(song) },
            )
            ActionRow(
                icon = Icons.Rounded.Share,
                label = "Share file",
                onClick = { onShare(song) },
            )
            ActionRow(
                icon = Icons.Rounded.Edit,
                label = "Edit metadata",
                onClick = { onEditMetadata(song) },
            )
            if (song.hasOverrides) {
                ActionRow(
                    icon = Icons.Rounded.Restore,
                    label = "Revert to file metadata",
                    onClick = { onResetMetadata(song) },
                )
            }
            ActionRow(
                icon = Icons.Rounded.DeleteForever,
                label = "Delete file from device",
                tint = MaterialTheme.colorScheme.error,
                onClick = { onDeleteFile(song) },
            )
            ActionRow(
                icon = Icons.Rounded.DeleteOutline,
                label = "Remove from library",
                tint = MaterialTheme.colorScheme.error,
                onClick = { onRemove(song) },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            DetailsSection(state)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DetailsSection(state: SongActionsViewModel.DetailsState) {
    val song = state.song
    val info = state.audioInfo
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Details",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        DetailLine("Album", song.album)
        DetailLine("Album artist", song.albumArtist)
        DetailLine("Track", song.trackNumber?.toString())
        DetailLine("Year", song.year?.toString())
        DetailLine("Genre", song.genre)
        DetailLine("Duration", Formatters.duration(song.durationMs))
        DetailLine("Size", Formatters.fileSize(song.sizeBytes))
        DetailLine(
            "Format",
            info?.mimeType?.substringAfter('/')?.uppercase()
                ?: song.mimeType?.substringAfter('/')?.uppercase(),
        )
        DetailLine("Bitrate", info?.bitrate?.takeIf { it > 0 }?.let { Formatters.bitrate(it) })
        DetailLine("Sample rate", info?.sampleRate?.takeIf { it > 0 }?.let { Formatters.sampleRate(it) })
        DetailLine(
            "Channels",
            info?.channels?.takeIf { it > 0 }?.let { if (it == 1) "Mono" else "$it channels" },
        )
        DetailLine("File name", song.displayName)
        DetailLine("Folder", song.folder)
        DetailLine("Added", Formatters.date(song.dateAdded))
        DetailLine("Play count", song.playCount.toString())
        DetailLine(
            "Last played",
            if (song.lastPlayedAt > 0L) Formatters.dateTime(song.lastPlayedAt) else null,
        )
        if (song.hasOverrides) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "File metadata: ${state.base?.title ?: "-"} • ${state.base?.artist ?: "-"}",
                style = MaterialTheme.typography.labelSmall.copy(textDirection = MetadataTextDirection),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(textDirection = MetadataTextDirection),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Editor for the app-local metadata override. */
@Composable
fun EditMetadataDialog(
    song: Song,
    initial: SongMetadata,
    onDismiss: () -> Unit,
    onSave: (SongMetadata) -> Unit,
) {
    var title by remember(song.mediaId) { mutableStateOf(initial.title.orEmpty()) }
    var artist by remember(song.mediaId) { mutableStateOf(initial.artist.orEmpty()) }
    var album by remember(song.mediaId) { mutableStateOf(initial.album.orEmpty()) }
    var albumArtist by remember(song.mediaId) { mutableStateOf(initial.albumArtist.orEmpty()) }
    var genre by remember(song.mediaId) { mutableStateOf(initial.genre.orEmpty()) }
    var track by remember(song.mediaId) { mutableStateOf(initial.trackNumber?.toString().orEmpty()) }
    var year by remember(song.mediaId) { mutableStateOf(initial.year?.toString().orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit metadata") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Changes are stored inside this app only; the audio file is never modified.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = MetadataTextDirection),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Artist") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = MetadataTextDirection),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = album,
                    onValueChange = { album = it },
                    label = { Text("Album") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = MetadataTextDirection),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = albumArtist,
                    onValueChange = { albumArtist = it },
                    label = { Text("Album artist") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = MetadataTextDirection),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = genre,
                    onValueChange = { genre = it },
                    label = { Text("Genre") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = MetadataTextDirection),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = track,
                        onValueChange = { value -> track = value.filter { it.isDigit() }.take(3) },
                        label = { Text("Track") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = year,
                        onValueChange = { value -> year = value.filter { it.isDigit() }.take(4) },
                        label = { Text("Year") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    SongMetadata(
                        title = title.trim(),
                        artist = artist.trim().ifBlank { null },
                        album = album.trim().ifBlank { null },
                        albumArtist = albumArtist.trim().ifBlank { null },
                        trackNumber = track.toIntOrNull(),
                        genre = genre.trim().ifBlank { null },
                        year = year.toIntOrNull(),
                    ),
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Simple dialog used to create or rename a playlist. */
@Composable
fun PlaylistNameDialog(
    title: String,
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Playlist name") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = MetadataTextDirection),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun LabeledValue(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(textDirection = MetadataTextDirection),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

package com.example.myapplication.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.PlaylistRemove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.domain.model.Playlist
import com.example.myapplication.domain.model.Song
import com.example.myapplication.ui.MetadataTextDirection
import com.example.myapplication.ui.components.EmptyState
import com.example.myapplication.ui.components.PlayAllHeader
import com.example.myapplication.ui.components.SelectionActionBar
import com.example.myapplication.ui.components.SongArtwork
import com.example.myapplication.ui.components.SongRow
import com.example.myapplication.ui.components.SongSelectionActions
import com.example.myapplication.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    playlists: List<Playlist>,
    onOpen: (Playlist) -> Unit,
    onCreate: () -> Unit,
    onAddSongs: () -> Unit,
    onRename: (Playlist) -> Unit,
    onDelete: (Playlist) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Column {
                        Text("Playlists", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "${playlists.size} playlists",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
            if (playlists.isEmpty()) {
                EmptyState(
                    title = "No playlists yet",
                    description = "Create a playlist and add songs from anywhere in the app.",
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    items(playlists, key = { it.id }) { playlist ->
                        PlaylistRow(
                            playlist = playlist,
                            onClick = { onOpen(playlist) },
                            onRename = { onRename(playlist) },
                            onDelete = { onDelete(playlist) },
                        )
                    }
                }
            }
        }
        // The button that adds songs to a playlist floats above the button that creates one.
        SmallFloatingActionButton(
            onClick = onAddSongs,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 88.dp),
        ) {
            Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = "Add songs to a playlist")
        }
        ExtendedFloatingActionButton(
            onClick = onCreate,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
            text = { Text("New playlist") },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistRow(
    playlist: Playlist,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = { menuExpanded = true })
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SongArtwork(
            mediaId = playlist.artworkMediaId ?: 0L,
            albumId = 0L,
            modifier = Modifier.size(56.dp),
            cornerRadius = 12.dp,
            iconSize = 24.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyLarge.copy(textDirection = MetadataTextDirection),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = Formatters.songCount(playlist.songCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "Playlist options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = {
                        menuExpanded = false
                        onRename()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlist: Playlist?,
    songs: List<Song>,
    nowPlayingId: Long,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onAddAllToQueue: () -> Unit,
    onSongClick: (Song) -> Unit,
    onRemoveSong: (Song) -> Unit,
    onRemoveSelected: (List<Song>) -> Unit,
    selection: SongSelectionActions = SongSelectionActions(),
    modifier: Modifier = Modifier,
) {
    var songToRemove by remember { mutableStateOf<Song?>(null) }
    val isSelecting = selection.isActive

    Column(modifier = modifier.fillMaxSize()) {
        if (isSelecting) {
            SelectionActionBar(
                selectedCount = selection.count,
                onClear = selection.onClear,
                onAddToPlaylist = selection.onAddToPlaylist,
                onAddToQueue = selection.onAddToQueue,
                onShare = selection.onShare,
                onDeleteFiles = selection.onDeleteFiles,
                onRemoveFromLibrary = selection.onRemoveFromLibrary,
                onSelectAll = selection.onSelectAll,
                extraAction = {
                    IconButton(onClick = {
                        onRemoveSelected(songs.filter { it.mediaId in selection.selectedIds })
                        selection.onClear()
                    }) {
                        Icon(
                            Icons.Rounded.PlaylistRemove,
                            contentDescription = "Remove from playlist",
                        )
                    }
                },
            )
        } else {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = playlist?.name ?: "Playlist",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = Formatters.songCount(songs.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(onClick = onAddAllToQueue, enabled = songs.isNotEmpty()) {
                    Icon(Icons.Rounded.PlaylistPlay, contentDescription = "Add all to queue")
                }
            },
        )
        }

        if (songs.isEmpty()) {
            EmptyState(
                title = "Empty playlist",
                description = "Long press a song anywhere in the app to add it here.",
            )
            return@Column
        }

        PlayAllHeader(
            primaryLabel = "Play",
            secondaryLabel = "Shuffle",
            onPlay = onPlayAll,
            onShuffle = onShuffle,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            items(songs, key = { it.mediaId }) { song ->
                SongRow(
                    song = song,
                    isPlaying = song.mediaId == nowPlayingId && isPlaying,
                    selectionMode = isSelecting,
                    selected = song.mediaId in selection.selectedIds,
                    onToggleSelected = { selection.onToggle(song.mediaId) },
                    onClick = {
                        if (isSelecting) selection.onToggle(song.mediaId)
                        else onSongClick(song)
                    },
                    onLongClick = {
                        if (isSelecting) selection.onToggle(song.mediaId)
                        else selection.onStart(song.mediaId)
                    },
                    onMoreClick = { songToRemove = song },
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }

    songToRemove?.let { song ->
        AlertDialog(
            onDismissRequest = { songToRemove = null },
            title = { Text("Remove from playlist?") },
            text = { Text("\"${song.title}\" will be removed from this playlist. The file stays on your device.") },
            confirmButton = {
                TextButton(onClick = {
                    onRemoveSong(song)
                    songToRemove = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { songToRemove = null }) { Text("Cancel") }
            },
        )
    }
}

package com.tidalmusic.player.ui.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tidalmusic.player.R
import com.tidalmusic.player.data.repository.PlaylistSummary
import com.tidalmusic.player.domain.model.Song
import com.tidalmusic.player.ui.components.TidalAlertDialog
import com.tidalmusic.player.ui.components.AlbumArtwork
import com.tidalmusic.player.ui.components.EmptyState
import com.tidalmusic.player.ui.components.LoadingState
import com.tidalmusic.player.ui.components.TidalTextButton

/**
 * The Playlists overview: a create action and the list of user playlists.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PlaylistsScreen(
    playlists: List<PlaylistSummary>,
    isLoading: Boolean,
    coverSongs: (PlaylistSummary) -> List<Song>,
    onOpenPlaylist: (Long) -> Unit,
    onCreatePlaylist: () -> Unit,
    onRenamePlaylist: (PlaylistSummary) -> Unit,
    onDeletePlaylist: (PlaylistSummary) -> Unit,
    onSharePlaylist: (PlaylistSummary) -> Unit,
    onOpenFolders: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.playlists_title)) },
                actions = {
                    // The mini file manager lives one tap away from the playlists.
                    IconButton(onClick = onOpenFolders, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Folder,
                            contentDescription = stringResource(R.string.folders_title),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        floatingActionButton = {
            CreatePlaylistButton(onClick = onCreatePlaylist)
        },
    ) { contentPadding ->
        when {
            isLoading -> LoadingState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            )

            playlists.isEmpty() -> EmptyState(
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                title = stringResource(R.string.playlists_empty_title),
                body = stringResource(R.string.playlists_empty_body),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            )

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 16.dp,
                    bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items = playlists, key = { it.id }) { playlist ->
                    PlaylistCard(
                        modifier = Modifier.animateItem(),
                        playlist = playlist,
                        coverSongs = coverSongs(playlist),
                        onClick = { onOpenPlaylist(playlist.id) },
                        onRename = { onRenamePlaylist(playlist) },
                        onDelete = { onDeletePlaylist(playlist) },
                        onShare = { onSharePlaylist(playlist) },
                    )
                }
            }
        }
    }
}

/** A playlist row: representative artwork, the name and the track count. */
@Composable
private fun PlaylistCard(
    playlist: PlaylistSummary,
    coverSongs: List<Song>,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val totalDuration = coverSongs.sumOf { it.durationMs }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PlaylistArtwork(songs = coverSongs)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.playlist_track_count, playlist.trackCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (totalDuration > 0L) {
                    Text(
                        text = stringResource(
                            R.string.playlist_duration,
                            formatLongDuration(totalDuration),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.action_more_options),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_share)) },
                        leadingIcon = { Icon(Icons.Rounded.Share, null) },
                        onClick = {
                            menuOpen = false
                            onShare()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_rename)) },
                        leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, null) },
                        onClick = {
                            menuOpen = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.action_delete),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

/** Playlist artwork: the first track's cover, or a music note when the playlist is empty. */
@Composable
private fun PlaylistArtwork(songs: List<Song>) {
    val cover = songs.firstOrNull()
    if (cover != null) {
        AlbumArtwork(song = cover, size = 56.dp, cornerRadius = 12.dp)
    } else {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(12.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                contentDescription = stringResource(R.string.playlist_artwork_fallback),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The create/rename dialog.
 *
 * The confirm button is disabled while the name is blank and the dialog shows progress while
 * the playlist is being written, so a double tap cannot create two playlists.
 */
@Composable
fun PlaylistNameDialog(
    title: String,
    initialName: String = "",
    initialDescription: String = "",
    isSaving: Boolean = false,
    confirmLabel: Int = R.string.action_create,
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String?) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }
    val trimmed = name.trim()

    TidalAlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.playlist_name_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.playlist_description_label)) },
                    shape = RoundedCornerShape(14.dp),
                )
            }
        },
        confirmButton = {
            TidalTextButton(
                onClick = { onConfirm(trimmed, description.trim().takeIf { it.isNotEmpty() }) },
                enabled = trimmed.isNotEmpty() && !isSaving,
            ) {
                Text(stringResource(confirmLabel))
            }
        },
        dismissButton = {
            TidalTextButton(onClick = onDismiss, enabled = !isSaving) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/** The delete confirmation. The audio files are never touched. */
@Composable
fun DeletePlaylistDialog(
    playlistName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    TidalAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.playlist_delete_title, playlistName)) },
        text = { Text(stringResource(R.string.playlist_delete_body)) },
        confirmButton = {
            TidalTextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.action_delete))
            }
        },
        dismissButton = {
            TidalTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/** The "create playlist" button used in the playlists screen. */
@Composable
fun CreatePlaylistButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
        text = { Text(stringResource(R.string.playlists_create)) },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    )
}

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.tidalmusic.player.domain.model.Song
import com.tidalmusic.player.ui.components.AlbumArtwork
import com.tidalmusic.player.ui.components.EmptyState
import com.tidalmusic.player.ui.components.HintChip
import com.tidalmusic.player.ui.components.SongRow
import com.tidalmusic.player.ui.components.TidalTextButton
import com.tidalmusic.player.util.TimeFormat

/**
 * The playlist detail screen.
 *
 * The track list can be reordered with the up/down buttons in the overflow menu of every
 * row; the new order is persisted immediately so the queue always follows what is shown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistName: String,
    songs: List<Song>,
    isCurrentSong: (Long) -> Boolean,
    isPlaying: Boolean,
    isFavorite: (Long) -> Boolean,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onAddSongs: () -> Unit,
    onPlaySong: (Int) -> Unit,
    onRemoveSong: (Long) -> Unit,
    onMoveSong: (Int, Int) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onSongOverflow: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val totalDuration = songs.sumOf { it.durationMs }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = playlistName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(48.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = stringResource(R.string.action_more_options),
                            )
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PlaylistHeroArtwork(songs)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlistName,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.playlist_track_count, songs.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (totalDuration > 0L) {
                        Text(
                            text = stringResource(
                                R.string.playlist_duration,
                                TimeFormat.longDuration(totalDuration),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = onPlayAll, enabled = songs.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.action_play),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                FilledTonalButton(onClick = onShuffle, enabled = songs.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.action_shuffle),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                TidalTextButton(onClick = onAddSongs) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.playlist_add_songs),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            if (songs.isEmpty()) {
                EmptyState(
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    title = stringResource(R.string.playlist_empty_title),
                    body = stringResource(R.string.playlist_empty_body),
                    actionLabel = stringResource(R.string.playlist_add_songs),
                    onAction = onAddSongs,
                )
            } else {
                HintChip(
                    text = stringResource(R.string.playlist_reorder_hint),
                    modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 4.dp),
                )
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    itemsIndexed(items = songs, key = { _, song -> song.id }) { index, song ->
                        SongRow(
                            song = song,
                            isCurrent = isCurrentSong(song.id),
                            isPlaying = isPlaying,
                            isFavorite = isFavorite(song.id),
                            showArtwork = true,
                            compact = false,
                            onPlay = { onPlaySong(index) },
                            onOverflow = { onSongOverflow(song) },
                            overflowLabel = stringResource(R.string.action_more_options),
                            trailingContent = {
                                Row {
                                    TidalTextButton(
                                        onClick = { onMoveSong(index, index - 1) },
                                        enabled = index > 0,
                                    ) {
                                        Text("↑")
                                    }
                                    TidalTextButton(
                                        onClick = { onMoveSong(index, index + 1) },
                                        enabled = index < songs.lastIndex,
                                    ) {
                                        Text("↓")
                                    }
                                    TidalTextButton(onClick = { onRemoveSong(song.id) }) {
                                        Text(stringResource(R.string.action_remove_from_playlist))
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistHeroArtwork(songs: List<Song>) {
    val cover = songs.firstOrNull()
    if (cover != null) {
        AlbumArtwork(song = cover, size = 96.dp, cornerRadius = 20.dp)
    } else {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(20.dp),
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

package com.example.myapplication.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myapplication.domain.model.Song
import com.example.myapplication.ui.components.EmptyState
import com.example.myapplication.ui.components.PlayAllHeader
import com.example.myapplication.ui.components.SelectionActionBar
import com.example.myapplication.ui.components.SongRow
import com.example.myapplication.ui.components.SongSelectionActions
import com.example.myapplication.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    favorites: List<Song>,
    nowPlayingId: Long,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onSongClick: (Song) -> Unit,
    onSongMore: (Song) -> Unit,
    selection: SongSelectionActions = SongSelectionActions(),
    modifier: Modifier = Modifier,
) {
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
            )
        } else {
        TopAppBar(
            title = {
                Column {
                    Text("Favourites", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = Formatters.songCount(favorites.size),
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
        )
        }

        if (favorites.isEmpty()) {
            EmptyState(
                title = "No favourites yet",
                description = "Tap the heart on a song, or long press a song and add it to favourites.",
            )
        } else {
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
            ) {
                itemsIndexed(favorites, key = { _, song -> song.mediaId }) { index, song ->
                    SongRow(
                        song = song,
                        index = index + 1,
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
                        onMoreClick = { onSongMore(song) },
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}

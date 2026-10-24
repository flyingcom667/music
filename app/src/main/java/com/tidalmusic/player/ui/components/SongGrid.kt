package com.tidalmusic.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tidalmusic.player.R
import com.tidalmusic.player.domain.model.Song

/**
 * The cover-grid alternative to the song row list.
 *
 * Every screen that shows songs can switch between [SongRow]s and this grid; the choice
 * is persisted per screen. Tap plays, long-press toggles selection (mirroring the list
 * behaviour), and the corner button opens the song action sheet.
 */
@Composable
fun SongGrid(
    songs: List<Song>,
    currentSongId: Long?,
    isPlaying: Boolean,
    isSelected: (Long) -> Boolean,
    selectionMode: Boolean,
    onPlay: (Int) -> Unit,
    onToggleSelect: (Long) -> Unit,
    onEnterSelection: () -> Unit,
    onOverflow: (Song) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        itemsIndexed(items = songs, key = { _, song -> song.id }) { index, song ->
            SongGridCell(
                song = song,
                isCurrent = song.id == currentSongId,
                isPlaying = isPlaying,
                isSelected = isSelected(song.id),
                selectionMode = selectionMode,
                onPlay = { onPlay(index) },
                onToggleSelect = { onToggleSelect(song.id) },
                onEnterSelection = onEnterSelection,
                onOverflow = { onOverflow(song) },
            )
        }
    }
}

@Composable
private fun SongGridCell(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isSelected: Boolean,
    selectionMode: Boolean,
    onPlay: () -> Unit,
    onToggleSelect: () -> Unit,
    onEnterSelection: () -> Unit,
    onOverflow: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (selectionMode) onToggleSelect() else onPlay() },
                onLongClick = { if (selectionMode) onToggleSelect() else onEnterSelection() },
            ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box {
            AlbumArtwork(
                song = song,
                size = 120.dp,
                cornerRadius = 14.dp,
                fillWidth = true,
            )
            if (isCurrent && isPlaying) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 5.dp, vertical = 3.dp),
                ) {
                    NowPlayingBars()
                }
            }
            if (selectionMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(color = MaterialTheme.colorScheme.surface, shape = CircleShape),
                ) {
                    Icon(
                        imageVector = if (isSelected) {
                            Icons.Rounded.CheckCircle
                        } else {
                            Icons.Rounded.RadioButtonUnchecked
                        },
                        contentDescription = null,
                        tint = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier
                            .padding(2.dp)
                            .size(22.dp),
                    )
                }
            }
        }
        Text(
            text = song.title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = song.artist.ifBlank { "—" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = onOverflow, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.action_more_options),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

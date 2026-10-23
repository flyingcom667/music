package com.tidalmusic.player.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.tidalmusic.player.domain.model.QueueItem
import com.tidalmusic.player.ui.components.AlbumArtwork
import com.tidalmusic.player.ui.components.TidalTextButton
import com.tidalmusic.player.ui.components.EmptyState

/**
 * The playback queue.
 *
 * Every change is applied to the real Media3 timeline, so the sheet and the audio can never
 * disagree. The currently playing track is marked and the upcoming tracks can be reordered or
 * removed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    queue: List<QueueItem>,
    onDismiss: () -> Unit,
    onPlayItem: (Int) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onMoveItem: (Int, Int) -> Unit,
    onClearQueue: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirmClear by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.queue_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (queue.isNotEmpty()) {
                    IconButton(
                        onClick = { confirmClear = true },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = stringResource(R.string.queue_clear),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (queue.isEmpty()) {
                EmptyState(
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    title = stringResource(R.string.queue_empty_title),
                    body = stringResource(R.string.queue_empty_body),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    itemsIndexed(
                        items = queue,
                        key = { index, item -> "${item.index}-${item.song.id}" },
                    ) { index, item ->
                        QueueTrackRow(
                            item = item,
                            canMoveUp = index > 0,
                            canMoveDown = index < queue.lastIndex,
                            onPlay = { onPlayItem(index) },
                            onRemove = { onRemoveItem(index) },
                            onMoveUp = { onMoveItem(index, index - 1) },
                            onMoveDown = { onMoveItem(index, index + 1) },
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.queue_clear_title)) },
            text = { Text(stringResource(R.string.queue_clear_body)) },
            confirmButton = {
                TidalTextButton(
                    onClick = {
                        confirmClear = false
                        onClearQueue()
                    },
                ) {
                    Text(stringResource(R.string.queue_clear))
                }
            },
            dismissButton = {
                TidalTextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/** One row of the queue: the track, its position and the actions that apply to it. */
@Composable
private fun QueueTrackRow(
    item: QueueItem,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AlbumArtwork(song = item.song, size = 44.dp, cornerRadius = 10.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.song.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (item.isCurrent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.song.artist.ifBlank { "—" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowUp,
                contentDescription = stringResource(R.string.queue_move_up),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = stringResource(R.string.queue_move_down),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.queue_remove),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

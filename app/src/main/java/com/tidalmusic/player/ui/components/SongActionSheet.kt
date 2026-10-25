package com.tidalmusic.player.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tidalmusic.player.R
import com.tidalmusic.player.domain.model.Song

/**
 * The song actions sheet.
 *
 * Only actions that actually work for the selected track are shown: the caller decides which
 * of them apply (for example, "Remove from playlist" only inside a playlist).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongActionSheet(
    song: Song,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onPlayNow: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDetails: () -> Unit,
    onEditMetadata: () -> Unit,
    onEditAudio: () -> Unit,
    onShare: () -> Unit,
    onHide: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AlbumArtwork(song = song, size = 56.dp, cornerRadius = 12.dp)
                Column(modifier = Modifier.padding(start = 14.dp)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
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
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            ActionRow(
                icon = { Icon(Icons.Rounded.PlayArrow, null) },
                label = stringResource(R.string.action_play_now),
                onClick = onPlayNow,
            )
            ActionRow(
                icon = { Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null) },
                label = stringResource(R.string.action_play_next),
                onClick = onPlayNext,
            )
            ActionRow(
                icon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null) },
                label = stringResource(R.string.action_add_to_queue),
                onClick = onAddToQueue,
            )
            ActionRow(
                icon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) },
                label = stringResource(R.string.action_add_to_playlist),
                onClick = onAddToPlaylist,
            )
            ActionRow(
                icon = {
                    Icon(
                        imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = null,
                        tint = if (isFavorite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                },
                label = stringResource(
                    if (isFavorite) R.string.action_unfavorite else R.string.action_favorite,
                ),
                onClick = onToggleFavorite,
            )
            ActionRow(
                icon = { Icon(Icons.Rounded.Info, null) },
                label = stringResource(R.string.action_song_info),
                onClick = onDetails,
            )
            ActionRow(
                icon = { Icon(Icons.Rounded.Edit, null) },
                label = stringResource(R.string.action_edit_metadata),
                onClick = onEditMetadata,
            )
            ActionRow(
                icon = { Icon(Icons.Rounded.ContentCut, null) },
                label = stringResource(R.string.action_edit_audio),
                onClick = onEditAudio,
            )
            ActionRow(
                icon = { Icon(Icons.Rounded.Share, null) },
                label = stringResource(R.string.action_share),
                onClick = onShare,
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            ActionRow(
                icon = { Icon(Icons.Rounded.VisibilityOff, null) },
                label = stringResource(R.string.action_hide_from_library),
                onClick = onHide,
            )
            ActionRow(
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                label = stringResource(R.string.action_delete_file),
                onClick = onDelete,
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun ActionRow(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    ListItem(
        headlineContent = { Text(label, style = MaterialTheme.typography.bodyLarge, color = tint) },
        leadingContent = icon,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    )
}

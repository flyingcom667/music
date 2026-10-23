package com.tidalmusic.player.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tidalmusic.player.R

/**
 * The secondary player controls.
 *
 * The main transport row stays uncluttered: everything that is not play/pause/skip lives in
 * this sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerControlsSheet(
    playbackSpeed: Float,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onPlaybackSpeed: (Float) -> Unit,
    onSleepTimer: () -> Unit,
    onOpenQueue: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onSongInfo: () -> Unit,
    onShare: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.player_now_playing),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
            )

            SheetRow(
                icon = { Icon(Icons.Rounded.Timer, null) },
                label = stringResource(R.string.action_sleep_timer),
                onClick = onSleepTimer,
            )
            SheetRow(
                icon = { Icon(Icons.Rounded.Speed, null) },
                label = stringResource(R.string.action_playback_speed) +
                    " " + formatSpeed(playbackSpeed),
                onClick = { onPlaybackSpeed(nextSpeed(playbackSpeed)) },
            )
            SheetRow(
                icon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null) },
                label = stringResource(R.string.action_queue),
                onClick = onOpenQueue,
            )
            SheetRow(
                icon = {
                    Icon(
                        imageVector = if (isFavorite) {
                            Icons.Rounded.Favorite
                        } else {
                            Icons.Rounded.FavoriteBorder
                        },
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
            SheetRow(
                icon = { Icon(Icons.Rounded.PlaylistAdd, null) },
                label = stringResource(R.string.action_add_to_playlist),
                onClick = onAddToPlaylist,
            )
            SheetRow(
                icon = { Icon(Icons.Rounded.Info, null) },
                label = stringResource(R.string.action_song_info),
                onClick = onSongInfo,
            )
            SheetRow(
                icon = { Icon(Icons.Rounded.Share, null) },
                label = stringResource(R.string.action_share),
                onClick = onShare,
            )
        }
    }
}

/** The playback speeds the player offers, in order. */
private val SPEEDS = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

internal fun nextSpeed(current: Float): Float {
    val index = SPEEDS.indexOfFirst { it == current }
    return if (index < 0) 1.0f else SPEEDS[(index + 1) % SPEEDS.size]
}

internal fun formatSpeed(speed: Float): String {
    val text = if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()
    return "$text×"
}

@Composable
private fun SheetRow(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(label, style = MaterialTheme.typography.bodyLarge) },
        leadingContent = icon,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    )
}

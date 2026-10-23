package com.tidalmusic.player.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
 * The persistent compact player above the navigation bar.
 *
 * The song area opens the full player; the transport buttons act immediately and never
 * navigate. The progress strip is a two-pixel indicator, not a control, so tapping the strip
 * expands the player instead of seeking by accident.
 *
 * [progress] is a function rather than a value on purpose: it is read inside the progress
 * indicator's draw lambda, so the twice-per-second position tick repaints two pixels and
 * never recomposes this composable or the shell that hosts it.
 */
@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    progress: () -> Float,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp)),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
    ) {
        Column {
            LinearProgressIndicator(
                progress = { progress().coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .size(width = 0.dp, height = 2.dp),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onExpand)
                    .padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AlbumArtwork(song = song, size = 44.dp, cornerRadius = 10.dp)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleSmall,
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
                IconButton(onClick = onPlayPause, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(
                            if (isPlaying) R.string.action_pause else R.string.action_play,
                        ),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = stringResource(R.string.action_next),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

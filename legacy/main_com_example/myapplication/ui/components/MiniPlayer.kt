package com.example.myapplication.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.playback.PlaybackProgress
import com.example.myapplication.playback.PlaybackUiState
import com.example.myapplication.ui.MetadataTextDirection
import com.example.myapplication.ui.mirroredInRtl

/** Compact player shown above the navigation bar on every screen. */
@Composable
fun MiniPlayer(
    state: PlaybackUiState,
    progress: PlaybackProgress,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.hasCurrent) return
    val fraction = if (progress.durationMs > 0L) {
        (progress.positionMs.toFloat() / progress.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onExpand),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
    ) {
        Column {
            Row(
                modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AnimatedArtwork(
                    mediaId = state.mediaId,
                    albumId = state.albumId,
                    isPlaying = state.isPlaying,
                    modifier = Modifier.size(44.dp),
                    cornerRadius = 10.dp,
                    iconSize = 18.dp,
                    showBadge = false,
                    // The mini player is visible on every screen, so it does not pulse: the
                    // animation would keep the screen redrawing all the time.
                    pulse = false,
                )
                Spacer(Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.bodyMedium.copy(textDirection = MetadataTextDirection),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = state.artist ?: "",
                        style = MaterialTheme.typography.bodySmall.copy(textDirection = MetadataTextDirection),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onPlayPause) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Next",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.mirroredInRtl(),
                    )
                }
                IconButton(onClick = onStop) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Stop and close player",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                drawStopIndicator = {},
            )
        }
    }
}

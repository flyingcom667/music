package com.tidalmusic.player.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tidalmusic.player.domain.model.Song
import com.tidalmusic.player.util.TimeFormat

/**
 * One song in a list.
 *
 * The row is the tap target for playback; the artwork, title, artist and duration are one
 * merged accessibility node while the overflow button stays separately focusable, which is
 * what TalkBack users expect from a list of songs.
 */
@Composable
fun SongRow(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    showArtwork: Boolean,
    compact: Boolean,
    onPlay: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onOverflow: () -> Unit,
    modifier: Modifier = Modifier,
    overflowLabel: String,
    contentDescription: String? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val rowHeight = if (compact) 60.dp else 76.dp
    val artworkSize = if (compact) 44.dp else 52.dp
    val titleColor = if (isCurrent) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .height(rowHeight)
            .combinedClickable(
                onClick = onPlay,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leadingContent?.invoke()

        if (showArtwork) {
            AlbumArtwork(
                song = song,
                size = artworkSize,
                cornerRadius = 10.dp,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .then(
                    if (contentDescription == null) {
                        Modifier
                    } else {
                        Modifier.clearAndSetSemantics {
                            this.contentDescription = contentDescription
                        }
                    },
                ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (isCurrent && isPlaying) {
                    NowPlayingBars()
                }
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (isFavorite) {
                    Icon(
                        imageVector = Icons.Rounded.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Text(
                text = song.artist.ifBlank { "—" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (trailingContent != null) {
            trailingContent()
        } else if (song.durationMs > 0L) {
            Text(
                text = TimeFormat.duration(song.durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        IconButton(onClick = onOverflow, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = overflowLabel,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

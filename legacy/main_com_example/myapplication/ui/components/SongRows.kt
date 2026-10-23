@file:OptIn(ExperimentalFoundationApi::class)

package com.example.myapplication.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.domain.model.Song
import com.example.myapplication.domain.model.SongListLayout
import com.example.myapplication.ui.MetadataTextDirection
import com.example.myapplication.util.Formatters

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onMoreClick: (() -> Unit)? = null,
    isPlaying: Boolean = false,
    /** True while the multi selection is active: the row shows a checkbox instead of the menu. */
    selectionMode: Boolean = false,
    /** True when the row is part of the current selection. */
    selected: Boolean = false,
    /** Called when the checkbox of the row is toggled. */
    onToggleSelected: (() -> Unit)? = null,
    showArtwork: Boolean = true,
    layout: SongListLayout = SongListLayout.DETAILED,
    index: Int? = null,
    showDivider: Boolean = true,
) {
    val artworkSize = if (layout == SongListLayout.ARTWORK) 56.dp else 48.dp
    val titleStyle = MaterialTheme.typography.bodyLarge
    val subtitle = when {
        layout == SongListLayout.COMPACT -> song.artist.orEmpty()
        else -> listOfNotNull(song.artist, song.album).joinToString(" • ")
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .background(
                    if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    else MaterialTheme.colorScheme.surface,
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        if (selectionMode) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggleSelected?.invoke() },
            )
            Spacer(Modifier.width(4.dp))
        }
        if (showArtwork) {
            AnimatedArtwork(
                mediaId = song.mediaId,
                albumId = song.albumId,
                isPlaying = isPlaying,
                modifier = Modifier.size(artworkSize),
                cornerRadius = 10.dp,
                showBadge = false,
                pulse = true,
            )
            Spacer(Modifier.width(12.dp))
        } else if (index != null) {
            Text(
                text = index.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(28.dp),
            )
            Spacer(Modifier.width(8.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                // Persian/Arabic titles keep their own direction, so trailing punctuation and
                // parentheses stay where they belong.
                style = titleStyle.copy(textDirection = MetadataTextDirection),
                fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(textDirection = MetadataTextDirection),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.width(8.dp))
        if (isPlaying) {
            PlayingBars(isPlaying = true)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = Formatters.duration(song.durationMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onMoreClick != null && !selectionMode) {
            IconButton(onClick = onMoreClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "More options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.6.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            )
        }
    }
}

/** Big play/shuffle row used at the top of album, artist and playlist screens. */
@Composable
fun PlayAllHeader(
    primaryLabel: String,
    secondaryLabel: String,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .combinedClickableCompat(onClick = onPlay)
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = primaryLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Text(
            text = secondaryLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(CircleShape)
                .combinedClickableCompat(onClick = onShuffle)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.combinedClickableCompat(onClick: () -> Unit): Modifier =
    this.clip(CircleShape).combinedClickable(onClick = onClick)

@Composable
fun EmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        action?.invoke()
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onPlay: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (onPlay != null) {
            IconButton(onClick = onPlay, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Play $title",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(CircleShape)
                    .combinedClickable(onClick = onAction)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

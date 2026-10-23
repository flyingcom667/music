package com.example.myapplication.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.MetadataTextDirection
import com.example.myapplication.ui.viewmodel.PreviewViewModel
import com.example.myapplication.util.Formatters

/**
 * Compact preview of a file that was opened from a file manager.
 *
 * It shows the cover, the title, the artist and a small player. The full player of the app is
 * one tap away, but the preview never navigates to the library on its own.
 */
@Composable
fun AudioPreviewScreen(
    state: PreviewViewModel.UiState,
    artwork: Bitmap?,
    isPlaying: Boolean,
    isCurrentItem: Boolean,
    positionMs: Long,
    onClose: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onOpenFullPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val durationMs = if (state.durationMs > 0L) state.durationMs else 0L
    var draggingPosition by remember { mutableFloatStateOf(-1f) }
    val sliderValue = if (draggingPosition >= 0f) {
        draggingPosition
    } else if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }

    val background = Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
            MaterialTheme.colorScheme.surface,
        ),
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background),
    ) {
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
        ) {
            Icon(Icons.Rounded.Close, contentDescription = "Close preview")
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp)
                .padding(top = 48.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.size(56.dp),
                )
                artwork?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            if (state.isLoading) {
                CircularProgressIndicator()
            } else if (state.error != null) {
                Text(
                    text = state.error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                val subtitle = listOfNotNull(state.artist, state.album).joinToString(" • ")
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium.copy(textDirection = MetadataTextDirection),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Slider(
                value = sliderValue,
                onValueChange = { draggingPosition = it },
                onValueChangeFinished = {
                    if (durationMs > 0L) onSeek((draggingPosition * durationMs).toLong())
                    draggingPosition = -1f
                },
                enabled = durationMs > 0L,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = Formatters.duration(
                        if (draggingPosition >= 0f && durationMs > 0L) {
                            (draggingPosition * durationMs).toLong()
                        } else {
                            positionMs
                        },
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = Formatters.duration(durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))

            FilledIconButton(
                onClick = onTogglePlay,
                enabled = state.error == null,
                modifier = Modifier.size(64.dp),
            ) {
                Icon(
                    imageVector = if (isPlaying && isCurrentItem) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying && isCurrentItem) "Pause" else "Play",
                    modifier = Modifier.size(32.dp),
                )
            }

            Spacer(Modifier.height(24.dp))

            OutlinedButton(onClick = onOpenFullPlayer, modifier = Modifier.fillMaxWidth()) {
                Text("Open full player")
            }
        }
    }
}

package com.example.myapplication.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.domain.model.RepeatMode
import com.example.myapplication.playback.PlaybackProgress
import com.example.myapplication.playback.PlaybackUiState
import com.example.myapplication.ui.MetadataTextDirection
import com.example.myapplication.ui.components.AnimatedArtwork
import com.example.myapplication.ui.components.HoldToSeekButton
import com.example.myapplication.ui.mirroredInRtl
import com.example.myapplication.ui.components.VolumePopupContent
import com.example.myapplication.util.Formatters
import kotlin.math.abs

@Composable
fun PlayerScreen(
    state: PlaybackUiState,
    progress: PlaybackProgress,
    isFavorite: Boolean,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeekFraction: (Float) -> Unit,
    onSeekBy: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenQueue: () -> Unit,
    onSetSpeed: (Float) -> Unit,
    onMoreClick: () -> Unit,
    onStopAndClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var speedDialogVisible by remember { mutableStateOf(false) }
    var draggingFraction by remember { mutableStateOf<Float?>(null) }
    var volumeMenuVisible by remember { mutableStateOf(false) }

    val durationMs = if (progress.durationMs > 0L) progress.durationMs else state.durationMs
    val targetFraction = if (durationMs > 0L) {
        (progress.positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    // The engine reports the position a few times per second; tweening between the reports keeps
    // the wavy bar gliding instead of jumping.
    val smoothFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(durationMillis = 150, easing = LinearEasing),
        label = "seek-fraction",
    )
    val shownFraction = draggingFraction ?: smoothFraction

    val background = Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
            MaterialTheme.colorScheme.surface,
        ),
    )

    // Small phones have little vertical room: the artwork shrinks and the spacings tighten, so
    // everything still fits and the rest can be reached by scrolling.
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val screenWidth = with(density) { windowInfo.containerSize.width.toDp() }
    val screenHeight = with(density) { windowInfo.containerSize.height.toDp() }
    val compact = screenHeight < 700.dp
    val horizontalPadding = if (compact) 16.dp else 24.dp
    val artworkSize = minOf(
        screenWidth - horizontalPadding,
        screenHeight * 0.38f,
        380.dp,
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = horizontalPadding),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close player")
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Now playing",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (state.queueSize > 0) "${state.currentIndex + 1} of ${state.queueSize}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(
                    onClick = { volumeMenuVisible = true },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = "Volume",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded = volumeMenuVisible,
                    onDismissRequest = { volumeMenuVisible = false },
                ) {
                    VolumePopupContent()
                }
            }
            IconButton(onClick = onMoreClick, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "Song options")
            }
            IconButton(onClick = onStopAndClose, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "Stop playback and close")
            }
        }

        Spacer(Modifier.height(8.dp))
        AnimatedArtwork(
            mediaId = state.mediaId,
            albumId = state.albumId,
            isPlaying = state.isPlaying,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(artworkSize),
            shape = RoundedCornerShape(24.dp),
            iconSize = 64.dp,
            showBadge = false,
            pulse = true,
        )
        Spacer(Modifier.height(if (compact) 12.dp else 24.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.title.ifBlank { "Nothing playing" },
                    style = MaterialTheme.typography.headlineSmall.copy(textDirection = MetadataTextDirection),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(state.artist, state.album).joinToString(" • "),
                    style = MaterialTheme.typography.bodyMedium.copy(textDirection = MetadataTextDirection),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (isFavorite) "Remove from favourites" else "Add to favourites",
                    tint = if (isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Slider(
            value = shownFraction,
            onValueChange = { value -> draggingFraction = value },
            onValueChangeFinished = {
                draggingFraction?.let(onSeekFraction)
                draggingFraction = null
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = Formatters.duration(
                    if (draggingFraction != null) (shownFraction * durationMs).toLong() else progress.positionMs,
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            IconButton(onClick = onToggleShuffle) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (state.shuffle) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "Previous",
                    modifier = Modifier.size(36.dp).mirroredInRtl(),
                )
            }
            FilledIconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(40.dp),
                )
            }
            IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Next",
                    modifier = Modifier.size(36.dp).mirroredInRtl(),
                )
            }
            IconButton(onClick = onCycleRepeat) {
                Icon(
                    imageVector = if (state.repeatMode == RepeatMode.ONE) {
                        Icons.Rounded.RepeatOne
                    } else {
                        Icons.Rounded.Repeat
                    },
                    contentDescription = "Repeat",
                    tint = if (state.repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Holding the button jumps further and further, like the fast forward of a
            // physical player.
            HoldToSeekButton(
                icon = Icons.Rounded.Replay10,
                contentDescription = "Jump back",
                onSeek = { delta -> onSeekBy(-delta) },
            )
            TextButton(onClick = onOpenQueue) {
                Icon(Icons.Rounded.QueueMusic, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Queue (${state.queueSize})")
            }
            TextButton(onClick = { speedDialogVisible = true }) {
                Icon(Icons.Rounded.Speed, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(Formatters.speed(state.speed))
            }
            HoldToSeekButton(
                icon = Icons.Rounded.Forward10,
                contentDescription = "Jump forward",
                onSeek = onSeekBy,
            )
        }
        Spacer(Modifier.height(12.dp))

        if (state.errorMessage != null) {
            Text(
                text = state.errorMessage,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(CircleShape)
                .background(Color.Transparent),
        )
    }

    if (speedDialogVisible) {
        SpeedDialog(
            currentSpeed = state.speed,
            onDismiss = { speedDialogVisible = false },
            onSelect = {
                onSetSpeed(it)
                speedDialogVisible = false
            },
        )
    }
}

@Composable
private fun SpeedDialog(
    currentSpeed: Float,
    onDismiss: () -> Unit,
    onSelect: (Float) -> Unit,
) {
    val options = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playback speed") },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { onSelect(option) }, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = Formatters.speed(option) +
                                    if (abs(option - currentSpeed) < 0.01f) "   ✓" else "",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

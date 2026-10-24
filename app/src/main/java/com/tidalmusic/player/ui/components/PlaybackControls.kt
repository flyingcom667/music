package com.tidalmusic.player.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.tidalmusic.player.R
import com.tidalmusic.player.domain.model.RepeatMode

/**
 * The main transport row: shuffle, previous, play/pause, next and repeat.
 *
 * The play button is the focal point at 64 dp; every other control keeps a 48 dp touch
 * target. Shuffle and repeat communicate their state through both colour and a state
 * description, never through colour alone.
 */
@Composable
fun PlaybackControls(
    isPlaying: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    enabled: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeTint = MaterialTheme.colorScheme.primary
    val inactiveTint = MaterialTheme.colorScheme.onSurfaceVariant

    val repeatLabel = when (repeatMode) {
        RepeatMode.OFF -> stringResource(R.string.action_repeat_off)
        RepeatMode.ALL -> stringResource(R.string.action_repeat_all)
        RepeatMode.ONE -> stringResource(R.string.action_repeat_one)
    }
    val shuffleLabel = stringResource(R.string.action_shuffle)
    val onLabel = stringResource(R.string.state_on)
    val offLabel = stringResource(R.string.state_off)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        IconButton(
            onClick = onShuffle,
            enabled = enabled,
            modifier = Modifier
                .size(48.dp)
                .semantics { stateDescription = if (shuffleEnabled) onLabel else offLabel },
        ) {
            Icon(
                imageVector = Icons.Rounded.Shuffle,
                contentDescription = shuffleLabel,
                tint = if (shuffleEnabled) activeTint else inactiveTint,
            )
        }

        Box(
            modifier = Modifier
                .size(48.dp)
                .combinedClickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onPrevious,
                    onLongClick = onSeekBackward,
                    onLongClickLabel = stringResource(R.string.player_rewind_20_seconds),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = stringResource(R.string.action_previous),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }

        FilledIconButton(
            onClick = onPlayPause,
            enabled = enabled,
            shape = CircleShape,
            modifier = Modifier.size(64.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Crossfade(
                targetState = isPlaying,
                animationSpec = tween(durationMillis = 180),
                label = "playPause",
            ) { playing ->
                Icon(
                    imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(
                        if (playing) R.string.action_pause else R.string.action_play,
                    ),
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        Box(
            modifier = Modifier
                .size(48.dp)
                .combinedClickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onNext,
                    onLongClick = onSeekForward,
                    onLongClickLabel = stringResource(R.string.player_forward_20_seconds),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = stringResource(R.string.action_next),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }

        IconButton(
            onClick = onRepeat,
            enabled = enabled,
            modifier = Modifier
                .size(48.dp)
                .semantics { stateDescription = repeatLabel },
        ) {
            Icon(
                imageVector = if (repeatMode == RepeatMode.ONE) {
                    Icons.Rounded.RepeatOne
                } else {
                    Icons.Rounded.Repeat
                },
                contentDescription = stringResource(R.string.action_repeat),
                tint = if (repeatMode == RepeatMode.OFF) inactiveTint else activeTint,
            )
        }
    }
}

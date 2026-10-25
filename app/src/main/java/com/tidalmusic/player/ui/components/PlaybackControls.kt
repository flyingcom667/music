package com.tidalmusic.player.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
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
 * Every control answers with a springy bounce and the play/pause icon swaps with a
 * scale-and-fade transition, so the player feels responsive and modern.
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
        BouncingIconButton(
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

        SkipButton(
            enabled = enabled,
            onClick = onPrevious,
            onLongClick = onSeekBackward,
            longClickLabel = stringResource(R.string.player_rewind_20_seconds),
            contentDescription = stringResource(R.string.action_previous),
            icon = Icons.Rounded.SkipPrevious,
        )

        AnimatedPlayPauseButton(
            isPlaying = isPlaying,
            enabled = enabled,
            onClick = onPlayPause,
        )

        SkipButton(
            enabled = enabled,
            onClick = onNext,
            onLongClick = onSeekForward,
            longClickLabel = stringResource(R.string.player_forward_20_seconds),
            contentDescription = stringResource(R.string.action_next),
            icon = Icons.Rounded.SkipNext,
        )

        BouncingIconButton(
            onClick = onRepeat,
            enabled = enabled,
            modifier = Modifier
                .size(48.dp)
                .semantics { stateDescription = repeatLabel },
        ) {
            Icon(
                imageVector = if (repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                contentDescription = stringResource(R.string.action_repeat),
                tint = if (repeatMode == RepeatMode.OFF) inactiveTint else activeTint,
            )
        }
    }
}

/** Previous/next with hold-to-seek; squeezes while held and springs back on release. */
@Composable
private fun SkipButton(
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    longClickLabel: String,
    contentDescription: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "skipScale",
    )
    Box(
        modifier = Modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .combinedClickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = longClickLabel,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** The big play/pause button: the icon swaps with a springy scale-and-fade transition. */
@Composable
private fun AnimatedPlayPauseButton(
    isPlaying: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "playScale",
    )
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        modifier = Modifier
            .size(64.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        interactionSource = interactionSource,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        AnimatedContent(
            targetState = isPlaying,
            transitionSpec = {
                (
                    scaleIn(
                        initialScale = 0.55f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                    ) + fadeIn(animationSpec = tween(90))
                    ) togetherWith (
                    scaleOut(targetScale = 0.55f, animationSpec = tween(90)) +
                        fadeOut(animationSpec = tween(90))
                    )
            },
            label = "playPauseSwap",
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
}

package com.tidalmusic.player.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tidalmusic.player.R
import com.tidalmusic.player.ui.theme.LocalTidalSeekbarColor
import com.tidalmusic.player.util.TimeFormat
import kotlin.math.abs

/**
 * The seek bar with its current and total time.
 *
 * Progress is animated (SeekBarAnimationAgent): a discrete jump — the user sought, or the
 * track changed — sweeps smoothly to its target, while the regular twice-per-second
 * position ticks snap directly so the bar never lags behind the audio engine. While the
 * thumb is dragged the slider shows the dragged position; the real position is applied
 * when the drag finishes. The value always converges to the actual playback state, so the
 * display can never drift from — or move against — the engine.
 *
 * When [showSafeLevelMarker] is set (a Bluetooth or wired headphone is the active route) a
 * red line marks the unsafe listening level on the track.
 */
@Composable
fun PlaybackProgressSlider(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    showSafeLevelMarker: Boolean = false,
) {
    val hasDuration = durationMs > 0L
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf(0f) }
    val reducedMotion = rememberReducedMotion()

    val playbackFraction = if (hasDuration) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val progress = remember { Animatable(playbackFraction) }

    // Follow the engine: snap for regular ticks, sweep for discrete jumps.
    LaunchedEffect(positionMs, durationMs) {
        if (dragging || !hasDuration) return@LaunchedEffect
        val target = playbackFraction
        when {
            reducedMotion || abs(target - progress.value) < 0.01f ->
                progress.snapTo(target)

            else -> progress.animateTo(
                target,
                tween(durationMillis = 320, easing = LinearOutSlowInEasing),
            )
        }
    }
    // While dragging, the thumb follows the finger exactly.
    LaunchedEffect(dragging, dragFraction) {
        if (dragging) progress.snapTo(dragFraction)
    }

    val sliderValue = progress.value
    val displayedPosition = if (hasDuration) {
        (sliderValue * durationMs).toLong()
    } else {
        positionMs.coerceAtLeast(0L)
    }

    val seekDescription = stringResource(R.string.player_seek_bar)
    val seekbarColor = LocalTidalSeekbarColor.current

    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = sliderValue.coerceIn(0f, 1f),
            onValueChange = { fraction ->
                dragging = true
                dragFraction = fraction
            },
            onValueChangeFinished = {
                if (hasDuration) onSeek((dragFraction * durationMs).toLong())
                dragging = false
            },
            enabled = hasDuration,
            modifier = Modifier
                .fillMaxWidth()
                .safeLevelMarker(
                    visible = showSafeLevelMarker,
                    color = MaterialTheme.colorScheme.error,
                )
                .semantics { contentDescription = seekDescription },
            colors = SliderDefaults.colors(
                thumbColor = seekbarColor,
                activeTrackColor = seekbarColor,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                disabledThumbColor = MaterialTheme.colorScheme.outlineVariant,
                disabledActiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
                disabledInactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = TimeFormat.duration(displayedPosition),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (hasDuration) TimeFormat.duration(durationMs) else TimeFormat.UNKNOWN,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

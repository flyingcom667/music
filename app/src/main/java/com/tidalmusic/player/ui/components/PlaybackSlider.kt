package com.tidalmusic.player.ui.components

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

/**
 * The seek bar with its current and total time.
 *
 * While the thumb is dragged the slider shows the dragged position, so the label follows the
 * finger instead of fighting the playback tick. The real position is only updated when the
 * drag finishes, which keeps seeking responsive on slow devices.
 *
 * When [showSafeLevelMarker] is set (a Bluetooth or wired headphone is the active route) a red
 * line marks the unsafe listening level, so the limit is visible while seeking too.
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

    val playbackFraction = if (hasDuration) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val sliderValue = if (dragging) dragFraction else playbackFraction
    val displayedPosition = if (dragging) {
        (dragFraction * durationMs).toLong()
    } else {
        positionMs.coerceAtLeast(0L)
    }

    val seekDescription = stringResource(R.string.player_seek_bar)
    val seekbarColor = LocalTidalSeekbarColor.current

    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = sliderValue,
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

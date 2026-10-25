package com.tidalmusic.player.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.tidalmusic.player.R
import com.tidalmusic.player.audioeditor.WaveformCache
import com.tidalmusic.player.domain.model.Song
import com.tidalmusic.player.ui.theme.LocalTidalSeekbarColor
import kotlinx.coroutines.isActive

/**
 * The live playback visualizer on the Now Playing screen.
 *
 * Bars are driven by the track's REAL decoded amplitude envelope ([WaveformExtractor]):
 * when the music is loud a bar grows, when it is quiet it shrinks. While playing, the
 * playhead advances with an audio-rate clock (scaled by the playback speed) that is
 * re-synced to the engine's position on every engine tick; pausing freezes the bars
 * exactly where they are. When the track changes the envelope is re-extracted (cached
 * per track, so replaying a song is instant).
 *
 * If the envelope has not finished decoding, neutral placeholder bars are drawn and
 * announced as "analyzing" — never fabricated to look like the audio.
 */
@Composable
fun LiveWaveform(
    song: Song,
    durationMs: Long,
    positionMs: Long,
    isPlaying: Boolean,
    playbackSpeed: Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var envelope by remember(song.id) { mutableStateOf(WaveformCache.get(song.id)) }
    LaunchedEffect(song.id) {
        if (envelope == null) {
            envelope = WaveformCache.extract(context, song)
        }
    }

    // A smooth local playhead: advances with wall-clock time while playing (scaled by the
    // playback speed) and is corrected to the engine's position on each engine tick.
    var smoothMs by remember(song.id) { mutableFloatStateOf(positionMs.toFloat()) }
    LaunchedEffect(positionMs, durationMs) {
        smoothMs = positionMs.toFloat().coerceAtMost(durationMs.coerceAtLeast(1L).toFloat())
    }
    val reducedMotion = rememberReducedMotion()
    LaunchedEffect(isPlaying, playbackSpeed, reducedMotion) {
        if (!isPlaying || reducedMotion) return@LaunchedEffect
        var lastNanos = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { now ->
                smoothMs = (smoothMs + (now - lastNanos) / 1_000_000f * playbackSpeed)
                    .coerceIn(0f, durationMs.toFloat())
                lastNanos = now
            }
        }
    }

    val accent = LocalTidalSeekbarColor.current
    val muted = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    val description = if (envelope == null) {
        stringResource(R.string.editor_waveform_analyzing)
    } else {
        stringResource(R.string.live_waveform_description)
    }

    Canvas(
        modifier = modifier.semantics { this.contentDescription = description },
    ) {
        val bars = 44
        val gap = 2.dp.toPx()
        val barWidth = ((size.width - gap * (bars - 1)) / bars).coerceAtLeast(1f)
        val cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
        val midY = size.height / 2f
        val playable = size.height * 0.86f
        val windowMs = 10_000f * playbackSpeed

        val data = envelope
        fun amplitudeAt(timeMs: Float): Float {
            if (data == null || durationMs <= 0L) return 0.3f
            val index = (timeMs / durationMs * data.size).toInt()
                .coerceIn(0, data.lastIndex)
            val frac = (timeMs / durationMs * data.size) - index
            val next = (index + 1).coerceAtMost(data.lastIndex)
            val value = data[index] + (data[next] - data[index]) * frac
            return (value / 100f).coerceIn(0.06f, 1f)
        }

        // 65% of the window shows the music already played, 35% what is coming.
        for (bar in 0 until bars) {
            val offsetBars = (bar.toFloat() / (bars - 1) - 0.65f)
            val timeMs = smoothMs + offsetBars * windowMs
            val amplitude = amplitudeAt(timeMs)
            val barHeight = (amplitude * playable).coerceAtLeast(barWidth)
            val x = bar * (barWidth + gap)
            val isPast = timeMs <= smoothMs
            drawRoundRect(
                color = if (isPast) accent else muted,
                topLeft = Offset(x, midY - barHeight / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = cornerRadius,
            )
        }
    }
}

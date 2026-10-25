package com.tidalmusic.player.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.absoluteOffset
import com.tidalmusic.player.R
import com.tidalmusic.player.ui.theme.LocalTidalSeekbarColor

/**
 * A Telegram-style waveform for the audio editor, drawn from real decoded amplitudes.
 *
 * - Bars visualize the actual amplitude envelope ([amplitudes], 0..100); while the real
 *   data is still being decoded a neutral placeholder envelope is shown and marked as
 *   "analyzing" for accessibility.
 * - The portion before the playback cursor is tinted with the seek-bar accent; the rest
 *   stays muted.
 * - The trimming range between [startMs] and [endMs] is highlighted: everything outside
 *   is dimmed and two draggable boundary handles mark the cut points.
 * - Tapping or dragging anywhere on the waveform seeks the preview to that position.
 *
 * The time axis is intentionally laid out left-to-right (physical coordinates) in every
 * locale — audio time does not mirror in RTL — while text labels around it follow the
 * layout direction. The trim sliders remain as the accessible alternative controls.
 */
@Composable
fun WaveformSelection(
    amplitudes: IntArray?,
    durationMs: Long,
    positionMs: Long,
    startMs: Long,
    endMs: Long,
    onSeek: (Long) -> Unit,
    onStartChange: (Long) -> Unit,
    onEndChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    selectionEnabled: Boolean = true,
) {
    val accent = LocalTidalSeekbarColor.current
    val muted = mutedBarColor()
    val scrim = scrimColor()
    val description = if (amplitudes == null) {
        stringResource(R.string.editor_waveform_analyzing)
    } else {
        stringResource(R.string.editor_waveform_description)
    }

    var canvasWidthPx by remember { mutableStateOf(1f) }
    val handleWidth = 36.dp
    val density = LocalDensity.current

    // Fractions kept locally for gesture math, re-synced whenever the model changes.
    var startFraction by remember { mutableFloatStateOf(0f) }
    var endFraction by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(startMs, endMs, durationMs) {
        if (durationMs > 0L) {
            startFraction = (startMs.toFloat() / durationMs).coerceIn(0f, 1f)
            endFraction = (endMs.toFloat() / durationMs).coerceIn(0f, 1f)
        }
    }
    val playedFraction = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }

    Box(
        modifier = modifier
            .onSizeChanged { canvasWidthPx = it.width.toFloat().coerceAtLeast(1f) }
            .semantics { this.contentDescription = description },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val barCount = amplitudes?.size ?: 48
            val gap = 2.dp.toPx()
            val barWidth = ((size.width - gap * (barCount - 1)) / barCount).coerceAtLeast(1f)
            val cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            val midY = size.height / 2f
            val playable = size.height * 0.78f
            val playedX = playedFraction * size.width
            val startX = startFraction * size.width
            val endX = endFraction * size.width

            for (bar in 0 until barCount) {
                val amplitude = (amplitudes?.getOrNull(bar) ?: 30) / 100f
                val barHeight = (amplitude * playable).coerceAtLeast(barWidth)
                val x = bar * (barWidth + gap)
                val top = midY - barHeight / 2f
                val color = if (x + barWidth <= playedX) accent else muted
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = cornerRadius,
                )
            }

            if (selectionEnabled) {
                // Dim everything outside the trim range.
                if (startX > 0f) {
                    drawRect(
                        color = scrim,
                        topLeft = Offset(0f, 0f),
                        size = Size(startX, size.height),
                    )
                }
                if (endX < size.width) {
                    drawRect(
                        color = scrim,
                        topLeft = Offset(endX, 0f),
                        size = Size(size.width - endX, size.height),
                    )
                }
                // Boundary lines.
                drawLine(
                    color = accent,
                    start = Offset(startX, 0f),
                    end = Offset(startX, size.height),
                    strokeWidth = 2.dp.toPx(),
                )
                drawLine(
                    color = accent,
                    start = Offset(endX, 0f),
                    end = Offset(endX, size.height),
                    strokeWidth = 2.dp.toPx(),
                )
            }
        }

        // Seek layer: tap anywhere to jump the preview to that position.
        Box(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        if (durationMs > 0L) {
                            onSeek(((offset.x / canvasWidthPx) * durationMs).toLong().coerceIn(0L, durationMs))
                        }
                    }
                },
        )

        if (selectionEnabled && durationMs > 0L) {
            val handleWidthPx = with(density) { handleWidth.toPx() }
            fun startHandleOffset(): Float =
                (startFraction * canvasWidthPx - handleWidthPx / 2f).coerceIn(0f, canvasWidthPx - handleWidthPx)

            fun endHandleOffset(): Float =
                (endFraction * canvasWidthPx - handleWidthPx / 2f).coerceIn(0f, canvasWidthPx - handleWidthPx)

            // Start boundary handle.
            Box(
                modifier = Modifier
                    .absoluteOffset(x = with(density) { startHandleOffset().toDp() })
                    .width(handleWidth)
                    .fillMaxHeight()
                    .pointerInput(durationMs, canvasWidthPx) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            startFraction = (startFraction + dragAmount / canvasWidthPx)
                                .coerceIn(0f, endFraction - 0.01f)
                            onStartChange((startFraction * durationMs).toLong())
                        }
                    },
            )
            // End boundary handle.
            Box(
                modifier = Modifier
                    .absoluteOffset(x = with(density) { endHandleOffset().toDp() })
                    .width(handleWidth)
                    .fillMaxHeight()
                    .pointerInput(durationMs, canvasWidthPx) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            endFraction = (endFraction + dragAmount / canvasWidthPx)
                                .coerceIn(startFraction + 0.01f, 1f)
                            onEndChange((endFraction * durationMs).toLong())
                        }
                    },
            )
        }
    }
}

// Theme reads are hoisted out of the drawing lambda.
@Composable
private fun mutedBarColor(): Color =
    androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)

@Composable
private fun scrimColor(): Color =
    androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)

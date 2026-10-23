package com.example.myapplication.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.theme.LocalAnimationsEnabled
import kotlin.math.PI
import kotlin.math.sin

private const val TAU = (2.0 * PI).toFloat()

/**
 * Tiny animated equalizer. Shown next to / on top of the song that is playing so the state is
 * obvious at a glance — the bars freeze when playback is paused.
 *
 * The infinite animation is only started while the bars actually move, so idle rows in a long
 * list do not keep a frame callback alive (that is what made scrolling feel laggy).
 */
@Composable
fun PlayingBars(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 4,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val animations = LocalAnimationsEnabled.current
    val moving = isPlaying && animations

    val tick = if (moving) {
        val transition = rememberInfiniteTransition(label = "playing-bars")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = TAU,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "playing-bars-tick",
        )
        value
    } else {
        0f
    }

    Canvas(modifier = modifier.size(width = 18.dp, height = 14.dp)) {
        val slot = size.width / barCount
        val barWidth = slot * 0.55f
        repeat(barCount) { index ->
            val level = if (moving) {
                0.2f + 0.8f * ((sin(tick + index * 1.15f) + 1f) / 2f)
            } else if (isPlaying) {
                0.45f + 0.2f * index / barCount
            } else {
                0.25f
            }
            val barHeight = size.height * level
            drawRoundRect(
                color = color,
                topLeft = Offset(
                    x = index * slot + (slot - barWidth) / 2f,
                    y = size.height - barHeight,
                ),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}

/**
 * Artwork that reacts to playback: a gentle breathing pulse plus an equalizer badge while the
 * song is playing.
 *
 * Like [PlayingBars], the pulse animation only runs while the song is actually playing.
 */
@Composable
fun AnimatedArtwork(
    mediaId: Long,
    albumId: Long,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 10.dp,
    iconSize: Dp = 22.dp,
    shape: Shape = RoundedCornerShape(cornerRadius),
    showBadge: Boolean = true,
    pulse: Boolean = false,
) {
    val animations = LocalAnimationsEnabled.current
    val animating = pulse && isPlaying && animations

    val scale = if (animating) {
        val transition = rememberInfiniteTransition(label = "artwork-pulse")
        val value by transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.025f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "artwork-pulse-value",
        )
        value
    } else {
        1f
    }

    Box(
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            alpha = 1f
        },
    ) {
        SongArtwork(
            mediaId = mediaId,
            albumId = albumId,
            modifier = Modifier.fillMaxSize(),
            cornerRadius = cornerRadius,
            iconSize = iconSize,
            shape = shape,
        )
        if (showBadge) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayingBars(isPlaying = isPlaying, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

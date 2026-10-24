package com.tidalmusic.player.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.MaterialTheme
import com.tidalmusic.player.ui.theme.MotionTokens
import com.tidalmusic.player.ui.theme.OceanGradientColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A calm rotating gradient in the ocean palette, shown behind the Now Playing screen.
 *
 * While music plays the gradient sweeps slowly around the centre; when playback pauses
 * the animation simply stops mid-sweep — the gradient stays visible, frozen, and
 * resumes from the same angle when playback continues.
 */
@Composable
fun PlayingGradientBackdrop(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val phase = remember { Animatable(0f) }
    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        while (true) {
            phase.animateTo(
                targetValue = phase.value + 1f,
                animationSpec = tween(
                    durationMillis = MotionTokens.PlayingGradientSweepMs,
                    easing = LinearEasing,
                ),
            )
            // The colours are periodic, so wrapping the phase is invisible.
            if (phase.value >= 1f) phase.snapTo(phase.value - 1f)
        }
    }

    // A dark background needs a stronger tint for the sweep to read at all.
    val alpha = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) 0.30f else 0.15f

    Box(
        modifier = modifier.drawBehind {
        val angle = phase.value * 2f * PI.toFloat()
        val direction = Offset(cos(angle), sin(angle))
        val radius = sqrt(size.width * size.width + size.height * size.height) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
            drawRect(
                brush = Brush.linearGradient(
                    colors = OceanGradientColors.map { it.copy(alpha = alpha) },
                    start = center - direction * radius,
                    end = center + direction * radius,
                ),
            )
        },
    )
}

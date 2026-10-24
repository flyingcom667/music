package com.tidalmusic.player.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The OS media volume fraction above which listening is considered unsafe.
 *
 * Every volume-related indicator uses this one value so the red line, the tinted track and the
 * warning text always agree.
 */
const val SAFE_VOLUME_FRACTION = 0.7f

/**
 * Draws a thin red line across a slider track at [fraction] of its travel.
 *
 * Sliders are laid out with the thumb radius as horizontal padding, so the marker is inset by
 * the same amount to land exactly on the track instead of drifting towards the ends.
 */
fun Modifier.safeLevelMarker(
    fraction: Float = SAFE_VOLUME_FRACTION,
    visible: Boolean,
    color: Color,
    thumbRadius: Dp = 10.dp,
    strokeWidth: Dp = 2.dp,
): Modifier = drawWithContent {
    drawContent()
    if (!visible) return@drawWithContent
    val inset = thumbRadius.toPx()
    val travel = (size.width - inset * 2).coerceAtLeast(0f)
    val x = inset + travel * fraction.coerceIn(0f, 1f)
    drawLine(
        color = color,
        start = Offset(x, size.height * 0.28f),
        end = Offset(x, size.height * 0.72f),
        strokeWidth = strokeWidth.toPx(),
    )
}

package com.tidalmusic.player.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tidalmusic.player.R

/**
 * Three animated bars marking the track that is playing right now.
 *
 * The animation is decorative: the row carries a single accessibility label so TalkBack reads
 * "Currently playing" instead of describing each bar.
 */
@Composable
fun NowPlayingBars(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    val transition = rememberInfiniteTransition(label = "playing")
    val first by transition.animateFloat(
        initialValue = 0.30f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse),
        label = "bar1",
    )
    val second by transition.animateFloat(
        initialValue = 0.90f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(420), RepeatMode.Reverse),
        label = "bar2",
    )
    val third by transition.animateFloat(
        initialValue = 0.50f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(640), RepeatMode.Reverse),
        label = "bar3",
    )
    val label = stringResource(R.string.queue_item_playing)

    Row(
        modifier = modifier
            .height(12.dp)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Bar(fraction = first, tint = tint)
        Bar(fraction = second, tint = tint)
        Bar(fraction = third, tint = tint)
    }
}

@Composable
private fun Bar(fraction: Float, tint: Color) {
    Box(
        modifier = Modifier
            .size(width = 2.dp, height = (12 * fraction).dp)
            .clip(RoundedCornerShape(1.dp))
            .background(tint),
    )
}

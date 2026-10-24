package com.tidalmusic.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
 * Three lightweight bars marking the track that is playing right now.
 *
 * The animation is decorative: the row carries a single accessibility label so TalkBack reads
 * "Currently playing" instead of describing each bar.
 */
@Composable
fun NowPlayingBars(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    val label = stringResource(R.string.queue_item_playing)

    Row(
        modifier = modifier
            .height(12.dp)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Bar(fraction = 0.45f, tint = tint)
        Bar(fraction = 0.8f, tint = tint)
        Bar(fraction = 0.6f, tint = tint)
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

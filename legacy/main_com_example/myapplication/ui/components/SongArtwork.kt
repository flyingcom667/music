package com.example.myapplication.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.myapplication.data.local.ArtworkModel

/**
 * Album artwork with a graceful placeholder. Artwork is decoded from the audio file by
 * [com.example.myapplication.data.local.ArtworkFetcherFactory], so no extra permission and no
 * separate image index is needed.
 */
@Composable
fun SongArtwork(
    mediaId: Long,
    albumId: Long,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 10.dp,
    iconSize: Dp = 22.dp,
    shape: Shape = RoundedCornerShape(cornerRadius),
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.size(iconSize),
        )
        AsyncImage(
            model = ArtworkModel(mediaId = mediaId, albumId = albumId),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

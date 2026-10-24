package com.tidalmusic.player.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tidalmusic.player.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Album artwork loader.
 *
 * Deliberately dependency-free: it reads embedded artwork through the platform APIs, keeps a
 * small memory cache and decodes at a bounded size. The same bitmap is reused by every screen
 * that shows the track, so a long list never decodes the same cover twice.
 */
object ArtworkLoader {

    private const val MAX_CACHE_BYTES = 12 * 1024 * 1024
    private const val DEFAULT_TARGET_SIZE = 256
    private val TARGET_SIZE_BUCKETS = intArrayOf(128, 192, 256, 384, 512)

    private data class ArtworkKey(val songId: Long, val size: Int)

    private val cache = object : LruCache<ArtworkKey, Bitmap>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: ArtworkKey, value: Bitmap): Int = value.byteCount
    }

    private val missing = HashSet<Long>()

    suspend fun load(
        context: Context,
        song: Song,
        requestedSizePx: Int = DEFAULT_TARGET_SIZE,
    ): Bitmap? = withContext(Dispatchers.IO) {
        val targetSize = TARGET_SIZE_BUCKETS.firstOrNull { it >= requestedSizePx }
            ?: TARGET_SIZE_BUCKETS.last()
        val key = ArtworkKey(song.id, targetSize)
        cache.get(key)?.let { return@withContext it }
        if (song.id in missing) return@withContext null
        val bitmap = readArtwork(context, song, targetSize)
        if (bitmap == null) {
            if (missing.size < MAX_MISSING) missing.add(song.id)
        } else {
            cache.put(key, bitmap)
        }
        bitmap
    }

    private fun readArtwork(context: Context, song: Song, targetSize: Int): Bitmap? {
        // Preferred path: the media provider's own thumbnail, which respects the size hint.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                context.contentResolver.loadThumbnail(
                    song.uri,
                    Size(targetSize, targetSize),
                    null,
                )
            }.getOrNull()?.let { return it }
        }
        // Fallback: the embedded picture tag, downscaled while decoding.
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, song.uri)
            val bytes = retriever.embeddedPicture ?: return null
            decodeBounded(bytes, targetSize)
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun decodeBounded(bytes: ByteArray, targetSize: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > targetSize || bounds.outHeight / sample > targetSize) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private const val MAX_MISSING = 512
}

/**
 * Square album artwork with a rounded shape and a consistent fallback.
 *
 * @param contentDescription the accessibility description; pass `null` for decorative artwork
 *   that is described by the surrounding text.
 */
@Composable
fun AlbumArtwork(
    song: Song?,
    size: Dp,
    cornerRadius: Dp = 12.dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val targetSizePx = with(density) { size.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = song?.id,
        key2 = targetSizePx,
    ) {
        value = if (song == null) {
            null
        } else {
            ArtworkLoader.load(context, song, targetSizePx)?.asImageBitmap()
        }
    }

    val shape = RoundedCornerShape(cornerRadius)
    val semantics = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .then(semantics),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

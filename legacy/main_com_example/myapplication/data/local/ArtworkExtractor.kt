package com.example.myapplication.data.local

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Album artwork is read from the audio file itself (embedded picture) with two fallbacks,
 * so it works from Android 8 to the latest release:
 *  1. embedded picture via [MediaMetadataRetriever],
 *  2. system generated thumbnail via `ContentResolver.loadThumbnail` (API 29+),
 *  3. legacy `MediaStore.Audio.Albums.ALBUM_ART` path (API < 29).
 *
 * The result is returned as encoded bytes so it can be handed to Coil.
 */
class ArtworkExtractor(private val context: Context) {

    private val cache = object : LruCache<String, ByteArray>(MAX_CACHE_ENTRIES) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }

    suspend fun bytes(mediaId: Long, albumId: Long): ByteArray? = withContext(Dispatchers.IO) {
        val key = if (albumId > 0L) "album:$albumId" else "song:$mediaId"
        cache.get(key)?.let { return@withContext it }

        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaId)
        var bytes = fromEmbeddedPicture(uri)
        if (bytes == null) bytes = fromThumbnail(uri)
        if (bytes == null && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            bytes = fromLegacyAlbumArt(albumId)
        }
        if (bytes == null) return@withContext null

        cache.put(key, bytes)
        bytes
    }

    private fun fromEmbeddedPicture(uri: Uri): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val picture = retriever.embeddedPicture
            if (picture != null && picture.isNotEmpty()) picture else null
        } catch (error: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun fromThumbnail(uri: Uri): ByteArray? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            val size = Size(ARTWORK_SIZE, ARTWORK_SIZE)
            val bitmap = context.contentResolver.loadThumbnail(uri, size, null) ?: return null
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            bitmap.recycle()
            stream.toByteArray()
        } catch (error: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun fromLegacyAlbumArt(albumId: Long): ByteArray? {
        if (albumId <= 0L) return null
        val uri = ContentUris.withAppendedId(
            MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
            albumId,
        )
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.Audio.Albums.ALBUM_ART),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val path = cursor.getString(0) ?: return@use null
                File(path).takeIf { it.exists() }?.readBytes()
            }
        } catch (error: Exception) {
            null
        }
    }

    private companion object {
        const val ARTWORK_SIZE = 512
        const val MAX_CACHE_ENTRIES = 40
    }
}

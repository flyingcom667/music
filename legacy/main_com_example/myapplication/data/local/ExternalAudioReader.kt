package com.example.myapplication.data.local

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Metadata of an audio file that is not part of the scanned library, for example a file that
 * was opened from a file manager.
 */
class ExternalAudio(
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val mimeType: String?,
    val artwork: ByteArray?,
)

/**
 * Reads title, artist, duration and the embedded cover of a file the app only has a temporary
 * read permission for (an `ACTION_VIEW` intent).
 */
class ExternalAudioReader(private val context: Context) {

    /**
     * Reads the metadata of [uri].
     *
     * Fails when the app is not allowed to read the file — the caller shows the reason to the
     * user.
     */
    suspend fun read(uri: Uri): Result<ExternalAudio> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val duration = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            Result.success(
                ExternalAudio(
                    title = title?.takeIf { it.isNotBlank() }
                        ?: displayName(uri)
                        ?: uri.lastPathSegment.orEmpty(),
                    artist = artist?.takeIf { it.isNotBlank() },
                    album = album?.takeIf { it.isNotBlank() },
                    durationMs = duration,
                    mimeType = context.contentResolver.getType(uri),
                    artwork = retriever.embeddedPicture,
                ),
            )
        } catch (error: Exception) {
            Log.w(TAG, "Unable to read $uri", error)
            Result.failure(error)
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** True when the uri points at something the app is allowed to read. */
    fun canRead(uri: Uri): Boolean = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
    }.getOrDefault(false)

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
            }
    }.getOrNull()

    private companion object {
        const val TAG = "ExternalAudioReader"
    }
}

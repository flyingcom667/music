package com.tidalmusic.player.data.local

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.tidalmusic.player.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads audio files from the system media provider.
 *
 * The media store is the source of truth: this scanner never copies files into the
 * app and never writes to the database. Rows are read on [Dispatchers.IO] and mapped
 * into the [Song] model.
 */
class MediaStoreScanner(private val context: Context) {

    /**
     * Queries the media store for audio files.
     *
     * @param includeNonMusic when true, also returns ringtones, notifications and
     *   other non-music categories (`IS_MUSIC = 0` rows).
     * @param onBatch invoked after each chunk of rows so the UI can show progress
     *   while a large library is still being read.
     * @return the songs found, in provider order.
     */
    suspend fun scan(
        includeNonMusic: Boolean = false,
        onBatch: suspend (loaded: Int, total: Int) -> Unit = { _, _ -> },
    ): List<Song> = withContext(Dispatchers.IO) {
        val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val selection: String?
        val selectionArgs: Array<String>?
        if (includeNonMusic) {
            // Accept every row the provider considers audio, including IS_MUSIC = 0.
            selection = null
            selectionArgs = null
        } else {
            selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            selectionArgs = null
        }

        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.TRACK)
            add(MediaStore.Audio.Media.YEAR)
            add(MediaStore.Audio.Media.SIZE)
            add(MediaStore.Audio.Media.DATE_ADDED)
            add(MediaStore.Audio.Media.DATE_MODIFIED)
            add(MediaStore.Audio.Media.MIME_TYPE)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
        }.toTypedArray()

        val songs = mutableListOf<Song>()
        var total = 0

        try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                null,
            )?.use { cursor ->
                total = cursor.count
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val dateModifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val mimeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                val nameCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)

                var loaded = 0
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val displayName = nameCol.takeIf { it >= 0 }?.let { cursor.getString(it) }.orEmpty()
                    songs += Song(
                        id = id,
                        contentUri = ContentUris.withAppendedId(collection, id).toString(),
                        title = cursor.getString(titleCol).cleanTitle(displayName),
                        artist = cursor.getString(artistCol).cleanArtist(),
                        album = cursor.getString(albumCol).cleanAlbum(),
                        durationMs = cursor.getLong(durationCol),
                        trackNumber = cursor.getInt(trackCol),
                        year = cursor.getInt(yearCol),
                        genre = null,
                        dateAdded = cursor.getLong(dateAddedCol),
                        dateModified = cursor.getLong(dateModifiedCol),
                        sizeBytes = cursor.getLong(sizeCol),
                        displayName = displayName,
                        mimeType = mimeCol.takeIf { it >= 0 }?.let { cursor.getString(it) },
                        albumId = cursor.getLong(albumIdCol),
                    )
                    loaded++
                    if (loaded % BATCH_SIZE == 0) {
                        onBatch(loaded, total)
                    }
                }
                onBatch(loaded, total)
            }
        } catch (e: SecurityException) {
            // The permission was revoked while scanning; the caller surfaces an error state.
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Media scan failed", e)
            throw e
        }

        songs
    }

    /**
     * Reads the genre of a single track. The provider exposes it through an extra
     * query because it is not part of the main audio projection on all versions.
     */
    suspend fun readGenre(songId: Long): String? = withContext(Dispatchers.IO) {
        try {
            val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.Audio.Media._ID),
                "${MediaStore.Audio.Media._ID} = ?",
                arrayOf(songId.toString()),
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val uri = ContentUris.withAppendedId(collection, songId)
                context.contentResolver.query(
                    uri,
                    arrayOf(MediaStore.Audio.Genres.NAME),
                    null,
                    null,
                    null,
                )?.use { genreCursor ->
                    if (genreCursor.moveToFirst()) {
                        genreCursor.getString(0)?.takeIf { it.isNotBlank() }
                    } else {
                        null
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read genre for $songId", e)
            null
        }
    }

    /** The number of audio rows the provider currently holds, for the storage summary. */
    suspend fun countAudio(includeNonMusic: Boolean): Int = withContext(Dispatchers.IO) {
        try {
            val selection = if (includeNonMusic) null else "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Media._ID),
                selection,
                null,
                null,
            )?.use { it.count } ?: 0
        } catch (e: Exception) {
            Log.w(TAG, "Unable to count audio", e)
            0
        }
    }

    /** Total bytes of audio, for the storage summary. */
    suspend fun totalAudioBytes(includeNonMusic: Boolean): Long = withContext(Dispatchers.IO) {
        try {
            val selection = if (includeNonMusic) null else "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Media.SIZE),
                selection,
                null,
                null,
            )?.use { cursor ->
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                var total = 0L
                while (cursor.moveToNext()) total += cursor.getLong(sizeCol)
                total
            } ?: 0L
        } catch (e: Exception) {
            Log.w(TAG, "Unable to sum audio size", e)
            0L
        }
    }

    companion object {
        private const val TAG = "MediaStoreScanner"
        private const val BATCH_SIZE = 40

        /** The album-art URI for a track, used by the artwork loader. */
        fun albumArtUri(song: Song): Uri = Uri.parse("content://media/external/audio/albumart/${song.id}")

        /** The file name of a track without the extension. */
        private fun String.cleanTitle(displayName: String): String {
            val raw = this.trim()
            if (raw.isNotEmpty() && raw != Song.UNKNOWN_ARTIST && raw != Song.UNKNOWN_ALBUM) {
                return raw
            }
            if (displayName.isNotBlank()) {
                return displayName.substringBeforeLast('.', displayName).trim()
            }
            return raw.ifEmpty { "Unknown" }
        }

        private fun String.cleanArtist(): String {
            val raw = this.trim()
            return if (raw.isEmpty() || raw == Song.UNKNOWN_ARTIST || raw == Song.UNKNOWN_ALBUM) {
                ""
            } else {
                raw
            }
        }

        private fun String.cleanAlbum(): String {
            val raw = this.trim()
            return if (raw.isEmpty() || raw == Song.UNKNOWN_ARTIST || raw == Song.UNKNOWN_ALBUM) {
                ""
            } else {
                raw
            }
        }
    }
}

/** Reads a cursor column as a long without throwing when the column is absent. */
internal fun Cursor.longOrNull(column: String): Long? {
    val index = getColumnIndex(column)
    return if (index >= 0 && !isNull(index)) getLong(index) else null
}

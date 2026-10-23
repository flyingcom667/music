package com.example.myapplication.data.local

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.example.myapplication.data.db.SongEntity
import com.example.myapplication.util.TextNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log

/**
 * Reads the device's audio files through `MediaStore`.
 *
 * Column availability differs across API levels:
 *  - `DATA` (absolute path) exists everywhere but only identifies a real folder below 29.
 *  - `RELATIVE_PATH` exists from API 29.
 *  - `ALBUM_ARTIST` and `GENRE` exist from API 30.
 * Only supported columns are requested for the running platform, so the app works on
 * Android 8 (API 26) without crashing on unknown columns.
 */
class MediaStoreScanner(private val context: Context) : LibraryScanner {

    override suspend fun scan(
        minDurationMs: Long,
        excludedFolders: Set<String>,
    ): LibraryScanResult = withContext(Dispatchers.IO) {
        val excluded = excludedFolders.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        val songs = LinkedHashMap<Long, SongEntity>()
        val folders = LinkedHashSet<String>()

        for (collection in collections()) {
            queryCollection(collection, minDurationMs) { song ->
                if (song.folder != null) folders += song.folder
                if (excluded.isEmpty() || song.folder?.lowercase() !in excluded) {
                    songs[song.mediaId] = song
                }
            }
        }
        LibraryScanResult(songs.values.toList(), folders.toList().sorted())
    }

    private fun collections(): List<Uri> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val volumes = runCatching { MediaStore.getExternalVolumeNames(context) }.getOrNull()
            if (!volumes.isNullOrEmpty()) {
                return volumes.mapNotNull { volume ->
                    runCatching { MediaStore.Audio.Media.getContentUri(volume) }.getOrNull()
                }.ifEmpty { listOf(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI) }
            }
        }
        return listOf(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
    }

    private fun projection(): Array<String> = buildList {
        add(MediaStore.Audio.Media._ID)
        add(MediaStore.Audio.Media.TITLE)
        add(MediaStore.Audio.Media.ARTIST)
        add(MediaStore.Audio.Media.ALBUM)
        add(MediaStore.Audio.Media.DURATION)
        add(MediaStore.Audio.Media.DATA)
        add(MediaStore.Audio.Media.DISPLAY_NAME)
        add(MediaStore.Audio.Media.MIME_TYPE)
        add(MediaStore.Audio.Media.SIZE)
        add(MediaStore.Audio.Media.DATE_ADDED)
        add(MediaStore.Audio.Media.DATE_MODIFIED)
        add(MediaStore.Audio.Media.ALBUM_ID)
        add(MediaStore.Audio.Media.TRACK)
        add(MediaStore.Audio.Media.YEAR)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(MediaStore.Audio.Media.RELATIVE_PATH)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            add(MediaStore.Audio.Media.ALBUM_ARTIST)
            add(MediaStore.Audio.Media.GENRE)
        }
    }.toTypedArray()

    private fun queryCollection(
        collection: Uri,
        minDurationMs: Long,
        onSong: (SongEntity) -> Unit,
    ) {
        val projection = projection()
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val cursor = runCatching {
            context.contentResolver.query(collection, projection, selection, null, null)
        }.onFailure { Log.w(TAG, "query failed for $collection", it) }.getOrNull() ?: return

        cursor.use { c ->
            val idIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataIdx = c.getColumnIndex(MediaStore.Audio.Media.DATA)
            val displayNameIdx = c.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
            val mimeIdx = c.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
            val sizeIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val dateAddedIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val dateModifiedIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            val albumIdIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val trackIdx = c.getColumnIndex(MediaStore.Audio.Media.TRACK)
            val yearIdx = c.getColumnIndex(MediaStore.Audio.Media.YEAR)
            val relativeIdx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                c.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
            } else {
                -1
            }
            val albumArtistIdx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                c.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST)
            } else {
                -1
            }
            val genreIdx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                c.getColumnIndex(MediaStore.Audio.Media.GENRE)
            } else {
                -1
            }

            while (c.moveToNext()) {
                val mediaId = c.getLong(idIdx)
                val duration = if (durationIdx >= 0) c.getLong(durationIdx) else 0L
                if (duration in 1 until minDurationMs) continue

                val displayName = if (displayNameIdx >= 0) c.getString(displayNameIdx).orEmpty() else ""
                val data = if (dataIdx >= 0) c.getString(dataIdx).orEmpty() else ""
                val relativePath = if (relativeIdx >= 0) c.getString(relativeIdx) else null
                val folder = resolveFolder(relativePath, data)

                val uri = ContentUris.withAppendedId(collection, mediaId).toString()
                val title = if (titleIdx >= 0) c.getString(titleIdx).orEmpty() else ""
                val artist = if (artistIdx >= 0) c.getString(artistIdx) else null
                val album = if (albumIdx >= 0) c.getString(albumIdx) else null
                val albumArtist = if (albumArtistIdx >= 0) c.getString(albumArtistIdx) else null
                val genre = if (genreIdx >= 0) c.getString(genreIdx) else null
                val trackNumber = if (trackIdx >= 0) parseTrackNumber(c.getString(trackIdx)) else null
                val year = if (yearIdx >= 0) c.getInt(yearIdx).takeIf { it > 0 } else null
                val albumId = if (albumIdIdx >= 0) c.getLong(albumIdIdx) else 0L

                val entity = SongEntity(
                    mediaId = mediaId,
                    uri = uri,
                    title = title,
                    artist = artist,
                    album = album,
                    albumArtist = albumArtist,
                    trackNumber = trackNumber,
                    genre = genre,
                    year = year,
                    albumId = albumId,
                    durationMs = duration,
                    displayName = displayName.ifEmpty { data.substringAfterLast('/') },
                    relativePath = relativePath,
                    folder = folder,
                    mimeType = if (mimeIdx >= 0) c.getString(mimeIdx) else null,
                    sizeBytes = c.getLong(sizeIdx),
                    dateAdded = c.getLong(dateAddedIdx) * 1000L,
                    dateModified = c.getLong(dateModifiedIdx) * 1000L,
                    titleKey = TextNormalizer.normalize(title),
                    artistKey = TextNormalizer.normalize(artist.orEmpty()),
                    albumKey = TextNormalizer.normalize(album.orEmpty()),
                    albumArtistKey = TextNormalizer.normalize(albumArtist.orEmpty()),
                )
                onSong(entity)
            }
        }
    }

    private fun resolveFolder(relativePath: String?, data: String): String? {
        val relative = relativePath?.trimEnd('/')?.substringAfterLast('/')
        if (!relative.isNullOrBlank()) return relative
        val parent = data.substringBeforeLast('/', "")
        if (parent.isBlank()) return null
        return parent.substringAfterLast('/').ifBlank { parent }
    }

    /** MediaStore stores the track number as "12" or "12/20". */
    private fun parseTrackNumber(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val value = raw.substringBefore('/').trim()
        return value.toIntOrNull()?.takeIf { it in 1..999 }
    }

    companion object {
        private const val TAG = "MediaStoreScanner"
    }
}

package com.tidalmusic.player.data.preferences

import android.content.Context
import com.tidalmusic.player.domain.model.Song

/**
 * Persists the track and position that are currently loaded so playback can be restored
 * after the process is killed or the device reboots.
 *
 * SharedPreferences is used deliberately: the playback service reads it on the main thread
 * while starting up, where a suspending DataStore read would not be available yet.
 */
class PlaybackStateStore(context: Context) : PlaybackSessionStore {

    private val prefs = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    override fun save(song: Song, positionMs: Long) {
        prefs.edit()
            .putLong(KEY_SONG_ID, song.id)
            .putString(KEY_URI, song.contentUri)
            .putString(KEY_TITLE, song.title)
            .putString(KEY_ARTIST, song.artist)
            .putString(KEY_ALBUM, song.album)
            .putLong(KEY_DURATION, song.durationMs)
            .putLong(KEY_POSITION, positionMs.coerceAtLeast(0L))
            .putLong(KEY_DATE_ADDED, song.dateAdded)
            .putLong(KEY_SIZE, song.sizeBytes)
            .putString(KEY_DISPLAY_NAME, song.displayName)
            .putLong(KEY_ALBUM_ID, song.albumId)
            .apply()
    }

    override fun restore(): PlaybackSessionStore.RestoredSession? {
        val uri = prefs.getString(KEY_URI, null) ?: return null
        val title = prefs.getString(KEY_TITLE, null) ?: return null
        val id = prefs.getLong(KEY_SONG_ID, -1L)
        if (id <= 0L) return null
        // Only the fields that were saved are restored — nothing is invented.
        val song = Song(
            id = id,
            contentUri = uri,
            title = title,
            artist = prefs.getString(KEY_ARTIST, "").orEmpty(),
            album = prefs.getString(KEY_ALBUM, "").orEmpty(),
            durationMs = prefs.getLong(KEY_DURATION, 0L),
            trackNumber = 0,
            year = 0,
            genre = null,
            dateAdded = prefs.getLong(KEY_DATE_ADDED, 0L),
            dateModified = 0L,
            sizeBytes = prefs.getLong(KEY_SIZE, 0L),
            displayName = prefs.getString(KEY_DISPLAY_NAME, "").orEmpty(),
            mimeType = null,
            albumId = prefs.getLong(KEY_ALBUM_ID, id),
        )
        return PlaybackSessionStore.RestoredSession(song, prefs.getLong(KEY_POSITION, 0L))
    }

    companion object {
        private const val NAME = "tidal_music_playback"
        private const val KEY_SONG_ID = "song_id"
        private const val KEY_URI = "uri"
        private const val KEY_TITLE = "title"
        private const val KEY_ARTIST = "artist"
        private const val KEY_ALBUM = "album"
        private const val KEY_DURATION = "duration"
        private const val KEY_POSITION = "position"
        private const val KEY_DATE_ADDED = "date_added"
        private const val KEY_SIZE = "size"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_ALBUM_ID = "album_id"
    }
}

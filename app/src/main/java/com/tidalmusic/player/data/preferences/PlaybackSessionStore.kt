package com.tidalmusic.player.data.preferences

import com.tidalmusic.player.domain.model.Song

/**
 * Remembers the track and position that were loaded when the app was last used.
 *
 * The production implementation is backed by SharedPreferences ([PlaybackStateStore])
 * because the playback service reads it on the main thread while it starts; the screen
 * models only see this interface.
 */
interface PlaybackSessionStore {

    /** The last known track and position, or `null` when nothing was ever persisted. */
    data class RestoredSession(val song: Song, val positionMs: Long)

    fun save(song: Song, positionMs: Long)

    fun restore(): RestoredSession?
}

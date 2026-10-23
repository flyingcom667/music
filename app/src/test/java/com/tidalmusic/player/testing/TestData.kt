package com.tidalmusic.player.testing

import com.tidalmusic.player.domain.model.Song

/**
 * Builds songs for tests without touching the Android framework.
 *
 * [Song.contentUri] is plain text, so a song can be created in a plain JVM test.
 */
object TestData {

    fun song(
        id: Long,
        title: String = "Title $id",
        artist: String = "Artist",
        album: String = "Album",
        durationMs: Long = 180_000L,
        trackNumber: Int = 0,
        year: Int = 0,
        genre: String? = null,
        dateAdded: Long = 0L,
        dateModified: Long = 0L,
        sizeBytes: Long = 4_000_000L,
        displayName: String = "$title.mp3",
        mimeType: String? = "audio/mpeg",
    ): Song = Song(
        id = id,
        contentUri = "content://media/external/audio/media/$id",
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        trackNumber = trackNumber,
        year = year,
        genre = genre,
        dateAdded = dateAdded,
        dateModified = dateModified,
        sizeBytes = sizeBytes,
        displayName = displayName,
        mimeType = mimeType,
    )
}

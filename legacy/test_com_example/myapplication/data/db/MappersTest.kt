package com.example.myapplication.data.db

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The displayed metadata is the raw file metadata, only the search keys are normalised.
 * Normalising the display would change the title the user sees.
 */
class MappersTest {

    private fun entity(title: String, artist: String? = "علی رضایی") = SongEntity(
        mediaId = 1L,
        uri = "content://media/external/audio/media/1",
        title = title,
        artist = artist,
        album = "شب",
        albumArtist = null,
        trackNumber = 3,
        genre = "پاپ",
        year = 1398,
        albumId = 10L,
        durationMs = 200_000L,
        displayName = "$title.mp3",
        relativePath = "Music/",
        folder = "Music",
        mimeType = "audio/mpeg",
        sizeBytes = 4096L,
        dateAdded = 1L,
        dateModified = 1L,
        titleKey = "",
        artistKey = "",
        albumKey = "",
        albumArtistKey = "",
    )

    @Test
    fun `keeps the title exactly as the file reports it`() {
        val song = entity("آهنگ من").toDomain()

        assertEquals("آهنگ من", song.title)
        assertEquals("علی رضایی", song.artist)
        assertEquals("شب", song.album)
        assertEquals("پاپ", song.genre)
    }

    @Test
    fun `builds normalised search keys next to the untouched display values`() {
        val song = entity("آهنگ من").toDomain()

        assertEquals("اهنگ من", song.titleKey)
        assertEquals("علی رضایی", song.artistKey)
    }
}

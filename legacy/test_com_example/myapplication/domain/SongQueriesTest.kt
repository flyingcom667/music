package com.example.myapplication.domain

import com.example.myapplication.domain.model.Song
import com.example.myapplication.domain.model.SongMetadata
import com.example.myapplication.domain.model.SongSortOption
import com.example.myapplication.util.TextNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Searching and sorting have to work for Persian, Arabic and mixed titles. */
class SongQueriesTest {

    private fun song(
        id: Long,
        title: String,
        artist: String? = null,
        album: String? = null,
        favorite: Boolean = false,
    ): Song {
        val metadata = SongMetadata(title = title, artist = artist, album = album)
        return Song(
            mediaId = id,
            uri = "content://media/external/audio/media/$id",
            metadata = metadata,
            base = metadata,
            albumId = 0L,
            durationMs = 180_000L,
            displayName = "$title.mp3",
            relativePath = null,
            folder = null,
            mimeType = "audio/mpeg",
            sizeBytes = 1024L,
            dateAdded = id,
            dateModified = id,
            lastPlayedAt = 0L,
            playCount = 0,
            favoriteAddedAt = if (favorite) id else 0L,
            titleKey = TextNormalizer.normalize(title),
            artistKey = TextNormalizer.normalize(artist.orEmpty()),
            albumKey = TextNormalizer.normalize(album.orEmpty()),
            albumArtistKey = TextNormalizer.normalize(artist.orEmpty()),
        )
    }

    @Test
    fun `finds a persian title typed with arabic letters`() {
        val songs = listOf(song(1L, "کتاب عشق", artist = "حسین علیزاده"))

        val result = SongQueries.search(songs, "كتاب عشق")

        assertEquals(1, result.size)
        assertEquals("کتاب عشق", result.first().title)
    }

    @Test
    fun `finds a title that only differs by harakat`() {
        val songs = listOf(song(1L, "مُحَمَّد"))

        assertEquals(1, SongQueries.search(songs, "محمد").size)
    }

    @Test
    fun `finds a title that only differs by the zero width joiner`() {
        val songs = listOf(song(1L, "می‌رود"))

        assertEquals(1, SongQueries.search(songs, "می رود").size)
    }

    @Test
    fun `finds a title that only differs by the digit script`() {
        val songs = listOf(song(1L, "آهنگ ۱۳۹۸"))

        assertEquals(1, SongQueries.search(songs, "آهنگ 1398").size)
    }

    @Test
    fun `searches the artist and the album as well`() {
        val songs = listOf(song(1L, "Selected song", artist = "علی رضایی", album = "شب"))

        assertEquals(1, SongQueries.search(songs, "رضایی").size)
        assertEquals(1, SongQueries.search(songs, "شب").size)
        assertTrue(SongQueries.search(songs, "علی رضایی شب").isNotEmpty())
    }

    @Test
    fun `does not match a different song`() {
        val songs = listOf(song(1L, "کتاب عشق"))

        assertTrue(SongQueries.search(songs, "نغمه").isEmpty())
    }

    @Test
    fun `an empty query returns every song`() {
        val songs = listOf(song(1L, "یک"), song(2L, "دو"))

        assertEquals(2, SongQueries.search(songs, "   ").size)
    }

    @Test
    fun `sorts persian titles with the persian alphabet order`() {
        // پ comes before ت in the persian alphabet, but after it in unicode code point order.
        val songs = listOf(song(1L, "تست"), song(2L, "پسر"))

        val sorted = SongQueries.sort(songs, SongSortOption.NAME_ASC)

        assertEquals(listOf("پسر", "تست"), sorted.map { it.title })
    }

    @Test
    fun `keeps the favourites filter working`() {
        val songs = listOf(
            song(1L, "یک", favorite = true),
            song(2L, "دو"),
        )

        assertEquals(listOf("یک"), SongQueries.favorites(songs).map { it.title })
    }
}

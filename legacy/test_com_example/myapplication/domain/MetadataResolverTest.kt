package com.example.myapplication.domain

import com.example.myapplication.domain.model.MetadataOverride
import com.example.myapplication.domain.model.SongMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The metadata shown in the UI must stay exactly as the file reports it. */
class MetadataResolverTest {

    @Test
    fun `keeps the persian title of the file`() {
        val base = SongMetadata(title = "آهنگ من", artist = "علی رضایی", album = "شب")

        val result = MetadataResolver.effective(base, null, "آهنگ من.mp3")

        assertEquals("آهنگ من", result.title)
        assertEquals("علی رضایی", result.artist)
        assertEquals("شب", result.album)
    }

    @Test
    fun `falls back to the file name when the title is missing`() {
        val base = SongMetadata(title = "", artist = null, album = null)

        val result = MetadataResolver.effective(base, null, "آهنگ من.mp3")

        assertEquals("آهنگ من", result.title)
    }

    @Test
    fun `the override wins over the file metadata`() {
        val base = SongMetadata(title = "آهنگ من", artist = "علی")

        val result = MetadataResolver.effective(
            base = base,
            override = MetadataOverride(title = "عنوان تازه"),
            displayName = "آهنگ من.mp3",
        )

        assertEquals("عنوان تازه", result.title)
        assertEquals("علی", result.artist)
    }

    @Test
    fun `placeholder metadata becomes missing instead of being displayed`() {
        val base = SongMetadata(title = "<unknown>", artist = "<unknown>", album = null)

        val result = MetadataResolver.effective(base, null, "آهنگ من.mp3")

        assertEquals("آهنگ من", result.title)
        assertNull(result.artist)
    }

    @Test
    fun `the album artist falls back to the artist`() {
        val base = SongMetadata(title = "آهنگ", artist = "گروه", album = "آلبوم")

        val result = MetadataResolver.effective(base, null, "آهنگ.mp3")

        assertEquals("گروه", result.albumArtist)
    }
}

package com.tidalmusic.player.domain

import com.tidalmusic.player.domain.model.SortDirection
import com.tidalmusic.player.domain.model.SortOrder
import com.tidalmusic.player.domain.model.SortSpec
import com.tidalmusic.player.testing.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sorting and searching rules of the library.
 */
class LibraryQueriesTest {

    private val songs = listOf(
        TestData.song(id = 1, title = "Banana", artist = "Yellow", album = "Fruit", durationMs = 200_000, dateAdded = 30),
        TestData.song(id = 2, title = "apple", artist = "Red", album = "Fruit", durationMs = 100_000, dateAdded = 10),
        TestData.song(id = 3, title = "Cherry", artist = "Red", album = "Stone", durationMs = 300_000, dateAdded = 20),
    )

    // ------------------------------------------------------------------ sorting

    @Test
    fun `sorts by title case insensitively`() {
        val sorted = LibraryQueries.sort(songs, SortSpec(SortOrder.TITLE, SortDirection.ASCENDING))
        assertEquals(listOf("apple", "Banana", "Cherry"), sorted.map { it.title })
    }

    @Test
    fun `descending title reverses the order`() {
        val sorted = LibraryQueries.sort(songs, SortSpec(SortOrder.TITLE, SortDirection.DESCENDING))
        assertEquals(listOf("Cherry", "Banana", "apple"), sorted.map { it.title })
    }

    @Test
    fun `sorts by artist then album then title`() {
        val sorted = LibraryQueries.sort(songs, SortSpec(SortOrder.ARTIST, SortDirection.ASCENDING))
        // "Red" before "Yellow"; within "Red" the album decides: Fruit before Stone.
        assertEquals(listOf("apple", "Cherry", "Banana"), sorted.map { it.title })
    }

    @Test
    fun `sorts by album`() {
        val sorted = LibraryQueries.sort(songs, SortSpec(SortOrder.ALBUM, SortDirection.ASCENDING))
        // Both "Fruit" tracks come first, ordered by artist.
        assertEquals(listOf("apple", "Banana", "Cherry"), sorted.map { it.title })
    }

    @Test
    fun `sorts by duration`() {
        val sorted = LibraryQueries.sort(songs, SortSpec(SortOrder.DURATION, SortDirection.ASCENDING))
        assertEquals(listOf(100_000L, 200_000L, 300_000L), sorted.map { it.durationMs })
    }

    @Test
    fun `sorts by date added`() {
        val sorted = LibraryQueries.sort(songs, SortSpec(SortOrder.DATE_ADDED, SortDirection.ASCENDING))
        assertEquals(listOf(10L, 20L, 30L), sorted.map { it.dateAdded })
    }

    @Test
    fun `ties are broken by id so the order is stable`() {
        val duplicated = listOf(
            TestData.song(id = 9, title = "Same", artist = "A"),
            TestData.song(id = 4, title = "Same", artist = "A"),
            TestData.song(id = 7, title = "Same", artist = "A"),
        )
        val sorted = LibraryQueries.sort(duplicated, SortSpec(SortOrder.TITLE, SortDirection.ASCENDING))
        assertEquals(listOf(4L, 7L, 9L), sorted.map { it.id })
    }

    @Test
    fun `blank artists sort after named ones`() {
        val withBlank = songs + TestData.song(id = 4, title = "Zulu", artist = "")
        val sorted = LibraryQueries.sort(withBlank, SortSpec(SortOrder.ARTIST, SortDirection.ASCENDING))
        assertEquals("Zulu", sorted.last().title)
    }

    // ------------------------------------------------------------------ searching

    @Test
    fun `search matches the title regardless of case`() {
        val result = LibraryQueries.search(songs, "banana")
        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun `search matches artist and album`() {
        assertEquals(listOf(2L, 3L), LibraryQueries.search(songs, "red").map { it.id })
        assertEquals(listOf(1L, 2L), LibraryQueries.search(songs, "fruit").map { it.id })
    }

    @Test
    fun `every token has to match somewhere`() {
        val result = LibraryQueries.search(songs, "red fruit")
        assertEquals(listOf(2L), result.map { it.id })
    }

    @Test
    fun `a blank query returns everything`() {
        assertEquals(songs, LibraryQueries.search(songs, "   "))
    }

    @Test
    fun `search matches the file name too`() {
        val renamed = listOf(TestData.song(id = 5, title = "Untitled", displayName = "Live in Paris.mp3"))
        assertEquals(listOf(5L), LibraryQueries.search(renamed, "paris").map { it.id })
    }

    @Test
    fun `no match returns an empty list`() {
        assertTrue(LibraryQueries.search(songs, "nothing here").isEmpty())
    }

    // ------------------------------------------------------------------ text folding

    @Test
    fun `normalises arabic yeh and kaf variants`() {
        assertEquals(
            LibraryQueries.normalize("مُحَمَّد"),
            LibraryQueries.normalize("محمد"),
        )
        assertEquals(
            LibraryQueries.normalize("کتاب"),
            LibraryQueries.normalize("كتاب"),
        )
    }

    @Test
    fun `normalises arabic alef variants`() {
        assertEquals(
            LibraryQueries.normalize("أحمد"),
            LibraryQueries.normalize("احمد"),
        )
    }

    @Test
    fun `folds persian and arabic digits to western ones`() {
        assertEquals("123", LibraryQueries.normalize("۱۲۳"))
        assertEquals("123", LibraryQueries.normalize("١٢٣"))
    }

    @Test
    fun `removes diacritics and tatweel`() {
        assertEquals("سلام", LibraryQueries.normalize("ســلام"))
    }

    @Test
    fun `collapses whitespace and zero width joiners`() {
        assertEquals("a b", LibraryQueries.normalize("a‌ b"))
    }

    @Test
    fun `search finds a track through a different yeh spelling`() {
        val persian = listOf(TestData.song(id = 7, title = "كتابخانه"))
        val arabicQuery = listOf(TestData.song(id = 8, title = "مكتبه"))
        assertEquals(listOf(7L), LibraryQueries.search(persian, "کتابخانه").map { it.id })
        assertEquals(listOf(8L), LibraryQueries.search(arabicQuery, "مكتبه").map { it.id })
    }

    // ------------------------------------------------------------------ helpers

    @Test
    fun `recently added is newest first`() {
        val recent = LibraryQueries.recentlyAdded(songs)
        assertEquals(listOf(30L, 20L, 10L), recent.map { it.dateAdded })
    }

    @Test
    fun `by ids keeps the requested order and skips unknown ids`() {
        val byId = songs.associateBy { it.id }
        assertEquals(listOf(3L, 1L), LibraryQueries.byIds(byId, listOf(3L, 99L, 1L)).map { it.id })
        assertTrue(LibraryQueries.byIds(byId, emptyList()).isEmpty())
    }
}

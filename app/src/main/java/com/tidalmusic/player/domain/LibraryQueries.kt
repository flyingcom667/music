package com.tidalmusic.player.domain

import com.tidalmusic.player.domain.model.Song
import com.tidalmusic.player.domain.model.SortDirection
import com.tidalmusic.player.domain.model.SortOrder
import com.tidalmusic.player.domain.model.SortSpec
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/**
 * Sorting and searching over the loaded library.
 *
 * The library is held in memory as a `StateFlow`, so these are pure list transforms that run
 * on a background dispatcher; SQL `LIKE` could not express the Unicode folding that Persian
 * and Arabic metadata needs.
 */
object LibraryQueries {

    /**
     * Locale-aware comparator.
     *
     * A Persian collator is used when the platform ships it so that "پ" sorts next to "ب";
     * otherwise the default collator is used, which still gives a stable, case-insensitive
     * order. Comparison never throws for unknown scripts.
     */
    fun stringComparator(): Comparator<String> {
        val collator = runCatching {
            Collator.getInstance(Locale.forLanguageTag("fa-IR")).apply { strength = Collator.PRIMARY }
        }.getOrElse { Collator.getInstance() }
        return Comparator { a, b -> collator.compare(a, b) }
    }

    /** Sorts [songs] by [spec]. Ties are broken by id so the order is always deterministic. */
    fun sort(
        songs: List<Song>,
        spec: SortSpec,
        comparator: Comparator<String> = stringComparator(),
    ): List<Song> {
        val byTitle = Comparator<Song> { a, b -> comparator.compare(a.title, b.title) }
        val byArtist = Comparator<Song> { a, b ->
            comparator.compare(a.artist.ifBlank { "\uFFFF" }, b.artist.ifBlank { "\uFFFF" })
        }
        val byAlbum = Comparator<Song> { a, b ->
            comparator.compare(a.album.ifBlank { "\uFFFF" }, b.album.ifBlank { "\uFFFF" })
        }
        val byId = Comparator<Song> { a, b -> a.id.compareTo(b.id) }
        val byDuration = Comparator<Song> { a, b -> a.durationMs.compareTo(b.durationMs) }
        val byDateAdded = Comparator<Song> { a, b -> a.dateAdded.compareTo(b.dateAdded) }

        val base: Comparator<Song> = when (spec.order) {
            SortOrder.TITLE -> byTitle.then(byId)
            SortOrder.ARTIST -> byArtist.then(byAlbum).then(byTitle).then(byId)
            SortOrder.ALBUM -> byAlbum.then(byArtist).then(byTitle).then(byId)
            SortOrder.DURATION -> byDuration.then(byTitle).then(byId)
            SortOrder.DATE_ADDED -> byDateAdded.then(byTitle).then(byId)
        }
        val ordered = songs.sortedWith(base)
        return if (spec.direction == SortDirection.ASCENDING) ordered else ordered.reversed()
    }

    /**
     * Filters [songs] by [query], matching title, artist, album and file name.
     *
     * Every whitespace-separated token must match somewhere, so "radio gaga" finds
     * "Radio Ga Ga" and Persian text matches regardless of the yeh/kaf variant used.
     */
    fun search(songs: List<Song>, query: String): List<Song> {
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return songs
        return songs.filter { song ->
            val haystack = listOf(song.title, song.artist, song.album, song.displayName)
                .map { normalize(it) }
            tokens.all { token -> haystack.any { it.contains(token) } }
        }
    }

    /** Newest first, the definition of the "Recently Added" tab. */
    fun recentlyAdded(songs: List<Song>): List<Song> =
        songs.sortedWith(compareByDescending<Song> { it.dateAdded }.thenBy { it.title })

    /** Resolves [ids] to songs, keeping the id order (used for playlists and history). */
    fun byIds(songsById: Map<Long, Song>, ids: List<Long>): List<Song> =
        if (ids.isEmpty()) emptyList() else ids.mapNotNull { songsById[it] }

    // ------------------------------------------------------------------ text folding

    private const val ZWNJ = '\u200C'
    private const val ZWJ = '\u200D'
    private const val TATWEEL = '\u0640'

    /** Case-insensitive, diacritic-insensitive, digit-folding normal form. */
    fun normalize(input: String): String {
        if (input.isEmpty()) return ""
        val folded = StringBuilder(input.length)
        val decomposed = Normalizer.normalize(input, Normalizer.Form.NFD)
        for (ch in decomposed) {
            when {
                isMark(ch) -> Unit
                ch == ZWNJ || ch == ZWJ -> folded.append(' ')
                ch == TATWEEL -> Unit
                else -> folded.append(foldChar(ch))
            }
        }
        return folded.toString()
            .trim()
            .replace(WHITESPACE, " ")
            .lowercase(Locale.ROOT)
    }

    fun tokenize(query: String): List<String> =
        normalize(query).split(' ').filter { it.isNotEmpty() }

    private val WHITESPACE = Regex("\\s+")

    private fun isMark(ch: Char): Boolean {
        val type = Character.getType(ch)
        return type == Character.NON_SPACING_MARK.toInt() ||
            type == Character.COMBINING_SPACING_MARK.toInt() ||
            type == Character.ENCLOSING_MARK.toInt()
    }

    private fun foldChar(ch: Char): Char = when (ch) {
        // Arabic yeh variants -> Farsi yeh
        '\u064A', '\u0649', '\u0626', '\u06D2' -> '\u06CC'
        // Arabic kaf -> keheh
        '\u0643' -> '\u06A9'
        // teh marbuta / heh with yeh above / ae -> heh
        '\u0629', '\u06C0', '\u06D5' -> '\u0647'
        // alef variants -> bare alef
        '\u0622', '\u0623', '\u0625', '\u0671', '\u0672', '\u0673' -> '\u0627'
        // waw with hamza -> waw
        '\u0624' -> '\u0648'
        else -> when (ch) {
            in '\u0660'..'\u0669' -> '0' + (ch - '\u0660')
            in '\u06F0'..'\u06F9' -> '0' + (ch - '\u06F0')
            else -> ch
        }
    }
}

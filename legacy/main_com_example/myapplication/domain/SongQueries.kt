package com.example.myapplication.domain

import com.example.myapplication.domain.model.Song
import com.example.myapplication.domain.model.SongSortOption
import com.example.myapplication.util.PersianTextOrder
import com.example.myapplication.util.TextNormalizer
import java.text.Collator
import java.util.Locale

/**
 * Sorting and searching over the (already loaded) library.
 *
 * Sorting and filtering happen in memory because the displayed values include the user's
 * Unicode overrides and because matching Persian/Arabic text needs normalisation that SQL
 * `LIKE` cannot express. The library is exposed as a `StateFlow`, so this is a pure list
 * transform per query keystroke.
 */
object SongQueries {

    /**
     * Locale aware comparator so that "پ" sorts next to "ب" instead of after every Arabic
     * letter.
     *
     * The Persian order is applied explicitly for Arabic script titles, because the platform
     * collator only knows it when the device ships the matching locale data. Everything else
     * is compared by the collator, which falls back to the default one when the Farsi locale
     * is unavailable.
     */
    fun stringComparator(): Comparator<String> {
        val collator = runCatching {
            Collator.getInstance(Locale.forLanguageTag("fa-IR")).apply { strength = Collator.PRIMARY }
        }.getOrElse { Collator.getInstance() }
        return Comparator { a, b ->
            if (PersianTextOrder.isArabicScript(a) && PersianTextOrder.isArabicScript(b)) {
                val byPersianOrder = PersianTextOrder.compare(a, b)
                if (byPersianOrder != 0) byPersianOrder else collator.compare(a, b)
            } else {
                collator.compare(a, b)
            }
        }
    }

    fun sort(
        songs: List<Song>,
        option: SongSortOption,
        comparator: Comparator<String> = stringComparator(),
    ): List<Song> {
        val byTitle = Comparator<Song> { a, b -> comparator.compare(a.title, b.title) }
        val byArtist = Comparator<Song> { a, b -> comparator.compare(a.artist.orEmpty(), b.artist.orEmpty()) }
        val byAlbum = Comparator<Song> { a, b -> comparator.compare(a.album.orEmpty(), b.album.orEmpty()) }
        val byTrack = Comparator<Song> { a, b ->
            (a.trackNumber ?: Int.MAX_VALUE).compareTo(b.trackNumber ?: Int.MAX_VALUE)
        }
        val byId = Comparator<Song> { a, b -> a.mediaId.compareTo(b.mediaId) }

        return when (option) {
            SongSortOption.NAME_ASC -> songs.sortedWith(byTitle.then(byId))
            SongSortOption.NAME_DESC -> songs.sortedWith(byTitle.reversed().then(byId))
            // The date and duration sorts use the media id as tie breaker: comparing the
            // titles with a collator would be needlessly expensive for a long library.
            SongSortOption.RECENTLY_ADDED -> songs.sortedWith(
                Comparator<Song> { a, b -> b.dateAdded.compareTo(a.dateAdded) }.then(byId),
            )
            SongSortOption.OLDEST_ADDED -> songs.sortedWith(
                Comparator<Song> { a, b -> a.dateAdded.compareTo(b.dateAdded) }.then(byId),
            )
            SongSortOption.RECENTLY_PLAYED -> songs.sortedWith(
                Comparator<Song> { a, b -> b.lastPlayedAt.compareTo(a.lastPlayedAt) }.then(byId),
            )
            SongSortOption.SHORTEST -> songs.sortedWith(
                Comparator<Song> { a, b -> a.durationMs.compareTo(b.durationMs) }.then(byId),
            )
            SongSortOption.LONGEST -> songs.sortedWith(
                Comparator<Song> { a, b -> b.durationMs.compareTo(a.durationMs) }.then(byId),
            )
            SongSortOption.ARTIST -> songs.sortedWith(
                byArtist.then(byAlbum).then(byTrack).then(byTitle),
            )
            SongSortOption.ALBUM -> songs.sortedWith(byAlbum.then(byTrack).then(byTitle))
        }
    }

    /**
     * Multi token, Unicode aware search across title, artist, album and album artist.
     * Repeated/leading/trailing spaces are ignored.
     */
    fun search(songs: List<Song>, rawQuery: String): List<Song> {
        val tokens = TextNormalizer.tokenize(rawQuery)
        if (tokens.isEmpty()) return songs
        return songs.filter { song -> TextNormalizer.matches(tokens, song.searchKeys) }
    }

    fun favorites(songs: List<Song>, option: SongSortOption = SongSortOption.NAME_ASC): List<Song> =
        sort(songs.filter { it.isFavorite }, option)

    fun recentlyPlayed(songs: List<Song>, limit: Int): List<Song> =
        songs.asSequence()
            .filter { it.lastPlayedAt > 0L }
            .sortedByDescending { it.lastPlayedAt }
            .take(limit)
            .toList()

    fun recentlyAdded(songs: List<Song>, limit: Int): List<Song> =
        songs.asSequence()
            .sortedByDescending { it.dateAdded }
            .take(limit)
            .toList()

    fun byIds(songs: List<Song>, ids: List<Long>): List<Song> {
        if (ids.isEmpty()) return emptyList()
        val index = songs.associateBy { it.mediaId }
        return ids.mapNotNull { index[it] }
    }
}

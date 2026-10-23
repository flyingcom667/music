package com.tidalmusic.player.data.repository

import com.tidalmusic.player.data.local.MediaStoreScanner
import com.tidalmusic.player.domain.model.Song
import kotlinx.coroutines.flow.Flow

/**
 * Reads the device's audio library through the system media provider.
 */
class MediaStoreLibraryRepository(
    private val scanner: MediaStoreScanner,
) : LibraryRepository {

    override suspend fun loadSongs(
        includeNonMusic: Boolean,
        onProgress: suspend (loaded: Int, total: Int) -> Unit,
    ): List<Song> = scanner.scan(includeNonMusic = includeNonMusic, onBatch = onProgress)

    override suspend fun findSong(songId: Long): Song? =
        scanner.scan(includeNonMusic = true).firstOrNull { it.id == songId }

    override suspend fun totalAudioBytes(includeNonMusic: Boolean): Long =
        scanner.totalAudioBytes(includeNonMusic)

    override suspend fun audioCount(includeNonMusic: Boolean): Int =
        scanner.countAudio(includeNonMusic)

    override suspend fun genreOf(songId: Long): String? = scanner.readGenre(songId)
}

/** Exposed for tests: a repository over a fixed list of songs. */
class InMemoryLibraryRepository(
    private val songs: List<Song>,
) : LibraryRepository {

    override suspend fun loadSongs(
        includeNonMusic: Boolean,
        onProgress: suspend (loaded: Int, total: Int) -> Unit,
    ): List<Song> {
        onProgress(songs.size, songs.size)
        return songs
    }

    override suspend fun findSong(songId: Long): Song? = songs.firstOrNull { it.id == songId }

    override suspend fun totalAudioBytes(includeNonMusic: Boolean): Long =
        songs.sumOf { it.sizeBytes }

    override suspend fun audioCount(includeNonMusic: Boolean): Int = songs.size

    override suspend fun genreOf(songId: Long): String? =
        songs.firstOrNull { it.id == songId }?.genre
}

/** Not used by the app but keeps the flow type explicit for callers. */
internal typealias LibraryFlow<T> = Flow<T>

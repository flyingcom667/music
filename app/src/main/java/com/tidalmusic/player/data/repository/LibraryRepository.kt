package com.tidalmusic.player.data.repository

import com.tidalmusic.player.domain.model.Song
import kotlinx.coroutines.flow.Flow

/**
 * Read access to the device's audio library.
 *
 * Implementations must never block the caller's thread; the media store is queried on
 * a background dispatcher.
 */
interface LibraryRepository {

    /**
     * Scans the media store and returns every permitted audio track.
     *
     * @param includeNonMusic when true, also returns ringtones, notifications and
     *   other non-music categories.
     * @param onProgress reports how many rows have been read so far, together with
     *   the total the provider reports (0 when the provider cannot estimate it).
     */
    suspend fun loadSongs(
        includeNonMusic: Boolean = false,
        onProgress: suspend (loaded: Int, total: Int) -> Unit = { _, _ -> },
    ): List<Song>

    /** Looks up a single track by its media-store id. */
    suspend fun findSong(songId: Long): Song?

    /** Total bytes of audio on the device. */
    suspend fun totalAudioBytes(includeNonMusic: Boolean): Long

    /** The number of audio rows on the device. */
    suspend fun audioCount(includeNonMusic: Boolean): Int

    /** The genre of a track, when the provider exposes one. */
    suspend fun genreOf(songId: Long): String?
}

/**
 * The app-owned relational data: playlists, favourites, history and hidden tracks.
 */
interface UserLibraryRepository {

    // ------------------------------------------------------------------ playlists

    fun observePlaylists(): Flow<List<PlaylistSummary>>

    fun observePlaylist(id: Long): Flow<PlaylistSummary?>

    suspend fun createPlaylist(name: String, description: String?): Result<Long>

    suspend fun renamePlaylist(id: Long, name: String, description: String?): Result<Unit>

    suspend fun deletePlaylist(id: Long): Result<Unit>

    suspend fun addSongsToPlaylist(playlistId: Long, songIds: List<Long>): Result<Int>

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long): Result<Unit>

    /** Persists a new track order for a playlist. */
    suspend fun reorderPlaylist(playlistId: Long, orderedSongIds: List<Long>): Result<Unit>

    suspend fun isInPlaylist(playlistId: Long, songId: Long): Boolean

    // ------------------------------------------------------------------ favorites

    fun observeFavoriteIds(): Flow<Set<Long>>

    fun observeIsFavorite(songId: Long): Flow<Boolean>

    suspend fun setFavorite(songId: Long, favorite: Boolean): Result<Unit>

    fun observeFavoriteCount(): Flow<Int>

    // ------------------------------------------------------------------ history

    fun observeRecentlyPlayedIds(): Flow<List<Long>>

    fun observeRecentlyPlayedCount(): Flow<Int>

    suspend fun recordPlay(songId: Long)

    // ------------------------------------------------------------------ hidden

    fun observeHiddenIds(): Flow<Set<Long>>

    fun observeHiddenCount(): Flow<Int>

    suspend fun setHidden(songId: Long, hidden: Boolean): Result<Unit>

    /** Removes all app-owned references after the underlying audio file is deleted. */
    suspend fun removeSongReferences(songId: Long): Result<Unit>

    // ------------------------------------------------------------------ stats

    fun observeLibraryStats(): Flow<LibraryStats>
}

/** A playlist plus the ids of the tracks it contains. */
data class PlaylistSummary(
    val id: Long,
    val name: String,
    val description: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val songIds: List<Long>,
) {
    val trackCount: Int get() = songIds.size
}

/** Aggregated library figures used by the home header and the settings summary. */
data class LibraryStats(
    val songCount: Int = 0,
    val albumCount: Int = 0,
    val totalDurationMs: Long = 0L,
    val totalSizeBytes: Long = 0L,
    val favoriteCount: Int = 0,
    val recentlyPlayedCount: Int = 0,
)

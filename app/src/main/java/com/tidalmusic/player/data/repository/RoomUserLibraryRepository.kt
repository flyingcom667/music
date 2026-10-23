package com.tidalmusic.player.data.repository

import com.tidalmusic.player.data.database.AppDatabase
import com.tidalmusic.player.data.database.FavoriteEntity
import com.tidalmusic.player.data.database.HiddenTrackEntity
import com.tidalmusic.player.data.database.PlaylistEntity
import com.tidalmusic.player.data.database.PlaylistTrackEntity
import com.tidalmusic.player.data.database.RecentlyPlayedEntity
import androidx.room.withTransaction
import com.tidalmusic.player.domain.PlaylistRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The app-owned data, backed by Room.
 *
 * Audio files are *not* stored here — only playlist membership, favourites, playback
 * history and hidden tracks are.
 */
class RoomUserLibraryRepository(
    private val database: AppDatabase,
) : UserLibraryRepository {

    private val playlistDao = database.playlistDao()
    private val favoriteDao = database.favoriteDao()
    private val historyDao = database.historyDao()
    private val hiddenDao = database.hiddenTrackDao()

    /** Guards re-ordering against concurrent writes. */
    private val reorderMutex = Mutex()

    // ------------------------------------------------------------------ playlists

    override fun observePlaylists(): Flow<List<PlaylistSummary>> =
        combine(
            playlistDao.observeAll(),
            playlistDao.observeAllTracks(),
        ) { playlists, tracks ->
            val byPlaylist = tracks.groupBy { it.playlistId }
            playlists.map { playlist ->
                playlist.toSummary(
                    byPlaylist[playlist.id]
                        ?.sortedBy { it.position }
                        ?.map { it.songId }
                        .orEmpty(),
                )
            }
        }

    override fun observePlaylist(id: Long): Flow<PlaylistSummary?> =
        combine(
            playlistDao.observeById(id),
            playlistDao.observeSongIds(id),
        ) { playlist, songIds ->
            playlist?.toSummary(songIds)
        }

    override suspend fun createPlaylist(name: String, description: String?): Result<Long> =
        runCatching {
            val trimmed = PlaylistRules.normalizeName(name)
            val rejection = PlaylistRules.rejectionReason(trimmed, playlistDao.findByName(trimmed)?.name)
            require(rejection == null) { rejectionMessage(rejection) }
            val now = System.currentTimeMillis()
            playlistDao.insert(
                PlaylistEntity(
                    name = trimmed,
                    description = description?.trim()?.takeIf { it.isNotEmpty() },
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }

    override suspend fun renamePlaylist(
        id: Long,
        name: String,
        description: String?,
    ): Result<Unit> = runCatching {
        val trimmed = PlaylistRules.normalizeName(name)
        val existing = playlistDao.findByName(trimmed)
        // Keeping the playlist's own name is allowed; only another playlist blocks it.
        val conflicting = existing?.takeIf { it.id != id }
        val rejection = PlaylistRules.rejectionReason(trimmed, conflicting?.name)
        require(rejection == null) { rejectionMessage(rejection) }
        playlistDao.update(id, trimmed, description?.trim()?.takeIf { it.isNotEmpty() }, System.currentTimeMillis())
    }

    override suspend fun deletePlaylist(id: Long): Result<Unit> = runCatching {
        // Deleting the playlist removes only the playlist and its ordering rows.
        // The audio files are never touched.
        playlistDao.delete(id)
    }

    override suspend fun addSongsToPlaylist(
        playlistId: Long,
        songIds: List<Long>,
    ): Result<Int> = runCatching {
        var position = playlistDao.maxPosition(playlistId)
        var added = 0
        val now = System.currentTimeMillis()
        songIds.forEach { songId ->
            if (!playlistDao.containsSong(playlistId, songId)) {
                position += 1
                playlistDao.addTrack(
                    PlaylistTrackEntity(
                        playlistId = playlistId,
                        songId = songId,
                        position = position,
                        addedAt = now,
                    ),
                )
                added += 1
            }
        }
        if (added > 0) playlistDao.touch(playlistId, now)
        added
    }

    override suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long): Result<Unit> =
        runCatching {
            playlistDao.removeTrack(playlistId, songId)
            // Close the gap in the ordering so positions stay contiguous.
            val remaining = playlistDao.songIdsIn(playlistId)
            remaining.forEachIndexed { index, id ->
                playlistDao.updatePosition(playlistId, id, index)
            }
            playlistDao.touch(playlistId, System.currentTimeMillis())
        }

    override suspend fun reorderPlaylist(
        playlistId: Long,
        orderedSongIds: List<Long>,
    ): Result<Unit> = reorderMutex.withLock {
        runCatching {
            // Keep only the ids the playlist actually holds, then append anything the
            // caller omitted so no track is silently lost.
            val existing = playlistDao.songIdsIn(playlistId)
            val finalOrder = PlaylistRules.mergeTrackOrder(existing, orderedSongIds)
            PlaylistRules.positionsFor(finalOrder).forEach { (songId, position) ->
                playlistDao.updatePosition(playlistId, songId, position)
            }
            playlistDao.touch(playlistId, System.currentTimeMillis())
        }
    }

    override suspend fun isInPlaylist(playlistId: Long, songId: Long): Boolean =
        playlistDao.containsSong(playlistId, songId)

    // ------------------------------------------------------------------ favorites

    override fun observeFavoriteIds(): Flow<Set<Long>> =
        favoriteDao.observeFavoriteIds().map { it.toSet() }

    override fun observeIsFavorite(songId: Long): Flow<Boolean> =
        favoriteDao.observeIsFavorite(songId)

    override suspend fun setFavorite(songId: Long, favorite: Boolean): Result<Unit> = runCatching {
        if (favorite) {
            // REPLACE semantics via the primary key guarantee no duplicate entries.
            favoriteDao.insert(FavoriteEntity(songId = songId, addedAt = System.currentTimeMillis()))
        } else {
            favoriteDao.delete(songId)
        }
    }

    override fun observeFavoriteCount(): Flow<Int> = favoriteDao.observeCount()

    // ------------------------------------------------------------------ history

    override fun observeRecentlyPlayedIds(): Flow<List<Long>> =
        historyDao.observeIds()

    override fun observeRecentlyPlayedCount(): Flow<Int> =
        historyDao.observeAll().map { it.size }

    override suspend fun recordPlay(songId: Long) {
        val existing = historyDao.get(songId)
        historyDao.upsert(
            RecentlyPlayedEntity(
                songId = songId,
                lastPlayedAt = System.currentTimeMillis(),
                playCount = (existing?.playCount ?: 0) + 1,
            ),
        )
        historyDao.trimTo(HISTORY_LIMIT)
    }

    // ------------------------------------------------------------------ hidden

    override fun observeHiddenIds(): Flow<Set<Long>> =
        hiddenDao.observeIds().map { it.toSet() }

    override fun observeHiddenCount(): Flow<Int> = hiddenDao.observeCount()

    override suspend fun setHidden(songId: Long, hidden: Boolean): Result<Unit> = runCatching {
        if (hidden) {
            hiddenDao.insert(HiddenTrackEntity(songId = songId, hiddenAt = System.currentTimeMillis()))
        } else {
            hiddenDao.delete(songId)
        }
    }

    override suspend fun removeSongReferences(songId: Long): Result<Unit> = runCatching {
        database.withTransaction {
            playlistDao.removeTrackFromAllPlaylists(songId)
            favoriteDao.delete(songId)
            historyDao.delete(songId)
            hiddenDao.delete(songId)
        }
    }

    // ------------------------------------------------------------------ stats

    override fun observeLibraryStats(): Flow<LibraryStats> = combine(
        favoriteDao.observeCount(),
        historyDao.observeAll(),
    ) { favorites, history ->
        LibraryStats(favoriteCount = favorites, recentlyPlayedCount = history.size)
    }

    /** Adds the media-provider derived figures to the app-owned statistics. */
    fun mergeWithLibrary(
        stats: Flow<LibraryStats>,
        songCount: Int,
        albumCount: Int,
        totalDurationMs: Long,
        totalSizeBytes: Long,
    ): Flow<LibraryStats> = stats.map { owned ->
        owned.copy(
            songCount = songCount,
            albumCount = albumCount,
            totalDurationMs = totalDurationMs,
            totalSizeBytes = totalSizeBytes,
        )
    }

    private fun PlaylistEntity.toSummary(songIds: List<Long>) = PlaylistSummary(
        id = id,
        name = name,
        description = description,
        createdAt = createdAt,
        updatedAt = updatedAt,
        songIds = songIds,
    )

    /** The human readable reason a playlist name was refused. */
    private fun rejectionMessage(rejection: PlaylistRules.NameRejection?): String = when (rejection) {
        PlaylistRules.NameRejection.BLANK -> "Playlist name must not be blank"
        PlaylistRules.NameRejection.DUPLICATE -> "A playlist with this name already exists"
        null -> "Playlist name is not valid"
    }

    companion object {
        /** The history is bounded so the database never grows without limit. */
        const val HISTORY_LIMIT = 500
    }
}

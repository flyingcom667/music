package com.example.myapplication.data.repository

import com.example.myapplication.data.db.PlaylistDao
import com.example.myapplication.data.db.PlaylistEntity
import com.example.myapplication.data.db.PlaylistSongDao
import com.example.myapplication.data.db.PlaylistSongEntity
import com.example.myapplication.data.db.toDomain
import com.example.myapplication.domain.model.Playlist
import com.example.myapplication.domain.model.Song
import com.example.myapplication.domain.repository.PlaylistRepository
import com.example.myapplication.domain.QueueOperations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class PlaylistRepositoryImpl(
    private val playlistDao: PlaylistDao,
    private val playlistSongDao: PlaylistSongDao,
    scope: CoroutineScope,
    /**
     * Runs a block of database calls in one transaction.
     *
     * The repository does not know about Room itself, which keeps it unit testable; the app
     * hands in the transaction of the real database.
     */
    private val transaction: suspend (suspend () -> Unit) -> Unit = { block -> block() },
) : PlaylistRepository {

    override val playlists: StateFlow<List<Playlist>> = playlistDao.observeSummaries()
        .map { summaries -> summaries.map { it.toDomain() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override fun songs(playlistId: Long): Flow<List<Song>> =
        playlistSongDao.observeSongs(playlistId)
            .map { entities -> entities.map { it.toDomain() } }

    override suspend fun songIds(playlistId: Long): List<Long> = playlistSongDao.songIds(playlistId)

    override suspend fun create(name: String): Long {
        val now = System.currentTimeMillis()
        return playlistDao.insert(
            PlaylistEntity(name = name.trim().ifEmpty { "Playlist" }, createdAt = now, updatedAt = now),
        )
    }

    override suspend fun rename(playlistId: Long, name: String) {
        playlistDao.rename(playlistId, name.trim().ifEmpty { "Playlist" }, System.currentTimeMillis())
    }

    override suspend fun delete(playlistId: Long) {
        transaction {
            playlistSongDao.clear(playlistId)
            playlistDao.delete(playlistId)
        }
    }

    override suspend fun addSong(playlistId: Long, mediaId: Long): Boolean {
        if (playlistSongDao.contains(playlistId, mediaId) > 0) return false
        val position = playlistSongDao.maxPosition(playlistId) + 1
        playlistSongDao.insert(
            PlaylistSongEntity(
                playlistId = playlistId,
                mediaId = mediaId,
                position = position,
                addedAt = System.currentTimeMillis(),
            ),
        )
        playlistDao.touch(playlistId, System.currentTimeMillis())
        return true
    }

    override suspend fun removeSong(playlistId: Long, mediaId: Long) {
        transaction {
            playlistSongDao.remove(playlistId, mediaId)
            val ids = playlistSongDao.songIds(playlistId)
            ids.forEachIndexed { index, id ->
                playlistSongDao.updatePosition(playlistId, id, index)
            }
            playlistDao.touch(playlistId, System.currentTimeMillis())
        }
    }

    override suspend fun reorder(playlistId: Long, from: Int, to: Int) {
        transaction {
            val ids = playlistSongDao.songIds(playlistId)
            val reordered = QueueOperations.move(ids, from, to)
            reordered.forEachIndexed { index, id ->
                playlistSongDao.updatePosition(playlistId, id, index)
            }
            playlistDao.touch(playlistId, System.currentTimeMillis())
        }
    }
}

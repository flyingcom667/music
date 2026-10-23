package com.example.myapplication.domain.repository

import com.example.myapplication.domain.model.Playlist
import com.example.myapplication.domain.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface PlaylistRepository {

    val playlists: StateFlow<List<Playlist>>

    fun songs(playlistId: Long): Flow<List<Song>>

    suspend fun songIds(playlistId: Long): List<Long>

    suspend fun create(name: String): Long

    suspend fun rename(playlistId: Long, name: String)

    suspend fun delete(playlistId: Long)

    /** Returns false when the song is already part of the playlist. */
    suspend fun addSong(playlistId: Long, mediaId: Long): Boolean

    suspend fun removeSong(playlistId: Long, mediaId: Long)

    suspend fun reorder(playlistId: Long, from: Int, to: Int)
}

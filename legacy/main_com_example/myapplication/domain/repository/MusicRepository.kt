package com.example.myapplication.domain.repository

import com.example.myapplication.domain.model.MetadataOverride
import com.example.myapplication.domain.model.Song
import com.example.myapplication.domain.model.SongMetadata
import kotlinx.coroutines.flow.StateFlow

/** Source of truth for the scanned library, favourites and user metadata overrides. */
interface MusicRepository {

    /** All visible songs. Hidden songs and excluded folders are already filtered out. */
    val songs: StateFlow<List<Song>>

    val folders: StateFlow<List<String>>

    val isLoading: StateFlow<Boolean>

    /** True once a library scan has completed at least once in this process. */
    val isLoaded: StateFlow<Boolean>

    val lastSync: StateFlow<Long>

    /** Rescans `MediaStore` and reconciles the database. Returns the number of songs found. */
    suspend fun sync(): Result<Int>

    suspend fun markPlayed(mediaId: Long)

    suspend fun setFavorite(mediaId: Long, favorite: Boolean)

    suspend fun toggleFavorite(mediaId: Long): Boolean

    /** Hides a song from the library (for files the app is not allowed to delete). */
    suspend fun hideSong(mediaId: Long)

    /**
     * Forgets songs whose files are gone: the rows are removed from every table so the
     * library does not show files that no longer exist.
     */
    suspend fun removeSongs(mediaIds: List<Long>)

    suspend fun unhideSong(mediaId: Long)

    /** The songs the user removed from the library; they are back in it after a restore. */
    suspend fun hiddenSongs(): List<Song>

    /** Shows every hidden song in the library again. */
    suspend fun restoreHiddenSongs()

    suspend fun saveOverride(mediaId: Long, override: MetadataOverride)

    suspend fun resetOverride(mediaId: Long)

    suspend fun getSong(mediaId: Long): Song?

    suspend fun getSongs(ids: List<Long>): List<Song>

    suspend fun getOverride(mediaId: Long): MetadataOverride

    /** Original (file) metadata, used by the editor to show what is being overridden. */
    suspend fun getBaseMetadata(mediaId: Long): SongMetadata?

    suspend fun hiddenCount(): Int

    suspend fun clearPlayStatistics()
}

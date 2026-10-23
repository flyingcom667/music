package com.example.myapplication.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cached copy of what `MediaStore` reports for an audio file. Nothing in this table is ever
 * written back to the file; user edits live in [SongOverrideEntity].
 */
@Entity(
    tableName = "songs",
    indices = [
        Index("titleKey"),
        Index("artistKey"),
        Index("albumKey"),
        Index("albumId"),
        Index("dateAdded"),
    ],
)
data class SongEntity(
    @PrimaryKey val mediaId: Long,
    val uri: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val albumArtist: String?,
    val trackNumber: Int?,
    val genre: String?,
    val year: Int?,
    val albumId: Long,
    val durationMs: Long,
    val displayName: String,
    val relativePath: String?,
    val folder: String?,
    val mimeType: String?,
    val sizeBytes: Long,
    val dateAdded: Long,
    val dateModified: Long,
    val titleKey: String,
    val artistKey: String,
    val albumKey: String,
    val albumArtistKey: String,
)

/**
 * The play statistics of a song.
 *
 * They live in their own table: the table with the songs is much bigger, and every time a song
 * is played only the statistics change. With a separate table the whole library does not have to
 * be read again after every play.
 */
@Entity(tableName = "play_stats")
data class PlayStatEntity(
    @PrimaryKey val mediaId: Long,
    val playCount: Int = 0,
    val lastPlayedAt: Long = 0L,
)

/** App-local metadata overrides. A `null` column means "fall back to [SongEntity]". */
@Entity(tableName = "song_overrides")
data class SongOverrideEntity(
    @PrimaryKey val mediaId: Long,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val trackNumber: Int? = null,
    val genre: String? = null,
    val year: Int? = null,
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val mediaId: Long,
    val addedAt: Long,
)

/** Tracks hidden from the library ("remove from library" for files the app cannot delete). */
@Entity(tableName = "hidden_songs")
data class HiddenSongEntity(
    @PrimaryKey val mediaId: Long,
    val hiddenAt: Long,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "mediaId"],
    indices = [Index("playlistId"), Index("mediaId")],
)
data class PlaylistSongEntity(
    val playlistId: Long,
    val mediaId: Long,
    val position: Int,
    val addedAt: Long,
)

/** Persisted playback queue; [position] is the play order. */
@Entity(tableName = "queue_items")
data class QueueItemEntity(
    @PrimaryKey val position: Int,
    val mediaId: Long,
)

/** Single row table (id = 1) holding the playback state that survives process death. */
@Entity(tableName = "playback_state")
data class PlaybackStateEntity(
    @PrimaryKey val id: Int = 1,
    val currentMediaId: Long = 0L,
    val positionMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = 0,
    val queueIds: String = "",
)

/** Projection returned by the playlist list query. */
data class PlaylistSummary(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val songCount: Int,
    val artworkMediaId: Long?,
)

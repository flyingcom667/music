package com.tidalmusic.player.data.database

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * A user-created playlist. Playlists own no audio files — deleting one never
 * touches the underlying media.
 */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val description: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * Join row between a playlist and a track.
 *
 * The track itself lives in the media provider; only its stable id is referenced
 * here so the app never duplicates audio metadata in its own database.
 */
@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "songId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playlistId"), Index("songId")],
)
data class PlaylistTrackEntity(
    val playlistId: Long,
    val songId: Long,
    val position: Int,
    val addedAt: Long,
)

/** A favourite track. [songId] is the media-store id. */
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val songId: Long,
    val addedAt: Long,
)

/**
 * One entry of the playback history.
 *
 * A track appears at most once; playing it again moves it to the top with a fresh
 * timestamp instead of inserting a duplicate row.
 */
@Entity(tableName = "recently_played")
data class RecentlyPlayedEntity(
    @PrimaryKey val songId: Long,
    val lastPlayedAt: Long,
    val playCount: Int,
)

/** A track the user chose to hide from the library. The file itself is untouched. */
@Entity(tableName = "hidden_tracks")
data class HiddenTrackEntity(
    @PrimaryKey val songId: Long,
    val hiddenAt: Long,
)

@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observeById(id: Long): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getById(id: Long): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): PlaylistEntity?

    @Query("SELECT COUNT(*) FROM playlists")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name, description = :description, updatedAt = :updatedAt WHERE id = :id")
    suspend fun update(id: Long, name: String, description: String?, updatedAt: Long)

    @Query("UPDATE playlists SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: Long, updatedAt: Long)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM playlist_tracks WHERE playlistId = :playlistId AND songId = :songId)")
    suspend fun containsSong(playlistId: Long, songId: Long): Boolean

    @Query("SELECT songId FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun songIdsIn(playlistId: Long): List<Long>

    @Query("SELECT songId FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC")
    fun observeSongIds(playlistId: Long): Flow<List<Long>>

    @Query("SELECT * FROM playlist_tracks ORDER BY playlistId ASC, position ASC")
    fun observeAllTracks(): Flow<List<PlaylistTrackEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTrack(track: PlaylistTrackEntity)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeTrack(playlistId: Long, songId: Long)

    @Query("DELETE FROM playlist_tracks WHERE songId = :songId")
    suspend fun removeTrackFromAllPlaylists(songId: Long)

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun maxPosition(playlistId: Long): Int

    @Query("UPDATE playlist_tracks SET position = :position WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun updatePosition(playlistId: Long, songId: Long, position: Int)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearTracks(playlistId: Long)
}

@Dao
interface FavoriteDao {

    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE songId = :songId)")
    fun observeIsFavorite(songId: Long): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE songId = :songId)")
    suspend fun isFavorite(songId: Long): Boolean

    @Query("SELECT songId FROM favorites")
    fun observeFavoriteIds(): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(favorite: FavoriteEntity): Long

    @Query("DELETE FROM favorites WHERE songId = :songId")
    suspend fun delete(songId: Long)

    @Query("SELECT COUNT(*) FROM favorites")
    fun observeCount(): Flow<Int>
}

@Dao
interface HistoryDao {

    @Query("SELECT * FROM recently_played ORDER BY lastPlayedAt DESC")
    fun observeAll(): Flow<List<RecentlyPlayedEntity>>

    @Query("SELECT * FROM recently_played ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<RecentlyPlayedEntity>>

    @Query("SELECT songId FROM recently_played ORDER BY lastPlayedAt DESC")
    fun observeIds(): Flow<List<Long>>

    @Query("SELECT * FROM recently_played WHERE songId = :songId")
    suspend fun get(songId: Long): RecentlyPlayedEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: RecentlyPlayedEntity)

    @Query("DELETE FROM recently_played WHERE songId = :songId")
    suspend fun delete(songId: Long)

    @Query("DELETE FROM recently_played")
    suspend fun clear()

    /** Keeps the history bounded so the database never grows without limit. */
    @Query(
        """
        DELETE FROM recently_played
        WHERE songId NOT IN (
            SELECT songId FROM recently_played
            ORDER BY lastPlayedAt DESC
            LIMIT :keep
        )
        """
    )
    suspend fun trimTo(keep: Int)
}

@Dao
interface HiddenTrackDao {

    @Query("SELECT * FROM hidden_tracks")
    fun observeAll(): Flow<List<HiddenTrackEntity>>

    @Query("SELECT songId FROM hidden_tracks")
    fun observeIds(): Flow<List<Long>>

    @Query("SELECT COUNT(*) FROM hidden_tracks")
    fun observeCount(): Flow<Int>

    @Query("SELECT EXISTS(SELECT 1 FROM hidden_tracks WHERE songId = :songId)")
    suspend fun isHidden(songId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: HiddenTrackEntity)

    @Query("DELETE FROM hidden_tracks WHERE songId = :songId")
    suspend fun delete(songId: Long)
}

/**
 * The app's Room database.
 *
 * Audio files themselves are never stored here — the media provider remains the
 * source of truth. Only app-owned relational data lives in Room.
 */
@Database(
    entities = [
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        FavoriteEntity::class,
        RecentlyPlayedEntity::class,
        HiddenTrackEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun playlistDao(): PlaylistDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao
    abstract fun hiddenTrackDao(): HiddenTrackDao

    companion object {
        const val NAME = "tidal_music.db"
    }
}

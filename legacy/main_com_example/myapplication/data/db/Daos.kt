package com.example.myapplication.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {

    @Query("SELECT * FROM songs")
    fun observeAll(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs")
    suspend fun getAll(): List<SongEntity>

    @Query("SELECT * FROM songs WHERE mediaId = :mediaId")
    suspend fun getById(mediaId: Long): SongEntity?

    @Query("SELECT * FROM songs WHERE mediaId IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<SongEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(songs: List<SongEntity>)

    @Query("DELETE FROM songs WHERE mediaId NOT IN (:ids)")
    suspend fun deleteMissing(ids: List<Long>)

    @Query("DELETE FROM songs WHERE mediaId = :mediaId")
    suspend fun deleteById(mediaId: Long)

    @Query("DELETE FROM songs WHERE mediaId IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM songs")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM songs")
    suspend fun count(): Int

    @Query("SELECT DISTINCT folder FROM songs WHERE folder IS NOT NULL")
    suspend fun folders(): List<String>
}

@Dao
interface PlayStatDao {

    @Query("SELECT * FROM play_stats")
    fun observeAll(): Flow<List<PlayStatEntity>>

    @Query("SELECT * FROM play_stats")
    suspend fun getAll(): List<PlayStatEntity>

    @Query("SELECT * FROM play_stats WHERE mediaId = :mediaId")
    suspend fun get(mediaId: Long): PlayStatEntity?

    @Query("SELECT * FROM play_stats WHERE mediaId IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<PlayStatEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stat: PlayStatEntity)

    @Query("DELETE FROM play_stats WHERE mediaId IN (:ids)")
    suspend fun deleteAll(ids: List<Long>)

    @Query("DELETE FROM play_stats")
    suspend fun clear()
}

@Dao
interface SongOverrideDao {

    @Query("SELECT * FROM song_overrides")
    fun observeAll(): Flow<List<SongOverrideEntity>>

    @Query("SELECT * FROM song_overrides WHERE mediaId = :mediaId")
    suspend fun get(mediaId: Long): SongOverrideEntity?

    @Query("SELECT * FROM song_overrides WHERE mediaId IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<SongOverrideEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(override: SongOverrideEntity)

    @Query("DELETE FROM song_overrides WHERE mediaId = :mediaId")
    suspend fun delete(mediaId: Long)

    @Query("DELETE FROM song_overrides WHERE mediaId IN (:ids)")
    suspend fun deleteAll(ids: List<Long>)

    @Query("DELETE FROM song_overrides")
    suspend fun clear()
}

@Dao
interface FavoriteDao {

    @Query("SELECT * FROM favorites")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE mediaId = :mediaId")
    suspend fun get(mediaId: Long): FavoriteEntity?

    @Query("SELECT * FROM favorites WHERE mediaId IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<FavoriteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE mediaId = :mediaId")
    suspend fun remove(mediaId: Long)

    @Query("DELETE FROM favorites WHERE mediaId IN (:ids)")
    suspend fun removeAll(ids: List<Long>)
}

@Dao
interface HiddenSongDao {

    @Query("SELECT * FROM hidden_songs")
    fun observeAll(): Flow<List<HiddenSongEntity>>

    @Query("SELECT * FROM hidden_songs")
    suspend fun getAll(): List<HiddenSongEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun hide(entity: HiddenSongEntity)

    @Query("DELETE FROM hidden_songs WHERE mediaId = :mediaId")
    suspend fun unhide(mediaId: Long)

    @Query("DELETE FROM hidden_songs WHERE mediaId IN (:ids)")
    suspend fun unhideAll(ids: List<Long>)

    @Query("DELETE FROM hidden_songs")
    suspend fun clear()
}

@Dao
interface PlaylistDao {

    @Query(
        """
        SELECT p.id AS id, p.name AS name, p.createdAt AS createdAt, p.updatedAt AS updatedAt,
            (SELECT COUNT(*) FROM playlist_songs ps WHERE ps.playlistId = p.id) AS songCount,
            (SELECT ps.mediaId FROM playlist_songs ps WHERE ps.playlistId = p.id
                ORDER BY ps.position LIMIT 1) AS artworkMediaId
        FROM playlists p
        ORDER BY p.name COLLATE NOCASE ASC
        """,
    )
    fun observeSummaries(): Flow<List<PlaylistSummary>>

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    fun observePlaylist(playlistId: Long): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getPlaylist(playlistId: Long): PlaylistEntity?

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name, updatedAt = :timestamp WHERE id = :playlistId")
    suspend fun rename(playlistId: Long, name: String, timestamp: Long)

    @Query("UPDATE playlists SET updatedAt = :timestamp WHERE id = :playlistId")
    suspend fun touch(playlistId: Long, timestamp: Long)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun delete(playlistId: Long)
}

@Dao
interface PlaylistSongDao {

    @Query(
        """
        SELECT s.* FROM songs s
        INNER JOIN playlist_songs ps ON s.mediaId = ps.mediaId
        WHERE ps.playlistId = :playlistId
        ORDER BY ps.position ASC
        """,
    )
    fun observeSongs(playlistId: Long): Flow<List<SongEntity>>

    @Query("SELECT mediaId FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun songIds(playlistId: Long): List<Long>

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun maxPosition(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: PlaylistSongEntity)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND mediaId = :mediaId")
    suspend fun remove(playlistId: Long, mediaId: Long)

    @Query("DELETE FROM playlist_songs WHERE mediaId IN (:ids)")
    suspend fun removeAll(ids: List<Long>)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun clear(playlistId: Long)

    @Query("UPDATE playlist_songs SET position = :position WHERE playlistId = :playlistId AND mediaId = :mediaId")
    suspend fun updatePosition(playlistId: Long, mediaId: Long, position: Int)

    @Query("SELECT COUNT(*) FROM playlist_songs WHERE playlistId = :playlistId AND mediaId = :mediaId")
    suspend fun contains(playlistId: Long, mediaId: Long): Int
}

@Dao
interface QueueDao {

    @Query("SELECT * FROM queue_items ORDER BY position ASC")
    fun observe(): Flow<List<QueueItemEntity>>

    @Query("SELECT * FROM queue_items ORDER BY position ASC")
    suspend fun getAll(): List<QueueItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<QueueItemEntity>)

    @Query("DELETE FROM queue_items")
    suspend fun clear()
}

@Dao
interface PlaybackStateDao {

    @Query("SELECT * FROM playback_state WHERE id = 1")
    fun observe(): Flow<PlaybackStateEntity?>

    @Query("SELECT * FROM playback_state WHERE id = 1")
    suspend fun get(): PlaybackStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(state: PlaybackStateEntity)
}

package com.example.myapplication.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SongEntity::class,
        PlayStatEntity::class,
        SongOverrideEntity::class,
        FavoriteEntity::class,
        HiddenSongEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        QueueItemEntity::class,
        PlaybackStateEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class MusicDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao
    abstract fun playStatDao(): PlayStatDao
    abstract fun songOverrideDao(): SongOverrideDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun hiddenSongDao(): HiddenSongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playlistSongDao(): PlaylistSongDao
    abstract fun queueDao(): QueueDao
    abstract fun playbackStateDao(): PlaybackStateDao

    companion object {
        private const val NAME = "music.db"

        fun build(context: Context): MusicDatabase =
            Room.databaseBuilder(context.applicationContext, MusicDatabase::class.java, NAME)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}

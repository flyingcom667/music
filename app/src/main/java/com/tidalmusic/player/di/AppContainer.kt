package com.tidalmusic.player.di

import android.content.Context
import androidx.room.Room
import com.tidalmusic.player.data.database.AppDatabase
import com.tidalmusic.player.data.local.MediaStoreScanner
import com.tidalmusic.player.data.local.MetadataEditor
import com.tidalmusic.player.data.preferences.AppPreferences
import com.tidalmusic.player.data.preferences.PlaybackStateStore
import com.tidalmusic.player.data.repository.LibraryRepository
import com.tidalmusic.player.data.repository.MediaStoreLibraryRepository
import com.tidalmusic.player.data.repository.RoomUserLibraryRepository
import com.tidalmusic.player.data.repository.UserLibraryRepository
import com.tidalmusic.player.playback.Media3PlaybackController
import com.tidalmusic.player.playback.PlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency injection.
 *
 * A single instance lives in [com.tidalmusic.player.TidalMusicApp] and is shared by the
 * Activity and the playback service. Constructor injection is used everywhere else, so no
 * DI framework is needed for an app of this size.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    /** Lives as long as the process; used for work that must outlive a screen. */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database: AppDatabase = Room.databaseBuilder(
        appContext,
        AppDatabase::class.java,
        AppDatabase.NAME,
    ).build()

    /** User preferences backed by DataStore. */
    val preferences: AppPreferences = AppPreferences(appContext)

    /** Last track and position, shared with the playback service. */
    val playbackStateStore: PlaybackStateStore = PlaybackStateStore(appContext)

    private val scanner: MediaStoreScanner by lazy { MediaStoreScanner(appContext) }

    /** Device audio, read from the system media provider. */
    val libraryRepository: LibraryRepository = MediaStoreLibraryRepository(scanner)

    /** App-owned data: playlists, favourites, history and hidden tracks. */
    val userLibraryRepository: UserLibraryRepository = RoomUserLibraryRepository(database)

    /**
     * Writes metadata back to the media provider, with user consent where required.
     * Created on first use: it is only needed from the edit-metadata flow.
     */
    val metadataEditor: MetadataEditor by lazy { MetadataEditor(appContext) }

    /**
     * The single playback client. It talks to the playback service through a Media3
     * `MediaController`, so playback survives navigation and configuration changes.
     *
     * Built lazily so that binding the service is never part of the cold-start path.
     */
    val playbackController: PlaybackController by lazy { Media3PlaybackController(appContext) }

    /** Total bytes of audio on the device, used by the settings storage summary. */
    suspend fun totalAudioBytes(includeNonMusic: Boolean): Long =
        scanner.totalAudioBytes(includeNonMusic)
}

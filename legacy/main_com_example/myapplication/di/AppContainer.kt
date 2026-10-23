package com.example.myapplication.di

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import coil3.ImageLoader
import coil3.SingletonImageLoader
import androidx.room.withTransaction
import coil3.request.crossfade
import com.example.myapplication.data.db.MusicDatabase
import com.example.myapplication.data.local.AudioFileProbe
import com.example.myapplication.data.local.ArtworkExtractor
import com.example.myapplication.data.local.ArtworkFetcherFactory
import com.example.myapplication.data.local.ExternalAudioReader
import com.example.myapplication.data.local.FileDeleter
import com.example.myapplication.data.local.MediaStoreScanner
import com.example.myapplication.data.repository.MusicRepositoryImpl
import com.example.myapplication.data.repository.PlaylistRepositoryImpl
import com.example.myapplication.data.repository.SettingsRepositoryImpl
import com.example.myapplication.domain.repository.MusicRepository
import com.example.myapplication.domain.repository.PlaylistRepository
import com.example.myapplication.domain.repository.SettingsRepository
import com.example.myapplication.playback.PlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manual dependency injection. A single instance lives in [com.example.myapplication.MusicApplication]
 * and is reachable from the service as well as the activity.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database: MusicDatabase = MusicDatabase.build(appContext)

    val settingsRepository: SettingsRepository = SettingsRepositoryImpl(appContext, applicationScope)

    private val mediaStoreScanner = MediaStoreScanner(appContext)

    val musicRepository: MusicRepository = MusicRepositoryImpl(
        songDao = database.songDao(),
        playStatDao = database.playStatDao(),
        overrideDao = database.songOverrideDao(),
        favoriteDao = database.favoriteDao(),
        hiddenDao = database.hiddenSongDao(),
        playlistSongDao = database.playlistSongDao(),
        scanner = mediaStoreScanner,
        settingsRepository = settingsRepository,
        scope = applicationScope,
        transaction = { block -> database.withTransaction { block() } },
    )

    val playlistRepository: PlaylistRepository = PlaylistRepositoryImpl(
        playlistDao = database.playlistDao(),
        playlistSongDao = database.playlistSongDao(),
        scope = applicationScope,
        transaction = { block -> database.withTransaction { block() } },
    )

    val artworkExtractor = ArtworkExtractor(appContext)

    val audioFileProbe = AudioFileProbe(appContext)

    /** Deletes audio files from the device. */
    val fileDeleter = FileDeleter(appContext)

    /** Reads the metadata of files that are opened from outside the app. */
    val externalAudioReader = ExternalAudioReader(appContext)

    val playbackController = PlaybackController(appContext, database, musicRepository)

    val imageLoader: ImageLoader = ImageLoader.Builder(appContext)
        .components { add(ArtworkFetcherFactory(artworkExtractor)) }
        // A short fade: a long one keeps the screen busy while the user scrolls through a
        // long list of covers.
        .crossfade(120)
        .build()

    /** One-shot scan used on first launch and whenever the permission is granted. */
    fun refreshLibrary() {
        applicationScope.launch { musicRepository.sync() }
    }

    /**
     * Cover art of a song as a bitmap.
     *
     * The media notification cannot use the composable image API, so the artwork is read with the
     * same extractor the UI uses and decoded into a (downscaled) bitmap.
     */
    suspend fun loadArtworkBitmap(mediaId: Long, albumId: Long): Bitmap? =
        withContext(Dispatchers.IO) {
            if (mediaId <= 0L) return@withContext null
            val bytes = artworkExtractor.bytes(mediaId, albumId) ?: return@withContext null
            decodeArtwork(bytes)
        }

    private fun decodeArtwork(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > ARTWORK_SIZE || bounds.outHeight / sample > ARTWORK_SIZE) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    fun installImageLoader() {
        SingletonImageLoader.setSafe { imageLoader }
    }

    private companion object {
        const val ARTWORK_SIZE = 512
    }
}

package com.example.myapplication.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.room.withTransaction
import com.example.myapplication.data.db.MusicDatabase
import com.example.myapplication.data.db.PlaybackStateEntity
import com.example.myapplication.data.db.QueueItemEntity
import com.example.myapplication.domain.QueueOperations
import com.example.myapplication.domain.model.QueueItem
import com.example.myapplication.domain.model.RepeatMode
import com.example.myapplication.domain.model.Song
import com.example.myapplication.domain.repository.MusicRepository
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Single app-scoped bridge between the UI and [MusicPlaybackService].
 *
 * It owns a [MediaController], exposes the playback state as flows, keeps the queue and the
 * playback position in Room so playback survives process death, and turns domain songs into
 * `MediaItem`s.
 */
class PlaybackController(
    private val context: Context,
    private val database: MusicDatabase,
    private val musicRepository: MusicRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private val _progress = MutableStateFlow(PlaybackProgress())
    val progress: StateFlow<PlaybackProgress> = _progress.asStateFlow()

    private val _queue = MutableStateFlow<List<QueueItem>>(emptyList())
    val queue: StateFlow<List<QueueItem>> = _queue.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var progressJob: Job? = null
    private var lastPersistedIds: List<Long> = emptyList()
    private var lastMarkedId: Long = 0L
    private var restoreAttempted = false

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            syncState()
            player.playerError?.let { error ->
                _state.value = _state.value.copy(errorMessage = error.message)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.value = _state.value.copy(errorMessage = error.message ?: error.errorCodeName)
            _messages.tryEmit("Unable to play this file")
        }
    }

    fun connect() {
        if (controller != null || controllerFuture != null) return
        val token = SessionToken(context, ComponentName(context, MusicPlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess { mediaController ->
                        if (controller == null) {
                            onConnected(mediaController)
                        } else {
                            mediaController.release()
                        }
                    }
                    .onFailure { error ->
                        _state.value = _state.value.copy(
                            isConnected = false,
                            errorMessage = error.message,
                        )
                    }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    private fun onConnected(mediaController: MediaController) {
        controller = mediaController
        mediaController.addListener(listener)
        syncState()
        startProgressLoop()
        if (!restoreAttempted) {
            restoreAttempted = true
            scope.launch { restoreQueue() }
        }
    }

    fun release() {
        progressJob?.cancel()
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        scope.cancel()
    }

    // region commands

    fun play(songs: List<Song>, startIndex: Int = 0) {
        val mediaController = controller ?: return
        if (songs.isEmpty()) return
        val items = songs.map { it.toMediaItem() }
        mediaController.setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), 0L)
        mediaController.prepare()
        mediaController.play()
    }

    fun playSong(song: Song) = play(listOf(song), 0)

    /**
     * Plays a file that is not part of the library, e.g. one that was opened from a file
     * manager with an `ACTION_VIEW` intent.
     *
     * The file gets a negative media id so it can never collide with a MediaStore id; the
     * library therefore does not try to look up a song for it.
     */
    fun playExternal(
        uri: String,
        title: String,
        artist: String?,
        album: String?,
        durationMs: Long,
    ) {
        val mediaController = controller ?: return
        val item = MediaItem.Builder()
            .setMediaId(externalMediaId(uri).toString())
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setDurationMs(durationMs)
                    .build(),
            )
            .build()
        mediaController.setMediaItems(listOf(item), 0, 0L)
        mediaController.prepare()
        mediaController.play()
    }

    fun playSongsShuffled(songs: List<Song>) {
        val mediaController = controller ?: return
        if (songs.isEmpty()) return
        val items = songs.map { it.toMediaItem() }
        mediaController.shuffleModeEnabled = true
        mediaController.setMediaItems(items, 0, 0L)
        mediaController.prepare()
        mediaController.play()
    }

    fun togglePlayPause() {
        val mediaController = controller ?: return
        if (mediaController.isPlaying) {
            mediaController.pause()
        } else {
            if (mediaController.playbackState == Player.STATE_IDLE) mediaController.prepare()
            mediaController.play()
        }
    }

    fun playPauseIfCurrent(mediaId: Long) {
        if (_state.value.mediaId == mediaId) togglePlayPause() else resume()
    }

    fun resume() {
        val mediaController = controller ?: return
        if (mediaController.mediaItemCount == 0) return
        if (mediaController.playbackState == Player.STATE_IDLE) mediaController.prepare()
        mediaController.play()
    }

    fun pause() {
        controller?.pause()
    }

    fun next() {
        val mediaController = controller ?: return
        if (mediaController.hasNextMediaItem()) {
            mediaController.seekToNextMediaItem()
        } else {
            mediaController.seekToDefaultPosition(0)
        }
    }

    fun previous() {
        val mediaController = controller ?: return
        // Restart the current track unless the user is within the first seconds.
        if (mediaController.currentPosition > RESTART_THRESHOLD_MS) {
            mediaController.seekTo(0L)
        } else if (mediaController.hasPreviousMediaItem()) {
            mediaController.seekToPreviousMediaItem()
        } else {
            mediaController.seekTo(0L)
        }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun seekToFraction(fraction: Float) {
        val mediaController = controller ?: return
        val duration = mediaController.duration
        if (duration > 0) {
            mediaController.seekTo((duration * fraction.coerceIn(0f, 1f)).toLong())
        }
    }

    /**
     * Moves the playback forward or backward inside the current track.
     *
     * A negative value goes back, a positive one forward. The position never leaves the track.
     */
    fun seekBy(deltaMs: Long) {
        val mediaController = controller ?: return
        val target = mediaController.currentPosition + deltaMs
        val duration = mediaController.duration
        mediaController.seekTo(
            if (duration > 0L) target.coerceIn(0L, duration) else target.coerceAtLeast(0L),
        )
    }

    fun setShuffle(enabled: Boolean) {
        controller?.shuffleModeEnabled = enabled
    }

    fun toggleShuffle() {
        val mediaController = controller ?: return
        mediaController.shuffleModeEnabled = PlayerOptions.nextShuffleMode(mediaController.shuffleModeEnabled)
    }

    fun cycleRepeat() {
        val mediaController = controller ?: return
        setRepeatMode(PlayerOptions.nextRepeatMode(mediaController.repeatMode.toRepeatMode()))
    }

    fun setRepeatMode(mode: RepeatMode) {
        controller?.repeatMode = mode.toPlayerRepeatMode()
    }

    fun setSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed.coerceIn(0.25f, 3f))
    }

    fun playNext(song: Song) {
        val mediaController = controller ?: return
        if (mediaController.mediaItemCount == 0) {
            playSong(song)
            return
        }
        val index = QueueOperations.playNextIndex(
            mediaController.currentMediaItemIndex,
            mediaController.mediaItemCount,
        )
        mediaController.addMediaItem(index, song.toMediaItem())
        _messages.tryEmit("Added to play next")
    }

    fun addToQueue(song: Song) {
        val mediaController = controller ?: return
        if (mediaController.mediaItemCount == 0) {
            playSong(song)
            return
        }
        mediaController.addMediaItem(song.toMediaItem())
        _messages.tryEmit("Added to queue")
    }

    fun addAllToQueue(songs: List<Song>) {
        val mediaController = controller ?: return
        if (songs.isEmpty()) return
        if (mediaController.mediaItemCount == 0) {
            play(songs, 0)
            return
        }
        mediaController.addMediaItems(songs.map { it.toMediaItem() })
        _messages.tryEmit("${songs.size} songs added to queue")
    }

    fun moveQueueItem(from: Int, to: Int) {
        val mediaController = controller ?: return
        if (from !in 0 until mediaController.mediaItemCount) return
        mediaController.moveMediaItem(from, to.coerceIn(0, mediaController.mediaItemCount - 1))
    }

    fun removeQueueItem(index: Int) {
        val mediaController = controller ?: return
        if (index !in 0 until mediaController.mediaItemCount) return
        mediaController.removeMediaItem(index)
    }

    fun jumpToQueueItem(index: Int) {
        val mediaController = controller ?: return
        if (index !in 0 until mediaController.mediaItemCount) return
        mediaController.seekToDefaultPosition(index)
        mediaController.play()
    }

    fun clearQueue() {
        val mediaController = controller ?: return
        mediaController.stop()
        mediaController.clearMediaItems()
        _queue.value = emptyList()
        scope.launch {
            database.withTransaction {
                database.queueDao().clear()
                database.playbackStateDao().save(PlaybackStateEntity())
            }
        }
    }

    /**
     * Stops playback for good: the queue is cleared, the notification is removed and the service
     * shuts itself down. Used by the player's close button.
     */
    fun stopAndDismiss() {
        clearQueue()
        _progress.value = PlaybackProgress()
    }

    // endregion

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch {
            var tick = 0
            var lastPosition = -1L
            while (isActive) {
                var playing = false
                controller?.let { mediaController ->
                    val duration = mediaController.duration
                    val position = mediaController.currentPosition.coerceAtLeast(0L)
                    playing = mediaController.isPlaying
                    // While nothing is played the position does not change, so the state is
                    // only published when it really changed.
                    if (playing || position != lastPosition) {
                        _progress.value = PlaybackProgress(
                            positionMs = position,
                            durationMs = if (duration > 0L) duration else _state.value.durationMs,
                        )
                        lastPosition = position
                    }
                    if (tick % 10 == 0) persistPlaybackState(mediaController)
                }
                tick++
                // A paused song does not need a fast loop, so the app stays quiet.
                delay(if (playing) PROGRESS_INTERVAL_MS else IDLE_PROGRESS_INTERVAL_MS)
            }
        }
    }

    private fun syncState() {
        val mediaController = controller ?: return
        val count = mediaController.mediaItemCount
        val index = mediaController.currentMediaItemIndex
        val current = if (index in 0 until count) mediaController.getMediaItemAt(index) else null
        val metadata = current?.mediaMetadata
        val mediaId = current?.mediaId?.toLongOrNull() ?: 0L
        val duration = mediaController.duration

        _state.value = PlaybackUiState(
            isConnected = true,
            isPlaying = mediaController.isPlaying,
            isBuffering = mediaController.playbackState == Player.STATE_BUFFERING,
            mediaId = mediaId,
            title = metadata?.title?.toString().orEmpty(),
            artist = metadata?.artist?.toString()?.takeIf { it.isNotBlank() },
            album = metadata?.albumTitle?.toString()?.takeIf { it.isNotBlank() },
            albumId = metadata?.extras?.getLong(MediaItemExtra.ALBUM_ID) ?: 0L,
            durationMs = if (duration > 0L) duration else metadata?.durationMs ?: 0L,
            currentIndex = index,
            queueSize = count,
            shuffle = mediaController.shuffleModeEnabled,
            repeatMode = mediaController.repeatMode.toRepeatMode(),
            speed = mediaController.playbackParameters.speed,
            errorMessage = mediaController.playerError?.message,
        )

        _queue.value = buildList(count) {
            for (position in 0 until count) {
                val item = mediaController.getMediaItemAt(position)
                val itemMetadata = item.mediaMetadata
                add(
                    QueueItem(
                        mediaId = item.mediaId.toLongOrNull() ?: 0L,
                        title = itemMetadata.title?.toString().orEmpty(),
                        artist = itemMetadata.artist?.toString()?.takeIf { it.isNotBlank() },
                        albumId = itemMetadata.extras?.getLong(MediaItemExtra.ALBUM_ID) ?: 0L,
                        durationMs = itemMetadata.durationMs ?: 0L,
                    ),
                )
            }
        }

        if (mediaId != 0L && mediaId != lastMarkedId && mediaController.isPlaying) {
            lastMarkedId = mediaId
            scope.launch { musicRepository.markPlayed(mediaId) }
        }

        persistQueue(_queue.value.map { it.mediaId })
    }

    private fun persistQueue(ids: List<Long>) {
        if (ids == lastPersistedIds) return
        lastPersistedIds = ids
        scope.launch {
            database.withTransaction {
                database.queueDao().clear()
                if (ids.isNotEmpty()) {
                    database.queueDao().insertAll(
                        ids.mapIndexed { index, id -> QueueItemEntity(position = index, mediaId = id) },
                    )
                }
            }
        }
    }

    private suspend fun persistPlaybackState(mediaController: MediaController) {
        val current = _state.value
        if (current.mediaId == 0L) return
        database.playbackStateDao().save(
            PlaybackStateEntity(
                currentMediaId = current.mediaId,
                positionMs = mediaController.currentPosition.coerceAtLeast(0L),
                shuffle = mediaController.shuffleModeEnabled,
                repeatMode = mediaController.repeatMode,
            ),
        )
    }

    private suspend fun restoreQueue() {
        val mediaController = controller ?: return
        if (mediaController.mediaItemCount > 0) return
        val ids = database.queueDao().getAll().map { it.mediaId }
        if (ids.isEmpty()) return
        val songs = musicRepository.getSongs(ids)
        if (songs.isEmpty()) {
            // Only stale entries (for example a file that was opened from outside) are left.
            database.queueDao().clear()
            return
        }
        val saved = database.playbackStateDao().get()
        val startIndex = songs.indexOfFirst { it.mediaId == saved?.currentMediaId }
            .takeIf { it >= 0 } ?: 0
        lastPersistedIds = ids
        mediaController.setMediaItems(songs.map { it.toMediaItem() }, startIndex, 0L)
        mediaController.repeatMode = saved?.repeatMode ?: Player.REPEAT_MODE_OFF
        mediaController.shuffleModeEnabled = saved?.shuffle ?: false
        mediaController.prepare()
        val position = saved?.positionMs ?: 0L
        if (position > 0L) mediaController.seekTo(position)
        syncState()
    }

    /** Restores the last position without starting playback (used on cold start). */
    fun restoreLastPosition() {
        scope.launch {
            if (controller?.mediaItemCount == 0) restoreQueue()
        }
    }

    private fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(mediaId.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setDurationMs(durationMs)
                .setExtras(Bundle().apply { putLong(MediaItemExtra.ALBUM_ID, albumId) })
                .build(),
        )
        .build()

    private fun RepeatMode.toPlayerRepeatMode(): Int = PlayerOptions.toPlayerRepeatMode(this)

    private fun Int.toRepeatMode(): RepeatMode = PlayerOptions.toRepeatMode(this)

    companion object {
        // 200 ms keeps the wavy seek bar smooth without waking the UI thread too often.
        const val PROGRESS_INTERVAL_MS = 200L

        /** The delay between two updates while nothing is played. */
        const val IDLE_PROGRESS_INTERVAL_MS = 1_000L
        const val RESTART_THRESHOLD_MS = 3_000L

        /**
         * The media id of a file that is not part of the library.
         *
         * It is negative so it can never be mistaken for a MediaStore id.
         */
        fun externalMediaId(uri: String): Long =
            -(uri.hashCode().toLong().and(0x7FFFFFFFL) + 1L)
    }
}

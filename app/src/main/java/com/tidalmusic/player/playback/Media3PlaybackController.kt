package com.tidalmusic.player.playback

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.tidalmusic.player.domain.model.PlaybackProgress
import com.tidalmusic.player.domain.model.PlaybackState
import com.tidalmusic.player.domain.model.QueueItem
import com.tidalmusic.player.domain.model.RepeatMode
import com.tidalmusic.player.domain.model.Song
import com.tidalmusic.player.playback.service.PlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Collections
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * The production [PlaybackController], backed by a Media3 `MediaController` bound to
 * [PlaybackService].
 *
 * Design notes:
 *  - The service owns the only real `ExoPlayer`. This class is a thin, long-lived
 *    client, so it survives navigation and never leaks an Activity.
 *  - The player's timeline is the single source of truth for the queue. Every queue
 *    mutation is issued to the player first and the displayed queue is then rebuilt
 *    from the player's timeline — the UI can never drift out of sync with the audio.
 *  - Shuffling is done by the app (rebuilding the timeline in a shuffled order)
 *    rather than by Media3's internal shuffle, because the app must be able to show
 *    and reorder the queue it will actually play.
 */
class Media3PlaybackController(
    private val context: Context,
) : PlaybackController {

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var controller: MediaController? = null
    private var connectJob: Job? = null
    private var progressJob: Job? = null

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _progress = MutableStateFlow(PlaybackProgress())
    override val progress: StateFlow<PlaybackProgress> = _progress.asStateFlow()

    private val _queue = MutableStateFlow<List<QueueItem>>(emptyList())
    override val queue: StateFlow<List<QueueItem>> = _queue.asStateFlow()

    private val _events = MutableSharedFlow<PlaybackEvent>(extraBufferCapacity = 16)
    override val events: SharedFlow<PlaybackEvent> = _events.asSharedFlow()

    /**
     * The order the user built the queue in. Used to restore the un-shuffled order
     * when the shuffle switch is turned off.
     */
    private val baseOrder = mutableListOf<Song>()

    /** Full metadata for the tracks currently in the timeline. */
    private val catalog = mutableMapOf<Long, Song>()

    private var shuffleEnabled = false

    /**
     * A play request that arrived before the [MediaController] finished connecting.
     *
     * A cold start can make the library usable before the service binding completes; the
     * request is kept and flushed right after the connection instead of being dropped
     * silently, so the first tap on a song always starts playback.
     */
    private var pendingPlay: PendingPlay? = null

    private data class PendingPlay(val songs: List<Song>, val startIndex: Int, val shuffle: Boolean)

    // ------------------------------------------------------------------ connection

    override suspend fun connect() {
        if (controller != null) {
            flushPendingPlay()
            return
        }
        if (connectJob?.isActive == true) {
            connectJob?.join()
            return
        }
        connectJob = scope.launch {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val mediaController = try {
                suspendCancellableCoroutine<MediaController> { continuation ->
                    val future = MediaController.Builder(context, token).buildAsync()
                    future.addListener(
                        {
                            try {
                                continuation.resume(future.get())
                            } catch (e: Exception) {
                                Log.e(TAG, "Unable to connect to the playback service", e)
                                continuation.resumeWith(Result.failure(e))
                            }
                        },
                        MoreExecutors.directExecutor(),
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unable to connect to the playback service", e)
                return@launch
            }

            controller = mediaController
            mediaController.addListener(playerListener)
            rebuildQueueFromTimeline()
            refreshState()
            startProgressUpdates()
            flushPendingPlay()
        }
        connectJob?.join()
    }

    override fun release() {
        progressJob?.cancel()
        connectJob?.cancel()
        controller?.removeListener(playerListener)
        controller?.release()
        controller = null
    }

    // ------------------------------------------------------------------ playback

    override fun play(songs: List<Song>, startIndex: Int, shuffle: Boolean) {
        if (songs.isEmpty()) return
        val mediaController = controller
        if (mediaController == null) {
            // Not connected yet: keep the request and start connecting. Flushed in connect().
            pendingPlay = PendingPlay(songs, startIndex, shuffle)
            scope.launch { connect() }
            return
        }
        val safeIndex = startIndex.coerceIn(0, songs.lastIndex)
        baseOrder.clear()
        baseOrder.addAll(songs)
        catalog.clear()
        songs.forEach { catalog[it.id] = it }

        shuffleEnabled = shuffle
        val order = if (shuffle) shuffledWithFirst(songs, safeIndex) else songs.toList()
        val startPosition = order.indexOfFirst { it.id == songs[safeIndex].id }.coerceAtLeast(0)

        mediaController.setMediaItems(order.map { MediaItemMapper.toMediaItem(it) }, startPosition, 0L)
        mediaController.prepare()
        mediaController.play()
        rebuildQueueFromTimeline()
    }

    /** Runs a play request that was made before the connection was ready. */
    private fun flushPendingPlay() {
        val pending = pendingPlay ?: return
        pendingPlay = null
        play(pending.songs, pending.startIndex, pending.shuffle)
    }

    override fun play(song: Song, queue: List<Song>, startIndex: Int) {
        val order = queue.ifEmpty { listOf(song) }
        val index = order.indexOfFirst { it.id == song.id }.takeIf { it >= 0 } ?: startIndex
        play(order, index, shuffle = false)
    }

    override fun restoreSession(songs: List<Song>, startIndex: Int, positionMs: Long) {
        if (songs.isEmpty()) return
        val mediaController = controller ?: return
        val safeIndex = startIndex.coerceIn(0, songs.lastIndex)
        baseOrder.clear()
        baseOrder.addAll(songs)
        catalog.clear()
        songs.forEach { catalog[it.id] = it }
        shuffleEnabled = false
        mediaController.setMediaItems(songs.map { MediaItemMapper.toMediaItem(it) }, safeIndex, positionMs)
        mediaController.prepare()
        mediaController.pause()
        rebuildQueueFromTimeline()
        refreshState()
    }

    override fun playNext(song: Song) {
        val mediaController = controller ?: return
        catalog[song.id] = song
        val currentId = mediaController.currentMediaItem?.mediaId?.toLongOrNull()
        val baseIndex = currentId?.let { id -> baseOrder.indexOfFirst { it.id == id } } ?: -1
        baseOrder.add((baseIndex + 1).coerceAtLeast(0), song)
        // the base order is only used to restore the unshuffled sequence.
        val insertAt = (mediaController.currentMediaItemIndex + 1)
            .coerceIn(0, mediaController.mediaItemCount)
        mediaController.addMediaItem(insertAt, MediaItemMapper.toMediaItem(song))
    }

    override fun addToQueue(song: Song) {
        val mediaController = controller ?: return
        catalog[song.id] = song
        baseOrder.add(song)
        mediaController.addMediaItem(mediaController.mediaItemCount, MediaItemMapper.toMediaItem(song))
    }

    override fun addToQueue(songs: List<Song>) {
        val mediaController = controller ?: return
        if (songs.isEmpty()) return
        songs.forEach { catalog[it.id] = it }
        baseOrder.addAll(songs)
        mediaController.addMediaItems(
            mediaController.mediaItemCount,
            songs.map { MediaItemMapper.toMediaItem(it) },
        )
    }

    override fun togglePlayPause() {
        val mediaController = controller ?: return
        if (mediaController.isPlaying) mediaController.pause() else mediaController.play()
    }

    override fun play() {
        controller?.play()
    }

    override fun pause() {
        controller?.pause()
    }

    override fun next() {
        val mediaController = controller ?: return
        if (mediaController.hasNextMediaItem()) {
            mediaController.seekToNextMediaItem()
            mediaController.play()
        }
    }

    override fun previous() {
        val mediaController = controller ?: return
        // Standard behaviour: restart the track unless it just started.
        if (mediaController.currentPosition > RESTART_THRESHOLD_MS && mediaController.hasPreviousMediaItem()) {
            mediaController.seekTo(0)
        } else if (mediaController.hasPreviousMediaItem()) {
            mediaController.seekToPreviousMediaItem()
        } else {
            mediaController.seekTo(0)
        }
        mediaController.play()
    }

    override fun seekTo(positionMs: Long) {
        val mediaController = controller ?: return
        val duration = mediaController.duration
        val target = if (duration > 0) positionMs.coerceIn(0, duration) else positionMs.coerceAtLeast(0)
        mediaController.seekTo(target)
        _progress.value = _progress.value.copy(positionMs = target)
    }

    override fun setShuffleEnabled(enabled: Boolean) {
        if (shuffleEnabled == enabled) return
        val mediaController = controller ?: return
        val currentIndex = mediaController.currentMediaItemIndex
        val currentSong = songAt(currentIndex) ?: return
        val currentPosition = mediaController.currentPosition.coerceAtLeast(0L)
        val wasPlaying = mediaController.playWhenReady
        val newOrder = if (enabled) {
            val timelineSongs = (0 until mediaController.mediaItemCount).mapNotNull(::songAt)
            val timelineCurrentIndex = currentIndex
            if (timelineSongs.getOrNull(timelineCurrentIndex) == null) return
            val upcoming = timelineSongs
                .filterIndexed { index, _ -> index != timelineCurrentIndex }
                .shuffled()
            listOf(currentSong) + upcoming
        } else {
            val restored = baseOrder.toList()
            if (restored.isEmpty() || restored.none { it.id == currentSong.id }) return
            restored
        }

        catalog.putAll(newOrder.associateBy { it.id })
        val newCurrentIndex = if (enabled) 0 else newOrder.indexOfFirst { it.id == currentSong.id }
        mediaController.setMediaItems(
            newOrder.map(MediaItemMapper::toMediaItem),
            newCurrentIndex,
            currentPosition,
        )
        mediaController.prepare()
        if (wasPlaying) mediaController.play() else mediaController.pause()
        shuffleEnabled = enabled
        rebuildQueueFromTimeline()
        refreshState()
    }

    override fun cycleRepeatMode() {
        setRepeatMode(currentRepeatMode().next())
    }

    override fun setRepeatMode(mode: RepeatMode) {
        val mediaController = controller ?: return
        mediaController.repeatMode = when (mode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
        refreshState()
    }

    override fun setPlaybackSpeed(speed: Float) {
        val mediaController = controller ?: return
        mediaController.setPlaybackSpeed(speed.coerceIn(0.5f, 2.0f))
        refreshState()
    }

    override fun removeFromQueue(index: Int) {
        val mediaController = controller ?: return
        if (index !in 0 until mediaController.mediaItemCount) return
        val removed = songAt(index)
        mediaController.removeMediaItem(index)
        removed?.let { baseOrder.removeAll { song -> song.id == it.id } }
    }

    override fun moveQueueItem(from: Int, to: Int) {
        val mediaController = controller ?: return
        if (from !in 0 until mediaController.mediaItemCount) return
        if (to !in 0 until mediaController.mediaItemCount) return
        mediaController.moveMediaItem(from, to)
        // Keep the base order consistent so that switching shuffle off restores a
        // sensible sequence.
        val moved = baseOrder.getOrNull(from)
        if (moved != null && to in baseOrder.indices) {
            baseOrder.removeAt(from)
            baseOrder.add(to, moved)
        }
    }

    override fun skipToQueueItem(index: Int) {
        val mediaController = controller ?: return
        if (index !in 0 until mediaController.mediaItemCount) return
        mediaController.seekTo(index, 0L)
        mediaController.play()
    }

    override fun clearQueue() {
        val mediaController = controller ?: return
        val currentIndex = mediaController.currentMediaItemIndex
        if (currentIndex >= 0) {
            if (currentIndex > 0) mediaController.removeMediaItems(0, currentIndex)
            val remaining = mediaController.mediaItemCount - currentIndex - 1
            if (remaining > 0) mediaController.removeMediaItems(currentIndex + 1, mediaController.mediaItemCount)
        } else {
            mediaController.clearMediaItems()
        }
        baseOrder.clear()
        catalog.clear()
        _events.tryEmit(PlaybackEvent.QueueCleared)
    }

    override fun stop() {
        controller?.stop()
        controller?.clearMediaItems()
        baseOrder.clear()
        catalog.clear()
        rebuildQueueFromTimeline()
    }

    override fun setVolume(volume: Float) {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val requestedVolume = (volume.coerceIn(0f, 1f) * maxVolume).roundToInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, requestedVolume, 0)
    }

    override fun volume(): Float {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (maxVolume <= 0) return 1f
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
    }

    // ------------------------------------------------------------------ internals

    private fun currentSong(): Song? {
        val mediaController = controller ?: return null
        val item = mediaController.currentMediaItem ?: return null
        val id = item.mediaId.toLongOrNull()
        val known = id?.let { catalog[it] }
        return known ?: MediaItemMapper.toSong(item)
    }

    private fun songAt(index: Int): Song? {
        val mediaController = controller ?: return null
        if (index !in 0 until mediaController.mediaItemCount) return null
        val item = mediaController.getMediaItemAt(index)
        val id = item.mediaId.toLongOrNull()
        val known = id?.let { catalog[it] }
        return known ?: MediaItemMapper.toSong(item)
    }

    /** Rebuilds the exposed queue from the player's timeline — the source of truth. */
    private fun rebuildQueueFromTimeline() {
        val mediaController = controller ?: return
        val currentIndex = mediaController.currentMediaItemIndex
        val items = ArrayList<QueueItem>(mediaController.mediaItemCount)
        for (index in 0 until mediaController.mediaItemCount) {
            val song = songAt(index) ?: continue
            items += QueueItem(index = index, song = song, isCurrent = index == currentIndex)
        }
        _queue.value = items
    }

    private fun currentRepeatMode(): RepeatMode {
        val mediaController = controller ?: return RepeatMode.OFF
        return when (mediaController.repeatMode) {
            Player.REPEAT_MODE_ALL -> RepeatMode.ALL
            Player.REPEAT_MODE_ONE -> RepeatMode.ONE
            else -> RepeatMode.OFF
        }
    }

    private fun refreshState() {
        val mediaController = controller ?: return
        val song = currentSong()
        val duration = mediaController.duration.takeIf { it > 0 } ?: song?.durationMs ?: 0L
        _state.value = PlaybackState(
            currentSong = song,
            isPlaying = mediaController.isPlaying,
            isBuffering = mediaController.playbackState == Player.STATE_BUFFERING,
            shuffleEnabled = shuffleEnabled,
            repeatMode = currentRepeatMode(),
            playbackSpeed = mediaController.playbackParameters.speed,
            hasNext = mediaController.hasNextMediaItem(),
            hasPrevious = mediaController.hasPreviousMediaItem(),
            queueSize = mediaController.mediaItemCount,
            errorMessage = null,
        )
        _progress.value = _progress.value.copy(
            positionMs = mediaController.currentPosition.coerceAtLeast(0),
            durationMs = duration,
            bufferedMs = mediaController.bufferedPosition.coerceAtLeast(0),
        )
    }

    /** Polls the position while playing so the slider advances smoothly. */
    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val mediaController = controller
                if (mediaController != null && mediaController.isPlaying) {
                    _progress.value = _progress.value.copy(
                        positionMs = mediaController.currentPosition.coerceAtLeast(0),
                        durationMs = mediaController.duration.takeIf { it > 0 }
                            ?: _progress.value.durationMs,
                        bufferedMs = mediaController.bufferedPosition.coerceAtLeast(0),
                    )
                }
                delay(PROGRESS_INTERVAL_MS)
            }
        }
    }

    private val playerListener = object : Player.Listener {

        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_TIMELINE_CHANGED)) {
                rebuildQueueFromTimeline()
            }
            refreshState()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            rebuildQueueFromTimeline()
            refreshState()
            emitTrackStartedIfPlaying()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            refreshState()
            if (isPlaying) emitTrackStartedIfPlaying()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            refreshState()
        }

        override fun onPlayerError(error: PlaybackException) {
            val song = currentSong()
            _state.value = _state.value.copy(errorMessage = error.errorCodeName)
            _events.tryEmit(
                PlaybackEvent.Error(song, error.errorCodeName),
            )
        }

        private fun emitTrackStartedIfPlaying() {
            val mediaController = controller ?: return
            if (!mediaController.isPlaying) return
            currentSong()?.let { _events.tryEmit(PlaybackEvent.TrackStarted(it)) }
        }
    }

    /**
     * Produces a shuffled order that still starts with [firstIndex].
     *
     * The chosen track goes first so that "shuffle and play this song" behaves the
     * way users expect.
     */
    private fun shuffledWithFirst(songs: List<Song>, firstIndex: Int): List<Song> {
        val chosen = songs[firstIndex]
        val rest = songs.filterIndexed { index, _ -> index != firstIndex }.toMutableList()
        Collections.shuffle(rest)
        return buildList {
            add(chosen)
            addAll(rest)
        }
    }

    companion object {
        private const val TAG = "Media3PlaybackController"
        private const val PROGRESS_INTERVAL_MS = 500L
        private const val RESTART_THRESHOLD_MS = 3_000L
    }
}

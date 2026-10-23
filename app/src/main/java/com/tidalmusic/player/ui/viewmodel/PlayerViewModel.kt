package com.tidalmusic.player.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tidalmusic.player.R
import com.tidalmusic.player.data.preferences.AppPreferences
import com.tidalmusic.player.data.preferences.PlaybackSessionStore
import com.tidalmusic.player.data.preferences.SettingsStore
import com.tidalmusic.player.data.repository.UserLibraryRepository
import com.tidalmusic.player.domain.model.PlaybackProgress
import com.tidalmusic.player.domain.model.PlaybackState
import com.tidalmusic.player.domain.model.QueueItem
import com.tidalmusic.player.domain.model.RepeatMode
import com.tidalmusic.player.domain.model.Song
import com.tidalmusic.player.playback.PlaybackController
import com.tidalmusic.player.playback.PlaybackEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** The sleep timer, as shown in the player sheet. */
data class SleepTimerState(
    val endsAtEpochMs: Long? = null,
    val remainingMs: Long = 0L,
) {
    val isActive: Boolean get() = endsAtEpochMs != null
}

/**
 * The playback screen model.
 *
 * It is a thin, immutable-state façade over [PlaybackController]: it never touches
 * ExoPlayer directly, so the mini-player, the full player and the queue sheet all read the
 * same source of truth.
 */
class PlayerViewModel(
    private val controller: PlaybackController,
    private val userLibraryRepository: UserLibraryRepository,
    private val preferences: SettingsStore,
    private val stateStore: PlaybackSessionStore,
) : ViewModel() {

    val state: StateFlow<PlaybackState> = controller.state
    val progress: StateFlow<PlaybackProgress> = controller.progress
    val queue: StateFlow<List<QueueItem>> = controller.queue

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    private val _volume = MutableStateFlow(1f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _sleepTimer = MutableStateFlow(SleepTimerState())
    val sleepTimer: StateFlow<SleepTimerState> = _sleepTimer.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    private var sleepJob: Job? = null
    private var settingsApplied = false
    private var shuffleNewSessions = false
    private var lastRecordedTrackId: Long? = null

    init {
        viewModelScope.launch { controller.connect() }

        viewModelScope.launch {
            preferences.settings.collect { settings ->
                shuffleNewSessions = settings.shuffleNewSessions
                if (!settingsApplied) {
                    settingsApplied = true
                    applyInitialSettings(settings)
                }
            }
        }

        // Favorite state always follows the current track, so the heart is correct in the
        // mini-player, the full player and the queue at the same time.
        viewModelScope.launch {
            controller.state
                .map { it.currentSong?.id }
                .distinctUntilChanged()
                .flatMapLatest { id ->
                    if (id == null) flowOf(false) else userLibraryRepository.observeIsFavorite(id)
                }
                .collect { _isFavorite.value = it }
        }

        // Playback history and player errors.
        viewModelScope.launch {
            controller.events.collect { event ->
                when (event) {
                    is PlaybackEvent.TrackStarted -> {
                        if (controller.state.value.isPlaying &&
                            controller.state.value.currentSong?.id == event.song.id &&
                            lastRecordedTrackId != event.song.id
                        ) {
                            lastRecordedTrackId = event.song.id
                            userLibraryRepository.recordPlay(event.song.id)
                        }
                    }

                    is PlaybackEvent.Error -> _messages.tryEmit(
                        UiMessage(R.string.playback_error_generic, isError = true),
                    )

                    PlaybackEvent.QueueCleared -> Unit
                    PlaybackEvent.SleepTimerExpired -> _messages.tryEmit(
                        UiMessage(R.string.sleep_timer_off),
                    )
                }
            }
        }

        // Keep the persisted position fresh while playing.
        viewModelScope.launch {
            while (isActive) {
                delay(POSITION_SAVE_INTERVAL_MS)
                val song = controller.state.value.currentSong ?: continue
                if (controller.state.value.isPlaying) {
                    stateStore.save(song, controller.progress.value.positionMs)
                }
            }
        }

        _volume.value = controller.volume()
    }

    private fun applyInitialSettings(settings: AppPreferences.Settings) {
        controller.setRepeatMode(RepeatMode.fromKey(settings.defaultRepeatMode))
        controller.setPlaybackSpeed(settings.defaultPlaybackSpeed)
        if (settings.resumePlayback && controller.state.value.currentSong == null) {
            stateStore.restore()?.let { session ->
                controller.restoreSession(listOf(session.song), 0, session.positionMs)
            }
        }
    }

    // ------------------------------------------------------------------ transport

    fun togglePlayPause() = controller.togglePlayPause()

    fun play() = controller.play()

    fun playExternalSong(song: Song) {
        viewModelScope.launch {
            controller.connect()
            lastRecordedTrackId = null
            controller.play(listOf(song), 0, shuffle = false)
        }
    }

    fun restorePlaybackContext(
        songs: List<Song>,
        startIndex: Int,
        positionMs: Long,
        wasPlaying: Boolean,
    ) {
        viewModelScope.launch {
            controller.connect()
            if (songs.isEmpty()) {
                controller.stop()
                return@launch
            }
            lastRecordedTrackId = null
            controller.play(songs, startIndex, shuffle = false)
            controller.seekTo(positionMs)
            if (!wasPlaying) controller.pause()
        }
    }

    fun stop() = controller.stop()

    fun pause() = controller.pause()

    fun next() = controller.next()

    fun previous() = controller.previous()

    fun seekTo(positionMs: Long) = controller.seekTo(positionMs)

    fun toggleShuffle() = controller.setShuffleEnabled(!controller.state.value.shuffleEnabled)

    fun cycleRepeatMode() = controller.cycleRepeatMode()

    fun setPlaybackSpeed(speed: Float) = controller.setPlaybackSpeed(speed)

    fun setVolume(volume: Float) {
        controller.setVolume(volume.coerceIn(0f, 1f))
        refreshVolume()
    }

    fun refreshVolume() {
        _volume.value = controller.volume()
    }

    /**
     * Starts [songs] at [startIndex].
     *
     * When [shuffle] is true, or the user asked for shuffled new sessions in settings, the
     * queue is randomised — the chosen track still plays first.
     */
    fun playSongs(songs: List<Song>, startIndex: Int, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        val index = startIndex.coerceIn(0, songs.lastIndex)
        lastRecordedTrackId = null
        controller.play(songs, index, shuffle || shuffleNewSessions)
    }

    fun playNext(song: Song) = controller.playNext(song)

    fun addToQueue(song: Song) = controller.addToQueue(song)

    fun addToQueue(songs: List<Song>) = controller.addToQueue(songs)

    fun removeFromQueue(index: Int) = controller.removeFromQueue(index)

    fun moveQueueItem(from: Int, to: Int) = controller.moveQueueItem(from, to)

    fun skipToQueueItem(index: Int) = controller.skipToQueueItem(index)

    fun clearQueue() = controller.clearQueue()

    fun currentSong(): Song? = controller.state.value.currentSong

    // ------------------------------------------------------------------ variants

    fun setRepeatMode(mode: RepeatMode) = controller.setRepeatMode(mode)

    // ------------------------------------------------------------------ sleep timer

    fun startSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) {
            _sleepTimer.value = SleepTimerState()
            return
        }
        val durationMs = minutes * 60_000L
        val endsAt = System.currentTimeMillis() + durationMs
        _sleepTimer.value = SleepTimerState(endsAt, durationMs)
        _messages.tryEmit(UiMessage(R.string.sleep_timer_set, listOf(minutes)))
        sleepJob = viewModelScope.launch {
            while (isActive) {
                val remaining = endsAt - System.currentTimeMillis()
                if (remaining <= 0L) {
                    controller.pause()
                    _sleepTimer.value = SleepTimerState()
                    _messages.tryEmit(UiMessage(R.string.sleep_timer_off))
                    break
                }
                _sleepTimer.value = SleepTimerState(endsAt, remaining)
                delay(1000L)
            }
        }
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        _sleepTimer.value = SleepTimerState()
    }

    fun toggleFavorite() {
        val song = controller.state.value.currentSong ?: return
        val next = !_isFavorite.value
        viewModelScope.launch {
            userLibraryRepository.setFavorite(song.id, next)
            _messages.tryEmit(
                UiMessage(
                    if (next) R.string.action_favorite else R.string.action_unfavorite,
                ),
            )
        }
    }

    private companion object {
        const val POSITION_SAVE_INTERVAL_MS = 5_000L
    }
}

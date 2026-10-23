package com.example.myapplication.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.domain.model.QueueItem
import com.example.myapplication.domain.model.Song
import com.example.myapplication.domain.repository.MusicRepository
import com.example.myapplication.playback.ArtworkKey
import com.example.myapplication.playback.PlaybackController
import com.example.myapplication.playback.PlaybackProgress
import com.example.myapplication.playback.PlaybackUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Exposes the playback state to the mini player, the full player and the queue screen. */
class PlayerViewModel(
    private val playbackController: PlaybackController,
    private val musicRepository: MusicRepository,
) : ViewModel() {

    val state: StateFlow<PlaybackUiState> = playbackController.state
    val progress: StateFlow<PlaybackProgress> = playbackController.progress
    val queue: StateFlow<List<QueueItem>> = playbackController.queue
    val messages: SharedFlow<String> = playbackController.messages

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    init {
        viewModelScope.launch {
            playbackController.state
                .map { it.mediaId }
                .distinctUntilChanged()
                .collect { mediaId ->
                    _currentSong.value = if (mediaId == 0L) null else musicRepository.getSong(mediaId)
                }
        }
        viewModelScope.launch {
            combine(
                musicRepository.songs,
                playbackController.state.map { it.mediaId }.distinctUntilChanged(),
            ) { songs, mediaId ->
                songs.firstOrNull { it.mediaId == mediaId }?.isFavorite ?: false
            }.distinctUntilChanged().collect { favorite -> _isFavorite.value = favorite }
        }
    }

    fun togglePlayPause() = playbackController.togglePlayPause()
    fun next() = playbackController.next()
    fun previous() = playbackController.previous()
    fun seekTo(positionMs: Long) = playbackController.seekTo(positionMs)
    fun seekToFraction(fraction: Float) = playbackController.seekToFraction(fraction)

    /** Moves the playback forward or backward inside the current track. */
    fun seekBy(deltaMs: Long) = playbackController.seekBy(deltaMs)
    fun toggleShuffle() = playbackController.toggleShuffle()
    fun setShuffle(enabled: Boolean) = playbackController.setShuffle(enabled)
    fun cycleRepeat() = playbackController.cycleRepeat()
    fun setSpeed(speed: Float) = playbackController.setSpeed(speed)
    fun jumpToQueueItem(index: Int) = playbackController.jumpToQueueItem(index)
    fun moveQueueItem(from: Int, to: Int) = playbackController.moveQueueItem(from, to)
    fun removeQueueItem(index: Int) = playbackController.removeQueueItem(index)
    fun clearQueue() = playbackController.clearQueue()

    /** Stops playback, clears the queue and dismisses the media notification. */
    fun stopAndDismiss() = playbackController.stopAndDismiss()

    fun toggleFavorite() {
        val song = _currentSong.value ?: return
        viewModelScope.launch { musicRepository.toggleFavorite(song.mediaId) }
    }

    fun playNext(song: Song) = playbackController.playNext(song)
    fun addToQueue(song: Song) = playbackController.addToQueue(song)
    fun playShuffled(songs: List<Song>) {
        if (songs.isNotEmpty()) playbackController.playSongsShuffled(songs)
    }

    fun artworkKey(): ArtworkKey? = playbackController.state.value.artworkModel
}

package com.tidalmusic.player.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tidalmusic.player.R
import com.tidalmusic.player.data.repository.PlaylistSummary
import com.tidalmusic.player.data.repository.UserLibraryRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One playlist: its tracks, their order and the editing actions.
 *
 * Track order is persisted through [UserLibraryRepository.reorderPlaylist], so the next and
 * previous controls follow exactly what the user sees.
 */
class PlaylistDetailViewModel(
    private val playlistId: Long,
    private val repository: UserLibraryRepository,
) : ViewModel() {

    val playlist: StateFlow<PlaylistSummary?> = repository.observePlaylist(playlistId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    fun removeSong(songId: Long) {
        viewModelScope.launch {
            repository.removeSongFromPlaylist(playlistId, songId).fold(
                onSuccess = { _messages.tryEmit(UiMessage(R.string.playlist_song_removed)) },
                onFailure = { _messages.tryEmit(UiMessage(R.string.playlist_song_removed, isError = true)) },
            )
        }
    }

    /**
     * Moves the track at [from] to [to] in the playlist order.
     *
     * The new order is persisted immediately; a failure restores the previous order on the
     * next database emission, so the UI can never show an order that was not saved.
     */
    fun move(from: Int, to: Int) {
        val current = playlist.value?.songIds ?: return
        if (from !in current.indices || to !in current.indices || from == to) return
        val reordered = current.toMutableList().apply { add(to, removeAt(from)) }
        viewModelScope.launch { repository.reorderPlaylist(playlistId, reordered) }
    }
}

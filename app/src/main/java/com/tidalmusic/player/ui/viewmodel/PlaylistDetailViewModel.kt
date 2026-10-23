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
import kotlinx.coroutines.flow.asStateFlow
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

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    fun addSongs(songIds: List<Long>) {
        if (songIds.isEmpty() || _isBusy.value) return
        _isBusy.value = true
        viewModelScope.launch {
            val result = repository.addSongsToPlaylist(playlistId, songIds)
            _isBusy.value = false
            result.fold(
                onSuccess = { added ->
                    _messages.tryEmit(
                        if (added > 0) {
                            UiMessage(R.string.playlist_songs_added, listOf(added))
                        } else {
                            UiMessage(R.string.playlist_songs_already_present)
                        },
                    )
                },
                onFailure = { _messages.tryEmit(UiMessage(R.string.playlist_song_added, isError = true)) },
            )
        }
    }

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

    fun rename(name: String, description: String?) {
        if (_isBusy.value) return
        _isBusy.value = true
        viewModelScope.launch {
            val result = repository.renamePlaylist(playlistId, name, description)
            _isBusy.value = false
            result.fold(
                onSuccess = { _messages.tryEmit(UiMessage(R.string.playlist_renamed)) },
                onFailure = { _messages.tryEmit(UiMessage(R.string.playlist_name_duplicate, isError = true)) },
            )
        }
    }

    fun delete() {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId).fold(
                onSuccess = {
                    _messages.tryEmit(UiMessage(R.string.playlist_deleted))
                    _deleted.value = true
                },
                onFailure = { _messages.tryEmit(UiMessage(R.string.playlist_deleted, isError = true)) },
            )
        }
    }
}

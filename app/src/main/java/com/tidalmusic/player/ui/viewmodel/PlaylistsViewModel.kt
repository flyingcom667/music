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

/** The playlist list, plus the create/rename/delete actions. */
class PlaylistsViewModel(
    private val repository: UserLibraryRepository,
) : ViewModel() {

    val playlists: StateFlow<List<PlaylistSummary>> = repository.observePlaylists().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    /** Set right after a playlist is created so the screen can open it. */
    private val _createdPlaylistId = MutableStateFlow<Long?>(null)
    val createdPlaylistId: StateFlow<Long?> = _createdPlaylistId.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    fun create(name: String, description: String?) {
        if (_isSaving.value) return
        _isSaving.value = true
        viewModelScope.launch {
            val result = repository.createPlaylist(name, description)
            _isSaving.value = false
            result.fold(
                onSuccess = { id ->
                    _createdPlaylistId.value = id
                    _messages.tryEmit(UiMessage(R.string.playlist_created))
                },
                onFailure = { _messages.tryEmit(UiMessage(R.string.playlist_name_duplicate, isError = true)) },
            )
        }
    }

    fun rename(id: Long, name: String, description: String?) {
        if (_isSaving.value) return
        _isSaving.value = true
        viewModelScope.launch {
            val result = repository.renamePlaylist(id, name, description)
            _isSaving.value = false
            result.fold(
                onSuccess = { _messages.tryEmit(UiMessage(R.string.playlist_renamed)) },
                onFailure = { _messages.tryEmit(UiMessage(R.string.playlist_name_duplicate, isError = true)) },
            )
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(id).fold(
                onSuccess = { _messages.tryEmit(UiMessage(R.string.playlist_deleted)) },
                onFailure = { _messages.tryEmit(UiMessage(R.string.playlist_deleted, isError = true)) },
            )
        }
    }

    fun consumeCreatedPlaylist() {
        _createdPlaylistId.value = null
    }
}

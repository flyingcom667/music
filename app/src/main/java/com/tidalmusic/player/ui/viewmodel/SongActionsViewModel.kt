package com.tidalmusic.player.ui.viewmodel

import android.content.IntentSender
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tidalmusic.player.R
import com.tidalmusic.player.data.local.MetadataEditor
import com.tidalmusic.player.data.repository.LibraryRepository
import com.tidalmusic.player.data.repository.PlaylistSummary
import com.tidalmusic.player.data.repository.UserLibraryRepository
import com.tidalmusic.player.domain.model.Song
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The metadata the user typed into the edit dialog. */
data class MetadataEdit(
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
)

/**
 * A system consent dialog that has to be launched from an Activity.
 *
 * @param metadata when set the request is a metadata write, otherwise a file deletion.
 */
data class ConsentRequest(
    val song: Song,
    val intentSender: IntentSender,
    val metadata: MetadataEdit? = null,
    val songs: List<Song> = listOf(song),
    val retryDeletionAfterConsent: Boolean = false,
    val deletedBeforeConsent: Int = 0,
    val failedBeforeConsent: Int = 0,
)

/** Everything the "song actions" UI needs: the sheet, the dialogs and their feedback. */
class SongActionsViewModel(
    private val userLibraryRepository: UserLibraryRepository,
    private val libraryRepository: LibraryRepository,
    private val metadataEditor: MetadataEditor,
) : ViewModel() {

    private val _selectedSong = MutableStateFlow<Song?>(null)
    val selectedSong: StateFlow<Song?> = _selectedSong.asStateFlow()

    private val _detailsSong = MutableStateFlow<Song?>(null)
    val detailsSong: StateFlow<Song?> = _detailsSong.asStateFlow()

    private val _detailsGenre = MutableStateFlow<String?>(null)
    val detailsGenre: StateFlow<String?> = _detailsGenre.asStateFlow()

    private val _consentRequest = MutableStateFlow<ConsentRequest?>(null)
    val consentRequest: StateFlow<ConsentRequest?> = _consentRequest.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    /** Incremented whenever a file was deleted or its metadata changed. */
    private val _libraryRevision = MutableStateFlow(0)
    val libraryRevision: StateFlow<Int> = _libraryRevision.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    private val _deletedSongs = MutableSharedFlow<Song>(extraBufferCapacity = 8)
    val deletedSongs: SharedFlow<Song> = _deletedSongs.asSharedFlow()

    val playlists: StateFlow<List<PlaylistSummary>> = userLibraryRepository.observePlaylists().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val favorites: StateFlow<Set<Long>> = userLibraryRepository.observeFavoriteIds().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptySet(),
    )

    // ------------------------------------------------------------------ selection

    fun select(song: Song) {
        _selectedSong.value = song
    }

    fun clearSelection() {
        _selectedSong.value = null
    }

    fun showDetails(song: Song) {
        _detailsSong.value = song
        _detailsGenre.value = null
        viewModelScope.launch {
            _detailsGenre.value = runCatching { libraryRepository.genreOf(song.id) }.getOrNull()
        }
    }

    fun dismissDetails() {
        _detailsSong.value = null
        _detailsGenre.value = null
    }

    // ------------------------------------------------------------------ favourites

    fun toggleFavorite(song: Song) {
        val favorite = song.id !in favorites.value
        viewModelScope.launch {
            userLibraryRepository.setFavorite(song.id, favorite)
            _messages.tryEmit(
                UiMessage(if (favorite) R.string.action_favorite else R.string.action_unfavorite),
            )
        }
    }

    // ------------------------------------------------------------------ playlists

    fun addToPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            userLibraryRepository.addSongsToPlaylist(playlistId, listOf(songId)).fold(
                onSuccess = { added ->
                    _messages.tryEmit(
                        if (added > 0) {
                            UiMessage(R.string.playlist_song_added)
                        } else {
                            UiMessage(R.string.playlist_songs_already_present)
                        },
                    )
                },
                onFailure = { _messages.tryEmit(UiMessage(R.string.playlist_song_added, isError = true)) },
            )
        }
    }

    fun createPlaylistWithSong(name: String, songId: Long) {
        if (_isBusy.value) return
        _isBusy.value = true
        viewModelScope.launch {
            val result = userLibraryRepository.createPlaylist(name, null)
            _isBusy.value = false
            result.fold(
                onSuccess = { id ->
                    userLibraryRepository.addSongsToPlaylist(id, listOf(songId))
                    _messages.tryEmit(UiMessage(R.string.playlist_created))
                },
                onFailure = { _messages.tryEmit(UiMessage(R.string.playlist_name_duplicate, isError = true)) },
            )
        }
    }

    // ------------------------------------------------------------------ visibility

    fun setHidden(song: Song, hidden: Boolean) {
        viewModelScope.launch {
            userLibraryRepository.setHidden(song.id, hidden).fold(
                onSuccess = {
                    _messages.tryEmit(
                        UiMessage(if (hidden) R.string.hide_track_done else R.string.unhide_track_done),
                    )
                },
                onFailure = { _messages.tryEmit(UiMessage(R.string.hide_track_done, isError = true)) },
            )
        }
    }

    fun hideSongs(songs: List<Song>) {
        if (songs.isEmpty() || _isBusy.value) return
        _isBusy.value = true
        viewModelScope.launch {
            val results = songs.map { song -> userLibraryRepository.setHidden(song.id, true) }
            _isBusy.value = false
            if (results.all { it.isSuccess }) {
                _messages.tryEmit(UiMessage(R.string.library_songs_removed, listOf(songs.size)))
            } else {
                _messages.tryEmit(UiMessage(R.string.library_remove_failed, isError = true))
            }
        }
    }

    // ------------------------------------------------------------------ deletion

    /**
     * Starts a file deletion.
     *
     * On Android 11+ the system asks the user for consent and performs the delete itself; on
     * older versions the app deletes the row directly. The app never deletes a file silently.
     */
    fun requestDelete(song: Song) {
        requestDeleteSongs(listOf(song))
    }

    fun requestDeleteSongs(songs: List<Song>) {
        if (songs.isEmpty() || _isBusy.value) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val sender = metadataEditor.createDeleteRequest(songs)
                if (sender == null) {
                    _messages.tryEmit(UiMessage(R.string.delete_file_failed, isError = true))
                } else {
                    _consentRequest.value = ConsentRequest(
                        song = songs.first(),
                        intentSender = sender,
                        songs = songs,
                    )
                }
            } catch (error: SecurityException) {
                _messages.tryEmit(UiMessage(R.string.delete_file_failed, isError = true))
            } catch (error: IllegalArgumentException) {
                _messages.tryEmit(UiMessage(R.string.delete_file_failed, isError = true))
            }
        } else {
            viewModelScope.launch { deleteSongs(songs) }
        }
    }

    private suspend fun deleteSongs(
        songs: List<Song>,
        deletedBefore: Int = 0,
        failedBefore: Int = 0,
    ) {
        _isBusy.value = true
        var deleted = deletedBefore
        var failed = failedBefore

        for ((index, song) in songs.withIndex()) {
            when (val result = metadataEditor.deleteDirectly(song)) {
                MetadataEditor.Result.Success -> {
                    deleted++
                    _deletedSongs.tryEmit(song)
                    if (userLibraryRepository.removeSongReferences(song.id).isFailure) failed++
                    _libraryRevision.value += 1
                }

                is MetadataEditor.Result.NeedsConsent -> {
                    _isBusy.value = false
                    _consentRequest.value = ConsentRequest(
                        song = song,
                        intentSender = result.intentSender,
                        songs = songs.drop(index),
                        retryDeletionAfterConsent = true,
                        deletedBeforeConsent = deleted,
                        failedBeforeConsent = failed,
                    )
                    return
                }

                is MetadataEditor.Result.Failure -> failed++
            }
        }

        _isBusy.value = false
        when {
            deleted == 0 -> _messages.tryEmit(UiMessage(R.string.delete_file_failed, isError = true))
            failed > 0 -> _messages.tryEmit(
                UiMessage(R.string.delete_selected_partial, listOf(deleted), isError = true),
            )
            songs.size == 1 -> _messages.tryEmit(UiMessage(R.string.delete_file_done))
            else -> _messages.tryEmit(UiMessage(R.string.delete_selected_done, listOf(deleted)))
        }
    }

    /** Called by the Activity with the result of the system delete dialog. */
    fun onDeleteConsentResult(granted: Boolean) {
        val request = _consentRequest.value ?: return
        _consentRequest.value = null
        if (granted) {
            if (request.retryDeletionAfterConsent) {
                viewModelScope.launch {
                    deleteSongs(
                        request.songs,
                        request.deletedBeforeConsent,
                        request.failedBeforeConsent,
                    )
                }
            } else {
                viewModelScope.launch {
                    var failed = 0
                    request.songs.forEach { song ->
                        _deletedSongs.tryEmit(song)
                        if (userLibraryRepository.removeSongReferences(song.id).isFailure) failed++
                        _libraryRevision.value += 1
                    }
                    val count = request.songs.size
                    _messages.tryEmit(
                        if (failed == 0) {
                            if (count == 1) UiMessage(R.string.delete_file_done)
                            else UiMessage(R.string.delete_selected_done, listOf(count))
                        } else {
                            UiMessage(R.string.delete_selected_partial, listOf(count - failed), isError = true)
                        },
                    )
                }
            }
        } else {
            _messages.tryEmit(UiMessage(R.string.delete_file_failed, isError = true))
        }
    }

    // ------------------------------------------------------------------ metadata

    /**
     * Starts a metadata write.
     *
     * On Android 10+ the system must grant a per-file write request first; the dialog is
     * launched by the Activity and the result comes back through [onWriteConsentResult].
     */
    fun requestMetadataWrite(song: Song, edit: MetadataEdit) {
        if (edit.title.isBlank()) {
            _messages.tryEmit(UiMessage(R.string.playlist_name_required, isError = true))
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val sender = metadataEditor.createWriteRequest(song)
            if (sender == null) {
                _messages.tryEmit(UiMessage(R.string.metadata_edit_unsupported, isError = true))
                return
            }
            _consentRequest.value = ConsentRequest(song, sender, edit)
            return
        }
        writeMetadata(song, edit)
    }

    fun onWriteConsentResult(granted: Boolean) {
        val request = _consentRequest.value ?: return
        _consentRequest.value = null
        val edit = request.metadata
        if (granted && edit != null) {
            writeMetadata(request.song, edit)
        } else {
            _messages.tryEmit(UiMessage(R.string.metadata_edit_failed, isError = true))
        }
    }

    fun cancelConsent() {
        _consentRequest.value = null
    }

    private fun writeMetadata(song: Song, edit: MetadataEdit) {
        _isBusy.value = true
        viewModelScope.launch {
            val result = metadataEditor.write(
                song = song,
                title = edit.title,
                artist = edit.artist,
                album = edit.album,
                genre = edit.genre,
            )
            _isBusy.value = false
            when (result) {
                is MetadataEditor.Result.Success -> {
                    _messages.tryEmit(UiMessage(R.string.metadata_edit_saved))
                    _libraryRevision.value += 1
                }

                is MetadataEditor.Result.NeedsConsent ->
                    _messages.tryEmit(UiMessage(R.string.metadata_edit_failed, isError = true))

                is MetadataEditor.Result.Failure -> _messages.tryEmit(
                    UiMessage(R.string.metadata_edit_failed, isError = true),
                )
            }
        }
    }
}

package com.example.myapplication.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.local.AudioFileInfo
import com.example.myapplication.data.local.AudioFileProbe
import com.example.myapplication.domain.model.MetadataOverride
import com.example.myapplication.domain.model.Playlist
import com.example.myapplication.domain.model.Song
import com.example.myapplication.domain.model.SongMetadata
import com.example.myapplication.domain.repository.MusicRepository
import com.example.myapplication.domain.repository.PlaylistRepository
import com.example.myapplication.playback.PlaybackController
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Shared "long press a song" experience: actions plus the read-only details of the file and
 * the editable metadata fields.
 */
class SongActionsViewModel(
    private val musicRepository: MusicRepository,
    private val playlistRepository: PlaylistRepository,
    private val playbackController: PlaybackController,
    private val probe: AudioFileProbe,
) : ViewModel() {

    data class DetailsState(
        val song: Song,
        val base: SongMetadata? = null,
        val override: MetadataOverride = MetadataOverride(),
        val audioInfo: AudioFileInfo? = null,
        val isLoading: Boolean = true,
    )

    private val _details = MutableStateFlow<DetailsState?>(null)
    val details: StateFlow<DetailsState?> = _details.asStateFlow()

    val playlists: StateFlow<List<Playlist>> = playlistRepository.playlists

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun show(song: Song) {
        _details.value = DetailsState(song = song)
        viewModelScope.launch {
            val base = musicRepository.getBaseMetadata(song.mediaId)
            val override = musicRepository.getOverride(song.mediaId)
            val info = probe.probe(song.uri)
            _details.update { current ->
                current?.takeIf { it.song.mediaId == song.mediaId }?.copy(
                    base = base,
                    override = override,
                    audioInfo = info,
                    isLoading = false,
                )
            }
        }
    }

    fun dismiss() {
        _details.value = null
    }

    fun refresh(song: Song) {
        viewModelScope.launch {
            val fresh = musicRepository.getSong(song.mediaId) ?: return@launch
            _details.update { current ->
                current?.takeIf { it.song.mediaId == fresh.mediaId }?.copy(song = fresh)
            }
        }
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            val favorite = musicRepository.toggleFavorite(song.mediaId)
            _messages.tryEmit(if (favorite) "Added to favourites" else "Removed from favourites")
            refresh(song)
        }
    }

    fun playNext(song: Song) {
        playbackController.playNext(song)
        _messages.tryEmit("Playing next")
    }

    fun addToQueue(song: Song) {
        playbackController.addToQueue(song)
        _messages.tryEmit("Added to queue")
    }

    fun hide(song: Song) {
        viewModelScope.launch {
            musicRepository.hideSong(song.mediaId)
            _details.value = null
            _messages.tryEmit("Removed from library")
        }
    }

    fun saveMetadata(song: Song, metadata: SongMetadata) {
        viewModelScope.launch {
            musicRepository.saveOverride(
                song.mediaId,
                MetadataOverride(
                    title = metadata.title.takeIf { it.isNotBlank() },
                    artist = metadata.artist?.takeIf { it.isNotBlank() },
                    album = metadata.album?.takeIf { it.isNotBlank() },
                    albumArtist = metadata.albumArtist?.takeIf { it.isNotBlank() },
                    trackNumber = metadata.trackNumber?.takeIf { it > 0 },
                    genre = metadata.genre?.takeIf { it.isNotBlank() },
                    year = metadata.year?.takeIf { it > 0 },
                ),
            )
            _messages.tryEmit("Metadata saved")
            refresh(song)
        }
    }

    fun resetMetadata(song: Song) {
        viewModelScope.launch {
            musicRepository.resetOverride(song.mediaId)
            _messages.tryEmit("Reverted to file metadata")
            refresh(song)
        }
    }
}

package com.example.myapplication.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.domain.model.Playlist
import com.example.myapplication.domain.model.Song
import com.example.myapplication.domain.repository.PlaylistRepository
import com.example.myapplication.playback.PlaybackController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class PlaylistsViewModel(
    private val playlistRepository: PlaylistRepository,
    private val playbackController: PlaybackController,
) : ViewModel() {

    val playlists: StateFlow<List<Playlist>> = playlistRepository.playlists

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun songs(playlistId: Long): Flow<List<Song>> = playlistRepository.songs(playlistId)

    suspend fun create(name: String): Long {
        val id = playlistRepository.create(name)
        _messages.tryEmit("Playlist created")
        return id
    }

    fun rename(playlistId: Long, name: String) {
        viewModelScope.launch {
            playlistRepository.rename(playlistId, name)
            _messages.tryEmit("Playlist renamed")
        }
    }

    fun delete(playlistId: Long) {
        viewModelScope.launch {
            playlistRepository.delete(playlistId)
            _messages.tryEmit("Playlist deleted")
        }
    }

    fun addSong(playlistId: Long, mediaId: Long) {
        viewModelScope.launch {
            val added = playlistRepository.addSong(playlistId, mediaId)
            _messages.tryEmit(if (added) "Added to playlist" else "Already in playlist")
        }
    }

    /** Adds several songs to a playlist at once. */
    fun addSongs(playlistId: Long, songs: List<Song>) {
        if (songs.isEmpty()) return
        viewModelScope.launch {
            var added = 0
            songs.forEach { song ->
                if (playlistRepository.addSong(playlistId, song.mediaId)) added++
            }
            _messages.tryEmit(
                when {
                    added == 0 -> "Already in playlist"
                    added == 1 -> "1 song added to playlist"
                    else -> "$added songs added to playlist"
                },
            )
        }
    }

    fun removeSong(playlistId: Long, mediaId: Long) {
        viewModelScope.launch {
            playlistRepository.removeSong(playlistId, mediaId)
            _messages.tryEmit("Removed from playlist")
        }
    }

    /** Removes several songs from a playlist at once. */
    fun removeSongs(playlistId: Long, songs: List<Song>) {
        if (songs.isEmpty()) return
        viewModelScope.launch {
            songs.forEach { playlistRepository.removeSong(playlistId, it.mediaId) }
            _messages.tryEmit(
                if (songs.size == 1) "Removed from playlist" else "${songs.size} songs removed from playlist",
            )
        }
    }

    fun reorder(playlistId: Long, from: Int, to: Int) {
        viewModelScope.launch { playlistRepository.reorder(playlistId, from, to) }
    }

    fun play(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isNotEmpty()) playbackController.play(songs, startIndex)
    }

    fun shuffle(songs: List<Song>) {
        if (songs.isNotEmpty()) playbackController.playSongsShuffled(songs)
    }

    fun addAllToQueue(songs: List<Song>) = playbackController.addAllToQueue(songs)
}

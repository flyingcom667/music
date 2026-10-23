package com.example.myapplication.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
import com.example.myapplication.domain.model.AppSettings
import com.example.myapplication.domain.model.Song
import com.example.myapplication.domain.repository.MusicRepository
import com.example.myapplication.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val musicRepository: MusicRepository,
    private val imageLoader: ImageLoader,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _hiddenCount = MutableStateFlow(0)
    val hiddenCount: StateFlow<Int> = _hiddenCount.asStateFlow()

    private val _hiddenSongs = MutableStateFlow<List<Song>>(emptyList())
    val hiddenSongs: StateFlow<List<Song>> = _hiddenSongs.asStateFlow()

    init {
        refreshHiddenCount()
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    fun setExcludedFolder(folder: String, excluded: Boolean) {
        update { current ->
            val folders = if (excluded) {
                current.excludedFolders + folder
            } else {
                current.excludedFolders - folder
            }
            current.copy(excludedFolders = folders)
        }
    }

    fun rescan() {
        if (_isScanning.value) return
        viewModelScope.launch {
            _isScanning.value = true
            val result = musicRepository.sync()
            _isScanning.value = false
            result.onSuccess { count -> _messages.tryEmit("$count songs found") }
                .onFailure { _messages.tryEmit("Library scan failed") }
        }
    }

    fun clearPlayStatistics() {
        viewModelScope.launch {
            musicRepository.clearPlayStatistics()
            _messages.tryEmit("Play statistics cleared")
        }
    }

    fun clearArtworkCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                imageLoader.memoryCache?.clear()
                imageLoader.diskCache?.clear()
            }
            _messages.tryEmit("Artwork cache cleared")
        }
    }

    fun refreshHiddenCount() {
        viewModelScope.launch { _hiddenCount.value = musicRepository.hiddenCount() }
    }

    /** Loads the songs that were removed from the library, for the hidden songs dialog. */
    fun loadHiddenSongs() {
        viewModelScope.launch {
            _hiddenSongs.value = musicRepository.hiddenSongs()
        }
    }

    fun restoreHiddenSong(mediaId: Long) {
        viewModelScope.launch {
            musicRepository.unhideSong(mediaId)
            _messages.tryEmit("Song restored")
            refreshHiddenCount()
            loadHiddenSongs()
        }
    }

    fun restoreAllHiddenSongs() {
        viewModelScope.launch {
            musicRepository.restoreHiddenSongs()
            _messages.tryEmit("Hidden songs restored")
            refreshHiddenCount()
            loadHiddenSongs()
        }
    }
}

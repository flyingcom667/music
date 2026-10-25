package com.tidalmusic.player.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tidalmusic.player.data.preferences.AppPreferences
import com.tidalmusic.player.data.preferences.SettingsStore
import com.tidalmusic.player.data.preferences.ListDensity
import com.tidalmusic.player.data.preferences.ThemeMode
import com.tidalmusic.player.data.preferences.AppLanguage
import com.tidalmusic.player.data.preferences.ViewMode
import com.tidalmusic.player.data.repository.LibraryRepository
import com.tidalmusic.player.data.repository.UserLibraryRepository
import com.tidalmusic.player.domain.model.SortOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Library figures shown in the settings summary. */
data class StorageInfo(
    val songCount: Int = 0,
    val totalBytes: Long = 0L,
    val isLoading: Boolean = false,
)

/** All user preferences, plus the derived counts the settings screen displays. */
class SettingsViewModel(
    private val preferences: SettingsStore,
    private val libraryRepository: LibraryRepository,
    private val userLibraryRepository: UserLibraryRepository,
) : ViewModel() {

    val settings: StateFlow<AppPreferences.Settings> = preferences.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppPreferences.Settings(),
    )

    val hiddenCount: StateFlow<Int> = userLibraryRepository.observeHiddenCount().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 0,
    )

    val favoriteCount: StateFlow<Int> = userLibraryRepository.observeFavoriteCount().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 0,
    )

    private val _storage = MutableStateFlow(StorageInfo())
    val storage: StateFlow<StorageInfo> = _storage.asStateFlow()

    private var storageLoadStarted = false

    /** Reads the number of tracks and the total size; both are cheap provider queries. */
    fun loadStorage(includeNonMusic: Boolean) {
        if (_storage.value.isLoading) return
        if (storageLoadStarted && _storage.value.songCount > 0) return
        storageLoadStarted = true
        _storage.value = _storage.value.copy(isLoading = true)
        viewModelScope.launch {
            val count = runCatching { libraryRepository.audioCount(includeNonMusic) }.getOrDefault(0)
            val bytes = runCatching { libraryRepository.totalAudioBytes(includeNonMusic) }.getOrDefault(0L)
            _storage.value = StorageInfo(songCount = count, totalBytes = bytes, isLoading = false)
        }
    }

    // ------------------------------------------------------------------ appearance

    fun setTheme(mode: ThemeMode) = launch { preferences.setThemeMode(mode) }

    fun setDynamicColor(enabled: Boolean) = launch { preferences.setDynamicColor(enabled) }

    fun setArtworkAnimation(enabled: Boolean) = launch { preferences.setArtworkAnimation(enabled) }

    fun setDensity(density: ListDensity) = launch { preferences.setDensity(density) }

    fun setAppLanguage(language: AppLanguage) = launch { preferences.setAppLanguage(language) }

    fun setViewModeLibrary(mode: ViewMode) = launch { preferences.setViewModeLibrary(mode) }

    fun setViewModeHome(mode: ViewMode) = launch { preferences.setViewModeHome(mode) }

    fun setViewModeFolders(mode: ViewMode) = launch { preferences.setViewModeFolders(mode) }

    // ------------------------------------------------------------------ playback

    fun setResumePlayback(enabled: Boolean) = launch { preferences.setResumePlayback(enabled) }

    fun setShuffleNewSessions(enabled: Boolean) = launch { preferences.setShuffleNewSessions(enabled) }

    fun setDefaultRepeatMode(mode: String) = launch { preferences.setDefaultRepeatMode(mode) }

    fun setDefaultPlaybackSpeed(speed: Float) = launch { preferences.setDefaultPlaybackSpeed(speed) }

    // ------------------------------------------------------------------ library

    fun setIncludeNonMusic(enabled: Boolean) = launch { preferences.setIncludeNonMusic(enabled) }

    fun setShowArtwork(enabled: Boolean) = launch { preferences.setShowArtwork(enabled) }

    fun setDefaultSort(order: SortOrder) = launch {
        preferences.setDefaultSort(order, settings.value.defaultSortDirection)
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

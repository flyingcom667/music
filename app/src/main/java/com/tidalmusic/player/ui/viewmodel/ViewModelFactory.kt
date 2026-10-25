package com.tidalmusic.player.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tidalmusic.player.di.AppContainer
import com.tidalmusic.player.domain.model.Song

/**
 * Creates every screen model from the single [AppContainer].
 *
 * Explicit constructor injection keeps the dependency graph readable and makes the screen
 * models trivial to construct with fakes in tests.
 */
class AppViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(LibraryViewModel::class.java) -> LibraryViewModel(
            libraryRepository = container.libraryRepository,
            userLibraryRepository = container.userLibraryRepository,
            preferences = container.preferences,
        )

        modelClass.isAssignableFrom(PlayerViewModel::class.java) -> PlayerViewModel(
            controller = container.playbackController,
            userLibraryRepository = container.userLibraryRepository,
            preferences = container.preferences,
            stateStore = container.playbackStateStore,
        )

        modelClass.isAssignableFrom(PlaylistsViewModel::class.java) -> PlaylistsViewModel(
            repository = container.userLibraryRepository,
        )

        modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(
            preferences = container.preferences,
            libraryRepository = container.libraryRepository,
            userLibraryRepository = container.userLibraryRepository,
        )

        modelClass.isAssignableFrom(SongActionsViewModel::class.java) -> SongActionsViewModel(
            userLibraryRepository = container.userLibraryRepository,
            libraryRepository = container.libraryRepository,
            metadataEditor = container.metadataEditor,
        )

        else -> error("Unknown ViewModel class: ${modelClass.name}")
    } as T

    /** The detail screen needs the playlist id, so it gets its own scoped factory. */
    fun playlistDetailFactory(playlistId: Long): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = PlaylistDetailViewModel(
                playlistId = playlistId,
                repository = container.userLibraryRepository,
            ) as T
        }

    /** The editor is scoped to the song being edited and the library it can combine with. */
    fun editorFactory(song: Song, candidates: List<Song>): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                com.tidalmusic.player.audioeditor.AudioEditorViewModel(
                    appContext = container.androidContext(),
                    engine = container.audioEditorEngine,
                    song = song,
                    candidates = candidates,
                ) as T
        }
}

package com.example.myapplication.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.myapplication.di.AppContainer

/** Creates the app's ViewModels from the manual DI container. */
class MusicViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val viewModel: ViewModel = when {
            modelClass.isAssignableFrom(LibraryViewModel::class.java) -> LibraryViewModel(
                musicRepository = container.musicRepository,
                settingsRepository = container.settingsRepository,
                playbackController = container.playbackController,
                fileDeleter = container.fileDeleter,
                externalAudioReader = container.externalAudioReader,
            )

            modelClass.isAssignableFrom(PreviewViewModel::class.java) -> PreviewViewModel(
                reader = container.externalAudioReader,
                playbackController = container.playbackController,
            )

            modelClass.isAssignableFrom(PlayerViewModel::class.java) -> PlayerViewModel(
                playbackController = container.playbackController,
                musicRepository = container.musicRepository,
            )

            modelClass.isAssignableFrom(PlaylistsViewModel::class.java) -> PlaylistsViewModel(
                playlistRepository = container.playlistRepository,
                playbackController = container.playbackController,
            )

            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(
                settingsRepository = container.settingsRepository,
                musicRepository = container.musicRepository,
                imageLoader = container.imageLoader,
            )

            modelClass.isAssignableFrom(SongActionsViewModel::class.java) -> SongActionsViewModel(
                musicRepository = container.musicRepository,
                playlistRepository = container.playlistRepository,
                playbackController = container.playbackController,
                probe = container.audioFileProbe,
            )

            else -> error("Unknown ViewModel class: ${modelClass.name}")
        }
        return viewModel as T
    }
}

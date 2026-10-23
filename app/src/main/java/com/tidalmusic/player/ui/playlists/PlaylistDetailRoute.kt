package com.tidalmusic.player.ui.playlists

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tidalmusic.player.domain.model.Song
import com.tidalmusic.player.ui.viewmodel.AppViewModelFactory
import com.tidalmusic.player.ui.viewmodel.LibraryUiState
import com.tidalmusic.player.ui.viewmodel.PlaylistDetailViewModel

/**
 * Connects [PlaylistDetailViewModel] to [PlaylistDetailScreen].
 *
 * The view model owns the playlist itself; the songs come from the already loaded library so
 * that the detail screen does not scan the media provider a second time.
 */
@Composable
fun PlaylistDetailRoute(
    playlistId: Long,
    factory: AppViewModelFactory,
    libraryState: LibraryUiState,
    currentSongId: Long?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onOpenPlayer: () -> Unit,
    onSongOverflow: (Song) -> Unit,
) {
    val viewModel: PlaylistDetailViewModel = viewModel(
        key = "playlist-$playlistId",
        factory = factory.playlistDetailFactory(playlistId),
    )
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val songs: List<Song> = playlist?.songIds
        ?.mapNotNull { libraryState.allSongs[it] }
        .orEmpty()

    PlaylistDetailScreen(
        playlistName = playlist?.name.orEmpty(),
        songs = songs,
        isCurrentSong = { id -> id == currentSongId },
        isPlaying = isPlaying,
        isFavorite = { id -> id in libraryState.favorites },
        onBack = onBack,
        onPlayAll = { if (songs.isNotEmpty()) onPlay(songs, 0) },
        onShuffle = { onShuffle(songs) },
        onAddSongs = { onOpenPlayer() },
        onPlaySong = { index -> onPlay(songs, index) },
        onRemoveSong = { songId -> viewModel.removeSong(songId) },
        onMoveSong = { from, to -> viewModel.move(from, to) },
        onRename = {
            val current = playlist ?: return@PlaylistDetailScreen
            viewModel.rename(current.name, current.description)
        },
        onDelete = { viewModel.delete() },
        onSongOverflow = onSongOverflow,
    )
}

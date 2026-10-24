package com.tidalmusic.player.ui.playlists

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tidalmusic.player.data.repository.PlaylistSummary
import com.tidalmusic.player.domain.model.Song
import com.tidalmusic.player.ui.viewmodel.AppViewModelFactory
import com.tidalmusic.player.ui.viewmodel.LibraryUiState
import com.tidalmusic.player.ui.viewmodel.PlaylistDetailViewModel

/**
 * Connects [PlaylistDetailViewModel] to [PlaylistDetailScreen].
 *
 * The view model owns the playlist itself; the songs come from the already loaded library so
 * that the detail screen does not scan the media provider a second time.
 *
 * Rename and delete go through the shared confirmation dialogs owned by the app shell —
 * deleting a playlist from its own detail screen must never happen on a single unconfirmed
 * tap.
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
    onAddSongs: (PlaylistSummary) -> Unit,
    onRenamePlaylist: (PlaylistSummary) -> Unit,
    onDeletePlaylist: (PlaylistSummary) -> Unit,
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

    // When the playlist disappears from the database (deleted from this screen through the
    // shell's confirmation dialog, or from anywhere else), the screen must leave instead of
    // showing an empty husk. The null emitted before the first load does not count.
    var hadPlaylist by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(playlist) {
        when {
            playlist != null -> hadPlaylist = true
            hadPlaylist -> onBack()
        }
    }

    PlaylistDetailScreen(
        playlistName = playlist?.name.orEmpty(),
        songs = songs,
        isCurrentSong = { id -> id == currentSongId },
        isPlaying = isPlaying,
        isFavorite = { id -> id in libraryState.favorites },
        onBack = onBack,
        onPlayAll = { if (songs.isNotEmpty()) onPlay(songs, 0) },
        onShuffle = { onShuffle(songs) },
        onAddSongs = { playlist?.let(onAddSongs) },
        onPlaySong = { index -> onPlay(songs, index) },
        onRemoveSong = { songId -> viewModel.removeSong(songId) },
        onMoveSong = { from, to -> viewModel.move(from, to) },
        onRename = { playlist?.let(onRenamePlaylist) },
        onDelete = { playlist?.let(onDeletePlaylist) },
        onSongOverflow = onSongOverflow,
    )
}

package com.example.myapplication.navigation

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.core.content.FileProvider
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.myapplication.data.local.DeleteRequest
import com.example.myapplication.di.AppContainer
import java.io.File
import com.example.myapplication.domain.model.Playlist
import com.example.myapplication.domain.model.Song
import com.example.myapplication.ui.components.ConfirmDialog
import com.example.myapplication.ui.components.EditMetadataDialog
import com.example.myapplication.ui.components.MiniPlayer
import com.example.myapplication.ui.components.PlaylistNameDialog
import com.example.myapplication.ui.components.PlaylistPickerDialog
import com.example.myapplication.ui.components.SongActionsSheet
import com.example.myapplication.ui.components.SongPickerDialog
import com.example.myapplication.ui.components.SongSelectionActions
import com.example.myapplication.ui.screens.FavoritesScreen
import com.example.myapplication.ui.screens.HomeScreen
import com.example.myapplication.ui.screens.LibraryScreen
import com.example.myapplication.ui.screens.PlayerScreen
import com.example.myapplication.ui.screens.PlaylistDetailScreen
import com.example.myapplication.ui.screens.PlaylistsScreen
import com.example.myapplication.ui.screens.QueueScreen
import com.example.myapplication.ui.screens.SettingsScreen
import com.example.myapplication.ui.viewmodel.LibraryViewModel
import com.example.myapplication.ui.viewmodel.MusicViewModelFactory
import com.example.myapplication.ui.viewmodel.PlayerViewModel
import com.example.myapplication.ui.viewmodel.PlaylistsViewModel
import com.example.myapplication.ui.viewmodel.SettingsViewModel
import com.example.myapplication.ui.viewmodel.SongActionsViewModel
import kotlinx.coroutines.launch

private sealed interface PlaylistDialogState {
    data object Create : PlaylistDialogState
    data class Rename(val playlist: Playlist) : PlaylistDialogState
}

@Composable
fun MusicApp(
    container: AppContainer,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    /** A file that should be played in the full player, for example from the preview screen. */
    playUri: String? = null,
    onPlayUriHandled: () -> Unit = {},
) {
    val factory = remember(container) { MusicViewModelFactory(container) }
    val libraryVm: LibraryViewModel = viewModel(factory = factory)
    val playerVm: PlayerViewModel = viewModel(factory = factory)
    val playlistsVm: PlaylistsViewModel = viewModel(factory = factory)
    val settingsVm: SettingsViewModel = viewModel(factory = factory)
    val songActionsVm: SongActionsViewModel = viewModel(factory = factory)

    val libraryState by libraryVm.state.collectAsStateWithLifecycle()
    val playback by playerVm.state.collectAsStateWithLifecycle()
    val progress by playerVm.progress.collectAsStateWithLifecycle()
    val queue by playerVm.queue.collectAsStateWithLifecycle()
    val isFavorite by playerVm.isFavorite.collectAsStateWithLifecycle()
    val playlists by playlistsVm.playlists.collectAsStateWithLifecycle()
    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    val hiddenCount by settingsVm.hiddenCount.collectAsStateWithLifecycle()
    val hiddenSongs by settingsVm.hiddenSongs.collectAsStateWithLifecycle()
    val isScanning by settingsVm.isScanning.collectAsStateWithLifecycle()
    val songDetails by songActionsVm.details.collectAsStateWithLifecycle()
    val selectionIds by libraryVm.selection.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // The back button of the phone ends a selection of songs first; only the next press leaves
    // the screen. Without this the selection would be lost when the screen is left.
    BackHandler(enabled = selectionIds.isNotEmpty()) {
        libraryVm.clearSelection()
    }

    // Kept outside of the library screen so the scroll position survives a tab switch.
    val libraryListState = rememberLazyListState()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute == null || currentRoute !in fullScreenRoutes
    val showMiniPlayer = playback.hasCurrent && currentRoute != Routes.PLAYER

    var playlistDialog by remember { mutableStateOf<PlaylistDialogState?>(null) }
    var playlistToDelete by remember { mutableStateOf<Playlist?>(null) }
    var editingSong by remember { mutableStateOf<Song?>(null) }

    /** The songs that should be added to a playlist by the playlist picker. */
    var playlistPickerSongs by remember { mutableStateOf<List<Song>?>(null) }

    /** The songs the user picks out of the library to add them to a playlist. */
    var songPickerVisible by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // The system asks the user to confirm the deletion of files it does not own.
    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            libraryVm.confirmPendingDeletion()
        } else {
            libraryVm.cancelPendingDeletion()
        }
    }

    LaunchedEffect(Unit) {
        libraryVm.messages.collect { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(Unit) {
        playerVm.messages.collect { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(Unit) {
        playlistsVm.messages.collect { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(Unit) {
        settingsVm.messages.collect { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(Unit) {
        songActionsVm.messages.collect { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(hasPermission) {
        if (hasPermission && libraryState.songs.isEmpty() && !libraryState.isLoading) {
            libraryVm.scan()
        }
    }
    LaunchedEffect(Unit) {
        libraryVm.pendingDelete.collect { request ->
            when (request) {
                is DeleteRequest.NeedsConsent -> deleteLauncher.launch(
                    IntentSenderRequest.Builder(request.pendingIntent.intentSender).build(),
                )

                else -> Unit
            }
        }
    }
    LaunchedEffect(playUri) {
        val uri = playUri ?: return@LaunchedEffect
        libraryVm.playExternalFile(uri)
        navController.navigate(Routes.PLAYER)
        onPlayUriHandled()
    }

    /** The uri another app can read the file of [song] with. */
    fun shareUri(song: Song): Uri? {
        val uri = song.uri.toUri()
        return when (uri.scheme) {
            "content" -> uri
            "file" -> uri.path?.let { path ->
                runCatching {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        File(path),
                    )
                }.getOrNull()
            }
            else -> null
        }
    }

    /** Shares the given songs with another app. */
    fun shareSongs(songs: List<Song>) {
        val uris = songs.mapNotNull { shareUri(it) }
        if (uris.isEmpty()) {
            scope.launch { snackbarHostState.showSnackbar("This file cannot be shared") }
            return
        }
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, uris.first())
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "audio/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
        }
        context.startActivity(
            Intent.createChooser(intent, "Share audio file").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
        )
    }

    /** The multi selection of a list of songs. */
    fun selectionActions(songs: List<Song>): SongSelectionActions = SongSelectionActions(
        selectedIds = selectionIds,
        onStart = libraryVm::startSelection,
        onToggle = libraryVm::toggleSelection,
        onClear = libraryVm::clearSelection,
        onSelectAll = { libraryVm.selectAll(songs.map { it.mediaId }) },
        onAddToPlaylist = {
            val selected = libraryVm.selectedSongs(songs)
            if (selected.isNotEmpty()) playlistPickerSongs = selected
        },
        onAddToQueue = {
            val selected = libraryVm.selectedSongs(songs)
            if (selected.isNotEmpty()) {
                libraryVm.addAllToQueue(selected)
                libraryVm.clearSelection()
            }
        },
        onShare = {
            val selected = libraryVm.selectedSongs(songs)
            if (selected.isNotEmpty()) {
                shareSongs(selected)
                libraryVm.clearSelection()
            }
        },
        onDeleteFiles = {
            val selected = libraryVm.selectedSongs(songs)
            if (selected.isNotEmpty()) libraryVm.requestDeleteFiles(selected)
        },
        onRemoveFromLibrary = {
            val selected = libraryVm.selectedSongs(songs)
            if (selected.isNotEmpty()) libraryVm.removeFromLibrary(selected)
        },
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column {
                if (showMiniPlayer) {
                    MiniPlayer(
                        state = playback,
                        progress = progress,
                        onExpand = { navController.navigate(Routes.PLAYER) },
                        onPlayPause = playerVm::togglePlayPause,
                        onNext = playerVm::next,
                        onStop = playerVm::stopAndDismiss,
                    )
                }
                AnimatedVisibility(visible = showBottomBar) {
                    NavigationBar {
                        bottomTabs.forEach { tab ->
                            NavigationBarItem(
                                selected = currentRoute == tab.route,
                                onClick = {
                                    val start = navController.graph.findStartDestination().id
                                    // Leave every screen that was pushed on top of the tabs
                                    // (settings, favourites, …) so the tab itself is shown.
                                    navController.popBackStack(start, inclusive = false)
                                    navController.navigate(tab.route) {
                                        popUpTo(start) { saveState = false }
                                        launchSingleTop = true
                                        restoreState = false
                                    }
                                },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    state = libraryState,
                    nowPlayingId = playback.mediaId,
                    isPlaying = playback.isPlaying,
                    hasPermission = hasPermission,
                    onRequestPermission = onRequestPermission,
                    onScan = libraryVm::scan,
                    onSongClick = libraryVm::play,
                    onSongMore = songActionsVm::show,
                    onShuffleAll = libraryVm::shuffleAll,
                    onOpenFavorites = { navController.navigate(Routes.FAVORITES) },
                    onOpenLibrary = { navController.navigate(Routes.LIBRARY) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onPlaySongs = { songs -> libraryVm.playSongs(songs, 0) },
                )
            }

            composable(Routes.LIBRARY) {
                LibraryScreen(
                    state = libraryState,
                    nowPlayingId = playback.mediaId,
                    isPlaying = playback.isPlaying,
                    listState = libraryListState,
                    onSongClick = libraryVm::play,
                    onSongMore = songActionsVm::show,
                    onSortChange = libraryVm::setSortOption,
                    onLayoutToggle = libraryVm::cycleLayout,
                    onFolderFilter = libraryVm::setFolderFilter,
                    onQueryChange = libraryVm::setQuery,
                    onPlaySongs = { songs -> libraryVm.playSongs(songs, 0) },
                    selection = selectionActions(libraryState.visibleSongs),
                )
            }

            composable(Routes.FAVORITES) {
                val favorites = libraryState.favorites
                FavoritesScreen(
                    favorites = favorites,
                    nowPlayingId = playback.mediaId,
                    isPlaying = playback.isPlaying,
                    onBack = { navController.popBackStack() },
                    onPlayAll = { libraryVm.playSongs(favorites, 0) },
                    onShuffle = { playerVm.playShuffled(favorites) },
                    onSongClick = { song ->
                        val index = favorites.indexOfFirst { it.mediaId == song.mediaId }
                        libraryVm.playSongs(favorites, index.coerceAtLeast(0))
                    },
                    onSongMore = songActionsVm::show,
                    selection = selectionActions(favorites),
                )
            }

            composable(Routes.PLAYLISTS) {
                PlaylistsScreen(
                    playlists = playlists,
                    onOpen = { playlist ->
                        navController.navigate(Routes.playlistDetail(playlist.id))
                    },
                    onCreate = { playlistDialog = PlaylistDialogState.Create },
                    onAddSongs = { songPickerVisible = true },
                    onRename = { playlistDialog = PlaylistDialogState.Rename(it) },
                    onDelete = { playlistToDelete = it },
                )
            }

            composable(
                route = Routes.PLAYLIST_DETAIL,
                arguments = listOf(navArgument(Routes.ARG_PLAYLIST_ID) { type = NavType.LongType }),
            ) { entry ->
                val playlistId = entry.arguments?.getLong(Routes.ARG_PLAYLIST_ID) ?: 0L
                val playlistSongs by remember(playlistId) {
                    playlistsVm.songs(playlistId)
                }.collectAsStateWithLifecycle(initialValue = emptyList<Song>())
                val playlist = playlists.firstOrNull { it.id == playlistId }

                PlaylistDetailScreen(
                    playlist = playlist,
                    songs = playlistSongs,
                    nowPlayingId = playback.mediaId,
                    isPlaying = playback.isPlaying,
                    onBack = { navController.popBackStack() },
                    onPlayAll = { playlistsVm.play(playlistSongs, 0) },
                    onShuffle = { playlistsVm.shuffle(playlistSongs) },
                    onAddAllToQueue = { playlistsVm.addAllToQueue(playlistSongs) },
                    onSongClick = { song ->
                        val index = playlistSongs.indexOfFirst { it.mediaId == song.mediaId }
                        playlistsVm.play(playlistSongs, index.coerceAtLeast(0))
                    },
                    onRemoveSong = { song -> playlistsVm.removeSong(playlistId, song.mediaId) },
                    onRemoveSelected = { songs -> playlistsVm.removeSongs(playlistId, songs) },
                    selection = selectionActions(playlistSongs),
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    settings = settings,
                    folders = libraryState.folders,
                    songCount = libraryState.songs.size,
                    hiddenCount = hiddenCount,
                    hiddenSongs = hiddenSongs,
                    isScanning = isScanning,
                    onUpdate = settingsVm::update,
                    onToggleExcludedFolder = settingsVm::setExcludedFolder,
                    onRescan = settingsVm::rescan,
                    onClearStatistics = settingsVm::clearPlayStatistics,
                    onClearArtworkCache = settingsVm::clearArtworkCache,
                    onOpenHiddenSongs = settingsVm::loadHiddenSongs,
                    onRestoreHiddenSong = settingsVm::restoreHiddenSong,
                    onRestoreAllHiddenSongs = settingsVm::restoreAllHiddenSongs,
                )
            }

            composable(Routes.PLAYER) {
                PlayerScreen(
                    state = playback,
                    progress = progress,
                    isFavorite = isFavorite,
                    onBack = { navController.popBackStack() },
                    onPlayPause = playerVm::togglePlayPause,
                    onNext = playerVm::next,
                    onPrevious = playerVm::previous,
                    onSeekFraction = playerVm::seekToFraction,
                    onSeekBy = playerVm::seekBy,
                    onToggleShuffle = playerVm::toggleShuffle,
                    onCycleRepeat = playerVm::cycleRepeat,
                    onToggleFavorite = playerVm::toggleFavorite,
                    onOpenQueue = { navController.navigate(Routes.QUEUE) },
                    onSetSpeed = playerVm::setSpeed,
                    onMoreClick = { playerVm.currentSong.value?.let(songActionsVm::show) },
                    onStopAndClose = {
                        playerVm.stopAndDismiss()
                        navController.popBackStack()
                    },
                )
            }

            composable(Routes.QUEUE) {
                QueueScreen(
                    queue = queue,
                    currentIndex = playback.currentIndex,
                    isPlaying = playback.isPlaying,
                    onBack = { navController.popBackStack() },
                    onItemClick = playerVm::jumpToQueueItem,
                    onMove = playerVm::moveQueueItem,
                    onRemove = playerVm::removeQueueItem,
                    onClear = playerVm::clearQueue,
                )
            }
        }
    }

    SongActionsSheet(
        state = songDetails,
        onDismiss = songActionsVm::dismiss,
        onToggleFavorite = songActionsVm::toggleFavorite,
        onPlayNext = songActionsVm::playNext,
        onAddToQueue = songActionsVm::addToQueue,
        onAddToPlaylist = { song ->
            playlistPickerSongs = listOf(song)
            songActionsVm.dismiss()
        },
        onShare = { song ->
            shareSongs(listOf(song))
            songActionsVm.dismiss()
        },
        onDeleteFile = { song ->
            libraryVm.requestDeleteFiles(listOf(song))
            songActionsVm.dismiss()
        },
        onEditMetadata = { song ->
            editingSong = song
            songActionsVm.dismiss()
        },
        onResetMetadata = songActionsVm::resetMetadata,
        onRemove = songActionsVm::hide,
    )

    editingSong?.let { song ->
        EditMetadataDialog(
            song = song,
            initial = song.metadata,
            onDismiss = { editingSong = null },
            onSave = { metadata ->
                songActionsVm.saveMetadata(song, metadata)
                editingSong = null
            },
        )
    }

    when (val dialog = playlistDialog) {
        null -> Unit
        PlaylistDialogState.Create -> PlaylistNameDialog(
            title = "New playlist",
            initialName = "",
            onDismiss = { playlistDialog = null },
            onConfirm = { name ->
                playlistDialog = null
                scope.launch { playlistsVm.create(name) }
            },
        )

        is PlaylistDialogState.Rename -> PlaylistNameDialog(
            title = "Rename playlist",
            initialName = dialog.playlist.name,
            onDismiss = { playlistDialog = null },
            onConfirm = { name ->
                playlistsVm.rename(dialog.playlist.id, name)
                playlistDialog = null
            },
        )
    }

    playlistToDelete?.let { playlist ->
        ConfirmDialog(
            title = "Delete playlist?",
            message = "\"${playlist.name}\" will be deleted. The songs stay on your device.",
            confirmLabel = "Delete",
            onConfirm = { playlistsVm.delete(playlist.id) },
            onDismiss = { playlistToDelete = null },
        )
    }

    if (songPickerVisible) {
        SongPickerDialog(
            songs = libraryState.songs,
            onConfirm = { songs ->
                songPickerVisible = false
                if (songs.isNotEmpty()) playlistPickerSongs = songs
            },
            onDismiss = { songPickerVisible = false },
        )
    }

    playlistPickerSongs?.let { songs ->
        PlaylistPickerDialog(
            playlists = playlists,
            songCount = songs.size,
            onPickPlaylist = { playlistId ->
                playlistsVm.addSongs(playlistId, songs)
                playlistPickerSongs = null
                libraryVm.clearSelection()
            },
            onAddToQueue = {
                libraryVm.addAllToQueue(songs)
                playlistPickerSongs = null
                libraryVm.clearSelection()
            },
            onCreatePlaylist = { name ->
                scope.launch {
                    val id = playlistsVm.create(name)
                    playlistsVm.addSongs(id, songs)
                    playlistPickerSongs = null
                    libraryVm.clearSelection()
                }
            },
            onDismiss = { playlistPickerSongs = null },
        )
    }
}

package com.tidalmusic.player.ui.navigation

import android.app.Activity
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tidalmusic.player.R
import com.tidalmusic.player.data.repository.PlaylistSummary
import com.tidalmusic.player.domain.model.Song
import com.tidalmusic.player.ui.components.TidalAlertDialog
import com.tidalmusic.player.ui.components.EditMetadataDialog
import com.tidalmusic.player.ui.components.ExternalPlaybackDialog
import com.tidalmusic.player.ui.components.MiniPlayer
import com.tidalmusic.player.ui.components.PlaylistPickerDialog
import com.tidalmusic.player.ui.components.SleepTimerDialog
import com.tidalmusic.player.ui.components.SongActionSheet
import com.tidalmusic.player.ui.components.SongDetailsDialog
import com.tidalmusic.player.ui.components.TidalTextButton
import com.tidalmusic.player.ui.collection.AlbumsScreen
import com.tidalmusic.player.ui.collection.ArtistsScreen
import com.tidalmusic.player.ui.collection.RecordingsScreen
import com.tidalmusic.player.ui.favorites.FavoritesScreen
import com.tidalmusic.player.ui.folders.FoldersScreen
import com.tidalmusic.player.ui.home.HomeScreen
import com.tidalmusic.player.ui.library.LibraryScreen
import com.tidalmusic.player.ui.library.SortDialog
import com.tidalmusic.player.ui.player.NowPlayingScreen
import com.tidalmusic.player.ui.player.PlayerControlsSheet
import com.tidalmusic.player.ui.player.QueueSheet
import com.tidalmusic.player.ui.playlists.DeletePlaylistDialog
import com.tidalmusic.player.ui.playlists.PlaylistsScreen
import com.tidalmusic.player.ui.playlists.PlaylistDetailRoute
import com.tidalmusic.player.ui.playlists.PlaylistNameDialog
import com.tidalmusic.player.ui.settings.SettingsScreen
import com.tidalmusic.player.ui.theme.MotionTokens
import com.tidalmusic.player.ui.viewmodel.AppViewModelFactory
import com.tidalmusic.player.ui.viewmodel.LibraryViewModel
import com.tidalmusic.player.ui.viewmodel.MetadataEdit
import com.tidalmusic.player.ui.viewmodel.PlaylistsViewModel
import com.tidalmusic.player.ui.viewmodel.PlayerViewModel
import com.tidalmusic.player.ui.viewmodel.SettingsViewModel
import com.tidalmusic.player.ui.viewmodel.SongActionsViewModel
import com.tidalmusic.player.util.TimeFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class ExternalPlaybackSnapshot(
    val songs: List<Song>,
    val startIndex: Int,
    val positionMs: Long,
    val wasPlaying: Boolean,
)

/**
 * The application shell.
 *
 * It owns the navigation bar, the persistent mini-player and the shared overlays (the song
 * action sheet and its dialogs). Every screen below it is a pure function of its own state.
 */
@Composable
fun MusicApp(
    factory: AppViewModelFactory,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    batteryExempt: Boolean,
    onRequestBatteryExemption: () -> Unit,
    externalSong: Song?,
    onExternalSongHandled: () -> Unit,
    externalAudioUnavailable: Boolean,
    onExternalAudioErrorHandled: () -> Unit,
    appVersion: String,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDeleteSongs by remember { mutableStateOf<List<Song>?>(null) }

    val libraryVm: LibraryViewModel = viewModel(factory = factory)
    val playerVm: PlayerViewModel = viewModel(factory = factory)
    val playlistsVm: PlaylistsViewModel = viewModel(factory = factory)
    val settingsVm: SettingsViewModel = viewModel(factory = factory)
    val songActionsVm: SongActionsViewModel = viewModel(factory = factory)

    val libraryState by libraryVm.state.collectAsStateWithLifecycle()
    val playbackState by playerVm.state.collectAsStateWithLifecycle()
    val queue by playerVm.queue.collectAsStateWithLifecycle()
    val isCurrentFavorite by playerVm.isFavorite.collectAsStateWithLifecycle()
    val sleepTimer by playerVm.sleepTimer.collectAsStateWithLifecycle()
    val playlists by playlistsVm.playlists.collectAsStateWithLifecycle()
    val isSavingPlaylist by playlistsVm.isSaving.collectAsStateWithLifecycle()
    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    val storage by settingsVm.storage.collectAsStateWithLifecycle()

    val selectedSong by songActionsVm.selectedSong.collectAsStateWithLifecycle()
    val detailsSong by songActionsVm.detailsSong.collectAsStateWithLifecycle()
    val detailsGenre by songActionsVm.detailsGenre.collectAsStateWithLifecycle()
    val actionPlaylists by songActionsVm.playlists.collectAsStateWithLifecycle()
    val actionFavorites by songActionsVm.favorites.collectAsStateWithLifecycle()
    val isActionBusy by songActionsVm.isBusy.collectAsStateWithLifecycle()
    val consentRequest by songActionsVm.consentRequest.collectAsStateWithLifecycle()
    val libraryRevision by songActionsVm.libraryRevision.collectAsStateWithLifecycle()
    val writePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val pending = pendingDeleteSongs
        pendingDeleteSongs = null
        if (granted && pending != null) {
            songActionsVm.requestDeleteSongs(pending)
        } else if (!granted) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.permission_denied_body))
            }
        }
    }

    fun requestDeleteSongs(songs: List<Song>) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            pendingDeleteSongs = songs
            writePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            songActionsVm.requestDeleteSongs(songs)
        }
    }

    // The library is scanned as soon as the permission is available.
    LaunchedEffect(hasPermission) {
        libraryVm.refresh(hasPermission)
    }
    LaunchedEffect(libraryRevision) {
        if (libraryRevision > 0) libraryVm.refresh(hasPermission, force = true)
    }

    LaunchedEffect(settings.includeNonMusic) {
        settingsVm.loadStorage(settings.includeNonMusic)
    }

    // Feedback from the screen models is shown as a snackbar.
    LaunchedEffect(Unit) {
        playerVm.messages.collect { message ->
            snackbarHostState.showSnackbar(
                context.getString(message.messageRes, *message.args.toTypedArray()),
            )
        }
    }
    LaunchedEffect(Unit) {
        playlistsVm.messages.collect { message ->
            snackbarHostState.showSnackbar(
                context.getString(message.messageRes, *message.args.toTypedArray()),
            )
        }
    }
    LaunchedEffect(Unit) {
        songActionsVm.messages.collect { message ->
            snackbarHostState.showSnackbar(
                context.getString(message.messageRes, *message.args.toTypedArray()),
            )
        }
    }
    LaunchedEffect(Unit) {
        songActionsVm.deletedSongs.collect { deletedSong ->
            if (playerVm.currentSong()?.contentUri == deletedSong.contentUri) {
                playerVm.stop()
            }
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val topLevelRoutes = remember { TopLevelDestination.entries.map { it.route } }
    val showBottomBar = currentRoute in topLevelRoutes

    LaunchedEffect(currentRoute) {
        if (currentRoute == Routes.PLAYER) {
            while (true) {
                playerVm.refreshVolume()
                delay(250L)
            }
        }
    }

    var showSortDialog by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showControlsSheet by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    // "Add songs to playlist" sends the user to the Library screen in selection mode:
    // they check the songs with the row checkboxes and confirm in the bottom bar.
    var addToPlaylistTarget by remember { mutableStateOf<PlaylistSummary?>(null) }
    // Song ids waiting for a target playlist: the picker adds them in one batch, so the
    // sheet flow (one song) and the library selection flow (many songs) share the dialog.
    var pickerSongIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    var showEditMetadata by remember { mutableStateOf(false) }
    var showCreatePlaylist by remember { mutableStateOf(false) }
    var renamePlaylist by remember { mutableStateOf<PlaylistSummary?>(null) }
    var deletePlaylist by remember { mutableStateOf<PlaylistSummary?>(null) }
    var shareEmptyPlaylist by remember { mutableStateOf<PlaylistSummary?>(null) }
    var externalPreviewSong by remember { mutableStateOf<Song?>(null) }
    var externalPlaybackSnapshot by remember { mutableStateOf<ExternalPlaybackSnapshot?>(null) }
    var deleteSongConfirmation by remember { mutableStateOf<Song?>(null) }
    var showAudioRouteWarning by remember { mutableStateOf(false) }
    var openLibrarySearch by remember { mutableStateOf(false) }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val request = consentRequest
        if (request?.metadata != null) {
            songActionsVm.onWriteConsentResult(result.resultCode == Activity.RESULT_OK)
        } else {
            songActionsVm.onDeleteConsentResult(result.resultCode == Activity.RESULT_OK)
        }
    }

    LaunchedEffect(consentRequest) {
        val request = consentRequest ?: return@LaunchedEffect
        try {
            consentLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        } catch (error: IntentSender.SendIntentException) {
            songActionsVm.cancelConsent()
            snackbarHostState.showSnackbar(context.getString(R.string.delete_file_failed))
        }
    }

    val shareSongs: (List<Song>) -> Unit = { songs ->
        if (songs.isNotEmpty()) {
            try {
                val mimeTypes = songs.mapNotNull { it.mimeType }.distinct()
                val mimeType = mimeTypes.singleOrNull()
                    ?.takeIf { it.startsWith("audio/") }
                    ?: "audio/*"
                val action = if (songs.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE
                val shareIntent = Intent(action).apply {
                    type = mimeType
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    if (songs.size == 1) {
                        putExtra(Intent.EXTRA_STREAM, songs.first().uri)
                    } else {
                        putParcelableArrayListExtra(
                            Intent.EXTRA_STREAM,
                            ArrayList(songs.map { it.uri }),
                        )
                    }
                    clipData = ClipData.newUri(
                        context.contentResolver,
                        songs.first().title,
                        songs.first().uri,
                    ).apply {
                        songs.drop(1).forEach { addItem(ClipData.Item(it.uri)) }
                    }
                }
                val chooser = Intent.createChooser(
                    shareIntent,
                    context.getString(R.string.share_chooser_title),
                ).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(chooser)
            } catch (error: ActivityNotFoundException) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.share_failed))
                }
            } catch (error: SecurityException) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.share_failed))
                }
            } catch (error: IllegalArgumentException) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.share_failed))
                }
            }
        }
    }
    val shareSong: (Song) -> Unit = { song -> shareSongs(listOf(song)) }

    LaunchedEffect(externalSong) {
        val song = externalSong ?: return@LaunchedEffect
        if (externalPlaybackSnapshot == null) {
            val queuedSongs = queue.map { it.song }
            val playingIndex = queuedSongs.indexOfFirst {
                it.id == playbackState.currentSong?.id
            }.coerceAtLeast(0)
            externalPlaybackSnapshot = ExternalPlaybackSnapshot(
                songs = queuedSongs,
                startIndex = playingIndex,
                positionMs = playerVm.progress.value.positionMs,
                wasPlaying = playbackState.isPlaying,
            )
        }
        externalPreviewSong = song
        playerVm.playExternalSong(song)
        onExternalSongHandled()
    }

    val audioManager = remember(context) {
        context.getSystemService(AudioManager::class.java)
    }
    DisposableEffect(audioManager, playbackState.isPlaying, showAudioRouteWarning) {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                val relevantTypes = setOf(
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    AudioDeviceInfo.TYPE_BLE_HEADSET,
                    AudioDeviceInfo.TYPE_BLE_SPEAKER,
                    AudioDeviceInfo.TYPE_HEARING_AID,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_USB_ACCESSORY,
                    AudioDeviceInfo.TYPE_USB_DEVICE,
                    AudioDeviceInfo.TYPE_USB_HEADSET,
                )
                val removedPersonalOutput = removedDevices.any { it.type in relevantTypes }
                val anotherPersonalOutputConnected = audioManager
                    .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    .any { it.type in relevantTypes }
                if (removedPersonalOutput &&
                    !anotherPersonalOutputConnected &&
                    playbackState.isPlaying &&
                    !showAudioRouteWarning
                ) {
                    playerVm.pause()
                    showAudioRouteWarning = true
                }
            }
        }
        audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        onDispose { audioManager.unregisterAudioDeviceCallback(callback) }
    }

    // Rapid-tap guard: a navigation is ignored while the previous one is still settling,
    // so double taps can never push two destinations.
    var navigationLocked by remember { mutableStateOf(false) }
    LaunchedEffect(currentRoute) {
        if (currentRoute != null) navigationLocked = false
    }

    fun navigateToTopLevel(route: String) {
        if (navigationLocked || currentRoute == route) return
        navigationLocked = true
        navController.navigate(route) {
            popUpTo(TopLevelDestination.HOME.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                Column {
                    AnimatedVisibility(
                        visible = playbackState.hasSong,
                        enter = fadeIn(animationSpec = tween(MotionTokens.TopLevelTransitionMs)),
                        exit = fadeOut(animationSpec = tween(MotionTokens.TopLevelTransitionMs)),
                    ) {
                        val miniPlayerProgress by playerVm.progress.collectAsStateWithLifecycle()
                        playbackState.currentSong?.let { song ->
                            MiniPlayer(
                                song = song,
                                isPlaying = playbackState.isPlaying,
                                progress = { miniPlayerProgress.progress },
                                onExpand = { navController.navigate(Routes.PLAYER) },
                                onPlayPause = playerVm::togglePlayPause,
                                onNext = playerVm::next,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                    }
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 0.dp,
                    ) {
                        TopLevelDestination.entries.forEach { destination ->
                            val selected = currentRoute == destination.route
                            // The selected tab icon lifts with a springy scale (NavigationButton
                            // animation); unselected tabs settle back to rest.
                            val iconScale by animateFloatAsState(
                                targetValue = if (selected) 1.15f else 1f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                                label = "navScale",
                            )
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTopLevel(destination.route) },
                                icon = {
                                    Icon(
                                        imageVector = destination.icon,
                                        contentDescription = null,
                                        modifier = Modifier.scale(iconScale),
                                    )
                                },
                                label = { Text(stringResource(destination.labelRes)) },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.HOME.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            enterTransition = {
                if (targetState.destination.route == Routes.PLAYER) {
                    slideInVertically(
                        animationSpec = tween(MotionTokens.PlayerTransitionMs),
                    ) { it } + fadeIn(animationSpec = tween(MotionTokens.PlayerTransitionMs))
                } else {
                    slideInHorizontally(
                        animationSpec = tween(MotionTokens.ScreenSlideMs),
                    ) { it / 6 } + fadeIn(animationSpec = tween(MotionTokens.ScreenSlideMs))
                }
            },
            exitTransition = {
                if (targetState.destination.route == Routes.PLAYER) {
                    // The screen under Now Playing shrinks back slightly — layered-sheet look.
                    scaleOut(
                        targetScale = 0.96f,
                        animationSpec = tween(MotionTokens.PlayerTransitionMs),
                    ) + fadeOut(animationSpec = tween(MotionTokens.PlayerTransitionMs))
                } else {
                    slideOutHorizontally(
                        animationSpec = tween(MotionTokens.ScreenSlideMs),
                    ) { -it / 10 } + fadeOut(animationSpec = tween(MotionTokens.ScreenSlideMs))
                }
            },
            popEnterTransition = {
                if (initialState.destination.route == Routes.PLAYER) {
                    scaleIn(
                        initialScale = 0.96f,
                        animationSpec = tween(MotionTokens.PlayerTransitionMs),
                    ) + fadeIn(animationSpec = tween(MotionTokens.PlayerTransitionMs))
                } else {
                    slideInHorizontally(
                        animationSpec = tween(MotionTokens.ScreenSlideMs),
                    ) { -it / 10 } + fadeIn(animationSpec = tween(MotionTokens.ScreenSlideMs))
                }
            },
            popExitTransition = {
                if (initialState.destination.route == Routes.PLAYER) {
                    slideOutVertically(
                        animationSpec = tween(MotionTokens.PlayerTransitionMs),
                    ) { it } + fadeOut(animationSpec = tween(MotionTokens.PlayerTransitionMs))
                } else {
                    slideOutHorizontally(
                        animationSpec = tween(MotionTokens.ScreenSlideMs),
                    ) { it / 6 } + fadeOut(animationSpec = tween(MotionTokens.ScreenSlideMs))
                }
            },
        ) {
            composable(TopLevelDestination.HOME.route) {
                val favoriteSongs = libraryState.allSongs.values.filter {
                    it.id in libraryState.favorites
                }
                HomeScreen(
                    recentSongs = libraryState.recentlyPlayed,
                    favoriteSongs = favoriteSongs,
                    librarySummary = librarySummary(
                        context,
                        libraryState.songCount,
                        libraryState.allSongs.values.distinctBy { it.albumId }.size,
                    ),
                    currentSongId = playbackState.currentSong?.id,
                    isPlaying = playbackState.isPlaying,
                    hasPermission = hasPermission,
                    viewMode = settings.viewModeHome,
                    onViewModeChange = settingsVm::setViewModeHome,
                    onSongLongPress = songActionsVm::select,
                    onSongOverflow = songActionsVm::select,
                    onOpenAlbums = { navController.navigate(Routes.ALBUMS) },
                    onOpenArtists = { navController.navigate(Routes.ARTISTS) },
                    onOpenRecordings = { navController.navigate(Routes.RECORDINGS) },
                    onOpenFavorites = { navController.navigate(Routes.FAVORITES) },
                    onPlayRecent = { song ->
                        val songs = libraryState.recentlyPlayed
                        val index = songs.indexOfFirst { it.id == song.id }
                        if (index >= 0) playerVm.playSongs(songs, index)
                    },
                    onPlayFavorite = { song ->
                        val index = favoriteSongs.indexOfFirst { it.id == song.id }
                        if (index >= 0) playerVm.playSongs(favoriteSongs, index)
                    },
                    onOpenSearch = {
                        openLibrarySearch = true
                        navigateToTopLevel(TopLevelDestination.LIBRARY.route)
                    },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onBrowseLibrary = { navigateToTopLevel(TopLevelDestination.LIBRARY.route) },
                    onRequestPermission = onRequestPermission,
                )
            }

            composable(TopLevelDestination.LIBRARY.route) {
                LibraryScreen(
                    state = libraryState,
                    currentSongId = playbackState.currentSong?.id,
                    isPlaying = playbackState.isPlaying,
                    density = settings.density,
                    showArtwork = settings.showArtwork,
                    viewMode = settings.viewModeLibrary,
                    onViewModeChange = settingsVm::setViewModeLibrary,
                    onQueryChange = libraryVm::onQueryChange,
                    onClearQuery = libraryVm::clearQuery,
                    onSortClick = { showSortDialog = true },
                    onRefresh = { libraryVm.refresh(hasPermission, force = true) },
                    onPlay = { songs, index -> playerVm.playSongs(songs, index) },
                    onSongOverflow = songActionsVm::select,
                    onRequestPermission = onRequestPermission,
                    onOpenAppSettings = onOpenAppSettings,
                    onRemoveSongs = ::requestDeleteSongs,
                    onShareSongs = shareSongs,
                    onBack = {
                        navController.popBackStack(
                            TopLevelDestination.HOME.route,
                            inclusive = false,
                        )
                    },
                    openSearch = openLibrarySearch,
                    onSearchOpened = { openLibrarySearch = false },
                    addPlaylistName = addToPlaylistTarget?.name,
                    onAddSelectedToPlaylist = { songs ->
                        val target = addToPlaylistTarget
                        if (target != null) {
                            songActionsVm.addSongsToPlaylist(target.id, songs.map { it.id })
                        }
                    },
                    onAddSelectionToPlaylist = { songs ->
                        if (songs.isNotEmpty()) {
                            pickerSongIds = songs.map { it.id }
                            addToPlaylistTarget = null
                            showPlaylistPicker = true
                        }
                    },
                    onExitAddMode = { addToPlaylistTarget = null },
                )
            }

            composable(TopLevelDestination.PLAYLISTS.route) {
                PlaylistsScreen(
                    playlists = playlists,
                    isLoading = false,
                    coverSongs = { playlist ->
                        playlist.songIds.mapNotNull { libraryState.allSongs[it] }
                    },
                    onOpenPlaylist = { id -> navController.navigate(Routes.playlistDetail(id)) },
                    onCreatePlaylist = { showCreatePlaylist = true },
                    onRenamePlaylist = { renamePlaylist = it },
                    onDeletePlaylist = { deletePlaylist = it },
                    onSharePlaylist = { playlist ->
                        val songs = playlist.songIds.mapNotNull { libraryState.allSongs[it] }
                        if (songs.isEmpty()) shareEmptyPlaylist = playlist else shareSongs(songs)
                    },
                    onOpenFolders = { navController.navigate(Routes.FOLDERS) },
                )
            }

            composable(Routes.FOLDERS) {
                FoldersScreen(
                    songs = libraryState.allSongs.values.toList(),
                    currentSongId = playbackState.currentSong?.id,
                    isPlaying = playbackState.isPlaying,
                    viewMode = settings.viewModeFolders,
                    onViewModeChange = settingsVm::setViewModeFolders,
                    onBack = { navController.popBackStack() },
                    onPlaySongs = { songs, index -> playerVm.playSongs(songs, index) },
                    onShareSongs = shareSongs,
                    onDeleteSongs = ::requestDeleteSongs,
                    onSongOverflow = songActionsVm::select,
                )
            }

            composable(
                route = Routes.PLAYLIST_DETAIL,
                arguments = listOf(
                    navArgument(Routes.PLAYLIST_ID_ARG) { type = NavType.LongType },
                ),
            ) { entry ->
                val playlistId = entry.arguments?.getLong(Routes.PLAYLIST_ID_ARG) ?: 0L
                PlaylistDetailRoute(
                    playlistId = playlistId,
                    factory = factory,
                    libraryState = libraryState,
                    currentSongId = playbackState.currentSong?.id,
                    isPlaying = playbackState.isPlaying,
                    onBack = { navController.popBackStack() },
                    onPlay = { songs, index -> playerVm.playSongs(songs, index) },
                    onShuffle = { songs -> playerVm.playSongs(songs, 0, shuffle = true) },
                    onAddSongs = { playlist ->
                        addToPlaylistTarget = playlist
                        openLibrarySearch = false
                        navigateToTopLevel(TopLevelDestination.LIBRARY.route)
                    },
                    onRenamePlaylist = { renamePlaylist = it },
                    onDeletePlaylist = { deletePlaylist = it },
                    onSharePlaylistSongs = shareSongs,
                    onShareEmptyPlaylist = { shareEmptyPlaylist = it },
                    onSongOverflow = songActionsVm::select,
                )
            }

            composable(Routes.FAVORITES) {
                FavoritesScreen(
                    songs = libraryState.allSongs.values.toList(),
                    favoriteIds = libraryState.favorites,
                    currentSongId = playbackState.currentSong?.id,
                    isPlaying = playbackState.isPlaying,
                    onBack = { navController.popBackStack() },
                    onPlaySongs = { songs, index -> playerVm.playSongs(songs, index) },
                    onSongOverflow = songActionsVm::select,
                )
            }

            composable(Routes.ALBUMS) {
                AlbumsScreen(
                    songs = libraryState.allSongs.values.toList(),
                    currentSongId = playbackState.currentSong?.id,
                    isPlaying = playbackState.isPlaying,
                    onBack = { navController.popBackStack() },
                    onPlaySongs = { songs, index -> playerVm.playSongs(songs, index) },
                    onSongOverflow = songActionsVm::select,
                )
            }

            composable(Routes.ARTISTS) {
                ArtistsScreen(
                    songs = libraryState.allSongs.values.toList(),
                    currentSongId = playbackState.currentSong?.id,
                    isPlaying = playbackState.isPlaying,
                    onBack = { navController.popBackStack() },
                    onPlaySongs = { songs, index -> playerVm.playSongs(songs, index) },
                    onSongOverflow = songActionsVm::select,
                )
            }

            composable(Routes.RECORDINGS) {
                RecordingsScreen(
                    songs = libraryState.allSongs.values.toList(),
                    currentSongId = playbackState.currentSong?.id,
                    isPlaying = playbackState.isPlaying,
                    onBack = { navController.popBackStack() },
                    onPlaySongs = { songs, index -> playerVm.playSongs(songs, index) },
                    onSongOverflow = songActionsVm::select,
                )
            }

            composable(
                route = Routes.EDITOR,
                arguments = listOf(
                    navArgument(Routes.EDITOR_ID_ARG) { type = NavType.LongType },
                ),
            ) { entry ->
                val songId = entry.arguments?.getLong(Routes.EDITOR_ID_ARG) ?: 0L
                val song = libraryState.allSongs[songId]
                if (song == null) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    val factory = remember(song) { factory.editorFactory(song, libraryState.allSongs.values.toList()) }
                    val editorVm: com.tidalmusic.player.audioeditor.AudioEditorViewModel =
                        androidx.lifecycle.viewmodel.compose.viewModel(factory = factory)
                    com.tidalmusic.player.audioeditor.AudioEditorScreen(
                        viewModel = editorVm,
                        onBack = { navController.popBackStack() },
                    )
                }
            }

            composable(Routes.PLAYER) {
                val playerProgress by playerVm.progress.collectAsStateWithLifecycle()
                val volume by playerVm.volume.collectAsStateWithLifecycle()
                NowPlayingScreen(
                    state = playbackState,
                    progress = playerProgress,
                    isFavorite = isCurrentFavorite,
                    artworkAnimationEnabled = settings.artworkAnimation,
                    onBack = { navController.popBackStack() },
                    onPlayPause = playerVm::togglePlayPause,
                    onNext = playerVm::next,
                    onPrevious = playerVm::previous,
                    onShuffle = playerVm::toggleShuffle,
                    onRepeat = playerVm::cycleRepeatMode,
                    onSeek = playerVm::seekTo,
                    onToggleFavorite = playerVm::toggleFavorite,
                    onOpenQueue = { showQueueSheet = true },
                    onOpenMore = { showControlsSheet = true },
                    volume = volume,
                    onVolumeChange = playerVm::setVolume,
                    playbackSpeed = playbackState.playbackSpeed,
                    onPlaybackSpeed = playerVm::setPlaybackSpeed,
                    onSongInfo = {
                        playbackState.currentSong?.let(songActionsVm::showDetails)
                    },
                    onShare = {
                        playbackState.currentSong?.let(shareSong)
                    },
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    settings = settings,
                    storage = storage,
                    appVersion = appVersion,
                    hasAudioPermission = hasPermission,
                    onBack = { navController.popBackStack() },
                    onThemeChange = settingsVm::setTheme,
                    onDynamicColorChange = settingsVm::setDynamicColor,
                    onArtworkAnimationChange = settingsVm::setArtworkAnimation,
                    onDensityChange = settingsVm::setDensity,
                    onResumePlaybackChange = settingsVm::setResumePlayback,
                    onShuffleNewSessionsChange = settingsVm::setShuffleNewSessions,
                    onDefaultRepeatChange = settingsVm::setDefaultRepeatMode,
                    onDefaultSpeedChange = settingsVm::setDefaultPlaybackSpeed,
                    onIncludeNonMusicChange = settingsVm::setIncludeNonMusic,
                    onShowArtworkChange = settingsVm::setShowArtwork,
                    onDefaultSortChange = settingsVm::setDefaultSort,
                    onRescan = { libraryVm.refresh(hasPermission, force = true) },
                    onRequestPermission = onRequestPermission,
                    onOpenAppSettings = onOpenAppSettings,
                    onOpenSystemVolume = { openSystemVolume(context) },
                    batteryExempt = batteryExempt,
                    onRequestBatteryExemption = onRequestBatteryExemption,
                    onAppLanguageChange = { language ->
                        settingsVm.setAppLanguage(language)
                        (context as? Activity)?.let { activity ->
                            com.tidalmusic.player.util.AppLanguage.apply(
                                activity,
                                when (language) {
                                    com.tidalmusic.player.data.preferences.AppLanguage.SYSTEM ->
                                        com.tidalmusic.player.util.AppLanguage.Option.SYSTEM

                                    com.tidalmusic.player.data.preferences.AppLanguage.ENGLISH ->
                                        com.tidalmusic.player.util.AppLanguage.Option.ENGLISH

                                    com.tidalmusic.player.data.preferences.AppLanguage.PERSIAN ->
                                        com.tidalmusic.player.util.AppLanguage.Option.PERSIAN
                                },
                            )
                        }
                    },
                )
            }
        }
    }

    // ------------------------------------------------------------------ overlays

    if (showSortDialog) {
        SortDialog(
            current = libraryState.sort,
            onDismiss = { showSortDialog = false },
            onSelect = libraryVm::setSort,
        )
    }

    if (showQueueSheet) {
        QueueSheet(
            queue = queue,
            onDismiss = { showQueueSheet = false },
            onPlayItem = playerVm::skipToQueueItem,
            onRemoveItem = playerVm::removeFromQueue,
            onMoveItem = playerVm::moveQueueItem,
            onClearQueue = playerVm::clearQueue,
        )
    }

    if (showControlsSheet) {
        PlayerControlsSheet(
            playbackSpeed = playbackState.playbackSpeed,
            isFavorite = isCurrentFavorite,
            onDismiss = { showControlsSheet = false },
            onPlaybackSpeed = playerVm::setPlaybackSpeed,
            onSleepTimer = {
                showControlsSheet = false
                showSleepTimer = true
            },
            onStopPlayback = {
                showControlsSheet = false
                playerVm.stop()
            },
            onOpenQueue = {
                showControlsSheet = false
                showQueueSheet = true
            },
            onToggleFavorite = playerVm::toggleFavorite,
            onAddToPlaylist = {
                showControlsSheet = false
                val song = playbackState.currentSong
                if (song != null) {
                    addToPlaylistTarget = null
                    pickerSongIds = listOf(song.id)
                    showPlaylistPicker = true
                }
            },
            onSongInfo = {
                showControlsSheet = false
                playbackState.currentSong?.let(songActionsVm::showDetails)
            },
            onShare = {
                showControlsSheet = false
                playbackState.currentSong?.let(shareSong)
            },
        )
    }

    if (showSleepTimer) {
        SleepTimerDialog(
            isActive = sleepTimer.isActive,
            onDismiss = { showSleepTimer = false },
            onSelect = { minutes ->
                playerVm.startSleepTimer(minutes)
                showSleepTimer = false
            },
            onCancel = {
                playerVm.cancelSleepTimer()
                showSleepTimer = false
            },
        )
    }

    selectedSong?.let { song ->
        SongActionSheet(
            song = song,
            isFavorite = song.id in actionFavorites,
            onDismiss = songActionsVm::clearSelection,
            onPlayNow = {
                songActionsVm.clearSelection()
                val index = libraryState.songs.indexOfFirst { it.id == song.id }
                if (index >= 0) {
                    playerVm.playSongs(libraryState.songs, index)
                } else {
                    // The sheet can be opened from a playlist or while a filter is active;
                    // the visible list may not contain the song. Never leave the action dead.
                    libraryState.allSongs[song.id]?.let { resolved ->
                        playerVm.playSongs(listOf(resolved), 0)
                    }
                }
            },
            onPlayNext = {
                songActionsVm.clearSelection()
                playerVm.playNext(song)
            },
            onAddToQueue = {
                songActionsVm.clearSelection()
                playerVm.addToQueue(song)
            },
            onAddToPlaylist = {
                songActionsVm.clearSelection()
                addToPlaylistTarget = null
                pickerSongIds = listOf(song.id)
                showPlaylistPicker = true
            },
            onToggleFavorite = { songActionsVm.toggleFavorite(song) },
            onDetails = {
                songActionsVm.clearSelection()
                songActionsVm.showDetails(song)
            },
            onEditMetadata = {
                songActionsVm.clearSelection()
                showEditMetadata = true
            },
            onEditAudio = {
                songActionsVm.clearSelection()
                navController.navigate(Routes.editor(song.id))
            },
            onShare = {
                songActionsVm.clearSelection()
                shareSong(song)
            },
            onHide = {
                songActionsVm.clearSelection()
                songActionsVm.setHidden(song, true)
            },
            onDelete = {
                songActionsVm.clearSelection()
                deleteSongConfirmation = song
            },
        )
    }

    deleteSongConfirmation?.let { song ->
        TidalAlertDialog(
            onDismissRequest = { deleteSongConfirmation = null },
            title = { Text(context.getString(R.string.delete_file_title, song.title)) },
            text = {
                Text(
                    stringResource(
                        R.string.delete_file_body_with_size,
                        song.title,
                        TimeFormat.fileSize(song.sizeBytes),
                    ),
                )
            },
            confirmButton = {
                TidalTextButton(
                    onClick = {
                        deleteSongConfirmation = null
                        requestDeleteSongs(listOf(song))
                    },
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TidalTextButton(onClick = { deleteSongConfirmation = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showPlaylistPicker) {
        PlaylistPickerDialog(
            playlists = actionPlaylists,
            onDismiss = {
                showPlaylistPicker = false
                pickerSongIds = emptyList()
            },
            onPick = { playlistId ->
                showPlaylistPicker = false
                val songIds = pickerSongIds.ifEmpty {
                    listOfNotNull(selectedSong?.id ?: playbackState.currentSong?.id)
                }
                pickerSongIds = emptyList()
                if (songIds.isNotEmpty()) songActionsVm.addSongsToPlaylist(playlistId, songIds)
            },
            onCreateNew = { name ->
                showPlaylistPicker = false
                val songIds = pickerSongIds.ifEmpty {
                    listOfNotNull(selectedSong?.id ?: playbackState.currentSong?.id)
                }
                pickerSongIds = emptyList()
                if (songIds.isNotEmpty()) {
                    songActionsVm.createPlaylistWithSongs(name, songIds)
                }
            },
        )
    }

    detailsSong?.let { song ->
        SongDetailsDialog(
            song = song,
            genre = detailsGenre,
            onDismiss = songActionsVm::dismissDetails,
        )
    }

    if (showEditMetadata) {
        val song = selectedSong ?: playbackState.currentSong
        if (song != null) {
            EditMetadataDialog(
                song = song,
                isSaving = isActionBusy,
                onDismiss = { showEditMetadata = false },
                onSave = { title, artist, album, genre ->
                    songActionsVm.requestMetadataWrite(
                        song,
                        MetadataEdit(title, artist, album, genre),
                    )
                    showEditMetadata = false
                },
            )
        } else {
            showEditMetadata = false
        }
    }

    if (showCreatePlaylist) {
        PlaylistNameDialog(
            title = stringResource(R.string.playlist_create_title),
            isSaving = isSavingPlaylist,
            onDismiss = { showCreatePlaylist = false },
            onConfirm = { name, description ->
                playlistsVm.create(name, description)
                showCreatePlaylist = false
            },
        )
    }

    renamePlaylist?.let { playlist ->
        PlaylistNameDialog(
            title = stringResource(R.string.playlist_edit_title),
            initialName = playlist.name,
            initialDescription = playlist.description.orEmpty(),
            isSaving = isSavingPlaylist,
            confirmLabel = R.string.action_save,
            onDismiss = { renamePlaylist = null },
            onConfirm = { name, description ->
                playlistsVm.rename(playlist.id, name, description)
                renamePlaylist = null
            },
        )
    }

    deletePlaylist?.let { playlist ->
        DeletePlaylistDialog(
            playlistName = playlist.name,
            onDismiss = { deletePlaylist = null },
            onConfirm = {
                playlistsVm.delete(playlist.id)
                deletePlaylist = null
            },
        )
    }

    shareEmptyPlaylist?.let { playlist ->
        TidalAlertDialog(
            onDismissRequest = { shareEmptyPlaylist = null },
            title = { Text(stringResource(R.string.playlist_share_empty_title)) },
            text = { Text(stringResource(R.string.playlist_share_empty_body, playlist.name)) },
            confirmButton = {
                TidalTextButton(
                    onClick = {
                        shareEmptyPlaylist = null
                        addToPlaylistTarget = playlist
                        openLibrarySearch = false
                        navigateToTopLevel(TopLevelDestination.LIBRARY.route)
                    },
                ) {
                    Text(stringResource(R.string.playlist_add_songs))
                }
            },
            dismissButton = {
                TidalTextButton(onClick = { shareEmptyPlaylist = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    externalPreviewSong?.let { song ->
        val externalProgress by playerVm.progress.collectAsStateWithLifecycle()
        ExternalPlaybackDialog(
            song = song,
            isPlaying = playbackState.isPlaying,
            progress = externalProgress,
            onPlayPause = playerVm::togglePlayPause,
            onSeek = playerVm::seekTo,
            onOpenInApp = {
                externalPreviewSong = null
                externalPlaybackSnapshot = null
                navController.navigate(Routes.PLAYER)
            },
            onClose = {
                externalPreviewSong = null
                externalPlaybackSnapshot?.let { snapshot ->
                    playerVm.restorePlaybackContext(
                        songs = snapshot.songs,
                        startIndex = snapshot.startIndex,
                        positionMs = snapshot.positionMs,
                        wasPlaying = snapshot.wasPlaying,
                    )
                }
                externalPlaybackSnapshot = null
            },
        )
    }

    if (externalAudioUnavailable) {
        TidalAlertDialog(
            onDismissRequest = onExternalAudioErrorHandled,
            title = { Text(stringResource(R.string.external_audio_title)) },
            text = { Text(stringResource(R.string.external_audio_unavailable)) },
            confirmButton = {
                TidalTextButton(onClick = onExternalAudioErrorHandled) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
        )
    }

    if (showAudioRouteWarning) {
        TidalAlertDialog(
            onDismissRequest = { showAudioRouteWarning = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            title = { Text(stringResource(R.string.audio_device_disconnected_title)) },
            text = { Text(stringResource(R.string.audio_device_disconnected_body)) },
            confirmButton = {
                TidalTextButton(onClick = { showAudioRouteWarning = false }) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
        )
    }

    val hasOpenOverlay = showSortDialog ||
        showQueueSheet ||
        showControlsSheet ||
        showSleepTimer ||
        showPlaylistPicker ||
        showEditMetadata ||
        showCreatePlaylist ||
        renamePlaylist != null ||
        deletePlaylist != null ||
        selectedSong != null ||
        detailsSong != null ||
        deleteSongConfirmation != null ||
        shareEmptyPlaylist != null ||
        externalPreviewSong != null ||
        externalAudioUnavailable ||
        showAudioRouteWarning
    BackHandler(
        enabled = currentRoute != TopLevelDestination.HOME.route &&
            currentRoute != TopLevelDestination.LIBRARY.route &&
            !hasOpenOverlay,
    ) {
        if (currentRoute == TopLevelDestination.PLAYLISTS.route) {
            navController.popBackStack(
                TopLevelDestination.HOME.route,
                inclusive = false,
            )
        } else {
            navController.popBackStack()
        }
    }
}

/** The one-line summary shown under the app name on the home screen. */
private fun librarySummary(context: Context, songCount: Int, albumCount: Int): String =
    context.getString(R.string.home_library_summary, songCount, albumCount)

/** Opens Android's sound settings from the Settings screen. */
private fun openSystemVolume(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_SOUND_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

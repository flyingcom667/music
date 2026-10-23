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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
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
import com.tidalmusic.player.ui.components.EditMetadataDialog
import com.tidalmusic.player.ui.components.ExternalPlaybackDialog
import com.tidalmusic.player.ui.components.MiniPlayer
import com.tidalmusic.player.ui.components.PlaylistPickerDialog
import com.tidalmusic.player.ui.components.SleepTimerDialog
import com.tidalmusic.player.ui.components.SongActionSheet
import com.tidalmusic.player.ui.components.SongDetailsDialog
import com.tidalmusic.player.ui.components.TidalTextButton
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
    val progress by playerVm.progress.collectAsStateWithLifecycle()
    val queue by playerVm.queue.collectAsStateWithLifecycle()
    val isCurrentFavorite by playerVm.isFavorite.collectAsStateWithLifecycle()
    val volume by playerVm.volume.collectAsStateWithLifecycle()
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
    var showEditMetadata by remember { mutableStateOf(false) }
    var showCreatePlaylist by remember { mutableStateOf(false) }
    var renamePlaylist by remember { mutableStateOf<PlaylistSummary?>(null) }
    var deletePlaylist by remember { mutableStateOf<PlaylistSummary?>(null) }
    var externalPreviewSong by remember { mutableStateOf<Song?>(null) }
    var externalPlaybackSnapshot by remember { mutableStateOf<ExternalPlaybackSnapshot?>(null) }
    var deleteSongConfirmation by remember { mutableStateOf<Song?>(null) }
    var showAudioRouteWarning by remember { mutableStateOf(false) }
    var openLibrarySearch by remember { mutableStateOf(false) }

    // The library list state lives here so it survives switching tabs and destinations.
    val libraryListState = rememberLazyListState()
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
                positionMs = progress.positionMs,
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

    fun navigateToTopLevel(route: String) {
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
                        enter = expandVertically(
                            animationSpec = tween(MotionTokens.ScreenTransitionMs),
                        ) + fadeIn(animationSpec = tween(MotionTokens.ScreenTransitionMs)),
                        exit = shrinkVertically(
                            animationSpec = tween(MotionTokens.ScreenTransitionMs),
                        ) + fadeOut(animationSpec = tween(MotionTokens.ScreenTransitionMs)),
                    ) {
                        playbackState.currentSong?.let { song ->
                            MiniPlayer(
                                song = song,
                                isPlaying = playbackState.isPlaying,
                                progress = { progress.progress },
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
                            NavigationBarItem(
                                selected = currentRoute == destination.route,
                                onClick = { navigateToTopLevel(destination.route) },
                                icon = {
                                    Icon(
                                        imageVector = destination.icon,
                                        contentDescription = null,
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
                val switchingTopLevel = initialState.destination.route in topLevelRoutes &&
                    targetState.destination.route in topLevelRoutes
                if (switchingTopLevel) {
                    fadeIn(animationSpec = tween(MotionTokens.TopLevelTransitionMs))
                } else {
                    fadeIn(animationSpec = tween(MotionTokens.ScreenTransitionMs)) +
                        slideInHorizontally(
                            initialOffsetX = { width -> width / 18 },
                            animationSpec = tween(MotionTokens.ScreenTransitionMs),
                        )
                }
            },
            exitTransition = {
                val switchingTopLevel = initialState.destination.route in topLevelRoutes &&
                    targetState.destination.route in topLevelRoutes
                if (switchingTopLevel) {
                    fadeOut(animationSpec = tween(MotionTokens.TopLevelTransitionMs))
                } else {
                    fadeOut(animationSpec = tween(MotionTokens.ScreenTransitionMs)) +
                        slideOutHorizontally(
                            targetOffsetX = { width -> -width / 24 },
                            animationSpec = tween(MotionTokens.ScreenTransitionMs),
                        )
                }
            },
            popEnterTransition = {
                val switchingTopLevel = initialState.destination.route in topLevelRoutes &&
                    targetState.destination.route in topLevelRoutes
                if (switchingTopLevel) {
                    fadeIn(animationSpec = tween(MotionTokens.TopLevelTransitionMs))
                } else {
                    fadeIn(animationSpec = tween(MotionTokens.ScreenTransitionMs)) +
                        slideInHorizontally(
                            initialOffsetX = { width -> -width / 18 },
                            animationSpec = tween(MotionTokens.ScreenTransitionMs),
                        )
                }
            },
            popExitTransition = {
                val switchingTopLevel = initialState.destination.route in topLevelRoutes &&
                    targetState.destination.route in topLevelRoutes
                if (switchingTopLevel) {
                    fadeOut(animationSpec = tween(MotionTokens.TopLevelTransitionMs))
                } else {
                    fadeOut(animationSpec = tween(MotionTokens.ScreenTransitionMs)) +
                        slideOutHorizontally(
                            targetOffsetX = { width -> width / 24 },
                            animationSpec = tween(MotionTokens.ScreenTransitionMs),
                        )
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
                    listState = libraryListState,
                    currentSongId = playbackState.currentSong?.id,
                    isPlaying = playbackState.isPlaying,
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
                    onOpenPlayer = { navController.navigate(Routes.PLAYER) },
                    onSongOverflow = songActionsVm::select,
                )
            }

            composable(Routes.PLAYER) {
                NowPlayingScreen(
                    state = playbackState,
                    progress = progress,
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
            onOpenQueue = {
                showControlsSheet = false
                showQueueSheet = true
            },
            onToggleFavorite = playerVm::toggleFavorite,
            onAddToPlaylist = {
                showControlsSheet = false
                showPlaylistPicker = true
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
                if (index >= 0) playerVm.playSongs(libraryState.songs, index)
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
        AlertDialog(
            onDismissRequest = { deleteSongConfirmation = null },
            title = { Text(context.getString(R.string.delete_file_title, song.title)) },
            text = { Text(stringResource(R.string.delete_file_body)) },
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
            onDismiss = { showPlaylistPicker = false },
            onPick = { playlistId ->
                showPlaylistPicker = false
                val song = selectedSong ?: playbackState.currentSong
                if (song != null) songActionsVm.addToPlaylist(playlistId, song.id)
            },
            onCreateNew = { name ->
                showPlaylistPicker = false
                val song = selectedSong ?: playbackState.currentSong
                if (song != null) songActionsVm.createPlaylistWithSong(name, song.id)
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

    externalPreviewSong?.let { song ->
        ExternalPlaybackDialog(
            song = song,
            isPlaying = playbackState.isPlaying,
            progress = progress,
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
        AlertDialog(
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
        AlertDialog(
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

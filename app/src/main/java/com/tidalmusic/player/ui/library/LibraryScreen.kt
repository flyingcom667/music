package com.tidalmusic.player.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tidalmusic.player.R
import com.tidalmusic.player.domain.model.Song
import com.tidalmusic.player.domain.model.SortDirection
import com.tidalmusic.player.domain.model.SortOrder
import com.tidalmusic.player.domain.model.SortSpec
import com.tidalmusic.player.ui.components.DeterminateLoadingState
import com.tidalmusic.player.ui.components.EmptyState
import com.tidalmusic.player.ui.components.ErrorState
import com.tidalmusic.player.ui.components.InlineLoadingRow
import com.tidalmusic.player.ui.components.LoadingState
import com.tidalmusic.player.ui.components.SongRow
import com.tidalmusic.player.ui.components.TidalTextButton
import com.tidalmusic.player.ui.viewmodel.LibraryStatus
import com.tidalmusic.player.ui.viewmodel.LibraryUiState

/**
 * The Library screen: exactly two primary tabs, a search field and a sorting action.
 *
 * The screen is a pure function of [state] and its callbacks — it never queries the media
 * provider or the database itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    listState: LazyListState,
    currentSongId: Long?,
    isPlaying: Boolean,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onSortClick: () -> Unit,
    onRefresh: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    onSongOverflow: (Song) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onRemoveSongs: (List<Song>) -> Unit,
    onShareSongs: (List<Song>) -> Unit,
    onBack: () -> Unit,
    openSearch: Boolean,
    onSearchOpened: () -> Unit,
    addPlaylistName: String? = null,
    onAddSelectedToPlaylist: (List<Song>) -> Unit = {},
    onExitAddMode: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var searchExpanded by remember { mutableStateOf(false) }
    // Entering the add-to-playlist flow switches the screen into selection mode so the
    // user can immediately start ticking songs.
    var selectionMode by rememberSaveable { mutableStateOf(addPlaylistName != null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(addPlaylistName) {
        if (addPlaylistName != null) selectionMode = true
    }
    var selectedSongIds by rememberSaveable(
        stateSaver = object : androidx.compose.runtime.saveable.Saver<Set<Long>, List<Long>> {
            override fun androidx.compose.runtime.saveable.SaverScope.save(
                value: Set<Long>,
            ): List<Long>? = value.toList()

            override fun restore(value: List<Long>): Set<Long> = value.toSet()
        },
        key = "library_selected_song_ids",
    ) { mutableStateOf<Set<Long>>(emptySet()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(searchExpanded) {
        if (searchExpanded) {
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    LaunchedEffect(openSearch) {
        if (openSearch) {
            searchExpanded = true
            onSearchOpened()
        }
    }
    DisposableEffect(lifecycleOwner, focusManager, keyboardController) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                searchExpanded = false
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            keyboardController?.hide()
        }
    }

    fun clearSelection() {
        selectedSongIds = emptySet()
        selectionMode = false
    }

    BackHandler {
        if (selectionMode) clearSelection() else onBack()
    }

    val songs = if (selectedTab == 0) state.songs else state.recentlyAdded
    val titleHiddenByScroll = listState.firstVisibleItemIndex > 0 ||
        listState.firstVisibleItemScrollOffset > 0
    // The letter rail follows the *visible* tab: the All Songs tab is sorted by title, so
    // the precomputed index applies; other views jump to the first row of the matching song.
    val showLetterRail = selectedTab == 0 && state.indexLetters.size > 1
    val activeLetter = remember(listState, state.indexLetters) {
        val firstIndex = listState.firstVisibleItemIndex.coerceAtMost(songs.lastIndex)
        songs.getOrNull(firstIndex)?.title?.let { visibleTitle ->
            val target = com.tidalmusic.player.domain.LibraryQueries.indexLetter(visibleTitle)
            state.indexLetters.firstOrNull { it == target } ?: state.indexLetters.firstOrNull()
        }
    }

    fun scrollToLetter(letter: Char) {
        val start = state.startIndexFor(letter) ?: return
        scope.launch { listState.animateScrollToItem(start) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                Text(
                    text = if (addPlaylistName != null && selectedSongIds.isNotEmpty()) {
                        stringResource(
                            R.string.library_add_n_to_playlist,
                            selectedSongIds.size,
                            addPlaylistName,
                        )
                    } else if (addPlaylistName != null) {
                        stringResource(R.string.library_pick_songs_for_playlist, addPlaylistName)
                    } else {
                        stringResource(R.string.library_selection_count, selectedSongIds.size)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(
                    onClick = {
                        val visibleIds = songs.map { it.id }.toSet()
                        selectedSongIds = if (visibleIds.all { it in selectedSongIds }) {
                            selectedSongIds - visibleIds
                        } else {
                            selectedSongIds + visibleIds
                        }
                    },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SelectAll,
                        contentDescription = stringResource(R.string.library_select_all),
                    )
                }
                if (addPlaylistName == null) {
                    IconButton(
                        onClick = {
                            val selected = state.allSongs.values.filter { it.id in selectedSongIds }
                            if (selected.isNotEmpty()) {
                                onShareSongs(selected)
                                clearSelection()
                            }
                        },
                        enabled = selectedSongIds.isNotEmpty(),
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = stringResource(R.string.library_share_selected),
                        )
                    }
                    IconButton(
                        onClick = { confirmDelete = true },
                        enabled = selectedSongIds.isNotEmpty(),
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RemoveCircleOutline,
                            contentDescription = stringResource(R.string.library_delete_selected),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    // "Add" is the primary action of the flow: it stays disabled until at
                    // least one song is checked, so nothing can be added accidentally.
                    FilledTonalButton(
                        onClick = {
                            val selected = state.allSongs.values.filter { it.id in selectedSongIds }
                            onAddSelectedToPlaylist(selected)
                            clearSelection()
                            onExitAddMode()
                        },
                        enabled = selectedSongIds.isNotEmpty(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = stringResource(R.string.action_add),
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
                IconButton(
                    onClick = {
                        clearSelection()
                        onExitAddMode()
                    },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.action_cancel),
                    )
                }
            } else {
                AnimatedVisibility(
                    visible = !titleHiddenByScroll,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(R.string.library_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                    )
                }
                IconButton(
                    onClick = { searchExpanded = !searchExpanded },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = if (searchExpanded) Icons.Rounded.Close else Icons.Rounded.Search,
                        contentDescription = stringResource(R.string.action_search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onSortClick, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Sort,
                        contentDescription = stringResource(R.string.action_sort),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onRefresh, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.library_refresh),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (searchExpanded) {
            SearchField(
                query = state.query,
                onQueryChange = onQueryChange,
                onClear = onClearQuery,
                focusRequester = searchFocusRequester,
                onSearch = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                },
            )
        }

        PrimaryTabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = {
                    selectedTab = 0
                    focusManager.clearFocus()
                    keyboardController?.hide()
                },
                text = { Text(stringResource(R.string.library_tab_all_songs)) },
            )
            Tab(
                selected = selectedTab == 1,
                onClick = {
                    selectedTab = 1
                    focusManager.clearFocus()
                    keyboardController?.hide()
                },
                text = { Text(stringResource(R.string.library_tab_recently_added)) },
            )
        }

        when {
            !state.permissionGranted && state.songs.isEmpty() -> {
                EmptyState(
                    icon = Icons.Rounded.LibraryMusic,
                    title = stringResource(R.string.permission_required_title),
                    body = stringResource(R.string.permission_required_body),
                    actionLabel = stringResource(R.string.action_grant),
                    onAction = onRequestPermission,
                )
                TidalTextButton(
                    onClick = onOpenAppSettings,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(stringResource(R.string.settings_permission_open_settings))
                }
            }

            state.status == LibraryStatus.LOADING -> {
                if (state.scanTotal > 0) {
                    DeterminateLoadingState(
                        label = stringResource(
                            R.string.library_scanning_progress,
                            state.scanned,
                            state.scanTotal,
                        ),
                        fraction = (state.scanned.toFloat() / state.scanTotal.toFloat())
                            .coerceIn(0f, 1f),
                    )
                } else {
                    LoadingState(
                        label = stringResource(R.string.library_scanning),
                        modifier = Modifier.padding(top = 32.dp),
                    )
                }
            }

            state.status == LibraryStatus.ERROR -> {
                ErrorState(
                    icon = Icons.Rounded.LibraryMusic,
                    title = stringResource(R.string.library_error_title),
                    body = state.errorMessage ?: stringResource(R.string.library_error_body),
                    retryLabel = stringResource(R.string.action_retry),
                    onRetry = onRefresh,
                )
            }

            songs.isEmpty() && state.isSearching -> {
                EmptyState(
                    icon = Icons.Rounded.LibraryMusic,
                    title = stringResource(R.string.library_search_empty_title),
                    body = stringResource(R.string.library_search_empty_body, state.query),
                    actionLabel = stringResource(R.string.library_clear_search),
                    onAction = onClearQuery,
                )
            }

            songs.isEmpty() -> {
                EmptyState(
                    icon = Icons.Rounded.LibraryMusic,
                    title = stringResource(R.string.library_empty_title),
                    body = stringResource(R.string.library_empty_body),
                    actionLabel = stringResource(R.string.library_refresh),
                    onAction = onRefresh,
                )
            }

            else -> {
                if (state.isRefreshing) {
                    InlineLoadingRow(label = stringResource(R.string.library_refreshing))
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = 4.dp,
                            bottom = 32.dp,
                            end = if (showLetterRail && !selectionMode) 28.dp else 0.dp,
                        ),
                    ) {
                        itemsIndexed(items = songs, key = { _, song -> song.id }) { index, song ->
                            val checked = song.id in selectedSongIds
                            SongRow(
                                modifier = Modifier.animateItem(),
                                song = song,
                                isCurrent = song.id == currentSongId,
                                isPlaying = isPlaying,
                                isFavorite = song.id in state.favorites,
                                showArtwork = true,
                                compact = false,
                                isSelected = checked,
                                selectionMode = selectionMode,
                                selectionCheckedLabel = stringResource(R.string.library_selected_item),
                                selectionUncheckedLabel = stringResource(R.string.library_not_selected_item),
                                onPlay = {
                                    if (selectionMode) {
                                        selectedSongIds = if (song.id in selectedSongIds) {
                                            selectedSongIds - song.id
                                        } else {
                                            selectedSongIds + song.id
                                        }
                                    } else {
                                        onPlay(songs, index)
                                    }
                                },
                                onLongClick = {
                                    if (!selectionMode) {
                                        selectionMode = true
                                    }
                                    selectedSongIds = selectedSongIds + song.id
                                },
                                onOverflow = { onSongOverflow(song) },
                                overflowLabel = stringResource(R.string.action_more_options),
                            )
                        }
                    }

                    // Fast-search rail: only the letters that actually exist in the list.
                    if (showLetterRail && !selectionMode) {
                        LetterRail(
                            letters = state.indexLetters,
                            activeLetter = activeLetter,
                            onLetterClick = ::scrollToLetter,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 2.dp),
                        )
                    }
                }
            }
        }
    }

        // The add-to-playlist flow reuses this screen: while a target playlist is set, an
        // explicit confirmation bar sits at the bottom of the library.
        addPlaylistName?.let { name ->
            AddToPlaylistBar(
                playlistName = name,
                selectedCount = selectedSongIds.size,
                onCancel = {
                    clearSelection()
                    onExitAddMode()
                },
                onConfirm = {
                    val selected = state.allSongs.values.filter { it.id in selectedSongIds }
                    onAddSelectedToPlaylist(selected)
                    clearSelection()
                    onExitAddMode()
                },
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.library_delete_selected_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.library_delete_selected_body,
                        selectedSongIds.size,
                    ),
                )
            },
            confirmButton = {
                TidalTextButton(
                    onClick = {
                        val selected = state.allSongs.values.filter { it.id in selectedSongIds }
                        confirmDelete = false
                        if (selected.isNotEmpty()) onRemoveSongs(selected)
                        clearSelection()
                    },
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TidalTextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    focusRequester: FocusRequester,
    onSearch: () -> Unit,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = Color.Black,
            fontWeight = FontWeight.Bold,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .focusRequester(focusRequester),
        placeholder = { Text(stringResource(R.string.library_search_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        shape = RoundedCornerShape(14.dp),
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Clear,
                        contentDescription = stringResource(R.string.library_clear_search),
                    )
                }
            }
        },
        colors = TextFieldDefaults.colors(
            focusedTextColor = Color.Black,
            unfocusedTextColor = Color.Black,
            cursorColor = Color.Black,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

/**
 * The sort dialog: pick the field, then ascending or descending.
 *
 * The direction button and the chosen field are applied together, so the result is always
 * deterministic.
 */
@Composable
fun SortDialog(
    current: SortSpec,
    onDismiss: () -> Unit,
    onSelect: (SortSpec) -> Unit,
) {
    var order by remember(current) { mutableStateOf(current.order) }
    var direction by remember(current) { mutableStateOf(current.direction) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_sort_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                SortOrder.entries.forEach { option ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        RadioButton(
                            selected = order == option,
                            onClick = {
                                order = option
                                // Date added is only useful newest-first, so the default
                                // direction follows the field the user picked.
                                if (option == SortOrder.DATE_ADDED) {
                                    direction = SortDirection.DESCENDING
                                }
                            },
                        )
                        Text(
                            text = stringResource(option.labelRes()),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TidalTextButton(
                    onClick = {
                        direction = if (direction == SortDirection.ASCENDING) {
                            SortDirection.DESCENDING
                        } else {
                            SortDirection.ASCENDING
                        }
                    },
                ) {
                    Text(
                        stringResource(
                            if (direction == SortDirection.ASCENDING) {
                                R.string.library_sort_ascending
                            } else {
                                R.string.library_sort_descending
                            },
                        ),
                    )
                }
                TidalTextButton(
                    onClick = {
                        onSelect(SortSpec(order, direction))
                        onDismiss()
                    },
                ) {
                    Text(stringResource(R.string.action_save))
                }
            }
        },
        dismissButton = {
            TidalTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

internal fun SortOrder.labelRes(): Int = when (this) {
    SortOrder.TITLE -> R.string.library_sort_title_label
    SortOrder.ARTIST -> R.string.library_sort_artist_label
    SortOrder.ALBUM -> R.string.library_sort_album_label
    SortOrder.DURATION -> R.string.library_sort_duration_label
    SortOrder.DATE_ADDED -> R.string.library_sort_date_added_label
}

/**
 * The fast-search letter rail shown on the right edge of the library list.
 *
 * It only contains the letters that actually exist in the current song list (precomputed by
 * the view model), so tapping any letter always jumps somewhere. A vertical drag scrubs
 * through the letters continuously, like the index bars in large system lists.
 */
@Composable
private fun LetterRail(
    letters: List<Char>,
    activeLetter: Char?,
    onLetterClick: (Char) -> Unit,
    modifier: Modifier = Modifier,
) {
    val railLabel = stringResource(R.string.library_letter_rail)
    Column(
        modifier = modifier
            .widthIn(min = 24.dp)
            .fillMaxHeight()
            .semantics { contentDescription = railLabel }
            .pointerInput(letters) {
                detectVerticalDragGestures { change, _ ->
                    val fraction = (change.position.y / size.height).coerceIn(0f, 1f)
                    val index = (fraction * letters.size).toInt()
                        .coerceIn(letters.indices)
                    onLetterClick(letters[index])
                    change.consume()
                }
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterEnd,
    ) {
        letters.forEach { letter ->
            val isActive = letter == activeLetter
            Text(
                text = letter.toString(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = if (isActive) 12.sp else 10.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                ),
                color = if (isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .padding(vertical = 1.dp, horizontal = 4.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onLetterClick(letter) },
            )
        }
    }
}

/**
 * Bottom confirmation bar for the "add songs to playlist" flow: shows the target playlist,
 * how many songs are checked and offers Cancel / Add actions above the navigation bar.
 */
@Composable
private fun AddToPlaylistBar(
    playlistName: String,
    selectedCount: Int,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlistName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.library_selected_count_short, selectedCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TidalTextButton(onClick = onCancel) {
                Text(stringResource(R.string.action_cancel))
            }
            FilledTonalButton(
                onClick = onConfirm,
                enabled = selectedCount > 0,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(stringResource(R.string.action_add))
            }
        }
    }
}

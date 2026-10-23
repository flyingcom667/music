package com.example.myapplication.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myapplication.domain.model.Song
import com.example.myapplication.domain.model.SongListLayout
import com.example.myapplication.domain.model.SongSortOption
import com.example.myapplication.ui.components.AlphabetIndexBar
import com.example.myapplication.ui.components.EmptyState
import com.example.myapplication.ui.components.SelectionActionBar
import com.example.myapplication.ui.components.SongRow
import com.example.myapplication.ui.components.SongSelectionActions
import com.example.myapplication.ui.components.alphabetKeyOf
import com.example.myapplication.ui.viewmodel.LibraryViewModel
import com.example.myapplication.util.Formatters
import kotlinx.coroutines.launch

/** The two primary tabs of the library. */
private enum class LibraryTab(val label: String) {
    ALL_SONGS("All songs"),
    RECENTLY_ADDED("Recently added"),
}

/**
 * Remembers whether the header of the list should be shown.
 *
 * The header slides away while the list is scrolled down and comes back when the user scrolls
 * up, so the songs get the whole screen while browsing.
 */
@Composable
private fun rememberHeaderVisible(listState: LazyListState): State<Boolean> {
    val visible = remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        var lastIndex = 0
        var lastOffset = 0
        snapshotFlow {
            val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()
            (first?.index ?: 0) to (first?.offset ?: 0)
        }.collect { (index, offset) ->
            if (index != lastIndex) {
                // A different first item means the list was scrolled.
                visible.value = index < lastIndex
            } else if (offset != lastOffset) {
                // The first item moved: a negative offset means the list is scrolled down.
                visible.value = offset > lastOffset
            }
            lastIndex = index
            lastOffset = offset
        }
    }
    return visible
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: LibraryViewModel.UiState,
    nowPlayingId: Long,
    isPlaying: Boolean,
    onSongClick: (Song) -> Unit,
    onSongMore: (Song) -> Unit,
    onSortChange: (SongSortOption) -> Unit,
    onLayoutToggle: () -> Unit,
    onFolderFilter: (String?) -> Unit,
    onQueryChange: (String) -> Unit,
    onPlaySongs: (List<Song>) -> Unit,
    selection: SongSelectionActions = SongSelectionActions(),
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    var sortExpanded by remember { mutableStateOf(false) }
    var folderExpanded by remember { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableIntStateOf(LibraryTab.ALL_SONGS.ordinal) }

    // The search field and the tabs make way for the songs while the list is scrolled down.
    val showHeader by rememberHeaderVisible(listState)

    val isSelecting = selection.isActive
    val songs = if (LibraryTab.entries[selectedTab] == LibraryTab.RECENTLY_ADDED) {
        state.recentlyAdded
    } else {
        state.visibleSongs
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (isSelecting) {
            SelectionActionBar(
                selectedCount = selection.count,
                onClear = selection.onClear,
                onAddToPlaylist = selection.onAddToPlaylist,
                onAddToQueue = selection.onAddToQueue,
                onShare = selection.onShare,
                onDeleteFiles = selection.onDeleteFiles,
                onRemoveFromLibrary = selection.onRemoveFromLibrary,
                onSelectAll = selection.onSelectAll,
            )
        } else {
            TopAppBar(
                title = {
                    Column {
                        Text("Library", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = Formatters.songCount(songs.size) +
                                (state.folderFilter?.let { " • $it" } ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { sortExpanded = true }) {
                            Icon(Icons.Rounded.Sort, contentDescription = "Sort")
                        }
                        DropdownMenu(
                            expanded = sortExpanded,
                            onDismissRequest = { sortExpanded = false },
                        ) {
                            SongSortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label()) },
                                    trailingIcon = {
                                        if (option == state.sortOption) {
                                            Text("✓", color = MaterialTheme.colorScheme.primary)
                                        }
                                    },
                                    onClick = {
                                        sortExpanded = false
                                        onSortChange(option)
                                    },
                                )
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { folderExpanded = true }) {
                            Icon(Icons.Rounded.Folder, contentDescription = "Filter by folder")
                        }
                        DropdownMenu(
                            expanded = folderExpanded,
                            onDismissRequest = { folderExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("All folders") },
                                onClick = {
                                    folderExpanded = false
                                    onFolderFilter(null)
                                },
                            )
                            state.folders.forEach { folder ->
                                DropdownMenuItem(
                                    text = { Text(folder) },
                                    onClick = {
                                        folderExpanded = false
                                        onFolderFilter(folder)
                                    },
                                )
                            }
                        }
                    }
                    IconButton(onClick = onLayoutToggle) {
                        Icon(Icons.Rounded.ViewAgenda, contentDescription = "Change layout")
                    }
                },
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

        // The search field and the tabs are hidden while songs are selected so the selection
        // bar is the only header of the screen. They also slide away while the list is
        // scrolled down, and come back when the user scrolls up.
        if (!isSelecting) {
            AnimatedVisibility(
                visible = showHeader,
                enter = expandVertically(animationSpec = tween(durationMillis = 180)),
                exit = shrinkVertically(animationSpec = tween(durationMillis = 180)),
            ) {
                Column {
                    LibrarySearchField(query = state.query, onQueryChange = onQueryChange)
                    PrimaryTabRow(selectedTabIndex = selectedTab) {
                        LibraryTab.entries.forEachIndexed { index, tab ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = { Text(tab.label) },
                            )
                        }
                    }
                }
            }
        }

        when (LibraryTab.entries[selectedTab]) {
            LibraryTab.ALL_SONGS -> AllSongsTab(
                songs = state.visibleSongs,
                state = state,
                nowPlayingId = nowPlayingId,
                isPlaying = isPlaying,
                selection = selection,
                onSongClick = onSongClick,
                onSongMore = onSongMore,
                listState = listState,
            )

            LibraryTab.RECENTLY_ADDED -> RecentlyAddedTab(
                songs = state.recentlyAdded,
                state = state,
                nowPlayingId = nowPlayingId,
                isPlaying = isPlaying,
                selection = selection,
                onSongClick = onSongClick,
                onSongMore = onSongMore,
                listState = listState,
            )
        }
    }
}

/** Every track of the device, with the A-Z index bar on the right. */
@Composable
private fun AllSongsTab(
    songs: List<Song>,
    state: LibraryViewModel.UiState,
    nowPlayingId: Long,
    isPlaying: Boolean,
    selection: SongSelectionActions,
    onSongClick: (Song) -> Unit,
    onSongMore: (Song) -> Unit,
    listState: LazyListState,
) {
    if (songs.isEmpty()) {
        LibraryEmptyState(isLoading = state.isLoading, isSearching = state.isSearching)
        return
    }

    val letterTargets = remember(songs) {
        val targets = LinkedHashMap<Char, Int>()
        songs.forEachIndexed { index, song ->
            val letter = alphabetKeyOf(song.title)
            if (letter !in targets) targets[letter] = index
        }
        targets
    }
    val letters = remember(letterTargets) {
        letterTargets.keys.sortedWith(compareBy({ it != '#' }, { it }))
    }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            itemsIndexed(items = songs, key = { _, song -> "song-${song.mediaId}" }) { index, song ->
                LibrarySongRow(
                    song = song,
                    index = index,
                    hasTrailingIndexBar = true,
                    showDivider = index < songs.lastIndex,
                    state = state,
                    nowPlayingId = nowPlayingId,
                    isPlaying = isPlaying,
                    selection = selection,
                    onSongClick = onSongClick,
                    onSongMore = onSongMore,
                )
            }
        }
        AlphabetIndexBar(
            letters = letters,
            onLetterSelected = { letter ->
                letterTargets[letter]?.let { target ->
                    scope.launch { listState.scrollToItem(target) }
                }
            },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 2.dp, top = 24.dp, bottom = 24.dp),
        )
    }
}

/** The newest additions to the device, newest first. */
@Composable
private fun RecentlyAddedTab(
    songs: List<Song>,
    state: LibraryViewModel.UiState,
    nowPlayingId: Long,
    isPlaying: Boolean,
    selection: SongSelectionActions,
    onSongClick: (Song) -> Unit,
    onSongMore: (Song) -> Unit,
    listState: LazyListState,
) {
    if (songs.isEmpty()) {
        EmptyState(
            title = if (state.isLoading) "Scanning…" else "No recent additions",
            description = if (state.isLoading) {
                "Looking for audio files on this device."
            } else {
                "Files you copy to this device show up here, newest first."
            },
        )
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        itemsIndexed(items = songs, key = { _, song -> "recent-${song.mediaId}" }) { index, song ->
            LibrarySongRow(
                song = song,
                index = index,
                hasTrailingIndexBar = false,
                showDivider = index < songs.lastIndex,
                state = state,
                nowPlayingId = nowPlayingId,
                isPlaying = isPlaying,
                selection = selection,
                onSongClick = onSongClick,
                onSongMore = onSongMore,
            )
        }
    }
}

@Composable
private fun LibraryEmptyState(isLoading: Boolean, isSearching: Boolean) {
    EmptyState(
        title = if (isLoading) "Scanning…" else "Nothing to show",
        description = when {
            isLoading -> "Looking for audio files on this device."
            isSearching -> "No songs match your search."
            else -> "No songs match the current filter."
        },
    )
}

/** One row of either library list. */
@Composable
private fun LibrarySongRow(
    song: Song,
    index: Int,
    hasTrailingIndexBar: Boolean,
    showDivider: Boolean,
    state: LibraryViewModel.UiState,
    nowPlayingId: Long,
    isPlaying: Boolean,
    selection: SongSelectionActions,
    onSongClick: (Song) -> Unit,
    onSongMore: (Song) -> Unit,
) {
    val isSelecting = selection.isActive
    SongRow(
        song = song,
        index = index,
        layout = state.layout,
        showArtwork = state.layout != SongListLayout.COMPACT,
        isPlaying = song.mediaId == nowPlayingId && isPlaying,
        selectionMode = isSelecting,
        selected = song.mediaId in selection.selectedIds,
        onToggleSelected = { selection.onToggle(song.mediaId) },
        onClick = {
            if (isSelecting) selection.onToggle(song.mediaId) else onSongClick(song)
        },
        onLongClick = {
            if (isSelecting) selection.onToggle(song.mediaId) else selection.onStart(song.mediaId)
        },
        onMoreClick = { onSongMore(song) },
        showDivider = showDivider,
        modifier = Modifier.padding(end = if (hasTrailingIndexBar) 22.dp else 8.dp),
    )
}

internal fun SongSortOption.label(): String = when (this) {
    SongSortOption.NAME_ASC -> "Title (A-Z)"
    SongSortOption.NAME_DESC -> "Title (Z-A)"
    SongSortOption.ARTIST -> "Artist"
    SongSortOption.ALBUM -> "Album"
    SongSortOption.RECENTLY_ADDED -> "Recently added"
    SongSortOption.OLDEST_ADDED -> "Oldest first"
    SongSortOption.RECENTLY_PLAYED -> "Recently played"
    SongSortOption.SHORTEST -> "Shortest first"
    SongSortOption.LONGEST -> "Longest first"
}

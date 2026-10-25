package com.tidalmusic.player.ui.folders

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tidalmusic.player.R
import com.tidalmusic.player.data.preferences.ViewMode
import com.tidalmusic.player.domain.model.Song
import com.tidalmusic.player.ui.components.TidalAlertDialog
import com.tidalmusic.player.ui.components.EmptyState
import com.tidalmusic.player.ui.components.SongRow
import com.tidalmusic.player.ui.components.TidalTextButton
import com.tidalmusic.player.util.TimeFormat

/** One directory that holds songs, with the figures the folder card displays. */
data class FolderInfo(
    val path: String,
    val songs: List<Song>,
) {
    val songCount: Int get() = songs.size
    val totalBytes: Long get() = songs.sumOf { it.sizeBytes }
    val displayName: String get() = path.substringAfterLast('/').ifBlank { "/" }
}

/**
 * The built-in mini file manager: every folder that contains songs, with its track count
 * and size.
 *
 * Opening a folder lists its songs with the same actions as the library. Long-pressing a
 * folder enters selection mode, where several folders can be shared or removed at once.
 * The view mode (list or cover grid) is remembered separately from the other screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersScreen(
    songs: List<Song>,
    currentSongId: Long?,
    isPlaying: Boolean,
    viewMode: ViewMode,
    onViewModeChange: (ViewMode) -> Unit,
    onBack: () -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onShareSongs: (List<Song>) -> Unit,
    onDeleteSongs: (List<Song>) -> Unit,
    onSongOverflow: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    val folders = remember(songs) {
        songs
            .groupBy { it.relativePath }
            .map { (path, folderSongs) -> FolderInfo(path = path, songs = folderSongs.sortedBy { it.title }) }
            .sortedWith(compareBy<FolderInfo> { it.path.isEmpty() }.thenBy { it.displayName.lowercase() })
    }

    var openFolder by rememberSaveable { mutableStateOf<String?>(null) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedPaths by rememberSaveable(
        stateSaver = androidx.compose.runtime.saveable.listSaver(
            save = { it.toList() },
            restore = { it.toSet() },
        ),
    ) { mutableStateOf(emptySet<String>()) }
    var menuFolderPath by remember { mutableStateOf<String?>(null) }
    var infoFolder by remember { mutableStateOf<FolderInfo?>(null) }

    val openFolderInfo = openFolder?.let { path -> folders.firstOrNull { it.path == path } }

    fun exitSelection() {
        selectionMode = false
        selectedPaths = emptySet()
    }

    fun songsOfSelected(): List<Song> =
        folders.filter { it.path in selectedPaths }.flatMap { it.songs }

    BackHandler(enabled = openFolderInfo != null || selectionMode) {
        when {
            selectionMode -> exitSelection()
            else -> openFolder = null
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (openFolderInfo != null) {
                            openFolderInfo.displayName
                        } else {
                            stringResource(R.string.folders_title)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        when {
                            selectionMode -> exitSelection()
                            openFolder != null -> openFolder = null
                            else -> onBack()
                        }
                    }, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (openFolderInfo == null) {
                        IconButton(
                            onClick = {
                                onViewModeChange(
                                    if (viewMode == ViewMode.LIST) ViewMode.COVER else ViewMode.LIST,
                                )
                            },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = if (viewMode == ViewMode.LIST) {
                                    Icons.Rounded.GridView
                                } else {
                                    Icons.Rounded.ViewList
                                },
                                contentDescription = stringResource(
                                    if (viewMode == ViewMode.LIST) {
                                        R.string.view_mode_cover
                                    } else {
                                        R.string.view_mode_list
                                    },
                                ),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { padding ->
        when {
            folders.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                EmptyState(
                    icon = Icons.Rounded.CreateNewFolder,
                    title = stringResource(R.string.folders_empty_title),
                    body = stringResource(R.string.folders_empty_body),
                )
            }

            openFolderInfo != null -> FolderSongs(
                folder = openFolderInfo,
                currentSongId = currentSongId,
                isPlaying = isPlaying,
                contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onPlaySongs = { index -> onPlaySongs(openFolderInfo.songs, index) },
                onSongOverflow = onSongOverflow,
            )

            else -> Column(modifier = Modifier.fillMaxSize()) {
                if (viewMode == ViewMode.COVER) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        gridItems(items = folders, key = { it.path }) { folder ->
                            FolderGridCell(
                                folder = folder,
                                isSelected = folder.path in selectedPaths,
                                selectionMode = selectionMode,
                                onClick = { openFolder = folder.path },
                                onLongClick = {
                                    selectionMode = true
                                    selectedPaths = selectedPaths + folder.path
                                },
                                onToggleSelect = {
                                    selectedPaths = if (folder.path in selectedPaths) {
                                        selectedPaths - folder.path
                                    } else {
                                        selectedPaths + folder.path
                                    }
                                },
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
                    ) {
                        items(items = folders, key = { it.path }) { folder ->
                            FolderRow(
                                folder = folder,
                                isSelected = folder.path in selectedPaths,
                                selectionMode = selectionMode,
                                menuOpen = menuFolderPath == folder.path,
                                onMenuOpen = { menuFolderPath = folder.path },
                                onMenuClose = { menuFolderPath = null },
                                onClick = { openFolder = folder.path },
                                onLongClick = {
                                    selectionMode = true
                                    selectedPaths = selectedPaths + folder.path
                                },
                                onToggleSelect = {
                                    selectedPaths = if (folder.path in selectedPaths) {
                                        selectedPaths - folder.path
                                    } else {
                                        selectedPaths + folder.path
                                    }
                                },
                                onShare = { onShareSongs(folder.songs) },
                                onRemove = { onDeleteSongs(folder.songs) },
                                onInfo = { infoFolder = folder },
                            )
                        }
                    }
                }

                if (selectionMode) {
                    FolderSelectionBar(
                        selectedCount = selectedPaths.size,
                        totalBytes = songsOfSelected().sumOf { it.sizeBytes },
                        onCancel = { exitSelection() },
                        onShare = { onShareSongs(songsOfSelected()) },
                        onRemove = {
                            onDeleteSongs(songsOfSelected())
                            exitSelection()
                        },
                    )
                }
            }
        }
    }

    infoFolder?.let { folder ->
        TidalAlertDialog(
            onDismissRequest = { infoFolder = null },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            title = { Text(stringResource(R.string.folder_info_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoRow(stringResource(R.string.folder_info_name), folder.displayName)
                    InfoRow(stringResource(R.string.folder_info_path), folder.path.ifBlank { "/" })
                    InfoRow(
                        stringResource(R.string.folder_info_count),
                        folder.songCount.toString(),
                    )
                    InfoRow(
                        stringResource(R.string.folder_info_size),
                        TimeFormat.fileSize(folder.totalBytes),
                    )
                }
            },
            confirmButton = {
                TidalTextButton(onClick = { infoFolder = null }) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value.ifBlank { stringResource(R.string.details_unknown) },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** One folder card in the list. */
@Composable
private fun FolderRow(
    folder: FolderInfo,
    isSelected: Boolean,
    selectionMode: Boolean,
    menuOpen: Boolean,
    onMenuOpen: () -> Unit,
    onMenuClose: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleSelect: () -> Unit,
    onShare: () -> Unit,
    onRemove: () -> Unit,
    onInfo: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.background
                },
            )
            .combinedClickable(onClick = { if (selectionMode) onToggleSelect() else onClick() },
                onLongClick = { if (selectionMode) onToggleSelect() else onLongClick() })
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (selectionMode) {
            Icon(
                imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(24.dp),
            )
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(stringResource(R.string.folders_song_count, folder.songCount))
                    if (folder.totalBytes > 0L) {
                        append(" · ")
                        append(TimeFormat.fileSize(folder.totalBytes))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton(onClick = onMenuOpen, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.action_more_options),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = onMenuClose) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_share)) },
                    leadingIcon = { Icon(Icons.Rounded.Share, null) },
                    onClick = {
                        onMenuClose()
                        onShare()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.folder_info_menu)) },
                    leadingIcon = { Icon(Icons.Rounded.Info, null) },
                    onClick = {
                        onMenuClose()
                        onInfo()
                    },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.library_delete_selected),
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.RemoveCircleOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = {
                        onMenuClose()
                        onRemove()
                    },
                )
            }
        }
    }
}

@Composable
private fun FolderGridCell(
    folder: FolderInfo,
    isSelected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleSelect: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (selectionMode) onToggleSelect() else onClick() },
                onLongClick = { if (selectionMode) onToggleSelect() else onLongClick() },
            ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
            )
            if (selectionMode) {
                Icon(
                    imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(22.dp),
                )
            }
        }
        Text(
            text = folder.displayName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(R.string.folders_song_count, folder.songCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** The songs inside one folder. */
@Composable
private fun FolderSongs(
    folder: FolderInfo,
    currentSongId: Long?,
    isPlaying: Boolean,
    contentPadding: PaddingValues,
    onPlaySongs: (Int) -> Unit,
    onSongOverflow: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
    ) {
        items(items = folder.songs, key = { it.id }) { song ->
            SongRow(
                song = song,
                isCurrent = song.id == currentSongId,
                isPlaying = isPlaying,
                isFavorite = false,
                showArtwork = true,
                compact = false,
                onPlay = { onPlaySongs(folder.songs.indexOf(song)) },
                onOverflow = { onSongOverflow(song) },
                overflowLabel = stringResource(R.string.action_more_options),
            )
        }
    }
}

/** Bottom confirmation bar for multi-folder actions. */
@Composable
private fun FolderSelectionBar(
    selectedCount: Int,
    totalBytes: Long,
    onCancel: () -> Unit,
    onShare: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        modifier = Modifier
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.folders_selected_count, selectedCount),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = TimeFormat.fileSize(totalBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TidalTextButton(onClick = onCancel) {
                Text(stringResource(R.string.action_cancel))
            }
            TidalTextButton(onClick = onShare) {
                Text(stringResource(R.string.action_share))
            }
            TidalTextButton(onClick = onRemove) {
                Text(
                    text = stringResource(R.string.library_delete_selected),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

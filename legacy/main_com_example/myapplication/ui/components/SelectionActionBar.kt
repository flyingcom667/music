package com.example.myapplication.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

/**
 * Everything a list of songs needs to offer the multi selection.
 *
 * The selection itself lives in the ViewModel, the screen only forwards the actions.
 */
data class SongSelectionActions(
    val selectedIds: Set<Long> = emptySet(),
    /** Long press on a row: the selection starts with that row. */
    val onStart: (Long) -> Unit = {},
    /** The checkbox of a row was toggled. */
    val onToggle: (Long) -> Unit = {},
    val onClear: () -> Unit = {},
    val onSelectAll: () -> Unit = {},
    val onAddToPlaylist: () -> Unit = {},
    val onAddToQueue: () -> Unit = {},
    val onShare: () -> Unit = {},
    /** Deletes the files of the selected songs from the device. */
    val onDeleteFiles: () -> Unit = {},
    /** Only hides the selected songs in the app, the files stay on the device. */
    val onRemoveFromLibrary: () -> Unit = {},
    /** An extra action of the screen the selection belongs to. */
    val extraAction: (@Composable () -> Unit)? = null,
) {
    val isActive: Boolean get() = selectedIds.isNotEmpty()
    val count: Int get() = selectedIds.size
}

/**
 * Top bar of the multi selection: it replaces the regular top bar while songs are selected.
 *
 * The selected songs can be added to a playlist or to the queue, shared, deleted from the
 * device or only hidden from the library.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionActionBar(
    selectedCount: Int,
    onClear: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onAddToQueue: () -> Unit,
    onShare: () -> Unit,
    onDeleteFiles: () -> Unit,
    onRemoveFromLibrary: () -> Unit,
    onSelectAll: () -> Unit,
    modifier: Modifier = Modifier,
    extraAction: (@Composable () -> Unit)? = null,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    TopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = if (selectedCount == 1) "1 song selected" else "$selectedCount songs selected",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        navigationIcon = {
            IconButton(onClick = onClear) {
                Icon(Icons.Rounded.Close, contentDescription = "Cancel selection")
            }
        },
        actions = {
            IconButton(onClick = onAddToPlaylist) {
                Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = "Add to playlist")
            }
            IconButton(onClick = onAddToQueue) {
                Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = "Add to queue")
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Rounded.Share, contentDescription = "Share")
            }
            IconButton(onClick = onDeleteFiles) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete files",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
            extraAction?.invoke()
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Select all") },
                        onClick = {
                            menuExpanded = false
                            onSelectAll()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Remove from library") },
                        onClick = {
                            menuExpanded = false
                            onRemoveFromLibrary()
                        },
                    )
                }
            }
        },
    )
}

package com.example.myapplication.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.domain.model.Song
import com.example.myapplication.ui.components.EmptyState
import com.example.myapplication.ui.components.SectionHeader
import com.example.myapplication.ui.components.SongRow
import com.example.myapplication.ui.viewmodel.LibraryViewModel
import com.example.myapplication.util.Formatters

private const val SECTION_PLAYED = "played"

/**
 * Everything the home list can render.
 *
 * The home screen only shows the quick actions and the recently played songs — the full song
 * list and the recently added songs live in the library page.
 */
private sealed interface HomeRow {
    val key: String

    data object Actions : HomeRow {
        override val key = "actions"
    }

    data object Empty : HomeRow {
        override val key = "empty"
    }

    data class Header(
        val title: String,
        val actionLabel: String? = null,
        val playable: List<Song>? = null,
    ) : HomeRow {
        override val key = "header-$title"
    }

    data class Track(
        val song: Song,
        val section: String,
    ) : HomeRow {
        override val key = "track-$section-${song.mediaId}"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: LibraryViewModel.UiState,
    nowPlayingId: Long,
    isPlaying: Boolean,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onScan: () -> Unit,
    onSongClick: (Song) -> Unit,
    onSongMore: (Song) -> Unit,
    onShuffleAll: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSettings: () -> Unit,
    onPlaySongs: (List<Song>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = remember(
        state.isEmpty,
        state.recentlyPlayed,
    ) {
        buildList {
            add(HomeRow.Actions)
            if (state.isEmpty) {
                add(HomeRow.Empty)
            } else if (state.recentlyPlayed.isNotEmpty()) {
                add(
                    HomeRow.Header(
                        title = "Recently played",
                        actionLabel = "See all",
                        playable = state.recentlyPlayed,
                    ),
                )
                state.recentlyPlayed.forEach { add(HomeRow.Track(it, SECTION_PLAYED)) }
            }
        }
    }

    val listState = rememberLazyListState()

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Audiofy",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = Formatters.songCount(state.songs.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = onScan) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Rescan library")
                    }
                },
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(
                    count = rows.size,
                    key = { index -> rows[index].key },
                ) { index ->
                    when (val row = rows[index]) {
                        HomeRow.Actions -> Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            QuickActionCard(
                                icon = { Icon(Icons.Rounded.Shuffle, contentDescription = null) },
                                title = "Shuffle all",
                                subtitle = Formatters.songCount(state.songs.size),
                                onClick = onShuffleAll,
                                modifier = Modifier.weight(1f),
                            )
                            QuickActionCard(
                                icon = {
                                    Icon(
                                        Icons.Rounded.Favorite,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                    )
                                },
                                title = "Favourites",
                                subtitle = Formatters.songCount(state.favorites.size),
                                onClick = onOpenFavorites,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        HomeRow.Empty -> if (hasPermission) {
                            EmptyState(
                                title = "No music found",
                                description = "Nothing in your library yet. Add some audio files and rescan.",
                                action = {
                                    Button(onClick = onScan) { Text("Scan library") }
                                },
                            )
                        } else {
                            EmptyState(
                                title = "Access your music",
                                description = "Audiofy needs permission to read the audio files on this device.",
                                action = {
                                    Button(onClick = onRequestPermission) { Text("Allow access") }
                                },
                            )
                        }

                        is HomeRow.Header -> SectionHeader(
                            title = row.title,
                            actionLabel = row.actionLabel,
                            onAction = if (row.actionLabel != null) onOpenLibrary else null,
                            onPlay = if (row.playable != null) {
                                { onPlaySongs(row.playable) }
                            } else {
                                null
                            },
                            modifier = Modifier.padding(
                                start = 16.dp,
                                end = 16.dp,
                                top = if (row.title == "Recently played") 16.dp else 8.dp,
                                bottom = 4.dp,
                            ),
                        )

                        is HomeRow.Track -> SongRow(
                            song = row.song,
                            onClick = { onSongClick(row.song) },
                            onLongClick = { onSongMore(row.song) },
                            onMoreClick = { onSongMore(row.song) },
                            isPlaying = row.song.mediaId == nowPlayingId && isPlaying,
                            showDivider = false,
                            modifier = Modifier.padding(start = 4.dp, end = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(onClick = onClick, modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier.size(28.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                icon()
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

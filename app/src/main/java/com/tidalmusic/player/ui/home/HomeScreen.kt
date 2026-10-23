package com.tidalmusic.player.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tidalmusic.player.R
import com.tidalmusic.player.domain.model.Song
import com.tidalmusic.player.ui.components.AlbumArtwork
import com.tidalmusic.player.ui.components.EmptyState
import com.tidalmusic.player.ui.components.NowPlayingBars
import com.tidalmusic.player.ui.components.SectionHeader

/**
 * The Home screen: identity, a one-line library summary and the recently played music.
 *
 * Only playback history is listed here; library browsing is kept on the Library screen.
 */
@Composable
fun HomeScreen(
    recentSongs: List<Song>,
    favoriteSongs: List<Song>,
    librarySummary: String,
    currentSongId: Long?,
    isPlaying: Boolean,
    hasPermission: Boolean,
    onPlayRecent: (Song) -> Unit,
    onPlayFavorite: (Song) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onBrowseLibrary: () -> Unit,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item(key = "header") {
            HomeHeader(
                librarySummary = librarySummary,
                onOpenSearch = onOpenSearch,
                onOpenSettings = onOpenSettings,
            )
        }

        if (!hasPermission) {
            item(key = "permission") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            RoundedCornerShape(16.dp),
                        )
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.permission_required_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.permission_required_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onRequestPermission) {
                        Text(stringResource(R.string.home_storage_permission))
                    }
                }
            }
        }

        if (recentSongs.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    icon = Icons.Rounded.MusicNote,
                    title = stringResource(R.string.home_recently_played_empty_title),
                    body = stringResource(R.string.home_recently_played_empty_body),
                    actionLabel = if (!hasPermission) {
                        stringResource(R.string.home_storage_permission)
                    } else {
                        stringResource(R.string.nav_library)
                    },
                    onAction = if (!hasPermission) onRequestPermission else onBrowseLibrary,
                )
            }
        } else {
            item(key = "recent-header") {
                SectionHeader(title = stringResource(R.string.home_recently_played))
            }
            item(key = "carousel") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    itemsIndexed(
                        items = recentSongs.take(12),
                        key = { _, song -> song.id },
                    ) { _, song ->
                        RecentArtworkCard(
                            song = song,
                            isCurrent = song.id == currentSongId && isPlaying,
                            onClick = { onPlayRecent(song) },
                        )
                    }
                }
            }
        }

        if (favoriteSongs.isNotEmpty()) {
            item(key = "favorite-header") {
                SectionHeader(title = stringResource(R.string.home_favorite_songs))
            }
            item(key = "favorites") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    itemsIndexed(
                        items = favoriteSongs.take(12),
                        key = { _, song -> song.id },
                    ) { _, song ->
                        RecentArtworkCard(
                            song = song,
                            isCurrent = song.id == currentSongId && isPlaying,
                            onClick = { onPlayFavorite(song) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(
    librarySummary: String,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.home_your_music),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = librarySummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onOpenSearch, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = stringResource(R.string.action_search),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onOpenSettings, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = stringResource(R.string.action_settings),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecentArtworkCard(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(148.dp)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box {
            AlbumArtwork(
                song = song,
                size = 148.dp,
                cornerRadius = 16.dp,
            )
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                ) {
                    NowPlayingBars()
                }
            }
        }
        Text(
            text = song.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = song.artist.ifBlank { stringResource(R.string.player_unknown_artist) },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

package com.tidalmusic.player.ui.collection

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.tidalmusic.player.domain.model.Song
import com.tidalmusic.player.ui.components.AlbumArtwork
import com.tidalmusic.player.ui.components.EmptyState
import com.tidalmusic.player.ui.components.SongRow
import com.tidalmusic.player.ui.playlists.formatLongDuration

/** One album as shown in the Albums browser: the songs that share an album id. */
data class AlbumInfo(val albumId: Long, val name: String, val songs: List<Song>) {
    val songCount: Int get() = songs.size
    val totalDurationMs: Long get() = songs.sumOf { it.durationMs }
    val artist: String get() = songs.firstOrNull()?.artist.orEmpty()
}

/** One artist as shown in the Artists browser: the songs that share an artist name. */
data class ArtistInfo(val name: String, val songs: List<Song>) {
    val songCount: Int get() = songs.size
}

/**
 * The Albums browser. Tapping an album opens its songs in place; every song row carries
 * the same actions (play, share, hide, delete) as the library through [onSongOverflow].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    songs: List<Song>,
    currentSongId: Long?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onSongOverflow: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    val albums = remember(songs) {
        songs
            .filter { it.album.isNotBlank() }
            .groupBy { it.albumId }
            .map { (_, albumSongs) ->
                AlbumInfo(
                    albumId = albumSongs.first().albumId,
                    name = albumSongs.first().album,
                    songs = albumSongs.sortedWith(
                        compareBy({ it.trackNumber.takeIf { t -> t > 0 } ?: Int.MAX_VALUE }, { it.title }),
                    ),
                )
            }
            .sortedBy { it.name.lowercase() }
    }
    var openAlbumId by rememberSaveable { mutableStateOf<Long?>(null) }
    val openAlbum = openAlbumId?.let { id -> albums.firstOrNull { it.albumId == id } }

    BackHandler(enabled = openAlbum != null) { openAlbumId = null }

    CollectionScaffold(
        title = if (openAlbum != null) openAlbum.name else stringResource(R.string.albums_title),
        onBack = { if (openAlbum != null) openAlbumId = null else onBack() },
        modifier = modifier,
    ) { padding ->
        when {
            albums.isEmpty() -> EmptyState(
                icon = Icons.Rounded.Album,
                title = stringResource(R.string.albums_empty_title),
                body = stringResource(R.string.albums_empty_body),
                modifier = Modifier.padding(padding),
            )

            openAlbum != null -> SongList(
                songs = openAlbum.songs,
                currentSongId = currentSongId,
                isPlaying = isPlaying,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp),
                onPlaySongs = { index -> onPlaySongs(openAlbum.songs, index) },
                onSongOverflow = onSongOverflow,
            )

            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                gridItems(items = albums, key = { it.albumId }) { album ->
                    AlbumCard(album = album, onClick = { openAlbumId = album.albumId })
                }
            }
        }
    }
}

@Composable
private fun AlbumCard(album: AlbumInfo, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            AlbumArtwork(
                song = album.songs.firstOrNull(),
                size = 160.dp,
                cornerRadius = 14.dp,
                fillWidth = true,
            )
        }
        Text(
            text = album.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = buildString {
                append(stringResource(R.string.folders_song_count, album.songCount))
                if (album.totalDurationMs > 0L) {
                    append(" · ")
                    append(formatLongDuration(album.totalDurationMs))
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The Artists browser: one row per artist name; tapping lists that artist's songs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistsScreen(
    songs: List<Song>,
    currentSongId: Long?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onSongOverflow: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    val artists = remember(songs) {
        songs
            .filter { it.artist.isNotBlank() }
            .groupBy { it.artist.lowercase() }
            .map { (_, artistSongs) -> ArtistInfo(name = artistSongs.first().artist, songs = artistSongs.sortedBy { it.album }) }
            .sortedBy { it.name.lowercase() }
    }
    var openArtistKey by rememberSaveable { mutableStateOf<String?>(null) }
    val openArtist = openArtistKey?.let { key -> artists.firstOrNull { it.name.lowercase() == key } }

    BackHandler(enabled = openArtist != null) { openArtistKey = null }

    CollectionScaffold(
        title = if (openArtist != null) openArtist.name else stringResource(R.string.artists_title),
        onBack = { if (openArtist != null) openArtistKey = null else onBack() },
        modifier = modifier,
    ) { padding ->
        when {
            artists.isEmpty() -> EmptyState(
                icon = Icons.Rounded.Person,
                title = stringResource(R.string.artists_empty_title),
                body = stringResource(R.string.artists_empty_body),
                modifier = Modifier.padding(padding),
            )

            openArtist != null -> SongList(
                songs = openArtist.songs,
                currentSongId = currentSongId,
                isPlaying = isPlaying,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp),
                onPlaySongs = { index -> onPlaySongs(openArtist.songs, index) },
                onSongOverflow = onSongOverflow,
            )

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp),
            ) {
                items(items = artists, key = { it.name.lowercase() }) { artist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openArtistKey = artist.name.lowercase() }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = artist.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = stringResource(R.string.folders_song_count, artist.songCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The Recordings browser: every non-music audio the scan found — voice notes, chat-app
 * voice messages, recordings — when the include-non-music setting is enabled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingsScreen(
    songs: List<Song>,
    currentSongId: Long?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onSongOverflow: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    val recordings = remember(songs) {
        songs.filterNot { it.isMusic }.sortedByDescending { it.dateAdded }
    }
    CollectionScaffold(
        title = stringResource(R.string.recordings_title),
        onBack = onBack,
        modifier = modifier,
    ) { padding ->
        if (recordings.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.GraphicEq,
                title = stringResource(R.string.recordings_empty_title),
                body = stringResource(R.string.recordings_empty_body),
                modifier = Modifier.padding(padding),
            )
        } else {
            SongList(
                songs = recordings,
                currentSongId = currentSongId,
                isPlaying = isPlaying,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp),
                onPlaySongs = { index -> onPlaySongs(recordings, index) },
                onSongOverflow = onSongOverflow,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectionScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) { content(padding) }
    }
}

/** The shared song list used by the collection detail views. */
@Composable
private fun SongList(
    songs: List<Song>,
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
        items(items = songs, key = { it.id }) { song ->
            SongRow(
                song = song,
                isCurrent = song.id == currentSongId,
                isPlaying = isPlaying,
                isFavorite = false,
                showArtwork = true,
                compact = false,
                onPlay = { onPlaySongs(songs.indexOf(song)) },
                onOverflow = { onSongOverflow(song) },
                overflowLabel = stringResource(R.string.action_more_options),
            )
        }
    }
}

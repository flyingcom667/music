package com.tidalmusic.player.testing

import com.tidalmusic.player.data.preferences.AppPreferences
import com.tidalmusic.player.data.preferences.ListDensity
import com.tidalmusic.player.data.preferences.PlaybackSessionStore
import com.tidalmusic.player.data.preferences.SettingsStore
import com.tidalmusic.player.data.preferences.ThemeMode
import com.tidalmusic.player.data.preferences.ViewMode
import com.tidalmusic.player.data.repository.LibraryRepository
import com.tidalmusic.player.data.repository.LibraryStats
import com.tidalmusic.player.data.repository.PlaylistSummary
import com.tidalmusic.player.data.repository.UserLibraryRepository
import com.tidalmusic.player.domain.PlaylistRules
import com.tidalmusic.player.domain.model.PlaybackProgress
import com.tidalmusic.player.domain.model.PlaybackState
import com.tidalmusic.player.domain.model.QueueItem
import com.tidalmusic.player.domain.model.RepeatMode
import com.tidalmusic.player.domain.model.Song
import com.tidalmusic.player.domain.model.SortDirection
import com.tidalmusic.player.domain.model.SortOrder
import com.tidalmusic.player.playback.PlaybackController
import com.tidalmusic.player.playback.PlaybackEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * The in-memory doubles used by the unit tests.
 *
 * They implement the same contracts as the production classes, including the rules that the
 * production code delegates to [PlaylistRules], so a test failure points at a real
 * behavioural difference.
 */
class FakeLibraryRepository(
    var songs: List<Song> = emptyList(),
) : LibraryRepository {

    var totalBytes: Long = 0L

    /** Set to a non-null value to make the next scan fail. */
    var failure: Throwable? = null

    var loadCount: Int = 0
        private set

    override suspend fun loadSongs(
        includeNonMusic: Boolean,
        onProgress: suspend (loaded: Int, total: Int) -> Unit,
    ): List<Song> {
        loadCount += 1
        failure?.let { throw it }
        onProgress(songs.size, songs.size)
        return songs
    }

    override suspend fun findSong(songId: Long): Song? = songs.firstOrNull { it.id == songId }

    override suspend fun totalAudioBytes(includeNonMusic: Boolean): Long = totalBytes

    override suspend fun audioCount(includeNonMusic: Boolean): Int = songs.size

    override suspend fun genreOf(songId: Long): String? =
        songs.firstOrNull { it.id == songId }?.genre
}

/**
 * An in-memory [UserLibraryRepository] with the same rules as the Room implementation.
 */
class FakeUserLibraryRepository : UserLibraryRepository {

    private val playlistsFlow = MutableStateFlow<List<PlaylistSummary>>(emptyList())
    private val favoritesFlow = MutableStateFlow<Set<Long>>(emptySet())
    private val historyFlow = MutableStateFlow<List<Long>>(emptyList())
    private val hiddenFlow = MutableStateFlow<Set<Long>>(emptySet())

    private var nextId = 1L
    private var clock = 1_000L

    /** The number of times a play was recorded; used to check the play count. */
    val playCounts: MutableMap<Long, Int> = mutableMapOf()

    override fun observePlaylists(): Flow<List<PlaylistSummary>> = playlistsFlow.asStateFlow()

    override fun observePlaylist(id: Long): Flow<PlaylistSummary?> =
        playlistsFlow.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun createPlaylist(name: String, description: String?): Result<Long> =
        runCatching {
            val normalized = PlaylistRules.normalizeName(name)
            val existing = playlistsFlow.value.firstOrNull {
                it.name.equals(normalized, ignoreCase = true)
            }
            val rejection = PlaylistRules.rejectionReason(normalized, existing?.name)
            require(rejection == null) { "rejected: $rejection" }
            val id = nextId++
            playlistsFlow.update { list ->
                list + PlaylistSummary(
                    id = id,
                    name = normalized,
                    description = description?.trim()?.takeIf { it.isNotEmpty() },
                    createdAt = clock++,
                    updatedAt = clock,
                    songIds = emptyList(),
                )
            }
            id
        }

    override suspend fun renamePlaylist(id: Long, name: String, description: String?): Result<Unit> =
        runCatching {
            val normalized = PlaylistRules.normalizeName(name)
            require(playlistsFlow.value.any { it.id == id }) { "no playlist $id" }
            val conflicting = playlistsFlow.value.firstOrNull {
                it.id != id && it.name.equals(normalized, ignoreCase = true)
            }
            val rejection = PlaylistRules.rejectionReason(normalized, conflicting?.name)
            require(rejection == null) { "rejected: $rejection" }
            playlistsFlow.update { list ->
                list.map {
                    if (it.id == id) {
                        it.copy(
                            name = normalized,
                            description = description?.trim()?.takeIf { d -> d.isNotEmpty() },
                            updatedAt = clock++,
                        )
                    } else {
                        it
                    }
                }
            }
        }

    override suspend fun deletePlaylist(id: Long): Result<Unit> = runCatching {
        playlistsFlow.update { list -> list.filterNot { it.id == id } }
    }

    override suspend fun addSongsToPlaylist(playlistId: Long, songIds: List<Long>): Result<Int> =
        runCatching {
            var added = 0
            playlistsFlow.update { list ->
                list.map { playlist ->
                    if (playlist.id != playlistId) {
                        playlist
                    } else {
                        val newIds = songIds.filterNot { it in playlist.songIds }
                        added = newIds.size
                        playlist.copy(songIds = playlist.songIds + newIds)
                    }
                }
            }
            added
        }

    override suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long): Result<Unit> =
        runCatching {
            playlistsFlow.update { list ->
                list.map { playlist ->
                    if (playlist.id == playlistId) {
                        playlist.copy(songIds = playlist.songIds - songId)
                    } else {
                        playlist
                    }
                }
            }
        }

    override suspend fun reorderPlaylist(playlistId: Long, orderedSongIds: List<Long>): Result<Unit> =
        runCatching {
            playlistsFlow.update { list ->
                list.map { playlist ->
                    if (playlist.id == playlistId) {
                        playlist.copy(
                            songIds = PlaylistRules.mergeTrackOrder(playlist.songIds, orderedSongIds),
                        )
                    } else {
                        playlist
                    }
                }
            }
        }

    override suspend fun isInPlaylist(playlistId: Long, songId: Long): Boolean =
        playlistsFlow.value.firstOrNull { it.id == playlistId }?.songIds?.contains(songId) == true

    override fun observeFavoriteIds(): Flow<Set<Long>> = favoritesFlow.asStateFlow()

    override fun observeIsFavorite(songId: Long): Flow<Boolean> =
        favoritesFlow.map { it.contains(songId) }

    override suspend fun setFavorite(songId: Long, favorite: Boolean): Result<Unit> =
        runCatching {
            favoritesFlow.update { ids ->
                if (favorite) ids + songId else ids - songId
            }
        }

    override fun observeFavoriteCount(): Flow<Int> = favoritesFlow.map { it.size }

    /** The current favourite ids; a convenience for the tests. */
    fun favoriteIds(): Set<Long> = favoritesFlow.value

    override fun observeRecentlyPlayedIds(): Flow<List<Long>> = historyFlow.asStateFlow()

    override fun observeRecentlyPlayedCount(): Flow<Int> = historyFlow.map { it.size }

    override suspend fun recordPlay(songId: Long) {
        playCounts[songId] = (playCounts[songId] ?: 0) + 1
        historyFlow.update { ids -> listOf(songId) + ids.filterNot { it == songId } }
    }

    override fun observeHiddenIds(): Flow<Set<Long>> = hiddenFlow.asStateFlow()

    override fun observeHiddenCount(): Flow<Int> = hiddenFlow.map { it.size }

    override suspend fun setHidden(songId: Long, hidden: Boolean): Result<Unit> =
        runCatching {
            hiddenFlow.update { ids ->
                if (hidden) ids + songId else ids - songId
            }
        }

    override suspend fun removeSongReferences(songId: Long): Result<Unit> = runCatching {
        playlistsFlow.update { playlists ->
            playlists.map { it.copy(songIds = it.songIds - songId) }
        }
        favoritesFlow.update { it - songId }
        historyFlow.update { it - songId }
        hiddenFlow.update { it - songId }
    }

    override fun observeLibraryStats(): Flow<LibraryStats> =
        favoritesFlow.map { LibraryStats(favoriteCount = it.size) }
}

/** An in-memory [SettingsStore]. */
class FakeSettingsStore(
    initial: AppPreferences.Settings = AppPreferences.Settings(),
) : SettingsStore {

    private val state = MutableStateFlow(initial)
    override val settings: Flow<AppPreferences.Settings> = state.asStateFlow()

    val current: AppPreferences.Settings get() = state.value

    override suspend fun setThemeMode(mode: ThemeMode) {
        state.update { it.copy(themeMode = mode) }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        state.update { it.copy(dynamicColor = enabled) }
    }

    override suspend fun setArtworkAnimation(enabled: Boolean) {
        state.update { it.copy(artworkAnimation = enabled) }
    }

    override suspend fun setDensity(density: ListDensity) {
        state.update { it.copy(density = density) }
    }

    override suspend fun setResumePlayback(enabled: Boolean) {
        state.update { it.copy(resumePlayback = enabled) }
    }

    override suspend fun setShuffleNewSessions(enabled: Boolean) {
        state.update { it.copy(shuffleNewSessions = enabled) }
    }

    override suspend fun setDefaultRepeatMode(mode: String) {
        state.update { it.copy(defaultRepeatMode = mode) }
    }

    override suspend fun setDefaultPlaybackSpeed(speed: Float) {
        state.update { it.copy(defaultPlaybackSpeed = speed) }
    }

    override suspend fun setIncludeNonMusic(enabled: Boolean) {
        state.update { it.copy(includeNonMusic = enabled) }
    }

    override suspend fun setShowArtwork(enabled: Boolean) {
        state.update { it.copy(showArtwork = enabled) }
    }

    override suspend fun setDefaultSort(order: SortOrder, direction: SortDirection) {
        state.update { it.copy(defaultSortOrder = order, defaultSortDirection = direction) }
    }

    override suspend fun setViewModeLibrary(mode: ViewMode) {
        state.update { it.copy(viewModeLibrary = mode) }
    }

    override suspend fun setViewModeHome(mode: ViewMode) {
        state.update { it.copy(viewModeHome = mode) }
    }

    override suspend fun setViewModeFolders(mode: ViewMode) {
        state.update { it.copy(viewModeFolders = mode) }
    }
}

/** An in-memory [PlaybackSessionStore]. */
class FakePlaybackSessionStore : PlaybackSessionStore {
    var saved: Pair<Song, Long>? = null

    override fun save(song: Song, positionMs: Long) {
        saved = song to positionMs
    }

    override fun restore(): PlaybackSessionStore.RestoredSession? =
        saved?.let { (song, position) -> PlaybackSessionStore.RestoredSession(song, position) }
}

/**
 * An in-memory [PlaybackController].
 *
 * The queue is kept exactly as the UI would see it, so the screen models can be tested
 * without a media session.
 */
class FakePlaybackController : PlaybackController {

    private val _state = MutableStateFlow(PlaybackState())
    override val state = _state.asStateFlow()

    private val _progress = MutableStateFlow(PlaybackProgress())
    override val progress = _progress.asStateFlow()

    private val _queue = MutableStateFlow<List<QueueItem>>(emptyList())
    override val queue = _queue.asStateFlow()

    private val _events = MutableSharedFlow<PlaybackEvent>(extraBufferCapacity = 16)
    override val events = _events

    fun emit(event: PlaybackEvent) {
        _events.tryEmit(event)
    }

    var lastPlayed: List<Song>? = null
        private set

    var lastStartIndex: Int = 0
        private set

    var lastShuffle: Boolean = false
        private set

    override suspend fun connect() = Unit

    override fun release() = Unit

    override fun play(songs: List<Song>, startIndex: Int, shuffle: Boolean) {
        lastPlayed = songs
        lastStartIndex = startIndex
        lastShuffle = shuffle
        _queue.value = songs.mapIndexed { index, song -> QueueItem(index, song, index == startIndex) }
        _state.value = _state.value.copy(
            currentSong = songs.getOrNull(startIndex),
            isPlaying = true,
            queueSize = songs.size,
        )
    }

    override fun play(song: Song, queue: List<Song>, startIndex: Int) =
        play(queue, startIndex, shuffle = false)

    override fun restoreSession(songs: List<Song>, startIndex: Int, positionMs: Long) {
        play(songs, startIndex)
        _state.value = _state.value.copy(isPlaying = false)
        _progress.value = PlaybackProgress(positionMs = positionMs, durationMs = songs.getOrNull(startIndex)?.durationMs ?: 0L)
    }

    override fun playNext(song: Song) {
        val current = _state.value.currentSong
        val index = _queue.value.indexOfFirst { it.song.id == current?.id }
        if (index < 0) {
            addToQueue(song)
            return
        }
        _queue.value = _queue.value.toMutableList().apply { add(index + 1, QueueItem(index + 1, song)) }
            .mapIndexed { i, item -> item.copy(index = i) }
    }

    override fun addToQueue(song: Song) {
        _queue.value = _queue.value + QueueItem(_queue.value.size, song)
        _state.value = _state.value.copy(queueSize = _queue.value.size)
    }

    override fun addToQueue(songs: List<Song>) {
        songs.forEach { addToQueue(it) }
    }

    override fun togglePlayPause() {
        _state.value = _state.value.copy(isPlaying = !_state.value.isPlaying)
    }

    override fun play() {
        _state.value = _state.value.copy(isPlaying = true)
    }

    override fun pause() {
        _state.value = _state.value.copy(isPlaying = false)
    }

    override fun next() {
        val next = _queue.value.getOrNull(_state.value.currentSong?.let { song -> _queue.value.indexOfFirst { it.song.id == song.id } }?.plus(1) ?: 0)
        if (next != null) {
            _state.value = _state.value.copy(currentSong = next.song, isPlaying = true)
        }
    }

    override fun previous() {
        val previous = _queue.value.getOrNull(_state.value.currentSong?.let { song -> _queue.value.indexOfFirst { it.song.id == song.id } }?.minus(1) ?: 0)
        if (previous != null) {
            _state.value = _state.value.copy(currentSong = previous.song, isPlaying = true)
        }
    }

    override fun seekTo(positionMs: Long) {
        _progress.value = _progress.value.copy(positionMs = positionMs)
    }

    override fun setShuffleEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(shuffleEnabled = enabled)
    }

    override fun cycleRepeatMode() {
        val next = when (_state.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _state.value = _state.value.copy(repeatMode = next)
    }

    override fun setRepeatMode(mode: RepeatMode) {
        _state.value = _state.value.copy(repeatMode = mode)
    }

    override fun setPlaybackSpeed(speed: Float) {
        _state.value = _state.value.copy(playbackSpeed = speed)
    }

    override fun removeFromQueue(index: Int) {
        _queue.value = _queue.value.filterIndexed { i, _ -> i != index }
            .mapIndexed { i, item -> item.copy(index = i) }
    }

    override fun moveQueueItem(from: Int, to: Int) {
        val current = _queue.value.toMutableList()
        if (from !in current.indices || to !in current.indices) return
        val item = current.removeAt(from)
        current.add(to, item)
        _queue.value = current.mapIndexed { i, entry -> entry.copy(index = i) }
    }

    override fun skipToQueueItem(index: Int) {
        _queue.value.getOrNull(index)?.let {
            _state.value = _state.value.copy(currentSong = it.song, isPlaying = true)
        }
    }

    override fun clearQueue() {
        _queue.value = emptyList()
        _state.value = _state.value.copy(queueSize = 0)
    }

    override fun stop() {
        _queue.value = emptyList()
        _state.value = PlaybackState()
    }

    override fun setVolume(volume: Float) = Unit

    override fun volume(): Float = 1f

    /** Emits a track-started event, as the real controller does. */
    suspend fun emitTrackStarted(song: Song) {
        _events.emit(PlaybackEvent.TrackStarted(song))
    }
}

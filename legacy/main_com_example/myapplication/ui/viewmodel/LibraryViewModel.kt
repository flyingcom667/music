package com.example.myapplication.ui.viewmodel

import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.local.DeleteRequest
import com.example.myapplication.data.local.ExternalAudioReader
import com.example.myapplication.data.local.FileDeleter
import com.example.myapplication.domain.SongQueries
import com.example.myapplication.domain.model.AppSettings
import com.example.myapplication.domain.model.Song
import com.example.myapplication.domain.model.SongListLayout
import com.example.myapplication.domain.model.SongSortOption
import com.example.myapplication.domain.repository.MusicRepository
import com.example.myapplication.domain.repository.SettingsRepository
import com.example.myapplication.playback.PlaybackController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Backs the home, library, search and favourites tabs. The library itself is a single
 * `StateFlow` of songs; search/sort/folder filtering are pure list transforms so the UI never
 * touches the database while typing.
 */
@OptIn(FlowPreview::class)
class LibraryViewModel(
    private val musicRepository: MusicRepository,
    private val settingsRepository: SettingsRepository,
    private val playbackController: PlaybackController,
    private val fileDeleter: FileDeleter,
    private val externalAudioReader: ExternalAudioReader,
) : ViewModel() {

    data class UiState(
        val songs: List<Song> = emptyList(),
        val visibleSongs: List<Song> = emptyList(),
        val favorites: List<Song> = emptyList(),
        val recentlyAdded: List<Song> = emptyList(),
        val recentlyPlayed: List<Song> = emptyList(),
        val folders: List<String> = emptyList(),
        val query: String = "",
        val folderFilter: String? = null,
        val sortOption: SongSortOption = SongSortOption.NAME_ASC,
        val layout: SongListLayout = SongListLayout.DETAILED,
        val isLoading: Boolean = false,
        val lastSync: Long = 0L,
        val selection: Set<Long> = emptySet(),
    ) {
        val isEmpty: Boolean get() = songs.isEmpty() && !isLoading
        val isSearching: Boolean get() = query.isNotBlank()
        val isSelecting: Boolean get() = selection.isNotEmpty()
    }

    private data class Filter(val songs: List<Song>, val query: String, val folder: String?)

    /**
     * Remembers the result of the search and of the sorts.
     *
     * The state is rebuilt for every change (a new selection, a changed setting, a played
     * song), but filtering and sorting a big library is expensive, so the results are reused
     * when neither the songs nor the sort option changed.
     */
    private class LibraryMemo {

        private var searchSource: List<Song> = emptyList()
        private var searchQuery: String = ""
        private var searchResult: List<Song> = emptyList()

        private val sorted = HashMap<SongSortOption, List<Song>>()
        private var sortSource: List<Song> = emptyList()
        private var recentSource: List<Song> = emptyList()
        private var recentResult: List<Song> = emptyList()

        fun searched(songs: List<Song>, query: String): List<Song> {
            if (query == searchQuery && sameContent(searchSource, songs)) return searchResult
            val result = SongQueries.search(songs, query)
            searchSource = songs
            searchQuery = query
            searchResult = result
            return result
        }

        fun sorted(songs: List<Song>, option: SongSortOption): List<Song> {
            if (sameContent(sortSource, songs)) {
                sorted[option]?.let { return it }
            }
            val result = SongQueries.sort(songs, option)
            sortSource = songs
            sorted[option] = result
            return result
        }

        fun recentlyAdded(songs: List<Song>): List<Song> {
            if (sameContent(recentSource, songs)) return recentResult
            val result = SongQueries.sort(songs, SongSortOption.RECENTLY_ADDED)
            recentSource = songs
            recentResult = result
            return result
        }

        /** Compares two lists of songs by identity: the songs are reused by the repository. */
        private fun sameContent(a: List<Song>, b: List<Song>): Boolean {
            if (a.size != b.size) return false
            for (index in a.indices) {
                if (a[index] !== b[index]) return false
            }
            return true
        }
    }

    private val query = MutableStateFlow("")
    private val folderFilter = MutableStateFlow<String?>(null)
    private val memo = LibraryMemo()

    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    val selection: StateFlow<Set<Long>> = _selection.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Set while the system asks the user to confirm the deletion of files. */
    private val _pendingDelete = MutableStateFlow<DeleteRequest?>(null)
    val pendingDelete: StateFlow<DeleteRequest?> = _pendingDelete.asStateFlow()
    private var pendingDeleteSongs: List<Song> = emptyList()

    val state: StateFlow<UiState> = combine(
        combine(
            musicRepository.songs,
            // Typing is debounced: a long library is only filtered once the user stops typing.
            query.debounce { text -> if (text.isBlank()) 0L else SEARCH_DEBOUNCE_MS }
                .distinctUntilChanged(),
            folderFilter,
        ) { songs, q, folder ->
            Filter(songs, q, folder)
        },
        settingsRepository.settings,
        musicRepository.isLoading,
        musicRepository.folders,
        _selection,
    ) { filter, settings, loading, folders, selected ->
        val inFolder = if (filter.folder == null) {
            filter.songs
        } else {
            filter.songs.filter { it.folder == filter.folder }
        }
        val searched = memo.searched(inFolder, filter.query)
        // The library is sorted once and reused: the favourites are a subset of the same
        // order, and without a folder or search filter the visible list is the sorted library.
        val sortedAll = memo.sorted(filter.songs, settings.sortOption)
        val visible = if (searched === inFolder && inFolder === filter.songs) {
            sortedAll
        } else {
            memo.sorted(searched, settings.sortOption)
        }
        UiState(
            songs = filter.songs,
            visibleSongs = visible,
            favorites = sortedAll.filter { it.isFavorite },
            // Recently Added is its own library tab, so it is not capped like the home carousels.
            recentlyAdded = memo.recentlyAdded(searched),
            recentlyPlayed = SongQueries.recentlyPlayed(filter.songs, RECENT_LIMIT),
            folders = folders,
            query = filter.query,
            folderFilter = filter.folder,
            sortOption = settings.sortOption,
            layout = settings.listLayout,
            isLoading = loading,
            lastSync = settings.lastLibrarySync,
            selection = selected,
        )
        // Filtering and sorting a big library is not a main-thread job: the whole transformation
        // runs on the default dispatcher so scrolling and typing stay smooth.
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    init {
        viewModelScope.launch {
            if (!musicRepository.isLoaded.value) scan()
        }
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun clearQuery() {
        query.value = ""
    }

    fun setFolderFilter(folder: String?) {
        folderFilter.value = folder
    }

    fun setSortOption(option: SongSortOption) {
        viewModelScope.launch { settingsRepository.update { it.copy(sortOption = option) } }
    }

    fun setLayout(layout: SongListLayout) {
        viewModelScope.launch { settingsRepository.update { it.copy(listLayout = layout) } }
    }

    fun cycleLayout() {
        viewModelScope.launch {
            settingsRepository.update { current: AppSettings ->
                val next = when (current.listLayout) {
                    SongListLayout.DETAILED -> SongListLayout.COMPACT
                    SongListLayout.COMPACT -> SongListLayout.ARTWORK
                    SongListLayout.ARTWORK -> SongListLayout.DETAILED
                }
                current.copy(listLayout = next)
            }
        }
    }

    fun scan() {
        viewModelScope.launch {
            val result = musicRepository.sync()
            result.onSuccess { count -> _messages.tryEmit("$count songs found") }
                .onFailure { _messages.tryEmit("Library scan failed") }
        }
    }

    // region selection

    /** Long press on a row: the row is selected and the selection mode starts. */
    fun startSelection(mediaId: Long) {
        _selection.update { current ->
            if (mediaId in current) current else current + mediaId
        }
    }

    fun toggleSelection(mediaId: Long) {
        _selection.update { current ->
            if (mediaId in current) current - mediaId else current + mediaId
        }
    }

    fun selectAll(mediaIds: List<Long>) {
        _selection.value = mediaIds.toSet()
    }

    fun clearSelection() {
        _selection.value = emptySet()
    }

    /** The songs of the current selection, in the order of the library. */
    fun selectedSongs(songs: List<Song>): List<Song> {
        val selected = _selection.value
        if (selected.isEmpty()) return emptyList()
        return songs.filter { it.mediaId in selected }
    }

    // endregion

    // region playback

    fun play(song: Song) {
        val songs = state.value.visibleSongs.ifEmpty { listOf(song) }
        val index = songs.indexOfFirst { it.mediaId == song.mediaId }.coerceAtLeast(0)
        playbackController.play(songs, index)
    }

    fun playAll() {
        val songs = state.value.visibleSongs
        if (songs.isNotEmpty()) playbackController.play(songs, 0)
    }

    fun shuffleAll() {
        val songs = state.value.visibleSongs.ifEmpty { state.value.songs }
        if (songs.isNotEmpty()) playbackController.playSongsShuffled(songs)
    }

    fun playSongs(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isNotEmpty()) playbackController.play(songs, startIndex)
    }

    /**
     * Plays a file that is not part of the library, for example one that was opened from a
     * file manager. The metadata is read from the file itself.
     */
    fun playExternalFile(uri: String) {
        viewModelScope.launch {
            val audio = externalAudioReader.read(uri.toUri()).getOrNull() ?: return@launch
            playbackController.playExternal(
                uri = uri,
                title = audio.title,
                artist = audio.artist,
                album = audio.album,
                durationMs = audio.durationMs,
            )
        }
    }

    fun playNext(song: Song) = playbackController.playNext(song)

    fun addToQueue(song: Song) = playbackController.addToQueue(song)

    fun addAllToQueue(songs: List<Song>) {
        if (songs.isEmpty()) return
        playbackController.addAllToQueue(songs)
        clearSelection()
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            val favorite = musicRepository.toggleFavorite(song.mediaId)
            _messages.tryEmit(if (favorite) "Added to favourites" else "Removed from favourites")
        }
    }

    fun hide(song: Song) {
        viewModelScope.launch {
            musicRepository.hideSong(song.mediaId)
            _messages.tryEmit("Removed from library")
        }
    }

    /** Hides the given songs: the files stay on the device, they are only hidden in the app. */
    fun removeFromLibrary(songs: List<Song>) {
        if (songs.isEmpty()) return
        viewModelScope.launch {
            songs.forEach { musicRepository.hideSong(it.mediaId) }
            _messages.tryEmit(
                if (songs.size == 1) "Removed from library" else "${songs.size} songs removed from library",
            )
            clearSelection()
        }
    }

    // endregion

    // region file deletion

    /**
     * Deletes the files of [songs] from the device.
     *
     * On Android 11 and newer the user has to confirm, so the request is published on
     * [pendingDelete] and the deletion happens in [confirmPendingDeletion].
     */
    fun requestDeleteFiles(songs: List<Song>) {
        if (songs.isEmpty()) return
        viewModelScope.launch {
            // The system dialog is created by the MediaProvider, so it is prepared off the
            // main thread.
            val request = withContext(Dispatchers.IO) {
                fileDeleter.prepare(songs.map { it.uri.toUri() })
            }
            when (request) {
                is DeleteRequest.Empty -> Unit
                is DeleteRequest.Ready -> performDeletion(request.uris, songs)
                is DeleteRequest.NeedsConsent -> {
                    pendingDeleteSongs = songs
                    _pendingDelete.value = request
                }
            }
        }
    }

    /** The user confirmed the deletion in the system dialog. */
    fun confirmPendingDeletion() {
        val request = _pendingDelete.value as? DeleteRequest.NeedsConsent ?: return
        _pendingDelete.value = null
        val songs = pendingDeleteSongs
        pendingDeleteSongs = emptyList()
        viewModelScope.launch { performDeletion(request.uris, songs) }
    }

    /** The user declined the deletion in the system dialog. */
    fun cancelPendingDeletion() {
        _pendingDelete.value = null
        pendingDeleteSongs = emptyList()
    }

    private suspend fun performDeletion(uris: List<Uri>, songs: List<Song>) {
        val deleted = withContext(Dispatchers.IO) { fileDeleter.delete(uris) }.toSet()
        val removedIds = songs.filter { it.uri.toUri() in deleted }.map { it.mediaId }
        if (removedIds.isNotEmpty()) musicRepository.removeSongs(removedIds)
        clearSelection()
        _messages.tryEmit(
            when {
                deleted.isEmpty() -> "The file could not be deleted"
                deleted.size == 1 -> "File deleted"
                else -> "${deleted.size} files deleted"
            },
        )
    }

    // endregion

    private companion object {
        const val RECENT_LIMIT = 20

        /** The search only runs when the user stops typing for a moment. */
        const val SEARCH_DEBOUNCE_MS = 250L
    }
}

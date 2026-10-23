package com.tidalmusic.player.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tidalmusic.player.data.preferences.SettingsStore
import com.tidalmusic.player.data.repository.LibraryRepository
import com.tidalmusic.player.data.repository.UserLibraryRepository
import com.tidalmusic.player.domain.LibraryQueries
import com.tidalmusic.player.domain.model.Song
import com.tidalmusic.player.domain.model.SortDirection
import com.tidalmusic.player.domain.model.SortOrder
import com.tidalmusic.player.domain.model.SortSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the library screen is showing right now. */
enum class LibraryStatus { LOADING, CONTENT, EMPTY, ERROR }

/**
 * The whole library screen in one immutable snapshot.
 *
 * Every field the UI renders lives here, so a screen never queries the media provider or the
 * database itself and the load/empty/error states are explicit rather than inferred.
 */
data class LibraryUiState(
    val status: LibraryStatus = LibraryStatus.LOADING,
    val songs: List<Song> = emptyList(),
    val recentlyAdded: List<Song> = emptyList(),
    val allSongs: Map<Long, Song> = emptyMap(),
    val favorites: Set<Long> = emptySet(),
    val recentlyPlayed: List<Song> = emptyList(),
    val query: String = "",
    val sort: SortSpec = SortSpec(),
    val isRefreshing: Boolean = false,
    val scanned: Int = 0,
    val scanTotal: Int = 0,
    val errorMessage: String? = null,
    val permissionGranted: Boolean = true,
    /** The first letters present in [songs], in list order — the letter rail shows only these. */
    val indexLetters: List<Char> = emptyList(),
    /** Row index of the first song for each letter in [indexLetters]. */
    val letterStartIndices: Map<Char, Int> = emptyMap(),
) {
    val isEmpty: Boolean get() = songs.isEmpty() && recentlyAdded.isEmpty()
    val isSearching: Boolean get() = query.isNotBlank()
    val songCount: Int get() = allSongs.size

    /** The row the letter rail should scroll to for [letter], or null when it has no songs. */
    fun startIndexFor(letter: Char): Int? = letterStartIndices[letter]
}

private data class ComputedLibrary(
    val status: LibraryStatus,
    val songs: List<Song>,
    val recentlyAdded: List<Song>,
    val allSongs: Map<Long, Song>,
    val recentlyPlayed: List<Song>,
    val indexLetters: List<Char>,
    val letterStartIndices: Map<Char, Int>,
)

/**
 * Owns the device library: scanning, filtering, sorting and the two library tabs.
 *
 * Scanning runs on [Dispatchers.IO] inside the repository; filtering and sorting run on
 * [Dispatchers.Default]. Duplicate scans are prevented by keeping the running job.
 */
class LibraryViewModel(
    private val libraryRepository: LibraryRepository,
    private val userLibraryRepository: UserLibraryRepository,
    private val preferences: SettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    private var scanJob: Job? = null
    private var filterJob: Job? = null

    /** The unfiltered library and the ids the user hid from it. */
    private var rawSongs: List<Song> = emptyList()
    private var hiddenIds: Set<Long> = emptySet()
    private var recentlyPlayedIds: List<Long> = emptyList()
    private var hasScannedOnce = false
    private var hasLoadedSortPreference = false
    private var includeNonMusic = false

    init {
        viewModelScope.launch {
            userLibraryRepository.observeHiddenIds().collect { hidden ->
                hiddenIds = hidden
                recompute(immediate = true)
            }
        }
        viewModelScope.launch {
            userLibraryRepository.observeFavoriteIds().collect { favorites ->
                _state.value = _state.value.copy(favorites = favorites)
            }
        }
        // The history stores only ids; the songs themselves come from the media provider, so
        // the recently played list follows the library automatically.
        viewModelScope.launch {
            userLibraryRepository.observeRecentlyPlayedIds().collect { ids ->
                recentlyPlayedIds = ids
                val byId = _state.value.allSongs
                _state.value = _state.value.copy(
                    recentlyPlayed = ids.mapNotNull { byId[it] },
                )
            }
        }
        viewModelScope.launch {
            preferences.settings.collect { current ->
                includeNonMusic = current.includeNonMusic
                if (!hasLoadedSortPreference) {
                    hasLoadedSortPreference = true
                    _state.value = _state.value.copy(
                        sort = SortSpec(current.defaultSortOrder, current.defaultSortDirection),
                    )
                    recompute(immediate = true)
                }
            }
        }
    }

    /**
     * Reads the media library.
     *
     * @param permissionGranted whether the audio permission is currently held. When it is
     *   not, the screen shows the permission state instead of an empty library.
     * @param force when false a scan that already produced content is kept and the call is
     *   ignored; the screen uses this to load only once per process.
     */
    fun refresh(permissionGranted: Boolean, force: Boolean = false) {
        val current = _state.value
        if (!permissionGranted) {
            scanJob?.cancel()
            clearMediaLibrary(current)
            return
        }
        if (scanJob?.isActive == true) return
        if (!force && hasScannedOnce && current.status == LibraryStatus.CONTENT) return

        _state.value = current.copy(
            permissionGranted = true,
            isRefreshing = rawSongs.isNotEmpty(),
            status = if (rawSongs.isEmpty()) LibraryStatus.LOADING else current.status,
            errorMessage = null,
        )

        scanJob = viewModelScope.launch {
            try {
                val songs = libraryRepository.loadSongs(includeNonMusic = includeNonMusic) { loaded, total ->
                    _state.value = _state.value.copy(scanned = loaded, scanTotal = total)
                }
                rawSongs = songs
                hasScannedOnce = true
                recompute(immediate = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                hasScannedOnce = true
                clearMediaLibrary(_state.value)
            } catch (e: Exception) {
                hasScannedOnce = true
                _state.value = _state.value.copy(
                    status = LibraryStatus.ERROR,
                    isRefreshing = false,
                    errorMessage = null,
                )
            }
        }
    }

    private fun clearMediaLibrary(current: LibraryUiState) {
        rawSongs = emptyList()
        _state.value = current.copy(
            permissionGranted = false,
            status = LibraryStatus.EMPTY,
            songs = emptyList(),
            recentlyAdded = emptyList(),
            allSongs = emptyMap(),
            recentlyPlayed = emptyList(),
            isRefreshing = false,
            scanned = 0,
            scanTotal = 0,
            errorMessage = null,
        )
    }

    fun onQueryChange(query: String) {
        _state.value = _state.value.copy(query = query)
        // Debounced so that typing never re-filters a large library on every keystroke.
        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            delay(QUERY_DEBOUNCE_MS)
            recompute(immediate = true)
        }
    }

    fun clearQuery() {
        filterJob?.cancel()
        _state.value = _state.value.copy(query = "")
        recompute(immediate = true)
    }

    fun setSortOrder(order: SortOrder) {
        val current = _state.value.sort
        val next = if (current.order == order) {
            current.copy(direction = current.direction.opposite())
        } else {
            SortSpec(order, defaultDirection(order))
        }
        applySort(next)
    }

    fun setSort(spec: SortSpec) = applySort(spec)

    private fun applySort(spec: SortSpec) {
        _state.value = _state.value.copy(sort = spec)
        recompute(immediate = true)
        viewModelScope.launch {
            preferences.setDefaultSort(spec.order, spec.direction)
        }
    }

    fun songById(id: Long): Song? = _state.value.allSongs[id]

    private fun recompute(immediate: Boolean) {
        val snapshot = _state.value
        val query = snapshot.query
        val sort = snapshot.sort
        val sourceSongs = rawSongs
        val hidden = hiddenIds
        val historyIds = recentlyPlayedIds

        val compute: suspend () -> Unit = {
            val result = withContext(Dispatchers.Default) {
                val visible = sourceSongs.filterNot { it.id in hidden }
                val filtered = LibraryQueries.search(visible, query)
                val byId = visible.associateBy { it.id }
                val sorted = LibraryQueries.sort(filtered, sort)
                // One pass over the sorted list builds the letter rail index: only letters
                // that actually have songs are recorded, together with their first row.
                val letters = ArrayList<Char>()
                val starts = HashMap<Char, Int>()
                sorted.forEachIndexed { index, song ->
                    val letter = LibraryQueries.indexLetter(song.title)
                    if (letter !in starts) {
                        starts[letter] = index
                        letters += letter
                    }
                }
                ComputedLibrary(
                    status = if (visible.isEmpty()) LibraryStatus.EMPTY else LibraryStatus.CONTENT,
                    songs = sorted,
                    recentlyAdded = LibraryQueries.recentlyAdded(filtered),
                    allSongs = byId,
                    recentlyPlayed = historyIds.mapNotNull { byId[it] },
                    indexLetters = letters,
                    letterStartIndices = starts,
                )
            }
            _state.value = _state.value.copy(
                status = result.status,
                songs = result.songs,
                recentlyAdded = result.recentlyAdded,
                allSongs = result.allSongs,
                recentlyPlayed = result.recentlyPlayed,
                indexLetters = result.indexLetters,
                letterStartIndices = result.letterStartIndices,
                isRefreshing = false,
            )
        }

        if (immediate) {
            filterJob?.cancel()
            viewModelScope.launch { compute() }
        } else {
            filterJob?.cancel()
            filterJob = viewModelScope.launch {
                delay(QUERY_DEBOUNCE_MS)
                compute()
            }
        }
    }

    private fun defaultDirection(order: SortOrder): SortDirection = when (order) {
        SortOrder.DATE_ADDED -> SortDirection.DESCENDING
        else -> SortDirection.ASCENDING
    }

    private fun SortDirection.opposite(): SortDirection = when (this) {
        SortDirection.ASCENDING -> SortDirection.DESCENDING
        SortDirection.DESCENDING -> SortDirection.ASCENDING
    }

    private companion object {
        const val QUERY_DEBOUNCE_MS = 150L
    }
}

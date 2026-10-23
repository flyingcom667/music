package com.example.myapplication.data.repository

import android.util.Log
import com.example.myapplication.data.db.FavoriteDao
import com.example.myapplication.data.db.FavoriteEntity
import com.example.myapplication.data.db.HiddenSongDao
import com.example.myapplication.data.db.HiddenSongEntity
import com.example.myapplication.data.db.PlayStatDao
import com.example.myapplication.data.db.PlaylistSongDao
import com.example.myapplication.data.db.PlayStatEntity
import com.example.myapplication.data.db.SongDao
import com.example.myapplication.data.db.SongEntity
import com.example.myapplication.data.db.SongOverrideDao
import com.example.myapplication.data.db.SongOverrideEntity
import com.example.myapplication.data.db.baseMetadata
import com.example.myapplication.data.db.toDomain
import com.example.myapplication.data.db.toEntity
import com.example.myapplication.data.local.LibraryScanner
import com.example.myapplication.domain.model.MetadataOverride
import com.example.myapplication.domain.model.Song
import com.example.myapplication.domain.model.SongMetadata
import com.example.myapplication.domain.repository.MusicRepository
import com.example.myapplication.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Library, favourites and metadata overrides.
 *
 * The Room tables are combined in Kotlin instead of through one big SQL join so the
 * displayed metadata (base + overrides + normalised search keys) is produced in one place
 * and stays unit testable.
 */
class MusicRepositoryImpl(
    private val songDao: SongDao,
    private val playStatDao: PlayStatDao,
    private val overrideDao: SongOverrideDao,
    private val favoriteDao: FavoriteDao,
    private val hiddenDao: HiddenSongDao,
    private val playlistSongDao: PlaylistSongDao,
    private val scanner: LibraryScanner,
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
    /**
     * Runs a block of database calls in one transaction.
     *
     * The repository does not know about Room itself, which keeps it unit testable; the app
     * hands in the transaction of the real database.
     */
    private val transaction: suspend (suspend () -> Unit) -> Unit = { block -> block() },
) : MusicRepository {

    private val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)
    override val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    private val _folders = MutableStateFlow<List<String>>(emptyList())
    override val folders: StateFlow<List<String>> = _folders.asStateFlow()

    private val _lastSync = MutableStateFlow(0L)
    override val lastSync: StateFlow<Long> = _lastSync.asStateFlow()

    /**
     * The last mapping of a song.
     *
     * The library is re-read completely whenever a row changes (a play count, a favourite, a
     * metadata override), so the mapping of the unchanged songs is kept and reused.
     */
    private class MappedSong(
        val entity: SongEntity,
        val override: SongOverrideEntity?,
        val playCount: Int,
        val lastPlayedAt: Long,
        val song: Song,
    )

    private val mappingCache = HashMap<Long, MappedSong>()

    override val songs: StateFlow<List<Song>> = combine(
        songDao.observeAll(),
        overrideDao.observeAll(),
        favoriteDao.observeAll(),
        hiddenDao.observeAll(),
        playStatDao.observeAll(),
    ) { entities, overrides, favorites, hidden, stats ->
        val overrideMap = overrides.associateBy { it.mediaId }
        val favoriteMap = favorites.associate { it.mediaId to it.addedAt }
        val statMap = stats.associateBy { it.mediaId }
        val hiddenIds = hidden.mapTo(HashSet()) { it.mediaId }
        val cache = mappingCache
        synchronized(cache) {
            val songs = entities.asSequence()
                .filter { it.mediaId !in hiddenIds }
                .map { entity ->
                    val override = overrideMap[entity.mediaId]
                    val favoriteAddedAt = favoriteMap[entity.mediaId] ?: 0L
                    val stat = statMap[entity.mediaId]
                    val playCount = stat?.playCount ?: 0
                    val lastPlayedAt = stat?.lastPlayedAt ?: 0L
                    val cached = cache[entity.mediaId]
                    if (cached != null &&
                        cached.entity == entity &&
                        cached.override == override &&
                        cached.playCount == playCount &&
                        cached.lastPlayedAt == lastPlayedAt
                    ) {
                        // Nothing about the song changed, so the mapping (and the expensive
                        // normalisation of its search keys) can be reused.
                        if (cached.song.favoriteAddedAt == favoriteAddedAt) {
                            cached.song
                        } else {
                            cached.song.copy(favoriteAddedAt = favoriteAddedAt)
                        }
                    } else {
                        val song = entity.toDomain(
                            override = override,
                            favoriteAddedAt = favoriteAddedAt,
                            playCount = playCount,
                            lastPlayedAt = lastPlayedAt,
                        )
                        cache[entity.mediaId] = MappedSong(
                            entity = entity,
                            override = override,
                            playCount = playCount,
                            lastPlayedAt = lastPlayedAt,
                            song = song,
                        )
                        song
                    }
                }
                .toList()
            // Songs that are gone from the library must not stay in the cache.
            val present = entities.mapTo(HashSet()) { it.mediaId }
            cache.keys.retainAll(present)
            songs
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    override suspend fun sync(): Result<Int> {
        _isLoading.value = true
        return try {
            val settings = settingsRepository.settings.value
            val result = scanner.scan(
                minDurationMs = settings.minimumTrackDurationSec * 1000L,
                excludedFolders = settings.excludedFolders,
            )
            transaction {
                // Only the songs that really changed are written. A rescan of an unchanged
                // library does not touch the database, so the list on the screen stays as it
                // is and nothing has to be built again.
                val existing = songDao.getAll().associateBy { it.mediaId }
                val changed = result.songs.filter { existing[it.mediaId] != it }
                if (changed.isNotEmpty()) songDao.upsertAll(changed)
                val ids = result.songs.map { it.mediaId }
                if (ids.isEmpty()) {
                    songDao.deleteAll()
                } else {
                    val missing = existing.keys - ids.toSet()
                    if (missing.isNotEmpty()) songDao.deleteByIds(missing.toList())
                }
            }
            _folders.value = result.scannedFolders
            _lastSync.value = System.currentTimeMillis()
            settingsRepository.update { it.copy(lastLibrarySync = _lastSync.value) }
            _isLoaded.value = true
            Result.success(result.songs.size)
        } catch (error: Exception) {
            Log.e(TAG, "Library scan failed", error)
            Result.failure(error)
        } finally {
            _isLoading.value = false
        }
    }

    override suspend fun markPlayed(mediaId: Long) {
        val timestamp = System.currentTimeMillis()
        val stat = playStatDao.get(mediaId)
        playStatDao.upsert(
            PlayStatEntity(
                mediaId = mediaId,
                playCount = (stat?.playCount ?: 0) + 1,
                lastPlayedAt = timestamp,
            ),
        )
    }

    override suspend fun setFavorite(mediaId: Long, favorite: Boolean) {
        if (favorite) {
            favoriteDao.add(FavoriteEntity(mediaId, System.currentTimeMillis()))
        } else {
            favoriteDao.remove(mediaId)
        }
    }

    override suspend fun toggleFavorite(mediaId: Long): Boolean {
        val nowFavorite = favoriteDao.get(mediaId) == null
        setFavorite(mediaId, nowFavorite)
        return nowFavorite
    }

    override suspend fun hideSong(mediaId: Long) {
        hiddenDao.hide(HiddenSongEntity(mediaId, System.currentTimeMillis()))
        favoriteDao.remove(mediaId)
    }

    override suspend fun unhideSong(mediaId: Long) {
        hiddenDao.unhide(mediaId)
    }

    override suspend fun hiddenSongs(): List<Song> {
        val hiddenIds = hiddenDao.getAll().mapTo(HashSet()) { it.mediaId }
        if (hiddenIds.isEmpty()) return emptyList()
        // Hidden songs are never favourites (hiding removes the favourite), so the favourite
        // timestamp is not needed here.
        return songDao.getAll()
            .filter { it.mediaId in hiddenIds }
            .map { it.toDomain(override = null, favoriteAddedAt = 0L) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    }

    override suspend fun restoreHiddenSongs() {
        hiddenDao.clear()
    }

    override suspend fun removeSongs(mediaIds: List<Long>) {
        if (mediaIds.isEmpty()) return
        transaction {
            songDao.deleteByIds(mediaIds)
            overrideDao.deleteAll(mediaIds)
            favoriteDao.removeAll(mediaIds)
            hiddenDao.unhideAll(mediaIds)
            playlistSongDao.removeAll(mediaIds)
            playStatDao.deleteAll(mediaIds)
        }
    }

    override suspend fun saveOverride(mediaId: Long, override: MetadataOverride) {
        if (override.isEmpty) {
            overrideDao.delete(mediaId)
        } else {
            overrideDao.upsert(override.toEntity(mediaId))
        }
    }

    override suspend fun resetOverride(mediaId: Long) {
        overrideDao.delete(mediaId)
    }

    override suspend fun getSong(mediaId: Long): Song? {
        val entity = songDao.getById(mediaId) ?: return null
        val stat = playStatDao.get(mediaId)
        return entity.toDomain(
            override = overrideDao.get(mediaId),
            favoriteAddedAt = favoriteDao.get(mediaId)?.addedAt ?: 0L,
            playCount = stat?.playCount ?: 0,
            lastPlayedAt = stat?.lastPlayedAt ?: 0L,
        )
    }

    override suspend fun getSongs(ids: List<Long>): List<Song> {
        if (ids.isEmpty()) return emptyList()
        val entities = songDao.getByIds(ids).associateBy { it.mediaId }
        val overrides = overrideDao.getByIds(ids).associateBy { it.mediaId }
        val favorites = favoriteDao.getByIds(ids).associate { it.mediaId to it.addedAt }
        val stats = playStatDao.getByIds(ids).associateBy { it.mediaId }
        return ids.mapNotNull { id ->
            val entity = entities[id] ?: return@mapNotNull null
            val stat = stats[id]
            entity.toDomain(
                override = overrides[id],
                favoriteAddedAt = favorites[id] ?: 0L,
                playCount = stat?.playCount ?: 0,
                lastPlayedAt = stat?.lastPlayedAt ?: 0L,
            )
        }
    }

    override suspend fun getOverride(mediaId: Long): MetadataOverride =
        overrideDao.get(mediaId)?.toDomain() ?: MetadataOverride()

    override suspend fun getBaseMetadata(mediaId: Long): SongMetadata? =
        songDao.getById(mediaId)?.baseMetadata()

    override suspend fun hiddenCount(): Int = hiddenDao.getAll().size

    override suspend fun clearPlayStatistics() {
        playStatDao.clear()
    }

    private companion object {
        const val TAG = "MusicRepository"
    }
}

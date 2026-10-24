package com.tidalmusic.player.data.preferences

import com.tidalmusic.player.domain.model.SortDirection
import com.tidalmusic.player.domain.model.SortOrder
import kotlinx.coroutines.flow.Flow

/**
 * Read/write access to the user settings.
 *
 * The production implementation is backed by DataStore ([AppPreferences]); the screen models
 * only ever see this interface, so they can be exercised with an in-memory fake.
 */
interface SettingsStore {

    /** The current settings; emits again whenever something changes. */
    val settings: Flow<AppPreferences.Settings>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setDynamicColor(enabled: Boolean)

    suspend fun setArtworkAnimation(enabled: Boolean)

    suspend fun setDensity(density: ListDensity)

    suspend fun setResumePlayback(enabled: Boolean)

    suspend fun setShuffleNewSessions(enabled: Boolean)

    suspend fun setDefaultRepeatMode(mode: String)

    suspend fun setDefaultPlaybackSpeed(speed: Float)

    suspend fun setIncludeNonMusic(enabled: Boolean)

    suspend fun setShowArtwork(enabled: Boolean)

    suspend fun setDefaultSort(order: SortOrder, direction: SortDirection)

    /** Per-screen song layout: a detailed list or a cover grid. Each screen remembers its own. */
    suspend fun setViewModeLibrary(mode: ViewMode)

    suspend fun setViewModeHome(mode: ViewMode)

    suspend fun setViewModeFolders(mode: ViewMode)
}

package com.tidalmusic.player.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tidalmusic.player.domain.model.SortDirection
import com.tidalmusic.player.domain.model.SortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tidal_music_settings")

/** Theme preference. */
enum class ThemeMode(val key: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/** Density of the song lists. */
enum class ListDensity(val key: String) {
    COMFORTABLE("comfortable"),
    COMPACT("compact");

    companion object {
        fun fromKey(key: String?): ListDensity = entries.firstOrNull { it.key == key } ?: COMFORTABLE
    }
}

/**
 * How a screen lays out its songs: a detailed row list or a cover grid.
 *
 * Every screen keeps its own value, so switching the library to covers does not force
 * the same layout on playlists or the folder browser.
 */
enum class ViewMode(val key: String) {
    LIST("list"),
    COVER("cover");

    companion object {
        fun fromKey(key: String?): ViewMode = entries.firstOrNull { it.key == key } ?: LIST
    }
}

/**
 * Typed façade over the DataStore preferences. Screens read a single [Settings] flow
 * and never touch keys directly.
 */
class AppPreferences(private val context: Context) : SettingsStore {

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val ARTWORK_ANIMATION = booleanPreferencesKey("artwork_animation")
        val DENSITY = stringPreferencesKey("list_density")
        val RESUME_PLAYBACK = booleanPreferencesKey("resume_playback")
        val SHUFFLE_NEW = booleanPreferencesKey("shuffle_new_sessions")
        val DEFAULT_REPEAT = stringPreferencesKey("default_repeat")
        val DEFAULT_SPEED = floatPreferencesKey("default_speed")
        val INCLUDE_NON_MUSIC = booleanPreferencesKey("include_non_music")
        val SHOW_ARTWORK = booleanPreferencesKey("show_artwork")
        val DEFAULT_SORT_ORDER = stringPreferencesKey("default_sort_order")
        val DEFAULT_SORT_DIRECTION = stringPreferencesKey("default_sort_direction")
        val VIEW_MODE_LIBRARY = stringPreferencesKey("view_mode_library")
        val VIEW_MODE_HOME = stringPreferencesKey("view_mode_home")
        val VIEW_MODE_FOLDERS = stringPreferencesKey("view_mode_folders")
    }

    /** All user-facing settings in one immutable snapshot. */
    data class Settings(
        val themeMode: ThemeMode = ThemeMode.SYSTEM,
        val dynamicColor: Boolean = false,
        val artworkAnimation: Boolean = true,
        val density: ListDensity = ListDensity.COMFORTABLE,
        val resumePlayback: Boolean = true,
        val shuffleNewSessions: Boolean = false,
        val defaultRepeatMode: String = "off",
        val defaultPlaybackSpeed: Float = 1.0f,
        val includeNonMusic: Boolean = false,
        val showArtwork: Boolean = true,
        val defaultSortOrder: SortOrder = SortOrder.TITLE,
        val defaultSortDirection: SortDirection = SortDirection.ASCENDING,
        val viewModeLibrary: ViewMode = ViewMode.LIST,
        val viewModeHome: ViewMode = ViewMode.LIST,
        val viewModeFolders: ViewMode = ViewMode.LIST,
    )

    override val settings: Flow<Settings> = context.dataStore.data
        .catch { error ->
            // A corrupt preferences file must not crash the app; fall back to defaults.
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs ->
            Settings(
                themeMode = ThemeMode.fromKey(prefs[Keys.THEME]),
                dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
                artworkAnimation = prefs[Keys.ARTWORK_ANIMATION] ?: true,
                density = ListDensity.fromKey(prefs[Keys.DENSITY]),
                resumePlayback = prefs[Keys.RESUME_PLAYBACK] ?: true,
                shuffleNewSessions = prefs[Keys.SHUFFLE_NEW] ?: false,
                defaultRepeatMode = prefs[Keys.DEFAULT_REPEAT] ?: "off",
                defaultPlaybackSpeed = prefs[Keys.DEFAULT_SPEED] ?: 1.0f,
                includeNonMusic = prefs[Keys.INCLUDE_NON_MUSIC] ?: false,
                showArtwork = prefs[Keys.SHOW_ARTWORK] ?: true,
                defaultSortOrder = SortOrder.fromKey(prefs[Keys.DEFAULT_SORT_ORDER]),
                defaultSortDirection = SortDirection.fromKey(prefs[Keys.DEFAULT_SORT_DIRECTION]),
                viewModeLibrary = ViewMode.fromKey(prefs[Keys.VIEW_MODE_LIBRARY]),
                viewModeHome = ViewMode.fromKey(prefs[Keys.VIEW_MODE_HOME]),
                viewModeFolders = ViewMode.fromKey(prefs[Keys.VIEW_MODE_FOLDERS]),
            )
        }

    override suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.THEME] = mode.key }

    override suspend fun setDynamicColor(enabled: Boolean) = edit { it[Keys.DYNAMIC_COLOR] = enabled }

    override suspend fun setArtworkAnimation(enabled: Boolean) = edit { it[Keys.ARTWORK_ANIMATION] = enabled }

    override suspend fun setDensity(density: ListDensity) = edit { it[Keys.DENSITY] = density.key }

    override suspend fun setResumePlayback(enabled: Boolean) = edit { it[Keys.RESUME_PLAYBACK] = enabled }

    override suspend fun setShuffleNewSessions(enabled: Boolean) = edit { it[Keys.SHUFFLE_NEW] = enabled }

    override suspend fun setDefaultRepeatMode(mode: String) = edit { it[Keys.DEFAULT_REPEAT] = mode }

    override suspend fun setDefaultPlaybackSpeed(speed: Float) = edit { it[Keys.DEFAULT_SPEED] = speed }

    override suspend fun setIncludeNonMusic(enabled: Boolean) = edit { it[Keys.INCLUDE_NON_MUSIC] = enabled }

    override suspend fun setShowArtwork(enabled: Boolean) = edit { it[Keys.SHOW_ARTWORK] = enabled }

    override suspend fun setDefaultSort(order: SortOrder, direction: SortDirection) = edit {
        it[Keys.DEFAULT_SORT_ORDER] = order.key
        it[Keys.DEFAULT_SORT_DIRECTION] = direction.key
    }

    override suspend fun setViewModeLibrary(mode: ViewMode) = edit { it[Keys.VIEW_MODE_LIBRARY] = mode.key }

    override suspend fun setViewModeHome(mode: ViewMode) = edit { it[Keys.VIEW_MODE_HOME] = mode.key }

    override suspend fun setViewModeFolders(mode: ViewMode) = edit { it[Keys.VIEW_MODE_FOLDERS] = mode.key }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}

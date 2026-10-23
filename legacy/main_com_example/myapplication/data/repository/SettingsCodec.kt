package com.example.myapplication.data.repository

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.example.myapplication.domain.model.AppFontFamily
import com.example.myapplication.domain.model.AppSettings
import com.example.myapplication.domain.model.PlayerTheme
import com.example.myapplication.domain.model.SongListLayout
import com.example.myapplication.domain.model.SongSortOption
import com.example.myapplication.domain.model.ThemeMode

/**
 * Reads and writes the settings of the app.
 *
 * The codec is kept apart from the repository so the persistence can be unit tested without a
 * running `DataStore`: the keys and the fallback values live in one place.
 */
internal object SettingsCodec {

    fun decode(preferences: Preferences): AppSettings = AppSettings(
        themeMode = preferences.enumOr(Keys.THEME_MODE, ThemeMode.SYSTEM),
        dynamicColor = preferences[Keys.DYNAMIC_COLOR] ?: true,
        playerTheme = preferences.enumOr(Keys.PLAYER_THEME, PlayerTheme.GLASS),
        fontFamily = preferences.enumOr(Keys.FONT_FAMILY, AppFontFamily.SYSTEM),
        fontScale = preferences[Keys.FONT_SCALE] ?: 1f,
        listLayout = preferences.enumOr(Keys.LIST_LAYOUT, SongListLayout.DETAILED),
        animationsEnabled = preferences[Keys.ANIMATIONS] ?: true,
        glassIntensity = preferences[Keys.GLASS_INTENSITY] ?: 0.6f,
        defaultSpeed = preferences[Keys.DEFAULT_SPEED] ?: 1f,
        resumePlayback = preferences[Keys.RESUME_PLAYBACK] ?: true,
        pauseWhenHeadphonesDisconnected = preferences[Keys.PAUSE_ON_DISCONNECT] ?: true,
        gaplessPlayback = preferences[Keys.GAPLESS] ?: true,
        minimumTrackDurationSec = preferences[Keys.MIN_DURATION] ?: 0,
        excludedFolders = preferences[Keys.EXCLUDED_FOLDERS] ?: emptySet(),
        sortOption = preferences.enumOr(Keys.SORT_OPTION, SongSortOption.NAME_ASC),
        lastLibrarySync = preferences[Keys.LAST_SYNC]?.toLongOrNull() ?: 0L,
    )

    fun encode(preferences: MutablePreferences, settings: AppSettings) {
        preferences[Keys.THEME_MODE] = settings.themeMode.name
        preferences[Keys.DYNAMIC_COLOR] = settings.dynamicColor
        preferences[Keys.PLAYER_THEME] = settings.playerTheme.name
        preferences[Keys.FONT_FAMILY] = settings.fontFamily.name
        preferences[Keys.FONT_SCALE] = settings.fontScale
        preferences[Keys.LIST_LAYOUT] = settings.listLayout.name
        preferences[Keys.ANIMATIONS] = settings.animationsEnabled
        preferences[Keys.GLASS_INTENSITY] = settings.glassIntensity
        preferences[Keys.DEFAULT_SPEED] = settings.defaultSpeed
        preferences[Keys.RESUME_PLAYBACK] = settings.resumePlayback
        preferences[Keys.PAUSE_ON_DISCONNECT] = settings.pauseWhenHeadphonesDisconnected
        preferences[Keys.GAPLESS] = settings.gaplessPlayback
        preferences[Keys.MIN_DURATION] = settings.minimumTrackDurationSec
        preferences[Keys.EXCLUDED_FOLDERS] = settings.excludedFolders
        preferences[Keys.SORT_OPTION] = settings.sortOption.name
        preferences[Keys.LAST_SYNC] = settings.lastLibrarySync.toString()
    }

    private inline fun <reified T : Enum<T>> Preferences.enumOr(key: Preferences.Key<String>, fallback: T): T {
        val raw = this[key] ?: return fallback
        return runCatching { enumValueOf<T>(raw) }.getOrDefault(fallback)
    }

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val PLAYER_THEME = stringPreferencesKey("player_theme")
        val FONT_FAMILY = stringPreferencesKey("font_family")
        val FONT_SCALE = floatPreferencesKey("font_scale")
        val LIST_LAYOUT = stringPreferencesKey("list_layout")
        val ANIMATIONS = booleanPreferencesKey("animations")
        val GLASS_INTENSITY = floatPreferencesKey("glass_intensity")
        val DEFAULT_SPEED = floatPreferencesKey("default_speed")
        val RESUME_PLAYBACK = booleanPreferencesKey("resume_playback")
        val PAUSE_ON_DISCONNECT = booleanPreferencesKey("pause_on_disconnect")
        val GAPLESS = booleanPreferencesKey("gapless")
        val MIN_DURATION = intPreferencesKey("min_duration")
        val EXCLUDED_FOLDERS = stringSetPreferencesKey("excluded_folders")
        val SORT_OPTION = stringPreferencesKey("sort_option")
        val LAST_SYNC = stringPreferencesKey("last_sync")
    }
}

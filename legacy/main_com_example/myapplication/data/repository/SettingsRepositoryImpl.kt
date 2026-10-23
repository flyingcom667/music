package com.example.myapplication.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.example.myapplication.domain.model.AppSettings
import com.example.myapplication.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Preferences are stored with DataStore; every key is written on each update for simplicity. */
class SettingsRepositoryImpl(
    private val context: Context,
    scope: CoroutineScope,
) : SettingsRepository {

    override val settings: StateFlow<AppSettings> = context.settingsDataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { SettingsCodec.decode(it) }
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsDataStore.edit { preferences ->
            SettingsCodec.encode(preferences, transform(SettingsCodec.decode(preferences)))
        }
    }
}

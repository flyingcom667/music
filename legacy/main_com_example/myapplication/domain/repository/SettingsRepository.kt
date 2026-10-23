package com.example.myapplication.domain.repository

import com.example.myapplication.domain.model.AppSettings
import kotlinx.coroutines.flow.StateFlow

interface SettingsRepository {

    val settings: StateFlow<AppSettings>

    suspend fun update(transform: (AppSettings) -> AppSettings)
}

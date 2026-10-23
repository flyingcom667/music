package com.example.myapplication.domain.model

enum class ThemeMode { LIGHT, DARK, SYSTEM }

enum class PlayerTheme { GLASS, MINIMAL, ARTWORK, SOLID }

enum class AppFontFamily { SYSTEM, SANS_SERIF, SERIF, MONOSPACE }

enum class SongListLayout { DETAILED, COMPACT, ARTWORK }

enum class RepeatMode { OFF, ALL, ONE }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val playerTheme: PlayerTheme = PlayerTheme.GLASS,
    val fontFamily: AppFontFamily = AppFontFamily.SYSTEM,
    val fontScale: Float = 1f,
    val listLayout: SongListLayout = SongListLayout.DETAILED,
    val animationsEnabled: Boolean = true,
    val glassIntensity: Float = 0.6f,
    val defaultSpeed: Float = 1f,
    val resumePlayback: Boolean = true,
    val pauseWhenHeadphonesDisconnected: Boolean = true,
    val gaplessPlayback: Boolean = true,
    val minimumTrackDurationSec: Int = 0,
    val excludedFolders: Set<String> = emptySet(),
    val sortOption: SongSortOption = SongSortOption.NAME_ASC,
    val lastLibrarySync: Long = 0L,
)

val PlaybackSpeeds: List<Float> = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

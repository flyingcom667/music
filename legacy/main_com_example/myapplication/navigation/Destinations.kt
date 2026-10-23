package com.example.myapplication.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.ui.graphics.vector.ImageVector

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val PLAYLISTS = "playlists"
    const val SETTINGS = "settings"
    const val FAVORITES = "favorites"
    const val PLAYER = "player"
    const val QUEUE = "queue"

    const val PLAYLIST_DETAIL = "playlist/{playlistId}"
    const val ARG_PLAYLIST_ID = "playlistId"

    fun playlistDetail(playlistId: Long): String = "playlist/$playlistId"
}

data class BottomTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

/**
 * Bottom navigation.
 *
 * Settings moved to the top bar of the home screen and searching is part of the library page,
 * so both are no longer tabs.
 */
val bottomTabs: List<BottomTab> = listOf(
    BottomTab(Routes.HOME, "Home", Icons.Rounded.Home),
    BottomTab(Routes.LIBRARY, "Library", Icons.Rounded.LibraryMusic),
    BottomTab(Routes.PLAYLISTS, "Playlists", Icons.Rounded.PlaylistPlay),
)

/** Routes that should not show the bottom navigation bar. */
val fullScreenRoutes = setOf(Routes.PLAYER, Routes.QUEUE, Routes.FAVORITES, Routes.PLAYLIST_DETAIL)

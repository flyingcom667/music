package com.tidalmusic.player.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.annotation.StringRes
import com.tidalmusic.player.R

/** The three top-level destinations shown in the navigation bar. */
enum class TopLevelDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    HOME("home", R.string.nav_home, Icons.Rounded.Home),
    LIBRARY("library", R.string.nav_library, Icons.Rounded.LibraryMusic),
    PLAYLISTS("playlists", R.string.nav_playlists, Icons.Rounded.QueueMusic),
}

/** Routes that are not part of the bottom navigation. */
object Routes {
    const val PLAYER = "player"
    const val SETTINGS = "settings"
    const val SEARCH = "search"
    const val QUEUE = "queue"

    /** The built-in mini file manager: folders that contain songs. */
    const val FOLDERS = "folders"

    /** The audio editor; the id is the song being edited. */
    const val EDITOR = "editor/{songId}"
    const val EDITOR_ID_ARG = "songId"

    /** Collection browsers. */
    const val ALBUMS = "albums"
    const val ARTISTS = "artists"
    const val RECORDINGS = "recordings"

    /** A playlist detail screen; the id is a path argument. */
    const val PLAYLIST_DETAIL = "playlist/{playlistId}"
    const val PLAYLIST_ID_ARG = "playlistId"

    fun playlistDetail(id: Long): String = "playlist/$id"

    fun editor(id: Long): String = "editor/$id"
}

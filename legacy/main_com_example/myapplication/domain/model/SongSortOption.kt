package com.example.myapplication.domain.model

/** The sort orders offered in the "All songs" screen. */
enum class SongSortOption(val label: String) {
    NAME_ASC("Name (A-Z)"),
    NAME_DESC("Name (Z-A)"),
    RECENTLY_ADDED("Recently added"),
    RECENTLY_PLAYED("Recently played"),
    OLDEST_ADDED("Oldest added"),
    SHORTEST("Shortest duration"),
    LONGEST("Longest duration"),
    ARTIST("Artist"),
    ALBUM("Album"),
}

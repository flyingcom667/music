package com.tidalmusic.player.domain.model

/**
 * Sorting options for the library. [DateAdded] is only meaningful for the
 * "Recently Added" tab and the All Songs tab when the provider exposes a date.
 */
enum class SortOrder(val key: String) {
    TITLE("title"),
    ARTIST("artist"),
    ALBUM("album"),
    DURATION("duration"),
    DATE_ADDED("date_added");

    companion object {
        fun fromKey(key: String?): SortOrder =
            entries.firstOrNull { it.key == key } ?: TITLE
    }
}

/** Whether a sort runs ascending or descending. */
enum class SortDirection(val key: String) {
    ASCENDING("asc"),
    DESCENDING("desc");

    companion object {
        fun fromKey(key: String?): SortDirection =
            entries.firstOrNull { it.key == key } ?: ASCENDING
    }
}

/** A sort order plus its direction. */
data class SortSpec(
    val order: SortOrder = SortOrder.TITLE,
    val direction: SortDirection = SortDirection.ASCENDING,
) {
    /** True when this spec should be applied ascending, given the current sort order. */
    fun isAscending(): Boolean = direction == SortDirection.ASCENDING
}

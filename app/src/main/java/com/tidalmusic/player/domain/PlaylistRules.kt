package com.tidalmusic.player.domain

/**
 * The rules that decide what a playlist may be called and how its tracks are ordered.
 *
 * They live outside the Room repository so that they can be verified without a database and
 * so that the repository, the screen models and the tests all agree on the same behaviour.
 */
object PlaylistRules {

    /** Why a playlist name was rejected. */
    enum class NameRejection {
        /** The name was empty or only whitespace. */
        BLANK,

        /** Another playlist already uses this name. */
        DUPLICATE,
    }

    private val WHITESPACE = Regex("\\s+")

    /** The canonical form of a playlist name: trimmed and with single spaces. */
    fun normalizeName(raw: String): String = raw.trim().replace(WHITESPACE, " ")

    /**
     * Checks a name against the name that is already taken.
     *
     * @param existingName the name of the other playlist, or `null` when the name is free.
     *   The comparison ignores case, like the database query does.
     */
    fun rejectionReason(name: String, existingName: String?): NameRejection? = when {
        normalizeName(name).isEmpty() -> NameRejection.BLANK
        existingName != null && existingName.equals(name.trim(), ignoreCase = true) -> {
            NameRejection.DUPLICATE
        }

        else -> null
    }

    /**
     * Merges a requested order with the tracks the playlist actually holds.
     *
     * Tracks the caller did not mention are kept (appended at the end) so that a reorder can
     * never silently drop a track, and unknown ids are ignored.
     */
    fun mergeTrackOrder(existing: List<Long>, requested: List<Long>): List<Long> {
        val existingSet = existing.toSet()
        val ordered = requested.filter { it in existingSet }.distinct()
        val seen = ordered.toSet()
        val missing = existing.filterNot { it in seen }
        return ordered + missing
    }

    /**
     * The positions that the merged order implies.
     *
     * Positions are contiguous and start at zero, which is what the database expects.
     */
    fun positionsFor(order: List<Long>): Map<Long, Int> =
        order.mapIndexed { index, songId -> songId to index }.toMap()
}

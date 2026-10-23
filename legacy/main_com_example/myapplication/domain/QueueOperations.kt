package com.example.myapplication.domain

/**
 * Pure queue ordering helpers. They mirror the operations applied to the underlying
 * `ExoPlayer` timeline so that the current/new index maths can be unit tested without a
 * running player.
 */
object QueueOperations {

    /** Moves the item at [from] to [to], returning the new order. */
    fun <T> move(items: List<T>, from: Int, to: Int): List<T> {
        if (from == to) return items
        if (from !in items.indices) return items
        val target = to.coerceIn(0, items.lastIndex)
        val mutable = items.toMutableList()
        val item = mutable.removeAt(from)
        mutable.add(target, item)
        return mutable
    }

    /** Removes [index]; out of range indexes are ignored. */
    fun <T> removeAt(items: List<T>, index: Int): List<T> {
        if (index !in items.indices) return items
        val mutable = items.toMutableList()
        mutable.removeAt(index)
        return mutable
    }

    /** Index a "play next" insertion should use, i.e. directly after the current item. */
    fun playNextIndex(currentIndex: Int, size: Int): Int =
        (currentIndex + 1).coerceIn(0, size)

    /** Where the currently playing item ends up after a move. */
    fun currentIndexAfterMove(currentIndex: Int, from: Int, to: Int): Int = when {
        currentIndex == from -> to
        from < currentIndex && to >= currentIndex -> currentIndex - 1
        from > currentIndex && to <= currentIndex -> currentIndex + 1
        else -> currentIndex
    }

    /**
     * Ensures the item at [index] can be removed without interrupting playback: removing the
     * currently playing track would stop it, so that case returns `false`.
     */
    fun canRemove(index: Int, currentIndex: Int, size: Int): Boolean =
        index in 0 until size && (index != currentIndex || size == 1)
}

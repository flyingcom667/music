package com.tidalmusic.player.domain.model

/**
 * Explicit load state for anything that loads data. Every screen backed by data
 * renders one of [Loading], [Success], [Empty] or [Error] — never an endless spinner
 * after a failure.
 */
sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Success<T>(val data: T) : LoadState<T>
    data object Empty : LoadState<Nothing>
    data class Error(val message: String?, val cause: Throwable? = null) : LoadState<Nothing>
}

/** Convenience accessors. */
val <T> LoadState<T>.dataOrNull: T?
    get() = (this as? LoadState.Success)?.data

val LoadState<*>.isLoading: Boolean get() = this is LoadState.Loading

val LoadState<*>.isError: Boolean get() = this is LoadState.Error

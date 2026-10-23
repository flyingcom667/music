package com.tidalmusic.player.ui.viewmodel

import androidx.annotation.StringRes

/**
 * A one-shot message for a snackbar.
 *
 * Resource ids are used instead of ready-made strings so messages stay localisable.
 */
data class UiMessage(
    @StringRes val messageRes: Int,
    val args: List<Any> = emptyList(),
    val isError: Boolean = false,
)

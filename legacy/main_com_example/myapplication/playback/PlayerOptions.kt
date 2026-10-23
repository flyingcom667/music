package com.example.myapplication.playback

import com.example.myapplication.domain.model.RepeatMode

/**
 * The repeat and shuffle options of the player.
 *
 * The mapping to the `Media3` constants lives here so the behaviour can be unit tested
 * without a running player.
 */
object PlayerOptions {

    /** The repeat mode the user gets when the button is pressed again. */
    fun nextRepeatMode(current: RepeatMode): RepeatMode = when (current) {
        RepeatMode.OFF -> RepeatMode.ALL
        RepeatMode.ALL -> RepeatMode.ONE
        RepeatMode.ONE -> RepeatMode.OFF
    }

    /** The `Media3` constant of a repeat mode. */
    fun toPlayerRepeatMode(mode: RepeatMode): Int = when (mode) {
        RepeatMode.OFF -> PlayerConstants.REPEAT_MODE_OFF
        RepeatMode.ALL -> PlayerConstants.REPEAT_MODE_ALL
        RepeatMode.ONE -> PlayerConstants.REPEAT_MODE_ONE
    }

    /** The repeat mode of a `Media3` constant. */
    fun toRepeatMode(playerValue: Int): RepeatMode = when (playerValue) {
        PlayerConstants.REPEAT_MODE_ONE -> RepeatMode.ONE
        PlayerConstants.REPEAT_MODE_ALL -> RepeatMode.ALL
        else -> RepeatMode.OFF
    }

    /** The shuffle mode the user gets when the button is pressed again. */
    fun nextShuffleMode(enabled: Boolean): Boolean = !enabled
}

/**
 * The repeat constants of `Media3`.
 *
 * They are copied here so the mapping can be tested without the player library; the values are
 * the ones of `androidx.media3.common.Player`.
 */
object PlayerConstants {
    const val REPEAT_MODE_OFF = 0
    const val REPEAT_MODE_ONE = 1
    const val REPEAT_MODE_ALL = 2
}

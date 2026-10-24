package com.tidalmusic.player.ui.theme

/** Shared durations for the app's transitions. */
object MotionTokens {
    const val TopLevelTransitionMs = 60
    const val ScreenTransitionMs = 90

    /** The Now Playing screen slides up from the bottom edge; a slide needs more time than a fade. */
    const val PlayerTransitionMs = 220

    /** One full sweep of the playing gradient, in milliseconds. Calm by design. */
    const val PlayingGradientSweepMs = 24_000
}

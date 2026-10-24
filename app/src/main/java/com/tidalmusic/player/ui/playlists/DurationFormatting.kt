package com.tidalmusic.player.ui.playlists

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.tidalmusic.player.R
import com.tidalmusic.player.util.TimeFormat

/**
 * A human readable total duration such as "1 h 24 min", localized.
 *
 * The hour/minute split is computed by [TimeFormat] (pure logic, unit tested); the wording
 * comes from string resources so the abbreviations follow the device language.
 */
@Composable
internal fun formatLongDuration(millis: Long): String {
    val (hours, minutes) = remember(millis) { TimeFormat.longDurationParts(millis) }
    return when {
        hours <= 0L && minutes <= 0L -> stringResource(R.string.duration_zero_minutes)
        hours <= 0L -> stringResource(R.string.duration_minutes, minutes)
        minutes <= 0L -> stringResource(R.string.duration_hours, hours)
        else -> stringResource(R.string.duration_hours_minutes, hours, minutes)
    }
}

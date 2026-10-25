package com.tidalmusic.player.ui.components

import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tidalmusic.player.R
import com.tidalmusic.player.util.AudioOutputDetails
import com.tidalmusic.player.util.OutputCategory
import com.tidalmusic.player.util.resolveAudioOutputDetails

/**
 * The "advanced connected device" pill on the Now Playing screen.
 *
 * It appears (animated) only when the audio is routed to a personal output — wired or
 * USB headphones, Bluetooth headphones/speakers, or a hearing device — and shows the
 * device category icon plus the product name the platform reports, falling back to a
 * localized category label. It hides on the loudspeaker because "phone speaker" is not
 * information the user needs.
 */
@Composable
fun ConnectedDeviceChip(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var details by remember(context) { mutableStateOf(resolveAudioOutputDetails(context)) }

    DisposableEffect(context) {
        val audioManager = context.getSystemService(AudioManager::class.java)
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                details = resolveAudioOutputDetails(context)
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                details = resolveAudioOutputDetails(context)
            }
        }
        audioManager?.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        onDispose {
            audioManager?.unregisterAudioDeviceCallback(callback)
        }
    }

    AnimatedVisibility(
        visible = details.category != OutputCategory.SPEAKER,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier,
    ) {
        DeviceChipContent(details)
    }
}

@Composable
private fun DeviceChipContent(details: AudioOutputDetails) {
    val (icon, categoryLabel) = when (details.category) {
        OutputCategory.WIRED_HEADPHONES ->
            Icons.Rounded.Headphones to stringResource(R.string.output_wired_headphones)

        OutputCategory.BLUETOOTH_HEADPHONES ->
            Icons.Rounded.BluetoothAudio to stringResource(R.string.output_bluetooth_headphones)

        OutputCategory.BLUETOOTH_SPEAKER ->
            Icons.Rounded.BluetoothAudio to stringResource(R.string.output_bluetooth_speaker)

        OutputCategory.HEARING_AID ->
            Icons.Rounded.Hearing to stringResource(R.string.output_hearing_aid)

        OutputCategory.SPEAKER ->
            Icons.Rounded.Speaker to stringResource(R.string.output_speaker)
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(0.dp),
            )
            Text(
                text = details.deviceName ?: categoryLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

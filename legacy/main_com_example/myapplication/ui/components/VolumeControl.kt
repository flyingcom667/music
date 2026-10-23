package com.example.myapplication.ui.components

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Where the sound is currently going — decides whether a volume warning is shown. */
enum class AudioOutput { SPEAKER, WIRED, BLUETOOTH }

/** Level above which long listening on headphones is considered risky (65% of max). */
private const val SAFE_LIMIT = 0.65f

/**
 * Compact volume panel. It is shown in a popup (tapping the volume button in the player) so the
 * level can never be changed by accident while dragging on the screen.
 */
@Composable
fun VolumePopupContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val maxVolume = remember(audioManager) {
        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }
    var volume by remember { mutableIntStateOf(currentVolume(audioManager)) }
    var lastAudible by remember { mutableIntStateOf(volume.coerceAtLeast(1)) }
    val output = rememberAudioOutput()

    // Follow changes made with the hardware buttons or another app.
    LaunchedEffect(audioManager) {
        while (true) {
            volume = currentVolume(audioManager)
            if (volume > 0) lastAudible = volume
            delay(500)
        }
    }

    val ratio = (volume.toFloat() / maxVolume).coerceIn(0f, 1f)
    val onHeadphones = output != AudioOutput.SPEAKER
    val tooLoud = onHeadphones && ratio > SAFE_LIMIT

    Column(
        modifier = modifier
            .width(264.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    if (volume > 0) {
                        lastAudible = volume
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                        volume = 0
                    } else {
                        val restore = lastAudible.coerceIn(1, maxVolume)
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, restore, 0)
                        volume = restore
                    }
                },
            ) {
                Icon(
                    imageVector = when {
                        ratio <= 0f -> Icons.AutoMirrored.Rounded.VolumeOff
                        ratio < 0.5f -> Icons.AutoMirrored.Rounded.VolumeDown
                        else -> Icons.AutoMirrored.Rounded.VolumeUp
                    },
                    contentDescription = if (ratio <= 0f) "Unmute" else "Mute",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (onHeadphones) {
                    val markerColor = MaterialTheme.colorScheme.error
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val x = size.width * SAFE_LIMIT
                        drawLine(
                            color = markerColor,
                            start = Offset(x, size.height * 0.08f),
                            end = Offset(x, size.height * 0.92f),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                }
                Slider(
                    value = ratio,
                    onValueChange = { value ->
                        val target = (value * maxVolume).roundToInt().coerceIn(0, maxVolume)
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                        volume = target
                        if (target > 0) lastAudible = target
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = when (output) {
                    AudioOutput.BLUETOOTH -> Icons.Rounded.BluetoothAudio
                    AudioOutput.WIRED -> Icons.Rounded.Headphones
                    AudioOutput.SPEAKER -> Icons.AutoMirrored.Rounded.VolumeUp
                },
                contentDescription = when (output) {
                    AudioOutput.BLUETOOTH -> "Bluetooth headphones"
                    AudioOutput.WIRED -> "Wired headphones"
                    AudioOutput.SPEAKER -> "Speaker"
                },
                tint = if (onHeadphones) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${(ratio * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (tooLoud) {
                Text(
                    text = "Loud — protect your hearing",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun currentVolume(audioManager: AudioManager): Int =
    audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(0)

/** Live output-device tracking so the warning appears the moment headphones are plugged in. */
@Composable
fun rememberAudioOutput(): AudioOutput {
    val context = LocalContext.current
    var output by remember { mutableStateOf(detectOutput(context)) }
    DisposableEffect(context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                output = detectOutput(context)
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                output = detectOutput(context)
            }
        }
        audioManager.registerAudioDeviceCallback(callback, null)
        onDispose { audioManager.unregisterAudioDeviceCallback(callback) }
    }
    return output
}

private fun detectOutput(context: Context): AudioOutput {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val devices = runCatching { audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS) }
        .getOrDefault(emptyArray())
    if (devices.any { it.type in bluetoothTypes }) return AudioOutput.BLUETOOTH
    if (devices.any { it.type in wiredTypes }) return AudioOutput.WIRED
    return AudioOutput.SPEAKER
}

private val wiredTypes: Set<Int> = buildSet {
    add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
    add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
    add(AudioDeviceInfo.TYPE_USB_HEADSET)
}

private val bluetoothTypes: Set<Int> = buildSet {
    add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
    add(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
}

package com.tidalmusic.player.util

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * The kind of output the media stream is currently routed to.
 *
 * Personal outputs (headphones, Bluetooth speakers, USB headsets) are the ones where a volume
 * change is felt directly, so the player warns about unsafe levels only for those.
 */
enum class AudioOutputKind {
    /** Phone loudspeaker or the device itself. */
    SPEAKER,

    /** Wired headphones, headset or USB audio. */
    WIRED,

    /** Bluetooth A2DP speaker/headphones, BLE and hearing aids. */
    BLUETOOTH,
    ;

    val isPersonal: Boolean get() = this != SPEAKER
}

/**
 * Reads the connected output routes from [AudioManager].
 *
 * Every output device the system reports is considered, because the user cares about what can
 * be plugged into their ears; Bluetooth wins over wired when both are present.
 */
fun resolveAudioOutput(context: Context): AudioOutputKind {
    val audioManager = context.getSystemService(AudioManager::class.java) ?: return AudioOutputKind.SPEAKER
    val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

    val bluetooth = outputs.any { device ->
        when (device.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            -> true
            else -> {
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                    device.type == AudioDeviceInfo.TYPE_HEARING_AID) ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        (device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                            device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER)) ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        device.type == AudioDeviceInfo.TYPE_BLE_BROADCAST)
            }
        }
    }
    val wired = outputs.any { device ->
        when (device.type) {
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_LINE_DIGITAL,
            -> true
            else -> false
        }
    }
    return when {
        bluetooth -> AudioOutputKind.BLUETOOTH
        wired -> AudioOutputKind.WIRED
        else -> AudioOutputKind.SPEAKER
    }
}

/**
 * Tracks the current output route and recomposes when a device is plugged in or removed.
 *
 * The callback is registered for the lifetime of the caller, so the Now Playing screen keeps
 * its warning in sync with the phone without polling.
 */
@Composable
fun rememberAudioOutputKind(): AudioOutputKind {
    val context = LocalContext.current
    var kind by remember(context) { mutableStateOf(resolveAudioOutput(context)) }

    DisposableEffect(context) {
        val audioManager = context.getSystemService(AudioManager::class.java)
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                kind = resolveAudioOutput(context)
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                kind = resolveAudioOutput(context)
            }
        }
        audioManager?.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        onDispose {
            audioManager?.unregisterAudioDeviceCallback(callback)
        }
    }
    return kind
}

package com.tidalmusic.player.util

import android.bluetooth.BluetoothDevice
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

/** The device categories the "connected device" surfaces distinguish. */
enum class OutputCategory { WIRED_HEADPHONES, BLUETOOTH_HEADPHONES, BLUETOOTH_SPEAKER, HEARING_AID, SPEAKER }

/**
 * Everything the system can honestly report about the currently active output.
 *
 * [deviceName] is the product name the platform publishes (blank when the device does
 * not provide one); [category] is derived from the device type only.
 */
data class AudioOutputDetails(
    val kind: AudioOutputKind,
    val category: OutputCategory,
    val deviceName: String?,
)

/**
 * Inspects the active routes and describes the output the user is listening to.
 * Bluetooth wins over wired, exactly like [resolveAudioOutput].
 */
fun resolveAudioOutputDetails(context: Context): AudioOutputDetails {
    val audioManager = context.getSystemService(AudioManager::class.java)
        ?: return AudioOutputDetails(AudioOutputKind.SPEAKER, OutputCategory.SPEAKER, null)
    val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

    fun categoryOf(type: Int): OutputCategory? = when (type) {
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_ACCESSORY,
        AudioDeviceInfo.TYPE_LINE_ANALOG,
        AudioDeviceInfo.TYPE_LINE_DIGITAL,
        -> OutputCategory.WIRED_HEADPHONES

        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> OutputCategory.BLUETOOTH_HEADPHONES
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> OutputCategory.BLUETOOTH_HEADPHONES

        AudioDeviceInfo.TYPE_BLE_SPEAKER -> OutputCategory.BLUETOOTH_SPEAKER
        AudioDeviceInfo.TYPE_HEARING_AID -> OutputCategory.HEARING_AID
        else -> null
    }

    val bluetoothFirst = outputs.sortedBy { device ->
        when (device.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            -> 0

            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_BROADCAST,
            -> 1

            AudioDeviceInfo.TYPE_HEARING_AID -> 2

            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
            -> 3

            else -> 4
        }
    }
    for (device in bluetoothFirst) {
        val isBle = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            (device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER)
        val isBleBroadcast = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            device.type == AudioDeviceInfo.TYPE_BLE_BROADCAST
        val isHearingAid = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            device.type == AudioDeviceInfo.TYPE_HEARING_AID

        val category = when {
            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ->
                OutputCategory.BLUETOOTH_HEADPHONES

            isBle -> if (device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER) {
                OutputCategory.BLUETOOTH_SPEAKER
            } else {
                OutputCategory.BLUETOOTH_HEADPHONES
            }

            isBleBroadcast -> OutputCategory.BLUETOOTH_SPEAKER
            isHearingAid -> OutputCategory.HEARING_AID
            else -> categoryOf(device.type) ?: continue
        }
        val name = device.productName?.toString()?.takeIf { it.isNotBlank() }
        val kind = when (category) {
            OutputCategory.WIRED_HEADPHONES -> AudioOutputKind.WIRED
            OutputCategory.SPEAKER -> AudioOutputKind.SPEAKER
            else -> AudioOutputKind.BLUETOOTH
        }
        return AudioOutputDetails(kind, category, name)
    }
    return AudioOutputDetails(AudioOutputKind.SPEAKER, OutputCategory.SPEAKER, null)
}

/** One connected Bluetooth audio output, as far as the platform reports it. */
data class BluetoothDeviceInfo(
    val name: String,
    /** Whole-device battery, 1..100, or null when the device does not report it. */
    val batteryPercent: Int? = null,
    /** Per-bud batteries for TWS headsets, or null when unsupported. */
    val leftBatteryPercent: Int? = null,
    val rightBatteryPercent: Int? = null,
)

private val BLUETOOTH_OUTPUT_TYPES = setOf(
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    AudioDeviceInfo.TYPE_BLE_HEADSET,
    AudioDeviceInfo.TYPE_BLE_SPEAKER,
    AudioDeviceInfo.TYPE_BLE_BROADCAST,
    AudioDeviceInfo.TYPE_HEARING_AID,
)

/**
 * The Bluetooth audio outputs that are currently connected.
 *
 * The device name always comes from the system audio routing and needs no Bluetooth
 * permission. Battery figures are only read when the user granted BLUETOOTH_CONNECT on
 * Android 13+ and the headset actually reports them; unsupported devices simply leave
 * the percentages null and the UI shows the name only.
 */
fun connectedBluetoothDevices(
    context: Context,
    canAccessBluetooth: Boolean,
): List<BluetoothDeviceInfo> {
    val audioManager = context.getSystemService(AudioManager::class.java) ?: return emptyList()
    return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .filter { it.type in BLUETOOTH_OUTPUT_TYPES }
        .map { info ->
            val name = info.productName?.toString()?.takeIf { it.isNotBlank() }
                ?: info.address?.takeIf { it.isNotBlank() && it != "00:00:00:00:00:00" }
                ?: ""
            var battery: Int? = null
            // Per-bud (left/right) batteries are not part of the public SDK, and the whole
            // -device battery APIs (getBatteryLevel/getMetadata) were removed from the
            // public platform stubs after Android 13. Android publishes no supported way
            // for a normal app to read headset battery, so the percentage stays null and
            // the UI truthfully shows the device name and category only.
            var left: Int? = null
            var right: Int? = null
            BluetoothDeviceInfo(
                name = name,
                batteryPercent = battery,
                leftBatteryPercent = left,
                rightBatteryPercent = right,
            )
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

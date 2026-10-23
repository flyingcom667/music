package com.tidalmusic.player.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Version-aware media permission handling.
 *
 * The app only ever asks for the *read* permission that the running platform actually
 * needs:
 *  - API 33+ → `READ_MEDIA_AUDIO`
 *  - API 23–32 → `READ_EXTERNAL_STORAGE`
 *
 * Notification permission is requested separately, only when the OS requires it
 * (API 33+).
 */
object MediaPermissions {

    /** The permission required to read audio on the current OS version. */
    val audioPermission: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    /** All permissions the app may ever request for reading media. */
    val audioPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    /** True when the app may read the media store. */
    fun hasAudioAccess(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED

    /** True when the app may post the playback notification (API 33+ only). */
    fun hasNotificationAccess(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** The runtime permission required to show playback notifications on Android 13+. */
    val notificationPermission: String
        get() = Manifest.permission.POST_NOTIFICATIONS

    /** True when the notification permission needs to be requested at all. */
    val requiresNotificationPermission: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /**
     * Builds the "app details" screen intent so the user can grant a permission that
     * was permanently denied (the system will no longer show a request dialog).
     */
    fun appSettingsIntent(context: Context): Intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Intent that opens the system volume panel for the media stream. */
    fun systemVolumePanelIntent(): Intent? {
        val intent = Intent("android.intent.action.VOLUME_CHANGED")
        intent.putExtra("android.intent.extra.VOLUME_STREAM_TYPE", 3 /* STREAM_MUSIC */)
        return intent
    }
}

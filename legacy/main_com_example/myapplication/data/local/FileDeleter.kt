package com.example.myapplication.data.local

import android.app.PendingIntent
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log

/**
 * What has to happen before audio files can be removed from the device.
 */
sealed interface DeleteRequest {

    /** There is nothing to delete. */
    data object Empty : DeleteRequest

    /** The files can be deleted right away. */
    data class Ready(val uris: List<Uri>) : DeleteRequest

    /**
     * Android 11 and newer ask the user to confirm the deletion of files the app does not own.
     * [pendingIntent] starts that system dialog.
     */
    data class NeedsConsent(val uris: List<Uri>, val pendingIntent: PendingIntent) : DeleteRequest
}

/**
 * Deletes audio files from the device.
 *
 * On Android 11 and newer the system shows a confirmation dialog, so the deletion is split in
 * two: [prepare] returns the dialog to show and [delete] removes the files once the user
 * agreed. On older versions the files are deleted without any interaction.
 */
class FileDeleter(private val context: Context) {

    /**
     * Prepares the deletion of [uris].
     *
     * The returned request tells the caller whether the files can be deleted right away or
     * whether the user has to confirm first.
     */
    fun prepare(uris: List<Uri>): DeleteRequest {
        val unique = uris.distinct()
        if (unique.isEmpty()) return DeleteRequest.Empty
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val consent = runCatching {
                MediaStore.createDeleteRequest(context.contentResolver, unique)
            }.getOrNull()
            if (consent != null) return DeleteRequest.NeedsConsent(unique, consent)
            Log.w(TAG, "Unable to create the delete request, deleting directly")
        }
        return DeleteRequest.Ready(unique)
    }

    /** Removes the files and returns the URIs that are really gone. */
    fun delete(uris: List<Uri>): List<Uri> = uris.filter { uri ->
        runCatching { context.contentResolver.delete(uri, null, null) > 0 }
            .getOrElse { error ->
                Log.w(TAG, "Unable to delete $uri", error)
                false
            }
    }

    private companion object {
        const val TAG = "FileDeleter"
    }
}

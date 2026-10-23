package com.tidalmusic.player.data.local

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.os.Build
import android.app.RecoverableSecurityException
import com.tidalmusic.player.domain.model.Song
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Writes metadata back to the audio files.
 *
 * Two code paths exist because Android changed the rules in API 29:
 *
 *  - **API 29+** — the app has no direct write permission. It asks the system for a
 *    per-file write grant through [MediaStore.createWriteRequest]; the user confirms
 *    in a system dialog. Only after that grant does the update actually happen.
 *  - **API 23–28** — `WRITE_EXTERNAL_STORAGE` is an install-time permission, so the
 *    update goes straight through [android.content.ContentResolver.update].
 *
 * If the provider refuses the change the app reports failure — it never claims a
 * write succeeded when it did not.
 */
class MetadataEditor(private val context: Context) {

    /** Result of a metadata write attempt. */
    sealed interface Result {
        data object Success : Result
        data class Failure(val reason: Reason) : Result
        data class NeedsConsent(val intentSender: android.content.IntentSender) : Result

        enum class Reason {
            /** The user dismissed the system write-request dialog. */
            PERMISSION_DENIED,
            /** The media provider rejected the update (read-only entry or unsupported field). */
            PROVIDER_REJECTED,
            /** The file no longer exists. */
            NOT_FOUND,
            /** Writing is not supported on this Android version. */
            UNSUPPORTED,
        }
    }

    /**
     * Requests the user consent needed to write to [song]'s file.
     *
     * @return an [android.content.IntentSender] to launch, or `null` when no consent is
     *   needed (API < 29) or writing is unsupported.
     */
    fun createWriteRequest(song: Song): android.content.IntentSender? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            MediaStore.createWriteRequest(
                context.contentResolver,
                listOf(song.uri),
            ).intentSender
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Applies the edited fields to the media provider.
     *
     * Must only be called after the write request from [createWriteRequest] has been
     * confirmed by the user on API 29+.
     */
    suspend fun write(
        song: Song,
        title: String?,
        artist: String?,
        album: String?,
        genre: String?,
    ): Result = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            title?.trim()?.takeIf { it.isNotEmpty() }?.let { put(MediaStore.Audio.Media.TITLE, it) }
            artist?.trim()?.takeIf { it.isNotEmpty() }?.let { put(MediaStore.Audio.Media.ARTIST, it) }
            album?.trim()?.takeIf { it.isNotEmpty() }?.let { put(MediaStore.Audio.Media.ALBUM, it) }
            genre?.trim()?.takeIf { it.isNotEmpty() }?.let { put(MediaStore.Audio.Media.GENRE, it) }
        }
        if (values.size() == 0) return@withContext Result.Failure(Result.Reason.PROVIDER_REJECTED)

        try {
            val rows = context.contentResolver.update(song.uri, values, null, null)
            if (rows > 0) Result.Success else Result.Failure(Result.Reason.PROVIDER_REJECTED)
        } catch (e: SecurityException) {
            Result.Failure(Result.Reason.PERMISSION_DENIED)
        } catch (e: IllegalArgumentException) {
            Result.Failure(Result.Reason.NOT_FOUND)
        } catch (e: Exception) {
            Result.Failure(Result.Reason.PROVIDER_REJECTED)
        }
    }

    /**
     * Builds the system delete request for [song] (API 30+).
     *
     * On API 29 `MediaStore.createDeleteRequest` is not available, so the caller
     * falls back to `ContentResolver.delete`, which requires the legacy write
     * permission on those versions.
     */
    fun createDeleteRequest(song: Song): android.content.IntentSender? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return MediaStore.createDeleteRequest(context.contentResolver, listOf(song.uri)).intentSender
    }

    /** Deletes the file directly. Only valid on API < 30 without a system grant. */
    suspend fun deleteDirectly(song: Song): Result = withContext(Dispatchers.IO) {
        try {
            val rows = context.contentResolver.delete(song.uri, null, null)
            if (rows > 0) Result.Success else Result.Failure(Result.Reason.NOT_FOUND)
        } catch (e: RecoverableSecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Result.NeedsConsent(e.userAction.actionIntent.intentSender)
            } else {
                Result.Failure(Result.Reason.PERMISSION_DENIED)
            }
        } catch (e: SecurityException) {
            Result.Failure(Result.Reason.PERMISSION_DENIED)
        } catch (e: Exception) {
            Result.Failure(Result.Reason.NOT_FOUND)
        }
    }

    /** Builds the system delete request for several songs at once. */
    fun createDeleteRequest(songs: List<Song>): android.content.IntentSender? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || songs.isEmpty()) return null
        return MediaStore.createDeleteRequest(
            context.contentResolver,
            songs.map { it.uri },
        ).intentSender
    }
}

/**
 * Bridges the system `IntentSender` dialog to a coroutine.
 *
 * The Activity result launcher must be started from a UI callback, so the caller
 * launches the sender and then awaits [awaitConsent] before applying the write.
 */
class ConsentRequest {
    private val deferred = CompletableDeferred<Boolean>()

    /** Completes the request with the user's answer. */
    fun complete(granted: Boolean) {
        if (!deferred.isCompleted) deferred.complete(granted)
    }

    /** Suspends until the user answers the system dialog. */
    suspend fun awaitConsent(): Boolean = deferred.await()
}

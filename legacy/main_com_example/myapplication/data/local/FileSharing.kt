package com.example.myapplication.data.local

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.myapplication.domain.model.Song
import java.io.File

/**
 * Shares audio files with other apps.
 *
 * The files are handed out through a [FileProvider] so the receiving app gets a real file it
 * can read without asking for a permission of its own. When the file cannot be resolved the
 * MediaStore URI is shared instead, with the read permission granted to the receiver.
 */
object FileSharing {

    private const val AUTHORITY_SUFFIX = ".fileprovider"
    private const val DEFAULT_MIME = "audio/*"

    /** Opens the system share sheet for [songs]. */
    fun share(context: Context, songs: List<Song>) {
        if (songs.isEmpty()) return
        val uris = songs.mapNotNull { shareUri(context, it) }
        if (uris.isEmpty()) return

        val mimeType = songs.firstOrNull { !it.mimeType.isNullOrBlank() }?.mimeType ?: DEFAULT_MIME
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uris.first())
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mimeType
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
        }
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        val chooser = Intent.createChooser(send, null).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    /** The URI the receiving app gets: a file provider URI when possible, the media URI else. */
    private fun shareUri(context: Context, song: Song): Uri? {
        val path = resolvePath(context, song)
        if (path != null) {
            val file = File(path)
            if (file.exists() && file.canRead()) {
                val providerUri = runCatching {
                    FileProvider.getUriForFile(
                        context,
                        context.packageName + AUTHORITY_SUFFIX,
                        file,
                    )
                }.getOrNull()
                if (providerUri != null) return providerUri
            }
        }
        return runCatching { Uri.parse(song.uri) }.getOrNull()
    }

    /** The absolute path of the file, used to build the file provider URI. */
    @Suppress("DEPRECATION")
    private fun resolvePath(context: Context, song: Song): String? = runCatching {
        context.contentResolver
            .query(
                Uri.parse(song.uri),
                arrayOf(MediaStore.MediaColumns.DATA),
                null,
                null,
                null,
            )
            ?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
            }
    }.getOrNull()
}

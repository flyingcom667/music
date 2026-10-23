package com.example.myapplication.data.local

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bit rate / sample rate details for the "song details" sheet. */
data class AudioFileInfo(
    val bitrate: Int,
    val sampleRate: Int,
    val channels: Int,
    val mimeType: String?,
)

class AudioFileProbe(private val context: Context) {

    suspend fun probe(uri: String): AudioFileInfo? = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, Uri.parse(uri), null)
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (!mime.startsWith("audio/")) continue
                return@withContext AudioFileInfo(
                    bitrate = format.intOrZero(MediaFormat.KEY_BIT_RATE),
                    sampleRate = format.intOrZero(MediaFormat.KEY_SAMPLE_RATE),
                    channels = format.intOrZero(MediaFormat.KEY_CHANNEL_COUNT),
                    mimeType = mime,
                )
            }
            null
        } catch (error: Exception) {
            Log.w(TAG, "Unable to probe $uri", error)
            null
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun MediaFormat.intOrZero(key: String): Int =
        if (containsKey(key)) runCatching { getInteger(key) }.getOrDefault(0) else 0

    companion object {
        private const val TAG = "AudioFileProbe"
    }
}

package com.tidalmusic.player.audioeditor

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.tidalmusic.player.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.min

/**
 * Computes real waveform amplitudes from decoded audio samples.
 *
 * The file is decoded once on [Dispatchers.IO]; the maximum absolute sample of every
 * frame is accumulated into [BAR_COUNT] buckets, peak-normalized to 0..100 and returned.
 * Nothing here is fabricated: if the file cannot be decoded the extractor fails and the
 * UI keeps its "analyzing" placeholder instead of drawing made-up bars.
 */
object WaveformExtractor {

    const val BAR_COUNT = 120

    /**
     * Decodes [song] and returns [barCount] relative amplitudes in 0..100.
     *
     * @throws Exception when the audio track is missing or cannot be decoded; the caller
     *   decides how to degrade.
     */
    suspend fun extract(
        context: Context,
        song: Song,
        barCount: Int = BAR_COUNT,
    ): IntArray = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, song.uri, null)
            var trackIndex = -1
            var trackFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(i)
                if (candidate.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    trackIndex = i
                    trackFormat = candidate
                    break
                }
            }
            val format = requireNotNull(trackFormat) { "no_audio_track" }
            extractor.selectTrack(trackIndex)
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val durationUs = format.getLong(MediaFormat.KEY_DURATION)

            val mime = requireNotNull(format.getString(MediaFormat.KEY_MIME))
            val decoder = MediaCodec.createDecoderByType(mime).also { codec = it }
            decoder.configure(format, null, null, 0)
            decoder.start()

            // Per-bucket maxima are collected at the file's own resolution and folded
            // into barCount buckets at the end, so the estimate does not need to be exact.
            val estimatedFrames = (durationUs * sampleRate / 1_000_000L).coerceAtLeast(1L)
            val bucketCount = maxOf(barCount * 8, 1024)
            val maxima = FloatArray(bucketCount)
            var peak = 0f
            var framesSeen = 0L

            var sawInputEos = false
            var sawOutputEos = false
            val info = MediaCodec.BufferInfo()
            while (!sawOutputEos) {
                coroutineContext.ensureActive()
                if (!sawInputEos) {
                    val inIndex = decoder.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inIndex)!!
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(
                                inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            sawInputEos = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = decoder.dequeueOutputBuffer(info, 10_000)
                if (outIndex >= 0) {
                    val outFormat = decoder.outputFormat
                    val channels = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    val pcmEncoding = outFormat.getInteger(
                        MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT,
                    )
                    val outBuffer = decoder.getOutputBuffer(outIndex)!!
                    val buffer = outBuffer.duplicate()
                    buffer.position(info.offset)
                    buffer.limit(info.offset + info.size)

                    if (pcmEncoding == AudioFormat.ENCODING_PCM_16BIT) {
                        val shortCount = info.size / 2
                        val shorts = ShortArray(shortCount)
                        buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
                        var s = 0
                        // One sample per frame (channel 0) keeps the scan cheap while still
                        // reflecting the envelope of the audio.
                        while (s + channels <= shortCount) {
                            val norm = abs(shorts[s].toInt()) / 32768f
                            val bucket = (framesSeen * bucketCount / estimatedFrames)
                                .toInt()
                                .coerceIn(0, bucketCount - 1)
                            if (norm > maxima[bucket]) maxima[bucket] = norm
                            if (norm > peak) peak = norm
                            framesSeen++
                            s += channels
                        }
                    } else {
                        val floatCount = info.size / 4
                        val floats = FloatArray(floatCount)
                        buffer.order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer().get(floats)
                        var f = 0
                        while (f + channels <= floatCount) {
                            val norm = abs(floats[f])
                            val bucket = (framesSeen * bucketCount / estimatedFrames)
                                .toInt()
                                .coerceIn(0, bucketCount - 1)
                            if (norm > maxima[bucket]) maxima[bucket] = norm
                            if (norm > peak) peak = norm
                            framesSeen++
                            f += channels
                        }
                    }
                    decoder.releaseOutputBuffer(outIndex, false)
                }
            }

            // Fold the fine buckets into bars, normalize against the file's own peak.
            val bars = IntArray(barCount)
            if (peak > 0.0001f) {
                for (b in 0 until barCount) {
                    val from = b * bucketCount / barCount
                    val to = ((b + 1) * bucketCount / barCount).coerceAtMost(bucketCount)
                    var max = 0f
                    for (k in from until to) {
                        if (maxima[k] > max) max = maxima[k]
                    }
                    bars[b] = ((max / peak) * 100f).toInt().coerceIn(0, 100)
                }
            }
            bars
        } finally {
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }
}

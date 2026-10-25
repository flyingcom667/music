package com.tidalmusic.player.audioeditor

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.ParcelFileDescriptor
import com.tidalmusic.player.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.min

/** The container/codec an edit is exported to. */
enum class ExportFormat(val mimeType: String, val fileExtension: String) {
    /** PCM WAV: universally supported, larger files, no encoder required. */
    WAV("audio/wav", "wav"),

    /** AAC in an MP4/M4A container: the standard compressed format Android encodes natively. */
    M4A("audio/mp4", "m4a"),
}

/** Outcome details of a finished export. */
data class ExportResult(
    val frameCount: Long,
    val sampleRate: Int,
    val channels: Int,
    val appliedGain: Float,
)

/**
 * The audio editing engine: trims, combines, applies volume and normalizes, then exports
 * to WAV or M4A.
 *
 * Built entirely on the platform's own [MediaExtractor]/[MediaCodec]/[MediaMuxer] — no
 * external processing library. Any format Android can decode (MP3, M4A/AAC, WAV, OGG,
 * FLAC, …) can be a source; exports are written as 16-bit PCM WAV or AAC-LC in M4A.
 *
 * The pipeline runs in two passes over a temporary PCM file in the app cache:
 *  1. decode (with sample-accurate trim boundaries and multi-source concatenation),
 *  2. apply gain/normalize while encoding to the requested target.
 * This keeps memory flat for long files and makes normalize a true peak-scan.
 */
class AudioEditorEngine {

    /** Result of pass 1: the raw 16-bit PCM waiting in the temp file. */
    private data class DecodedPcm(
        val file: File,
        val sampleRate: Int,
        val channels: Int,
        val frameCount: Long,
        val peak: Float,
    )

    /**
     * Trims ([startMs]..[endMs] of the single source), concatenates [sources] when several
     * are given, applies [gainFactor] and/or peak-normalization, and writes the result to
     * the provided target.
     *
     * @param targetStream used for [ExportFormat.WAV]; must not be null then.
     * @param targetPfd used for [ExportFormat.M4A]; must not be null then.
     */
    suspend fun export(
        context: Context,
        sources: List<Song>,
        startMs: Long,
        endMs: Long,
        gainFactor: Float,
        normalize: Boolean,
        format: ExportFormat,
        targetStream: OutputStream?,
        targetPfd: ParcelFileDescriptor?,
        onProgress: suspend (Float) -> Unit,
    ): ExportResult = withContext(Dispatchers.IO) {
        require(sources.isNotEmpty()) { "no source songs" }
        require(
            (format == ExportFormat.WAV && targetStream != null) ||
                (format == ExportFormat.M4A && targetPfd != null),
        ) { "output target does not match format" }

        val tempFile = File.createTempFile("audiofy_edit", ".pcm", context.cacheDir)
        try {
            var meta: DecodedPcm? = null
            val perSource = 0.65f / sources.size
            sources.forEachIndexed { index, song ->
                val isTrimmedSource = index == 0 && sources.size == 1
                val from = if (isTrimmedSource) startMs else 0L
                val to = if (isTrimmedSource) endMs else Long.MAX_VALUE
                meta = decodeToTemp(
                    context = context,
                    source = song,
                    startMs = from,
                    endMs = to,
                    tempFile = tempFile,
                    append = index > 0,
                    previous = meta,
                    progressOffset = index * perSource,
                    progressSpan = perSource,
                    onProgress = onProgress,
                )
            }
            val pcm = requireNotNull(meta)

            val effectiveGain = when {
                normalize && pcm.peak > 0.001f -> (0.98f / pcm.peak).coerceIn(0.05f, 8f)
                else -> gainFactor
            }

            when (format) {
                ExportFormat.WAV -> writeWav(pcm, effectiveGain, requireNotNull(targetStream), onProgress)
                ExportFormat.M4A -> writeM4a(pcm, effectiveGain, requireNotNull(targetPfd), onProgress)
            }
            ExportResult(
                frameCount = pcm.frameCount,
                sampleRate = pcm.sampleRate,
                channels = pcm.channels,
                appliedGain = effectiveGain,
            )
        } finally {
            tempFile.delete()
        }
    }

    // ------------------------------------------------------------------ pass 1: decode

    private suspend fun decodeToTemp(
        context: Context,
        source: Song,
        startMs: Long,
        endMs: Long,
        tempFile: File,
        append: Boolean,
        previous: DecodedPcm?,
        progressOffset: Float,
        progressSpan: Float,
        onProgress: suspend (Float) -> Unit,
    ): DecodedPcm = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, source.uri, null)
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
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val durationUs = format.getLong(MediaFormat.KEY_DURATION)

            if (append && previous != null &&
                (previous.sampleRate != sampleRate || previous.channels != channels)
            ) {
                // Combine sources must share a PCM layout; resampling is out of scope, so
                // mismatched sources are rejected with a clear error instead of producing
                // sped-up audio.
                error("combine_sample_mismatch")
            }

            val mime = requireNotNull(format.getString(MediaFormat.KEY_MIME))
            val decoder = MediaCodec.createDecoderByType(mime).also { codec = it }
            decoder.configure(format, null, null, 0)
            decoder.start()

            val startUs = startMs * 1000
            val endUs = if (endMs in 1 until Long.MAX_VALUE) endMs * 1000 else durationUs
            if (startUs > 0) {
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            }

            val output = FileOutputStream(tempFile, append)
            val copyBuffer = ByteArray(64 * 1024)
            var frameCount = previous?.frameCount ?: 0L
            var writtenFrames = 0L
            var peak = previous?.peak ?: 0f
            var sampleRateNow = sampleRate
            var channelsNow = channels

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
                    val outBuffer = decoder.getOutputBuffer(outIndex)!!
                    val outFormat = decoder.outputFormat
                    sampleRateNow = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channelsNow = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    val pcmEncoding = outFormat.getInteger(
                        MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT,
                    )
                    val bytesPerSample = if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
                    val bytesPerFrame = bytesPerSample * channelsNow
                    val framesInBuffer = info.size / bytesPerFrame
                    val firstFrameUs = info.presentationTimeUs

                    // Sample-accurate trim: drop frames before startUs and after endUs.
                    var skipFrames = 0L
                    var keepFrames = framesInBuffer.toLong()
                    if (firstFrameUs < startUs) {
                        skipFrames = min(
                            (startUs - firstFrameUs) * sampleRateNow / 1_000_000,
                            framesInBuffer.toLong(),
                        )
                        keepFrames -= skipFrames
                    }
                    if (endUs in 1 until Long.MAX_VALUE) {
                        val framesUntilEnd =
                            (endUs - firstFrameUs) * sampleRateNow / 1_000_000 - writtenFrames
                        when {
                            framesUntilEnd <= 0 -> keepFrames = 0
                            writtenFrames > 0 -> keepFrames = min(keepFrames, framesUntilEnd)
                            firstFrameUs >= startUs -> keepFrames = min(keepFrames, framesUntilEnd)
                        }
                    }

                    if (keepFrames > 0) {
                        val offsetBytes = (skipFrames * bytesPerFrame).toInt()
                        val copyBytes = (keepFrames * bytesPerFrame).toInt()
                        outBuffer.position(info.offset + offsetBytes)
                        outBuffer.limit(info.offset + offsetBytes + copyBytes)
                        if (bytesPerSample == 2) {
                            while (outBuffer.remaining() > 0) {
                                val n = min(outBuffer.remaining(), copyBuffer.size)
                                outBuffer.get(copyBuffer, 0, n)
                                var i = 0
                                while (i + 1 < n) {
                                    val lo = copyBuffer[i].toInt() and 0xFF
                                    val hi = copyBuffer[i + 1].toInt()
                                    val sample = ((hi shl 8) or lo).toShort().toInt()
                                    val norm = abs(sample) / 32768f
                                    if (norm > peak) peak = norm
                                    i += 2
                                }
                                output.write(copyBuffer, 0, n)
                            }
                        } else {
                            // Float PCM output: convert to 16-bit while tracking the peak.
                            val floats = copyBytes / 4
                            val floatBuf = ByteBuffer
                                .allocate(copyBytes)
                                .order(ByteOrder.nativeOrder())
                            floatBuf.put(outBuffer)
                            floatBuf.flip()
                            val shortBytes = ByteArray(floats * 2)
                            for (f in 0 until floats) {
                                val v = floatBuf.float
                                val s = (v.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                                shortBytes[f * 2] = (s.toInt() and 0xFF).toByte()
                                shortBytes[f * 2 + 1] = ((s.toInt() shr 8) and 0xFF).toByte()
                                val norm = abs(v)
                                if (norm > peak) peak = norm
                            }
                            output.write(shortBytes)
                        }
                        writtenFrames += keepFrames
                        frameCount += keepFrames
                    }
                    decoder.releaseOutputBuffer(outIndex, false)
                    if (endUs in 1 until Long.MAX_VALUE &&
                        writtenFrames > 0 &&
                        firstFrameUs + framesInBuffer * 1_000_000L / sampleRateNow >= endUs
                    ) {
                        sawOutputEos = true
                    }
                    val spanFrames = maxOf(
                        1L,
                        (endUs - startUs) * sampleRateNow / 1_000_000,
                    )
                    onProgress(
                        progressOffset + progressSpan *
                            min(1f, writtenFrames.toFloat() / spanFrames),
                    )
                }
            }
            output.close()
            DecodedPcm(
                file = tempFile,
                sampleRate = sampleRateNow,
                channels = channelsNow,
                frameCount = frameCount,
                peak = peak,
            )
        } finally {
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    // ------------------------------------------------------------------ pass 2: targets

    private suspend fun writeWav(
        pcm: DecodedPcm,
        gain: Float,
        target: OutputStream,
        onProgress: suspend (Float) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val bytesPerFrame = pcm.channels * 2
        val dataLength = pcm.frameCount * bytesPerFrame
        target.write(wavHeader(dataLength, pcm.sampleRate, pcm.channels))

        val buffer = ByteArray(64 * 1024)
        pcm.file.inputStream().buffered().use { input ->
            var processed = 0L
            while (true) {
                coroutineContext.ensureActive()
                val n = input.read(buffer)
                if (n < 0) break
                if (gain != 1f) {
                    var i = 0
                    while (i + 1 < n) {
                        val lo = buffer[i].toInt() and 0xFF
                        val hi = buffer[i + 1].toInt()
                        val sample = ((hi shl 8) or lo).toShort().toInt()
                        val scaled = (sample * gain).toInt()
                            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        buffer[i] = (scaled and 0xFF).toByte()
                        buffer[i + 1] = ((scaled shr 8) and 0xFF).toByte()
                        i += 2
                    }
                }
                target.write(buffer, 0, n)
                processed += n
                onProgress(0.65f + 0.35f * min(1f, processed.toFloat() / dataLength.toFloat()))
            }
        }
        target.flush()
    }

    private suspend fun writeM4a(
        pcm: DecodedPcm,
        gain: Float,
        targetPfd: ParcelFileDescriptor,
        onProgress: suspend (Float) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        val muxer = MediaMuxer(targetPfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            val encodeFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC, pcm.sampleRate, pcm.channels,
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, if (pcm.channels > 1) 192_000 else 128_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)
            }
            encoder.configure(encodeFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            var muxerTrack = -1
            var sawInputEos = false
            var sawOutputEos = false
            val info = MediaCodec.BufferInfo()
            val inputBytes = ByteArray(64 * 1024)
            var processedBytes = 0L
            val bytesPerFrame = pcm.channels * 2
            val totalBytes = pcm.frameCount * bytesPerFrame

            pcm.file.inputStream().buffered().use { input ->
                while (!sawOutputEos) {
                    coroutineContext.ensureActive()
                    if (!sawInputEos) {
                        val inIndex = encoder.dequeueInputBuffer(10_000)
                        if (inIndex >= 0) {
                            val inputBuffer = encoder.getInputBuffer(inIndex)!!
                            inputBuffer.clear()
                            val read = input.read(inputBytes)
                            if (read < 0) {
                                encoder.queueInputBuffer(
                                    inIndex, 0, 0,
                                    processedBytes * 1_000_000L /
                                        (pcm.sampleRate.toLong() * bytesPerFrame),
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                                )
                                sawInputEos = true
                            } else {
                                if (gain != 1f) {
                                    var i = 0
                                    while (i + 1 < read) {
                                        val lo = inputBytes[i].toInt() and 0xFF
                                        val hi = inputBytes[i + 1].toInt()
                                        val sample = ((hi shl 8) or lo).toShort().toInt()
                                        val scaled = (sample * gain).toInt()
                                            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                                        inputBytes[i] = (scaled and 0xFF).toByte()
                                        inputBytes[i + 1] = ((scaled shr 8) and 0xFF).toByte()
                                        i += 2
                                    }
                                }
                                val presentationUs = processedBytes * 1_000_000L /
                                    (pcm.sampleRate.toLong() * bytesPerFrame)
                                encoder.queueInputBuffer(inIndex, 0, read, presentationUs, 0)
                                processedBytes += read
                                onProgress(
                                    0.65f + 0.35f * min(1f, processedBytes.toFloat() / totalBytes.toFloat()),
                                )
                            }
                        }
                    }
                    val outIndex = encoder.dequeueOutputBuffer(info, 10_000)
                    when {
                        outIndex >= 0 -> {
                            // (subject-less when; the codec-constant branches compare explicitly)
                            if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                muxerTrack = muxer.addTrack(encoder.outputFormat)
                                muxer.start()
                                info.size = 0
                            }
                            if (info.size > 0 && muxerTrack >= 0) {
                                val outBuffer = encoder.getOutputBuffer(outIndex)!!
                                muxer.writeSampleData(muxerTrack, outBuffer, info)
                            }
                            encoder.releaseOutputBuffer(outIndex, false)
                            if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                sawOutputEos = true
                            }
                        }

                        outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            muxerTrack = muxer.addTrack(encoder.outputFormat)
                            muxer.start()
                        }

                        else -> Unit // INFO_TRY_AGAIN_LATER
                    }
                }
            }
            muxer.stop()
        } finally {
            runCatching { encoder.release() }
            runCatching { muxer.release() }
            runCatching { targetPfd.close() }
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Standard 44-byte RIFF header for 16-bit PCM. */
    private fun wavHeader(dataLength: Long, sampleRate: Int, channels: Int): ByteArray {
        val byteRate = sampleRate * channels * 2
        val blockAlign = channels * 2
        return ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt((36 + dataLength).toInt())
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16) // fmt chunk size
            putShort(1) // PCM
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(16) // bits per sample
            put("data".toByteArray())
            putInt(dataLength.toInt())
        }.array()
    }
}

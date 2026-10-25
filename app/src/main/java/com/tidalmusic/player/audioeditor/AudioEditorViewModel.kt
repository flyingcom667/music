package com.tidalmusic.player.audioeditor

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tidalmusic.player.R
import com.tidalmusic.player.domain.model.Song
import com.tidalmusic.player.ui.viewmodel.UiMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Everything the audio editor screen shows. */
data class AudioEditorUiState(
    val song: Song,
    val durationMs: Long = 0L,
    val startMs: Long = 0L,
    val endMs: Long = 0L,
    val combineMode: Boolean = false,
    val selectedCombineIds: Set<Long> = emptySet(),
    val format: ExportFormat = ExportFormat.M4A,
    val normalize: Boolean = false,
    val gainPercent: Int = 100,
    val isPreviewing: Boolean = false,
    /** Null while idle; 0f..1f while the export runs. */
    val exportProgress: Float? = null,
    val exportErrorRes: Int? = null,
    val exportDone: Boolean = false,
    val lastExportAppliedGain: Int? = null,
)

/**
 * The audio editor screen model: trim boundaries, segment preview, combine selection and
 * the export job with progress and cancellation.
 *
 * The segment preview uses its own short-lived Media3 player — a deliberate exception to
 * the single-playback-owner rule, because previewing a cut must never disturb the queue
 * the main controller is playing.
 */
class AudioEditorViewModel(
    private val appContext: Context,
    private val engine: AudioEditorEngine,
    val song: Song,
    val candidates: List<Song>,
) : ViewModel() {

    private val _state = MutableStateFlow(AudioEditorUiState(song = song))
    val state: StateFlow<AudioEditorUiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    private var previewPlayer: androidx.media3.exoplayer.ExoPlayer? = null
    private var exportJob: Job? = null

    init {
        viewModelScope.launch {
            val duration = withContext(Dispatchers.IO) { probeDurationMs(song) }
            _state.update {
                it.copy(
                    durationMs = duration,
                    startMs = 0L,
                    endMs = duration,
                )
            }
        }
    }

    // ------------------------------------------------------------------ trimming

    fun setStart(ms: Long) = _state.update {
        it.copy(startMs = ms.coerceIn(0L, it.endMs - MIN_SPAN_MS))
    }

    fun setEnd(ms: Long) = _state.update {
        it.copy(endMs = ms.coerceIn(it.startMs + MIN_SPAN_MS, it.durationMs))
    }

    fun setCombineMode(enabled: Boolean) = _state.update {
        it.copy(combineMode = enabled, selectedCombineIds = if (enabled) it.selectedCombineIds else emptySet())
    }

    fun toggleCombineCandidate(songId: Long) = _state.update {
        it.copy(
            selectedCombineIds = if (songId in it.selectedCombineIds) {
                it.selectedCombineIds - songId
            } else {
                it.selectedCombineIds + songId
            },
        )
    }

    fun setFormat(format: ExportFormat) = _state.update { it.copy(format = format) }

    fun setNormalize(enabled: Boolean) = _state.update { it.copy(normalize = enabled) }

    fun setGainPercent(percent: Int) = _state.update { it.copy(gainPercent = percent) }

    // ------------------------------------------------------------------ preview

    fun startPreview() {
        val state = _state.value
        stopPreview()
        val player = androidx.media3.exoplayer.ExoPlayer.Builder(appContext).build().also {
            previewPlayer = it
        }
        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) _state.update { it.copy(isPreviewing = false) }
            }
        })
        val item = androidx.media3.common.MediaItem.Builder()
            .setUri(song.uri)
            .setClippingConfiguration(
                androidx.media3.common.MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(state.startMs)
                    .setEndPositionMs(state.endMs)
                    .build(),
            )
            .build()
        player.setMediaItem(item)
        player.prepare()
        player.play()
        _state.update { it.copy(isPreviewing = true) }
    }

    fun stopPreview() {
        previewPlayer?.run {
            stop()
            release()
        }
        previewPlayer = null
        _state.update { it.copy(isPreviewing = false) }
    }

    // ------------------------------------------------------------------ export

    fun export(target: android.net.Uri) {
        val state = _state.value
        if (state.exportProgress != null) return
        stopPreview()
        exportJob = viewModelScope.launch {
            _state.update { it.copy(exportProgress = 0f, exportErrorRes = null, exportDone = false) }
            try {
                val sources = buildList {
                    add(song)
                    if (state.combineMode) {
                        candidates
                            .filter { it.id in state.selectedCombineIds && it.id != song.id }
                            .forEach { add(it) }
                    }
                }
                val result = engine.export(
                    context = appContext,
                    sources = sources,
                    startMs = if (state.combineMode) 0L else state.startMs,
                    endMs = if (state.combineMode) Long.MAX_VALUE else state.endMs,
                    gainFactor = state.gainPercent / 100f,
                    normalize = state.normalize,
                    format = state.format,
                    targetStream = if (state.format == ExportFormat.WAV) {
                        appContext.contentResolver.openOutputStream(target, "wt")
                    } else {
                        null
                    },
                    targetPfd = if (state.format == ExportFormat.M4A) {
                        appContext.contentResolver.openFileDescriptor(target, "rw")
                    } else {
                        null
                    },
                ) { progress ->
                    _state.update { it.copy(exportProgress = progress.coerceIn(0f, 1f)) }
                }
                _state.update {
                    it.copy(
                        exportProgress = null,
                        exportDone = true,
                        lastExportAppliedGain = (result.appliedGain * 100).toInt(),
                    )
                }
            } catch (error: CancellationException) {
                _state.update { it.copy(exportProgress = null) }
                throw error
            } catch (error: Exception) {
                _state.update {
                    it.copy(exportProgress = null, exportErrorRes = errorResFor(error))
                }
            }
        }
    }

    fun cancelExport() {
        exportJob?.cancel()
        exportJob = null
        _state.update { it.copy(exportProgress = null) }
    }

    fun dismissExportError() = _state.update { it.copy(exportErrorRes = null) }

    private fun errorResFor(error: Exception): Int = when (error.message) {
        "combine_sample_mismatch" -> R.string.editor_error_combine_mismatch
        "no_audio_track" -> R.string.editor_error_no_audio_track
        else -> R.string.editor_error_generic
    }

    private suspend fun probeDurationMs(song: Song): Long = withContext(Dispatchers.IO) {
        if (song.durationMs > 0L) return@withContext song.durationMs
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(appContext, song.uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: song.durationMs
        } catch (_: Exception) {
            song.durationMs
        } finally {
            runCatching { retriever.release() }
        }
    }

    override fun onCleared() {
        stopPreview()
        exportJob?.cancel()
    }

    private companion object {
        const val MIN_SPAN_MS = 1_000L
    }
}

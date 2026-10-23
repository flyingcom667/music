package com.example.myapplication.ui.viewmodel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.local.ExternalAudioReader
import com.example.myapplication.playback.PlaybackController
import com.example.myapplication.playback.PlaybackProgress
import com.example.myapplication.playback.PlaybackUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Backs the audio preview that is shown when a file is opened from a file manager.
 *
 * The file is played with the regular playback engine, so the media notification and the
 * system media controls work exactly like for the library.
 */
class PreviewViewModel(
    private val reader: ExternalAudioReader,
    private val playbackController: PlaybackController,
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val error: String? = null,
        val title: String = "",
        val artist: String? = null,
        val album: String? = null,
        val durationMs: Long = 0L,
        val mimeType: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _artwork = MutableStateFlow<Bitmap?>(null)
    val artwork: StateFlow<Bitmap?> = _artwork.asStateFlow()

    val playback: StateFlow<PlaybackUiState> = playbackController.state
    val progress: StateFlow<PlaybackProgress> = playbackController.progress

    private var currentUri: String? = null

    /** The media id the preview file gets in the playback engine. */
    fun mediaId(): Long = currentUri?.let { PlaybackController.externalMediaId(it) } ?: 0L

    /** True when the preview file is the one that is currently loaded in the engine. */
    fun isCurrentItem(): Boolean {
        val uri = currentUri ?: return false
        return playbackController.state.value.mediaId == PlaybackController.externalMediaId(uri)
    }

    /** Reads the metadata of [uri] and shows it in the preview. */
    fun load(uri: String) {
        if (uri == currentUri) return
        currentUri = uri
        _state.value = UiState(isLoading = true)
        _artwork.value = null
        viewModelScope.launch {
            val result = reader.read(uri.toUri())
            result.onSuccess { audio ->
                _state.value = UiState(
                    isLoading = false,
                    title = audio.title,
                    artist = audio.artist,
                    album = audio.album,
                    durationMs = audio.durationMs,
                    mimeType = audio.mimeType,
                )
                _artwork.value = audio.artwork?.let { decodeArtwork(it) }
            }.onFailure { error ->
                _state.value = UiState(
                    isLoading = false,
                    error = error.message?.takeIf { it.isNotBlank() }
                        ?: "This file cannot be opened",
                )
            }
        }
    }

    /** Starts the preview or pauses it when it is already playing. */
    fun toggle() {
        val uri = currentUri ?: return
        if (isCurrentItem()) {
            playbackController.togglePlayPause()
        } else {
            val current = _state.value
            playbackController.playExternal(
                uri = uri,
                title = current.title,
                artist = current.artist,
                album = current.album,
                durationMs = current.durationMs,
            )
        }
    }

    fun seekTo(positionMs: Long) {
        playbackController.seekTo(positionMs)
    }

    /** Releases the playback of the preview file. */
    fun stop() {
        if (isCurrentItem()) playbackController.stopAndDismiss()
    }

    private suspend fun decodeArtwork(bytes: ByteArray): Bitmap? = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > MAX_ARTWORK_SIZE || bounds.outHeight / sample > MAX_ARTWORK_SIZE) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private companion object {
        const val MAX_ARTWORK_SIZE = 512
    }
}

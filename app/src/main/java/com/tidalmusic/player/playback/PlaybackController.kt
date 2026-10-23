package com.tidalmusic.player.playback

import com.tidalmusic.player.domain.model.PlaybackProgress
import com.tidalmusic.player.domain.model.PlaybackState
import com.tidalmusic.player.domain.model.QueueItem
import com.tidalmusic.player.domain.model.RepeatMode
import com.tidalmusic.player.domain.model.Song
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The single playback entry point used by the UI.
 *
 * The production implementation talks to the [com.tidalmusic.player.playback.service.PlaybackService]
 * through a Media3 `MediaController`; tests use an in-memory fake. Because the UI only
 * ever sees this interface, playback logic is fully testable without a device.
 *
 * The authoritative track order lives in this controller, not in the player, so that
 * "play next", "add to queue" and manual reordering behave predictably and stay in
 * sync with the queue sheet.
 */
interface PlaybackController {

    /** The current track and transport state. */
    val state: StateFlow<PlaybackState>

    /** The ticking playback position. Kept separate so the player screen stays cheap. */
    val progress: StateFlow<PlaybackProgress>

    /** The current queue, in playback order. */
    val queue: StateFlow<List<QueueItem>>

    /** One-shot events: a track started, an error occurred, the queue changed. */
    val events: SharedFlow<PlaybackEvent>

    /** Connects to the playback service. Safe to call more than once. */
    suspend fun connect()

    /** Releases the connection. Called when the app is finished with playback. */
    fun release()

    /**
     * Starts playback with [songs] as the queue, positioned at [startIndex].
     *
     * @param shuffle when true the queue order is randomised before playback starts.
     */
    fun play(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean = false)

    /** Plays a single song, replacing the queue. */
    fun play(song: Song, queue: List<Song> = listOf(song), startIndex: Int = 0)

    /**
     * Loads [songs] as the queue and seeks to [positionMs] without starting playback.
     *
     * Used to restore the previous session on launch: the track and position come back but
     * audio does not start until the user presses play.
     */
    fun restoreSession(songs: List<Song>, startIndex: Int, positionMs: Long)

    /** Inserts [song] directly after the current track. */
    fun playNext(song: Song)

    /** Appends [song] to the end of the queue. */
    fun addToQueue(song: Song)

    /** Appends several songs to the end of the queue. */
    fun addToQueue(songs: List<Song>)

    /** Toggles between playing and paused. */
    fun togglePlayPause()

    /** Starts playback if it is paused. */
    fun play()

    /** Pauses playback. */
    fun pause()

    /** Moves to the next track in the queue. */
    fun next()

    /** Moves to the previous track, or restarts the current one after 3 seconds. */
    fun previous()

    /** Seeks to [positionMs] in the current track. */
    fun seekTo(positionMs: Long)

    /** Enables or disables shuffle. Enabling reorders the upcoming tracks. */
    fun setShuffleEnabled(enabled: Boolean)

    /** Cycles the repeat mode: off → all → one → off. */
    fun cycleRepeatMode()

    /** Sets the repeat mode explicitly. */
    fun setRepeatMode(mode: RepeatMode)

    /** Sets the playback speed, e.g. `1.25f`. */
    fun setPlaybackSpeed(speed: Float)

    /** Removes the queue entry at [index]. */
    fun removeFromQueue(index: Int)

    /** Moves the queue entry at [from] to [to]. */
    fun moveQueueItem(from: Int, to: Int)

    /** Jumps to the queue entry at [index] and starts playing it. */
    fun skipToQueueItem(index: Int)

    /** Clears the queue, keeping the current track playing. */
    fun clearQueue()

    /** Stops playback and releases the queue. */
    fun stop()

    /** Sets Android's system media-stream volume, normalized to 0f..1f. */
    fun setVolume(volume: Float)

    /** Reads Android's current system media-stream volume, normalized to 0f..1f. */
    fun volume(): Float
}

/** One-shot events emitted by the [PlaybackController]. */
sealed interface PlaybackEvent {

    /** A track started playing. The app records this in the history. */
    data class TrackStarted(val song: Song) : PlaybackEvent

    /** The current track could not be played. */
    data class Error(val song: Song?, val message: String) : PlaybackEvent

    /** The queue changed in a way the UI should react to (e.g. it was cleared). */
    data object QueueCleared : PlaybackEvent

    /** The sleep timer expired and playback was paused. */
    data object SleepTimerExpired : PlaybackEvent
}

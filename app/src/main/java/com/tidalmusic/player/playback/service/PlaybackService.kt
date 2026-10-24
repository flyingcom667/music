package com.tidalmusic.player.playback.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.tidalmusic.player.MainActivity
import com.tidalmusic.player.R
import com.tidalmusic.player.data.preferences.PlaybackStateStore
import com.tidalmusic.player.playback.MediaItemMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The background playback service.
 *
 * Owns exactly one [ExoPlayer] and one [MediaSession] for the whole process. The UI never
 * creates a player of its own — it talks to this service through a Media3 `MediaController`,
 * so playback survives activity recreation and keeps running while the app is in the
 * background.
 *
 * Handled here:
 *  - the media notification and lock-screen controls (through the media session),
 *  - audio focus (including interruptions such as phone calls),
 *  - "becoming noisy" (playback pauses when a headphone or Bluetooth route disconnects),
 *  - Android's normal media routing to the selected Bluetooth, wired, USB, or speaker output,
 *  - restoring the last track and position after a process restart.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var persistenceJob: Job? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val stateStore by lazy { PlaybackStateStore(this) }

    override fun onCreate() {
        super.onCreate()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        val session = MediaSession.Builder(this, player)
            .setId(SESSION_ID)
            .setSessionActivity(sessionActivityIntent())
            .setCallback(sessionCallback)
            .build()

        mediaSession = session
        session.setCustomLayout(
            listOf(
                CommandButton.Builder()
                    .setDisplayName(getString(R.string.action_stop_playback))
                    .setIconResId(R.drawable.ic_notif_close)
                    .setSessionCommand(STOP_PLAYBACK_COMMAND)
                    .build(),
            ),
        )

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(NOTIFICATION_CHANNEL_ID)
                .setChannelName(R.string.playback_channel_name)
                .build()
                .apply { setSmallIcon(R.drawable.ic_notification) },
        )

        startPositionPersistence(player)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // The user swiped the app away: keep the service alive only while audio is running.
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        persistPosition(mediaSession?.player)
        persistenceJob?.cancel()
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    /** Opens the app when the notification or lock-screen controls are tapped. */
    private fun sessionActivityIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private val sessionCallback = object : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val sessionCommands = if (controller.isTrusted) {
                MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
            } else {
                MediaSession.ConnectionResult.DEFAULT_UNTRUSTED_SESSION_COMMANDS
            }
            val playerCommands = if (controller.isTrusted) {
                MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
            } else {
                MediaSession.ConnectionResult.DEFAULT_UNTRUSTED_PLAYER_COMMANDS
            }
            return MediaSession.ConnectionResult.AcceptedResultBuilder()
                .setAvailableSessionCommands(
                    sessionCommands.buildUpon().add(STOP_PLAYBACK_COMMAND).build(),
                )
                .setAvailablePlayerCommands(playerCommands)
                .setCustomLayout(
                    listOf(
                        CommandButton.Builder()
                            .setDisplayName(getString(R.string.action_stop_playback))
                            .setIconResId(R.drawable.ic_notif_close)
                            .setSessionCommand(STOP_PLAYBACK_COMMAND)
                            .build(),
                    ),
                )
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: android.os.Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction != STOP_PLAYBACK_ACTION) {
                return Futures.immediateFuture(
                    SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED),
                )
            }
            session.player.stop()
            session.player.clearMediaItems()
            stopSelf()
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        /**
         * The queue is owned by the app, so external controllers may only add items that
         * already carry their media URI (which the app always sets).
         */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> = Futures.immediateFuture(mediaItems)

        /** Lets the system restore the last track after a reboot or an app update. */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val restored = stateStore.restore()
                ?: return Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(ImmutableList.of(), 0, 0L),
                )
            val items = ImmutableList.of(MediaItemMapper.toMediaItem(restored.song))
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(items, 0, restored.positionMs),
            )
        }
    }

    /** Saves the position periodically so a process kill does not lose it. */
    private fun startPositionPersistence(player: ExoPlayer) {
        persistenceJob?.cancel()
        persistenceJob = serviceScope.launch {
            while (isActive) {
                delay(POSITION_SAVE_INTERVAL_MS)
                // A paused position never changes; writing it again would only wake the disk.
                if (player.isPlaying) {
                    persistPosition(player)
                }
            }
        }
    }

    private fun persistPosition(player: Player?) {
        val item = player?.currentMediaItem ?: return
        val song = MediaItemMapper.toSong(item) ?: return
        val position = player.currentPosition.coerceAtLeast(0L)
        stateStore.save(song, position)
    }

    companion object {
        const val SESSION_ID = "TidalMusicSession"
        const val NOTIFICATION_CHANNEL_ID = "tidal_music_playback"
        private const val STOP_PLAYBACK_ACTION = "com.tidalmusic.player.STOP_PLAYBACK"
        private val STOP_PLAYBACK_COMMAND = SessionCommand(STOP_PLAYBACK_ACTION, android.os.Bundle.EMPTY)
        private const val POSITION_SAVE_INTERVAL_MS = 5_000L
    }
}

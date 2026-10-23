package com.example.myapplication.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.core.app.NotificationManagerCompat
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.myapplication.MainActivity
import com.example.myapplication.MusicApplication
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Playback engine. ExoPlayer owns audio focus handling (it pauses on focus loss and resumes
 * when focus returns) and "becoming noisy" handling, so the app never fights the platform.
 */
@OptIn(UnstableApi::class)
class MusicPlaybackService : MediaSessionService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var isDismissed = false

    override fun onCreate() {
        super.onCreate()
        val container = (application as MusicApplication).container

        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player = exoPlayer
        exoPlayer.addListener(PlaybackEndListener())

        val session = MediaSession.Builder(this, exoPlayer)
            .setCallback(SessionCallback())
            .setSessionActivity(openAppIntent())
            .build()
        setMediaNotificationProvider(
            MusicNotificationProvider(
                context = this,
                contentIntent = openAppIntent(),
                onClose = { stopPlaybackAndDismiss() },
                artworkLoader = container::loadArtworkBitmap,
            ),
        )
        mediaSession = session

        // ExoPlayer plays gapless by default; the setting simply decides whether it should
        // insert a natural gap between tracks instead.
        serviceScope.launch {
            container.settingsRepository.settings.collect { settings ->
                exoPlayer.setPauseAtEndOfMediaItems(!settings.gaplessPlayback)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /**
     * The music keeps playing when the app is closed from the recents screen — the service is a
     * foreground service, so it survives until the playback itself stops.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        // Intentionally empty: swiping the app away must not stop the music.
    }

    /**
     * The notification belongs to the playback: as soon as the music stops — the queue ended or
     * the user closed it — the notification is removed and the service shuts itself down.
     */
    private inner class PlaybackEndListener : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) stopPlaybackAndDismiss()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // The queue was emptied → nothing is playing anymore.
            if (mediaItem == null) stopPlaybackAndDismiss()
        }
    }

    /** Stops playback, drops the media notification and shuts the service down. */
    private fun stopPlaybackAndDismiss() {
        if (isDismissed) return
        isDismissed = true
        player?.run {
            stop()
            clearMediaItems()
        }
        NotificationManagerCompat.from(this).cancel(MusicNotificationProvider.NOTIFICATION_ID)
        stopSelf()
    }

    override fun onDestroy() {
        mediaSession?.run {
            release()
            mediaSession = null
        }
        player?.release()
        player = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun openAppIntent(): PendingIntent {
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

    /** Controllers send items with `requestMetadata.mediaUri` only; rebuild them here. */
    private inner class SessionCallback : MediaSession.Callback {

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> =
            Futures.immediateFuture(mediaItems.map { it.asPlayable() })

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
            Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(
                    mediaItems.map { it.asPlayable() },
                    startIndex,
                    startPositionMs,
                ),
            )
    }

    private fun MediaItem.asPlayable(): MediaItem {
        if (localConfiguration != null) return this
        val uri = requestMetadata.mediaUri ?: return this
        return buildUpon().setUri(uri).build()
    }
}

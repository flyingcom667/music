package com.example.myapplication.playback

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.util.LruCache
import androidx.annotation.OptIn
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import com.example.myapplication.R
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Media notification with an extra close (✕) button and the cover art of the current song.
 *
 * Tapping ✕ — or swiping the notification away while paused — stops playback, clears the queue
 * and shuts the playback service down, so nothing keeps running in the background afterwards.
 *
 * The artwork is decoded in the background (it means reading the file) and the notification is
 * refreshed as soon as the bitmap is available, so it never shows a placeholder for long.
 */
@OptIn(UnstableApi::class)
class MusicNotificationProvider(
    private val context: Context,
    private val contentIntent: PendingIntent,
    private val onClose: () -> Unit,
    private val artworkLoader: suspend (mediaId: Long, albumId: Long) -> Bitmap? = { _, _ -> null },
) : MediaNotification.Provider {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Small LRU: only the covers of the last few tracks are kept in memory. */
    private val artworkCache = LruCache<String, Bitmap>(ARTWORK_CACHE_SIZE)
    private val failedArtwork = mutableSetOf<String>()
    private var pendingArtwork: String? = null
    private var currentSession: MediaSession? = null
    private var currentActionFactory: MediaNotification.ActionFactory? = null

    init {
        val channel = NotificationChannelCompat
            .Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(context.getString(R.string.notification_channel_name))
            .setDescription(context.getString(R.string.notification_channel_description))
            .setShowBadge(false)
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    override fun createNotification(
        mediaSession: MediaSession,
        mediaButtonPreferences: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        currentSession = mediaSession
        currentActionFactory = actionFactory

        val notification = buildNotification(mediaSession, actionFactory)

        val (mediaId, albumId) = currentArtworkIds(mediaSession)
        if (mediaId > 0L) {
            requestArtwork(mediaId, albumId, onNotificationChangedCallback)
        }

        return MediaNotification(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(
        mediaSession: MediaSession,
        actionFactory: MediaNotification.ActionFactory,
    ): Notification {
        val player = mediaSession.player
        val metadata = player.mediaMetadata
        val showPause = player.playWhenReady || player.isPlaying

        val closeAction = actionFactory.createCustomAction(
            mediaSession,
            IconCompat.createWithResource(context, R.drawable.ic_notif_close),
            CLOSE_LABEL,
            ACTION_CLOSE,
            Bundle.EMPTY,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(metadata.title ?: context.getString(R.string.app_name))
            .setContentText(metadata.artist ?: metadata.albumTitle ?: "")
            .setContentIntent(contentIntent)
            .setDeleteIntent(closeAction.actionIntent)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setOngoing(player.isPlaying)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                actionFactory.createMediaAction(
                    mediaSession,
                    IconCompat.createWithResource(context, R.drawable.ic_notif_previous),
                    PREVIOUS_LABEL,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                ),
            )
            .addAction(
                actionFactory.createMediaAction(
                    mediaSession,
                    IconCompat.createWithResource(
                        context,
                        if (showPause) R.drawable.ic_notif_pause else R.drawable.ic_notif_play,
                    ),
                    if (showPause) PAUSE_LABEL else PLAY_LABEL,
                    Player.COMMAND_PLAY_PAUSE,
                ),
            )
            .addAction(
                actionFactory.createMediaAction(
                    mediaSession,
                    IconCompat.createWithResource(context, R.drawable.ic_notif_next),
                    NEXT_LABEL,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                ),
            )
            .addAction(closeAction)
            .setStyle(
                MediaStyleNotificationHelper.MediaStyle(mediaSession)
                    .setShowActionsInCompactView(0, 1, 2),
            )

        // Cover art of the current track (the expanded notification shows it as a big picture).
        val (mediaId, albumId) = currentArtworkIds(mediaSession)
        val artwork = if (mediaId > 0L) artworkCache.get(artworkKey(mediaId, albumId)) else null
        if (artwork != null) {
            builder.setLargeIcon(artwork)
        }

        return builder.build()
    }

    /** Decodes the cover in the background and refreshes the notification afterwards. */
    private fun requestArtwork(
        mediaId: Long,
        albumId: Long,
        callback: MediaNotification.Provider.Callback,
    ) {
        val key = artworkKey(mediaId, albumId)
        if (artworkCache.get(key) != null) return
        if (key in failedArtwork) return
        if (pendingArtwork == key) return

        pendingArtwork = key
        scope.launch {
            val bitmap = runCatching { artworkLoader(mediaId, albumId) }.getOrNull()
            pendingArtwork = null
            if (bitmap == null) {
                failedArtwork += key
                return@launch
            }
            artworkCache.put(key, bitmap)
            // Ask media3 to rebuild the notification — now it contains the cover.
            val session = currentSession
            val factory = currentActionFactory
            if (session != null && factory != null) {
                callback.onNotificationChanged(
                    MediaNotification(NOTIFICATION_ID, buildNotification(session, factory)),
                )
            }
        }
    }

    private fun currentArtworkIds(mediaSession: MediaSession): Pair<Long, Long> {
        val player = mediaSession.player
        val mediaId = player.currentMediaItem?.mediaId?.toLongOrNull() ?: 0L
        val albumId = player.mediaMetadata.extras?.getLong(MediaItemExtra.ALBUM_ID, 0L) ?: 0L
        return mediaId to albumId
    }

    private fun artworkKey(mediaId: Long, albumId: Long): String =
        if (albumId > 0L) "album:$albumId" else "song:$mediaId"

    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: Bundle,
    ): Boolean {
        if (action != ACTION_CLOSE) return false
        session.player.stop()
        session.player.clearMediaItems()
        onClose()
        return true
    }

    override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo =
        MediaNotification.Provider.NotificationChannelInfo(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
        )

    companion object {
        const val CHANNEL_ID = "playback"
        const val NOTIFICATION_ID = 1001

        private const val ARTWORK_CACHE_SIZE = 6

        private const val ACTION_CLOSE = "com.example.myapplication.action.CLOSE_PLAYBACK"

        private const val CLOSE_LABEL = "Close"
        private const val PLAY_LABEL = "Play"
        private const val PAUSE_LABEL = "Pause"
        private const val NEXT_LABEL = "Next"
        private const val PREVIOUS_LABEL = "Previous"
    }
}

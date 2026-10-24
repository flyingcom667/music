package com.tidalmusic.player

import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.tidalmusic.player.playback.service.PlaybackService
import java.util.concurrent.ExecutionException

/** Compact file-manager playback that leaves the main app closed until requested. */
class ExternalPlaybackActivity : ComponentActivity() {

    // The ocean palette, mirrored from the Compose theme for this View-based window.
    private val oceanNavy = 0xFF03045E.toInt()
    private val oceanBlue = 0xFF0077B6.toInt()
    private val oceanCyan = 0xFF00B4D8.toInt()
    private val oceanLightCyan = 0xFF90E0EF.toInt()
    private val oceanFoam = 0xFFCAF0F8.toInt()

    private data class PlaybackSnapshot(
        val items: List<MediaItem>,
        val currentIndex: Int,
        val positionMs: Long,
        val playWhenReady: Boolean,
        val repeatMode: Int,
    )

    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var playButton: ImageButton? = null
    private var progressBar: SeekBar? = null
    private var currentTime: TextView? = null
    private var totalTime: TextView? = null
    private var snapshot: PlaybackSnapshot? = null
    private var isPlaying = false
    private var isClosing = false
    private var isOpeningInApp = false
    private var audioUri: Uri? = null
    private var audioTitle = ""
    private val handler = Handler(Looper.getMainLooper())
    private val progressUpdate = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, PROGRESS_UPDATE_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.data
        if (intent.action != Intent.ACTION_VIEW || uri == null) {
            showUnavailableAndClose()
            return
        }
        audioUri = uri
        audioTitle = readDisplayName(uri)
        showPlaybackWindow()
        connectAndPlay(uri)
    }

    override fun onDestroy() {
        handler.removeCallbacks(progressUpdate)
        controllerFuture?.cancel(false)
        if (!isOpeningInApp && !isClosing) restorePreviousPlayback()
        mediaController?.release()
        mediaController = null
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        closePlayback()
    }

    private fun showPlaybackWindow() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(oceanNavy, oceanBlue),
            ).apply {
                cornerRadius = dp(24).toFloat()
            }
        }
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            TextView(this).apply {
                text = audioTitle
                textSize = 17f
                setTextColor(oceanFoam)
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        header.addView(
            ImageButton(this).apply {
                setImageResource(R.drawable.ic_notif_close)
                contentDescription = getString(R.string.action_close)
                setBackgroundColor(Color.TRANSPARENT)
                imageTintList = android.content.res.ColorStateList.valueOf(oceanLightCyan)
                setPadding(dp(10), dp(10), dp(10), dp(10))
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
                setOnClickListener { closePlayback() }
            },
        )
        root.addView(header)

        val timeRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, 0)
        }
        currentTime = timeLabel().apply { text = formatTime(0L) }
        totalTime = timeLabel().apply { text = formatTime(0L) }
        timeRow.addView(currentTime, LinearLayout.LayoutParams(0, dp(24), 1f))
        timeRow.addView(totalTime, LinearLayout.LayoutParams(0, dp(24), 1f).apply {
            gravity = Gravity.END
        })
        root.addView(timeRow)

        progressBar = SeekBar(this).apply {
            max = 1_000
            isEnabled = false
            setPadding(0, 0, 0, 0)
            progressTintList = android.content.res.ColorStateList.valueOf(oceanCyan)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(oceanBlue)
            thumbTintList = android.content.res.ColorStateList.valueOf(oceanCyan)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) = Unit

                override fun onStartTrackingTouch(seekBar: SeekBar) {
                    handler.removeCallbacks(progressUpdate)
                }

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    val controller = mediaController ?: return
                    val duration = controller.duration
                    if (duration > 0L) {
                        controller.seekTo(duration * seekBar.progress / seekBar.max)
                    }
                    handler.post(progressUpdate)
                }
            })
        }
        root.addView(progressBar)

        val actions = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, 0)
        }
        playButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_notif_play)
            contentDescription = getString(R.string.action_play)
            imageTintList = android.content.res.ColorStateList.valueOf(oceanNavy)
            background = GradientDrawable().apply {
                setColor(oceanCyan)
                shape = GradientDrawable.OVAL
            }
            isEnabled = false
            layoutParams = LinearLayout.LayoutParams(dp(54), dp(54)).apply {
                marginEnd = dp(14)
            }
            setOnClickListener {
                val controller = mediaController ?: return@setOnClickListener
                if (controller.isPlaying) controller.pause() else controller.play()
            }
        }
        actions.addView(playButton)
        actions.addView(
            Button(this).apply {
                text = getString(R.string.external_audio_open_in_app)
                textSize = 15f
                isAllCaps = false
                backgroundTintList = android.content.res.ColorStateList.valueOf(oceanCyan)
                setTextColor(oceanNavy)
                setOnClickListener { openInApp() }
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        root.addView(actions)
        setContentView(root)
        window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    private fun connectAndPlay(uri: Uri) {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener(
            { runOnUiThread { onControllerConnected(future, uri) } },
            MoreExecutors.directExecutor(),
        )
    }

    private fun onControllerConnected(
        future: ListenableFuture<MediaController>,
        uri: Uri,
    ) {
        try {
            val controller = future.get()
            if (isClosing || isFinishing || isDestroyed) {
                controller.release()
                return
            }
            mediaController = controller
            snapshot = if (controller.mediaItemCount > 0) {
                PlaybackSnapshot(
                    items = (0 until controller.mediaItemCount).map(controller::getMediaItemAt),
                    currentIndex = controller.currentMediaItemIndex.coerceAtLeast(0),
                    positionMs = controller.currentPosition.coerceAtLeast(0L),
                    playWhenReady = controller.playWhenReady,
                    repeatMode = controller.repeatMode,
                )
            } else {
                null
            }
            controller.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                    updatePlayButton()
                }

                override fun onPlaybackStateChanged(playbackState: Int) = updateProgress()
            })
            controller.setMediaItem(
                MediaItem.Builder()
                    .setMediaId(songId(uri).toString())
                    .setUri(uri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(audioTitle)
                            .setDisplayTitle(audioTitle)
                            .setIsBrowsable(false)
                            .setIsPlayable(true)
                            .build(),
                    )
                    .build(),
            )
            controller.prepare()
            controller.play()
            playButton?.isEnabled = true
            progressBar?.isEnabled = true
            isPlaying = true
            updatePlayButton()
            updateProgress()
            handler.post(progressUpdate)
        } catch (error: ExecutionException) {
            showPlaybackError(error)
        } catch (error: SecurityException) {
            showPlaybackError(error)
        } catch (error: IllegalStateException) {
            showPlaybackError(error)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            showPlaybackError(error)
        }
    }

    private fun showPlaybackError(error: Exception) {
        android.util.Log.e(TAG, "Unable to play external audio", error)
        if (!isFinishing && !isDestroyed) {
            Toast.makeText(
                this,
                R.string.external_audio_unavailable,
                Toast.LENGTH_LONG,
            ).show()
            finish()
        }
    }

    private fun closePlayback() {
        if (isClosing) return
        isClosing = true
        restorePreviousPlayback()
        finish()
    }

    private fun openInApp() {
        val uri = audioUri ?: return
        isOpeningInApp = true
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = uri
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
            },
        )
        finish()
    }

    private fun showUnavailableAndClose() {
        Toast.makeText(this, R.string.external_audio_unavailable, Toast.LENGTH_LONG).show()
        finish()
    }

    private fun updatePlayButton() {
        playButton?.setImageResource(if (isPlaying) R.drawable.ic_notif_pause else R.drawable.ic_notif_play)
        playButton?.contentDescription = getString(
            if (isPlaying) R.string.action_pause else R.string.action_play,
        )
    }

    private fun restorePreviousPlayback() {
        val controller = mediaController ?: return
        val previous = snapshot
        if (previous == null) {
            controller.stop()
            controller.clearMediaItems()
            return
        }
        controller.setMediaItems(previous.items, previous.currentIndex, previous.positionMs)
        controller.repeatMode = previous.repeatMode
        controller.prepare()
        if (previous.playWhenReady) controller.play() else controller.pause()
    }

    private fun updateProgress() {
        val controller = mediaController ?: return
        val duration = controller.duration
        val position = controller.currentPosition.coerceAtLeast(0L)
        currentTime?.text = formatTime(position)
        totalTime?.text = formatTime(duration.takeIf { it > 0L } ?: 0L)
        progressBar?.let { bar ->
            bar.isEnabled = duration > 0L
            if (!bar.isPressed && duration > 0L) {
                bar.progress = (position * bar.max / duration).toInt().coerceIn(0, bar.max)
            }
        }
    }

    private fun timeLabel() = TextView(this).apply {
        textSize = 12f
        setTextColor(oceanLightCyan)
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun formatTime(timeMs: Long): String {
        val seconds = (timeMs / 1_000L).coerceAtLeast(0L)
        return "%d:%02d".format(seconds / 60L, seconds % 60L)
    }

    private fun readDisplayName(uri: Uri): String {
        val fallback = uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.substringBeforeLast('.')
            .orEmpty()
            .ifBlank { getString(R.string.external_audio_title) }
        return try {
            contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(0)?.substringBeforeLast('.')?.ifBlank { fallback } ?: fallback
                    } else {
                        fallback
                    }
                } ?: fallback
        } catch (error: SecurityException) {
            fallback
        }
    }

    private fun songId(uri: Uri): Long =
        kotlin.math.abs(uri.toString().hashCode().toLong()).coerceAtLeast(1L)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val TAG = "ExternalPlayback"
        const val PROGRESS_UPDATE_MS = 500L
    }
}

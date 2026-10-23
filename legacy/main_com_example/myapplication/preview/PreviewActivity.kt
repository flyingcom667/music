package com.example.myapplication.preview

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication.MainActivity
import com.example.myapplication.MusicApplication
import com.example.myapplication.di.AppContainer
import com.example.myapplication.ui.screens.MiniPreviewScreen
import com.example.myapplication.ui.theme.MusicTheme
import com.example.myapplication.ui.viewmodel.MusicViewModelFactory
import com.example.myapplication.ui.viewmodel.PreviewViewModel
import com.example.myapplication.ui.viewmodel.SettingsViewModel

/**
 * Shows a compact preview of an audio file that was opened from a file manager.
 *
 * The activity is registered for `ACTION_VIEW` intents with an audio mime type; the file is
 * read through the read permission the intent grants. It is a small floating window that plays
 * the file right away, instead of a full screen that would cover the file manager.
 */
class PreviewActivity : ComponentActivity() {

    private val container: AppContainer
        get() = (application as MusicApplication).container

    private val viewModel: PreviewViewModel by viewModels { MusicViewModelFactory(container) }
    private val settingsViewModel: SettingsViewModel by viewModels { MusicViewModelFactory(container) }

    private var currentUri by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The window floats above the file manager, at the bottom of the screen.
        window.attributes = window.attributes.apply { gravity = Gravity.BOTTOM }
        container.playbackController.connect()

        val uri = intent?.data
        if (uri == null) {
            finish()
            return
        }
        keepReadPermission(uri)
        currentUri = uri.toString()

        setContent {
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
            val state by viewModel.state.collectAsStateWithLifecycle()
            val artwork by viewModel.artwork.collectAsStateWithLifecycle()
            val playback by viewModel.playback.collectAsStateWithLifecycle()
            val progress by viewModel.progress.collectAsStateWithLifecycle()

            LaunchedEffect(currentUri) {
                currentUri?.let(viewModel::load)
            }

            MusicTheme(settings = settings) {
                MiniPreviewScreen(
                    state = state,
                    artwork = artwork,
                    isPlaying = playback.isPlaying,
                    positionMs = progress.positionMs,
                    durationMs = state.durationMs,
                    onClose = { finish() },
                    onTogglePlay = viewModel::toggle,
                    onOpenFullPlayer = { openFullPlayer() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val uri = intent.data ?: return
        keepReadPermission(uri)
        currentUri = uri.toString()
    }

    override fun onDestroy() {
        // The preview is a short lived session: when it is closed the file is released again.
        // On a configuration change the activity is only recreated, so nothing is stopped.
        if (isFinishing) {
            viewModel.stop()
        }
        super.onDestroy()
    }

    /** Opens the app and lets it show the file in the full player. */
    private fun openFullPlayer() {
        val uri = currentUri ?: return
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_PLAY_URI, uri)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
        finish()
    }

    /**
     * Keeps the read permission of the file for the lifetime of the app.
     *
     * Only a persistable grant can be kept; the temporary one of the intent is enough for the
     * preview itself.
     */
    private fun keepReadPermission(uri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }
}

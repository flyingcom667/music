package com.example.myapplication

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.di.AppContainer
import com.example.myapplication.navigation.MusicApp
import com.example.myapplication.ui.theme.MusicTheme
import com.example.myapplication.ui.viewmodel.MusicViewModelFactory
import com.example.myapplication.ui.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {

    private val container: AppContainer
        get() = (application as MusicApplication).container

    private var hasAudioPermission by mutableStateOf(false)
    private var showBatteryOptimizationDialog by mutableStateOf(false)

    /** A file that should be played in the full player, for example from the preview screen. */
    private var pendingPlayUri by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // The app has its own bar at the bottom of the screen. The system must not put a
            // scrim behind the navigation buttons, otherwise the bar of the app looks cut off.
            window.isNavigationBarContrastEnforced = false
        }
        container.playbackController.connect()
        hasAudioPermission = checkAudioPermission()
        pendingPlayUri = intent?.getStringExtra(EXTRA_PLAY_URI)

        setContent {
            val factory = MusicViewModelFactory(container)
            val settingsViewModel: SettingsViewModel = viewModel(factory = factory)
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
            ) { granted -> hasAudioPermission = granted }

            MusicTheme(settings = settings) {
                MusicApp(
                    container = container,
                    hasPermission = hasAudioPermission,
                    onRequestPermission = { permissionLauncher.launch(requiredPermission()) },
                    playUri = pendingPlayUri,
                    onPlayUriHandled = { pendingPlayUri = null },
                )
            }

            if (showBatteryOptimizationDialog) {
                BatteryOptimizationDialog(
                    onDismiss = { showBatteryOptimizationDialog = false },
                    onConfirm = {
                        showBatteryOptimizationDialog = false
                        requestIgnoreBatteryOptimizations()
                    },
                )
            }
        }

        // Asked once, the first time the app is used: playback should not be killed by Doze.
        window.decorView.postDelayed({ askForBatteryOptimizationIfNeeded() }, BATTERY_PROMPT_DELAY_MS)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingPlayUri = intent.getStringExtra(EXTRA_PLAY_URI)
    }

    override fun onResume() {
        super.onResume()
        hasAudioPermission = checkAudioPermission()
    }

    private fun checkAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, requiredPermission()) ==
            PackageManager.PERMISSION_GRANTED

    private fun requiredPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    /**
     * Shows the battery optimisation dialog the first time the app is opened.
     *
     * The user can decline — the flag is only set once, so the app never nags again.
     */
    private fun askForBatteryOptimizationIfNeeded() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_BATTERY_ASKED, false)) return
        prefs.edit { putBoolean(KEY_BATTERY_ASKED, true) }

        val powerManager = getSystemService(POWER_SERVICE) as? PowerManager ?: return
        if (powerManager.isIgnoringBatteryOptimizations(packageName)) return

        showBatteryOptimizationDialog = true
    }

    /** Opens the system dialog that asks for the "ignore battery optimisations" exemption. */
    private fun requestIgnoreBatteryOptimizations() {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = "package:$packageName".toUri()
        }
        val opened = runCatching { startActivity(direct) }.isSuccess
        if (!opened) {
            runCatching {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }

    companion object {
        /** Intent extra with the uri of a file that should be played in the full player. */
        const val EXTRA_PLAY_URI = "com.example.myapplication.extra.PLAY_URI"

        private const val PREFS_NAME = "audiofy_prefs"
        const val KEY_BATTERY_ASKED = "battery_optimization_asked"
        const val BATTERY_PROMPT_DELAY_MS = 1_200L
    }
}

@Composable
private fun BatteryOptimizationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = true),
        title = { Text("Keep playback alive") },
        text = {
            Text(
                "Android can stop background playback to save battery. Allowing Audiofy to " +
                    "ignore battery optimisations keeps the music running while the screen is off.",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Open settings") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Not now") }
        },
    )
}

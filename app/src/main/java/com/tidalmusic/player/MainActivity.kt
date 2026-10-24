package com.tidalmusic.player

import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tidalmusic.player.di.AppContainer
import com.tidalmusic.player.domain.model.Song
import com.tidalmusic.player.permissions.MediaPermissions
import com.tidalmusic.player.ui.navigation.MusicApp
import com.tidalmusic.player.ui.theme.TidalMusicTheme
import com.tidalmusic.player.ui.viewmodel.AppViewModelFactory
import com.tidalmusic.player.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The main application Activity.
 *
 * It owns nothing but the permission flow and the theme: all state lives in the screen
 * models, and all playback lives in the playback service.
 */
class MainActivity : ComponentActivity() {

    private val container: AppContainer
        get() = (application as TidalMusicApp).container

    private var hasAudioPermission by mutableStateOf(false)
    private var externalSong by mutableStateOf<Song?>(null)
    private var externalAudioUnavailable by mutableStateOf(false)

    /** Guards against a stale async resolution overwriting a newer "open with" request. */
    private var externalResolveGeneration = 0

    /**
     * Resolved once per Activity. Reading it from a composable would otherwise perform a
     * `PackageManager` call on the main thread on every recomposition of the app shell.
     */
    private val versionLabel: String by lazy { appVersion() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            // Android 15+ (API 35) enforces edge-to-edge: the system navigation bar — gesture
            // pill or 3-button — is transparent and overlaps app content. Keeping both system
            // bars fully transparent lets the Compose Scaffold consume the insets and place the
            // bottom bar / mini-player cleanly above the buttons on every Android version.
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
            ),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // The app draws its own bar at the bottom; a system scrim would make it look cut off.
            window.isNavigationBarContrastEnforced = false
        }
        hasAudioPermission = MediaPermissions.hasAudioAccess(this)
        resolveExternalSong(intent)

        setContent {
            val factory = remember(container) { AppViewModelFactory(container) }
            val settingsViewModel: SettingsViewModel = viewModel(factory = factory)
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions(),
            ) { result ->
                hasAudioPermission = result[MediaPermissions.audioPermission] ?: hasAudioPermission
            }
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
            ) {
                requestBatteryOptimizationExemptionIfNeeded()
            }

            LaunchedEffect(hasAudioPermission) {
                if (!hasAudioPermission) return@LaunchedEffect
                val preferences = getSharedPreferences(PROMPT_PREFS, MODE_PRIVATE)
                if (MediaPermissions.requiresNotificationPermission &&
                    !MediaPermissions.hasNotificationAccess(this@MainActivity) &&
                    !preferences.getBoolean(NOTIFICATION_REQUEST_SHOWN, false)
                ) {
                    preferences.edit().putBoolean(NOTIFICATION_REQUEST_SHOWN, true).apply()
                    notificationPermissionLauncher.launch(MediaPermissions.notificationPermission)
                } else {
                    requestBatteryOptimizationExemptionIfNeeded()
                }
            }

            TidalMusicTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor,
            ) {
                MusicApp(
                    factory = factory,
                    hasPermission = hasAudioPermission,
                    onRequestPermission = {
                        permissionLauncher.launch(MediaPermissions.audioPermissions)
                    },
                    onOpenAppSettings = { openAppSettings() },
                    externalSong = externalSong,
                    onExternalSongHandled = { externalSong = null },
                    externalAudioUnavailable = externalAudioUnavailable,
                    onExternalAudioErrorHandled = { externalAudioUnavailable = false },
                    appVersion = versionLabel,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The user may have granted or revoked access in the system settings.
        hasAudioPermission = MediaPermissions.hasAudioAccess(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        resolveExternalSong(intent)
    }

    /**
     * Resolves an externally opened audio file into a [Song].
     *
     * The provider queries and the readability probe are file I/O, so they run off the main
     * thread; the result is published only if it is still the newest request (a rapid
     * "open with" for another file must not be overwritten by a stale answer).
     */
    private fun resolveExternalSong(intent: Intent) {
        externalSong = null
        externalAudioUnavailable = false
        if (!intent.isAudioOpenRequest()) return
        val generation = ++externalResolveGeneration
        lifecycleScope.launch {
            val song = withContext(Dispatchers.IO) {
                runCatching { intent.toExternalSong() }.getOrNull()
            }
            if (generation == externalResolveGeneration) {
                externalSong = song
                externalAudioUnavailable = song == null
            }
        }
    }

    private fun openAppSettings() {
        startActivity(MediaPermissions.appSettingsIntent(this))
    }

    private fun requestBatteryOptimizationExemptionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || !hasAudioPermission) return
        val powerManager = getSystemService(PowerManager::class.java)
        if (powerManager.isIgnoringBatteryOptimizations(packageName)) return

        val preferences = getSharedPreferences(PROMPT_PREFS, MODE_PRIVATE)
        if (preferences.getBoolean(BATTERY_REQUEST_SHOWN, false)) return
        preferences.edit().putBoolean(BATTERY_REQUEST_SHOWN, true).apply()

        val request = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:$packageName"),
        )
        try {
            startActivity(request)
        } catch (error: ActivityNotFoundException) {
            Log.e(TAG, "Unable to open battery optimization request", error)
        }
    }

    /** The version shown in the settings screen. */
    private fun appVersion(): String {
        val version = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: "1.0"
        return version
    }

    private fun Intent.toExternalSong(): Song? {
        if (action != Intent.ACTION_VIEW && action != Intent.ACTION_SEND) return null
        val uri = data ?: (
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                getParcelableExtra(Intent.EXTRA_STREAM)
            }
            ) ?: clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri ?: return null
        return runCatching {
            val resolvedType = contentResolver.getType(uri)
            val displayName = contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                } else {
                    null
                }
            }
            val mimeType = type ?: resolvedType
            val audioExtension = (displayName ?: uri.lastPathSegment).orEmpty()
                .substringAfterLast('.', "")
                .lowercase() in AUDIO_EXTENSIONS
            if (mimeType?.startsWith("audio/") != true && !audioExtension) return null
            contentResolver.openAssetFileDescriptor(uri, "r")?.use { } ?: return null
            contentResolver.toSong(uri)
        }.getOrNull()
    }

    private fun Intent.isAudioOpenRequest(): Boolean =
        action == Intent.ACTION_VIEW || action == Intent.ACTION_SEND

    private fun ContentResolver.toSong(uri: Uri): Song {
        var title = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')
            .orEmpty()
        var artist = ""
        var album = ""
        var durationMs = 0L
        var sizeBytes = 0L
        var albumId = 0L
        var id = uri.toString().hashCode().toLong().let { kotlin.math.abs(it) }.coerceAtLeast(1L)
        var displayName = title

        runCatching {
            query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    displayName = cursor.getString(
                        cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME),
                    ) ?: displayName
                    title = displayName.substringBeforeLast('.')
                    val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) {
                        sizeBytes = cursor.getLong(sizeColumn)
                    }

                }
            }
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DISPLAY_NAME,
        )
        if (uri.authority == "media") {
            runCatching {
                query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                        title = cursor.getString(
                            cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE),
                        )?.takeIf { it.isNotBlank() } ?: title
                        artist = cursor.getString(
                            cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST),
                        ).orEmpty()
                        album = cursor.getString(
                            cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM),
                        ).orEmpty()
                        albumId = cursor.getLong(
                            cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID),
                        )
                        durationMs = cursor.getLong(
                            cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION),
                        )
                        sizeBytes = cursor.getLong(
                            cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE),
                        )
                        displayName = cursor.getString(
                            cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME),
                        ) ?: displayName
                    }
                }
            }
        }
        return Song(
            id = id,
            contentUri = uri.toString(),
            title = title.ifBlank { displayName.ifBlank { uri.toString() } },
            artist = artist,
            album = album,
            durationMs = durationMs,
            trackNumber = 0,
            year = 0,
            genre = null,
            dateAdded = 0L,
            dateModified = 0L,
            sizeBytes = sizeBytes,
            displayName = displayName,
            mimeType = getType(uri),
            albumId = albumId.takeIf { it > 0 } ?: id,
        )
    }

    private companion object {
        const val TAG = "MainActivity"
        const val PROMPT_PREFS = "first_run_prompts"
        const val BATTERY_REQUEST_SHOWN = "request_shown"
        const val NOTIFICATION_REQUEST_SHOWN = "notification_request_shown"
        val AUDIO_EXTENSIONS = setOf(
            "aac", "aif", "aiff", "alac", "amr", "flac", "m4a", "m4b", "mid", "midi",
            "mka", "mp3", "mpc", "oga", "ogg", "opus", "wav", "wma",
        )
    }
}

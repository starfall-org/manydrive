package com.starfall.gsadrive.feature.viewer

import com.starfall.gsadrive.R

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.starfall.gsadrive.data.DriveFile
import com.starfall.gsadrive.feature.viewer.playback.MediaPlaybackService
import com.starfall.gsadrive.feature.viewer.playback.PlaybackProgress
import com.starfall.gsadrive.feature.viewer.playback.PlaybackSource
import com.starfall.gsadrive.feature.viewer.playback.PlaybackSourceRegistry
import com.starfall.gsadrive.feature.viewer.playback.registerLocalPlaybackSources
import com.starfall.gsadrive.feature.viewer.playback.startLocalPlayback
import com.starfall.gsadrive.feature.viewer.ui.ExternalMediaScreen
import com.starfall.gsadrive.shared.operations.ioResult
import com.starfall.gsadrive.shared.i18n.localized
import com.starfall.gsadrive.shared.media.resolveLocalMedia
import com.starfall.gsadrive.shared.ui.theme.ManyDriveTheme
import com.starfall.gsadrive.shared.ui.theme.ThemeMode
import kotlinx.coroutines.launch

/**
 * Lightweight entry point for media opened from another app.
 *
 * The Activity only hosts the viewer. Audio/video playback is owned by the same
 * [MediaPlaybackService] used by the main app, so notification controls and background playback
 * have identical behavior without initializing the ManyDrive browser/account shell.
 */
class ExternalMediaActivity : ComponentActivity() {
    private var file by mutableStateOf<DriveFile?>(null)
    private var loading by mutableStateOf(true)
    private var error by mutableStateOf<String?>(null)
    private var playback by mutableStateOf<MediaController?>(null)
    private var sources: List<PlaybackSource>? = null
    private var playbackStartedFor: String? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var notificationPermissionPending = false
    private var closing = false

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationPermissionPending = false
        if (!granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(
                this,
                localized(R.string.no_notification_permission_the_player_still_runs_in_the_background_but_media_controls_may_not_appear_in_the_notification_bar),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val preferences = getSharedPreferences("manydrive_selection", MODE_PRIVATE)
        val themeMode = runCatching {
            ThemeMode.valueOf(preferences.getString("theme", ThemeMode.SYSTEM.name)!!)
        }.getOrDefault(ThemeMode.SYSTEM)
        val superDark = preferences.getBoolean("superDark", false)

        setContent {
            ManyDriveTheme(themeMode, superDark) {
                ExternalMediaScreen(
                    file = file,
                    loading = loading,
                    error = error,
                    player = playback,
                    onBack = ::closeExternalPlayback
                )
            }
        }

        open(intent)
    }

    private fun open(incoming: Intent?) {
        val uri = incoming?.data
        if (incoming?.action != Intent.ACTION_VIEW || uri == null || uri.scheme !in setOf("content", "file")) {
            loading = false
            error = localized(R.string.cannot_open_file)
            return
        }

        val declaredType = incoming.type
        lifecycleScope.launch {
            ioResult { resolveLocalMedia(this@ExternalMediaActivity, uri, declaredType) }
                .onSuccess { resolved ->
                    file = resolved
                    error = null
                    if (isMediaPreview(resolved)) {
                        ensureNotificationPermission()
                        sources = registerLocalPlaybackSources(listOf(resolved))
                        ensurePlaybackController()
                        maybeStartPlayback()
                    } else {
                        loading = false
                    }
                }
                .onFailure {
                    loading = false
                    error = it.message ?: localized(R.string.cannot_open_file)
                }
        }
    }

    private fun ensurePlaybackController() {
        if (playback != null || controllerFuture != null) return
        val sessionToken = SessionToken(this, ComponentName(this, MediaPlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync().also { future ->
            future.addListener({
                runCatching { future.get() }
                    .onSuccess { controller ->
                        if (closing) {
                            controller.release()
                            return@onSuccess
                        }
                        playback = controller
                        maybeStartPlayback()
                    }
                    .onFailure { failure ->
                        controllerFuture = null
                        loading = false
                        error = failure.message ?: localized(R.string.unable_to_connect_to_media_streaming_service)
                    }
            }, ContextCompat.getMainExecutor(this))
        }
    }

    private fun maybeStartPlayback() {
        if (closing) return
        val selected = file ?: return
        if (!isMediaPreview(selected) || playbackStartedFor == selected.id) return
        val controller = playback ?: return
        val registeredSources = sources ?: return
        runCatching { startLocalPlayback(this, controller, selected, registeredSources) }
            .onSuccess {
                playbackStartedFor = selected.id
                loading = false
                error = null
            }
            .onFailure {
                loading = false
                error = it.message ?: localized(R.string.cannot_open_media)
            }
    }

    /**
     * Back closes an externally opened player and stops its service session. Home is deliberately
     * not handled here: leaving this Activity lets MediaPlaybackService continue in the background.
     */
    private fun closeExternalPlayback() {
        if (closing) return
        closing = true
        if (sources != null) {
            playback?.let { controller ->
                runCatching { controller.stop() }
                runCatching { controller.clearMediaItems() }
            }
            PlaybackProgress.clear(this)
            PlaybackSourceRegistry.clear()
        }
        finish()
    }

    private fun ensureNotificationPermission() {
        if (notificationPermissionPending) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionPending = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onDestroy() {
        controllerFuture?.let(MediaController::releaseFuture)
        playback = null
        super.onDestroy()
    }
}

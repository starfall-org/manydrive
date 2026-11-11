package com.starfall.gsadrive.feature.viewer.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.starfall.gsadrive.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class MediaPlaybackService : MediaSessionService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaSession: MediaSession? = null
    private lateinit var player: PagedPlaybackPlayer
    private lateinit var artworkBitmapLoader: ManyDriveArtworkBitmapLoader

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(FixedTransportNotificationProvider(this))
        player = PagedPlaybackPlayer(this)
        artworkBitmapLoader = ManyDriveArtworkBitmapLoader(this)
        MediaPagePlayback.attach(player)

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) PlaybackProgress.save(this@MediaPlaybackService, player.currentMediaItem?.mediaId, 0L)
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying && player.playbackState != Player.STATE_ENDED)
                    PlaybackProgress.save(this@MediaPlaybackService, player.currentMediaItem?.mediaId, player.currentPosition)
            }
        })
        serviceScope.launch {
            while (true) {
                if (player.isPlaying) PlaybackProgress.save(this@MediaPlaybackService, player.currentMediaItem?.mediaId, player.currentPosition)
                kotlinx.coroutines.delay(1000)
            }
        }

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_PLAYER
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        mediaSession = MediaSession.Builder(this, player)
            .setBitmapLoader(artworkBitmapLoader)
            // Let Media3 publish periodic position updates; SystemUI owns the native media progress UI.
            .setPeriodicPositionUpdateEnabled(true)
            .setMediaButtonPreferences(listOf(
                CommandButton.Builder(CommandButton.ICON_PREVIOUS)
                    .setDisplayName("Previous")
                    .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .setSlots(CommandButton.SLOT_BACK)
                    .build(),
                CommandButton.Builder(CommandButton.ICON_NEXT)
                    .setDisplayName("Next")
                    .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .setSlots(CommandButton.SLOT_FORWARD)
                    .build()
            ))
            // Use the platform's native previous/play/next transport actions. Reserve
            // both side slots so an unavailable previous action cannot move next left.
            .setSessionExtras(Bundle().apply {
                putBoolean(MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_PREV, true)
                putBoolean(MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_NEXT, true)
            })
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        player.endAppSession()
        // Keep playing if playback is active. The default MediaSessionService behavior then keeps the
        // foreground service and notification alive even after the task is swiped away.
        if (!player.playWhenReady || player.playbackState == Player.STATE_ENDED || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        if (player.playbackState != Player.STATE_ENDED) PlaybackProgress.save(this, player.currentMediaItem?.mediaId, player.currentPosition)
        serviceScope.cancel()
        MediaPagePlayback.attach(null)
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        if (::artworkBitmapLoader.isInitialized) artworkBitmapLoader.release()
        VideoStreamCache.release()
        PlaybackSourceRegistry.clear()
        super.onDestroy()
    }
}

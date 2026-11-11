package com.starfall.gsadrive.feature.viewer.playback

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.Player
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.starfall.gsadrive.R
import com.starfall.gsadrive.shared.i18n.localized
import com.starfall.gsadrive.shared.media.ThumbnailRepository
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.Executors

@OptIn(UnstableApi::class)
internal class ManyDriveArtworkBitmapLoader(context: Context) : BitmapLoader {
    private val appContext = context.applicationContext
    private val executor = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(2))

    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/")

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = executor.submit(Callable {
        BitmapFactory.decodeByteArray(data, 0, data.size)
            ?: throw IOException(localized(R.string.unable_to_decode_artwork))
    })

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> = executor.submit(Callable {
        if (uri.scheme != "manydrive-artwork") throw IOException(localized(R.string.unsupported_artwork_uri_arg_1, uri))
        val mediaId = uri.getQueryParameter("id") ?: throw IOException(localized(R.string.artwork_missing_media_id))
        val source = PlaybackSourceRegistry.get(mediaId) ?: throw IOException(localized(R.string.no_artwork_source_found))
        val thumbnailUrl = source.file.thumbnailUrl ?: throw IOException(localized(R.string.media_does_not_have_thumbnails))
        ThumbnailRepository.load(
            appContext,
            notificationArtworkUrl(thumbnailUrl),
            source.accessToken,
            cacheKey = "notification:$mediaId"
        )
    })

    fun release() { executor.shutdownNow() }
}

private fun notificationArtworkUrl(url: String): String = when {
    Regex("=w\\d+-h\\d+.*$").containsMatchIn(url) ->
        url.replace(Regex("=w\\d+-h\\d+.*$"), "=w1024-h1024")
    Regex("=s\\d+.*$").containsMatchIn(url) ->
        url.replace(Regex("=s\\d+.*$"), "=s1024")
    else -> url
}

/**
 * Android's media template draws largeIcon in a square slot. Keep the source aspect ratio and
 * letterbox it instead of letting SystemUI stretch/crop a portrait or landscape thumbnail.
 */
private fun notificationLargeIcon(source: Bitmap, size: Int = 384): Bitmap {
    if (source.width <= 0 || source.height <= 0) return source
    val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val scale = minOf(size.toFloat() / source.width, size.toFloat() / source.height)
    val width = source.width * scale
    val height = source.height * scale
    val left = (size - width) / 2f
    val top = (size - height) / 2f
    Canvas(output).drawBitmap(
        source,
        null,
        RectF(left, top, left + width, top + height),
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    )
    return output
}

/** A muted artwork-derived tint for Android's native colorized MediaStyle background. */
private fun notificationArtworkColor(source: Bitmap): Int {
    if (source.width <= 0 || source.height <= 0) return Color.DKGRAY
    val xStep = maxOf(1, source.width / 24)
    val yStep = maxOf(1, source.height / 24)
    var red = 0L
    var green = 0L
    var blue = 0L
    var weight = 0L
    var y = 0
    while (y < source.height) {
        var x = 0
        while (x < source.width) {
            val color = source.getPixel(x, y)
            val alpha = Color.alpha(color)
            if (alpha > 32) {
                red += Color.red(color).toLong() * alpha
                green += Color.green(color).toLong() * alpha
                blue += Color.blue(color).toLong() * alpha
                weight += alpha
            }
            x += xStep
        }
        y += yStep
    }
    if (weight == 0L) return Color.DKGRAY
    // Keep enough contrast for the native white media controls.
    return Color.rgb(
        ((red / weight) * 0.58f).toInt().coerceIn(0, 255),
        ((green / weight) * 0.58f).toInt().coerceIn(0, 255),
        ((blue / weight) * 0.58f).toInt().coerceIn(0, 255)
    )
}

@OptIn(UnstableApi::class)
internal class FixedTransportNotificationProvider(context: Context) : MediaNotification.Provider {
    private val appContext = context.applicationContext
    private val delegate = DefaultMediaNotificationProvider(appContext).apply {
        setSmallIcon(R.drawable.ic_notification)
    }

    override fun createNotification(
        mediaSession: MediaSession,
        mediaButtonPreferences: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback
    ): MediaNotification {
        val base = delegate.createNotification(
            mediaSession,
            mediaButtonPreferences,
            actionFactory,
            onNotificationChangedCallback
        )
        val player = mediaSession.player
        val previous = CommandButton.Builder(CommandButton.ICON_PREVIOUS)
            .setDisplayName("Previous")
            .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .setSlots(CommandButton.SLOT_BACK)
            .build()
        val playPause = CommandButton.Builder(
            if (player.playWhenReady && player.playbackState != Player.STATE_ENDED)
                CommandButton.ICON_PAUSE else CommandButton.ICON_PLAY
        )
            .setDisplayName(if (player.playWhenReady && player.playbackState != Player.STATE_ENDED) localized(R.string.pause) else localized(R.string.play))
            .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
            .setSlots(CommandButton.SLOT_CENTRAL)
            .build()
        val next = CommandButton.Builder(CommandButton.ICON_NEXT)
            .setDisplayName("Next")
            .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .setSlots(CommandButton.SLOT_FORWARD)
            .build()

        fun action(button: CommandButton, enabled: Boolean): Notification.Action {
            val pendingIntent = if (enabled) {
                actionFactory.createMediaAction(
                    mediaSession,
                    IconCompat.createWithResource(appContext, button.iconResId),
                    button.displayName,
                    button.playerCommand
                ).actionIntent
            } else null
            return Notification.Action.Builder(
                Icon.createWithResource(appContext, button.iconResId),
                button.displayName,
                pendingIntent
            ).build()
        }

        val builder = Notification.Builder.recoverBuilder(appContext, base.notification)
            .setActions(
                action(previous, player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)),
                action(playPause, player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)),
                action(next, player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
            )

        val artwork = player.currentMediaItem?.mediaId
            ?.let { mediaId -> ThumbnailRepository.cached("notification:$mediaId") }
        if (artwork != null) {
            builder.setLargeIcon(notificationLargeIcon(artwork))
                .setColor(notificationArtworkColor(artwork))
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                builder.setColorized(true)
            }
        }

        val rebuilt = builder.build()
        return MediaNotification(base.notificationId, rebuilt)
    }

    override fun handleCustomCommand(mediaSession: MediaSession, action: String, extras: Bundle): Boolean =
        delegate.handleCustomCommand(mediaSession, action, extras)

    override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo =
        delegate.notificationChannelInfo
}

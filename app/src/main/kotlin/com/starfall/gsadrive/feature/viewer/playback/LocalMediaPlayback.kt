package com.starfall.gsadrive.feature.viewer.playback

import com.starfall.gsadrive.R

import android.content.Context
import androidx.media3.common.Player
import com.starfall.gsadrive.data.DriveFile
import com.starfall.gsadrive.feature.viewer.isMediaPreview
import com.starfall.gsadrive.shared.i18n.localized
import java.io.File

/** Shared local/content playback path used by both the in-app browser and external ACTION_VIEW. */
internal fun registerLocalPlaybackSources(queue: List<DriveFile>): List<PlaybackSource> {
    val sources = queue.filter(::isMediaPreview).map { file ->
        PlaybackSource(
            mediaId = "LOCAL:${file.id}",
            file = file,
            accountType = if (file.id.startsWith("content://")) "CONTENT" else "LOCAL",
            accessToken = null,
            s3Config = null,
            cacheFile = File(file.id)
        )
    }
    PlaybackSourceRegistry.replace(sources)
    return sources
}

internal fun startLocalPlayback(
    context: Context,
    player: Player,
    selected: DriveFile,
    sources: List<PlaybackSource>
) {
    val index = sources.indexOfFirst { it.file.id == selected.id }
    check(index >= 0) { localized(R.string.no_media_found_to_play) }
    val mediaId = sources[index].mediaId
    val position = if (player.currentMediaItem?.mediaId == mediaId) {
        player.currentPosition
    } else {
        PlaybackProgress.read(context, mediaId)
    }
    player.setMediaItems(sources.map(PlaybackSource::toMediaItem), index, position)
    player.prepare()
    player.play()
}

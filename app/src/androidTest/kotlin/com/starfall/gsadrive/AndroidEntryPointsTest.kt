package com.starfall.gsadrive

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.starfall.gsadrive.feature.viewer.ExternalMediaActivity
import com.starfall.gsadrive.feature.viewer.playback.MediaPlaybackService
import com.starfall.gsadrive.shared.media.resolveLocalMedia
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidEntryPointsTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun mediaIntentsStillResolveAndThePreviousActivityComponentRemainsUsable() {
        val manager = context.packageManager
        for (scheme in listOf("content", "file")) {
            for (mime in listOf("image/jpeg", "audio/mpeg", "video/mp4")) {
                val intent = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("$scheme:///media/sample"), mime)
                    .addCategory(Intent.CATEGORY_DEFAULT).setPackage(context.packageName)
                val resolved = manager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                assertEquals(ExternalMediaActivity::class.java.name, resolved?.activityInfo?.name)
            }
        }
        val alias = manager.getActivityInfo(ComponentName(context.packageName, "com.starfall.gsadrive.ExternalMediaActivity"), 0)
        assertEquals(ExternalMediaActivity::class.java.name, alias.targetActivity)
        assertTrue(alias.exported)
        assertTrue(manager.getServiceInfo(ComponentName(context, MediaPlaybackService::class.java), 0).exported)
    }

    @Test fun sharedLocalResolverPreservesContentUriAndDisplayName() {
        val exports = File(context.cacheDir, "shared-exports").apply { mkdirs() }
        val media = File.createTempFile("external-media-", ".mp3", exports)
        try {
            media.writeBytes(byteArrayOf(1, 2, 3))
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", media)
            val resolved = resolveLocalMedia(context, uri, null)
            assertEquals(uri.toString(), resolved.id)
            assertEquals(media.name, resolved.name)
            assertEquals("audio/mpeg", resolved.mimeType)
            val local = resolveLocalMedia(context, Uri.fromFile(media), "audio/mpeg")
            assertEquals(media.path, local.id)
        } finally {
            media.delete()
        }
    }
}

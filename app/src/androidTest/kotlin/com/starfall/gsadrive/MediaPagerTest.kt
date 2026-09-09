package com.starfall.gsadrive

import androidx.annotation.OptIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.test.platform.app.InstrumentationRegistry
import com.starfall.gsadrive.data.DriveFile
import com.starfall.gsadrive.ui.ExpandableMediaPlayer
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(UnstableApi::class)
class MediaPagerTest {
    @get:Rule val compose = createComposeRule()

    @Test(timeout = 45_000) fun dragMovesBothRealPagesAndCommitsOnlyWhenSettled() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = (0..2).map { DriveFile("page-$it", "Track $it", "audio/wav", null) }
        val sources = files.map { PlaybackSource(it.id, it, "GOOGLE", null, null, File(context.cacheDir, it.id)) }
        val selected = mutableIntStateOf(0)
        lateinit var session: PagedPlaybackPlayer
        compose.runOnUiThread {
            // A valid short silent WAV avoids remote IO and loading animations in the gesture test.
            sources.forEach { source ->
                val dataSize = 16000
                val wav = java.nio.ByteBuffer.allocate(44 + dataSize).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                wav.put("RIFF".toByteArray()).putInt(36 + dataSize).put("WAVEfmt ".toByteArray())
                    .putInt(16).putShort(1).putShort(1).putInt(8000).putInt(16000)
                    .putShort(2).putShort(16).put("data".toByteArray()).putInt(dataSize)
                source.cacheFile.writeBytes(wav.array())
            }
            PlaybackSourceRegistry.replace(sources)
            session = PagedPlaybackPlayer(context)
            session.setMediaItems(sources.map(PlaybackSource::toMediaItem))
            MediaPagePlayback.attach(session)
        }
        try {
            compose.setContent {
                MaterialTheme {
                    ExpandableMediaPlayer(
                        player = session, minimized = false, topPadding = 0.dp, miniBounds = null,
                        onMinimize = {}, onExpand = {}, onClose = {}, queue = files,
                        index = selected.intValue, previewPaths = emptyMap(),
                        onSwipeTo = { selected.intValue = it; session.seekTo(it, 0) }
                    )
                }
            }
            compose.waitForIdle()
            compose.mainClock.autoAdvance = false
            val pager = compose.onNodeWithTag("media-pager")
            val initialWidth = compose.onNodeWithTag("media-page-page-0").fetchSemanticsNode().boundsInRoot.width
            val controlsId = compose.onNodeWithTag("media-controls").fetchSemanticsNode().id
            pager.performTouchInput {
                down(Offset(width * 0.8f, height * 0.35f))
                moveBy(Offset(-width * 0.35f, 0f), delayMillis = 200)
            }
            compose.mainClock.advanceTimeBy(32)
            assertEquals(0, selected.intValue)
            assertTrue(compose.onNodeWithTag("media-page-page-0").fetchSemanticsNode().boundsInRoot.width < initialWidth)
            compose.onNodeWithTag("media-page-page-1").assertIsDisplayed()
            // Finish across the halfway point, then let the pager settle.
            pager.performTouchInput { moveBy(Offset(-width * 0.35f, 0f), delayMillis = 200); up() }
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
            compose.runOnIdle {
                assertEquals(1, selected.intValue)
                assertEquals("page-1", session.currentMediaItem!!.mediaId)
            }
            assertEquals(controlsId, compose.onNodeWithTag("media-controls").fetchSemanticsNode().id)
            compose.onAllNodesWithTag("media-controls").assertCountEquals(1)
            compose.runOnIdle { assertTrue(session.residentPlayerCount <= 3) }
            pager.performTouchInput { swipeRight() }
            compose.waitForIdle()
            compose.runOnIdle { assertEquals(0, selected.intValue) }
        } finally {
            compose.mainClock.autoAdvance = true
            compose.runOnUiThread { MediaPagePlayback.attach(null) }
            compose.waitForIdle()
            compose.runOnUiThread { session.release(); PlaybackSourceRegistry.clear() }
            sources.forEach { it.cacheFile.delete() }
        }
    }
}

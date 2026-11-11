package com.starfall.gsadrive.feature.viewer.playback

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Only the selected media survives process death; other positions belong to this session. */
internal class PlaybackProgressStore(private val preferences: android.content.SharedPreferences) {
    private val positions = mutableMapOf<String, Long>()
    private var activeId: String? = preferences.getString("active_id", null)

    init {
        activeId?.let { positions[it] = preferences.getLong("active_position", 0L).coerceAtLeast(0L) }
        // Discard legacy per-media history, whose active item cannot be determined.
        persist()
    }

    fun read(id: String): Long = positions[id] ?: 0L

    fun save(id: String?, position: Long) {
        if (id.isNullOrBlank()) return
        positions[id] = position.coerceAtLeast(0L)
        if (id == activeId) persist()
    }

    fun activate(id: String?, position: Long = 0L) {
        activeId = id?.takeIf { it.isNotBlank() }
        activeId?.let { positions[it] = position.coerceAtLeast(0L) }
        persist()
    }

    fun retainActive() {
        positions.keys.retainAll(setOfNotNull(activeId))
    }

    fun clear() {
        activeId = null
        positions.clear()
        persist()
    }

    private fun persist() {
        preferences.edit().clear().apply {
            activeId?.let { putString("active_id", it); putLong("active_position", read(it)) }
        }.apply()
    }
}

internal object PlaybackProgress {
    private var store: PlaybackProgressStore? = null
    private fun store(context: Context): PlaybackProgressStore = store ?: PlaybackProgressStore(
        context.applicationContext.getSharedPreferences("playback_progress", Context.MODE_PRIVATE)
    ).also { store = it }

    fun read(context: Context, id: String): Long = store(context).read(id)
    fun save(context: Context, id: String?, position: Long) = store(context).save(id, position)
    fun activate(context: Context, id: String?, position: Long = 0L) = store(context).activate(id, position)
    fun retainActive(context: Context) = store(context).retainActive()
    fun clear(context: Context) = store(context).clear()
}

/** Shared with the in-process viewer; playback policy is enforced by the service. */
object PlaybackSettings {
    private val slideshow = MutableStateFlow(true)
    val slideshowEnabled = slideshow.asStateFlow()

    fun setSlideshowEnabled(enabled: Boolean) {
        slideshow.value = enabled
    }
}

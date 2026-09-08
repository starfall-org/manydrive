package com.starfall.gsadrive.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

internal enum class NavigationSwipe { OPEN_DRAWER, PREVIOUS_TAB, NEXT_TAB }

/** Reserve gestures that start within 24 dp of the left edge for the drawer. */
internal fun navigationSwipe(startX: Float, distance: Float, edgeWidth: Float, threshold: Float): NavigationSwipe? =
    when {
        startX <= edgeWidth -> if (distance >= threshold) NavigationSwipe.OPEN_DRAWER else null
        distance <= -threshold -> NavigationSwipe.NEXT_TAB
        distance >= threshold -> NavigationSwipe.PREVIOUS_TAB
        else -> null
    }

internal fun Modifier.navigationSwipes(
    enabled: Boolean,
    onSwipe: (NavigationSwipe) -> Unit
): Modifier = if (!enabled) this else pointerInput(onSwipe) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var distance = 0f
        val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, overSlop ->
            change.consume()
            distance = overSlop
        }
        if (drag != null) {
            val completed = horizontalDrag(drag.id) { change ->
                distance += change.position.x - change.previousPosition.x
                change.consume()
            }
            if (completed) {
                navigationSwipe(down.position.x, distance, 24.dp.toPx(), 48.dp.toPx())?.let(onSwipe)
            }
        }
    }
}

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
    allowTabSwipes: Boolean = true,
    onSwipe: (NavigationSwipe) -> Unit
): Modifier = if (!enabled) this else pointerInput(onSwipe, allowTabSwipes) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val edgeWidth = 24.dp.toPx()
        // A real HorizontalPager owns normal tab drags. Keep only the drawer edge gesture here.
        if (!allowTabSwipes && down.position.x > edgeWidth) return@awaitEachGesture

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
                navigationSwipe(down.position.x, distance, edgeWidth, 48.dp.toPx())?.let(onSwipe)
            }
        }
    }
}

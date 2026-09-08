package com.starfall.gsadrive.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import kotlin.math.roundToInt

/** One mounted video surface moves between the viewer and the measured bottom-bar slot. */
@Composable
internal fun ExpandableMediaPlayer(
    player: Player,
    title: String,
    audioOnly: Boolean,
    minimized: Boolean,
    topPadding: Dp,
    miniBounds: Rect?,
    onMinimize: () -> Unit,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    onSwipe: (Boolean) -> Unit
) {
    BackHandler(enabled = !minimized, onBack = onMinimize)
    val density = LocalDensity.current
    var rootTop by remember { mutableFloatStateOf(0f) }
    var dragFraction by remember(player) { mutableStateOf<Float?>(null) }
    var dragDistance by remember(player) { mutableFloatStateOf(0f) }
    val fraction by animateFloatAsState(
        targetValue = dragFraction ?: if (minimized) 1f else 0f,
        animationSpec = if (dragFraction != null) snap() else tween(240),
        label = "player expansion"
    )
    val currentMinimize by rememberUpdatedState(onMinimize)
    val currentExpand by rememberUpdatedState(onExpand)
    val currentSwipe by rememberUpdatedState(onSwipe)
    val currentMinimized by rememberUpdatedState(minimized)
    val bottomInset = WindowInsets.safeDrawing.getBottom(density)

    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { rootTop = it.positionInRoot().y }) {
        val fullTop = with(density) { topPadding.toPx() }
        val fullHeight = (constraints.maxHeight - fullTop - bottomInset).coerceAtLeast(1f)
        val miniHeight = miniBounds?.height ?: with(density) { 72.dp.toPx() }
        val miniTop = miniBounds?.let { it.top - rootTop }
            ?: (constraints.maxHeight - bottomInset - miniHeight)
        val travel = (miniTop - fullTop).coerceAtLeast(1f)
        val currentTravel by rememberUpdatedState(travel)
        val height = fullHeight + (miniHeight - fullHeight) * fraction
        val y = fullTop + (miniTop - fullTop) * fraction
        var horizontalDrag by remember { mutableFloatStateOf(0f) }

        Box(
            Modifier.offset { IntOffset(0, y.roundToInt()) }
                .fillMaxWidth().height(with(density) { height.toDp() }).clipToBounds()
                .pointerInput(player) {
                    detectVerticalDragGestures(
                        onDragStart = { dragFraction = fraction; dragDistance = 0f },
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            dragDistance += amount
                            dragFraction = ((dragFraction ?: fraction) + amount / currentTravel).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            val collapse = when {
                                dragDistance > 64.dp.toPx() -> true
                                dragDistance < -64.dp.toPx() -> false
                                else -> (dragFraction ?: fraction) >= 0.5f
                            }
                            if (collapse) currentMinimize() else currentExpand()
                            dragFraction = null
                        },
                        onDragCancel = { dragFraction = null }
                    )
                }
                .pointerInput(player) {
                    detectHorizontalDragGestures(
                        onDragStart = { horizontalDrag = 0f },
                        onHorizontalDrag = { change, amount ->
                            if (!currentMinimized) { change.consume(); horizontalDrag += amount }
                        },
                        onDragEnd = {
                            // Match the viewer's existing direction: right = next, left = previous.
                            if (!currentMinimized && kotlin.math.abs(horizontalDrag) > 64.dp.toPx()) {
                                currentSwipe(horizontalDrag > 0f)
                            }
                        },
                        onDragCancel = { horizontalDrag = 0f }
                    )
                }
        ) {
            MediaViewer(player, audioOnly, fraction, title, onExpand, onClose)
        }
    }
}

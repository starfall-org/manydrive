package com.starfall.gsadrive.shared.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

private data class FolderTransitionTarget(
    val key: String,
    val depth: Int
)

@Composable
internal fun FolderZoomTransition(
    key: String,
    depth: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedContent(
        targetState = FolderTransitionTarget(key, depth),
        transitionSpec = {
            val openingFolder = targetState.depth >= initialState.depth
            val enterScale = if (openingFolder) 0.98f else 1.02f
            val exitScale = if (openingFolder) 1.02f else 0.98f

            (scaleIn(
                initialScale = enterScale,
                animationSpec = tween(320, easing = FastOutSlowInEasing)
            ) + fadeIn(tween(320, easing = FastOutSlowInEasing))).togetherWith(
                scaleOut(
                    targetScale = exitScale,
                    animationSpec = tween(320, easing = FastOutSlowInEasing)
                ) + fadeOut(tween(320, easing = FastOutSlowInEasing))
            )
        },
        modifier = modifier,
        label = "Folder zoom"
    ) {
        content()
    }
}

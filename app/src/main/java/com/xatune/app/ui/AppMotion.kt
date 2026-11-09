package com.xatune.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** Finite Compose animations inherit Android's animator duration scale, including zero. */
object AppMotion {
    const val EnterMillis = 280
    const val ExitMillis = 140
    val Easing = CubicBezierEasing(.2f, 0f, 0f, 1f)
}

/**
 * Animate only the entering page. The previous page is disposed immediately, so WebViews,
 * location/file callbacks and back handlers are never duplicated during navigation.
 */
@Composable internal fun <T> ScreenTransition(target: T, modifier: Modifier = Modifier, direction: Int = 1, content: @Composable (T) -> Unit) {
    key(target) {
        val progress = remember { Animatable(0f) }
        LaunchedEffect(Unit) { progress.animateTo(1f, tween(AppMotion.EnterMillis, easing = AppMotion.Easing)) }
        Box(modifier.fillMaxSize().graphicsLayer {
            alpha = progress.value
            translationX = direction * 24.dp.toPx() * (1f - progress.value)
        }) { content(target) }
    }
}

/** Used inside finite-height columns; outgoing progress/content collapses smoothly. */
@Composable internal fun Reveal(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(visible,
        enter = fadeIn(tween(AppMotion.EnterMillis)) + expandVertically(tween(AppMotion.EnterMillis, easing = AppMotion.Easing)),
        exit = fadeOut(tween(AppMotion.ExitMillis)) + shrinkVertically(tween(AppMotion.EnterMillis, easing = AppMotion.Easing))) {
        Column { content() }
    }
}

@Composable internal fun LoadingProgress(visible: Boolean) {
    Reveal(visible) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
}

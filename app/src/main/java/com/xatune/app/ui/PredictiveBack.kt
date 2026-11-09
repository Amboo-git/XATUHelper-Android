package com.xatune.app.ui

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Only in-app destinations consume back. At the root Android owns back-to-home. */
@Composable
internal fun Modifier.predictiveBack(enabled: Boolean = true, onBack: () -> Unit): Modifier {
    val progress = remember { Animatable(0f) }
    var edge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    PredictiveBackHandler(enabled) { events ->
        try {
            events.collect { event ->
                edge = event.swipeEdge
                progress.snapTo(event.progress.coerceIn(0f, 1f))
            }
            progress.snapTo(0f)
            onBack()
        } catch (cancelled: CancellationException) {
            // A cancelled gesture must restore the current page without navigating.
            withContext(NonCancellable) { progress.animateTo(0f, tween(AppMotion.ExitMillis)) }
            throw cancelled
        }
    }
    return graphicsLayer {
        val fraction = progress.value
        scaleX = 1f - .08f * fraction
        scaleY = 1f - .08f * fraction
        translationX = (if (edge == BackEventCompat.EDGE_LEFT) 1 else -1) * 24.dp.toPx() * fraction
        shape = RoundedCornerShape((28 * fraction).dp)
        clip = fraction > 0f
    }
}

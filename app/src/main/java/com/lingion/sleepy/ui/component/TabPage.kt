package com.lingion.sleepy.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Switches tab content without composing the outgoing screen beside the new one. The incoming
 * page gets a subtle layer-only rise and scale, so navigation still feels animated without
 * drawing two expensive page trees at once.
 */
@Composable
internal fun <T> SinglePageTabContent(
    targetState: T,
    content: @Composable (T) -> Unit,
) {
    var hasDisplayedPage by remember { mutableStateOf(false) }
    val progress = remember(targetState) { Animatable(if (hasDisplayedPage) 0f else 1f) }
    SideEffect { hasDisplayedPage = true }

    val motionSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val travelDistancePx = with(LocalDensity.current) { 8.dp.toPx() }
    LaunchedEffect(targetState) {
        progress.animateTo(1f, animationSpec = motionSpec)
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        key(targetState) {
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    val fraction = progress.value
                    val scale = SleepyContentScaleStart + (1f - SleepyContentScaleStart) * fraction
                    scaleX = scale
                    scaleY = scale
                    translationY = (1f - fraction) * travelDistancePx
                }
            ) {
                content(targetState)
            }
        }
    }
}

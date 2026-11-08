package com.lingion.sleepy.ui.component

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** Opaque while entering, including when an unfinished exit is reversed. */
@Composable
internal fun AnimatedContentScope.TabPage(content: @Composable () -> Unit) {
    val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val exitAlpha by transition.animateFloat(
        transitionSpec = { effectsSpec },
        label = "tab-exit-alpha",
    ) { state -> if (state == EnterExitState.PostExit) 0f else 1f }
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer {
                // AnimatedContent otherwise reverses a partly faded exit back toward 1,
                // letting the outgoing page show through the returning page in the meantime.
                alpha = if (transition.targetState == EnterExitState.Visible) 1f else exitAlpha
            }
            .background(MaterialTheme.colorScheme.background),
    ) {
        content()
    }
}

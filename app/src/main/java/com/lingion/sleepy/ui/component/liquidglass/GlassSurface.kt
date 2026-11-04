package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Foreground remains in its original draw/clip/z-order; only explicit sources are sampled. */
@Composable
fun GlassSurface(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    check(!LocalGlassSourceContent.current) { "Glass consumers cannot be captured as ordinary background" }
    Box(modifier, content = content)
}

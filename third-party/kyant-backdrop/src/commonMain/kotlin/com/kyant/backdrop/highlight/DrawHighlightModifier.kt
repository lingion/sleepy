// SleepDown extension: reuse upstream highlight drawing without a sampled backdrop.
package com.kyant.backdrop.highlight

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import com.kyant.backdrop.internal.ShapeProvider

/** Draws only the normal Kyant edge; it never records or samples scene content. */
fun Modifier.drawHighlight(
    shape: () -> Shape,
    highlight: () -> Highlight? = { Highlight.Default }
): Modifier = then(HighlightElement(ShapeProvider(shape), highlight))

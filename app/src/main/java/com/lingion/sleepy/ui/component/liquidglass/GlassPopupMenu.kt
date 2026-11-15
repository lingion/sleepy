package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.flow.first

internal val LocalGlassMenuAcceptsInput = compositionLocalOf { true }

/** Stop retaining an invisible focusable window on the descending exit branch. */
internal fun glassMenuExitOutputGone(progress: Float, velocity: Float): Boolean =
    progress.isFinite() && velocity.isFinite() && progress <= 1f / 255f && velocity <= 0f

/** Shared empirical spring for in-window menus; not native frame-equivalence. */
internal fun glassMenuEnter(origin: TransformOrigin): EnterTransition =
    scaleIn(spring(dampingRatio = .75f, stiffness = 323f), initialScale = .1f, transformOrigin = origin) +
        fadeIn(spring(dampingRatio = .75f, stiffness = 323f))

internal fun glassMenuExit(origin: TransformOrigin): ExitTransition =
    scaleOut(spring(dampingRatio = .8f, stiffness = 164f), targetScale = .1f, transformOrigin = origin) +
        fadeOut(spring(dampingRatio = .8f, stiffness = 164f))

/**
 * One popup lifetime and contour for ordinary glass menus. Material's dropdown
 * owns a separate scale/fade transition and Surface shadow; nesting glass inside
 * that Surface produced two unrelated exit paths and a surviving rectangular rim.
 * This reuses the measured language-menu spring, without claiming that every
 * native context-menu invocation follows the language menu's vertical morph.
 */
@Composable
internal fun GlassPopupMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier,
    materialModifier: Modifier,
    offset: DpOffset,
    scrollState: ScrollState,
    properties: PopupProperties,
    shape: Shape,
    containerColor: Color,
    border: BorderStroke?,
    content: @Composable ColumnScope.() -> Unit,
) {
    var retained by remember { mutableStateOf(false) }
    var positioned by remember { mutableStateOf(false) }
    var origin by remember { mutableStateOf(TransformOrigin(.5f, 0f)) }
    val progress = remember { SpringState(0f, rest = .0001) }
    LaunchedEffect(expanded) {
        if (expanded) retained = true
        if (!retained) return@LaunchedEffect
        if (expanded) snapshotFlow { positioned }.first { it }
        progress.springTo(if (expanded) 1f else 0f,
            if (expanded) LanguageMenuMotion.OpenSpring else LanguageMenuMotion.CloseSpring)
        val durationScale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
        if (durationScale <= 0f) progress.jumpTo(if (expanded) 1f else 0f)
        else {
            var previous = androidx.compose.runtime.withFrameNanos { it }
            while (progress.isRunning && (expanded || !glassMenuExitOutputGone(progress.value, progress.velocity))) {
                val now = androidx.compose.runtime.withFrameNanos { it }
                progress.tickDelta((now - previous) / 1_000_000_000.0 / durationScale)
                previous = now
            }
        }
        if (!expanded) {
            retained = false
            positioned = false
        }
    }
    if (!expanded && !retained) return
    val density = LocalDensity.current
    val windowSize = LocalWindowInfo.current.containerSize
    val safe = WindowInsets.safeDrawing
    val top = safe.getTop(density)
    val bottom = safe.getBottom(density)
    val horizontalMargin = with(density) { 16.dp.roundToPx() }
    val verticalMargin = with(density) { 16.dp.roundToPx() }
    val offsetPx = with(density) { IntOffset(offset.x.roundToPx(), offset.y.roundToPx()) }
    val maxHeight = with(density) {
        (windowSize.height - top - bottom - 2 * verticalMargin).coerceAtLeast(1).toDp()
    }
    val position = remember(offsetPx, horizontalMargin, verticalMargin, top, bottom) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
                layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val maxX = (windowSize.width - horizontalMargin - popupContentSize.width).coerceAtLeast(horizontalMargin)
                val preferredX = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.left + offsetPx.x
                    else anchorBounds.right - popupContentSize.width - offsetPx.x
                val x = preferredX.coerceIn(horizontalMargin, maxX)
                val minY = top + verticalMargin
                val maxY = (windowSize.height - bottom - verticalMargin - popupContentSize.height).coerceAtLeast(minY)
                val below = anchorBounds.bottom + offsetPx.y
                val above = anchorBounds.top - popupContentSize.height + offsetPx.y
                val y = (if (below <= maxY) below else above).coerceIn(minY, maxY)
                origin = TransformOrigin(
                    ((anchorBounds.center.x - x).toFloat() / popupContentSize.width.coerceAtLeast(1)).coerceIn(0f, 1f),
                    if (y >= anchorBounds.bottom) 0f else if (y + popupContentSize.height <= anchorBounds.top) 1f else
                        ((anchorBounds.center.y - y).toFloat() / popupContentSize.height.coerceAtLeast(1)).coerceIn(0f, 1f),
                )
                positioned = true
                return IntOffset(x, y)
            }
        }
    }
    Popup(popupPositionProvider = position, onDismissRequest = onDismissRequest, properties = properties) {
        CompositionLocalProvider(LocalGlassMenuAcceptsInput provides expanded) {
        Column(modifier
            .widthIn(min = 112.dp, max = 280.dp)
            .heightIn(max = maxHeight)
            .width(IntrinsicSize.Max)
            // Apply the transform outside the optical node and foreground so
            // the pane, clip, border and rows all leave on the same frame.
            .graphicsLayer {
                transformOrigin = origin
                val scale = .1f + .9f * progress.value
                scaleX = scale
                scaleY = scale
                alpha = progress.value.coerceIn(0f, 1f)
                clip = false
            }
            .then(materialModifier)
            .clip(shape)
            .then(if (containerColor == Color.Unspecified) Modifier else Modifier.background(containerColor, shape))
            .then(if (border == null) Modifier else Modifier.border(border, shape))
            .verticalScroll(scrollState)
            .padding(vertical = 10.dp),
            content = content,
        )
        }
    }
}

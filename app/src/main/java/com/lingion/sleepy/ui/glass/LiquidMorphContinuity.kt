package com.lingion.sleepy.ui.glass

import androidx.compose.ui.geometry.Rect
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.MorphCurveContinuity

/** Sampled only at direction changes. The last channel keeps the shell and content taper joined. */
internal fun LiquidMorphFrame.curveValues(taper: Float): FloatArray = floatArrayOf(
    rect.left, rect.top, rect.right, rect.bottom, cornerRadiusPx, trajectoryProgress, shapeProgress,
    sourceScale, content.sourceAlpha, content.destinationSurfaceAlpha, content.destinationContentAlpha,
    content.sourceBlurPx, content.destinationBlurPx, taper
)

internal fun LiquidMorphFrame.withCurveContinuity(curve: MorphCurveContinuity, p: Float): LiquidMorphFrame {
    if (!curve.isActive) return this
    fun v(i: Int, original: Float) = curve.value(i, p, original)
    val left = v(0, rect.left)
    val top = v(1, rect.top)
    val trajectory = v(5, trajectoryProgress).coerceIn(0f, 1f)
    val shape = v(6, shapeProgress).coerceIn(0f, 1f)
    return copy(
        rect = Rect(left, top, v(2, rect.right).coerceAtLeast(left + 1f), v(3, rect.bottom).coerceAtLeast(top + 1f)),
        cornerRadiusPx = v(4, cornerRadiusPx).coerceAtLeast(0f),
        trajectoryProgress = trajectory, shapeProgress = shape,
        motion = motion.copy(trajectory = motion.trajectory.copy(progress = trajectory), shape = motion.shape.copy(progress = shape)),
        sourceScale = v(7, sourceScale).coerceAtLeast(0f),
        content = content.copy(
            sourceAlpha = v(8, content.sourceAlpha).coerceIn(0f, 1f),
            destinationSurfaceAlpha = v(9, content.destinationSurfaceAlpha).coerceIn(0f, 1f),
            destinationContentAlpha = v(10, content.destinationContentAlpha).coerceIn(0f, 1f),
            sourceBlurPx = v(11, content.sourceBlurPx).coerceAtLeast(0f),
            destinationBlurPx = v(12, content.destinationBlurPx).coerceAtLeast(0f)
        )
    )
}

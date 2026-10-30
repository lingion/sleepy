@file:Suppress("unused")

package com.xiaomanjun.sleepdownschedule.core.ui.designsystem

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp

// ── NativeCapsuleButtonRequest ───────────────────────────────────────────────

internal data class NativeCapsuleButtonRequest(
    val onClick: () -> Unit = {},
    val modifier: Modifier = Modifier,
    val height: Dp = Dp.Unspecified,
    val contentPadding: PaddingValues = PaddingValues(),
    val dark: Boolean = false,
    val enabled: Boolean = true,
    val primaryColor: Color = Color.Unspecified,
    val acceptsGesture: (Size, Offset) -> Boolean = { _, _ -> true },
    val preserveMaterialWhenDisabled: Boolean = false
)

internal val LocalNativeCapsuleButtonRenderer = compositionLocalOf<
    (@Composable (NativeCapsuleButtonRequest, @Composable RowScope.() -> Unit) -> Boolean)?
> { null }

// ── NativeMaterialSurfaceRole & Request ──────────────────────────────────────

internal enum class NativeMaterialSurfaceRole { Content, Dialog, Form, HomeDock, HomeNavigation }

internal data class NativeMaterialSurfaceRequest(
    val modifier: Modifier = Modifier,
    val shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(0),
    val dark: Boolean = false,
    val renderSurface: Boolean = true,
    val materialVisible: Boolean = true,
    val onClick: (() -> Unit)? = null,
    val onDrawTint: (DrawScope.() -> Unit)? = null,
    val shapeProvider: (() -> Shape)? = null,
    val surfaceAlpha: () -> Float = { 1f },
    val edgeEffects: Boolean = true,
    val blurRadiusScale: Float = 1f,
    val tintAlphaScale: Float = 1f,
    val role: NativeMaterialSurfaceRole = NativeMaterialSurfaceRole.Content,
    val followTouch: Boolean = true,
    val followLightRadius: Dp? = null,
    val elasticTouchFeedback: Boolean = false,
    val followLightIntensityScale: Float = 1f
)

internal val LocalNativeMaterialSurfaceRenderer =
    compositionLocalOf<(@Composable (NativeMaterialSurfaceRequest, @Composable () -> Unit) -> Boolean)?> { null }

internal val LocalNativeMaterialSurfaceActive = compositionLocalOf { false }
internal val LocalNativeMaterialForeground = compositionLocalOf { Color.Unspecified }

// ── NativeCourseCardMode & Renderer ──────────────────────────────────────────

internal typealias NativeCourseCardRequest = NativeMaterialSurfaceRequest

@Stable
internal class NativeCourseCardMode(
    private val enabledState: State<Boolean> = mutableStateOf(false),
    val onEnabledChange: (Boolean) -> Unit = {},
    private val tintState: State<Float> = mutableStateOf(0f),
    private val blurScaleState: State<Float> = mutableStateOf(1f),
    private val defaultBlurRadiusPx: Float = 0f,
    val onTintStrengthChange: (Float, Boolean) -> Unit = { _, _ -> },
    val onBlurRadiusScaleChange: (Float, Boolean) -> Unit = { _, _ -> },
    val onPreviewFinished: () -> Unit = {}
) {
    val enabled: Boolean get() = enabledState.value
    val tintStrength: Float get() = tintState.value
    val blurRadiusScale: Float get() = blurScaleState.value.coerceIn(0.2f, 2f)
    val blurRadiusPx: Float get() = defaultBlurRadiusPx * blurRadiusScale
    val blurPercent: Float get() = nativeCardBlurPercent(blurRadiusScale)
    fun onBlurPercentChange(percent: Float, commit: Boolean) =
        onBlurRadiusScaleChange(nativeCardBlurScale(percent), commit)
}

internal fun nativeCardBlurScale(percent: Float): Float = percent.coerceIn(0f, 100f).let {
    if (it <= 50f) 0.2f + it / 50f * 0.8f else 1f + (it - 50f) / 50f
}

internal fun nativeCardBlurPercent(scale: Float): Float = scale.coerceIn(0.2f, 2f).let {
    if (it <= 1f) (it - 0.2f) / 0.8f * 50f else 50f + (it - 1f) * 50f
}

internal val LocalNativeCourseCardMode = compositionLocalOf<NativeCourseCardMode?> { null }
internal val LocalNativeCourseCardRenderer =
    compositionLocalOf<(@Composable (NativeCourseCardRequest, @Composable () -> Unit) -> Boolean)?> { null }

// ── NativeProgressiveBlur ────────────────────────────────────────────────────

internal fun interface NativeProgressiveBlur {
    fun create(startRadius: Float, endRadius: Float, vertical: Boolean): androidx.compose.ui.graphics.RenderEffect?
}

internal val LocalNativeProgressiveBlur = compositionLocalOf<NativeProgressiveBlur?> { null }
internal val LocalToolbarBackdropBlurred = compositionLocalOf { false }

// ── NativeSliderRequest & NativeSwitchRequest ────────────────────────────────

internal data class NativeSliderRequest(
    val value: () -> Float = { 0f },
    val range: ClosedFloatingPointRange<Float> = 0f..1f,
    val onPreview: (Float) -> Unit = {},
    val onPreviewMode: (Boolean) -> Unit = {},
    val onCommit: (Float) -> Unit = {},
    val modifier: Modifier = Modifier,
    val snapValue: Float? = null,
    val enabled: Boolean = true
)

internal data class NativeSwitchRequest(
    val checked: Boolean = false,
    val onCheckedChange: (Boolean) -> Unit = {},
    val modifier: Modifier = Modifier,
    val enabled: Boolean = true,
    val compact: Boolean = false
)

internal val LocalNativeSliderRenderer = compositionLocalOf<(@Composable (NativeSliderRequest) -> Boolean)?> { null }
internal val LocalNativeSwitchRenderer = compositionLocalOf<(@Composable (NativeSwitchRequest) -> Boolean)?> { null }

// ── usesNativeMaterialMotion ─────────────────────────────────────────────────

@Composable
internal fun usesNativeMaterialMotion(): Boolean =
    LocalNativeMaterialSurfaceRenderer.current != null || LocalNativeCourseCardMode.current?.enabled == true

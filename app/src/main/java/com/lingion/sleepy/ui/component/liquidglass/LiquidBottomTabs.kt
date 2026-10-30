// Based on Kyant0/AndroidLiquidGlass catalog components, Apache-2.0.
// Modified for SleepDown-Schedule.
package com.lingion.sleepy.ui.component.liquidglass

import com.lingion.sleepy.ui.glass.ui.*

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.lingion.sleepy.ui.component.liquidglass.DampedDragAnimation
import com.lingion.sleepy.ui.component.liquidglass.InteractiveHighlight
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.lingion.sleepy.ui.glass.GlassBackdropDomain
import com.lingion.sleepy.ui.glass.GlassEffectFrame
import com.lingion.sleepy.ui.glass.GlassMaterialRole
import com.lingion.sleepy.ui.glass.GlassMaterialSpec
import com.lingion.sleepy.ui.glass.glassBackdropProducer
import com.lingion.sleepy.ui.glass.rememberGlassCombinedBackdrop
import com.lingion.sleepy.ui.glass.rememberGlassLayerBackdrop
import com.lingion.sleepy.ui.glass.rememberGlassSurfaceDescriptor
import com.lingion.sleepy.ui.glass.sleepDownGlassSurface
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

@Composable
fun LiquidBottomTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    containerHeight: Dp = 64f.dp,
    indicatorHeight: Dp = 56f.dp,
    horizontalPadding: Dp = 4f.dp,
    blurRadius: Dp = 8f.dp,
    containerAlpha: Float = 0.4f,
    lensHeight: Dp = 34f.dp,
    lensAmount: Dp = 40f.dp,
    indicatorWidthOverflow: Dp = 0.dp,
    indicatorHeightOverflow: Dp = 0.dp,
    indicatorLensHeight: Dp? = null,
    indicatorLensAmount: Dp? = null,
    officialHighlightAlpha: Float = 1f,
    officialShadowAlpha: Float = 1f,
    officialInnerShadowAlpha: Float = 1f,
    containerShadowEnabled: Boolean = true,
    indicatorShadowEnabled: Boolean = true,
    indicatorInnerShadowEnabled: Boolean = true,
    pressedContentScale: Float = 1.2f,
    movingAccentContent: Boolean = true,
    chromaticAberrationEnabled: Boolean = false,
    isLightThemeOverride: Boolean? = null,
    lightContainerColor: Color = Color(0xFFFAFAFA),
    lightAccentColor: Color = Color(0xFF0088FF),
    darkAccentColor: Color = Color(0xFF0091FF),
    // 选中态强调色 = 主题色(FlClash navigation_dock.dart: tint = colorScheme.primary,
    // 明暗两制皆然)。调用方传 MaterialTheme.colorScheme.primary;默认值仅兜底。
    accentColorOverride: Color? = null,
    useOfficialGlassParameters: Boolean = false,
    containerSurfaceEnabled: Boolean = true,
    leadingWidth: Dp = 0.dp,
    leadingContent: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    val superPerformance = com.lingion.sleepy.ui.glass.performance.AppMaterialPreferences.isSuperPerformance
    val isLightTheme = isLightThemeOverride ?: !isSystemInDarkTheme()
    // Theme ownership can change when a dock moves between wallpaper-adaptive Home and the app
    // themed settings page. Keep one Kyant surface alive and crossfade its material after the tab
    // settles; recreating light/dark surfaces at release produces a visible one-frame flash.
    val themeBlend by animateFloatAsState(
        targetValue = if (isLightTheme) 1f else 0f,
        animationSpec = tween(220),
        label = "LiquidBottomTabsThemeBlend"
    )
    val animatedContainerAlpha by animateFloatAsState(
        targetValue = containerAlpha,
        animationSpec = tween(220),
        label = "LiquidBottomTabsContainerAlpha"
    )
    val accentColor by animateColorAsState(
        targetValue = accentColorOverride
            ?: if (isLightTheme) lightAccentColor else darkAccentColor,
        animationSpec = tween(220),
        label = "LiquidBottomTabsAccentColor"
    )
    val lightContainerSurface = lightContainerColor.copy(alpha = animatedContainerAlpha)
    val darkContainerSurface = Color(0xFF121212).copy(alpha = animatedContainerAlpha)

    val tabsBackdrop = rememberGlassLayerBackdrop(
        domain = GlassBackdropDomain.ChromeCombined,
        providerId = "liquid-bottom-tabs-accent-content"
    )
    val containerMaterial = remember(
        blurRadius,
        lensHeight,
        lensAmount,
        chromaticAberrationEnabled,
        containerShadowEnabled
    ) {
        GlassMaterialSpec(
            role = GlassMaterialRole.Control,
            blur = blurRadius,
            lensHeight = lensHeight,
            lensAmount = lensAmount,
            surfaceAlpha = 1f,
            borderAlpha = 0f,
            highlightAlpha = 1f,
            shadowAlpha = if (containerShadowEnabled) 1f else 0f,
            innerShadowAlpha = 0f,
            chromaticAberration = chromaticAberrationEnabled,
            depthEffect = false
        )
    }
    val movingAccentMaterial = remember(
        containerMaterial,
        useOfficialGlassParameters,
        officialHighlightAlpha
    ) {
        containerMaterial.copy(
            highlightAlpha = if (useOfficialGlassParameters) officialHighlightAlpha else 0.45f
        )
    }
    val selectedLensHeight = indicatorLensHeight ?: if (useOfficialGlassParameters) 10f.dp else 22f.dp
    val selectedLensAmount = indicatorLensAmount ?: if (useOfficialGlassParameters) 14f.dp else 31f.dp
    val indicatorMaterial = remember(
        selectedLensHeight,
        selectedLensAmount,
        useOfficialGlassParameters,
        officialHighlightAlpha,
        officialShadowAlpha,
        officialInnerShadowAlpha,
        indicatorShadowEnabled,
        indicatorInnerShadowEnabled
    ) {
        GlassMaterialSpec(
            role = GlassMaterialRole.Control,
            blur = 0.dp,
            lensHeight = selectedLensHeight,
            lensAmount = selectedLensAmount,
            surfaceAlpha = if (useOfficialGlassParameters) 0.1f else 0.07f,
            borderAlpha = 0f,
            highlightAlpha = if (useOfficialGlassParameters) officialHighlightAlpha else 0.45f,
            shadowAlpha = if (indicatorShadowEnabled) {
                if (useOfficialGlassParameters) officialShadowAlpha else 1f
            } else {
                0f
            },
            innerShadowAlpha = if (indicatorInnerShadowEnabled) {
                if (useOfficialGlassParameters) officialInnerShadowAlpha else 1f
            } else {
                0f
            },
            chromaticAberration = if (useOfficialGlassParameters) true else chromaticAberrationEnabled,
            depthEffect = false,
            useVibrancy = false
        )
    }
    val containerDescriptor = rememberGlassSurfaceDescriptor(
        debugLabel = "LiquidBottomTabsContainer",
        domain = GlassBackdropDomain.ChromeCombined,
        materialRole = GlassMaterialRole.Control,
        sceneKey = "liquid-bottom-tabs-container"
    )
    val movingAccentDescriptor = rememberGlassSurfaceDescriptor(
        debugLabel = "LiquidBottomTabsMovingAccent",
        domain = GlassBackdropDomain.ChromeCombined,
        materialRole = GlassMaterialRole.Control,
        sceneKey = "liquid-bottom-tabs-moving-accent"
    )
    val indicatorDescriptor = rememberGlassSurfaceDescriptor(
        debugLabel = "LiquidBottomTabsIndicator",
        domain = GlassBackdropDomain.ChromeCombined,
        materialRole = GlassMaterialRole.Control,
        sceneKey = "liquid-bottom-tabs-indicator"
    )

    BoxWithConstraints(
        modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - horizontalPadding.toPx() * 2f - leadingWidth.toPx()) / tabsCount
        }
        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density, constraints.maxWidth) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) {
                    4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        val selectedIndex = selectedTabIndex().fastCoerceIn(0, tabsCount - 1)
        val currentOnTabSelected by rememberUpdatedState(onTabSelected)
        val currentTabWidth by rememberUpdatedState(tabWidth)
        val currentIsLtr by rememberUpdatedState(isLtr)
        val currentLeadingInset by rememberUpdatedState(with(density) { (horizontalPadding + leadingWidth).toPx() })
        var dragging by remember(tabsCount) { mutableStateOf(false) }
        // FlClash navigation_dock.dart 1083/1108: HapticFeedback.selectionClick() 在
        // 拖拽扫过新 tab 与 fling 换目标两处触发; 普通点按无震动。selectionClick 在
        // Android = PlatformChannel 的 HapticFeedbackConstants.CLOCK_TICK(非 Compose
        // VirtualKey), 用 LocalView 直调同一常量保持 1:1。
        val hapticView = androidx.compose.ui.platform.LocalView.current
        val lastHapticIndex = remember(tabsCount) { intArrayOf(selectedTabIndex()) }
        val dampedDragAnimation = remember(animationScope, tabsCount) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedTabIndex().toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = { dragging = true },
                onDragStopped = {
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    // FlClash _fling: 松手惯性换目标与按压位不同 → 再补一次 tick
                    if (targetIndex != lastHapticIndex[0]) {
                        lastHapticIndex[0] = targetIndex
                        hapticView.performHapticFeedback(
                            android.view.HapticFeedbackConstants.CLOCK_TICK,
                            android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                        )
                    }
                    dragging = false
                    animateToValue(targetIndex.toFloat())
                    // The Topbar is first composed during the sidebar morph, when navigation is
                    // disabled. Dispatch through the current callback, never that initial closure.
                    currentOnTabSelected(targetIndex)
                    animationScope.launch {
                        offsetAnimation.animateTo(
                            0f,
                            spring(1f, 300f, 0.5f)
                        )
                    }
                },
                onDrag = { _, dragAmount ->
                    val next = (targetValue + dragAmount.x / currentTabWidth * if (currentIsLtr) 1f else -1f)
                        .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                    updateValue(next)
                    // 扫过新 tab 中心 = FlClash _handlePanUpdate 的 index != _pressedIndex 分支
                    val crossedIndex = next.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    if (crossedIndex != lastHapticIndex[0]) {
                        lastHapticIndex[0] = crossedIndex
                        hapticView.performHapticFeedback(
                            android.view.HapticFeedbackConstants.CLOCK_TICK,
                            android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                        )
                    }
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            )
        }
        LaunchedEffect(selectedIndex, dragging) {
            if (!dragging) {
                // 点按(无震动)后同步基准, 防下次拖拽首帧误报 crossing
                lastHapticIndex[0] = selectedIndex
                if (abs(dampedDragAnimation.targetValue - selectedIndex.toFloat()) > 0.01f) {
                    dampedDragAnimation.animateToValue(selectedIndex.toFloat())
                }
            }
        }

        val interactiveHighlight = remember(animationScope, dampedDragAnimation) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, _ ->
                    // This light is drawn in the already-translated container. Include the
                    // leading control/padding and do not apply panelOffset a second time.
                    val center = currentLeadingInset + (dampedDragAnimation.value + 0.5f) * currentTabWidth
                    Offset(
                        if (currentIsLtr) center else size.width - center,
                        size.height / 2f
                    )
                }
            )
        }

        Row(
            Modifier
                .graphicsLayer {
                    translationX = panelOffset
                }
                .sleepDownGlassSurface(
                    backdrop = backdrop,
                    descriptor = containerDescriptor,
                    material = containerMaterial,
                    fallbackColor = com.lingion.sleepy.ui.glass.flatControlColor(!isLightTheme),
                    renderEnabled = { containerSurfaceEnabled },
                    shape = { Capsule() },
                    effectFrame = GlassEffectFrame(blur = null),
                    effectsOverride = {
                        vibrancy()
                        blur(blurRadius.toPx())
                        lens(
                            lensHeight.toPx(),
                            lensAmount.toPx(),
                            chromaticAberration = chromaticAberrationEnabled
                        )
                    },
                    highlightOverride = { Highlight.Default },
                    additionalLayerBlock = {
                        val progress = dampedDragAnimation.pressProgress
                        val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
                        scaleX = scale
                        scaleY = scale
                    },
                    shadowOverride = if (containerShadowEnabled) ({ Shadow.Default }) else null,
                    onDrawSurface = {
                        drawRect(darkContainerSurface, alpha = 1f - themeBlend)
                        drawRect(lightContainerSurface, alpha = themeBlend)
                    }
                )
                .then(if (superPerformance) Modifier.drawBehind {
                    val inset = (horizontalPadding + leadingWidth).toPx()
                    val logicalX = inset + dampedDragAnimation.value * tabWidth
                    val pillHeight = indicatorHeight.toPx()
                    val origin = Offset(if (isLtr) logicalX else size.width - logicalX - tabWidth, (size.height - pillHeight) / 2f)
                    val pillSize = Size(tabWidth, pillHeight)
                    val radius = CornerRadius(pillHeight / 2f)
                    drawRoundRect(Color(0xFF9E9E9E).copy(alpha = 0.24f), origin, pillSize, radius)
                    val stroke = 1.dp.toPx()
                    drawRoundRect(Color(0xFF9E9E9E).copy(alpha = 0.50f),
                        topLeft = origin + Offset(stroke / 2, stroke / 2),
                        size = Size(pillSize.width - stroke, pillSize.height - stroke),
                        cornerRadius = CornerRadius((pillHeight - stroke) / 2),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                } else interactiveHighlight.modifier)
                .height(containerHeight)
                .fillMaxWidth()
                .padding(horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leadingContent?.invoke(this)
            content()
        }

        if (movingAccentContent && !superPerformance) {
            CompositionLocalProvider(
                LocalLiquidBottomTabInputEnabled provides false,
                LocalLiquidBottomTabScale provides {
                    lerp(1f, pressedContentScale, dampedDragAnimation.pressProgress)
                }
            ) {
                Row(
                    Modifier
                        .clearAndSetSemantics {}
                        .alpha(0f)
                        .glassBackdropProducer(tabsBackdrop)
                        .graphicsLayer {
                            translationX = panelOffset
                        }
                        .then(if (containerSurfaceEnabled) Modifier.sleepDownGlassSurface(
                            backdrop = backdrop,
                            descriptor = movingAccentDescriptor,
                            material = movingAccentMaterial,
                            shape = { Capsule() },
                            effectFrame = GlassEffectFrame(blur = null),
                            effectsOverride = {
                                val progress = dampedDragAnimation.pressProgress
                                vibrancy()
                                blur(blurRadius.toPx())
                                lens(
                                    lensHeight.toPx() * progress,
                                    lensAmount.toPx() * progress,
                                    chromaticAberration = chromaticAberrationEnabled
                                )
                            },
                            highlightOverride = {
                                val progress = dampedDragAnimation.pressProgress
                                Highlight.Default.copy(alpha = progress * if (useOfficialGlassParameters) officialHighlightAlpha else 0.45f)
                            },
                            shadowOverride = if (containerShadowEnabled) ({ Shadow.Default }) else null,
                            onDrawSurface = {
                                drawRect(darkContainerSurface, alpha = 1f - themeBlend)
                                drawRect(lightContainerSurface, alpha = themeBlend)
                            }
                        ) else Modifier)
                        .then(interactiveHighlight.modifier)
                        .height(indicatorHeight)
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding)
                        .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (leadingWidth > 0.dp) androidx.compose.foundation.layout.Spacer(Modifier.width(leadingWidth))
                    content()
                }
            }
        }

        Box(
            Modifier
                .padding(start = horizontalPadding + leadingWidth, end = horizontalPadding)
                .graphicsLayer {
                    val progress = dampedDragAnimation.pressProgress
                    val widthOverflowPx = indicatorWidthOverflow.toPx() * progress
                    translationX =
                        if (isLtr) dampedDragAnimation.value * tabWidth + panelOffset - widthOverflowPx / 2f
                        else size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset + widthOverflowPx / 2f
                }
                .then(interactiveHighlight.gestureModifier)
                .then(dampedDragAnimation.modifier)
                .sleepDownGlassSurface(
                    backdrop = rememberGlassCombinedBackdrop(backdrop, tabsBackdrop),
                    descriptor = indicatorDescriptor,
                    material = indicatorMaterial,
                    renderEnabled = { !superPerformance },
                    shape = { Capsule() },
                    effectFrame = GlassEffectFrame(blur = null),
                    effectsOverride = {
                        val progress = dampedDragAnimation.pressProgress
                        lens(
                            selectedLensHeight.toPx() * progress,
                            selectedLensAmount.toPx() * progress,
                            chromaticAberration = if (useOfficialGlassParameters) true else chromaticAberrationEnabled
                        )
                    },
                    highlightOverride = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Default.copy(alpha = progress * if (useOfficialGlassParameters) officialHighlightAlpha else 0.45f)
                    },
                    shadowOverride = if (indicatorShadowEnabled) {
                        {
                            val progress = dampedDragAnimation.pressProgress
                            Shadow(alpha = progress * if (useOfficialGlassParameters) officialShadowAlpha else 1f)
                        }
                    } else null,
                    innerShadowOverride = if (indicatorInnerShadowEnabled) {
                        {
                            val progress = dampedDragAnimation.pressProgress
                            InnerShadow(
                                radius = 8f.dp * progress,
                                alpha = progress * if (useOfficialGlassParameters) officialInnerShadowAlpha else 1f
                            )
                        }
                    } else null,
                    additionalLayerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        val neutralAlpha = if (useOfficialGlassParameters) 0.1f else 0.07f
                        drawRect(
                            Color.White.copy(alpha = neutralAlpha),
                            alpha = (1f - themeBlend) * (1f - progress)
                        )
                        drawRect(
                            Color.Black.copy(alpha = neutralAlpha),
                            alpha = themeBlend * (1f - progress)
                        )
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    }
                )
                .height(indicatorHeight + indicatorHeightOverflow * dampedDragAnimation.pressProgress)
                .then(
                    Modifier.width(with(density) { tabWidth.toDp() } + indicatorWidthOverflow * dampedDragAnimation.pressProgress)
                )
        )
    }
}

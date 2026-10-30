package com.lingion.sleepy.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.lingion.sleepy.Tab
import com.lingion.sleepy.ui.component.GlassBackdropHolder
import com.lingion.sleepy.ui.component.GlassPillBar
import com.lingion.sleepy.ui.component.NavDockSpec
import com.lingion.sleepy.ui.component.liquidglass.LiquidBottomTab
import com.lingion.sleepy.ui.component.liquidglass.LiquidBottomTabs
import com.lingion.sleepy.ui.glass.GlassBackdropDomain
import com.lingion.sleepy.ui.glass.glassBackdropProducer
import com.lingion.sleepy.ui.glass.rememberGlassLayerBackdrop

/**
 * 液态玻璃底栏宿主 — 三种导航形态(悬浮 Dock / 贴底 / 平板宽屏)共用。
 *
 * 采样根: content 整棵子树经 [glassBackdropProducer] 录进一个 LayerBackdrop,
 * 玻璃胶囊用 kyant 引擎对该层做 vibrancy+blur+lens 折射。floating 只影响摆位:
 * Dock 悬起(12dp 底 float + 16dp 横边距), 贴底形态收进手势条安全区。
 * 外层负责 [androidx.compose.foundation.layout.windowInsetsPadding] 等自身 insets,
 * 这里不再二次消费 statusBars。
 */
@Composable
internal fun LiquidGlassNavHost(
    currentTab: Tab,
    onTabSelected: (Tab) -> Unit,
    updateNoticeVisible: Boolean,
    floating: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // 渲染引擎分流(2026-10-30): API 33+ = kyant-backdrop 根采样(官方稳定面);
    // API < 33 = FlClash 全量 AGSL 移植栈(GlassPillBar → DockGlassBar,
    // LiquidGlassEngine 需 31+, 26–30 引擎取不到 → 栈内逐 pane 短路 classic 实底)。
    val useLegacyGlass = android.os.Build.VERSION.SDK_INT < 33
    val density = LocalDensity.current
    val navBarsBottom = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
    // 滚动内容页尾余量: kyant = 胶囊高 + 摆位底距; AGSL 栈 = FlClash 内容 inset
    // (条高随字号 + 底距取 max(21dp, 手势条)), 与旧 dock 分支同式。
    val legacyReserve = NavDockSpec.contentInset(density, navBarsBottom)
    val bottomPad = if (floating) {
        (NavDockSpec.bottomFloat + 4.dp).coerceAtLeast(navBarsBottom + NavDockSpec.bottomFloat)
    } else {
        navBarsBottom + 6.dp
    }
    val horizontalPad = if (floating) NavDockSpec.horizontalMargin else 8.dp
    val reserveBottom = if (useLegacyGlass) legacyReserve else NavDockSpec.capsuleHeight + bottomPad
    val rootBackdrop: LayerBackdrop = rememberGlassLayerBackdrop(
        domain = GlassBackdropDomain.Content,
        providerId = "liquid-nav-root",
    )
    val legacyHolder = if (useLegacyGlass) remember { GlassBackdropHolder() } else null
    Box(modifier.fillMaxSize()) {
        CompositionLocalProvider(
            com.lingion.sleepy.ui.component.LocalNavExtraBottomPadding provides reserveBottom,
        ) {
            if (legacyHolder != null) {
                // AGSL 分支内容捕获: drawWithContent 把内容录进 GraphicsLayer,
                // 逐帧 toImageBitmap 写回 holder(一帧滞后 = FlClash _Replay 语义)。
                // 必须走 4 参显式 record(density, layoutDirection, size, block):
                // 便利重载沿用层旧尺寸(首帧 IntSize.Zero)→ toImageBitmap 抛异常
                // 被吞 → holder.bitmap 恒 null → 三 pane 永不绘制。
                val contentLayer = rememberGraphicsLayer()
                val captureOriginPx = remember { mutableStateOf(Offset.Zero) }
                LaunchedEffect(legacyHolder) {
                    while (true) {
                        withFrameMillis { }
                        try {
                            legacyHolder.bitmap = contentLayer.toImageBitmap().asAndroidBitmap()
                            legacyHolder.originRootPx = captureOriginPx.value
                        } catch (e: Exception) {
                            // 首帧层未就绪
                        }
                    }
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            contentLayer.record(
                                this,
                                layoutDirection,
                                IntSize(size.width.toInt(), size.height.toInt()),
                            ) {
                                this@drawWithContent.drawContent()
                            }
                            drawContent()
                        }
                        .onGloballyPositioned { c ->
                            captureOriginPx.value = Offset(c.positionInRoot().x, c.positionInRoot().y)
                        }
                ) {
                    content()
                }
            } else {
                Box(Modifier.fillMaxSize().glassBackdropProducer(rootBackdrop)) {
                    content()
                }
            }
        }
        // AGSL 栈自带摆位(edgeMargin 21dp / bottomDp=max(21, 手势条) / shadowRoom),
        // 外层不再加 padding, 否则双重底距。
        Box(
            if (legacyHolder != null) {
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            } else {
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = horizontalPad, vertical = bottomPad)
            }
        ) {
            if (legacyHolder != null) {
                GlassPillBar(
                    currentTab = currentTab,
                    onTabSelected = onTabSelected,
                    updateNoticeVisible = updateNoticeVisible,
                    backdropHolder = legacyHolder,
                )
            } else {
                LiquidGlassNavBar(
                    currentTab = currentTab,
                    onTabSelected = onTabSelected,
                    updateNoticeVisible = updateNoticeVisible,
                    backdrop = rootBackdrop,
                )
            }
        }
    }
}

/** 玻璃胶囊本体 + 四个 tab 内容(icon/label/更新红点)。高度几何沿用 [NavDockSpec]。 */
@Composable
internal fun LiquidGlassNavBar(
    currentTab: Tab,
    onTabSelected: (Tab) -> Unit,
    updateNoticeVisible: Boolean,
    backdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val isDark = colors.surface.luminance() < 0.5f
    val tabs = Tab.entries
    LiquidBottomTabs(
        selectedTabIndex = { tabs.indexOf(currentTab).coerceAtLeast(0) },
        onTabSelected = { onTabSelected(tabs[it]) },
        backdrop = backdrop,
        tabsCount = tabs.size,
        modifier = modifier.fillMaxWidth(),
        containerHeight = NavDockSpec.capsuleHeight,
        indicatorHeight = NavDockSpec.itemSeat,
        isLightThemeOverride = !isDark,
        lightAccentColor = colors.primary,
        darkAccentColor = colors.primary,
        // FlClash 对齐: 选中 tint = colorScheme.primary 明暗同色, 不走内置蓝
        accentColorOverride = colors.primary,
    ) {
        tabs.forEachIndexed { index, tab ->
            val selected = index == tabs.indexOf(currentTab)
            val label = stringResource(tab.labelRes)
            LiquidBottomTab(onClick = { onTabSelected(tab) }) {
                Box {
                    Icon(
                        tab.icon,
                        contentDescription = label,
                        tint = if (selected) colors.primary else colors.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                    if (tab == Tab.Mine && updateNoticeVisible) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .size(7.dp)
                                .background(colors.primary, androidx.compose.foundation.shape.CircleShape)
                                .semantics { contentDescription = "" },
                        )
                    }
                }
                Text(
                    label,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Visible,
                    color = if (selected) colors.primary else colors.onSurfaceVariant,
                )
            }
        }
    }
}

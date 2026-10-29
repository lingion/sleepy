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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.lingion.sleepy.Tab
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
    val rootBackdrop: LayerBackdrop = rememberGlassLayerBackdrop(
        domain = GlassBackdropDomain.Content,
        providerId = "liquid-nav-root",
    )
    val density = LocalDensity.current
    val navBarsBottom = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
    val bottomPad = if (floating) {
        (NavDockSpec.bottomFloat + 4.dp).coerceAtLeast(navBarsBottom + NavDockSpec.bottomFloat)
    } else {
        navBarsBottom + 6.dp
    }
    val horizontalPad = if (floating) NavDockSpec.horizontalMargin else 8.dp
    // 滚动内容页尾余量 = 胶囊高 + 胶囊底边距屏幕底的距离 (同原 dock 分支实测语义)。
    val reserveBottom = NavDockSpec.capsuleHeight + bottomPad
    Box(modifier.fillMaxSize()) {
        androidx.compose.runtime.CompositionLocalProvider(
            com.lingion.sleepy.ui.component.LocalNavExtraBottomPadding provides reserveBottom,
        ) {
            Box(Modifier.fillMaxSize().glassBackdropProducer(rootBackdrop)) {
                content()
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = horizontalPad, vertical = bottomPad)
        ) {
            LiquidGlassNavBar(
                currentTab = currentTab,
                onTabSelected = onTabSelected,
                updateNoticeVisible = updateNoticeVisible,
                backdrop = rootBackdrop,
            )
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

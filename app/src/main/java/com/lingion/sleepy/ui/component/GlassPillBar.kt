package com.lingion.sleepy.ui.component

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import com.lingion.sleepy.Tab

/**
 * 悬浮 Dock 几何快照 — FlClash `_dockLayout` 返回记录的等价物。
 * pitch=座位宽, lens=色块宽, inset=色块外缘→内容列起点,
 * centers=各座位中心(内容列坐标), span=内容列总宽。
 * onGloballyPositioned 一次性写入, 供 DockGlassBar 三层 pane 复用。
 */
data class DockGeo(
    val pitch: Float,
    val lens: Float,
    val inset: Float,
    val centers: List<Float>,
    val span: Float,
)

/**
 * 自研 AGSL 玻璃 Dock 宿主 — LiquidGlassNavHost 在 API 31–32(kyant 引擎需 33)
 * 或 kyant 根采样不可用时使用的完整 FlClash 移植分支。
 *
 * 结构 = 6e8790fe 旧 PillNavigationBar 玻璃分支的等价物, 但独立成文件,
 * 不改动经典底栏组件本体。本层只持两项跨帧状态:
 *  - geo: 实测 span 推出的弹性几何快照(DockGlassBar 内部 onMeasure 回报);
 *  - labelBasePx: 各 tab 文字基准宽(每槽滞回, 防 scale↔measured 闭环振荡)。
 * lens 位置/弹簧/折射全在 [DockGlassBar] 栈内, 这里不碰。
 *
 * 引擎不可用 / backdrop 未就绪 → DockGlassBar 内部逐 pane 短路退 classic 实底,
 * 骨架不崩(与 Dart v0.9.1 首帧语义一致)。
 */
@Composable
internal fun GlassPillBar(
    currentTab: Tab,
    onTabSelected: (Tab) -> Unit,
    updateNoticeVisible: Boolean,
    backdropHolder: GlassBackdropHolder,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val colors = MaterialTheme.colorScheme
    val tabs = Tab.entries
    val items = tabs.map {
        PillNavItemSpec(
            icon = it.icon,
            label = stringResource(it.labelRes),
            badge = it == Tab.Mine && updateNoticeVisible,
        )
    }
    val labelBasePx = remember(items.size) {
        mutableStateListOf<Float>().apply { repeat(items.size) { add(0f) } }
    }
    val widestLabelPx = labelBasePx.fold(0f) { acc, v -> maxOf(acc, v) }
    var geo by remember { mutableStateOf<DockGeo?>(null) }
    val isDark = colors.surface.luminance() < 0.5f

    DockGlassBar(
        items = items,
        selectedIndex = tabs.indexOf(currentTab).coerceAtLeast(0),
        onSelect = { onTabSelected(tabs[it]) },
        colors = colors,
        density = density,
        isDark = isDark,
        darkStrength = if (isDark) 3f else 1f,
        barH = NavDockSpec.heightOf(density),
        bottomDp = NavDockSpec.glassBottomFloat.coerceAtLeast(
            with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
        ),
        backdropHolder = backdropHolder,
        modifier = modifier,
        onBarGeometry = { _, _, _ -> },
        widestLabelPx = widestLabelPx,
        onWidestLabel = { slot, base ->
            if (slot in labelBasePx.indices && base > labelBasePx[slot] + 0.5f) {
                labelBasePx[slot] = base
            }
        },
        geo = geo,
        onGeo = { geo = it },
    )
}

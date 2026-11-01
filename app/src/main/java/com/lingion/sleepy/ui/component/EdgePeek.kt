package com.lingion.sleepy.ui.component

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lingion.sleepy.R

/**
 * 拖课到周缘自动翻页 — 全部几何/时序常量的单一真源。
 *
 * 数值出处(2026-10-27 官方文档与实现交叉验证, 用户裁决采纳):
 * - 停留 500ms 换页: ItemTouchHelper 边缘滚动与 pragmatic-drag-and-drop 的
 *   timeDampeningDurationMs(400) 同量级, Baymard 触控研究上限 500 — 取整 500;
 * - 边缘区 10dp【2026-10-30 用户裁决, 替换 2026-10-29「列带外缘 10dp」】: 触发线锚
 *   **可视表格外壳缘**, 左右各内缩同一 10dp — 镜像对称。旧判据锚列带缘: 右线贴屏缘
 *   (≈15dp) 而左线把整条时间列划进触发区 (≈61dp) — 「往下翻页要这个页边距, 往上翻页
 *   就不是」(用户原话)。落子安全不变: 末列/首列列心距各自触发线仍 ≈ 列宽/2 + 5dp,
 *   各列落子全程在区外 (Lawnchair verifyInsidePage 语义维持: 中心悬在本页可见列 =
 *   落子, 永不翻页; 翻页只属越页命中);
 * - 翻页动画 300ms + 曲线 (0.1,0.1,0,1): 预测性返回官方动效规格
 *   (developer.android.com/design/architecture/predictive-back, CN 镜像同文);
 * - 预览条进入缩放 110%→100%、淡入阈值 35%: 同上预测性返回 enter/fade 规格;
 * - 防误触位移 8dp: Compose touchSlop 名义档位, FullCalendar everMoved 方向守卫同构。
 */
internal const val FLOAT_EDGE_ZONE_DP = 10f
internal const val FLOAT_EDGE_DWELL_MS = 500L
internal const val FLOAT_EDGE_DWELL_STEP_MS = 50L
internal const val FLOAT_PAGE_ANIM_MS = 300L
// 吸附跳格动画时长 — Lawnchair CellLayout.REORDER_ANIMATION_DURATION(HEAD 实测)。
// 曲线口径 (2026-10-29 逐行核实源库, 修正早前「quint=吸附曲线」的误记):
// - Lawnchair 拖拽中 DragView 恒跟手 (applyTranslation), 卡本身不做吸附动画;
//   吸附格靠 visualizeDropLocation 画淡入淡出轮廓 — DECELERATE_QUINT(1-(1-t)^5)
//   只是该轮廓 alpha 淡出曲线 (CellLayout:312), 不是位移曲线。
// - 邻居让位 shuffle 走 animateChildToPosition(150ms), 未设插值器 = 系统默认
//   ACCELERATE_DECELERATE。
// - 我们的「卡随投影跳」是用户裁决的渲染模型 (2026-10-29 f8a4e336), 位移曲线
//   取 FLOAT_PAGE_EASING (预测性返回规格) — 属自选, 非源库移植。
internal const val FLOAT_SNAP_ANIM_MS = 150L
internal const val FLOAT_PEEK_FADE_START = 0.35f
internal const val FLOAT_PEEK_ENTER_SCALE = 1.10f
internal const val FLOAT_EDGE_SLOP_DP = 8f
internal val FLOAT_PAGE_EASING = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

/**
 * 周缘的「这一页即将翻过去」预览条 — 仿预测性返回的目标页窥视。
 * 拖拽驻留进度从 [FLOAT_PEEK_FADE_START] 起淡入; 缩放从 [FLOAT_PEEK_ENTER_SCALE]
 * 收敛到 1(锚点在外缘侧, 呈「从屏外挤进来」姿态)。[blocked] = 学期边界:
 * 只以固定低透明度示形, 不随进度生长, 永不触发翻页 — 放手弹回。
 * 纯装饰层: 无 pointerInput, 空档按下由屏根 dismissFloatOnOutsidePress 统一消费。
 */
@Composable
internal fun FloatEdgePeek(
    dir: Int,
    progress: Float,
    targetWeek: Int,
    blocked: Boolean,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val onLeft = dir < 0
    val alpha = when {
        blocked -> 0.35f
        progress < FLOAT_PEEK_FADE_START -> 0f
        else -> ((progress - FLOAT_PEEK_FADE_START) / (1f - FLOAT_PEEK_FADE_START)).coerceIn(0f, 1f)
    }
    val shrink = if (blocked) 1f else FLOAT_PEEK_ENTER_SCALE - (FLOAT_PEEK_ENTER_SCALE - 1f) * progress.coerceIn(0f, 1f)
    val shape = if (onLeft) {
        RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = (16 * scale).dp, bottomEnd = (16 * scale).dp)
    } else {
        RoundedCornerShape(topStart = (16 * scale).dp, bottomStart = (16 * scale).dp, topEnd = 0.dp, bottomEnd = 0.dp)
    }
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width((56 * scale).dp)
            .graphicsLayer {
                this.alpha = alpha
                scaleX = shrink
                scaleY = shrink
                transformOrigin = TransformOrigin(if (onLeft) 0f else 1f, 0.5f)
            }
            .clip(shape)
            .background(colors.surfaceContainerHigh.copy(alpha = 0.94f))
            .testTag(if (onLeft) "float-peek-left" else "float-peek-right"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy((6 * scale).dp),
            horizontalAlignment = if (onLeft) Alignment.Start else Alignment.End,
            modifier = Modifier.padding(horizontal = (8 * scale).dp),
        ) {
            Text(
                text = stringResource(R.string.schedule_week_prefix, targetWeek),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (11 * scale).sp,
                ),
                color = if (blocked) colors.onSurfaceVariant else colors.primary,
            )
            // 目标页课列剪影 — 三根圆角短条暗示「那里有一张课表」
            for (w in listOf(0.9f, 0.6f, 0.75f)) {
                Box(
                    modifier = Modifier
                        .width((30 * scale * w).dp)
                        .height((4 * scale).dp)
                        .background(colors.primary.copy(alpha = 0.25f), RoundedCornerShape((2 * scale).dp)),
                )
            }
        }
    }
}

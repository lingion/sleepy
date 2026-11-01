package com.lingion.sleepy.ui.component

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment

/**
 * [intentional custom] 官方 MotionScheme.expressive() 已是全局默认(MD3E), 但其空间类
 * spring 带回弹(MediumBouncy) — thumb 类「跟手不弹」交互是用户实测定参
 * (高硬度+无回弹, MediumLow 拖沓不跟手), 官方 motionScheme 无对应档位。
 * 使用处: SegmentedSwitcher thumb / PillNavigationBar 贴底+Dock thumb(均为薄层组件)。
 * 注释里的「同款」即指此常量; 改参数三处同步生效。
 */
val SleepyThumbSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessHigh
)

/**
 * popup 家族起始缩放 (MD3E 签名值): 弹窗/菜单/胶囊/浮层类 — 尺度 0.8→1 走
 * fastSpatial (带回弹 = expressive 弹入性格), alpha 走 fastEffects。
 * 对应 M3 源码 DropdownMenu 容器 / Snackbar / FAB(targetScale 0.8) 同族。
 */
const val SleepyPopupScaleStart = 0.8f

/**
 * content 家族起始缩放 (页面族镜像约定): 导航/标签页/大块内容切换 — 尺度走
 * defaultSpatial (0.8 阻尼, 轻微收), alpha 走 defaultEffects。0.92 与
 * SleepyMaterialTransition 既有页面镜像缩放一致。
 */
const val SleepyContentScaleStart = 0.92f

/** 按压回缩目标 (自定义表面按压反馈, noRippleClickable 全家生效): 2% 微缩。 */
const val SleepyPressScale = 0.98f

/**
 * 弹窗/浮层入场: scaleIn 0.8→1 fastSpatial + fadeIn fastEffects (与 sleepyPopupExit 成对)。
 * alpha 只由 fadeIn 驱动 — scaleIn 的 initialAlpha 保持默认 1, 避免双重驱动。
 */
@Composable
fun sleepyPopupEnter(): EnterTransition =
    scaleIn(
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>(),
        initialScale = SleepyPopupScaleStart,
    ) + fadeIn(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>())

/** 弹窗/浮层退场: scaleOut →0.8 fastSpatial + fadeOut fastEffects (与 sleepyPopupEnter 成对)。 */
@Composable
fun sleepyPopupExit(): ExitTransition =
    scaleOut(
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>(),
        targetScale = SleepyPopupScaleStart,
    ) + fadeOut(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>())

/** 页面/大块内容入场: scaleIn 0.92→1 defaultSpatial + fadeIn defaultEffects。 */
@Composable
fun sleepyContentEnter(): EnterTransition =
    scaleIn(
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>(),
        initialScale = SleepyContentScaleStart,
    ) + fadeIn(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>())

/** 页面/大块内容退场: scaleOut →0.92 defaultSpatial + fadeOut defaultEffects。 */
@Composable
fun sleepyContentExit(): ExitTransition =
    scaleOut(
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>(),
        targetScale = SleepyContentScaleStart,
    ) + fadeOut(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>())

/** AnimatedVisibility 展开家族的 enter/exit 规格对 (单一调参点, 由 sleepyExpandSpecs 产出)。 */
class SleepyExpandTransition internal constructor(
    val enter: EnterTransition,
    val exit: ExitTransition,
)

/**
 * 纵向展开/收缩 + 淡入淡出 — AnimatedVisibility 展开家族 (尺寸 FastSpatial,
 * 淡入 DefaultEffects, 淡出 FastEffects; M3 Chip/ExtendedFAB 展开惯例)。
 * 注: brief 写的 ContentSizeAnimationSpec 在已解析的 material3 1.5.0-beta02 /
 * animation 1.12.1 中不存在 (classes.jar 全量检索零命中), animateContentSize 的
 * 公开签名只收 FiniteAnimationSpec<IntSize> — 故落为 expand/shrink 规格对。
 */
@Composable
fun sleepyExpandSpecs(): SleepyExpandTransition {
    val scheme = MaterialTheme.motionScheme
    return SleepyExpandTransition(
        enter = expandVertically(
            animationSpec = scheme.fastSpatialSpec(),
            expandFrom = Alignment.Top,
        ) + fadeIn(animationSpec = scheme.defaultEffectsSpec<Float>()),
        exit = shrinkVertically(
            animationSpec = scheme.fastSpatialSpec(),
            shrinkTowards = Alignment.Top,
        ) + fadeOut(animationSpec = scheme.fastEffectsSpec<Float>()),
    )
}

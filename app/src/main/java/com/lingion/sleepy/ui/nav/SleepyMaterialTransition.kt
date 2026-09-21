package com.lingion.sleepy.ui.nav

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.graphics.TransformOrigin

/**
 * issue#45: 官方 androidx.navigation3 的 Material Classic predictive-back 动效。
 *
 * 决策:不与任何 MIUI 库耦合。直接产出 androidx.compose.animation 的 ContentTransform,
 * 由 NavDisplay 在 transitionSpec / popTransitionSpec / predictivePopTransitionSpec
 * 三个槽位消费。
 *
 * 物理:InstallerX Revived ClassicNavTransition 的 Material 化翻译:
 *  - 推进(forward): 入页 fadeIn + scaleIn(0.92, Origin.Center) 220ms FastOutSlowIn
 *                  (给 push 的"新页"一点从中心浮起的视感, 而不是从边缘滑入)
 *  - 退出(forward): 出页 fadeOut + scaleOut(0.92, Origin.Center) 220ms FastOutSlowIn
 *  - 返回(pop):   入页 EnterTransition.None + scaleIn 220ms FastOutSlowIn
 *                  (返回时目标页原地放大显示, 不滑入)
 *                  出页 ExitTransition.None + scaleOut(0.92, Origin.Center) spring
 *                  (返回时退出页向中心缩回, 弹簧反弹)
 *  - 预测性手势(predictivePop):入页/出页 contentTransform 与 pop 同, 但第二参数 Integer
 *                  是 0..1 的实时手势进度, 把 scale/fade 的 alpha 当作静态 CompositionLocal
 *                  注入即可。官方 SeekableTransitionState 已替我们 handle 进度插值, 这里
 *                  只返回 ContentTransform 模板,Compose 框架在动画态时按 progress 调 tween。
 *
 * 为避免每个 entry 渲染都重建 TWEEN 常量,这里集中导出 — 改时长改一处。
 */

internal const val ForwardDuration: Int = 220
internal const val ForwardFadeIn: Int = 110
internal const val PredictiveDuration: Int = 200

private val forwardTween: TweenSpec<Float> = tween(
    durationMillis = ForwardDuration,
    easing = FastOutSlowInEasing,
)

private val forwardFadeInTween: TweenSpec<Float> = tween(
    durationMillis = ForwardFadeIn,
    easing = FastOutSlowInEasing,
)

private val predictiveCommitTween: TweenSpec<Float> = tween(
    durationMillis = PredictiveDuration,
    easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
)
internal val PredictiveTweenRef: TweenSpec<Float> = predictiveCommitTween

private val predictiveCancelSpring: SpringSpec<Float> = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

/**
 * forward (push): 入页 fadeIn + 从中心 scaleIn 0.92→1。
 */
internal fun sleepyForwardEnter(): EnterTransition {
    return fadeIn(forwardFadeInTween) + scaleIn(
        animationSpec = forwardTween,
        initialScale = 0.92f,
        transformOrigin = TransformOrigin.Center,
    )
}

/**
 * forward (push): 出页 fadeOut + 向中心 scaleOut 1→0.92。
 */
internal fun sleepyForwardExit(): ExitTransition {
    return fadeOut(forwardTween) + scaleOut(
        animationSpec = forwardTween,
        targetScale = 0.92f,
        transformOrigin = TransformOrigin.Center,
    )
}

/**
 * pop (programmatic, 无手势): 目标页 EnterTransition.None — 用户要求的"目标页原地不动"。
 * 退出页走 scaleOut(0.92, Center) + 220ms tween — 与 forward 对称(都走 0.92 缩放 + 220ms),
 * 与 forward 唯一区别是 forward 入页是 fadeIn+scaleIn,pop 出页是 fadeOut+scaleOut。
 */
internal fun sleepyPopEnter(): EnterTransition {
    return EnterTransition.None
}

internal fun sleepyPopExit(): ExitTransition {
    return fadeOut(forwardTween) + scaleOut(
        animationSpec = forwardTween,
        targetScale = 0.92f,
        transformOrigin = TransformOrigin.Center,
    )
}

/**
 * NavDisplay transitionSpec 入参 — 推进时新页 enter + 旧页 exit 同时启动。
 */
internal val sleepyForwardTransform: ContentTransform = ContentTransform(
    targetContentEnter = sleepyForwardEnter(),
    initialContentExit = sleepyForwardExit(),
    targetContentZIndex = 1f,
)

/**
 * NavDisplay popTransitionSpec 入参 — 程序化 pop。
 */
internal val sleepyPopTransform: ContentTransform = ContentTransform(
    targetContentEnter = sleepyPopEnter(),
    initialContentExit = sleepyPopExit(),
    targetContentZIndex = 1f,
)

/**
 * NavDisplay predictivePopTransitionSpec 入参 — 手势预测性返回。
 *
 * 第二参数 gestureProgress(0..1) 官方 API: 不参与 ContentTransform 的构建,
 * 由 Compose SeekableTransitionState 把动画态从 0..1 推进; 我们只需要给出
 * commit (手势释放通过) / cancel (手势取消回弹) 两态的视觉形态。
 *
 * Material 端 commit = 放行通过 = 等同程序化 pop(只用 spring 微弹); cancel = 拉回 = 等同程序化 push。
 */
internal val sleepyPredictivePopTransform: ContentTransform = ContentTransform(
    targetContentEnter = scaleIn(
        animationSpec = predictiveCommitTween,
        initialScale = 0.92f,
        transformOrigin = TransformOrigin.Center,
    ) + fadeIn(predictiveCommitTween),
    initialContentExit = scaleOut(
        animationSpec = predictiveCommitTween,
        targetScale = 0.92f,
        transformOrigin = TransformOrigin.Center,
    ) + fadeOut(predictiveCommitTween),
    targetContentZIndex = 1f,
)


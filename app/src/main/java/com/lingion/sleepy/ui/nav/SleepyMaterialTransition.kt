package com.lingion.sleepy.ui.nav

import androidx.compose.animation.ContentTransform
import androidx.compose.runtime.Composable
import com.lingion.sleepy.ui.component.sleepyContentEnter
import com.lingion.sleepy.ui.component.sleepyContentExit

/**
 * issue#45: 顶层页面转场 — NavDisplay 三槽位(transitionSpec / popTransitionSpec /
 * predictivePopTransitionSpec)消费的 ContentTransform 工厂。
 *
 * M3 Expressive 全量过闸(Task 2, ledger 反转裁决): InstallerX Revived Classic 的
 * 手搓物理(220ms/110ms 不对称淡入 + 200ms 贝塞尔回弹曲线)全部退场, 推进/程序化返回/
 * 预测性返回三路统一走 SleepyMotion 的 content 家族工厂:
 *  - 入页: sleepyContentEnter() — scaleIn 0.92→1 defaultSpatial + fadeIn defaultEffects
 *  - 出页: sleepyContentExit()  — scaleOut 1→0.92 defaultSpatial + fadeOut defaultEffects
 * 旧的「返回时目标页原地不动画」(EnterTransition.None)设计按用户裁决反转: 返回时
 * 目标页同样缩放淡入, 与 MD3E 页面族的对称 content 转场一致。取消手势的回弹旧常量
 * 系死代码已删除 — 官方 SeekableTransitionState 在取消时自动反向播放 commit 动画,
 * 不存在独立的取消视觉形态。
 *
 * 0.92 镜像缩放与 motionScheme 规格的单一定义点在 SleepyMotion.kt(改一处全局生效);
 * 本文件只做组装, 不再落任何时长/曲线字面量。scaleIn/scaleOut 的缺省 transformOrigin
 * 即 Center, 由工厂实现承载。三个 transform 都保留 targetContentZIndex = 1f(目标页压层)。
 *
 * 三工厂都是 @Composable(motionScheme 要读主题), 而 NavDisplay 的槽位 lambda 是
 * @DisallowComposableCalls — 调用点必须在 composable 体内先算好 ContentTransform 再捕获。
 */

/**
 * forward (push): 新页 content 家族入场 + 旧页 content 家族退场, 同时启动。
 */
@Composable
internal fun sleepyForwardTransform(): ContentTransform {
    return ContentTransform(
        targetContentEnter = sleepyContentEnter(),
        initialContentExit = sleepyContentExit(),
        targetContentZIndex = 1f,
    )
}

/**
 * pop (程序化返回): 目标页入场动画已反转(旧 EnterTransition.None 退场),
 * 出页与 forward 对称缩回淡出。
 */
@Composable
internal fun sleepyPopTransform(): ContentTransform {
    return ContentTransform(
        targetContentEnter = sleepyContentEnter(),
        initialContentExit = sleepyContentExit(),
        targetContentZIndex = 1f,
    )
}

/**
 * predictivePop (手势预测性返回): commit(手势放行)视觉与程序化 pop 同 pair —
 * 手势进度(0..1)由官方 SeekableTransitionState 插值, 这里只给模板;
 * cancel(回弹)自动反向播放同一组规格, 无独立取消形态。
 */
@Composable
internal fun sleepyPredictivePopTransform(): ContentTransform {
    return ContentTransform(
        targetContentEnter = sleepyContentEnter(),
        initialContentExit = sleepyContentExit(),
        targetContentZIndex = 1f,
    )
}

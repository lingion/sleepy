package com.lingion.sleepy.ui.nav

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.navArgument

/**
 * issue#45: 自研 Overlay 栈 → Navigation Compose。
 *
 * 迁移理由(不是"官方即正确",而是四条需求都压在自研栈的短板上):
 * - 预测性返回: NavHost 的 pop 动画由系统返回手势进度直接驱动,自研栈要自己接
 *   PredictiveBackHandler 并手搓手势进度动画,且每次系统手势语义变更都得跟;
 * - 页间过渡: enter/exit/popEnter/popExit 原生成对,不用手工维护进出场配对;
 * - 页面状态: 每个 NavBackStackEntry 自带 saveable 作用域(滚动/折叠/输入),
 *   替代手写 SaveableStateProvider + 自定义 Saver + 一堆伴生参数持久化
 *   (旧实现注释里记录过 3 次这类事故: 返回分层、旋转参数丢失、编辑会话丢弃);
 * - 生命周期: 非可见 entry 自动 ON_STOP,后台页停止取数与动画。
 *
 * 约定: 可空 Long 参数一律用 -1 哨兵(NavType.LongType 不可空),解析后转回 null。
 */
object Routes {
    const val MAIN = "main"
    const val ADD_COURSE = "add_course?courseId={courseId}&editing={editing}"
    const val ALL_TABLES = "all_tables"
    const val EDIT_TABLE = "edit_table?tableId={tableId}&pendingNew={pendingNew}&prevDefault={prevDefault}"
    const val APPEARANCE = "appearance"
    const val GENERAL = "general"
    const val HOLIDAY = "holiday"
    const val EXPORT = "export"
    const val REMINDER = "reminder"
    const val ABOUT = "about"
    const val LICENSE = "license"
    const val WIDGET_MANAGEMENT = "widget_management"
    const val WIDGET_EDIT = "widget_edit/{widgetId}"
    const val PERIOD_TABLES = "period_tables"
    const val PERIOD_EDIT = "period_edit/{periodId}?isNew={isNew}"

    /** 可空 id 的哨兵值。 */
    const val NO_ID = -1L

    fun addCourse(courseId: Long = NO_ID, editing: Boolean = false) =
        "add_course?courseId=$courseId&editing=$editing"

    fun editTable(tableId: Long = NO_ID, pendingNew: Long = NO_ID, prevDefault: Long = NO_ID) =
        "edit_table?tableId=$tableId&pendingNew=$pendingNew&prevDefault=$prevDefault"

    fun widgetEdit(widgetId: Int) = "widget_edit/$widgetId"

    fun periodEdit(periodId: Long, isNew: Boolean = false) = "period_edit/$periodId?isNew=$isNew"
}

/** 路由里的可空 id: -1 哨兵 → null(EditTable 的 tableId=null 语义是"编辑当前课表",不可混)。 */
fun NavBackStackEntry.longArg(key: String): Long? {
    val raw = arguments?.getLong(key) ?: Routes.NO_ID
    return if (raw == Routes.NO_ID) null else raw
}

fun NavBackStackEntry.intArg(key: String): Int = arguments?.getInt(key) ?: -1

fun NavBackStackEntry.boolArg(key: String): Boolean = arguments?.getBoolean(key) ?: false

val addCourseArgs = listOf(
    navArgument("courseId") { type = NavType.LongType; defaultValue = Routes.NO_ID },
    navArgument("editing") { type = NavType.BoolType; defaultValue = false },
)

val courseIdArgs = listOf(
    navArgument("courseId") { type = NavType.LongType; defaultValue = Routes.NO_ID },
)

val editTableArgs = listOf(
    navArgument("tableId") { type = NavType.LongType; defaultValue = Routes.NO_ID },
    navArgument("pendingNew") { type = NavType.LongType; defaultValue = Routes.NO_ID },
    navArgument("prevDefault") { type = NavType.LongType; defaultValue = Routes.NO_ID },
)

val widgetEditArgs = listOf(
    navArgument("widgetId") { type = NavType.IntType },
)

val periodEditArgs = listOf(
    navArgument("periodId") { type = NavType.LongType },
    navArgument("isNew") { type = NavType.BoolType; defaultValue = false },
)

/**
 * MD3E 共享轴(shared axis)过渡 + 官方推荐 pop 缩放。
 *
 * 2026-09-20 用户反馈链 (5 轮):
 *   - d7fc9421: 修暗色闪白 + slide 不对称 → spring 过冲卡顿。
 *   - da3a6335: 关 enableOnBackInvokedCallback 治"矩形缩小+logo 残留" — 但同时
 *     把 predictive-back 返回预览也关了 (用户反馈"返回预览没了吗?")。
 *   - B 方案 (本轮): 重开 predictive-back=true, popExit 改为官方示例
 *     scaleOut(0.92, TransformOrigin(0.5,0.5)) + spring(NoBouncy, MediumLow),
 *     popEnter = None 让目标页原地不动。
 *   - 用户再次反馈 "返回预览出现白底+中间 logo": 根因不是 transition, 是
 *     windowBackground。Theme.Sleepy.Splash 在整个应用窗口生命周期内挂着
 *     "纯色 + 居中 launcher 图标" layer-list, 系统 EMUI predictive-back 预演
 *     缩略图、最近任务快照、Activity 切换预览都直接拿它当 backdrop。PreDraw
 *     降级修不到系统快照 (快照发生在 PreDraw 切换之前/之时)。
 *
 * **NIA 模式迁移 (最终方案)**: 仿照 google/nowinandroid 的 splash 切换做法 —
 *   - 启动主题 parent=Theme.SplashScreen (androidx core-splashscreen) +
 *     windowSplashScreenBackground=纯色 + windowSplashScreenAnimatedIcon=
 *     launcher 图标 + postSplashScreenTheme=Theme.Sleepy
 *   - installSplashScreen() 完成后 androidx 内部立即切到运行时主题, 整个
 *     进程生命周期内窗口背景归零, 系统任何预览都拿不到 logo 残留。
 *   - night-adjusted 拆分 (values/ + values-night/ 各自重声明父主题与状态栏,
 *     共用属性挂中间层, 不被 night 侧覆盖重置) — 同 NIA NightAdjusted.Theme。
 *   - 删除 PreDraw/ColorDrawable 降级 hack (随窗口底衬一起归零)。
 *   - 删 drawable/splash_background.xml (无引用)。
 *   - 运行时主题 Theme.Sleepy 仍是 Theme.Material.Light.NoActionBar 系,
 *     Compose 用 SleepyThemeProvider 整套重画, 不依赖任何 windowBackground。
 *
 * 过渡动作 (本文件 B 方案, 不动):
 *   - 进子页 (forward): slideIn+slideOut ±1/8 屏, tween(220, FastOutSlowIn),
 *     110ms fadeIn, 推入感强。
 *   - 返回 (pop): scaleOut(0.92, center) + spring(MediumLow, NoBouncy) +
 *     fadeOut(220); popEnter = None (目标页不重画)。
 *   与 google/nowinandroid 与官方 developer.android.com/develop/ui/compose/
 *   system/predictive-back-setup "Add custom in-app animation" 示例一致。
 *
 * 必须在 AppRoot 组合期求值再捕获进 NavHost 的 transition lambda —
 * 那个 lambda 不是 composable 上下文,不能就地读 MaterialTheme。
 */
private const val ForwardDuration = 220
private const val ForwardFadeIn = 110
private const val ForwardOffsetFraction = 8  // 1/8 屏 — 比之前 1/6 更轻, 推入感更强但不抢戏

// scaleOut spring: 不回弹, stiffness 偏低 → 收尾平滑, 不会有 M3 expressive spatial
// 那种"快速回弹"造成的视觉抖动感
private val popExitSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

@Composable
fun sleepySharedAxisEnter(): EnterTransition {
    return slideInHorizontally(
        initialOffsetX = { it / ForwardOffsetFraction },
        animationSpec = tween(durationMillis = ForwardDuration, easing = FastOutSlowInEasing),
    ) + fadeIn(tween(durationMillis = ForwardFadeIn, easing = LinearEasing))
}

@Composable
fun sleepySharedAxisExit(): ExitTransition {
    return slideOutHorizontally(
        targetOffsetX = { -it / ForwardOffsetFraction },
        animationSpec = tween(durationMillis = ForwardDuration, easing = FastOutSlowInEasing),
    ) + fadeOut(tween(durationMillis = ForwardDuration, easing = LinearEasing))
}

// pop: 目标页 (Home) 原地不动画 (None), 退出页向屏幕中心缩小 + 淡出。
// 官方文档示例 popExit = scaleOut(0.92, TransformOrigin(0.5, 0.5));
// popEnter = None。Android 14+ 系统 predictive-back 手势进度由 NavHost 内部
// SeekableTransitionState 驱动这套过渡, 自动支持侧滑预览。
@Composable
fun sleepySharedAxisPopEnter(): EnterTransition {
    return EnterTransition.None
}

@Composable
fun sleepySharedAxisPopExit(): ExitTransition {
    return scaleOut(
        targetScale = 0.92f,
        animationSpec = popExitSpring,
        transformOrigin = TransformOrigin(0.5f, 0.5f),
    ) + fadeOut(tween(durationMillis = ForwardDuration, easing = LinearEasing))
}

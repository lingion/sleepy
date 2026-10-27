package com.lingion.sleepy.ui.component

import com.lingion.sleepy.testutil.readProjectSource
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 拖课边缘翻页 + 原占位置灰的源码契约锁 (2026-10-27 用户裁决).
 * 行为已由 CourseOccurrenceFloatDragTest 验证; 本类锁「数值出处」与
 * 「生产接线」两侧 — 有人改常量、拆接线时在这里红, 而不是在真机上悄悄变。
 * 常量语义见 EdgePeek.kt KDoc(官方文档交叉验证条目)。
 */
class EdgePagingContractTest {

    private val edgePeek = readProjectSource("app/src/main/java/com/lingion/sleepy/ui/component/EdgePeek.kt")
    private val edgeOrFloatState = readProjectSource("app/src/main/java/com/lingion/sleepy/ui/component/FloatOccurrence.kt")
    private val tableView = readProjectSource("app/src/main/java/com/lingion/sleepy/ui/component/CourseTableView.kt")
    private val schedule = readProjectSource("app/src/main/java/com/lingion/sleepy/ui/screen/schedule/ScheduleScreen.kt")
    private val theme = readProjectSource("app/src/main/java/com/lingion/sleepy/ui/theme/Theme.kt")

    @Test fun edgeGeometryAndTimingMatchTheRatifiedValues() {
        assertTrue("边缘区 72dp", edgePeek.contains("FLOAT_EDGE_ZONE_DP = 72f"))
        assertTrue("驻留 500ms", edgePeek.contains("FLOAT_EDGE_DWELL_MS = 500L"))
        assertTrue("翻页动画 300ms", edgePeek.contains("FLOAT_PAGE_ANIM_MS = 300L"))
        assertTrue("防误触位移 8dp", edgePeek.contains("FLOAT_EDGE_SLOP_DP = 8f"))
        assertTrue("peek 淡入阈值 35%", edgePeek.contains("FLOAT_PEEK_FADE_START = 0.35f"))
        assertTrue("进入缩放 110%", edgePeek.contains("FLOAT_PEEK_ENTER_SCALE = 1.10f"))
        assertTrue("预测性返回曲线", edgePeek.contains("CubicBezierEasing(0.1f, 0.1f, 0f, 1f)"))
    }

    @Test fun dragDimUsesMaterialDisabledContentValue() {
        assertTrue("Material 3 禁用内容档 0.38", theme.contains("dragDim = 0.38f"))
    }

    @Test fun timerRestartsPerPageAndOnlyRunsOnTheFloatHost() {
        // week 作键 → 每次翻页自然重计 500ms; 非宿主页静默退出不写共享态。
        assertTrue("计时键含 week", tableView.contains("LaunchedEffect(floatState.dragging, floatState.week)"))
        assertTrue("非宿主退出", tableView.contains("if (currentWeek != floatState.week) return@LaunchedEffect"))
        assertTrue("学期头禁翻", tableView.contains("dir < 0 && floatState.week <= 1"))
        assertTrue("学期尾禁翻", tableView.contains("dir > 0 && floatState.week >= floatMaxWeek"))
    }

    @Test fun oneDwellFlipsOnePageAndReArmsOnlyBackInTheGrid() {
        // 2026-10-28 用户反馈: 压边无限连翻停不了 — 翻过即消耗, 收回格内(dir==0)才重新武装。
        assertTrue("翻过即消耗", tableView.contains("floatState.edgeArmed = false"))
        assertTrue("未武装不计时", tableView.contains("if (!floatState.edgeArmed)"))
        assertTrue("回格重新武装", tableView.contains("floatState.edgeArmed = true; continue"))
        assertTrue("武装字段默认开", edgeOrFloatState.contains("var edgeArmed = true"))
        assertTrue("松手重新武装", edgeOrFloatState.contains("edgeArmed = true"))
    }

    @Test fun dimScopeIsOriginPageOnly() {
        // 周复现课程在目标页同 id/同日/同节次 — 无原页守卫会把落点卡误压暗。
        assertTrue(tableView.contains("currentWeek == floatState.originWeek"))
        assertTrue(tableView.contains("floatState.originStartNode"))
    }

    @Test fun screenWiresPagerPagingAndTimeDomainToFloatWeek() {
        // 预览页保持组合(浮层跨页不丢), 动画时长与曲线取 EdgePeek 常量。
        assertTrue("保留相邻页", schedule.contains("beyondViewportPageCount = 1"))
        assertTrue("翻页动画取常量", schedule.contains("FLOAT_PAGE_ANIM_MS.toInt()"))
        assertTrue("翻页曲线取常量", schedule.contains("FLOAT_PAGE_EASING"))
        assertTrue("夹取周上限", schedule.contains("coerceIn(0, pagerMaxWeek - 1)"))
        assertTrue("浮起周上限下发", schedule.contains("floatMaxWeek = pagerMaxWeek"))
        // 时间域以浮起页为准 — 跨周翻页后落点必须写目标周, 不能再拿「页号+1」。
        // (move 调用跨行, 锁「projected, floatState.week, day, node, step」子串)
        assertTrue("移动走浮起周", schedule.contains("projected, floatState.week, day, node, step"))
        // 被拖的那一次课住在起拖页 — 跨周翻页后 resolve/删除/编辑全按 originWeek,
        // 落库才按松手页 (2026-10-28 用户反馈: 单周号致翻页松手静默无效)。
        assertTrue("移动带起拖周", schedule.contains("sourceWeek = floatState.originWeek"))
        assertTrue("删除走起拖周", schedule.contains("it.delete(projected, floatState.originWeek)"))
        assertTrue("编辑走起拖周", schedule.contains("table.startDate, floatState.originWeek, projected.day"))
        assertTrue("settledPage 回写", schedule.contains("floatState.week = pagerState.settledPage + 1"))
        // 屏根周变化收起浮层 — 拖拽中例外(边缘翻页正是要改周)。
        assertTrue("收起守卫", schedule.contains("if (!floatState.dragging && floatState.active && floatState.week != state.selectedWeek)"))
    }

    @Test fun horizontalEdgeReleaseClampsInsteadOfBouncing() {
        // 2026-10-28 用户反馈: 压右缘松手曾「像完全没有任何发生」—
        // 水平出界钳到最近可见列, 纵向非法(末行以下)才弹回。
        assertTrue("列钳位", tableView.contains("coerceIn(0, sortedDays.lastIndex)"))
    }
}

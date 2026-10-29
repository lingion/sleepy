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
        // 2026-10-29 用户反馈「拖到周日列就翻页」后裁决: 触发域从「容器缘内缩 72dp」
        // 改为「列带外缘 10dp」— 72dp 带整个吞掉末列, 落子与翻页手势域重叠 (根因见
        // CourseTableView 轮询注释 + Lawnchair Workspace.verifyInsidePage 语义)。
        // 判据行为锁: CourseOccurrenceFloatDragTest.dwellingOverTheSundayColumn…
        assertTrue("缘容差 10dp", edgePeek.contains("FLOAT_EDGE_ZONE_DP = 10f"))
        assertTrue("触发线取列带缘", listOf(
            "cx < bandLeft + FLOAT_EDGE_ZONE_DP -> -1",
            "cx > bandRight - FLOAT_EDGE_ZONE_DP -> 1",
        ).all { tableView.contains(it) })
        assertTrue("列带含时间列右缘", tableView.contains("val bandLeft = (timeW + gapW).value"))
        assertTrue("列带止于末列右缘(扣尾隙)",
            tableView.contains("(colW + gapW) * dayCount - gapW).value"))
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
        // 工作区更新: 回格同时清 blocked/peek — 停形预览条随重新武装归零。
        assertTrue("回格重新武装", tableView.contains("floatState.edgeArmed = true; floatState.edgeBlocked = 0; floatState.peekProgress = 0f; continue"))
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

    @Test fun settledPageWriteBackReachesViewModelChip() {
        // 2026-10-28 真机实证: 边缘翻页落库正确, 但顶栏周 chip 停在起拖周(第 2 周),
        // 网格已是第 3 周 — 左箭头从第 3 周直接跳回第 1 周, 导航错位一周。
        // 根因: settledPage 落位 effect 只回写 floatState.week, 漏了 ViewModel —
        // 程序化 animateScrollToPage 被 currentPage effect 的手势守卫有意拦截
        // (2026-09-13 防闪烁环), 落位后必须在此补 changeWeek, 双通道才一致。
        val body = schedule.substringAfter("LaunchedEffect(\n                pagerState.settledPage")
            .substringBefore("LaunchedEffect(pagerState.currentPage)")
        // (?m)^\s* 只认行首代码 — 注释里出现同名字符串不算数
        // (回退验证时曾被注释假绿, 2026-10-28)。
        assertTrue(
            "settledPage 落位须回写 ViewModel(changeWeek), 否则 chip 与浏览周脱节",
            Regex("""(?m)^\s*viewModel\.changeWeek\(pagerState\.settledPage \+ 1\)""").containsMatchIn(body)
        )
        // 回写受「非拖拽中」守卫 — 拖拽期回写会腰斩手势(原注释口径)。
        assertTrue(
            "回写须在非拖拽守卫内",
            Regex("""!floatState\.dragging""").containsMatchIn(body)
        )
        // 根因二(同日二补, 真机 replay 实证): 旧守卫「active && !dragging」在合法落点
        // 路上从未打开 — 松手走 dismiss() 把两值同帧落 false, effect 永远等不到
        // 「浮着但不拖」的瞬间, chip 仍停起拖周。改由 chipPending 记账: 翻页置位,
        // effect 在「!dragging && (active || chipPending)」消费, 翻过页必回写 chip。
        assertTrue(
            "回写条件须含 chipPending(救合法落点路)",
            Regex("""floatState\.active \|\| floatState\.chipPending""").containsMatchIn(body)
        )
        assertTrue("chipPending 须作 effect 键", body.contains("floatState.chipPending,"))
        assertTrue("消费后须清零", Regex("""(?m)^\s*floatState\.chipPending = false""").containsMatchIn(body))
        // 翻页即置位 — 翻过页(week bump)才置, 且早于 onEdgeDrag 同帧生效。
        assertTrue("翻过页置位", tableView.contains("floatState.chipPending = true"))
        assertTrue(
            "置位在 week bump 之后 onEdgeDrag 之前",
            tableView.substringBefore("floatState.chipPending = true").contains("floatState.week += dir"),
        )
        // 字段须是快照态(作 effect 键须可观察)。
        assertTrue("chipPending 为组合态", edgeOrFloatState.contains("var chipPending by mutableStateOf(false)"))
    }

    @Test fun floatingCardRendersSnapProjectionNotFinger() {
        // 渲染接线锁 (2026-10-29): 浮卡/浮框位置尺寸必须读 snap* 的动画值, 不读跟手位。
        // 磁吸视觉的全部意义在「卡随投影走」— 若哪天有人把 offset 改回 floatState.x/y,
        // 吸附计算照跑、落库照对, 但用户看着像没吸住, 且单测全绿无人报警。
        // 同时钉死三条退路都把 snap 同步回跟手位/原位 (非法投影/弹回/否决拉伸),
        // 否则卡会卡在一个已作废的投影上。
        assertTrue("跳格时长常量 150ms", edgePeek.contains("FLOAT_SNAP_ANIM_MS = 150L"))
        assertTrue("渲染动画时长取常量", tableView.contains("tween<Dp>(FLOAT_SNAP_ANIM_MS.toInt()"))
        assertTrue("x/y/高三路动画自 snap*",
            listOf(
                "animateDpAsState(floatState.snapX",
                "animateDpAsState(floatState.snapY",
                "animateDpAsState(floatState.snapHeight",
            ).all { tableView.contains(it) }
        )
        assertTrue("浮卡 offset 读动画值不读跟手位", tableView.contains(".offset(x = renderX, y = overlayY)"))
        assertTrue("浮卡高度读动画值", tableView.contains(".height(renderH)"))
        assertTrue("浮框跟随同一动画值", tableView.contains("x = renderX,"))
        assertTrue("滚动补偿加在动画之外(overlayY 由 renderY 派生)",
            tableView.contains("val overlayY = renderY + headH + gapH - floatScrollDp")
        )
        assertTrue("非法投影退回跟手位", Regex("""floatState\.snapX = floatState\.x""").containsMatchIn(tableView))
        assertTrue("弹回同步 snap", Regex("""floatState\.snapY = floatState\.y""").containsMatchIn(tableView))
        assertTrue("否决拉伸同步 snap", edgeOrFloatState.contains("snapY = originY"))
    }

    @Test fun horizontalEdgeReleaseClampsInsteadOfBouncing() {
        // 2026-10-28 用户反馈: 压右缘松手曾「像完全没有任何发生」—
        // 水平出界钳到最近可见列, 纵向非法(末行以下)才弹回。
        assertTrue("列钳位", tableView.contains("coerceIn(0, sortedDays.lastIndex)"))
    }
}

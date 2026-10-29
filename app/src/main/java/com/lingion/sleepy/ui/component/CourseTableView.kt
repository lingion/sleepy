package com.lingion.sleepy.ui.component

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.lingion.sleepy.R
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.ui.theme.SleepyTextStyle
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.noRippleCombinedClickable
import com.lingion.sleepy.ui.theme.noRippleClickable
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.ConflictLayoutEngine
import com.lingion.sleepy.util.CourseColorUtil
import com.lingion.sleepy.util.CourseDisplayUtil
import com.lingion.sleepy.util.CourseHueStore
import com.lingion.sleepy.util.DateUtils
import com.lingion.sleepy.util.TimeTableUtils
import com.lingion.sleepy.util.PeriodHeaderFormatter
import com.lingion.sleepy.util.MealBreakDetector
import com.lingion.sleepy.util.GridSeparatorGeometry
import com.lingion.sleepy.util.TimetableViewportPolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * 时段定义 — 5 个时段（对应 HTML 里的 5 个 slot-row）
 * 与 WakeUp 默认 12 节对应: 1-2 / 3-5 / 6-7 / 8-10 / 11-13
 */
data class TimeSlot(
    val label: String,         // "1-2节"
    val start: LocalTime,
    val end: LocalTime,
    val displayStart: String,  // "08:00"
    val displayEnd: String,    // "09:35"
    val nodeStart: Int,
    val nodeEnd: Int
) {
    // nodeString 死属性已删（恒返回 "N-N" 且全库零调用; 界面用的是 CourseEntity.nodeString 本地化版本）
    val timeString: String get() = "$displayStart-$displayEnd"

    /**
     * 渲染期占位节次 (用户反馈 2026-09-09): 非常规课跨节次空隙时由
     * TimeTableUtils.buildRenderSlotPlan 合成的空隙占位行 — 只显示时间不显示
     * 节号(label 为空), 绝不写回 timeJson, 与 insertEdgeNode 手建节点无关。
     */
    val isPlaceholder: Boolean get() = label.isEmpty()
}

/**
 * Cards 网格视图
 *
 * 架构：
 *   BoxWithConstraints(fillMaxSize) → 算出 colW (dp)
 *     Column(fillMaxWidth, verticalScroll)
 *       Row(表头)            — Compose 自然排版
 *       Box(固定高度 = maxNode * rowH) — 时间栏 + 课程卡片全用 Modifier.offset 绝对定位
 *
 * 关键点：
 * - Column 用 fillMaxWidth（不是 fillMaxSize），内容高度 = 表头 + 固定 gridH，超出视口 → 可滚动
 * - 时间栏 / 卡片都在同一个 Box 内，Modifier.offset 定位 → 滚动完全同步
 * - 全用 dp 算 offset，不碰 px，不碰 Layout measure/place
 */
@Composable
fun CardsGridView(
    onQuickAdd: ((Int, Int, Int) -> Unit)? = null,
    quickAddState: QuickAddCourseState = remember { QuickAddCourseState() },
    quickAddEnabled: Boolean = true,
    courses: List<CourseEntity>,
    allCourses: List<CourseEntity> = courses,
    timeSlots: List<TimeSlot>,
    visibleDays: Set<Int> = (1..7).toSet(),
    showDate: Boolean = false,
    startDate: String = "",
    currentWeek: Int = 1,
    today: Int = DateUtils.todayDayOfWeek(),
    todayDate: java.time.LocalDate = java.time.LocalDate.now(),
    onCourseClick: (CourseEntity) -> Unit,
    onCourseLongClick: ((CourseEntity) -> Unit)? = null,
    modifier: Modifier = Modifier,
    greyDays: Set<Int> = emptySet(),  // 本周应灰显的星期几 (1-7)
    topOverrides: Map<String, Long> = emptyMap(),           // v7.10.5: 会话级置顶 override,上提到 ScheduleScreen(radio 瞬时更新写入同一真相源)
    onSetTopOverride: (String, Long?) -> Unit = { _, _ -> }, // (clusterKey, layerRepId|null)
    // v7.10.16r 轮换态(issue#10, 评审 #1): 与 topOverrides 同级持有(离开课表页重置)。
    // remember 留在页内会在 HorizontalPager 翻页时销毁( beyondViewportPageCount=0 ),
    // 周切换一次轮换态即丢。
    rotationSteps: Map<String, Int> = emptyMap(),
    onRotationStep: (String, Int) -> Unit = { _, _ -> },     // (clusterKey, step)
    // 用户反馈 2026-09-09: 非常规课跨节次空隙时按当前课程集合合成渲染期占位节次,
    // 比例定位基于扩展后的槽位表。null = 不合成(旧调用方兼容)。
    timeJson: String? = null,
    rowHeightScale: Float = 1f,
    onRowHeightScaleChange: (Float) -> Unit = {},
    // v1.0.56 T3(实验室): 双指捏放行高总开关, 默认 false = 手势整个不挂(存量缩放值不清)。
    pinchZoomEnabled: Boolean = false,
    // 长按浮起拖拽换节: onOccurrenceMove != null 即启用(传 null 保留旧长按直开编辑器路径)。
    // floatState 与 quickAddState 同级由调用方持有 — HorizontalPager 翻页销毁不掉浮动态。
    // onOccurrenceMove(course, day, node, step, resizeOnly, onResult): 管线落库后回 saved —
    // true=浮层已可关, false=用户否决/未落库, 网格按当前语义弹回或复原拉伸;
    // resizeOnly=true 表示这次提交来自「把手拉伸后经空白点击确认」而非网格落点。
    floatState: CourseFloatState = remember { CourseFloatState() },
    onOccurrenceMove: ((CourseEntity, Int, Int, Int, Boolean, (Boolean) -> Unit) -> Unit)? = null,
    onOccurrenceDelete: ((CourseEntity) -> Unit)? = null,
    // 浮窗「调整本次」直达回调: 用户已在浮窗点过调整, 调用方应直接展开编辑表单。
    // null = 退回长按通道(存量调用方契约不变)。
    onOccurrenceEdit: ((CourseEntity) -> Unit)? = null,
    // 边缘翻页拖拽 (2026-10-27 用户裁决): 卡片压住左/右缘驻留满 FLOAT_EDGE_DWELL_MS
    // 时触发, dir=-1 上一周 / +1 下一周; 学期边界由网格挡回(edgeBlocked), 不会走到这里。
    // null(默认) = 特性关闭, 存量调用方(Widget 预览)行为逐字节不变。
    onEdgeDrag: ((Int) -> Unit)? = null,
    // 学期末周(= pagerMaxWeek) — 边缘驻留到界判断用。0(默认)= 无翻页宿主, 特性关闭。
    floatMaxWeek: Int = 0,
) {
    val colors = MaterialTheme.colorScheme
    val floatAware = onOccurrenceMove != null
    // D 契约: 把手拉伸不即时落库; 空白点击/返回键把 pending 拉伸送进同一移动管线
    // (resizeOnly → handler 分型为「是否调整持续节次」)。否决 → revertSpan 复原原跨度。
    fun confirmPendingResizeOrDismiss() {
        val lifted = floatState.course ?: return
        if (!floatState.spanChanged) { floatState.dismiss(); return }
        onOccurrenceMove?.invoke(lifted, lifted.day, floatState.startNode, floatState.step, true) { saved ->
            if (saved) floatState.dismiss() else floatState.revertSpan()
        }
    }
    LaunchedEffect(currentWeek, startDate, courses, timeSlots, visibleDays, quickAddEnabled) {
        quickAddState.dismiss()
    }
    // 拖拽进行中不早杀: 边缘翻页落库会重发射 courses — 此时 dismiss 会腰斩手势流
    // (2026-10-27 边缘翻页特性: 落库发生在 commitFloatDrop 之后, 收层由管线自己走)。
    LaunchedEffect(currentWeek, startDate, courses, timeSlots, visibleDays, quickAddEnabled) {
        if (!floatState.dragging) floatState.dismiss()
    }
    androidx.activity.compose.BackHandler(enabled = quickAddEnabled && quickAddState.selection != null) { quickAddState.dismiss() }
    // D 契约: 拉伸未确认时返回键 = 空白点击 — 先走确认, 无 pending 才直接关浮层。
    androidx.activity.compose.BackHandler(enabled = floatAware && floatState.active) { confirmPendingResizeOrDismiss() }
    // 设置页改 scale / cornerRatio 后强制 recompose
    var prefVersion by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        AppPrefs.changeBus.filter {
            it == AppPrefs.KEY_GRID_SCALE || it == AppPrefs.KEY_GRID_CORNER_RATIO ||
                it == AppPrefs.KEY_GRID_USE_ALIAS ||
                it == AppPrefs.KEY_GRID_ADAPTIVE_HEIGHT ||
                it == AppPrefs.KEY_GRID_AUTO_HIDE_EMPTY_EVENING ||
                it == AppPrefs.KEY_GRID_EVENING_START ||
                it == AppPrefs.KEY_PERIOD_HEADER_LAYOUT ||
                it == AppPrefs.KEY_PERIOD_HEADER_STYLE ||
                it == AppPrefs.KEY_PERIOD_HEADER_HANGING ||
                it == AppPrefs.KEY_PERIOD_HEADER_SHOW_X ||
                it == AppPrefs.KEY_PERIOD_HEADER_HIDE_TIME ||
                it == AppPrefs.KEY_GRID_SHOW_SEPARATORS ||
                it == AppPrefs.KEY_GRID_LONG_BREAK_SPACING
        }.collect { prefVersion++ }
    }
    val context = LocalContext.current
    // 实验室开关 (2026-09-16 用户令): 自适应行高/晚间收起默认全关 —
    // 默认行为回归原固定行高 + 完整时间轴; 晚间起始时间用户自定义, 不再机械 18:00
    val adaptiveHeight = AppPrefs.isGridAdaptiveHeight(context)
    val autoHideEmptyEvening = AppPrefs.isGridAutoHideEmptyEvening(context)
    val eveningStart = TimetableViewportPolicy.parseEveningStart(
        AppPrefs.getGridEveningStart(context)
    )
    // 实验室开关(默认全关): 分隔线只管 drawBehind 里的额外网格线; 长课间留白只管
    // MealBreakDetector 结果是否进入行几何。关=严格回到 PR #56 之前的基线几何。
    val showSeparators = AppPrefs.isGridShowSeparators(context)
    val longBreakSpacing = AppPrefs.isGridLongBreakSpacing(context)

    val visibleSlotResult = remember(
        allCourses,
        timeSlots,
        visibleDays,
        timeJson,
        autoHideEmptyEvening,
        eveningStart,
        prefVersion
    ) {
        TimetableViewportPolicy.selectVisibleSlots(
            allCourses = allCourses,
            timeSlots = timeSlots,
            visibleDays = visibleDays,
            timeJson = timeJson,
            autoHideEmptyEvening = autoHideEmptyEvening,
            eveningStart = eveningStart
        )
    }
    val baseSlots = visibleSlotResult.slots
    // 先裁剪稳定的学期级基础槽位，再为当前周的非常规时间课程合成占位行。
    val renderPlan = remember(baseSlots, timeJson, courses) {
        if (timeJson != null) TimeTableUtils.buildRenderSlotPlan(courses, timeJson, baseSlots)
        else TimeTableUtils.RenderSlotPlan(baseSlots)
    }
    val renderSlots = renderPlan.slots
    val maxNode = renderSlots.maxOfOrNull { it.nodeEnd } ?: 12
    val sortedDays = visibleDays.sorted()
    val dayCount = sortedDays.size

    // 用户反馈 2026-09-16: 占位行"文字放不下 → 灰块 + 点击展开/再点折叠"。
    // 展开态键 = 占位行时间串("11:40-12:30", 周内多天共享同一空隙 → 同键联动展开);
    // 会话级 remember — 翻周/离开页面即复位(与 rotationSteps 同生命周期哲学)。
    // 展开/折叠通过改写 naturalWeights 的该行权重实现, rowHeightAt/yOfRows/gridH
    // 全部读同一张表 → 行高与 y 前缀和天然同源(时间轴不破)。
    var expandedPlaceholders by remember { mutableStateOf(setOf<String>()) }
    fun togglePlaceholder(key: String) {
        expandedPlaceholders = if (key in expandedPlaceholders) expandedPlaceholders - key
        else expandedPlaceholders + key
    }

    // issue#23: 边缘节次节点的"行号"按 renderSlots 自然顺序取(已按 node ASC 排序);
    // 前置节点(-1, 0)排到 grid 顶部, 后置节点(N+1, N+2)排到 grid 底部,
    // 视觉上就是"第 0 节在第 1 节之上" / "第 N+1 节在第 N 节之下", 与插入直觉一致。
    //   返回 -1 = 该节点不在 renderSlots(典型场景: 课程来自已删除的旧 timeJson, 数据脏)
    //   调用方需先判 >= 0 再绘;cardY 那侧 .coerceAtLeast(0) 兜底防负坐标。
    fun slotIndexOf(node: Int): Int = renderSlots.indexOfFirst { it.nodeStart == node }

    // issue#8 网格整体缩放: 0.7~1.3, 字号/行高/间距/圆角/内边距等比联动
    // (12节连堂课表缩到 0.7 可一屏放下; 只影响本 Cards 视图, 小组件与列表视图不受影响)
    val scale = AppPrefs.getGridScale(context)
    val cornerRatio = AppPrefs.getGridCornerRatio(context)
    val d = { v: Float -> (v * scale).dp }

    // 布局常量（全 dp, 乘 scale）
    val headH = d(52f)
    val headerStyle = AppPrefs.getPeriodHeaderStyle(context)
    val headerLayout = AppPrefs.getPeriodHeaderLayout(context)
    val headerHanging = AppPrefs.getPeriodHeaderHanging(context)
    val headerShowX = AppPrefs.isPeriodHeaderShowX(context)
    val headerTextMeasurer = rememberTextMeasurer()
    val headerDensity = LocalDensity.current
    // columnFont/columnPlacements 需真实行高, 故搁后到 rowHeightDp 可用后 (见下 BoxWithConstraints)。

    val timeW = if (headerLayout == "three_line") {
        threeLineWidthDp(
            renderSlots,
            headerStyle,
            scale,
            headerTextMeasurer,
            LocalDensity.current,
            headerHanging,
            headerShowX,
        )
            // 下限防极窄机型挤压时间列; 上限不再死钉 68dp — "第 X 节"更长标签
            // 需要 3×开始时间宽度的完整轨道, 交给 threeLineWidthDp 按实测回传。
            .coerceAtLeast(d(46f))
    } else {
        // 用户令 (老式表头同三行式统一): 列宽 = dash 轴几何
        // (所有行 dash 同一 X, 列宽含最宽行两侧余量), 不再硬钉 68dp。
        // hideTime=true 时时间行不渲染, 列宽收口到标签实测宽 (legacyTimeWidthDp)。
        legacyTimeWidthDp(
            renderSlots,
            headerStyle,
            scale,
            headerTextMeasurer,
            LocalDensity.current,
            headerShowX,
            AppPrefs.isPeriodHeaderHideTime(context),
        )
            // 下限与三行式同一防挤压口径
            .coerceAtLeast(d(46f))
    }
    val gapH = d(4f)
    val gapW = d(5f)
    // 长课间使用固定薄带 (24dp×gridScale×fontScale), 悬浮在等高课程行之间 —
    // 不改行高, 不折权重 (用户 2026-10 令: 旧 6dp 带宽 + 行等比压缩视觉累积成
    // 整行空隙; 现在带宽固定且行高刚性)。
    val mealGapExtra = if (longBreakSpacing) d(24f) * LocalConfiguration.current.fontScale else 0.dp
    /** 餐段锚行集 = 显示行索引 (band 插在该行与下一行之间)。 */
    val mealBreakAfterRows: Set<Int> =
        remember(timeJson, courses, renderSlots, longBreakSpacing) {
            if (!longBreakSpacing || timeJson == null) emptySet() else {
                val baseRows = TimeTableUtils.parseTimeSlotRows(timeJson)
                MealBreakDetector.detect(timeJson, courses).mapNotNull { detected ->
                    val leftNode = baseRows.getOrNull(detected.afterRowIndex)?.node
                        ?: return@mapNotNull null
                    renderSlots.indexOfLast { it.nodeEnd == leftNode }
                        .takeIf { it >= 0 }
                }.toSet()
            }
        }

    val gridBgShape = SleepyTheme.shapes.large

    Box(
        modifier = modifier
            .fillMaxSize()
            .dismissQuickAddOnOutsidePress(quickAddState)
            .dismissFloatOnOutsidePress(floatState, ::confirmPendingResizeOrDismiss)
            .background(colors.surfaceContainerHigh, gridBgShape)
            .padding(d(8f))
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // 自动适配只改变纵向行高；横向宽度、字号和卡片内容仍由原 gridScale 控制。
            val navExtra = com.lingion.sleepy.ui.component.LocalNavExtraBottomPadding.current
            // 餐段带在网格外额外占高，拟合行高只按课程行权重计算 (刚性, 带宽不摊进 rowH)。
            val mealBandCount = mealBreakAfterRows.count { it in renderSlots.indices && it + 1 < renderSlots.size }
            val mealBandTotal = mealGapExtra * mealBandCount
            val availableGridHeight = (maxHeight - headH - gapH - navExtra - mealBandTotal)
                .value
                .coerceAtLeast(0f)
            val naturalWeightsBase: List<Float> = renderPlan.slotWeights ?: List(renderSlots.size) { 1f }
            val fitWeights = naturalWeightsBase
            // 行高基座 (2026-09-16 用户令): 实验室开自适应=拟合高度; 默认关=原固定 52dp×scale。
            // 双指手势相对基座缩放, 上限 96dp 下限 36dp (×scale); 顶栏 tick 确认后长期生效, 撤回回退上次确认值。
            val baseRowHeight = TimetableViewportPolicy.baseRowHeightDp(
                adaptive = adaptiveHeight,
                fitRowHeightDp = TimetableViewportPolicy.fitRowHeightDp(
                    availableGridHeightDp = availableGridHeight,
                    slotWeights = fitWeights,
                    slotCount = renderSlots.size,
                    contentScale = scale
                ),
                contentScale = scale
            )
            val rowHeightDp = TimetableViewportPolicy.manualRowHeightDp(
                baseRowHeightDp = baseRowHeight,
                verticalScale = rowHeightScale,
                minRowHeightDp = TimetableViewportPolicy.MIN_ROW_DP * scale.coerceIn(0.7f, 1.3f),
                maxRowHeightDp = TimetableViewportPolicy.MAX_ROW_DP * scale.coerceIn(0.7f, 1.3f)
            )
            val rowH = rowHeightDp.dp

            // 用户反馈 2026-09-16: 展开的占位行权重换成"正好显示完文字"的展开权重 —
            // natural/boundary 两表同族, 行高与 y 同源。
            // 占位行文字适配检测也是几何函数(随 rowH/scale 联动), 放得下才允许灰置。
            // 两表 (交叉验证 blocker #1 修正): natural = 内容高(卡片/表头卡),
            // boundary = 行边界坐标(yOfRows/分隔线/gridH, 含长课间折权)。
            // 无占位行(slotWeights=null)时也走均匀表 — 否则折权被兜底表整体丢弃。
            val naturalWeights: List<Float> = naturalWeightsBase.mapIndexed { i, w ->
                val slot = renderSlots.getOrNull(i)
                if (slot != null && slot.isPlaceholder && slot.timeString in expandedPlaceholders) {
                    maxOf(
                        w,
                        TimeTableUtils.placeholderExpandedWeight(
                            rowHeightDp = rowHeightDp,
                            gapDp = TimetableViewportPolicy.ROW_GAP_DP,
                            requiredTextHeightDp = PLACEHOLDER_TEXT_REQUIRED_DP
                        )
                    )
                } else w
            }
            val boundaryWeights: List<Float> = naturalWeights
            /** natural 累计坐标 (r = 行分数, 同 yOfRows 口径, 无折权)。 */
            fun naturalSpan(r: Float): Dp {
                val ws = naturalWeights
                var acc = 0f
                val full = r.toInt().coerceAtMost(ws.size)
                for (i in 0 until full) acc += ws[i]
                if (full < ws.size && r > full) acc += ws[full] * (r - full)
                return rowH * acc
            }
            /** natural 跨度 (dp): 卡片高度用 — 餐段空隙不计入卡高。 */
            fun contentSpanDpOf(from: Float, to: Float): Dp =
                naturalSpan(to) - naturalSpan(from)

            // 用户反馈 2026-09-09 (精度): 时间轴按分钟加权 — 占位行只占真实分钟占比
            // (5 分钟占位 ≈ 0.111 标准行), 不再整行拉满把时间轴歪曲。
            // yOfRows(r) = 加权行坐标 r(0.0=网格顶, 1.0=一标准行) → dp (boundary 口径);
            fun yOfRows(r: Float): Dp {
                val ws = boundaryWeights
                var acc = 0f
                val full = r.toInt().coerceAtMost(ws.size)
                for (i in 0 until full) acc += ws[i]
                if (full < ws.size && r > full)
                    acc += (naturalWeights.getOrNull(full) ?: ws[full]) * (r - full)
                val crossedBreaks = mealBreakAfterRows.count { it + 1 <= full }
                return rowH * acc + mealGapExtra * crossedBreaks
            }
            fun rowHeightAt(i: Int): Dp = rowH * (naturalWeights.getOrNull(i) ?: 1f)
            fun gapAfterRow(i: Int): Dp = gapH + if (i in mealBreakAfterRows) mealGapExtra else 0.dp

            // 三行表头卡内容高 = 非占位 rowHeightAt − 行间 gap − 卡片内边距×2 (PERIOD_HEADER_CARD_PAD_DP, 与渲染侧一致)。
            // 用户令 2026-10-03: 整列字号的卡高输入必须用真实行高 (双指缩行/自适应行高联动),
            // 假 52f*scale 输入曾使矮卡字号过大 → 三行行盒互相重叠。最矮行兜底整列字号。
            val headerContentHeightSp = run {
                val padDp = 2f * PERIOD_HEADER_CARD_PAD_DP * scale
                val minRowDp = renderSlots.withIndex()
                    .filter { !it.value.isPlaceholder }
                    .minOfOrNull { (i, _) ->
                        (rowHeightAt(i) - gapH).value.coerceAtLeast(0f)
                    } ?: (rowHeightDp.dp - gapH).value
                with(headerDensity) { (minRowDp - padDp).dp.toPx() } / headerDensity.density
            }.coerceAtLeast(1f)
            // 用户 2026-09-29: 整列统一字号 — 全列行约束先收集, 由最紧约束算一次;
            // 每行共用同一个字号, 不再逐行独立自适应造成"有的行大有的行小"。
            val columnFont = if (headerLayout != "three_line") null else {
                val timeStyle = headerTimeStyle(scale)
                val labelStyle = headerLabelStyle(scale)
                val dPx = headerDensity.density
                val rows = renderSlots.filter { !it.isPlaceholder }.map { slot ->
                    val label = if (headerShowX && slot.nodeStart == slot.nodeEnd) {
                        PeriodHeaderFormatter.fullLabel(slot.nodeStart, headerStyle)
                    } else {
                        PeriodHeaderFormatter.range(slot.nodeStart, slot.nodeEnd, headerStyle)
                    }
                    val startW = headerTextMeasurer.measure(slot.displayStart, timeStyle).size.width
                    val endW = headerTextMeasurer.measure(slot.displayEnd, timeStyle).size.width
                    val labelW = headerTextMeasurer.measure(label, labelStyle).size.width.toFloat()
                    val timeMaxW = maxOf(startW, endW)
                    // 墨迹宽 = 时间块 + 标签在 u=0 时的联合包络 (端点异锚中点)
                    // 用基准字号测, compute 内部会按字号比投影到自适应字号
                    val inkW = timeMaxW + labelW
                    PeriodHeaderAdaptiveFont.RowConstraint(
                        inkWidthSp = inkW / dPx,
                        timeMaxWidthSp = timeMaxW / dPx,
                        labelWidthSp = labelW / dPx,
                    )
                }
                PeriodHeaderAdaptiveFont.forColumn(
                    cardWidthSp = 240f,
                    cardHeightSp = headerContentHeightSp,
                    rows = rows,
                )
            }
            // 用户令 2026-10-03: legacy 列级 dash 轴 (dp, 相对卡内容框左缘) —
            // 几何与 legacyLineWidthDp 同一解 (pad 已在列宽里, 轴本身不含 pad);
            // 网格所有行同一轴, 预览单卡未传走行内退化。
            val legacyAxis = if (headerLayout == "three_line") null else {
                val timeStyle = headerTimeStyle(scale)
                val labelStyle = headerLabelStyle(scale)
                val rows = renderSlots.filter { !it.isPlaceholder }.map { slot ->
                    val label = if (headerShowX && slot.nodeStart == slot.nodeEnd) {
                        PeriodHeaderFormatter.fullLabel(slot.nodeStart, headerStyle)
                    } else {
                        PeriodHeaderFormatter.range(slot.nodeStart, slot.nodeEnd, headerStyle)
                    }
                    LegacyDashRowInk(
                        labelPx = headerTextMeasurer.measure(label, labelStyle).size.width.toFloat(),
                        timeLeftPx = headerTextMeasurer.measure(slot.displayStart, timeStyle).size.width.toFloat(),
                        dashPx = headerTextMeasurer.measure("-", timeStyle).size.width.toFloat(),
                        timeRightPx = headerTextMeasurer.measure(slot.displayEnd, timeStyle).size.width.toFloat(),
                    )
                }
                with(LocalDensity.current) {
                    // 偏移现在以卡片内容框 (SingleTimeHeadCell padding 之内) 为原点 —
                    // PeriodHeaderCellContent 根 padding 已拆 (双重 pad 修复, 2026-10-04),
                    // 此处不再 +padDp, 否则全列墨迹右偏一个 pad (设备实锤右溢 7px)。
                    legacyDashColumnPx(rows, padPx = 0f).dashCenterX.toDp()
                }
            }
            // 用户令 2026-09-30: 整列统一卡宽 — 所有行卡片 = timeW (整列最宽包络),
            // 窄行文字由列级中点对齐 (PeriodHeaderCellContent sharedPlacement),
            // 取代 2026-09-27 的逐行收口 (用户复令: 卡片宽度永远一致, 禁参差)。
            val columnPlacements = remember(renderSlots, headerStyle, headerLayout, headerHanging, headerShowX, scale) {
                if (headerLayout != "three_line") return@remember null
                val timeStyle = headerTimeStyle(scale)
                val labelStyle = headerLabelStyle(scale)
                // 与 PeriodHeaderCellContent 自适应字号投影同一口径: 16sp 投影
                val projection = PeriodHeaderAdaptiveFont.MAX_LABEL_SP / PeriodHeaderAdaptiveFont.BASE_LABEL_SP
                val rows = renderSlots.filter { !it.isPlaceholder }.map { slot ->
                    val label = if (headerShowX && slot.nodeStart == slot.nodeEnd) {
                        PeriodHeaderFormatter.fullLabel(slot.nodeStart, headerStyle)
                    } else {
                        PeriodHeaderFormatter.range(slot.nodeStart, slot.nodeEnd, headerStyle)
                    }
                    PeriodHeaderMetrics(
                        startWidth = headerTextMeasurer.measure(slot.displayStart, timeStyle).size.width * projection,
                        endWidth = headerTextMeasurer.measure(slot.displayEnd, timeStyle).size.width * projection,
                        labelWidth = headerTextMeasurer.measure(label, labelStyle).size.width.toFloat() * projection,
                        showX = headerShowX && slot.nodeStart == slot.nodeEnd,
                    )
                }
                solveColumnPlacement(rows, headerHanging)
            }

            // 算出每列宽度 (dp)
            val colW = (maxWidth - timeW - gapW * (dayCount + 1)) / dayCount
            // issue#23: grid 高度按 renderSlots 行数算(maxNode 已不反映边缘节点总数)——
            // edge 节点在 timeSlots 末尾, 视觉上自然排到第 N 节之下;
            // 用户反馈 2026-09-09: 占位节次行同样扩展网格高度(按分钟加权, 不占满整行)。
            val gridH = yOfRows(renderSlots.size.coerceAtLeast(1).toFloat())

            val scrollState = rememberScrollState()

            // ---- 长按浮起交互 (onOccurrenceMove != null 才启用; null = 旧长按直开编辑器) ----
            // 浮卡坐标与网格主体 Box 同原点(dp); 渲染在滚动 Column 的平级兄弟上 —
            // renderEffect 自包含(整层进模糊纹理), 浮层若做 Column 子节点会被一起糊掉,
            // 故 overlay y = floatState.y + headH + gapH − scrollState (抵消表头与滚动)。
            var floatDragPx by remember { mutableFloatStateOf(0f) }
            // 边缘翻页锚点: 本页网格左缘实时值(root 坐标 px); 起拖页快照存 floatState.anchorRootLeftPx。
            var floatRootLeftPx by remember { mutableFloatStateOf(0f) }
            // 滚动偏移只在浮起态读取 — 平时不在 body 读 scrollState.value,
            // 避免每帧滚动重组整个 BoxWithConstraints 子树。
            val floatScrollDp =
                // 拖拽中用锚点快照: 翻页换宿主的当帧新页 scrollState 还是 0(同步 effect
                // 要等组合后才跑), 直读会跳一行; 非拖拽态读实时值(旧行为不变)。
                if (!floatState.active) 0.dp
                else if (floatState.dragging) floatState.anchorScrollDp
                else with(headerDensity) { scrollState.value.toDp() }
            // 排他宿主接管时新页滚动对齐拖拽锚: 各页 rememberScrollState 独立(新页=0),
            // 不同步则卡悬停行与视觉行错位一行以上。同步后宿主页几何与起拖页逐 dp 相同。
            LaunchedEffect(floatState.active, floatState.dragging, floatState.week, currentWeek) {
                if (floatState.active && floatState.dragging && currentWeek == floatState.week) {
                    scrollState.scrollTo(with(headerDensity) { floatState.anchorScrollDp.toPx().roundToInt() })
                }
            }
            val floatGridNodes = renderSlots.filter { !it.isPlaceholder }.map { it.nodeStart }
            // 卡片顶/底边在网格坐标系的 dp 边界 (bottom=true 取底边, 与胶囊同口径)。
            fun floatBoundary(node: Int, bottom: Boolean): Dp? {
                val idx = slotIndexOf(node)
                if (idx < 0) return null
                return if (bottom) yOfRows(idx.toFloat()) + rowHeightAt(idx) - gapH
                else yOfRows(idx.toFloat())
            }
            fun liftCourse(course: CourseEntity) {
                if (!floatAware) { onCourseLongClick?.invoke(course); return }
                val nodeIdx = slotIndexOf(course.startNode)
                val dayIdx = sortedDays.indexOf(course.day)
                if (nodeIdx < 0 || dayIdx < 0) return
                val steps = course.step.coerceAtLeast(1).coerceAtMost(renderSlots.size - nodeIdx)
                quickAddState.dismiss()
                floatState.lift(
                    course = course,
                    day = course.day,
                    week = currentWeek,
                    startNode = course.startNode,
                    endNode = course.startNode + steps - 1,
                    x = timeW + gapW + (colW + gapW) * dayIdx,
                    y = yOfRows(nodeIdx.toFloat()),
                    width = colW,
                    // 整格口径 (非 ownTime frac) — 与胶囊一致: 浮起后编辑/拖拽都按节次栅格走。
                    height = TimetableViewportPolicy.longBreakCardHeightDp(
                        naturalSpanDp = (naturalSpan((nodeIdx + steps).toFloat()) -
                            naturalSpan(nodeIdx.toFloat())).value,
                        gapDp = gapH.value,
                    ).dp,
                    isGrey = course.day in greyDays,
                )
            }
            fun seedFloatResize(top: Boolean) {
                val boundary = floatBoundary(if (top) floatState.startNode else floatState.endNode, !top)
                    ?: return
                floatDragPx = with(headerDensity) { boundary.toPx() }
            }
            // 与 QuickAddCourseCapsule 的 onResize 同款就近吸附: 只动被抓的边, 对边钉死。
            fun applyFloatResize(top: Boolean, delta: Float) {
                floatDragPx += delta
                val nodes = floatGridNodes.toSet()
                val candidates = if (top) {
                    var first = floatState.startNode
                    while (first > 1 && first - 1 in nodes) first--
                    (first..floatState.endNode).filter { it in nodes }
                } else {
                    var last = floatState.endNode
                    val maxNode = floatGridNodes.maxOrNull() ?: last
                    while (last < maxNode && last + 1 in nodes) last++
                    (floatState.startNode..last).filter { it in nodes }
                }
                val nearest = candidates.minByOrNull { node ->
                    val b = floatBoundary(node, !top) ?: return@minByOrNull Float.MAX_VALUE
                    abs(with(headerDensity) { b.toPx() } - floatDragPx)
                } ?: return
                if (top) {
                    floatState.startNode = nearest
                    floatState.y = yOfRows(slotIndexOf(nearest).toFloat())
                } else floatState.endNode = nearest
                val endIdx = slotIndexOf(floatState.endNode)
                floatState.height =
                    yOfRows(endIdx.toFloat()) + rowHeightAt(endIdx) - gapH - floatState.y
            }
            // 非法落点/拖拽撤销共用的弹回: 位置复位 + dragging=false → 模糊与浮框同帧回归。
            fun springFloatBack() {
                floatState.dragging = false
                floatState.x = floatState.originX
                floatState.y = floatState.originY
            }
            fun commitFloatDrop() {
                val course = floatState.course ?: return
                if (!floatState.dragging) return // 浮卡上的轻点: 不提交也不弹回(原位仍浮起)。
                // 落点 = 卡片中心列 + 顶边最近节次。列向钳位 (2026-10-28 用户反馈:
                // 压右缘松手曾整段静默弹回、像什么都没发生): 水平出界一律吸附到最近
                // 合法列 — 边缘区落点 = 首/末列。纵向出格(顶边吸附不到任何节次、
                // 跨度越出末节)仍是非法落点 → 弹回。
                val centerRatio =
                    (floatState.x + floatState.width / 2f - timeW - gapW).value / (colW + gapW).value
                val dayIdx = floor(centerRatio).toInt().coerceIn(0, sortedDays.lastIndex)
                val cardTopPx = with(headerDensity) { floatState.y.toPx() }
                val snapped = floatGridNodes.minByOrNull { node ->
                    val b = floatBoundary(node, false) ?: return@minByOrNull Float.MAX_VALUE
                    abs(with(headerDensity) { b.toPx() } - cardTopPx)
                }
                val maxNode = floatGridNodes.maxOrNull()
                // 跨度按"节次号连续"校验(午餐空隙不打断合法性), 只要求落点不越出末节。
                val endNode = snapped?.let { it + floatState.step - 1 }
                if (snapped == null || endNode == null || maxNode == null || endNode > maxNode) {
                    springFloatBack()
                    return
                }
                onOccurrenceMove?.invoke(course, sortedDays[dayIdx], snapped, floatState.step, false) { saved ->
                    // C 契约: 落库成功才收层; 否决/过期 → 弹回原位浮着(拉伸 pending 随卡保留,
                    // 由空白点击/返回键走「是否调整持续节次」确认)。
                    if (saved) floatState.dismiss() else springFloatBack()
                }
            }

            // ---- 边缘翻页 (2026-10-27 用户裁决: 压住左右缘驻留满 500ms 自动翻周) ----
            // 单一写入者: 拖拽期间 50ms 轮询自当前卡位推导方向并累计驻留 —
            // 手指静止压边同样计时; week 入键 → 换页即整效重启, 邻页实例与旧页实例
            // 即刻退出(不空转), 新宿主 elapsed/held 天然归零(逐页满 500ms, 无加速)。
            LaunchedEffect(floatState.dragging, floatState.week) {
                // 非宿主页静默退出: 不写共享态 — 清场由宿主页负责, 免得邻页抢先抹掉进度。
                if (currentWeek != floatState.week) return@LaunchedEffect
                if (!floatState.dragging || onEdgeDrag == null || floatMaxWeek <= 0) {
                    floatState.edgeDir = 0
                    floatState.edgeBlocked = 0
                    floatState.peekProgress = 0f
                    return@LaunchedEffect
                }
                var held = 0
                var elapsed = 0L
                while (true) {
                    delay(FLOAT_EDGE_DWELL_STEP_MS)
                    val cx = (floatState.x + floatState.width / 2f).value
                    // 防误触: 拖拽起点起水平位移未超 slop 不判边缘 (FullCalendar everMoved 同构)。
                    val dir = when {
                        abs(cx - floatState.dragOriginCx.value) < FLOAT_EDGE_SLOP_DP -> 0
                        cx < FLOAT_EDGE_ZONE_DP -> -1
                        cx > maxWidth.value - FLOAT_EDGE_ZONE_DP -> 1
                        else -> 0
                    }
                    if (dir != held) { held = dir; elapsed = 0L }
                    floatState.edgeDir = dir
                    // 回到格内 = 重新武装: 允许下一次满驻留翻页 (2026-10-28 用户反馈:
                    // 旧实现仍压边就每 500ms 连翻, 根本停不了)。
                    if (dir == 0) {
                        floatState.edgeArmed = true; floatState.edgeBlocked = 0; floatState.peekProgress = 0f; continue
                    }
                    val atBoundary = (dir < 0 && floatState.week <= 1) || (dir > 0 && floatState.week >= floatMaxWeek)
                    floatState.edgeBlocked = if (atBoundary) dir else 0
                    if (atBoundary) {
                        // 学期边界: 无计时, peek 静止示形, 放手弹回。
                        floatState.peekProgress = 1f
                        continue
                    }
                    if (!floatState.edgeArmed) { floatState.peekProgress = 0f; continue }
                    elapsed += FLOAT_EDGE_DWELL_STEP_MS
                    floatState.peekProgress = (elapsed.toFloat() / FLOAT_EDGE_DWELL_MS).coerceIn(0f, 1f)
                    if (elapsed >= FLOAT_EDGE_DWELL_MS) {
                        // 驻留满: 周号先行 — 浮层/计时/落点几何同帧整体换宿主(排他 currentWeek==week),
                        // 补偿平移把卡钉在指下, 300ms 页滑期间不飘。一次驻留只翻一页:
                        // 翻过即消耗(edgeArmed=false), 必须收回格内再压出来才翻下一页。
                        floatState.edgeArmed = false
                        floatState.week += dir
                        floatState.chipPending = true // 翻过页 → 顶栏 chip 待回写 (松手/落位时消费)
                        elapsed = 0L
                        held = 0
                        floatState.peekProgress = 0f
                        onEdgeDrag.invoke(dir)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // 边缘翻页锚点: 本页网格主体在 root 坐标系的左缘。翻页动画 300ms 内
                    // 浮层用「起拖页左缘 − 当前宿主左缘」反平移钉在指下; 位移符号由实测
                    // 坐标天然决定, 不依赖 pager 内部 offset 公式。仅浮动态写值 —
                    // 平时回调零写入, 不给滚动加重组。
                    .onGloballyPositioned { if (floatState.active) floatRootLeftPx = it.boundsInRoot().left }
                    .then(
                        // v1.0.56 T3: 实验室开关默认关 — 手势不挂即捏不动; 开=原行为
                        if (pinchZoomEnabled) Modifier.verticalResizeGesture(
                            baseRowHeightDp = baseRowHeight,
                            currentRowHeightDp = rowHeightDp,
                            contentScale = scale,
                            onRowHeightScaleChange = onRowHeightScaleChange
                        ) else Modifier
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .then(
                            // 浮起=整层网格进高斯模糊纹理; 移动中/dragging=false 复原。
                            if (floatState.active && !floatState.dragging) Modifier.gridFloatBlur()
                            else Modifier
                        )
                ) {
                // ---- 表头：自然 Compose Row ----
                Row(
                    modifier = Modifier.fillMaxWidth().height(headH),
                    horizontalArrangement = Arrangement.spacedBy(gapW),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(timeW))
                    for (day in sortedDays) {
                        val dateStr = if (showDate && startDate.isNotBlank()) {
                            try {
                                val ds = DateUtils.dateOfWeek(startDate, currentWeek, day)
                                DateUtils.shortDate(ds)
                            } catch (_: Exception) { null }
                        } else null
                        DayHeadCell(
                            day = day,
                            isToday = startDate.isNotBlank() && runCatching {
                                DateUtils.isDateToday(DateUtils.dateOfWeek(startDate, currentWeek, day), todayDate)
                            }.getOrDefault(false),
                            isGrey = day in greyDays,
                            courseCount = courses.count { it.day == day },
                            dateStr = dateStr,
                            scale = scale,
                            cornerRatio = cornerRatio,
                            modifier = Modifier.width(colW).fillMaxHeight()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(gapH))

                // ---- Grid 主体：固定高度 Box，内部全用 Modifier.offset 绝对定位 ----
                Box(
                    modifier = Modifier.fillMaxWidth().height(gridH).drawBehind {
                        if (!showSeparators) return@drawBehind
                        val lineColor = colors.outlineVariant.copy(alpha = 0.42f)
                        val stroke = 0.7.dp.toPx()
                        val firstColumn = timeW.toPx() + gapW.toPx() / 2f
                        val breakBands = mealBreakAfterRows.mapNotNull { row ->
                            if (row !in renderSlots.indices || row + 1 >= renderSlots.size) return@mapNotNull null
                            val bandBottom = yOfRows((row + 1).toFloat()).toPx()
                            val bandTop = bandBottom - gapAfterRow(row).toPx()
                            GridSeparatorGeometry.Span(bandTop, bandBottom)
                        }
                        for (column in 0..dayCount) {
                            val x = firstColumn + column * (colW + gapW).toPx()
                            for (segment in GridSeparatorGeometry.verticalSegments(0f, size.height, breakBands)) {
                                drawLine(lineColor, Offset(x, segment.start), Offset(x, segment.end), stroke)
                            }
                        }
                        // 线-卡协同 (用户 2026-10-10): 普通格间横线画在间隙中点, 卡上下各留半 gap;
                        // 旧口径线贴卡顶(0)、卡底离线 gapH, 空白全堆在卡下 → 上下不对称。
                        // 长休带(楚河汉界, 2026-09-09 用户令)双线保持原位贴 band 上下边, 不中点化。
                        // boundary 0 保持 y=0: Box 上方是 Spacer(gapH), 负坐标有被父级裁剪风险。
                        for (boundary in 0..renderSlots.size) {
                            val y = when {
                                boundary == 0 -> 0f
                                (boundary - 1) in mealBreakAfterRows ->
                                    yOfRows(boundary.toFloat()).toPx()          // 长休带下边界: 原位
                                else ->
                                    yOfRows(boundary.toFloat()).toPx() -
                                        gapAfterRow(boundary - 1).toPx() / 2f   // 普通格间: 间隙中点
                            }
                            drawLine(lineColor, Offset(timeW.toPx(), y), Offset(size.width, y), stroke)
                        }
                        for (band in breakBands) {
                            drawLine(lineColor, Offset(timeW.toPx(), band.start), Offset(size.width, band.start), stroke)
                        }
                    }
                ) {
                    // 时间栏：每个节次一个 Row，用 offset 定位到正确 y (分钟加权)
                    for ((i, slot) in renderSlots.withIndex()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((rowHeightAt(i) - gapH).coerceAtLeast(0.dp))
                                .offset(y = yOfRows(i.toFloat())),
                            horizontalArrangement = Arrangement.spacedBy(gapW),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 用户反馈 2026-09-16: 占位行几何检测 — 文字放不下 = 灰块,
                            // 点击展开(该行权重换展开权重, 下方时间轴同帧下移), 再点折叠。
                            val phFitsText = if (!slot.isPlaceholder) true else {
                                TimeTableUtils.placeholderTextFits(
                                    rowWeight = naturalWeights.getOrNull(i) ?: 1f,
                                    rowHeightDp = rowHeightDp,
                                    gapDp = TimetableViewportPolicy.ROW_GAP_DP,
                                    requiredTextHeightDp = PLACEHOLDER_TEXT_REQUIRED_DP
                                )
                            }
                            SingleTimeHeadCell(
                                slot = slot,
                                scale = scale,
                                modifier = Modifier.width(timeW).fillMaxHeight(),
                                visibleWidth = null,
                                cornerRatio = cornerRatio,
                                textFits = phFitsText,
                                onToggleExpand = if (slot.isPlaceholder && !phFitsText) {
                                    { togglePlaceholder(slot.timeString) }
                                } else null,
                                sharedFont = columnFont,
                                sharedPlacement = columnPlacements?.getOrNull(
                                    renderSlots.subList(0, i).count { !it.isPlaceholder }
                                ),
                                sharedLegacyAxis = legacyAxis,
                            )
                            // 透明占位：保证行宽和表头一致
                            for (day in sortedDays) {
                                val addLabel = stringResource(R.string.occurrence_add_cell, DateUtils.localizedDay(day, context), slot.nodeStart)
                                Spacer(modifier = Modifier.width(colW).fillMaxHeight().testTag("add-course-$day-${slot.nodeStart}").then(
                                    if (onQuickAdd != null && quickAddEnabled && !slot.isPlaceholder) Modifier.semantics { contentDescription = addLabel }.noRippleCombinedClickable(
                                        // B 契约: 浮动态轻点空白格 = 无动作(防误触), 长按才开加课胶囊;
                                        // 旧非浮动态轻点即开不变。Course cards above this layer consume
                                        // occupied-cell taps; 浮层空档按下已被 ancestor 消费, 到这里恒为无浮动态。
                                        onClick = { if (!floatAware) quickAddState.selection = QuickAddRange(day, slot.nodeStart, slot.nodeStart) },
                                        onLongClick = if (floatAware)
                                            ({ quickAddState.selection = QuickAddRange(day, slot.nodeStart, slot.nodeStart) })
                                        else null,
                                    ) else Modifier
                                ))
                            }
                        }
                    }

                    // 课程卡片：用 offset 绝对定位 — 冲突簇整簇走 ConflictClusterCard,
                    // 非簇课保持原 CourseOverlayCard 单卡路径(回归保护)
                    val context = LocalContext.current
                    val conflictStyle = AppPrefs.getConflictStyle(context)
                    // v7.10.16r 轮换态(issue#10): 簇键 → 轮换步数 — 与 topOverrides 同级
                    // 持有(ScheduleScreen),翻页/周切换不丢(评审 #1);纯会话级不落盘,
                    // 离开课表页即重置。默认序按用户置顶偏好走,轮换只改当次视图。
                    // v7.10.5: 交换置顶状态上提到 ScheduleScreen — 详情弹窗 radio 点击
                    // 与网格 onPickTop 写同一真相源,radio 勾选瞬间网格同帧换层。
                    // (此前 radio 只写持久化 defaultTopMap,被会话级 topOverrides 遮蔽 → 看似不生效)
                    fun setTopOverride(key: String, courseId: Long?) = onSetTopOverride(key, courseId)

                    // v7.9 默认置顶:跨会话持久化偏好,由详情弹窗 radio 写入。
                    // 与 topOverrides 同簇键,作 topOverrideId fallback — 当次切换仍由 topOverrides 主导。
                    val defaultTopMap by AppPrefs.conflictDefaultTopFlow(context).collectAsState(
                        initial = AppPrefs.getConflictDefaultTop(context)
                    )

                    // 引擎聚簇: 同天区间相交(含链式)课程归簇,簇键=主课判定序首课三元组
                    // (override 不改变首课——findClusters 输出固定,键稳定)
                    // 用户反馈 2026-09-09: 带 timeJson 按真实时间区间(分钟级)聚簇 —
                    // 跨空隙反算的节点范围不再制造假冲突(12:30 结束被吸进 14:00 节)。
                    val clusters = ConflictLayoutEngine.findClusters(courses, timeJson)
                    val clusteredIds = clusters.flatMap { c -> c.courses.map { it.id } }.toSet()

                    for (cluster in clusters) {
                        // 簇内课若因 visibleDays 过滤或节点不在 timeJson 则整簇跳过
                        if (cluster.day !in visibleDays) continue
                        // issue#23: 边缘节点同样允许 — 用 slotIndexOf 兜底,数据脏返回 -1 也直接过滤掉
                        val inGrid = cluster.courses.filter {
                            slotIndexOf(it.startNode) >= 0
                        }
                        if (inGrid.isEmpty()) continue
                        val anchor = cluster.courses.first() // 主课判定序首位,决定簇基点
                        val dayIdx = sortedDays.indexOf(cluster.day)
                        val cardX = timeW + gapW + (colW + gapW) * dayIdx
                        // 用户报障 2026-09-10: 簇锚点用锚课**小数行坐标**(ownTime 锚课
                        // 不再被 slotIndexOf 整格吸附丢掉小数偏移, 整簇上移 ~0.9 行的本体);
                        // 加权 dp 与簇内 spanDpOf 同一真值。
                        val anchorFrac = if (anchor.ownTime && anchor.startTime.isNotBlank() && anchor.endTime.isNotBlank()) {
                            TimeTableUtils.timeToFractionalRows(anchor.startTime, anchor.endTime, renderSlots)?.first
                        } else null
                        val anchorRow = anchorFrac ?: slotIndexOf(anchor.startNode).coerceAtLeast(0).toFloat()
                        val cardY = yOfRows(anchorRow)
                        val clusterKey = ConflictLayoutEngine.conflictClusterKey(cluster)

                        ConflictClusterCard(
                            cluster = cluster,
                            style = conflictStyle,
                            topOverrideId = topOverrides[clusterKey] ?: defaultTopMap[clusterKey],
                            onPickTop = { id -> setTopOverride(clusterKey, id) },
                            // v7.10.16r 轮换(issue#10): 步数与回调都按簇键隔离,纯会话态不落盘
                            rotationStep = rotationSteps[clusterKey],
                            onRotate = {
                                onRotationStep(clusterKey, (rotationSteps[clusterKey] ?: 0) + 1)
                            },
                            onPickFromBadge = { layerPos ->
                                // 气泡选课 = 换来看: 步数使目标层转到基准序首位(会话态)
                                onRotationStep(clusterKey, layerPos)
                            },
                            onCourseClick = onCourseClick,
                            onCourseLongClick = if (floatAware) ({ c: CourseEntity -> liftCourse(c) }) else onCourseLongClick,
                            colW = colW,
                            rowH = rowH,
                            maxNode = maxNode,
                            timeSlots = renderSlots,
                            spanDpOf = { from, to -> yOfRows(to) - yOfRows(from) },
                            // 卡高走 natural 跨度 — 空隙不被算进卡片高度, 空白落在卡后
                            contentSpanDpOf = { from, to -> contentSpanDpOf(from, to) },
                            timeW = timeW,
                            gapW = gapW,
                            gapH = gapH,
                            isGrey = cluster.day in greyDays,
                            modifier = Modifier.offset(x = cardX, y = cardY)
                        )
                    }

                    for (course in courses) {
                        if (course.day !in visibleDays) continue
                        // issue#23: 边缘节点允许 — slotIndexOf = -1 表示该课 startNode 不在当前 timeJson,
                        // 通常是数据脏(老 timeJson 残留了已删节点), 静默跳过不渲染避免越界
                        val nodeIdx = slotIndexOf(course.startNode)
                        if (nodeIdx < 0) continue
                        if (course.id in clusteredIds) continue // 簇内课已由 ConflictClusterCard 绘制
                        val dayIdx = sortedDays.indexOf(course.day)
                        // 步长上限按剩余行数算(边缘节点也按 renderSlots 总行数取模)
                        val steps = course.step.coerceAtLeast(1)
                            .coerceAtMost(renderSlots.size - nodeIdx)
                        val cardX = timeW + gapW + (colW + gapW) * dayIdx
                        // issue#23 §5: 非常规时间(ownTime)课按真实分钟比例定位(1.0 = 一整行);
                        // 用户反馈 2026-09-09: 比例映射基于 renderSlots(含占位节次) —
                        // 12:30 落在 11:40~12:30 占位行内, 不再侵入 14:00 行;
                        // 时间映射失败退回整格吸附(normalizeNode 已反算 nodeIdx/steps)
                        val frac = if (course.ownTime) TimeTableUtils.timeToFractionalRows(
                            course.startTime, course.endTime, renderSlots
                        ) else null
                        val cardY = frac?.let { yOfRows(it.first) } ?: yOfRows(nodeIdx.toFloat())
                        // 卡高走 natural 跨度 − 普通行隙 — 薄带不进卡高 (2026-10-17 修: 旧公式减
                        // gapAfterRow=gapH+带, 而 cardY 的 yOfRows 已加带, 同带双扣 → 锚行胶囊回缩)。
                        // 位置(cardY)仍走 yOfRows 边界, 与簇内 contentSpanDpOf−gapH 同规。
                        val cardH = if (frac != null) {
                            // 按比例(分钟加权), 保底 0.3 标准行避免过短课胶囊塌缩到不可点
                            TimetableViewportPolicy.longBreakCardHeightDp(
                                naturalSpanDp = (naturalSpan(frac.second) - naturalSpan(frac.first)).value,
                                gapDp = gapH.value,
                                minSpanDp = (rowH * 0.3f).value,
                            ).dp
                        } else {
                            TimetableViewportPolicy.longBreakCardHeightDp(
                                naturalSpanDp = (naturalSpan((nodeIdx + steps).toFloat()) -
                                    naturalSpan(nodeIdx.toFloat())).value,
                                gapDp = gapH.value,
                            ).dp
                        }

                        CourseOverlayCard(
                            course = course,
                            onClick = { onCourseClick(course) },
                            // 浮起开 → 长按只负责把卡提起来; 关 → 原编辑器快捷路径不变。
                            onLongClick = if (floatAware || onCourseLongClick != null)
                                ( { liftCourse(course) } )
                            else null,
                            // A 契约: 浮动态整卡交互 = 一次按下「长按浮起 → 不松手即拖」,
                            // 取代 combinedClickable(官方长按 consumeUntilUp 后同指拖不起来);
                            // 拖拽/落点复用浮卡同款 dragging 标记与 commitFloatDrop 判定。
                            liftGesture = if (floatAware) Modifier.continuousFloatLift(
                                lifted = { floatState.active },
                                onTap = { onCourseClick(course) },
                                onLift = { liftCourse(course) },
                                onDragStart = {
                                    floatState.dragging = true
                                    // 边缘判据锚: 拖拽起点中心 + 主体左缘/滚动量快照(翻页换宿主对齐用)。
                                    floatState.dragOriginCx = floatState.x + floatState.width / 2f
                                    floatState.anchorRootLeftPx = floatRootLeftPx
                                    floatState.anchorScrollDp = with(headerDensity) { scrollState.value.toDp() }
                                    floatState.edgeArmed = true // 否决弹回后再拖 = 新一次拖拽, 重新武装
                                },
                                onDrag = { amount ->
                                    with(headerDensity) {
                                        floatState.x += amount.x.toDp()
                                        floatState.y += amount.y.toDp()
                                    }
                                },
                                onDrop = { commitFloatDrop() },
                            ) else null,
                            modifier = Modifier
                                .offset(x = cardX, y = cardY)
                                .width(colW)
                                .height(cardH),
                            isGrey = course.day in greyDays,
                            scale = scale,
                            cornerRatio = cornerRatio,
                            // 置灰范围 (用户裁决 2026-10-27): 仅拖拽期间、仅被拖起那一格的原占位卡 —
                            // 同星期且同起止节次的身份判等 + 只在原起拖页(currentWeek==originWeek)置灰。
                            // 跨周翻页后原页仍组合(排他宿主换到目标页渲染浮层), 原占位格继续置灰;
                            // 目标页那张「同 id/同日/同节次」的周复真实课卡绝不置灰 — 它正是落点, 压暗会误导。
                            dragDimmed = floatState.dragging && currentWeek == floatState.originWeek &&
                                floatState.course?.id == course.id &&
                                course.day == floatState.day && course.startNode == floatState.originStartNode &&
                                course.startNode + course.step.coerceAtLeast(1) - 1 == floatState.originEndNode,
                            groupRows = courses.filter { it.groupId == course.groupId }
                        )
                    }
                    quickAddState.selection?.takeIf { quickAddEnabled }?.let { selected ->
                        val startIndex = slotIndexOf(selected.startNode)
                        val endIndex = slotIndexOf(selected.endNode)
                        val dayIndex = sortedDays.indexOf(selected.day)
                        if (startIndex >= 0 && endIndex >= startIndex && dayIndex >= 0) {
                            val density = LocalDensity.current
                            val maxStandard = timeJson?.let { json ->
                                TimeTableUtils.parseTimeSlotRows(json).filter { it.edgeClass == null }.maxOfOrNull { it.node }
                            } ?: timeSlots.maxOfOrNull { it.nodeEnd } ?: 12
                            val nodes = renderSlots.filter { !it.isPlaceholder }.map { it.nodeStart }.toSet()
                            // Edge periods are single-slot entries in the add form; standard periods may span adjacent nodes.
                            var firstNode = selected.startNode
                            var lastNode = selected.endNode
                            if (selected.startNode in 1..maxStandard) {
                                while (firstNode > 1 && firstNode - 1 in nodes) firstNode--
                                while (lastNode < maxStandard && lastNode + 1 in nodes) lastNode++
                            }
                            var dragPosition by remember { mutableStateOf(0f) }
                            QuickAddCourseCapsule(
                                selection = selected,
                                state = quickAddState,
                                onConfirm = {
                                    quickAddState.dismiss()
                                    onQuickAdd?.invoke(selected.day, selected.startNode, selected.step)
                                },
                                onResizeStart = { top ->
                                    dragPosition = with(density) {
                                        (if (top) yOfRows(startIndex.toFloat())
                                        else yOfRows(endIndex.toFloat()) + rowHeightAt(endIndex) - gapH).toPx()
                                    }
                                },
                                onResize = { top, delta ->
                                    dragPosition += delta
                                    val candidates = if (top) firstNode..selected.endNode else selected.startNode..lastNode
                                    val nearest = candidates.minByOrNull { node ->
                                        val index = slotIndexOf(node)
                                        val boundary = if (top) yOfRows(index.toFloat())
                                            else yOfRows(index.toFloat()) + rowHeightAt(index) - gapH
                                        abs(with(density) { boundary.toPx() } - dragPosition)
                                    }
                                    if (nearest != null) quickAddState.selection = if (top) selected.copy(startNode = nearest)
                                        else selected.copy(endNode = nearest)
                                },
                                modifier = Modifier
                                    .offset(x = timeW + gapW + (colW + gapW) * dayIndex, y = yOfRows(startIndex.toFloat()))
                                    .width(colW)
                                    .height(yOfRows(endIndex.toFloat()) + rowHeightAt(endIndex) - gapH - yOfRows(startIndex.toFloat()))
                            )
                        }
                    }
                }
                    if (navExtra > 0.dp) Spacer(modifier = Modifier.height(navExtra))
                }

                // ---- 浮起的课程卡 + 遮罩 + 小浮框 (滚动 Column 的兄弟节点, 不进模糊纹理) ----
                // 排他宿主: 浮层只渲染在 currentWeek == floatState.week 的那一页 —
                // 边缘翻页把 week bump 到新页的同一帧, 浮层/驻留计时/落点几何整体换宿主,
                // 动画中的旧页不再重复渲染, 也不会双份计时。
                if (floatState.active && currentWeek == floatState.week) {
                    val lifted = floatState.course
                    // API 26-30 无 RenderEffect — 压暗兜底把视觉焦点让给浮卡。
                    val scrimAlpha = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) 0.35f else 0.10f
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .testTag("float-backdrop")
                            .background(colors.scrim.copy(alpha = scrimAlpha))
                    )
                    // 翻页动画期跟手补偿: 「起拖页左缘 − 本宿主左缘」的实测位移反平移 —
                    // pager 滑 300ms 内卡钉在指下不随页飘; 静止两周差为 0, 无副作用。
                    val followShift = Modifier.graphicsLayer {
                        translationX = floatState.anchorRootLeftPx - floatRootLeftPx
                    }
                    if (lifted != null) {
                        val overlayY = floatState.y + headH + gapH - floatScrollDp
                        FloatingOccurrenceCard(
                            state = floatState,
                            scale = scale,
                            cornerRatio = cornerRatio,
                            groupRows = courses.filter { it.groupId == lifted.groupId },
                            onDragStart = {
                                floatState.dragging = true
                                floatState.dragOriginCx = floatState.x + floatState.width / 2f
                                floatState.anchorRootLeftPx = floatRootLeftPx
                                floatState.anchorScrollDp = with(headerDensity) { scrollState.value.toDp() }
                                floatState.edgeArmed = true // 否决弹回后再拖 = 新一次拖拽, 重新武装
                            },
                            onDrag = { amount ->
                                with(headerDensity) {
                                    floatState.x += amount.x.toDp()
                                    floatState.y += amount.y.toDp()
                                }
                            },
                            onDrop = { commitFloatDrop() },
                            onResizeStart = { top -> seedFloatResize(top) },
                            onResize = { top, delta -> applyFloatResize(top, delta) },
                            modifier = followShift
                                .offset(x = floatState.x, y = overlayY)
                                .width(floatState.width)
                                .height(floatState.height),
                        )
                        // 边缘预览条: 拖拽压边期间左右各挂一个候选(显隐由 edgeDir/peekProgress 决定)。
                        if (onEdgeDrag != null && floatMaxWeek > 0 && floatState.dragging) {
                            FloatEdgePeek(
                                dir = -1,
                                progress = if (floatState.edgeDir < 0 || floatState.edgeBlocked < 0) floatState.peekProgress else 0f,
                                targetWeek = (floatState.week - 1).coerceAtLeast(1),
                                blocked = floatState.week <= 1,
                                scale = scale,
                                modifier = Modifier.align(Alignment.CenterStart),
                            )
                            FloatEdgePeek(
                                dir = 1,
                                progress = if (floatState.edgeDir > 0 || floatState.edgeBlocked > 0) floatState.peekProgress else 0f,
                                targetWeek = (floatState.week + 1).coerceAtMost(floatMaxWeek),
                                blocked = floatState.week >= floatMaxWeek,
                                scale = scale,
                                modifier = Modifier.align(Alignment.CenterEnd),
                            )
                        }
                        if (!floatState.dragging) {
                            // 浮框宽于列宽(文案"调整本次|删除本次" ≈ 2×列宽): 容器收列宽,
                            // 内容 wrapContentWidth(unbounded) 量自然宽并居中溢出绘制 —
                            // 否则 Row 被压到列宽, 右半段(删除)宽度塌缩为 0 不可点。
                            Box(
                                modifier = followShift
                                    .offset(
                                        x = floatState.x,
                                        y = (overlayY - d(36f)).coerceAtLeast(0.dp),
                                    )
                                    .width(floatState.width),
                                contentAlignment = Alignment.TopCenter,
                            ) {
                                FloatActionPopup(
                                    state = floatState,
                                    scale = scale,
                                    onEdit = {
                                        floatState.dismiss()
                                        // 浮窗已点「调整本次」→ 优先直达回调; 无则退回长按通道。
                                        if (onOccurrenceEdit != null) onOccurrenceEdit(lifted)
                                        else onCourseLongClick?.invoke(lifted)
                                    },
                                    onDelete = {
                                        floatState.dismiss()
                                        onOccurrenceDelete?.invoke(lifted)
                                    },
                                    modifier = Modifier.wrapContentWidth(unbounded = true),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.verticalResizeGesture(
    baseRowHeightDp: Float,
    currentRowHeightDp: Float,
    contentScale: Float,
    onRowHeightScaleChange: (Float) -> Unit
): Modifier {
    return composed {
        val latestBaseRowHeight by rememberUpdatedState(baseRowHeightDp)
        val latestRowHeight by rememberUpdatedState(currentRowHeightDp)
        val latestScale by rememberUpdatedState(contentScale)
        val latestOnChange by rememberUpdatedState(onRowHeightScaleChange)

        pointerInput(Unit) {
            awaitPointerEventScope {
                var startVerticalSpan: Float? = null
                var startHorizontalSpan = 0f
                var startRowHeightDp = latestRowHeight
                var locked = false

                while (true) {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.size >= 2) {
                        val first = pressed[0].position
                        val second = pressed[1].position
                        val verticalSpan = abs(first.y - second.y)
                        val horizontalSpan = abs(first.x - second.x)
                        if (startVerticalSpan == null) {
                            startVerticalSpan = verticalSpan.coerceAtLeast(1f)
                            startHorizontalSpan = horizontalSpan
                            startRowHeightDp = latestRowHeight
                        }

                        if (!locked && TimetableViewportPolicy.locksVerticalResize(
                                startVerticalSpan = startVerticalSpan!!,
                                currentVerticalSpan = verticalSpan,
                                startHorizontalSpan = startHorizontalSpan,
                                currentHorizontalSpan = horizontalSpan
                            )
                        ) {
                            locked = true
                        }

                        if (locked) {
                            val minRow = TimetableViewportPolicy.MIN_ROW_DP * latestScale.coerceIn(0.7f, 1.3f)
                            val maxRow = TimetableViewportPolicy.MAX_ROW_DP * latestScale.coerceIn(0.7f, 1.3f)
                            val nextRowHeight = TimetableViewportPolicy.rowHeightFromVerticalSpan(
                                startRowHeightDp = startRowHeightDp,
                                startVerticalSpan = startVerticalSpan!!,
                                currentVerticalSpan = verticalSpan,
                                minRowHeightDp = minRow,
                                maxRowHeightDp = maxRow
                            )
                            latestOnChange(
                                nextRowHeight / latestBaseRowHeight.coerceAtLeast(1f)
                            )
                            event.changes.forEach { it.consume() }
                        } else {
                            val verticalDelta = abs(verticalSpan - startVerticalSpan!!)
                            val horizontalDelta = abs(horizontalSpan - startHorizontalSpan)
                            // 双指明确横向展开/合拢时不改变高度，也不让 Pager 抢走这次手势。
                            if (horizontalDelta >= TimetableViewportPolicy.VERTICAL_GESTURE_THRESHOLD_DP &&
                                horizontalDelta > verticalDelta * TimetableViewportPolicy.VERTICAL_DOMINANCE_RATIO
                            ) {
                                event.changes.forEach { it.consume() }
                            }
                        }
                    } else {
                        startVerticalSpan = null
                        locked = false
                    }
                }
            }
        }
    }
}

/** 用户反馈 2026-09-16: 占位行时间文字适配的最小内容区需求 = micro 一行 lineHeight 11dp
 *  + SingleTimeHeadCell 上下 padding 8dp。放不下 → 只显示灰块(文字隐藏), 点击展开。 */
private const val PLACEHOLDER_TEXT_REQUIRED_DP = 19f

@Composable
private fun SingleTimeHeadCell(slot: TimeSlot, scale: Float = 1f, modifier: Modifier = Modifier, visibleWidth: androidx.compose.ui.unit.Dp? = null, cornerRatio: Float = 1f, textFits: Boolean = true, onToggleExpand: (() -> Unit)? = null, sharedFont: PeriodHeaderAdaptiveFont? = null, sharedPlacement: PeriodHeaderPlacement? = null, sharedLegacyAxis: androidx.compose.ui.unit.Dp? = null) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val headerLayout = AppPrefs.getPeriodHeaderLayout(context)
    val headerStyle = AppPrefs.getPeriodHeaderStyle(context)
    val sd = { v: Float -> (v * scale).dp }
    val shape = RoundedCornerShape(sd(8f * cornerRatio.coerceAtMost(1f)))
    // 渲染期占位节次: 更低调的呈现 — 半透明底, 只显示时间不显示节号
    val isPh = slot.isPlaceholder
    // 用户反馈 2026-09-16: 文字放不下的占位行 = 纯灰块(不显示文字), 点击展开/折叠。
    // 只灰置占位卡片本身, 课程卡片照常渲染(几何由课程卡自身比例定位, 不受此影响)。
    val phCollapsed = isPh && !textFits
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = (visibleWidth?.let { Modifier.width(it) } ?: Modifier.fillMaxWidth())
                .fillMaxHeight()
                .clip(shape)
                .background(if (isPh) colors.surfaceContainerLow.copy(alpha = 0.5f) else colors.surfaceContainerLow)
                .then(
                    if (onToggleExpand != null) Modifier.noRippleClickable { onToggleExpand() }
                    else Modifier
                )
                // 与设置页预览逐层相等 (PERIOD_HEADER_CARD_PAD_DP, 用户 2026-09-28 令):
                // 之前 2dp 外 + 3dp 内 = 5dp, 预览只有 3dp → 周视图卡片比预览小一圈。
                .padding(sd(PERIOD_HEADER_CARD_PAD_DP)),
            contentAlignment = Alignment.Center
        ) {
            if (phCollapsed) {
                // 灰块形态: 无文字 — 信息靠点击展开
                Box(modifier = Modifier.fillMaxSize().align(Alignment.Center))
            } else if (isPh) {
                Text(
                    text = slot.timeString,
                    style = SleepyTextStyle.micro().copy(fontSize = (9 * scale).sp, lineHeight = (11 * scale).sp),
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                PeriodHeaderCellContent(
                    slot = slot,
                    layout = headerLayout,
                    style = headerStyle,
                    scale = scale,
                    sharedFont = sharedFont,
                    sharedPlacement = sharedPlacement,
                    sharedLegacyAxis = sharedLegacyAxis,
                )
            }
        }
    }
}

// EmptyGridCell 死组件已删（注释自述弃用, 全库零调用——Cards 网格走 SingleTimeHeadCell + CourseOverlayCard）。

@Composable
internal fun CourseOverlayCard(
    course: CourseEntity,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    isGrey: Boolean = false,
    scale: Float = 1f,
    cornerRatio: Float = 1f,
    groupRows: List<CourseEntity> = listOf(course),
    // 浮动态连续手势(A 契约): 非 null 即整卡交互改走该修饰符(长按浮起→不松手即拖),
    // onClick/onLongClick 不再挂载 — 与 onClick 可空模式同款, 调用方只传一个语义开关。
    liftGesture: Modifier? = null,
    // 边缘翻页特性 (2026-10-27 用户裁决): 被拖起课程的原占位卡在拖拽期间纯降透明度置灰
    // (Material 3 disabled-content 档, SleepyTheme.Alpha.dragDim)。跟手浮卡不传, 恒全亮。
    dragDimmed: Boolean = false,
) {
    val palette = SleepyTheme.palette
    val colors = MaterialTheme.colorScheme
    val context = androidx.compose.ui.platform.LocalContext.current
    // 统一取色入口（决策 D3）— colorless 读取 AppPrefs course_colorless 独立开关
    // issue#22: 同名课程多地点 — 用 groupRows 传同 groupId 全行,支持 AUTO/CUSTOM 模式取色
    // spec §2.3: persistedHue 派生档持久色（CLASSIC/查无 → null → stableHue 老路径零变化）
    val hueMap by CourseHueStore.hueMapFlow.collectAsState()
    val bg = CourseColorUtil.pickCourseColorComposeWithGroupRows(
        row = course,
        groupRows = groupRows,
        isDark = CourseColorUtil.isPaletteDark(palette),
        neutralColor = colors.secondaryContainer,
        colorless = AppPrefs.isCourseColorless(context),
        persistedHue = hueMap[course.groupId]
    )
    // 文字色亮度自适应（决策 D5-13）— 深色自定义课色上切白字，浅色底仍 onSurface
    val fg = CourseColorUtil.textColorOn(bg, CourseColorUtil.isPaletteDark(palette), colors.onSurface)
    val shape = RoundedCornerShape((12 * scale * cornerRatio).dp)
    val sd = { v: Float -> (v * scale).dp }
    // 副信息（教室/教师/无）— 左栏 SingleTimeHeadCell 已有节次+时间，卡片 y 位置本身编码节次，
    // 故卡内不再显示节次/时间，改由 grid_sub_info 设置决定
    val subInfo = AppPrefs.getGridSubInfo(context)
    // issue#26: 网格场景别名 — 网格设置开且别名非空才显示别名, 否则原名
    val name = CourseDisplayUtil.displayName(course, AppPrefs.isGridUseAlias(context))
    val subText = when (subInfo) {
        "room" -> course.room
        "teacher" -> course.teacher
        else -> ""
    }

    // 节假日灰显：色块叠 alpha + 文字应用 strikethrough 样式
    val effectiveBg = if (isGrey) bg.copy(alpha = SleepyTheme.Alpha.inactive) else bg
    val effectiveFg = if (isGrey) fg.copy(alpha = SleepyTheme.Alpha.inactive) else fg
    val holidayStyle = AppPrefs.getHolidayStyle(context)
    val textDecoration = if (isGrey && holidayStyle == "strikethrough") {
        androidx.compose.ui.text.style.TextDecoration.LineThrough
    } else null

    Box(
        modifier = modifier
            .padding(sd(2f))
            // 拖拽置灰 (边缘翻页特性): 原占位卡整卡纯降透明度, 浮卡跟手恒全亮。
            .then(if (dragDimmed) Modifier.graphicsLayer { alpha = SleepyTheme.Alpha.dragDim }.testTag("float-dim") else Modifier)
            .clip(shape)
            .background(effectiveBg)
            .then(liftGesture ?: if (onClick != null) Modifier.noRippleCombinedClickable(onClick, onLongClick) else Modifier)
            .padding(sd(4f))
    ) {
        if (subText.isBlank()) {
            // 无副信息: 课程名整体居中(原行为)
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (10 * scale).sp,
                    lineHeight = (13 * scale).sp,
                    textDecoration = textDecoration
                ),
                color = effectiveFg,
                textAlign = TextAlign.Center,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            // 有副信息: 课程名在剩余空间内居中, 副信息贴卡底 — 主文字不再紧贴副文字
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 用 weight(1f) 占位让课程名在上半区居中, 避免正正好好贴住副文字
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = (10 * scale).sp,
                            lineHeight = (13 * scale).sp,
                            textDecoration = textDecoration
                        ),
                        color = effectiveFg,
                        textAlign = TextAlign.Center,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = subText,
                    style = SleepyTextStyle.micro().copy(
                        fontSize = (9 * scale).sp,
                        lineHeight = (11 * scale).sp,
                        textDecoration = textDecoration
                    ),
                    color = effectiveFg.copy(alpha = SleepyTheme.Alpha.highContent),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun DayHeadCell(day: Int, isToday: Boolean, isGrey: Boolean = false, courseCount: Int, dateStr: String? = null, dayLabel: String = DateUtils.localizedDay(day, androidx.compose.ui.platform.LocalContext.current), modifier: Modifier = Modifier, scale: Float = 1f, cornerRatio: Float = 1f) {
    val colors = MaterialTheme.colorScheme
    val sd = { v: Float -> (v * scale).dp }
    val bg = if (isToday) colors.primaryContainer else colors.surface
    val fg = if (isGrey) colors.onSurfaceVariant.copy(alpha = SleepyTheme.Alpha.inactive) else if (isToday) colors.onPrimaryContainer else colors.onSurface
    val subFg = if (isGrey) colors.onSurfaceVariant.copy(alpha = SleepyTheme.Alpha.inactive) else if (isToday) colors.onPrimaryContainer.copy(alpha = SleepyTheme.Alpha.highContent) else colors.onSurfaceVariant

    Box(
        modifier = modifier
            .height(if (dateStr != null) sd(56f) else sd(52f))
            .clip(RoundedCornerShape((10 * scale * cornerRatio.coerceAtMost(1f)).dp))
            .background(bg)
            .padding(vertical = sd(if (dateStr != null) 4f else 3f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(sd(1f))
        ) {
            Text(
                text = dayLabel,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (14 * scale).sp,
                    lineHeight = (20 * scale).sp
                ),
                color = fg,
                maxLines = 1
            )
            if (dateStr != null) {
                Text(
                    text = dateStr,
                    style = SleepyTextStyle.micro().copy(fontSize = (10 * scale).sp, lineHeight = (11 * scale).sp),
                    color = subFg,
                    maxLines = 1
                )
            } else {
                Text(
                    text = if (courseCount == 0) stringResource(R.string.no_course) else stringResource(R.string.course_count_format, courseCount),
                    style = SleepyTextStyle.micro().copy(fontSize = (9 * scale).sp, lineHeight = (11 * scale).sp),
                    color = subFg,
                    maxLines = 1
                )
            }
        }
    }
}


// TimeHeadCell / SpannedTimeHeadCell 死组件已删（SpannedTimeHeadCell 注释自述弃用,
// TimeHeadCell 被 SingleTimeHeadCell 取代, 两者全库零调用; CELL_H 常量随之删除）。

// pickCourseColor / isPaletteDark / hslToColor 三函数已收敛至 util/CourseColorUtil.kt（决策 D3 单一事实来源）。
// 原注释 S/L 值写错（0.48/0.88、0.35/0.26），实际为亮色 S=0.55 L=0.82 / 暗色 S=0.40 L=0.28，正确值见 CourseColorUtil 常量。

// =====================================================================================
// 7days full 视图 — switchable.html #fullView
// =====================================================================================

@Composable
fun FullWeekView(
    courses: List<CourseEntity>,
    visibleDays: Set<Int> = (1..7).toSet(),
    displayMode: String = "node",
    timeJson: String = "",
    today: Int = DateUtils.todayDayOfWeek(),
    todayDate: java.time.LocalDate = java.time.LocalDate.now(),
    startDate: String = "",
    currentWeek: Int = 1,
    onCourseClick: (CourseEntity) -> Unit,
    onCourseLongClick: ((CourseEntity) -> Unit)? = null,
    modifier: Modifier = Modifier,
    greyDays: Set<Int> = emptySet(),
    showDate: Boolean = false
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // 设置页改 weekScale/cornerRatio/twoColumn/hideEmptyDays/别名后强制 recompose
    var prefVersion by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        AppPrefs.changeBus.filter {
            it == AppPrefs.KEY_WEEK_SCALE || it == AppPrefs.KEY_GRID_CORNER_RATIO ||
            it == AppPrefs.KEY_WEEK_TWO_COLUMN || it == AppPrefs.KEY_WEEK_TWO_COLUMN_MODE ||
            it == AppPrefs.KEY_WEEK_HIDE_EMPTY_DAYS || it == AppPrefs.KEY_WEEK_USE_ALIAS
        }.collect { prefVersion++ }
    }
    val scale = AppPrefs.getWeekScale(context)
    val cornerRatio = AppPrefs.getGridCornerRatio(context)
    val twoColumn = AppPrefs.isWeekTwoColumn(context)
    val twoColumnMode = AppPrefs.getWeekTwoColumnMode(context)
    val hideEmptyDays = AppPrefs.isWeekHideEmptyDays(context)
    // issue#26: 周视图场景别名 — 周视图设置开才用别名; 传给所有子渲染单元
    val useAlias = AppPrefs.isWeekUseAlias(context)
    val byDay = courses.groupBy { it.day }
    val navExtra = com.lingion.sleepy.ui.component.LocalNavExtraBottomPadding.current

    /** 每天的 M月d日 日期字符串 — showDate 开且 startDate 合法时显示 (用户要求: 周一·10月13日) */
    val dayDate: Map<Int, String?> = remember(showDate, startDate, currentWeek, visibleDays) {
        if (!showDate || startDate.isBlank()) return@remember emptyMap()
        val map = mutableMapOf<Int, String?>()
        for (day in visibleDays.filter { it in 1..7 }) {
            map[day] = runCatching {
                val d = DateUtils.dateOfWeek(startDate, currentWeek, day)
                if (d != null) DateUtils.shortDateChinese(d) else null
            }.getOrNull()
        }
        map
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        WeekStrip(
            byDay = byDay,
            onCourseClick = onCourseClick,
            onCourseLongClick = onCourseLongClick,
            visibleDays = visibleDays,
            today = today,
            todayDate = todayDate,
            startDate = startDate,
            currentWeek = currentWeek,
            greyDays = greyDays,
            useAlias = useAlias,
            scale = scale,
            cornerRatio = cornerRatio,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
        DetailPanel(
            byDay = byDay,
            visibleDays = visibleDays,
            displayMode = displayMode,
            timeJson = timeJson,
            today = today,
            todayDate = todayDate,
            startDate = startDate,
            currentWeek = currentWeek,
            onCourseClick = onCourseClick, onCourseLongClick = onCourseLongClick,
            greyDays = greyDays,
            scale = scale,
            cornerRatio = cornerRatio,
            twoColumn = twoColumn,
            twoColumnMode = twoColumnMode,
            hideEmptyDays = hideEmptyDays,
            dayDate = dayDate
        )
        // Dock 悬浮底栏: 滚动尾部多留 Dock 总高(同网格视图)
        if (navExtra > 0.dp) Spacer(modifier = Modifier.height(navExtra))
    }
}

@Composable
private fun WeekStrip(
    byDay: Map<Int, List<CourseEntity>>,
    onCourseClick: (CourseEntity) -> Unit,
    onCourseLongClick: ((CourseEntity) -> Unit)?,
    today: Int,
    todayDate: java.time.LocalDate,
    startDate: String,
    currentWeek: Int,
    visibleDays: Set<Int>,
    greyDays: Set<Int> = emptySet(),
    useAlias: Boolean = false,
    scale: Float = 1f,
    cornerRatio: Float = 1f,
    modifier: Modifier = Modifier
) {
    val sd = { v: Float -> (v * scale).dp }

    // 等高: 每个胶囊用 layout 以"宽松高度"测一次自然高度 (px), 记入 per-day 表;
    // Row 内所有胶囊摆放高度取该表最大值, 短的被拉到最高者。自然高度只由内容决定,
    // 不随拉伸改变 → 无正反馈, 杜绝 #191 的棘轮膨胀撑屏。
    // 严禁用 Row(IntrinsicSize): DaySummaryCell 内含 BoxWithConstraints(SubcomposeLayout),
    // 不支持 intrinsic 询问, 一进周视图首帧即抛 IllegalStateException 崩溃。
    val naturalHeights = remember(visibleDays) { mutableStateMapOf<Int, Int>() }
    val maxH = naturalHeights.values.maxOrNull() ?: 0

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(sd(6f))
    ) {
        for (day in visibleDays.sorted()) {
            val dayCourses = byDay[day].orEmpty()
            val isToday = startDate.isNotBlank() && runCatching {
                DateUtils.isDateToday(DateUtils.dateOfWeek(startDate, currentWeek, day), todayDate)
            }.getOrDefault(false)
            DaySummaryCell(
                day = day,
                onCourseClick = onCourseClick,
                onCourseLongClick = onCourseLongClick,
                courses = dayCourses,
                isToday = isToday,
                isGrey = day in greyDays,
                useAlias = useAlias,
                scale = scale,
                cornerRatio = cornerRatio,
                modifier = Modifier
                    .weight(1f)
                    .layout { measurable, constraints ->
                        // 第一遍: 宽松 maxHeight 测自然高度 (px), 该值只由内容决定, 上报它不会棘轮
                        val natural = measurable
                            .measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                            .height
                        if (naturalHeights[day] != natural) {
                            naturalHeights[day] = natural
                        }
                        // 第二遍: 以 max(自然, 全局最高) 强制等高, 背景随 Column 填满
                        val boxH = natural.coerceAtLeast(maxH)
                        val stretched = measurable
                            .measure(constraints.copy(minHeight = boxH, maxHeight = boxH))
                        layout(stretched.width, boxH) {
                            stretched.placeRelative(0, 0)
                        }
                    }
            )
        }
    }
}

@Composable
private fun DaySummaryCell(
    day: Int,
    onCourseClick: (CourseEntity) -> Unit,
    onCourseLongClick: ((CourseEntity) -> Unit)?,
    courses: List<CourseEntity>,
    isToday: Boolean,
    isGrey: Boolean = false,
    useAlias: Boolean = false,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    cornerRatio: Float = 1f
) {
    val colors = MaterialTheme.colorScheme
    val context = androidx.compose.ui.platform.LocalContext.current
    val sd = { v: Float -> (v * scale).dp }
    val bg = if (isToday) colors.primaryContainer else colors.surfaceContainer
    val fg = if (isGrey) colors.onSurfaceVariant.copy(alpha = SleepyTheme.Alpha.inactive) else if (isToday) colors.onPrimaryContainer else colors.onSurface
    // Chip: solid surfaceVariant with full alpha for dark mode readability
    val chipBg = colors.surfaceVariant
    val chipFg = colors.onSurfaceVariant.copy(alpha = if (isGrey) SleepyTheme.Alpha.inactive else 1f)

    Column(
        modifier = modifier
            .heightIn(min = (132 * scale).dp)
            .clip(RoundedCornerShape((12 * scale * cornerRatio).dp))
            .background(bg)
            .padding(horizontal = sd(6f), vertical = sd(8f))
    ) {
        // 日期
        Text(
            text = DateUtils.localizedDay(day, context),
            style = SleepyTextStyle.dayLabel().copy(fontSize = (13 * scale).sp, lineHeight = (18 * scale).sp),
            color = fg,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(sd(6f)))

        // Chip: 课程数 — 完整文字在列宽内换行就退化为纯数字，胶囊自动缩到数字宽度
        if (courses.isEmpty()) {
            Spacer(modifier = Modifier.height(sd(14f)))
        } else {
            val fullText = stringResource(R.string.course_count_format, courses.size)
            val numberText = courses.size.toString()
            val chipStyle = SleepyTextStyle.smallMeta().copy(fontWeight = FontWeight.SemiBold, fontSize = (10 * scale).sp, lineHeight = (14 * scale).sp)
            val textMeasurer = rememberTextMeasurer()
            BoxWithConstraints(modifier = Modifier.align(Alignment.CenterHorizontally)) {
                val chipPaddingPx = with(LocalDensity.current) { sd(14f).toPx() }.toInt()
                val availablePx = with(LocalDensity.current) { maxWidth.toPx() }.toInt()
                val layoutResult = textMeasurer.measure(
                    text = fullText,
                    style = chipStyle,
                    constraints = Constraints(maxWidth = (availablePx - chipPaddingPx).coerceAtLeast(0))
                )
                val showNumberOnly = layoutResult.lineCount > 1
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(chipBg)
                        .padding(horizontal = sd(7f), vertical = sd(2f))
                ) {
                    Text(
                        text = if (showNumberOnly) numberText else fullText,
                        style = chipStyle,
                        color = chipFg,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(sd(4f)))

        // Mini-list: 前 5 门课名（改掉 take(3)，空间够就全显示）
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(sd(2f))
        ) {
            courses.take(5).forEach { c ->
                Text(
                    modifier = Modifier.noRippleCombinedClickable(
                        onClick = { onCourseClick(c) },
                        onLongClick = onCourseLongClick?.let { callback -> { callback(c) } }
                    ),
                    text = com.lingion.sleepy.util.CourseDisplayUtil.displayName(c, useAlias),
                    style = SleepyTextStyle.micro().copy(fontSize = (9 * scale).sp, lineHeight = (11 * scale).sp),
                    color = if (isToday) colors.onPrimaryContainer.copy(alpha = SleepyTheme.Alpha.highContent) else colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DetailPanel(
    byDay: Map<Int, List<CourseEntity>>,
    today: Int,
    todayDate: java.time.LocalDate,
    startDate: String,
    currentWeek: Int,
    visibleDays: Set<Int>,
    displayMode: String,
    timeJson: String,
    onCourseClick: (CourseEntity) -> Unit,
    onCourseLongClick: ((CourseEntity) -> Unit)? = null,
    greyDays: Set<Int> = emptySet(),
    scale: Float = 1f,
    cornerRatio: Float = 1f,
    twoColumn: Boolean = false,
    twoColumnMode: String = "days",
    hideEmptyDays: Boolean = false,
    dayDate: Map<Int, String?> = emptyMap()
) {
    val colors = MaterialTheme.colorScheme
    val sd = { v: Float -> (v * scale).dp }
    // issue#8 隐藏无课日 — 单栏/两栏都生效
    val sortedDays = visibleDays.sorted().let {
        if (hideEmptyDays) it.filter { d -> byDay[d].orEmpty().isNotEmpty() } else it
    }

    // issue#8 周视图两栏, 省纵向滚动; 分栏标准由设置选择:
    //   days    = 按天对半分 — 前半周左/后半周右, 天数固定
    //   balance = 按课程数动态平衡 — 逐天放进当天卡片(约)更矮的栏, 两栏高度接近
    // 隐藏无课日后按剩余天数平分: 6 天=3+3, 4 天=2+2; 奇数天多的一天落左栏
    if (twoColumn && sortedDays.size >= 2) {
        val split: Pair<List<Int>, List<Int>> = if (twoColumnMode == "balance") {
            // 贪心: 按天序遍历, 每天记权重 = 课程数(权重按 DetailDayCard 高度近似, 空天也有卡头所以记 1)
            var l = 0; var r = 0
            val left = mutableListOf<Int>(); val right = mutableListOf<Int>()
            for (day in sortedDays) {
                val w = (byDay[day].orEmpty().size).coerceAtLeast(1)
                if (l <= r) { left.add(day); l += w } else { right.add(day); r += w }
            }
            left to right
        } else {
            val splitIdx = (sortedDays.size + 1) / 2
            sortedDays.subList(0, splitIdx) to sortedDays.subList(splitIdx, sortedDays.size)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(sd(12f)),
            horizontalArrangement = Arrangement.spacedBy(sd(10f)),
            verticalAlignment = Alignment.Top
        ) {
            DayColumn(
                days = split.first,
                byDay = byDay, today = today, todayDate = todayDate, startDate = startDate,
                currentWeek = currentWeek, displayMode = displayMode, timeJson = timeJson,
                onCourseClick = onCourseClick, onCourseLongClick = onCourseLongClick, greyDays = greyDays,
                scale = scale, cornerRatio = cornerRatio,
                dayDate = dayDate,
                modifier = Modifier.weight(1f)
            )
            if (split.second.isNotEmpty()) {
                DayColumn(
                    days = split.second,
                    byDay = byDay, today = today, todayDate = todayDate, startDate = startDate,
                    currentWeek = currentWeek, displayMode = displayMode, timeJson = timeJson,
                    onCourseClick = onCourseClick, onCourseLongClick = onCourseLongClick, greyDays = greyDays,
                    scale = scale, cornerRatio = cornerRatio,
                    dayDate = dayDate,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    } else {
        // 单栏(或两栏下过滤后不足 2 天) — 显示剩余星期(全空周时=全部所选星期, 不吃掉无课日)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape((16 * scale * cornerRatio).dp))
                .background(colors.surfaceContainerHigh)
                .padding(sd(12f)),
            verticalArrangement = Arrangement.spacedBy(sd(10f))
        ) {
            for (day in sortedDays) {
                val dayCourses = byDay[day].orEmpty().sortedBy { it.startNode }
                DetailDayCard(
                    day = day,
                    courses = dayCourses,
                    isToday = startDate.isNotBlank() && runCatching {
                        DateUtils.isDateToday(DateUtils.dateOfWeek(startDate, currentWeek, day), todayDate)
                    }.getOrDefault(false),
                    displayMode = displayMode,
                    timeJson = timeJson,
                    onCourseClick = onCourseClick, onCourseLongClick = onCourseLongClick,
                    isGrey = day in greyDays,
                    scale = scale,
                    cornerRatio = cornerRatio,
                    dateStr = dayDate[day]
                )
            }
        }
    }
}

/** 两栏模式的单侧栏 — 半周的天卡片竖排在一个独立面板里 */
@Composable
private fun DayColumn(
    days: List<Int>,
    byDay: Map<Int, List<CourseEntity>>,
    today: Int,
    todayDate: java.time.LocalDate,
    startDate: String,
    currentWeek: Int,
    displayMode: String,
    timeJson: String,
    onCourseClick: (CourseEntity) -> Unit,
    onCourseLongClick: ((CourseEntity) -> Unit)? = null,
    greyDays: Set<Int>,
    scale: Float,
    cornerRatio: Float,
    dayDate: Map<Int, String?> = emptyMap(),
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val sd = { v: Float -> (v * scale).dp }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape((16 * scale * cornerRatio).dp))
            .background(colors.surfaceContainerHigh)
            .padding(sd(10f)),
        verticalArrangement = Arrangement.spacedBy(sd(10f))
    ) {
        for (day in days) {
            val dayCourses = byDay[day].orEmpty().sortedBy { it.startNode }
            DetailDayCard(
                day = day,
                courses = dayCourses,
                isToday = startDate.isNotBlank() && runCatching {
                    DateUtils.isDateToday(DateUtils.dateOfWeek(startDate, currentWeek, day), todayDate)
                }.getOrDefault(false),
                displayMode = displayMode,
                timeJson = timeJson,
                onCourseClick = onCourseClick, onCourseLongClick = onCourseLongClick,
                isGrey = day in greyDays,
                scale = scale,
                cornerRatio = cornerRatio,
                dateStr = dayDate[day]
            )
        }
    }
}

@Composable
private fun DetailDayCard(
    day: Int,
    courses: List<CourseEntity>,
    isToday: Boolean,
    isGrey: Boolean = false,
    displayMode: String = "node",
    timeJson: String = "",
    onCourseClick: (CourseEntity) -> Unit,
    onCourseLongClick: ((CourseEntity) -> Unit)? = null,
    scale: Float = 1f,
    cornerRatio: Float = 1f,
    dateStr: String? = null
) {
    val colors = MaterialTheme.colorScheme
    val context = androidx.compose.ui.platform.LocalContext.current
    val sd = { v: Float -> (v * scale).dp }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape((12 * scale * cornerRatio).dp))
            .background(if (courses.isEmpty()) colors.surfaceContainerLow else colors.surface)
            .padding(sd(10f)),
        verticalArrangement = Arrangement.spacedBy(sd(8f))
    ) {
        // 头部：星期 + 今天标记
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = DateUtils.localizedDay(day, context) + (if (isToday) stringResource(R.string.today_suffix) else "") + (dateStr?.let { "·$it" } ?: ""),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (isGrey) colors.onSurfaceVariant.copy(alpha = SleepyTheme.Alpha.inactive) else colors.onSurface
            )
        }

        if (courses.isEmpty()) {
            Text(
                text = DateUtils.localizedDay(day, context) + stringResource(R.string.no_course_today) + (dateStr?.let { "·$it" } ?: ""),
                style = SleepyTextStyle.smallMeta().copy(fontSize = (12 * scale).sp, lineHeight = (16 * scale).sp),
                color = if (isGrey) colors.onSurfaceVariant.copy(alpha = SleepyTheme.Alpha.inactive) else colors.onSurfaceVariant
            )
        } else {
            // v7.10.6 行分组下沉引擎: mergeOverlapping 区域是划分(每课恰属一区域),
            // 冲突区域整区域一行(横向 laneCount 栏,同栏多门课纵向堆叠),
            // 无冲突课一行一门全宽。结构上保证不丢课、不重复(用户 2026-09-02 报障修复)。
            // 用户报障 2026-09-10: 分组走时间域 — FullWeekView 的 courses 输入虽已
            // normalizeNode, ownTime 课的真实分钟重叠判定仍需 timeJson 才与网格一致。
            val rows = remember(courses, timeJson) {
                ConflictLayoutEngine.weekLaneRows(courses, timeJson.ifBlank { null })
            }

            Column(verticalArrangement = Arrangement.spacedBy(sd(7f))) {
                rows.forEach { row ->
                    if (row.laneCount == 1) {
                        LessonRow(
                            course = row.courses[0], displayMode = displayMode, timeJson = timeJson,
                            onClick = { onCourseClick(row.courses[0]) },
                            onLongClick = onCourseLongClick?.let { callback -> { callback(row.courses[0]) } }, isGrey = isGrey,
                            scale = scale, cornerRatio = cornerRatio,
                            groupRows = courses.filter { it.groupId == row.courses[0].groupId }
                        )
                    } else {
                        // 冲突行: 按 lane 并排,每列 weight 均分,同栏课程纵向堆叠
                        // (栏内课互不重叠——chainGroups 独立集保证,堆叠即正确时序)。
                        // v7.10.4: BoxWithConstraints 拿 lane 实宽 → 字体/间距按比例压缩
                        // (两栏模式下 lane 半宽,不缩则字全挤在一起——用户 2026-09-02)
                        // v7.10.10: 栏间画浅细竖分隔线(用户 2026-09-02)
                        val laneCount = row.laneCount
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val laneGap = sd(6f)
                            val laneW = (maxWidth - laneGap * (laneCount - 1)) / laneCount
                            val laneScale = weekLaneFontScale(laneW)
                            val hideSide = weekLaneHideSideLabel(laneW)
                            Row(
                                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                                horizontalArrangement = Arrangement.spacedBy(laneGap)
                            ) {
                                repeat(laneCount) { li ->
                                    if (li > 0) {
                                        // 栏间浅细竖线: 0.5dp 宽, onSurface 0.3 透明度, 高度随行
                                        // (Row IntrinsicSize.Min → fillMaxHeight 有界, 随最高栏撑满)
                                        Box(
                                            modifier = Modifier
                                                .width(0.5.dp)
                                                .fillMaxHeight()
                                                .background(
                                                    colors.onSurface.copy(alpha = SleepyTheme.Alpha.hairline)
                                                )
                                        )
                                    }
                                    val laneCourses = row.courses.filter { row.laneOf[it.id] == li }
                                    Box(modifier = Modifier.weight(1f)) {
                                        Column(verticalArrangement = Arrangement.spacedBy(sd(5f))) {
                                            laneCourses.forEach { laneCourse ->
                                                LessonRow(
                                                    course = laneCourse, displayMode = displayMode,
                                                    timeJson = timeJson,
                                                    onClick = { onCourseClick(laneCourse) },
                            onLongClick = onCourseLongClick?.let { callback -> { callback(laneCourse) } },
                                                    isGrey = isGrey, scale = scale, cornerRatio = cornerRatio,
                                                    laneScale = laneScale, hideSideLabel = hideSide,
                                                    groupRows = courses.filter { it.groupId == laneCourse.groupId }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =====================================================================================
// v7.10.4 冲突栏字号/间距压缩 — 用户 2026-09-02: 两栏模式下分栏字全挤到一起,
// 需按 lane 实宽调画面比例。
// =====================================================================================

/** lane 宽压缩基准: 单栏半宽量级(用户实测"比较好"的现状),低于它开始线性缩。 */
internal val WEEK_LANE_SCALE_BASE = 150f

/** 压缩下限 — 再窄也不无限缩,可读性由隐藏侧栏标签/副信息兜底。 */
internal const val WEEK_LANE_SCALE_FLOOR = 0.6f

/**
 * 冲突栏内 LessonRow 的缩放比(纯函数可测):
 *   laneW ≥ 150dp → 1.0(保现状);以下线性压缩;0.6 封底。
 */
internal fun weekLaneFontScale(laneW: Dp): Float =
    if (laneW >= WEEK_LANE_SCALE_BASE.dp) 1f
    else (laneW.value / WEEK_LANE_SCALE_BASE).coerceAtLeast(WEEK_LANE_SCALE_FLOOR)

/** 极窄 lane 判定: 侧栏节次/时间标签(42dp+8dp 间距)挤占正文 → 隐藏它。 */
internal fun weekLaneHideSideLabel(laneW: Dp): Boolean = laneW < 110.dp

@Composable
private fun LessonRow(
    course: CourseEntity,
    displayMode: String,
    timeJson: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    isGrey: Boolean = false,
    scale: Float = 1f,
    cornerRatio: Float = 1f,
    laneScale: Float = 1f,
    hideSideLabel: Boolean = false,
    groupRows: List<CourseEntity> = listOf(course)
) {
    val colors = MaterialTheme.colorScheme
    val palette = SleepyTheme.palette
    val context = androidx.compose.ui.platform.LocalContext.current
    // issue#26: 周视图场景别名 — 与 colorless 同模式, 叶子直接读场景开关
    val name = CourseDisplayUtil.displayName(course, AppPrefs.isWeekUseAlias(context))
    // 双层缩放: scale=全局周视图缩放(issue#8), laneScale=v7.10.4 冲突栏按实宽压缩
    val effScale = scale * laneScale
    val sd = { v: Float -> (v * effScale).dp }
    // 统一取色入口（决策 D3）— colorless 读取 AppPrefs course_colorless 独立开关
    // issue#22: 同名课程多地点 — 用 groupRows 传同 groupId 全行,支持 AUTO/CUSTOM 模式取色
    // spec §2.3: persistedHue 派生档持久色（CLASSIC/查无 → null → stableHue 老路径零变化）
    val hueMap by CourseHueStore.hueMapFlow.collectAsState()
    val bg = CourseColorUtil.pickCourseColorComposeWithGroupRows(
        row = course,
        groupRows = groupRows,
        isDark = CourseColorUtil.isPaletteDark(palette),
        neutralColor = colors.secondaryContainer,
        colorless = AppPrefs.isCourseColorless(context),
        persistedHue = hueMap[course.groupId]
    )
    // 文字色亮度自适应（决策 D5-13）— 深色自定义课色上切白字，浅色底仍 onSurface
    val fg = CourseColorUtil.textColorOn(bg, CourseColorUtil.isPaletteDark(palette), colors.onSurface)
    val effectiveBg = if (isGrey) bg.copy(alpha = SleepyTheme.Alpha.inactive) else bg
    val effectiveFg = if (isGrey) fg.copy(alpha = SleepyTheme.Alpha.inactive) else fg
    val holidayStyle = AppPrefs.getHolidayStyle(context)
    val textDecoration = if (isGrey && holidayStyle == "strikethrough") androidx.compose.ui.text.style.TextDecoration.LineThrough else null

    // time 模式：「08:00-\n08:45」——时间段在连字符后折行，行距收紧读成一个整体
    val timeParts = if (displayMode == "time" && timeJson.isNotBlank()) {
        TimeTableUtils.courseTimeParts(course.startNode, course.step, timeJson, course.ownTime, course.startTime, course.endTime)
    } else null
    val nodeLabel = course.shortNodeString(context)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(sd(12f * cornerRatio)))
            .background(effectiveBg)
            .noRippleCombinedClickable(onClick, onLongClick)
            .padding(sd(9f)),
        horizontalArrangement = Arrangement.spacedBy(sd(8f))
    ) {
        val sideStyle = SleepyTextStyle.smallMeta().copy(
            fontSize = (12 * effScale).sp,
            lineHeight = (16 * effScale).sp,
            fontWeight = FontWeight.SemiBold,
            textDecoration = textDecoration
        )
        // 极窄 lane: 侧栏标签(42dp+8dp)挤占正文 → 隐藏,节次信息由卡片纵向位置表达
        if (!hideSideLabel) {
            if (timeParts != null) {
                Text(
                    text = "${timeParts.first}-\n${timeParts.second}",
                    style = sideStyle,
                    color = effectiveFg,
                    modifier = Modifier.width(sd(42f))
                )
            } else {
                Text(
                    text = nodeLabel,
                    style = sideStyle,
                    color = effectiveFg,
                    modifier = Modifier.width(sd(42f)),
                    maxLines = 1
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (12 * effScale).sp,
                    lineHeight = (16 * effScale).sp,
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Top,
                        trim = LineHeightStyle.Trim.FirstLineTop
                    ),
                    textDecoration = textDecoration
                ),
                color = effectiveFg,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val meta = buildString {
                if (course.teacher.isNotBlank()) append(course.teacher)
                if (course.room.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(course.room)
                }
            }
            // 极窄 lane 下副信息(教师/教室)也让位给课名
            if (meta.isNotEmpty() && !hideSideLabel) {
                Text(
                    text = meta,
                    style = SleepyTextStyle.smallMeta().copy(
                        fontSize = (11 * effScale).sp,
                        lineHeight = (14 * effScale).sp,
                        textDecoration = textDecoration
                    ),
                    color = effectiveFg.copy(alpha = SleepyTheme.Alpha.highContent),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// =====================================================================================
// 公共小组件
// =====================================================================================

@Composable
fun SectionHead(title: String, action: String? = null) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = SleepyTextStyle.sectionHead(),
            color = colors.onSurface
        )
        if (action != null) {
            Text(
                text = action,
                style = SleepyTextStyle.smallMeta().copy(
                    fontWeight = FontWeight.Medium,
                    color = colors.primary
                )
            )
        }
    }
}


// sp 已被上面 textStyle 直接用 inline

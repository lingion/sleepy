package com.lingion.sleepy.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.RangeSliderState
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * MD3 双头滑杆 (RangeSlider) 的整数区间封装 — 编辑"第2–4节""第1–16周"这类
 * 首尾都是整数的范围, 一头拖起点、一头拖终点, steps 把轨道离散成整数档位,
 * 物理上拖不出小数/交叉/越界。键盘方向键微调由 RangeSlider 内置。
 *
 * 调用方仍按各自的存储语义接 (start, end) 区间语言:
 * 节次卡写回时自行换算 step = end - start + 1, 周次直接写 startWeek/endWeek。
 * 数据实体 (CourseEntity startNode/step) 与所有消费方零改动。
 *
 * 纵向手势 (需求 2026-10-27 三次修正定形, 五次修正补多指): 手指一接触滑杆, 到
 * 该区域内所有手指全部离开为止, 整页 (外层 LazyColumn 与一切滚动容器) 必须纹丝
 * 不动 — 与手指方向、幅度、第几根手指无关; 拇指只跟手指横向分量走, 纯竖向拖不
 * 改值; 路径 (1,1)→(1,-1)→(0,-1) 与直接 (1,1)→(0,-1) 等效。
 * sliderFreezePageScroll 双协程两 pass 达成 (见其 KDoc): Main pass 无条件消费
 * 每帧 = 绝对冻结; Initial pass 拦纵向占优帧 = 滑块 Y 免疫。旧版"逐帧主轴裁决、
 * 横向帧放行给页面 + 拖动接管后静默"仍让斜拖帧的 Y 分量漏给外层列表 → 页面被
 * 带动, 与"摸到即全局冻住"不符, 已废弃。
 *
 * 原位置幽灵占位 (需求 2026-10-28): initialStart/initialEnd 传"打开页面时的初值
 * 区间"时, 当前显示区间一旦拖离初值 (任一端不同), 轨道上叠一条浅色幽灵 — 初值
 * 区间的轨道段 + 两端点细条, 提示"原来的范围是什么"; 拖回初值区间即重合不画;
 * 初值传 null (新建课程/新时段) 永不画。幽灵是纯装饰 Canvas, 不吃手势、不进语义。
 * 保存后离页重进即初值刷新, 幽灵自然消失, 无需额外基线管理。
 */
@Composable
fun IntRangeSlider(
    summary: String,
    minValue: Int,
    maxValue: Int,
    startValue: Int,
    endValue: Int,
    onValueChange: (start: Int, end: Int) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    initialStart: Int? = null,
    initialEnd: Int? = null,
) {
    val colors = MaterialTheme.colorScheme
    // 只有一个可选档位 (maxStd=1 的作息表) — 滑杆两端点重合无意义, 退化成静态读数;
    // 退化域只是普通文本, 拖它应当照常滚页面, 不加纵向拦截
    val hasTwoStops = maxValue >= minValue + 1
    val displayStart = startValue.coerceIn(minValue, maxValue)
    val displayEnd = endValue.coerceIn(displayStart, maxValue)
    // 幽灵坐标与可见性共用同一对夹取后的初值读数 (夹取规则与 displayStart/displayEnd
    // 完全同构, 见 sliderGhostVisible 的 KDoc) — 脏数据不会画出与摘要矛盾的幽灵
    val ghostVisible = sliderGhostVisible(
        currentStart = displayStart,
        currentEnd = displayEnd,
        initialStart = initialStart,
        initialEnd = initialEnd,
        minValue = minValue,
        maxValue = maxValue,
    )
    val ghostRange: IntRange? = if (ghostVisible && initialStart != null && initialEnd != null) {
        val os = initialStart.coerceIn(minValue, maxValue)
        os..initialEnd.coerceIn(os, maxValue)
    } else {
        null
    }
    // 终审 I-1: 退场帧保留的幽灵快照 — ghostRange 在拖回初值的同一次重组翻 null,
    // 与 visible=false 同帧; 内容若直读 ghostRange, Canvas 在退场第 0 帧即被拆走,
    // sleepyPopupExit 只剩空壳动画 (弹入真实、退出硬切)。组合期直写保留最后非空
    // 快照供内容判据 (CourseTableView retainedLifted 同款模式); 快照一旦现身不再
    // 复位 null — 无妨, 退场播完 AnimatedVisibility 移除整棵子树, 终态存在性不变。
    var lastGhostRange by remember { mutableStateOf<IntRange?>(null) }
    ghostRange?.let { lastGhostRange = it }
    Column(
        modifier = (if (hasTwoStops) modifier.sliderFreezePageScroll() else modifier)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant
                )
            }
            Text(
                text = summary,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onSurface
            )
        }
        if (hasTwoStops) {
            // 值域版 RangeSlider 重载没有 track 插槽 (且已弃用, ReplaceWith 指向此
            // 状态版), 幽灵只能画进状态版的 track lambda — 与真轨道同盒同坐标。
            // 控制形态照抄 material3 值域重载内部实现 (Slider.kt: 建 state 后每
            // 组合写回 value, 拖动经 onValueChange 冒给调用方再回流)
            val trackRange = minValue.toFloat()..maxValue.toFloat()
            // stops = maxValue-minValue+1, 内部档位 = stops-2 (两端点之外)
            val steps = maxValue - minValue - 1
            val sliderState = remember(steps, trackRange) {
                RangeSliderState(
                    startValue = displayStart.toFloat(),
                    endValue = displayEnd.toFloat(),
                    steps = steps,
                    trackRange = trackRange,
                )
            }
            sliderState.startValue = displayStart.toFloat()
            sliderState.endValue = displayEnd.toFloat()
            RangeSlider(
                state = sliderState,
                onValueChange = { range ->
                    val snapped = snapRangeToInt(range, minValue, maxValue)
                    onValueChange(snapped.first, snapped.second)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = if (label != null) "$label $summary" else summary },
                track = { rangeSliderState ->
                    // contentAlignment=Center: 真轨道 (16dp) 在盒内垂直居中, 幽灵
                    // Canvas 撑满 thumb 全高 (44dp) — 盒高变化不移动真轨道视觉位置
                    // (sliderHeight 本就由 44dp thumb 主导, trackOffsetY 同步抵消)
                    Box(contentAlignment = Alignment.Center) {
                        SliderDefaults.Track(
                            rangeSliderState = rangeSliderState,
                        )
                        // MD3E (Task 6) + 终审 I-1: 幽灵弹入弹出走 popup 家族 (T1 工厂)。
                        // testTag 仍挂 AnimatedVisibility — 存在性 = 终态语义不变
                        // (管线测试锁初始无节点、退场播完移除); 退场期间绘制内容经
                        // lastGhostRange 快照保留, 弹出动画真实播放 (CourseTableView
                        // retainedLifted 同款模式)。不许改 alpha 常驻。
                        // this@Column: Box 作用域无 AnimatedVisibility 扩展,
                        // 外层 Column 版不许隐式接收者 (DSL 闸) — CourseTableView:969 同款修法;
                        // .align 父级数据仍由本 Box 布局消费, 落位不受重载选择影响。
                        this@Column.AnimatedVisibility(
                            visible = ghostRange != null,
                            enter = sleepyPopupEnter(),
                            exit = sleepyPopupExit(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("slider-ghost"),
                        ) {
                            val ghost = lastGhostRange
                            if (ghost != null) {
                                val primary = colors.primary
                                val ghostStart = ghost.first
                                val ghostEnd = ghost.last
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .testTag("slider-ghost-canvas"),
                            ) {
                                // 1:1 复刻真滑杆形态 (material3 SliderTokens 实测):
                                // 真 thumb = HandleWidth 4dp x HandleHeight 44dp 全圆
                                // 胶囊 (HandleShape=CornerFull), 真轨道 = 16dp 高胶囊
                                // 垂直居中。幽灵 = 同款几何只降透明度。Canvas 宽 = 轨道
                                // 盒宽 (与真轨道同坐标系), 高 = 44dp; 轨道段画在中部
                                // 16dp 带内, 端条画满全高 — 与真 thumb 逐像素同形。
                                // x 映射 = drawTrack 同款: 档位轨道两端各缩进 corner
                                // (轨道高/2), 端点档 (frac==0/1) 走全宽线性;
                                // RTL 按 drawTrack 的端点翻转镜像
                                val trackH = 16.dp.toPx()
                                val corner = trackH / 2f
                                val trackTop = (size.height - trackH) / 2f
                                val isRtl = layoutDirection == LayoutDirection.Rtl
                                fun ghostX(value: Int): Float {
                                    val frac = (value - minValue).toFloat() / (maxValue - minValue)
                                    val f = if (isRtl) 1f - frac else frac
                                    return if (steps > 0 && f != 0f && f != 1f) {
                                        corner + (size.width - 2f * corner) * f
                                    } else {
                                        size.width * f
                                    }
                                }
                                val g1 = ghostX(ghostStart)
                                val g2 = ghostX(ghostEnd)
                                val left = minOf(g1, g2)
                                val right = maxOf(g1, g2)
                                // 幽灵轨道段: 浅色胶囊, 与真轨道同高 (16dp) 同垂直位
                                if (right - left >= 1f) {
                                    drawRoundRect(
                                        color = primary.copy(alpha = 0.25f),
                                        topLeft = Offset(left, trackTop),
                                        size = Size(right - left, trackH),
                                        cornerRadius = CornerRadius(corner, corner),
                                    )
                                }
                                // 两端条: 真 thumb 的 1:1 复刻 — 4dp 宽 x 44dp 全高
                                // 全圆胶囊 (CornerFull), 只是半透明
                                val barW = 4.dp.toPx()
                                for (gx in floatArrayOf(g1, g2)) {
                                    drawRoundRect(
                                        color = primary.copy(alpha = 0.40f),
                                        topLeft = Offset(gx - barW / 2f, 0f),
                                        size = Size(barW, size.height),
                                        cornerRadius = CornerRadius(barW / 2f, barW / 2f),
                                    )
                                }
                            }
                            }
                        }
                    }
                },
            )
        }
    }
}

/**
 * 原位置幽灵是否可见 (纯函数, 单测 IntRangeSliderGhostTest 锁定的就是它;
 * 组件内联同款夹取+比较, 二者语义必须一致):
 * - 初值任一为 null (新建路径无原值) → 永不可见;
 * - 初值按显示口径同构夹取 (os = initialStart.coerceIn(min, max);
 *   oe = initialEnd.coerceIn(os, max) — 交叉脏数据先夹起点再夹终点, 与
 *   displayEnd.coerceIn(displayStart, maxValue) 一致);
 * - 夹取后 (os, oe) 与当前显示区间完全重合 (未拖动或拖回原位) → 不可见;
 * - 其余 (部分重叠也算拖离) → 可见。
 */
internal fun sliderGhostVisible(
    currentStart: Int,
    currentEnd: Int,
    initialStart: Int?,
    initialEnd: Int?,
    minValue: Int,
    maxValue: Int,
): Boolean {
    if (initialStart == null || initialEnd == null) return false
    val os = initialStart.coerceIn(minValue, maxValue)
    val oe = initialEnd.coerceIn(os, maxValue)
    return os != currentStart || oe != currentEnd
}

/**
 * 单头滑杆原值幽灵是否可见 (纯函数, 单测 SingleSliderGhostTest 锁定)。
 * 需求 2026-10-29 用户定义原话语义: 原设 7dp 改成 8dp/6dp 时, 原 7dp 位置出现
 * 幽灵提醒"这是他上一次的结果"; 拖回原刻度即消失; 退出页面重进基线重算
 * (基线 = 进页/开弹窗一刻的取值, 由各调用点 remember 一次性捕获, 绝不接 setter)。
 * - baseline null (新建/无原值路径) → 永不可见;
 * - |current - baseline| <= epsilon → 不可见 (判"已回到原刻度点"); epsilon 由调用点
 *   按各自最小量化步进取半档 (0.1 步进 → 0.05; 1/20 缩放档 → 0.025; 整数 dp 档 →
 *   0.5; 色相 1 度档 → 0.5) — 浮点拖动落在档位中点附近时判得住;
 * - NaN/Inf (脏值或换算意外) → 不可见: abs(NaN-x)<=eps 恒 false 会误判"拖离",
 *   幽灵宁可不画不可乱画。
 */
internal fun singleSliderGhostVisible(
    current: Float,
    baseline: Float?,
    epsilon: Float,
): Boolean {
    if (baseline == null) return false
    if (!current.isFinite() || !baseline.isFinite()) return false
    return abs(current - baseline) > epsilon
}

/**
 * 单头滑杆轨道上的原值细条 (纯装饰, 需求 2026-10-29; 形态 = 用户裁定的"一条细条",
 * 单值没有区间段, 不做轨道段)。仿 material3 默认 thumb 竖胶囊形态: 4dp 宽 x 全高
 * 圆头, primary 低透明 — 与双头幽灵端点细条 (本文件 RangeSlider track 内) 同观感族。
 *
 * 几何与色值单点: 拇指半宽 4dp / 轨道两端缩进 8dp / 细条宽 4dp / alpha 0.40 只准
 * 出现在下面这一处 — material3 Slider 轨道盒 = slider 全宽两端各缩进 thumbWidth/2,
 * frac 0..1 线性映射进该内缩区; 调用点 Canvas 用 matchParentSize 撑整条 Slider 边界
 * (高由 44dp thumb 主导)时细条与真 thumb 逐像素同形同位。色相条等自定义底衬不进
 * 本件 (对比配方另画, 见 HueSlider), 本件服务标准 material3 Slider。
 *
 * 非交互铁律: 零 pointerInput、零语义 (testTag 由调用点挂在本件返回的布局上时
 * 仅服务 Robolectric 存在性断言, 不参与朗读), 幽灵纯渲染 — 落库节奏仍归调用点
 * onValueChangeFinished 一克不动。BoxScope 扩展 = 本件只准放进 Box{ Slider; … }
 * 的轨道边界内 (matchParentSize 是 BoxScope 能力), 与双头幽灵同宿主形态。
 */
@Composable
internal fun BoxScope.SingleSliderGhostMark(
    baselineFrac: Float,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    Canvas(modifier.matchParentSize()) {
        val thumbHalf = 4.dp.toPx()
        val barW = 4.dp.toPx()
        val frac = baselineFrac.coerceIn(0f, 1f)
        val x = thumbHalf + (size.width - 2f * thumbHalf) * frac
        drawRoundRect(
            color = primary.copy(alpha = 0.40f),
            topLeft = Offset(x - barW / 2f, 0f),
            size = Size(barW, size.height),
            cornerRadius = CornerRadius(barW / 2f, barW / 2f),
        )
    }
}

/**
 * RangeSlider 回调值 → 整数 (start, end)。
 * 两端各自四舍五入并夹进 [minValue, maxValue]; start 不得越过 end
 * (RangeSlider 本身保证 start<=end, 此处的夹取是对导入脏数据的兜底)。
 */
internal fun snapRangeToInt(
    range: ClosedFloatingPointRange<Float>,
    minValue: Int,
    maxValue: Int,
): Pair<Int, Int> {
    val start = range.start.roundToInt().coerceIn(minValue, maxValue)
    val end = range.endInclusive.roundToInt().coerceIn(start, maxValue)
    return start to end
}

/** 单帧拖动位移的裁决结果: 放行给滑块 / 拦截 (页面与滑块原地不动) */
internal enum class SliderFrameDecision { PassToSlider, Veto }

/**
 * 逐帧增量裁决 (非累积位移, 且无幅度闸): 纵向占优的帧一律拦, 其余放行。
 * 不能设"单帧过 slop 才拦"的幅度闸 — LazyColumn 与 RangeSlider 按压累积都是
 * 跨帧累计 slop: 慢速下滑每帧位移都不过闸 → 逐帧放行 → 小纵移在两个累计器里
 * 双双累计越线 → 页面照滚 (真机反馈 2026-10-27 三次修正的根因), 且 RangeSlider
 * 以纵向占优退出按压循环后两分支皆不命中, 本手势作废 (既不拖也不判 tap)。
 * 纯占优比较让纵向分量寸步不漏: 两个累计器只吃横向帧 → 页面冻、滑块稳。
 * 等号 (45° 平局) 与横向帧放行: 平局交给主功能 (滑块); 静止帧放行走
 * RangeSlider 的 tap 路径 (靠未消费位移判轻点, 拦了反而干扰)。
 * (1,1)→(1,-1)→(0,-1) 与 (1,1)→(0,-1) 对滑块等效: 竖帧位移读 0, 横帧照拖。
 */
internal fun sliderFrameDecision(deltaX: Float, deltaY: Float): SliderFrameDecision =
    if (abs(deltaY) > abs(deltaX)) SliderFrameDecision.Veto else SliderFrameDecision.PassToSlider

/**
 * 按住滑杆后的双协程双 pass 锁 (需求 2026-10-27 三次修正定形: 接触瞬间起整页冻结,
 * 滑块只认横向; 五次修正: 冻结窗口按"滑杆区域内任意手指"计 — 旧写法键死第一根
 * down 的 pointer id, 该指一抬即 break, 掌缘/边缘误触先落后抬会把冻结打穿, 主指
 * 后续下扯帧原样漏给外层列表 (本地多指用例 strayPointerLiftMidGestureKeepsPage
 * Frozen 复现 idx 5→0, 与真机"往下一扯页面照滚"吻合)):
 * - Main 协程 = 绝对冻结: 本节点 Main 在子树 Main 之后、外层 LazyColumn 的 Main
 *   之前 (Node.dispatchMainEventPass: 自身 Initial → children → 自身 Main, 冒泡向根),
 *   每帧 (含 down/up) 无条件消费全部 change → 子级 RangeSlider 已在同帧先读未消费帧
 *   (拖动/轻点照常), 外层滚动容器拿到的永远是已消费帧 → positionChange() 读 0、
 *   isConsumed 判取消 → 整条手势滚动作废, 与手指方向、幅度、第几根手指无关。旧版
 *   "接管后静默、不接管时只拦纵帧"让斜拖帧 Y 分量漏给页面 → 被带动, 已废弃。
 *   Main 同时观察 dragTakenOver: 帧在我们读到前已被消费且非自家 Initial 拦的
 *   (vetoedPointerIds 逐指区分 — 同节点两 pass 共用同一 change, consumedDelegate
 *   联动, 不区分会把自家拦截误判成子级接管; 多指下布尔标志会让掌缘的自家拦截
 *   掩盖滑拇指的子级接管 → Initial 继续拦 → drag 取消坑复发) → 子级进入拖动循环
 *   (每拖动帧自消费)。
 * - Initial 协程 = 按压期滑块 Y 免疫: 未接管时纵向占优帧先于子级消费 →
 *   RangeSlider 按压循环 overSlop 对该帧读 0 → 纯/斜纵向不推成纵向占优而作废手势,
 *   (1,1)→(1,-1)→(0,-1) 与 (1,1)→(0,-1) 等效; 横帧/45°平局/静止帧放行 (平局与静止
 *   交滑块; 静止帧拦了伤轻点判定)。多指下逐指裁决、只吃纵向占优那根 — 掌缘竖抖
 *   不许顺带冻结滑拇指的横移。已接管则不再拦 — 拖动期子级 horizontalDrag 只吃
 *   positionChange().x, Y 天然无效; 此时若仍拦纵帧, drag() 的 motionConsumed 会在
 *   子级读取时刻看到 isConsumed → 拖动中途被取消 (滑块卡住), 二次修正实证的坑。
 * 两协程存活判据一致: pressed 集合跟踪区域内全部按压手指, 非空就一直吃帧, 全部
 * 抬起才结束手势。PointerInputScope 不实现 CoroutineScope, 兄弟协程须套
 * coroutineScope (material3 RangeSlider 同款)。两协程均在首个 down 复位标志,
 * 跑在 pointerInput 单线程派发上下文, 同帧顺序 Initial→Main 保证标志读写无竞态。
 */
internal fun Modifier.sliderFreezePageScroll(): Modifier =
    pointerInput(Unit) {
        var dragTakenOver = false
        val vetoedPointerIds = mutableSetOf<PointerId>()
        coroutineScope {
            launch {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
                    dragTakenOver = false
                    val pressed = mutableSetOf(down.id)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        for (change in event.changes) {
                            // 只认子级消费 (= 进入拖动): 自家 Initial 拦的帧不算接管
                            if (change.isConsumed && change.id !in vetoedPointerIds) dragTakenOver = true
                            if (change.pressed) pressed.add(change.id) else pressed.remove(change.id)
                        }
                        // 绝对冻结: 本手势每一帧整体吃下, 外层任何滚动容器寸步不得
                        event.changes.forEach { it.consume() }
                        if (pressed.isEmpty()) break
                    }
                }
            }
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                dragTakenOver = false
                vetoedPointerIds.clear()
                val pressed = mutableSetOf(down.id)
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    vetoedPointerIds.clear()
                    for (change in event.changes) {
                        if (change.pressed) pressed.add(change.id) else pressed.remove(change.id)
                    }
                    if (!dragTakenOver) {
                        for (change in event.changes) {
                            if (!change.pressed) continue
                            val decision = sliderFrameDecision(
                                deltaX = change.position.x - change.previousPosition.x,
                                deltaY = change.position.y - change.previousPosition.y,
                            )
                            if (decision == SliderFrameDecision.Veto) {
                                // 按压期纵向吃帧: RangeSlider 位移读 0 → 拇指不认 Y
                                vetoedPointerIds.add(change.id)
                                change.consume()
                            }
                        }
                    }
                    if (pressed.isEmpty()) break
                }
            }
        }
    }

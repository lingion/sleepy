package com.lingion.sleepy.ui.component

import android.annotation.SuppressLint
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lingion.sleepy.R
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.ui.theme.noRippleClickable
import kotlinx.coroutines.withTimeout

/**
 * 拖拽吸附落点投影 (2026-10-28 移植 Lawnchair): 宿主网格坐标 — 列下标(对 sortedDays,
 * 水平出界已钳首/末列) + 吸附起始节次 + 跨度。每 MOVE 帧写、松手消费, 所见即所得。
 */
data class OccurrenceDropTarget(val dayIndex: Int, val startNode: Int, val step: Int)

/**
 * 长按浮起交互的状态容器 — 与 [QuickAddCourseState] 同级模式(由调用方持有,
 * 翻页/周切换不丢)。卡片从网格浮到顶层: 背景高斯模糊 + 编辑/删除小浮框;
 * 拖拽开始后模糊与浮框消失, 只有卡片跟手; 合法落点吸附节次并回调移动该次课,
 * 非法落点弹回原位、浮框回归。
 */
@Stable
class CourseFloatState {
    var course: CourseEntity? by mutableStateOf(null)
    var day: Int by mutableStateOf(1)
    /** 浮起时的周(1 起, 页+1) — 屏根空白确认路径看不到网格 currentWeek, 时间域随浮起快照存这里。 */
    var week: Int by mutableStateOf(1)
    var startNode: Int by mutableStateOf(1)
    var endNode: Int by mutableStateOf(1)
    var isGrey: Boolean by mutableStateOf(false)
    var dragging: Boolean by mutableStateOf(false)
    /** 网格坐标系(dp, 相对 grid 主体 Box 原点): 浮卡当前/原始位置与尺寸。 */
    var x: Dp by mutableStateOf(0.dp)
    var y: Dp by mutableStateOf(0.dp)
    var width: Dp by mutableStateOf(0.dp)
    var height: Dp by mutableStateOf(0.dp)
    var originX: Dp = 0.dp
    var originY: Dp = 0.dp
    // 拉起时的原始跨度快照 — 改跨度只拖把手不即时落库(二期 D 契约):
    // spanChanged 判 pending, revertSpan 供确认后否决复原, 落库几何只读状态不量网格。
    var originStartNode: Int = 1
    var originEndNode: Int = 1
    var originHeight: Dp = 0.dp
    /** 起拖那一页的周号 — 置灰只认原占位格, 跨周翻页后原页依旧置灰。 */
    var originWeek: Int = 1
    val step: Int get() = (endNode - startNode + 1).coerceAtLeast(1)
    val active: Boolean get() = course != null
    /** 浮卡当前跨度与拉起时不同 — 空白点击/返回键须先走「是否调整持续节次」确认。 */
    val spanChanged: Boolean get() = course != null && (startNode != originStartNode || endNode != originEndNode)

    // ---- 边缘翻页拖拽 (2026-10-27 用户裁决: 拖到周缘自动翻上/下周, 放手即落对应槽位) ----
    // 方向记号全场统一: +1 = 右缘 = 下一周, -1 = 左缘 = 上一周, 0 = 不在边缘。
    /** Semester 边界触界方向 (周 1 再往前 / maxWeek 再往后): 驻留计时禁用, peek 静止示形。 */
    var edgeBlocked: Int by mutableStateOf(0)
    /** 当前压住的边缘方向 — peek 与驻留计时的驱动键。 */
    var edgeDir: Int by mutableStateOf(0)
    /** 边缘驻留进度 0..1 — 网格每 50ms 步进写入, peek 淡入用它。 */
    var peekProgress: Float by mutableStateOf(0f)
    /** 起拖页网格主体左缘 (px, root 坐标系) — 翻页动画期浮层反平移补偿的基准。 */
    var anchorRootLeftPx by mutableStateOf(0f)
    /** 进入拖拽瞬间的卡片中心 x (网格坐标) — 边缘判据的防误触锚 (需位移超 slop)。 */
    var dragOriginCx: Dp by mutableStateOf(0.dp)
    /** 进入拖拽瞬间起拖页的纵向滚动 (dp) — 翻页换宿主后各页几何同构, 新宿主据此
     *  对齐滚动量, 卡悬停行 = 视觉行; 落点判定本就在内容坐标系, 双保险。 */
    var anchorScrollDp: Dp by mutableStateOf(0.dp)
    /** 一次驻留只许翻一页 (2026-10-28 用户反馈: 压边无限连翻停不了):
     *  翻页即消耗, 直到手指回到格内 (dir==0 轮询) 才复位重新计时。
     *  非组合读取 → 普通 var; 计时器换页重启 (week 入键) 也不得复活消耗量。 */
    var edgeArmed = true
    /** 顶栏周 chip 待回写 (2026-10-28 真机实证): 合法落点松手走 dismiss — active 与
     *  dragging 同帧落 false, settledPage effect 的「active && !dragging」窗口在这条
     *  路上从未打开, chip 永远停在起拖周。边缘翻页翻过即置位, 屏根 effect 在
     *  「非拖拽且 (浮起或待回写)」时消费清零。作 effect 键 → 必须是快照态。 */
    var chipPending by mutableStateOf(false)
    // ---- 吸附投影 (2026-10-28 移植 Lawnchair findNearestArea + 150ms 跳格) ----
    // 每个 MOVE 帧把卡片「吸附投影」写进 snap*: 卡随投影走(磁吸跳格), 松手即所见;
    // 非法投影(纵向越出格区/跨度越末节) snap 退回跟手位, 松手弹回。
    /** 每帧吸附落点 — 松手直接消费它, 所见即所得; null = 非法落点。 */
    var dropTarget: OccurrenceDropTarget? by mutableStateOf(null)
    /** 投影后的渲染位/尺寸 (网格坐标 dp) — 浮卡 modifier 读它, 150ms 动画收敛。 */
    var snapX: Dp by mutableStateOf(0.dp)
    var snapY: Dp by mutableStateOf(0.dp)
    var snapHeight: Dp by mutableStateOf(0.dp)

    /** 把手拉伸未获确认 → 位置/高度回到拉起时的原样, 浮卡本身留在原位。 */
    fun revertSpan() {
        startNode = originStartNode
        endNode = originEndNode
        y = originY
        height = originHeight
        // 渲染读 snap* (吸附投影) — 否决拉伸后投影同步回原位, 否则浮卡停在拉伸预览位。
        snapY = originY
        snapHeight = originHeight
    }

    /**
     * 空白点击/返回键的统一分派 (D 契约): 无 pending 拉伸 → 直接收层;
     * 有 pending → [submit] 走与落点同一个移动管线 (resizeOnly=true, 目标 = 当前格 +
     * 拉伸后的起止), 落库成功收层, 否决 → [revertSpan] 复原原跨度。
     */
    fun confirmPendingResizeBeforeDismiss(
        submit: (lifted: CourseEntity, day: Int, node: Int, step: Int, onResult: (Boolean) -> Unit) -> Unit,
    ) {
        val lifted = course ?: return
        if (!spanChanged) { dismiss(); return }
        submit(lifted, lifted.day, startNode, step) { saved -> if (saved) dismiss() else revertSpan() }
    }
    internal var cardBounds: Rect? by mutableStateOf(null)
    internal var popupBounds: Rect? by mutableStateOf(null)

    fun lift(
        course: CourseEntity,
        day: Int,
        week: Int,
        startNode: Int,
        endNode: Int,
        x: Dp,
        y: Dp,
        width: Dp,
        height: Dp,
        isGrey: Boolean,
    ) {
        this.course = course
        this.day = day
        this.week = week
        this.startNode = startNode
        this.endNode = endNode
        this.x = x; this.y = y; this.width = width; this.height = height
        this.isGrey = isGrey
        originX = x; originY = y
        originStartNode = startNode; originEndNode = endNode; originHeight = height
        originWeek = week
        dragging = false
        edgeDir = 0
        edgeBlocked = 0
        peekProgress = 0f
        edgeArmed = true
        dropTarget = null
        snapX = x; snapY = y; snapHeight = height // 未拖拽 = 原位渲染
        cardBounds = null
        popupBounds = null
    }

    fun dismiss() {
        course = null
        dragging = false
        edgeBlocked = 0
        peekProgress = 0f
        dropTarget = null
        cardBounds = null
        popupBounds = null
    }
}

/** 浮起时模糊整个网格内容层 — API 31+ 高斯模糊; 26-30 无 RenderEffect, 由遮罩压暗兜底。 */
@SuppressLint("NewApi")
internal fun Modifier.gridFloatBlur(): Modifier = graphicsLayer {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val radius = 20.dp.toPx()
        renderEffect = BlurEffect(radius, radius)
    }
}

/**
 * 浮层空档处的按下: 只关浮层, 不点击课程、不打开加课胶囊。
 * Initial pass — 与 [dismissQuickAddOnOutsidePress] 同构(祖先节点最先拿到 down):
 * 落在浮卡/浮框矩形内的 down 原样放行(交给拖拽/按钮), 其余空档消费并关浮层,
 * 底层网格因此既不加课也不点课。[onBlankPress] 非空且跨度已被把手拉伸
 * (D 契约: 拉伸不自动落库) → 空档按下改走确认回调, 浮层不直接消失。
 */
fun Modifier.dismissFloatOnOutsidePress(
    state: CourseFloatState,
    onBlankPress: (() -> Unit)? = null,
): Modifier = composed {
    var coordinates: LayoutCoordinates? by remember { mutableStateOf(null) }
    val blankPress by rememberUpdatedState(onBlankPress)
    onGloballyPositioned { coordinates = it }.pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!state.active || state.dragging || down.isConsumed) return@awaitEachGesture
            val position = coordinates?.localToRoot(down.position) ?: return@awaitEachGesture
            val onLifted = state.cardBounds?.contains(position) == true ||
                state.popupBounds?.contains(position) == true
            if (onLifted) return@awaitEachGesture
            down.consume()
            val pending = blankPress
            if (pending != null && state.spanChanged) pending() else state.dismiss()
        }
    }
}

/**
 * 一次按下里的「长按浮起 → 不松手即拖」连续手势 (A 契约) — 取代浮动态下课程卡
 * 的 combinedClickable(官方长按成功后 consumeUntilUp, 同一手指再也拖不起来)。
 * 长按等待段与官方 TapGestureDetector 同口径: 不消费任何事件, 滚动容器越 slop
 * 照旧劫走手势(stolen → 全程静默, 保持网格从卡片起手滚动的既有能力);
 * 超时前提前松手且位移不超 slop = 轻点 [onTap]。长按超时到达 = [onLift] 浮起,
 * 同帧起的后续位移逐帧消费(滚动劫不走)并进入拖拽: 首次位移先 [onDragStart] 一次,
 * 每帧 [onDrag](delta), 抬手/事件终结走 [onDrop] — 合法落点与弹回由调用方判定,
 * 与浮卡自身 detectDragGestures 同一 commitFloatDrop 路径。浮起后未移动即松手 →
 * 什么都不做(卡片保持浮起, 与旧长按浮起一致)。[lifted] 为真(已有浮卡)时整段
 * 静默 — 空档消费通常已先一步拦下, 这里是兜底。
 *
 * 拖拽段位移一律换算到 root 坐标再回调 (场景C, 2026-10-29 用户反馈「翻页之后只能
 * 上下拖」): change.position 相对接收节点, 边缘翻页把起拖页(本节点在页容器里)
 * 物理平移一整屏 — 换页那一帧本地坐标跳变一个页宽, 直接当位移即污染
 * floatState.x: 卡钉死末列缘带, edgeArmed 永不复归, 横向输入全丢(纵向不受水平
 * 平移影响 = 「只能上下拖」)。localToRoot 后的差值对任意时刻的节点平移都守恒,
 * 静止节点上 root delta ≡ local delta, 既有单页用例行为不变。
 */
fun Modifier.continuousFloatLift(
    lifted: () -> Boolean,
    onTap: () -> Unit,
    onLift: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDrop: () -> Unit,
): Modifier = composed {
    val isLifted by rememberUpdatedState(lifted)
    val tap by rememberUpdatedState(onTap)
    val lift by rememberUpdatedState(onLift)
    val dragStart by rememberUpdatedState(onDragStart)
    val drag by rememberUpdatedState(onDrag)
    val drop by rememberUpdatedState(onDrop)
    // 场景C 换算基准: 本节点坐标 → root 坐标。与 dismissFloatOnOutsidePress 同捕获
    // 模式; 节点换页平移时 localToRoot 随之更新, delta 守恒。null 帧(尚未布局)退化为
    // 本地坐标 — 与修复前同行为, 不崩不丢事件。
    var coordinates: LayoutCoordinates? by remember { mutableStateOf(null) }
    onGloballyPositioned { coordinates = it }.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (down.isConsumed || isLifted()) return@awaitEachGesture
            val id = down.id
            var previous = down.position
            var releasedEarly = false
            var stolen = false
            try {
                withTimeout(viewConfiguration.longPressTimeoutMillis) {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == id } ?: break
                        if (change.changedToUp()) { releasedEarly = true; break }
                        if (change.isConsumed) { stolen = true; break }
                        // 官方 waitForLongPress 同口径: 同一事件的 Final pass 再看一次 —
                        // 祖先(滚动容器)在 Main 后段消费的越界位移在这里被认定 stolen,
                        // 长按让位给滚动, 与 combinedClickable 行为一致。
                        val finalPass = awaitPointerEvent(PointerEventPass.Final)
                        if (finalPass.changes.any { it.id == id && it.isConsumed }) {
                            stolen = true; break
                        }
                        previous = change.position
                    }
                }
            } catch (e: PointerEventTimeoutCancellationException) {
                // 超时到达 — 官方 waitForLongPress 同形态, 即浮起信号
            }
            if (stolen) return@awaitEachGesture
            if (releasedEarly) {
                if ((previous - down.position).getDistance() <= viewConfiguration.touchSlop) tap()
                return@awaitEachGesture
            }
            lift()
            var dragging = false
            // 浮起死区 (2026-10-27 用户反馈: 长按浮起后手抖约 0.1cm 即进拖拽, 模糊浮框瞬间消失):
            // 旧实现按"逐帧非零增量"入场 — 0.1px 也算拖动。官方 DragGestureDetector 的
            // TouchSlopDetector 用「自锚点累计位移 ≥ touchSlop」判据; 长按转拖拽比官方
            // 「按下即拖」多一段持按, 抖动抑制取 2×touchSlop。死区内仍逐帧消费 —
            // 浮起即已认领手势, 不许滚动劫走 (与旧行为一致); 越区首帧一次性补齐累计位移,
            // 终帧位移量与旧算法逐帧相同 → 落点判定不受影响。
            // 死区锚与逐帧位移同取 root 系: 长按段(浮起前)不会有翻页(翻页先要浮起
            // 并拖拽), 故 anchor 的 root 值在浮起当帧锁定即恒真。
            val anchorRoot = coordinates?.localToRoot(previous) ?: previous
            var previousRoot = anchorRoot
            val liftSlop = viewConfiguration.touchSlop * 2f
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == id } ?: break
                if (change.changedToUp()) break
                // 拖拽段不设越界判据(detectDragGestures 同口径 — 拖出卡面正是常态),
                // 只认被消费: 消费 = 被滚动/他方抢走, 立即按 drop 收尾交落点判定。
                if (change.isConsumed) break
                change.consume()
                val rootNow = coordinates?.localToRoot(change.position) ?: change.position
                val delta = rootNow - previousRoot
                previousRoot = rootNow
                if (dragging) {
                    if (delta != Offset.Zero) drag(delta)
                    continue
                }
                val accumulated = rootNow - anchorRoot
                if (accumulated.getDistance() <= liftSlop) continue
                dragging = true
                dragStart()
                drag(accumulated)
            }
            if (dragging) drop()
        }
    }
}

/**
 * 浮起的课程卡: 复用 [CourseOverlayCard] 的取色/文案渲染(去掉内层点击 —
 * 浮卡只响应拖拽), 上下 14dp 边缘把手与加课胶囊同款手势语义。
 * 拖拽位移保持本地 delta: 浮卡节点本身随 floatState.x/y 逐帧平移, root 换算会把
 * 节点自位移二次计入 (反馈放大, 越界弹回) — 场景C 的失控只发生在网格卡起手那条
 * continuousFloatLift 路径, 由它自己做 root 换算。
 */
@Composable
internal fun FloatingOccurrenceCard(
    state: CourseFloatState,
    scale: Float,
    cornerRatio: Float,
    groupRows: List<CourseEntity>,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDrop: () -> Unit,
    onResizeStart: (Boolean) -> Unit,
    onResize: (Boolean, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifted = state.course ?: return
    val start by rememberUpdatedState(onDragStart)
    val drag by rememberUpdatedState(onDrag)
    val drop by rememberUpdatedState(onDrop)
    val resizeStart by rememberUpdatedState(onResizeStart)
    val resize by rememberUpdatedState(onResize)
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape((12 * scale * cornerRatio).dp)
    Box(modifier.onGloballyPositioned { state.cardBounds = it.boundsInRoot() }) {
        CourseOverlayCard(
            course = lifted,
            modifier = Modifier
                .fillMaxSize()
                .shadow((6 * scale).dp, shape, clip = false)
                .testTag("float-card")
                .pointerInput(lifted.id) {
                    detectDragGestures(
                        onDragStart = { start() },
                        onDrag = { change, amount -> change.consume(); drag(amount) },
                        onDragEnd = { drop() },
                        onDragCancel = { drop() },
                    )
                },
            isGrey = state.isGrey,
            scale = scale,
            cornerRatio = cornerRatio,
            groupRows = groupRows,
        )
        for (top in listOf(true, false)) {
            Box(
                modifier = Modifier
                    .align(if (top) Alignment.TopCenter else Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(14.dp)
                    .testTag(if (top) "float-top" else "float-bottom")
                    .pointerInput(lifted.id) {
                        detectVerticalDragGestures(
                            onDragStart = { resizeStart(top) },
                            onVerticalDrag = { change, amount -> change.consume(); resize(top, amount) },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.width(16.dp).height(3.dp).background(colors.primary, RoundedCornerShape(50)))
            }
        }
    }
}

/** 浮卡上方的小浮框: 编辑这一节 / 删除本次(复用单次课操作文案, 口径与编辑器一致)。 */
@Composable
internal fun FloatActionPopup(
    state: CourseFloatState,
    scale: Float,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape((10 * scale).dp)
    Row(
        modifier = modifier
            .shadow((4 * scale).dp, shape, clip = false)
            .clip(shape)
            .background(colors.surfaceContainerHigh)
            .border(1.dp, colors.outlineVariant, shape)
            .onGloballyPositioned { state.popupBounds = it.boundsInRoot() }
            .testTag("float-popup"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.occurrence_adjust),
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = (12 * scale).sp,
            ),
            color = colors.primary,
            modifier = Modifier.testTag("float-edit").noRippleClickable(onEdit)
                .padding(horizontal = (14 * scale).dp, vertical = (8 * scale).dp),
        )
        Box(Modifier.width(1.dp).height(20.dp).background(colors.outlineVariant))
        Text(
            text = stringResource(R.string.occurrence_delete),
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = (12 * scale).sp,
            ),
            color = colors.error,
            modifier = Modifier.testTag("float-delete").noRippleClickable(onDelete)
                .padding(horizontal = (14 * scale).dp, vertical = (8 * scale).dp),
        )
    }
}

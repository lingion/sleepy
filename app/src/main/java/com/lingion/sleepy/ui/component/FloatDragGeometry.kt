package com.lingion.sleepy.ui.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Launcher3 Workspace.checkDragObjectIsOverNeighbourPages: 手指/中心取向外值，非卡片边界。 */
internal fun floatEdgeDirection(centerX: Float, fingerX: Float?, originX: Float, width: Float): Int {
    if (abs(centerX - originX) < FLOAT_EDGE_SLOP_DP) return 0
    val pointer = fingerX ?: centerX
    return when {
        min(pointer, centerX) < FLOAT_EDGE_ZONE_DP -> -1
        max(pointer, centerX) > width - FLOAT_EDGE_ZONE_DP -> 1
        else -> 0
    }
}

/** Pager 可缓存旧页壳；只有视口邻页和正在接收拖拽的来源页运行重网格。 */
internal fun shouldComposeFloatDragPage(pageWeek: Int, viewportWeek: Int, state: CourseFloatState): Boolean =
    abs(pageWeek - viewportWeek) <= 1 || (state.dragging && pageWeek == state.dragHostWeek)

/** 在页的 Initial pass 记录真实触点；只固定接收本次手势的页，结束/取消必释放。 */
internal fun Modifier.trackFloatDragPointer(state: CourseFloatState): Modifier = composed {
    val container = LocalPinnableContainer.current
    var coordinates: LayoutCoordinates? by remember { mutableStateOf(null) }
    onGloballyPositioned { coordinates = it }.pointerInput(state, container) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val pin = container?.pin()
            try {
                state.dragPointerRootX = coordinates?.localToRoot(down.position)?.x
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    state.dragPointerRootX = coordinates?.localToRoot(change.position)?.x
                } while (change.pressed)
            } finally {
                state.dragPointerRootX = null
                pin?.release()
            }
        }
    }
}

/** 高频位置/滚动读取局限于本 modifier；自由拖动不经过补间，合法格仍保留磁吸动画。
 * https://developer.android.com/develop/ui/compose/performance/bestpractices#defer-reads
 */
internal fun Modifier.floatOverlayPlacement(
    state: CourseFloatState,
    scroll: ScrollState,
    header: Dp,
    popupOffset: Dp? = null,
): Modifier = composed {
    val spec = if (state.dragging && state.dropTarget == null) snap<Dp>()
        else tween<Dp>(FLOAT_SNAP_ANIM_MS.toInt(), easing = FLOAT_PAGE_EASING)
    // 每次重新浮起从新卡位置初始化，不能沿用上一张卡的动画状态。
    androidx.compose.runtime.key(state.course) {
        val x = animateDpAsState(state.snapX, spec, label = "float-snap-x")
        val y = animateDpAsState(state.snapY, spec, label = "float-snap-y")
        val height = animateDpAsState(state.snapHeight, spec, label = "float-snap-h")
        this.offset {
            val free = state.dragging && state.dropTarget == null
            val scrollDp = if (state.dragging) state.anchorScrollDp else scroll.value.toDp()
            val top = (if (free) state.snapY else y.value) + header - scrollDp
            IntOffset((if (free) state.snapX else x.value).roundToPx(),
                (if (popupOffset == null) top else (top - popupOffset).coerceAtLeast(0.dp)).roundToPx())
        }.layout { measurable, constraints ->
            val w = state.width.roundToPx().coerceAtLeast(0)
            val childConstraints = if (popupOffset == null)
                Constraints.fixed(w, height.value.roundToPx().coerceAtLeast(0))
            else constraints.copy(minWidth = w, maxWidth = w)
            val placeable = measurable.measure(childConstraints)
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }
    }
}

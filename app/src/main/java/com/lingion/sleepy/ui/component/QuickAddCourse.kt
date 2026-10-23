package com.lingion.sleepy.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lingion.sleepy.R
import com.lingion.sleepy.ui.theme.noRippleClickable

data class QuickAddRange(val day: Int, val startNode: Int, val endNode: Int) {
    val step: Int get() = endNode - startNode + 1
}

@Stable
class QuickAddCourseState {
    var selection by mutableStateOf<QuickAddRange?>(null)
    internal var capsuleBounds: Rect? = null
    fun dismiss() { selection = null; capsuleBounds = null }
}

/** Consume the outside press so the same tap cannot start another selection or open a course. */
fun Modifier.dismissQuickAddOnOutsidePress(state: QuickAddCourseState): Modifier = composed {
    var coordinates: LayoutCoordinates? by remember { mutableStateOf(null) }
    onGloballyPositioned { coordinates = it }.pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val position = coordinates?.localToRoot(down.position)
            if (state.selection != null && position != null && state.capsuleBounds?.contains(position) != true) {
                state.dismiss()
                down.consume()
            }
        }
    }
}

@Composable
internal fun QuickAddCourseCapsule(
    selection: QuickAddRange,
    state: QuickAddCourseState,
    onConfirm: () -> Unit,
    onResizeStart: (Boolean) -> Unit,
    onResize: (Boolean, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    val label = stringResource(R.string.occurrence_quick_add, selection.startNode, selection.endNode)
    Box(modifier.onGloballyPositioned { state.capsuleBounds = it.boundsInRoot() }
        .testTag("quick-add-capsule")
        .background(colors.primaryContainer, shape)
        .border(1.dp, colors.primary, shape)
        .semantics { contentDescription = label }
        .noRippleClickable(onClick = onConfirm)) {
        Text("+", modifier = Modifier.align(Alignment.Center), color = colors.onPrimaryContainer, fontSize = 24.sp)
        for (top in listOf(true, false)) {
            val start by rememberUpdatedState(onResizeStart)
            val resize by rememberUpdatedState(onResize)
            val handleLabel = stringResource(if (top) R.string.occurrence_quick_add_start else R.string.occurrence_quick_add_end)
            Box(Modifier.align(if (top) Alignment.TopCenter else Alignment.BottomCenter)
                .fillMaxWidth().height(14.dp)
                .testTag(if (top) "quick-add-top" else "quick-add-bottom")
                .semantics { contentDescription = handleLabel }
                .pointerInput(top) {
                    detectVerticalDragGestures(
                        onDragStart = { start(top) },
                        onVerticalDrag = { change, amount -> change.consume(); resize(top, amount) }
                    )
                }, contentAlignment = Alignment.Center) {
                Box(Modifier.width(16.dp).height(3.dp).background(colors.primary, RoundedCornerShape(50)))
            }
        }
    }
}

package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w300dp-h300dp-mdpi")
@OptIn(ExperimentalMaterial3Api::class)
class GlassTopAppBarTest {
    @get:Rule val compose = createComposeRule()
    private var clicks = 0
    private val marker = mutableStateOf(Color.Red)

    private fun show(rtl: Boolean = false, action: Boolean = false, style: UiStyle = UiStyle.LiquidGlass) {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalUiStyle provides style,
                    LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                ) {
                    GlassHost(modifier = Modifier.size(240.dp, 200.dp)) {
                        Scaffold(topBar = {
                            GlassTopAppBar(
                                title = { Text("Page") },
                                modifier = Modifier.testTag("bar"),
                                expandedHeight = 64.dp,
                                windowInsets = WindowInsets(0, 0, 0, 0),
                                navigationIcon = {
                                    if (!action) Box(Modifier.size(48.dp).testTag("control").glassControl().clickable { clicks++ }, contentAlignment = androidx.compose.ui.Alignment.Center) {
                                        Box(Modifier.fillMaxSize().background(marker.value))
                                    }
                                },
                                actions = {
                                    if (action) Box(Modifier.size(48.dp).testTag("control").glassControl().clickable { clicks++ }, contentAlignment = androidx.compose.ui.Alignment.Center) {
                                        Box(Modifier.fillMaxSize().background(marker.value))
                                    }
                                },
                            )
                        }) { padding ->
                            Box(Modifier.fillMaxSize().padding(padding).background(Color.Green))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun dragAndCheck(rtl: Boolean = false, action: Boolean = false) {
        show(rtl = rtl, action = action)
        compose.mainClock.autoAdvance = false
        val control = compose.onNodeWithTag("control")
        val centerX = control.fetchSemanticsNode().boundsInRoot.center.x.roundToInt()
        // 标记填满控件，让栏外取样点落在拖动后色块内部，避开缩放边缘的混色。
        // 仍在栏底以下验证绘制，不能退回栏内而漏掉裁切回归。
        val belowBar = compose.onNodeWithTag("bar").fetchSemanticsNode().boundsInRoot.bottom.roundToInt() + 2
        control.performTouchInput { down(center) }
        compose.waitForIdle()
        control.performTouchInput { moveBy(Offset(0f, 100f), 200) }
        settleSprings()
        assertEquals("放大拖拽后的按钮必须绘制在栏底以下", android.graphics.Color.RED,
            compose.onRoot().captureToImage().asAndroidBitmap().getPixel(centerX, belowBar))
        compose.runOnIdle { marker.value = Color.Blue }
        repeat(3) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        assertEquals("抬起层必须继续更新前景", android.graphics.Color.BLUE,
            compose.onRoot().captureToImage().asAndroidBitmap().getPixel(centerX, belowBar))
        control.performTouchInput { cancel() }
        settleSprings()
        assertEquals("取消后还原且不留下悬浮副本", android.graphics.Color.GREEN,
            compose.onRoot().captureToImage().asAndroidBitmap().getPixel(centerX, belowBar))
        compose.runOnIdle { assertEquals(0, clicks) }
        control.performClick()
        compose.runOnIdle { assertEquals("原语义节点只回调一次", 1, clicks) }
    }

    private fun settleSprings() {
        // The optical springs draw on frame callbacks. A screenshot must use a
        // settled position rather than sample a moving anti-aliased edge.
        repeat(60) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
    }

    @Test fun navigationDragPaintsOutsideToolbarAndSettlesBack() = dragAndCheck()
    @Test fun rtlNavigationDragPaintsOutsideToolbar() = dragAndCheck(rtl = true)
    @Test fun actionDragPaintsOutsideToolbar() = dragAndCheck(action = true)

    @Test fun classicRetainsMaterialDrawingAndSingleClick() {
        show(style = UiStyle.Classic)
        val control = compose.onNodeWithTag("control")
        val centerX = control.fetchSemanticsNode().boundsInRoot.center.x.roundToInt()
        val belowBar = compose.onNodeWithTag("bar").fetchSemanticsNode().boundsInRoot.bottom.roundToInt()
        control.performTouchInput { down(center); moveBy(Offset(0f, 100f), 200) }
        compose.waitForIdle()
        assertEquals(android.graphics.Color.GREEN,
            compose.onRoot().captureToImage().asAndroidBitmap().getPixel(centerX, belowBar))
        control.performTouchInput { cancel() }
        control.performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }
}

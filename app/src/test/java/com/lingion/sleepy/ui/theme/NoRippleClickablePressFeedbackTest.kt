package com.lingion.sleepy.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * noRippleClickable 按压反馈契约 (M3 Expressive motion pass, Task 1):
 * 1) 加 graphicsLayer 缩放后点击语义不得回归 — performClick 仍触发回调;
 * 2) 按压动画只走 interactionSource, 禁 pointerInput (课程卡长按拖拽母容器
 *    IntRangeSlider 冻结先例: pointerInput 会参与手势竞争, interactionSource
 *    只观察不改派发);
 * 3) 缩放规格必须来自 motionScheme — 源码级守卫锁单一调参点。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rUS-w360dp-h640dp-port-xhdpi")
class NoRippleClickablePressFeedbackTest {

    @get:Rule val compose = createComposeRule()

    private fun findUpward(rel: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, rel)
            if (f.exists()) return f
            dir = dir.parentFile
        }
        error("$rel not found")
    }

    @Test fun click_still_dispatches_after_press_scale_added() {
        val clicks = intArrayOf(0)
        compose.setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color.Red)
                        .testTag("press-target")
                        .noRippleClickable { clicks[0]++ },
                )
            }
        }
        compose.onNodeWithTag("press-target").assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertEquals("加缩放后 performClick 仍须恰好触发一次回调", 1, clicks[0])
    }

    @Test fun press_gesture_does_not_swallow_click_after_release() {
        val clicks = intArrayOf(0)
        compose.setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color.Red)
                        .testTag("press-target")
                        .noRippleClickable { clicks[0]++ },
                )
            }
        }
        // 真实按住再松手 (down→up): clickable 判定为点击, 回调必须触发
        compose.onNodeWithTag("press-target").performTouchInput {
            down(center)
            up()
        }
        compose.waitForIdle()
        assertEquals(1, clicks[0])
    }

    @Test fun press_feedback_uses_interaction_source_not_pointer_input() {
        val src = findUpward("app/src/main/java/com/lingion/sleepy/ui/theme/NoRippleClickable.kt").readText()
        // 禁 pointerInput 调用: 手势竞争会破坏拖拽母容器 (slider 冻结先例)。
        // 判据取调用形态 "pointerInput(" — 注释里允许出现该词的说明性提及。
        assertTrue("noRippleClickable 不得引入 pointerInput 调用", "pointerInput(" !in src)
        // 按压状态只从 interactionSource 采
        assertTrue(src.contains("collectIsPressedAsState"))
        // 缩放规格必须来自 motionScheme (单一调参点约束), 目标值用 SleepyPressScale 常量
        assertTrue(src.contains("fastEffectsSpec"))
        assertTrue(src.contains("SleepyPressScale"))
        assertTrue(src.contains("graphicsLayer"))
    }
}

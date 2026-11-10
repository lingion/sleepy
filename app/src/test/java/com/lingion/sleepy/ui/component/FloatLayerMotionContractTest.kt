package com.lingion.sleepy.ui.component
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatLayerMotionContractTest {
    @Test
    fun scrim_is_subtle_with_blur_and_stronger_on_older_devices() {
        assertEquals("API 26-30 无 RenderEffect, 压暗兜底 0.35", 0.35f, scrimAlphaFor(26), 0f)
        assertEquals(0.35f, scrimAlphaFor(30), 0f)
        assertEquals("API 31+ 以模糊为主，轻微压暗", 0.12f, scrimAlphaFor(31), 0f)
        assertEquals(0.12f, scrimAlphaFor(35), 0f)
    }

    @Test
    fun blur_only_applies_to_a_lifted_stationary_course() {
        assertEquals("静止浮起时模糊背景", 20.dp, floatBlurRadiusDp(active = true, dragging = false))
        assertEquals("拖拽中网格复原不糊", 0.dp, floatBlurRadiusDp(active = true, dragging = true))
        assertEquals(0.dp, floatBlurRadiusDp(active = false, dragging = false))
        assertEquals(0.dp, floatBlurRadiusDp(active = false, dragging = true))
    }
}

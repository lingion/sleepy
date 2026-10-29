package com.lingion.sleepy.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SleepyMotion 纯值契约 (M3 Expressive motion pass, Task 1):
 * 锁三族缩放的锚点常量 — 它们是 MD3E 特征动画的「签名值」, 后续任务与测试都引用
 * 这些常量本身 (单一调参点), 不许在调用处再写字面量 0.8f/0.92f/0.98f。
 * popup 家族 0.8→1 (MD3E dialog/menu/snackbar 特征族), content 家族 0.92 (页面族镜像,
 * 与 SleepyMaterialTransition 旧 0.92 约定一致), press 0.98 (自定义表面按压反馈)。
 */
class SleepyMotionTest {

    @Test
    fun popup_scale_root_matches_md3e_signature_family() {
        assertEquals(0.8f, SleepyPopupScaleStart, 0f)
    }

    @Test
    fun content_scale_root_matches_page_family_mirror() {
        assertEquals(0.92f, SleepyContentScaleStart, 0f)
    }

    @Test
    fun press_scale_is_subtle_contraction() {
        assertEquals(0.98f, SleepyPressScale, 0f)
        // 按压必须「几乎看不出缩、但摸得到」: 域 (0.9, 1.0) 开区间
        assertTrue("按压幅度不得小于 0.9 (抖动级) 或达到 1.0 (无反馈)",
            SleepyPressScale > 0.9f && SleepyPressScale < 1f)
    }

    @Test
    fun scale_roots_are_ordered_popup_below_content() {
        // 弹窗比页面「更远」→ 起始缩放更小; 反向即锚点写串
        assertTrue(SleepyPopupScaleStart < SleepyContentScaleStart)
    }
}

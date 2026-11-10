package com.lingion.sleepy.ui.component
import org.junit.Assert.assertEquals
import org.junit.Test

class IntRangeSliderVerticalDragTest {
    private val slop = 8f

    @Test
    fun vertical_frame_is_vetoed_page_and_thumb_frozen() {
        // 下滑躲遮挡的一帧: 纵向占优 — 拦 (滑块位移读 0, 页面不滚)
        assertEquals(SliderFrameDecision.Veto, sliderFrameDecision(0f, 20f))
        assertEquals(SliderFrameDecision.Veto, sliderFrameDecision(0f, -20f))
    }

    @Test
    fun diagonal_down_dominant_frame_is_vetoed() {
        // 斜着往下: |y| > |x| — 拦这一帧
        assertEquals(SliderFrameDecision.Veto, sliderFrameDecision(3f, 15f))
    }

    @Test
    fun horizontal_frames_pass_even_after_vertical_detour() {
        // 等效性核心: (1,1)→(1,-1) 的竖帧被拦后, (1,-1)→(0,-1) 的横帧必须放行 —
        // 手指"先下后左"与"直接左移"到达同一档位
        assertEquals(SliderFrameDecision.PassToSlider, sliderFrameDecision(-20f, 2f))
        assertEquals(SliderFrameDecision.PassToSlider, sliderFrameDecision(-20f, 0f))
    }

    @Test
    fun diagonal_forward_dominant_frame_passes() {
        // 斜着往右: |x| 占优 — 放行, RangeSlider 正常累积
        assertEquals(SliderFrameDecision.PassToSlider, sliderFrameDecision(30f, 8f))
        // 45° 平局: 主功能 (滑块) 优先
        assertEquals(SliderFrameDecision.PassToSlider, sliderFrameDecision(15f, 15f))
    }

    @Test
    fun tiny_vertical_jitter_vetoed_tiny_horizontal_drift_passes() {
        // 拇指按住不动的竖向轻抖: 不许滚页 → 拦
        assertEquals(SliderFrameDecision.Veto, sliderFrameDecision(1f, 10f))
        // 轻点前的横向小漂移: RangeSlider 靠未消费横移判 tap, 不许拦
        assertEquals(SliderFrameDecision.PassToSlider, sliderFrameDecision(3f, 0f))
        // 原地不动帧: 放行 (交给 RangeSlider 的 tap 路径, 拦了反而干扰)
        assertEquals(SliderFrameDecision.PassToSlider, sliderFrameDecision(0f, 0f))
    }

    @Test
    fun below_slop_vertical_still_vetoed_above_slop_tie_passes() {
        // |y| > |x| → 竖占优, 拦
        assertEquals(SliderFrameDecision.Veto, sliderFrameDecision(2f, slop + 0.1f))
        // |x| 更大 → 横占优, 放
        assertEquals(SliderFrameDecision.PassToSlider, sliderFrameDecision(12f, 9f))
    }

    @Test
    fun slow_vertical_frames_below_slop_must_still_be_vetoed() {
        // 真机反馈 2026-10-27 三次修正根因: 慢速匀速下滑每帧位移远小于设备
        // touchSlop (Mate30Pro density 2.55 → ≈46px)。幅度闸放行 → LazyColumn 与
        // RangeSlider 按压两个跨帧累计器双双累计越线 → 页面照滚。裁决必须只看
        // 方向占优、不看幅度 — 纵向占优的寸移也拦。
        assertEquals(SliderFrameDecision.Veto, sliderFrameDecision(1f, 3f))
        assertEquals(SliderFrameDecision.Veto, sliderFrameDecision(0f, 5f))
        assertEquals(SliderFrameDecision.Veto, sliderFrameDecision(2f, 4f))
        // 反向寸移 (快滑时的微小纵向回抖) 同样拦不住横向主路径: 横占优即放行
        assertEquals(SliderFrameDecision.PassToSlider, sliderFrameDecision(9f, 2f))
    }
}

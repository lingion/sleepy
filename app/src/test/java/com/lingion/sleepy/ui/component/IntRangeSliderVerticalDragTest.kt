package com.lingion.sleepy.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * IntRangeSlider 纵向手势裁决契约 (需求 2026-10-27 三次修正定形): 手指碰到滑杆的
 * 那一刹那起, 到抬手为止, 整页绝对冻结 — 与方向、幅度无关 (Main pass 无条件消费
 * 每帧); 滑杆只认横向分量 (Initial pass 拦纵向占优帧, RangeSlider 位移读 0),
 * 路径 (1,1)→(1,-1)→(0,-1) 与直接 (1,1)→(0,-1) 对滑块等效。
 * 契约: 逐帧增量裁决 (非累积!) 只看方向占优、不看幅度 (慢速下滑每帧位移远小于
 * 设备 slop, 幅度闸会让纵移漏进累计器)。旧版"横向帧放行给页面 + 拖动接管后静默"
 * 下斜拖帧的 Y 分量照滚页, 与"摸到即全局冻住"不符, 已废弃。
 * UI 接线走源码级守卫先例 (TodayDateNavWiringTest)。
 */
class IntRangeSliderVerticalDragTest {

    private val slop = 8f

    // ---- A. 纯函数裁决: 单帧增量 → 拦 (Initial 扣 Y) / 放 (交滑块) ----

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

    // ---- B. 源码级守卫: Main 绝对冻结 + Initial 扣 Y ----

    private fun findUpward(rel: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, rel)
            if (f.exists()) return f
            dir = dir.parentFile
        }
        error("$rel not found")
    }

    private fun countOccurrences(src: String, pattern: String): Int =
        Regex(Regex.escape(pattern)).findAll(src).count()

    @Test
    fun freeze_locks_page_on_main_pass_and_filters_y_on_initial_pass() {
        val src = findUpward("app/src/main/java/com/lingion/sleepy/ui/component/IntRangeSlider.kt").readText()
        // Main pass = 绝对冻结: 每帧无条件整体消费, 外层滚动容器寸步不得 (两协程各一处)
        assertEquals(1, countOccurrences(src, "awaitPointerEvent(PointerEventPass.Main)"))
        assertEquals(2, countOccurrences(src, "event.changes.forEach { it.consume() }"))
        // down 帧也吃 (首帧不留给外层累计)
        assertEquals(1, countOccurrences(src, "down.consume()"))
        // Initial pass = 按压期滑块 Y 免疫: 纵向占优帧先于子级消费
        assertEquals(1, countOccurrences(src, "awaitPointerEvent(PointerEventPass.Initial)"))
        // 接管检测必须保留: 拖动期 Initial 再拦纵帧会杀 drag() 的 motionConsumed
        // (子级读取时刻 isConsumed → 拖动取消), 接管后 Initial 整体静默。
        // "if (!dragTakenOver)" 全文只许出现 1 次 = 只在 Initial 协程; Main 协程的
        // 冻结消费不许带任何条件 (旧版据此静默 → 接管前横帧 Y 漏给页面, 已废弃)
        assertEquals(1, countOccurrences(src, "if (change.isConsumed && !vetoedThisFrame) dragTakenOver = true"))
        assertEquals(1, countOccurrences(src, "if (!dragTakenOver) {"))
        // PointerInputScope 不实现 CoroutineScope, 兄弟协程须套 coroutineScope (material3 RangeSlider 同款)
        assertEquals(1, countOccurrences(src, "coroutineScope {"))
        assertEquals(1, countOccurrences(src, "import kotlinx.coroutines.coroutineScope"))
        // 增量取法必须逐帧 (position - previousPosition), 禁回 down 起点累积
        assertEquals(1, countOccurrences(src, "change.position.x - change.previousPosition.x"))
        assertEquals(1, countOccurrences(src, "change.position.y - change.previousPosition.y"))
        assertEquals(2, countOccurrences(src, "sliderFrameDecision("))
        // 接线改名守卫: 冻结语义的旧名不得复活
        assertEquals(0, countOccurrences(src, "sliderBlockVerticalScroll"))
        assertEquals(0, countOccurrences(src, "sliderDragAxis"))
        // 幅度闸守卫: 单帧位移与 slop 的比较不得复活
        assertEquals(0, countOccurrences(src, "touchSlop"))
        // 单档退化域 (静态读数, 无滑杆) 不得冻结 — 那只是普通文本, 拖它就该滚页面
        assertEquals(1, countOccurrences(src, "if (hasTwoStops) modifier.sliderFreezePageScroll() else modifier"))
        assertEquals(0, countOccurrences(src, "sliderBlockPageScroll"))
    }
}

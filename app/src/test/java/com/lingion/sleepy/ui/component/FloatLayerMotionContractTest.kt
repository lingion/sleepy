package com.lingion.sleepy.ui.component

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 浮起层动效契约 (MD3E motion 全量补齐 Task 4, 2026-10-29):
 *   1. 弹回不再瞬移 — springFloatBack 只收 dragging 并置 returnTick, 位置由宿主
 *      LaunchedEffect 驱动的 animateReturnToOrigin(defaultSpatialSpec) 弹簧落回原点
 *      (函数名终与行为一致); 瞬时写回 originX/originY 禁止复活 — D 契约测试的
 *      精确相等断言在 runOnIdle 排空后由「动画收敛到目标值」保证, 不靠同帧写死。
 *   2. 遮罩 fadeIn/fadeOut 走 motionScheme fastEffects — 且 API 分档兜底值
 *      (26-30 = 0.35, 31+ = 0.10) 是用户实测定值, 抽成纯函数 scrimAlphaFor 锁死。
 *   3. 网格模糊半径 0→20dp 经 animateFloatAsState(defaultSpatialSpec) 缓动,
 *      gridFloatBlur 改为收半径参数(纯渲染), 判据抽成 floatBlurRadiusDp 纯函数。
 *   4. 浮框/卡片入场 = sleepyPopup 家族 (scale 0.8 + fast 对); 浮框退场 scale+fade,
 *      卡片收层瞬时移除 (brief 只为卡片定入场); 胶囊同 popup 家族。
 *      遮罩/浮框/卡片/胶囊全部经 AnimatedVisibility — 排空后节点删除语义保留,
 *      CourseOccurrenceFloatDragTest 的 assertDoesNotExist 网不许破。
 *   5. testTag("float-backdrop"/"float-popup"/"float-card") 在三处行为测试里按 tag
 *      寻址, 过渡改造后必须原样存活。
 *   6. 两文件禁原始 tween()/spring() — 规格全走 motionScheme 家族 (T2 同款禁令)。
 *
 * 载体形态: 弹簧收敛/淡入时序用 Robolectric 断言只能测「排空后的终态」(既有
 * CourseOccurrenceFloatDragTest 已覆盖), 「过程是否经过动画」由源码结构锁定 —
 * 与 MainTabsTransitionContractTest 同理: 声明式接线读源头文件等价于读编译产物。
 */
class FloatLayerMotionContractTest {

    private fun loadSource(vararg relPaths: String): String =
        sequenceOf(
            java.io.File("app/src/main/java/com/lingion/sleepy/ui/component/"),
            java.io.File("src/main/java/com/lingion/sleepy/ui/component/")
        ).firstOrNull { it.isDirectory }?.let { root ->
            relPaths.map { java.io.File(root, it) }.firstOrNull { it.isFile }?.readText()
        } ?: error("Unable to load sources: ${relPaths.joinToString()}")

    private val floatSource: String by lazy { loadSource("FloatOccurrence.kt") }
    private val tableSource: String by lazy { loadSource("CourseTableView.kt") }

    // ---- 契约 2: 遮罩兜底不透明度纯决策 ----

    @Test
    fun scrim_alpha_keeps_api_gated_fallback_values() {
        assertEquals("API 26-30 无 RenderEffect, 压暗兜底 0.35", 0.35f, scrimAlphaFor(26), 0f)
        assertEquals(0.35f, scrimAlphaFor(30), 0f)
        assertEquals("API 31+ 有模糊, 遮罩让位 0.10", 0.10f, scrimAlphaFor(31), 0f)
        assertEquals(0.10f, scrimAlphaFor(35), 0f)
    }

    // ---- 契约 3: 模糊半径纯决策 ----

    @Test
    fun blur_radius_target_only_when_lifted_and_resting() {
        assertEquals(20.dp, floatBlurRadiusDp(active = true, dragging = false))
        assertEquals("拖拽中网格复原不糊", 0.dp, floatBlurRadiusDp(active = true, dragging = true))
        assertEquals(0.dp, floatBlurRadiusDp(active = false, dragging = false))
        assertEquals(0.dp, floatBlurRadiusDp(active = false, dragging = true))
    }

    // ---- 契约 1: 弹簧归位接线 ----

    @Test
    fun springFloatBack_defers_position_to_animatable_spring() {
        val body = Regex("""fun springFloatBack\(\)[^{]*\{([^}]*)\}""")
            .find(tableSource)?.groupValues?.get(1)
            ?: error("springFloatBack() 未在 CourseTableView.kt 中找到")
        assertTrue("springFloatBack 必须经 requestReturnToOrigin/returnTick 触发弹簧归位",
            body.contains("requestReturnToOrigin") || body.contains("returnTick"))
        assertFalse("springFloatBack 不得再瞬时写回 originX (位置归弹簧管)",
            body.contains("originX"))
        assertFalse("springFloatBack 不得再瞬时写回 originY",
            body.contains("originY"))
        assertTrue("宿主必须以 LaunchedEffect(floatState.returnTick) 重启归位",
            Regex("""LaunchedEffect\(\s*floatState\.returnTick""").containsMatchIn(tableSource))
        assertTrue("归位必须调用 animateReturnToOrigin",
            Regex("""animateReturnToOrigin\(""").containsMatchIn(tableSource))
        assertTrue("CourseFloatState 必须持有 Animatable 弹簧",
            Regex("""Animatable\(""").containsMatchIn(floatSource))
        assertTrue("状态必须暴露 suspend animateReturnToOrigin",
            Regex("""suspend fun animateReturnToOrigin""").containsMatchIn(floatSource))
    }

    // ---- 契约 4: AnimatedVisibility 过渡家族接线 ----

    @Test
    fun float_layer_uses_animated_visibility_and_popup_family() {
        assertTrue("遮罩/卡片/浮框/胶囊四处过渡必须全经 AnimatedVisibility, 实得 ${
            Regex("""AnimatedVisibility\(""").findAll(tableSource).count()
        }",
            Regex("""AnimatedVisibility\(""").findAll(tableSource).count() >= 4)
        assertTrue("浮框/卡片入场必须用 sleepyPopupEnter 工厂 (不得手抄规格)",
            tableSource.contains("sleepyPopupEnter()"))
        assertTrue("浮框退场必须用 sleepyPopupExit 工厂",
            tableSource.contains("sleepyPopupExit()"))
        assertTrue("遮罩淡入淡出必须走 motionScheme fastEffects",
            tableSource.contains("fastEffectsSpec") &&
                Regex("""fadeIn\(""").containsMatchIn(tableSource) &&
                Regex("""fadeOut\(""").containsMatchIn(tableSource))
        assertTrue("浮框显隐判据 = 浮起且不拖拽 (拖起即退场)",
            Regex("""visible\s*=\s*floatState\.active\s*&&\s*!floatState\.dragging""")
                .containsMatchIn(tableSource))
    }

    // ---- 契约 3(下半): 模糊经 animateFloatAsState 缓动 ----

    @Test
    fun grid_blur_radius_is_animated_not_switched() {
        assertTrue("模糊半径必须 animateFloatAsState 缓动",
            Regex("""animateFloatAsState\(""").containsMatchIn(tableSource))
        assertTrue("缓动规格必须来自 motionScheme defaultSpatial",
            tableSource.contains("defaultSpatialSpec"))
        assertTrue("gridFloatBlur 必须改为收 Float 半径的纯渲染函数",
            Regex("""internal fun Modifier\.gridFloatBlur\(\s*\w+\s*:\s*Float""")
                .containsMatchIn(floatSource))
    }

    // ---- 契约 5: tag 存活 ----

    @Test
    fun float_layer_test_tags_survive_the_transitions() {
        assertTrue(tableSource.contains("""testTag("float-backdrop")"""))
        assertTrue(floatSource.contains("""testTag("float-popup")"""))
        assertTrue(floatSource.contains("""testTag("float-card")"""))
    }

    // ---- 契约 6: 禁原始规格 ----

    @Test
    fun float_layer_has_no_raw_tween_or_spring_specs() {
        for ((name, src) in listOf("CourseTableView.kt" to tableSource, "FloatOccurrence.kt" to floatSource)) {
            assertFalse("$name 不得写原始 tween() — 用 motionScheme 家族",
                Regex("""\btween\(""").containsMatchIn(src))
            assertFalse("$name 不得写原始 spring() — 用 motionScheme 家族",
                Regex("""\bspring\(""").containsMatchIn(src))
        }
    }
}

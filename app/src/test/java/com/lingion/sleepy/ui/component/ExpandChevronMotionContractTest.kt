package com.lingion.sleepy.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 展开/折叠家族与 chevron 旋转统一契约 (MD3E motion 全量补齐 Task 7a, 2026-10-29):
 *
 *   1. 全应用 AnimatedVisibility 纵向展开站点一律显式传 sleepyExpandSpecs()
 *      (T1 工厂; T5 rowExpand 先例) — 尺寸 fastSpatial、淡入 defaultEffects、
 *      淡出 fastEffects 全走 motionScheme, 禁裸 expandVertically()/shrinkVertically()
 *      默认 spring (与 Expressive 规格脱节)。站点清单 (8 处): SettingsCards、
 *      AddCourseScreen(非常规折叠栏)、ImportSheet(从文本导入, 旧版无 fadeIn —
 *      工厂补齐淡入)、EditTableScreen、GeneralSettingsScreen(旧全限定名一并转正)、
 *      PeriodTableEditScreen、LicenseScreen(导语卡+致谢卡两处)。
 *   2. 全部 5 处 ExpandMore chevron 一律 animateFloatAsState 0→180 defaultSpatial
 *      + Modifier.rotate — 禁 rotate(if (expanded) 180f else 0f) 瞬时硬翻。
 *   3. 反向闸: 展开家族不许退化为 animateContentSize 常驻 alpha 或 if 直挂 —
 *      站点文件里 AnimatedVisibility 计数只增不减。
 *
 * 载体形态: 7a 全部是纯 UI 折叠站点, 按 T3-T6 定型先例用源码结构锁定;
 * SettingsCard (公开组件、无 VM 依赖) 的行为终态另有 Robolectric 管线测试
 * SettingsCardExpandPipelineTest。AboutLicenseAttributionTest 的旧字面量锁
 * (enter = expandVertically() + fadeIn()) 随本 commit lockstep 改写。
 */
class ExpandChevronMotionContractTest {

    private fun loadSource(relPath: String): String =
        sequenceOf(
            File("app/src/main/java/com/lingion/sleepy/"),
            File("src/main/java/com/lingion/sleepy/")
        ).firstOrNull { it.isDirectory }?.let { root ->
            File(root, relPath).takeIf { it.isFile }?.readText()
        } ?: error("Unable to load $relPath")

    private val settingsCards: String by lazy { loadSource("ui/component/SettingsCards.kt") }
    private val addCourse: String by lazy { loadSource("ui/screen/edit/AddCourseScreen.kt") }
    private val importSheet: String by lazy { loadSource("ui/screen/imports/ImportSheet.kt") }
    private val editTable: String by lazy { loadSource("ui/screen/mine/EditTableScreen.kt") }
    private val generalSettings: String by lazy { loadSource("ui/screen/mine/GeneralSettingsScreen.kt") }
    private val periodTableEdit: String by lazy { loadSource("ui/screen/mine/PeriodTableEditScreen.kt") }
    private val license: String by lazy { loadSource("ui/screen/mine/LicenseScreen.kt") }

    /** 每文件: sleepyExpandSpecs() 调用 >=1 且 enter/exit 字段成对消费 */
    private fun assertExpandFamily(src: String, name: String, expectSites: Int) {
        assertTrue(
            "$name 必须取 sleepyExpandSpecs() (T1 工厂, 单一调参点)",
            Regex("""sleepyExpandSpecs\(\)""").containsMatchIn(src)
        )
        assertEquals(
            "$name 展开站点数应为 $expectSites (AnimatedVisibility 只增不减)",
            expectSites,
            Regex("""AnimatedVisibility\(""").findAll(src).count()
        )
        // 工厂结果必须先算后捕获再进 enter/exit 槽 (transitionSpec lambda 非
        // composable 的 T2 教训对 @Composable 工厂同样成立的统一写法)
        assertTrue(
            "$name 的 AV 必须消费工厂字段 enter = xxx.enter / exit = xxx.exit",
            Regex("""enter = \w+\.enter""").containsMatchIn(src) &&
                Regex("""exit = \w+\.exit""").containsMatchIn(src)
        )
    }

    // ---- 契约 1: 八个展开站点全走 sleepyExpandSpecs ----

    @Test
    fun all_expand_sites_consume_sleepy_expand_specs() {
        assertExpandFamily(settingsCards, "SettingsCards", expectSites = 1)
        assertExpandFamily(addCourse, "AddCourseScreen", expectSites = 1)
        assertExpandFamily(importSheet, "ImportSheet", expectSites = 1)
        assertExpandFamily(editTable, "EditTableScreen", expectSites = 1)
        assertExpandFamily(generalSettings, "GeneralSettingsScreen", expectSites = 1)
        assertExpandFamily(periodTableEdit, "PeriodTableEditScreen", expectSites = 1)
        assertExpandFamily(license, "LicenseScreen", expectSites = 2)
    }

    @Test
    fun no_bare_expand_literals_remain_anywhere() {
        val all = listOf(
            settingsCards, addCourse, importSheet, editTable,
            generalSettings, periodTableEdit, license,
        )
        for (src in all) {
            assertFalse(
                "不得残留裸 expandVertically() 调用 (默认 spring 与 Expressive 脱节)",
                Regex("""expandVertically\(\)""").containsMatchIn(src)
            )
            assertFalse(
                "不得残留裸 shrinkVertically() 调用",
                Regex("""shrinkVertically\(\)""").containsMatchIn(src)
            )
            assertFalse(
                "不得残留 import expandVertically/shrinkVertically (站点文件内零引用)",
                Regex("""import androidx\.compose\.animation\.(expand|shrink)Vertically""").containsMatchIn(src)
            )
        }
    }

    // ---- 契约 2: 五处 chevron 全走 animateFloatAsState defaultSpatial ----

    private fun assertChevron(src: String, name: String) {
        assertTrue(
            "$name chevron 必须 animateFloatAsState 目标 180f",
            Regex("""animateFloatAsState\([\s\S]{0,120}?180f""").containsMatchIn(src)
        )
        assertTrue(
            "$name chevron 旋转规格必须显式 defaultSpatialSpec (motionScheme)",
            Regex("""animateFloatAsState\([\s\S]{0,160}?defaultSpatialSpec""").containsMatchIn(src)
        )
        assertFalse(
            "$name 不得残留 rotate(if ...) 瞬时硬翻",
            Regex("""rotate\(if \(""").containsMatchIn(src)
        )
    }

    @Test
    fun all_five_chevrons_animate_rotation() {
        assertChevron(settingsCards, "SettingsCards")
        assertChevron(addCourse, "AddCourseScreen")
        assertChevron(editTable, "EditTableScreen")
        assertChevron(generalSettings, "GeneralSettingsScreen")
        assertChevron(periodTableEdit, "PeriodTableEditScreen")
    }

    // ---- 契约 3: 折叠显隐仍归 AnimatedVisibility, 禁 if 直挂回潮 ----
    // (站点级 AnimatedVisibility 计数已在 assertExpandFamily 锁死; 此处反向
    //  锁 EditTable/PeriodTable/GeneralSettings 三处折叠头没有 if(expanded) 内容块)

    @Test
    fun collapse_content_never_mounted_via_if() {
        for ((src, name) in listOf(
            editTable to "EditTableScreen",
            periodTableEdit to "PeriodTableEditScreen",
            generalSettings to "GeneralSettingsScreen",
        )) {
            // `) \{` 只锁"直接挂内容块"形态; 折叠头摘要里的 if(expanded) A else B
            // 三元 (收起/展开文案、旋转角) 是合法消费, 不在射程
            assertFalse(
                "$name 折叠内容禁 if(xxxExpanded) { ... } 直挂 (砍掉退场动画)",
                Regex("""if \((timeSlots|language)Expanded\) \{""").containsMatchIn(src)
            )
        }
    }
}

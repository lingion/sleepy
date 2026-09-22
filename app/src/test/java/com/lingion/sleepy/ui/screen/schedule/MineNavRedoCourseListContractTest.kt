package com.lingion.sleepy.ui.screen.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2026-09-21 用户令: 4 点行为契约 — 锁实现,以防后续重构破坏新行为。
 *
 *  A. 管理页「当前课表摘要卡」整张可点 → 所有课表页(用户直觉:点开就有课表列表)
 *  B. 我的页「课表数」卡可点 → 所有课表;「课程数」卡可点 → 新课程清单页;
 *     「周数」卡静态(无 onClick)
 *  C. 课表主页 TopBar 撤回/取消撤回合胶囊: 一体显隐(hasUndo||hasRedo 才挂载),
 *     体育场形状(CircleShape+两半 32dp)+中缝 1dp 淡淡竖线
 *  D. 课程清单(CourseListScreen)路由+导航入口齐全; 课程清单按 courseName 聚合,
 *     课名空时按 groupId 兜底; 空态显示 course_list_empty
 *  E. 6 locale 必须含 schedule_redo / schedule_redo_none / manage_view_all_tables
 *     / course_list_{title,subtitle,empty,arrangements}
 *  F. Navigator 必须有 openCourseList 入口(由 NavHostMigrationContractTest 验 15 个)
 *  G. UndoManagerTest 提供 redo 互斥/新写清 redo/clear 双清等独立用例,本类不重复
 *
 *  仓库无 Robolectric — 与其他契约测试同风格,源文件 token 扫描。
 */
class MineNavRedoCourseListContractTest {

    private fun findUpward(rel: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, rel)
            if (f.exists()) return f
            dir = dir.parentFile
        }
        error("$rel not found")
    }

    private val scheduleScreen: String by lazy {
        findUpward("app/src/main/java/com/lingion/sleepy/ui/screen/schedule/ScheduleScreen.kt").readText()
    }
    private val managementPage: String by lazy {
        findUpward("app/src/main/java/com/lingion/sleepy/ui/screen/manage/ManagementPage.kt").readText()
    }
    private val mineScreen: String by lazy {
        findUpward("app/src/main/java/com/lingion/sleepy/ui/screen/mine/MineScreen.kt").readText()
    }
    private val courseListScreen: String by lazy {
        findUpward("app/src/main/java/com/lingion/sleepy/ui/screen/mine/CourseListScreen.kt").readText()
    }
    private val routes: String by lazy {
        findUpward("app/src/main/java/com/lingion/sleepy/ui/nav/SleepyRoutes.kt").readText()
    }
    private val navigator: String by lazy {
        findUpward("app/src/main/java/com/lingion/sleepy/ui/nav/SleepyNavigator.kt").readText()
    }
    private val noRipple: String by lazy {
        findUpward("app/src/main/java/com/lingion/sleepy/ui/theme/NoRippleClickable.kt").readText()
    }
    private fun stringsFor(loc: String): String =
        findUpward("app/src/main/res/$loc/strings.xml").readText()

    // ---- A. 管理页: 当前表卡可点 → 所有课表 ----

    @Test
    fun `current table card on management page is clickable and routes to AllTables`() {
        // 卡内必须有 noRippleClickable(onOpenAllTables) 整卡包裹
        val m = Regex("""noRippleClickable\(\s*onOpenAllTables\s*\)""").findAll(managementPage).toList()
        assertTrue(
            "管理页必须有 noRippleClickable(onOpenAllTables) 整卡点击,且至少出现 1 次," +
                "实际 ${m.size}",
            m.isNotEmpty()
        )
    }

    @Test
    fun `management page declares onOpenAllTables parameter`() {
        assertTrue(
            "ManagementPage 形参列表必须有 onOpenAllTables: () -> Unit = {}",
            Regex("""onOpenAllTables:\s*\(\)\s*->\s*Unit\s*=\s*\{\}""").containsMatchIn(managementPage)
        )
    }

    // ---- B. 我的页: StatsCard 课表数/课程数可点,周数格静态 ----

    @Test
    fun `mine screen StatsCard wires tables and courses callbacks, week is static`() {
        // 调 StatsCard 必须传 onOpenTables + onOpenCourses
        val callCount = Regex(
            """StatsCard\(\s*[\s\S]*?onOpenTables\s*=\s*onOpenAllTables[\s\S]*?onOpenCourses\s*=\s*onOpenCourseList"""
        ).findAll(mineScreen).toList().size
        assertTrue(
            "MineScreen 调用 StatsCard 必须同时传 onOpenTables=onOpenAllTables 和 onOpenCourses=onOpenCourseList,实际 $callCount",
            callCount >= 1
        )
        // 周数格 StatItem 必须只 3 参数,无 onClick(静态)
        // 允许第三参数为 null
        val weekStatic = Regex(
            """StatItem\(\s*value\s*=\s*week\.toString\(\)\s*,\s*label\s*=\s*stringResource\(R\.string\.mine_stat_week\)\s*\)"""
        ).containsMatchIn(mineScreen)
        assertTrue("周数格 StatItem 必须无 onClick 形参(静态),实际:$weekStatic", weekStatic)
    }

    @Test
    fun `mine screen declares onOpenCourseList parameter`() {
        assertTrue(
            "MineScreen 必须新增 onOpenCourseList: () -> Unit = {} 形参",
            Regex("""onOpenCourseList:\s*\(\)\s*->\s*Unit\s*=\s*\{\}""").containsMatchIn(mineScreen)
        )
    }

    @Test
    fun `StatItem overload supports onClick nullable`() {
        assertTrue(
            "StatItem 必须有 3 参重载, 第三参 onClick: (() -> Unit)? = null",
            Regex("""fun\s+StatItem\([\s\S]{0,200}?onClick:\s*\(\(\)\s*->\s*Unit\)\?\s*=\s*null""")
                .containsMatchIn(mineScreen)
        )
    }

    // ---- C. 课表页 TopBar 撤回/取消撤回一体胶囊 ----

    @Test
    fun `schedule screen undo redo capsule is paired via single component`() {
        // 必须有一个 private/composable 函数 UndoRedoCapsule(showUndo, showRedo, onUndo, onRedo)
        assertTrue(
            "ScheduleScreen 必须声明 private UndoRedoCapsule(showUndo, showRedo, onUndo, onRedo)",
            Regex("""fun\s+UndoRedoCapsule\(\s*showUndo:\s*Boolean\s*,\s*showRedo:\s*Boolean\s*,\s*onUndo:\s*\(\)\s*->\s*Unit\s*,\s*onRedo:\s*\(\)\s*->\s*Unit\s*\)""")
                .containsMatchIn(scheduleScreen)
        )
    }

    @Test
    fun `schedule screen TopBar mounts capsule only when undo or redo exists`() {
        // 挂载条件:hasUndo = UndoManager.hasSnapshot;hasRedo = UndoManager.hasRedoSnapshot
        // 单一 if 块内挂胶囊(同一显隐)
        val pattern = Regex(
            """val\s+hasUndo\s*=\s*[\w.]*UndoManager\.hasSnapshot\s*[\s\S]{0,200}?val\s+hasRedo\s*=\s*[\w.]*UndoManager\.hasRedoSnapshot\s*[\s\S]{0,200}?if\s*\(hasUndo\s*\|\|\s*hasRedo\)"""
        )
        assertTrue(
            "ScheduleScreen TopBar 必须先取 hasUndo/hasRedo 再 if(hasUndo||hasRedo) 挂胶囊(配对显隐)",
            pattern.containsMatchIn(scheduleScreen)
        )
    }

    @Test
    fun `schedule screen TopBar signature includes onRedo`() {
        assertTrue(
            "TopBar 函数签名必须新增 onRedo: () -> Unit 形参",
            Regex("""onRedo:\s*\(\)\s*->\s*Unit""").containsMatchIn(scheduleScreen)
        )
    }

    @Test
    fun `UndoRedoCapsule uses stadium shape CircleShape with two 32dp halves and 1dp divider`() {
        // 体育场形状 = Row 整体 clip(CircleShape)
        // 中缝 Box 宽 1dp
        // 左半/右半各自 32dp 宽
        val capsuleBlock = Regex(
            """fun\s+UndoRedoCapsule\([\s\S]*?\n\}"""
        ).find(scheduleScreen)?.value ?: error("UndoRedoCapsule 找不到")
        assertTrue("胶囊外 Row 必须 clip(CircleShape)", capsuleBlock.contains(".clip(CircleShape)"))
        assertTrue("中缝 Box 宽必须是 1.dp", capsuleBlock.contains(".size(width = 1.dp"))
        val halves = Regex("""\.size\(width\s*=\s*32\.dp,\s*height\s*=\s*32\.dp\)""")
            .findAll(capsuleBlock).count()
        assertEquals("胶囊必须有两半(各 32dp×32dp)Box", 2, halves)
    }

    @Test
    fun `noRippleClickable supports enabled overload for capsule disabled halves`() {
        // 撤回/取消撤回 一半 disable 时 onClick 不响应
        // 用 overload fun Modifier.noRippleClickable(enabled: Boolean, onClick: () -> Unit)
        assertTrue(
            "noRippleClickable 必须新增 (enabled: Boolean, onClick: () -> Unit) 重载",
            Regex(
                """fun\s+Modifier\.noRippleClickable\(\s*enabled:\s*Boolean\s*,\s*onClick:\s*\(\)\s*->\s*Unit\s*\)"""
            ).containsMatchIn(noRipple)
        )
    }

    // ---- D. 课程清单路由 + 导航入口 + 聚合/空态 ----

    @Test
    fun `routes declare CourseList data object`() {
        assertTrue(
            "SleepyRoutes 必须新增 @Serializable data object CourseList : SleepyRoute",
            Regex("""@Serializable\s+data\s+object\s+CourseList\s*:\s*SleepyRoute""").containsMatchIn(routes)
        )
    }

    @Test
    fun `navigator exposes openCourseList`() {
        assertTrue(
            "SleepyNavigator 必须新增 fun openCourseList() = push(SleepyRoute.CourseList)",
            Regex("""fun\s+openCourseList\(\)\s*=\s*push\(SleepyRoute\.CourseList\)""")
                .containsMatchIn(navigator)
        )
    }

    @Test
    fun `course list screen groups by courseName with empty-name fallback`() {
        // groupBy { it.courseName.ifBlank { it.groupId } }
        assertTrue(
            "CourseListScreen 必须 groupBy { it.courseName.ifBlank { it.groupId } } 按课程名聚合," +
                "空名按 groupId 兜底",
            courseListScreen.contains("it.courseName.ifBlank { it.groupId }")
        )
    }

    @Test
    fun `course list screen renders empty state with course_list_empty`() {
        // grouped.isEmpty 分支必须显示 course_list_empty 文案
        val emptyBranch = Regex(
            """if\s*\(grouped\.isEmpty\(\)\)[\s\S]{0,400}?course_list_empty"""
        ).containsMatchIn(courseListScreen)
        assertTrue("空态必须显示 R.string.course_list_empty", emptyBranch)
    }

    // ---- E. 6 locale 字符串齐备 ----

    @Test
    fun `six locales contain redo and course list strings`() {
        val keys = listOf(
            "schedule_redo",
            "schedule_redo_none",
            "manage_view_all_tables",
            "course_list_title",
            "course_list_subtitle",
            "course_list_empty",
            "course_list_arrangements"
        )
        val locs = listOf("values", "values-en", "values-es", "values-ja", "values-zh-rCN", "values-zh-rTW")
        for (loc in locs) {
            val s = stringsFor(loc)
            for (k in keys) {
                assertTrue(
                    "locale=$loc 缺少 string key=$k",
                    Regex("""<string\s+name="${k}">""").containsMatchIn(s)
                )
            }
        }
    }

    @Test
    fun `table_info string relabeled to arrangement count in CN zh-rCN and zh-rTW`() {
        // 中文三 locale: 旧「%3$d门课」必须改成「%3$d个上课安排 / 个上課安排」
        // ja 保留 コマ 原状, en/es/cl 不在改列
        val zhs = listOf("values", "values-zh-rCN", "values-zh-rTW")
        for (loc in zhs) {
            val s = stringsFor(loc)
            val m = Regex("""<string\s+name="table_info">([^<]+)</string>""").find(s)?.groupValues?.get(1)
                ?: error("locale=$loc 缺 table_info")
            assertTrue(
                "locale=$loc table_info 必须含「%3\$d...个上课安排」/「%3\$d...個上課安排」," +
                    "实际:${m}",
                m.contains("个上课安排") || m.contains("個上課安排")
            )
            assertFalse(
                "locale=$loc table_info 仍含旧「门课」措辞 — 用户令要口径统一",
                m.contains("%3\$d门课") || m.contains("%3\$d門課")
            )
        }
    }
}
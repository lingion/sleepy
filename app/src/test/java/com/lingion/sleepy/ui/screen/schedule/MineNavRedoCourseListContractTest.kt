package com.lingion.sleepy.ui.screen.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2026-09-21 用户令: 4 点行为契约 — 锁实现,以防后续重构破坏新行为。
 *
 *  A. 管理页「当前课表摘要卡」左侧 → 编辑当前课表；仅「查看全部」→ 所有课表页。
 *     无课表时保留摘要和提示，禁用编辑入口。
 *  B. 我的页「课表数」卡可点 → 所有课表;「课程数」卡可点 → 新课程清单页;
 *     「作息表数」卡可点 → 作息表管理；下方不再重复显示课表、作息表和导出入口。
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

    // ---- A. 管理页: 左侧编辑当前课表，查看全部进入所有课表 ----

    @Test
    fun `current table summary edits current table and only view all opens AllTables`() {
        assertTrue(
            "摘要左侧应编辑当前课表，无课表时禁用编辑",
            Regex("""noRippleClickable\(\s*enabled\s*=\s*table\s*!=\s*null\s*,\s*onClick\s*=\s*onEditCurrentTable\s*\)""")
                .containsMatchIn(managementPage)
        )
        assertTrue(
            "查看全部按钮应单独连接所有课表入口",
            Regex("""TextButton\(\s*onClick\s*=\s*onOpenAllTables\s*,[\s\S]*?R\.string\.manage_view_all_tables""")
                .containsMatchIn(managementPage)
        )
        assertEquals(
            "onOpenAllTables 只应出现在回调声明和查看全部按钮中，禁止整卡跳转",
            2,
            Regex("""\bonOpenAllTables\b""").findAll(managementPage).count()
        )
    }

    @Test
    fun `management page declares onOpenAllTables parameter`() {
        assertTrue(
            "ManagementPage 必须要求调用方提供 onOpenAllTables 回调",
            Regex("""onOpenAllTables:\s*\(\)\s*->\s*Unit\s*,""").containsMatchIn(managementPage)
        )
    }

    // ---- B. 我的页: 三个统计格分别连接对应管理页 ----

    @Test
    fun `mine screen StatsCard wires tables courses and period tables callbacks`() {
        // 调 StatsCard 必须连接三类统计的导航回调。
        val callCount = Regex(
            """StatsCard\(\s*[\s\S]*?onOpenTables\s*=\s*onOpenAllTables[\s\S]*?onOpenCourses\s*=\s*onOpenCourseList[\s\S]*?onOpenPeriodTables\s*=\s*onOpenPeriodTables"""
        ).findAll(mineScreen).toList().size
        assertTrue(
            "MineScreen 调用 StatsCard 必须连接课表、课程和作息表入口，实际 $callCount",
            callCount >= 1
        )
        assertTrue("作息表数量必须订阅实际列表", mineScreen.contains("viewModel.allPeriodTables.collectAsState()"))
        assertTrue("统计卡必须使用作息表数量", mineScreen.contains("periodTableCount = periodTables.size"))
        val periodTablesClickable = Regex(
            """StatItem\(\s*value\s*=\s*periodTableCount\.toString\(\)\s*,\s*label\s*=\s*stringResource\(R\.string\.mine_stat_period_tables\)\s*,\s*onClick\s*=\s*onOpenPeriodTables\s*\)"""
        ).containsMatchIn(mineScreen)
        assertTrue("作息表数格必须进入作息表管理", periodTablesClickable)
        for (removedLabel in listOf("all_tables", "mine_period_tables", "mine_export")) {
            assertFalse("我的页不应保留重复列表入口：$removedLabel", mineScreen.contains("R.string.$removedLabel"))
        }
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
            "ScheduleScreen 必须声明 private UndoRedoCapsule(showUndo, showRedo, scale, onUndo, onRedo)",
            Regex("""fun\s+UndoRedoCapsule\(\s*showUndo:\s*Boolean\s*,\s*showRedo:\s*Boolean\s*,\s*scale:\s*Float\s*,\s*onUndo:\s*\(\)\s*->\s*Unit\s*,\s*onRedo:\s*\(\)\s*->\s*Unit\s*\)""")
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
    fun `UndoRedoCapsule preserves its default stadium and only yields space on collision`() {
        val capsuleBlock = Regex(
            """fun\s+UndoRedoCapsule\([\s\S]*?\n\}"""
        ).find(scheduleScreen)?.value ?: error("UndoRedoCapsule 找不到")
        assertTrue("胶囊外 Row 必须 clip(CircleShape)", capsuleBlock.contains(".clip(CircleShape)"))
        assertTrue("中缝 Box 宽必须是 1.dp", capsuleBlock.contains(".size(width = 1.dp"))
        assertTrue("胶囊默认半区必须保持 32dp", capsuleBlock.contains("val halfSize = 32.dp * scale"))
        assertTrue("胶囊比例必须由顶栏实际可用空间决定", scheduleScreen.contains("val capsuleScale"))
        assertTrue("空间足够时胶囊必须保持原尺寸", scheduleScreen.contains("coerceIn(0.65f, 1f)"))
        assertTrue("中央周导航必须提供实际位置用于防碰撞", scheduleScreen.contains("positionInParent().x"))

        val weekButtonBlock = Regex(
            """fun\s+WeekNavButton\([\s\S]*?\n\}"""
        ).find(scheduleScreen)?.value ?: error("WeekNavButton 找不到")
        assertTrue("其他顶栏按钮必须保持固定 32dp", weekButtonBlock.contains(".size(32.dp)"))
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
    fun `course list screen groups locations inside each course card`() {
        assertTrue("课程卡必须保留老师标题", courseListScreen.contains("teacher = rows.first().teacher"))
        assertTrue("课程卡必须按 room 分组地点", courseListScreen.contains("rows.groupBy { it.room.trim() }"))
        assertTrue("课程卡必须渲染地点安排", courseListScreen.contains("group.locations.forEach"))
    }

    @Test
    fun `week view filters empty days in single-column detail panel`() {
        val courseTableView = findUpward(
            "app/src/main/java/com/lingion/sleepy/ui/component/CourseTableView.kt"
        ).readText()
        val sortedDays = Regex("""val sortedDays = visibleDays\.sorted\(\)""")
            .find(courseTableView)
            ?: error("DetailPanel sortedDays declaration not found")
        val singleColumn = courseTableView.indexOf("// 单栏(或两栏下过滤后不足 2 天)")
        assertTrue("DetailPanel single-column branch must exist", singleColumn >= 0)
        val loop = courseTableView.indexOf("for (day in sortedDays)", singleColumn)
        assertTrue("single-column branch must render filtered sortedDays", loop > sortedDays.range.last)
    }

    @Test
    fun `course list screen renders empty state with course_list_empty`() {
        // grouped.isEmpty 分支必须显示 course_list_empty 文案
        val emptyBranch = Regex(
            """if\s*\(grouped\.isEmpty\(\)\)[\s\S]{0,400}?course_list_empty"""
        ).containsMatchIn(courseListScreen)
        assertTrue("空态必须显示 R.string.course_list_empty", emptyBranch)
    }

    @Test
    fun `adding a slot reuses the previous slot teacher and room`() {
        val addBlock = findUpward("app/src/main/java/com/lingion/sleepy/ui/screen/edit/AddCourseScreen.kt").readText()
        assertTrue("新增时段必须读取上一张卡", addBlock.contains("val previous = meetingBlocks.lastOrNull()"))
        assertTrue("新增时段必须复用上一张卡地点", addBlock.contains("room = previous?.roomState.orEmpty()"))
        assertTrue("新增时段必须复用上一张卡老师", addBlock.contains("teacher = previous?.teacherState.orEmpty()"))
    }

    // ---- E. 6 locale 字符串齐备 ----

    @Test
    fun `six locales contain redo and course list strings`() {
        val keys = listOf(
            "schedule_redo",
            "schedule_redo_none",
            "manage_view_all_tables",
            "mine_stat_period_tables",
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

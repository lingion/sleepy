package com.lingion.sleepy.ui.screen.schedule

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 无表空态 (EmptyState) 契约 — "第一张课表" 语义修复回归闸门。
 *
 * 用户反馈: 初始零课表时, EmptyState 的两个按钮语义错位 —
 *   1. "手动创建第一门课" 打开的是 AddCourseScreen, 而没有课表载体时"创建第一门课"
 *      无从谈起 (AddCourseScreen 保存时才偷偷 createEmptyTable(), 表名/作息全默认)。
 *      正确语义 = 走建表流 (createEmptyTable(commitSelection=false) → EditTable,
 *      与 AllTablesScreen.onCreateNewTable 同源)。
 *   2. "前往课表管理" 引导性不足, 应为 "导入第一张课表"。
 *
 * 本测试锁三点:
 *   A. ScheduleScreen 的 EmptyState 第二按钮不再接 onManualAdd(→AddCourse), 而是接建表流;
 *   B. EmptyState 专属新 string key (schedule_empty_* 前缀) 六语齐全, 且空态文案
 *      不再复用 NoCourseState (有表无课) 的"加课"语义 key;
 *   C. 空态跳转管理页自动弹导入面板 (autoShowImportSheet 引导)。
 *
 * 仓库无 Robolectric, 按 JwNeuWebViewContractTest / TodayDateNavHeaderWiringTest 的
 * 源码级守卫风格 (读源文件断言 token), NOT 意图匹配 — token 变化时本测试如实报错,
 * 由维护者同步更新。
 */
class EmptyStateFirstTableContractTest {

    // ---- 源文件定位 (findUpward, 兼容 app/ 与 worktree 子目录工作目录) ----

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

    private val mainActivity: String by lazy {
        findUpward("app/src/main/java/com/lingion/sleepy/MainActivity.kt").readText()
    }

    private val managementPage: String by lazy {
        findUpward("app/src/main/java/com/lingion/sleepy/ui/screen/manage/ManagementPage.kt").readText()
    }

    /** 全部发布语言 (与 AboutLicenseAttributionTest.ALL_RELEASED_LOCALES 同集)。 */
    private val locales = listOf(
        "values", "values-en", "values-es", "values-ja", "values-zh-rCN", "values-zh-rTW"
    )

    private fun readString(locale: String, key: String): String {
        val text = findUpward("app/src/main/res/$locale/strings.xml").readText()
        val regex = Regex("""<string\s+name="$key"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        return regex.find(text)?.groupValues?.get(1)?.trim().orEmpty()
    }

    // ---- A. EmptyState 接线: 建表流, 不是 AddCourse ----

    @Test
    fun `empty state second button routes to table creation flow not AddCourse`() {
        // EmptyState composable 区域: 从定义起, 到下一个 @Composable 或文件尾。
        val start = scheduleScreen.indexOf("private fun EmptyState(")
        assertTrue("ScheduleScreen.kt 必须定义 EmptyState composable", start >= 0)
        val region = scheduleScreen.substring(
            start,
            scheduleScreen.indexOf("@Composable", start + 1).takeIf { it > 0 }
                ?: scheduleScreen.length
        )

        // 空态第二按钮必须走建表流回调 (onCreateTable), 禁止复用加课回调名 onManualAdd。
        assertTrue(
            "EmptyState 必须声明建表流回调 (onCreateTable 类), " +
                "而非复用加课语义的 onManualAdd — 无表时'创建第一门课'无从谈起",
            Regex("""onCreateTable\w*\s*:\s*\(\)\s*->\s*Unit""").containsMatchIn(region)
        )
        assertFalse(
            "EmptyState 区域禁止再引用 onManualAdd (那是加课/NoCourseState 语义)",
            region.contains("onManualAdd")
        )
    }

    @Test
    fun `main activity wires empty state create-table callback to EditTable flow`() {
        // 与 AllTablesScreen.onCreateNewTable 同源: createEmptyTable(commitSelection = false)
        // → previousDefaultTableId/pendingNewTableId/editTableId → pushOverlay(EditTable)。
        // ScheduleScreen 调用点的 onCreateTable 参数必须复用这个闭包 (onCreateNewTable)。
        val scheduleCall = Regex("""ScheduleScreen\(([\s\S]*?)\)\s*$""", RegexOption.MULTILINE)
            .find(mainActivity)?.groupValues?.get(1).orEmpty()
        assertTrue(
            "MainActivity 的 ScheduleScreen(...) 调用必须存在", scheduleCall.isNotEmpty()
        )
        assertTrue(
            "ScheduleScreen 空态建表回调必须复用 MainTabs.onCreateNewTable 闭包 " +
                "(createEmptyTable(commitSelection=false) → EditTable), 不得开 AddCourse",
            Regex("""onCreateTable\w*\s*=\s*onCreateNewTable""").containsMatchIn(scheduleCall)
        )
        // NoCourseState/TopBar 仍合法使用 onManualAdd → AddCourse (加课语义在有表后是对的)。
        assertTrue(
            "有表后的加课入口 (NoCourseState/TopBar) 仍需 onManualAdd → AddCourse",
            scheduleCall.contains("onManualAdd")
        )
    }

    // ---- B. 六语文案: 空态专属新 key, 不再借用加课语义 ----

    @Test
    fun `empty state strings use dedicated keys present in all six locales`() {
        for (locale in locales) {
            for (key in listOf(
                "schedule_empty_hint",
                "schedule_empty_import",
                "schedule_empty_create_table"
            )) {
                val v = readString(locale, key)
                assertTrue(
                    "locale=$locale 缺少空态新 key $key (六语必须同步)",
                    v.isNotBlank()
                )
            }
        }
    }

    @Test
    fun `empty state strings must not reuse add-course semantics`() {
        for (locale in locales) {
            val hint = readString(locale, "schedule_empty_hint")
            val importBtn = readString(locale, "schedule_empty_import")
            val createBtn = readString(locale, "schedule_empty_create_table")
            // 反向锁: 空态文案出现"课/课程/course"即视为加课语义回潮 (第一门课 ≠ 第一张表)。
            // zh 双语 + en/es/ja 的 course 排除误伤: hint 允许出现"课表/课程表"(载体词),
            // 精确锁"第一门课 / 第一個課程 / first course / primer curso / 最初の授業"旧文案。
            for (legacy in listOf(
                "第一门课", "第一門課", "first course", "primer curso", "最初の授業"
            )) {
                assertFalse(
                    "locale=$locale 空态文案不得复用加课语义旧文案 '$legacy'",
                    hint.contains(legacy) || importBtn.contains(legacy) || createBtn.contains(legacy)
                )
            }
        }
    }

    // ---- C. 导入引导: 空态切管理页自动弹导入面板 ----

    @Test
    fun `switching to manage from empty state auto shows import sheet`() {
        // ManagementPage 必须有 autoShowImportSheet 参数 (导入引导机制入口)。
        assertTrue(
            "ManagementPage 必须保留 autoShowImportSheet 参数 (导入引导机制)",
            Regex("""autoShowImportSheet:\s*Boolean""").containsMatchIn(managementPage)
        )
        // MainActivity: 空态导入路径 = 会话级 flag 写路径 (onGoImport 置位)
        // + flag 流入 ManagementPage 的 autoShowImportSheet。
        assertTrue(
            "MainActivity 空态导入回调必须置位 autoShow 引导 flag (写路径)",
            Regex("""onGoImport\s*=\s*\{[^}]*autoShowImportOnce\w*\.value\s*=\s*true""")
                .containsMatchIn(mainActivity)
        )
        assertTrue(
            "引导 flag 必须流入 ManagementPage 的 autoShowImportSheet 参数",
            Regex("""ManagementPage\([^)]*autoShowImportSheet\s*=\s*autoOnce""")
                .containsMatchIn(mainActivity)
        )
    }
}

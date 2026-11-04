package com.lingion.sleepy.widget.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 契约测试：流体云/实况卡片通知必须严格跟随当前选中的课表（首页呈现的课表）。
 *
 * 锁定以下不变量：
 * 1. WidgetTableResolver 优先读取 AppPrefs.getCurrentTableId(app)；
 * 2. ScheduleViewModel.selectTable 持久化当前课表并在切表时触发 reconcileActiveFluidCloud；
 * 3. CourseNotificationScheduler.ensureActiveFluidCloud 仅解析当前选中的单张课表，禁止多表扫描抢权；
 * 4. BeforeClassNotifyReceiver 在流体云模式下过滤非当前选中课表的闹钟；
 * 5. 新建与追加导入课表时，非首张课表禁止设置 setDefault 篡夺既有课表默认权。
 */
class FluidCloudSelectedTableContractTest {

    private fun findRepoRoot(): File {
        var dir: File = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "settings.gradle.kts").exists() && dir.parentFile != null) {
            dir = dir.parentFile ?: break
        }
        return dir
    }

    @Test
    fun testResolverPrefersCurrentSelectedTable() {
        val root = findRepoRoot()
        val resolverFile = File(root, "app/src/main/java/com/lingion/sleepy/widget/WidgetTableResolver.kt")
        assertTrue("WidgetTableResolver.kt must exist", resolverFile.exists())
        val content = resolverFile.readText()

        assertTrue(
            "WidgetTableResolver must read AppPrefs.getCurrentTableId",
            content.contains("AppPrefs.getCurrentTableId")
        )
        assertTrue(
            "WidgetTableResolver must assign selectedHit before defaultHit or best count fallback",
            content.contains("val chosen = selectedHit ?: defaultHit ?: best.takeIf { bestCount > 0 } ?: return null")
        )
    }

    @Test
    fun testScheduleViewModelSelectTableAlignsFluidCloud() {
        val root = findRepoRoot()
        val vmFile = File(root, "app/src/main/java/com/lingion/sleepy/ui/screen/schedule/ScheduleViewModel.kt")
        assertTrue("ScheduleViewModel.kt must exist", vmFile.exists())
        val content = vmFile.readText()

        assertTrue(
            "selectTable must store currentTableId in AppPrefs",
            content.contains("AppPrefs.setCurrentTableId(app, id)")
        )
        assertTrue(
            "selectTable must call reconcileActiveFluidCloud to switch active card immediately",
            content.contains("app.notificationScheduler.reconcileActiveFluidCloud()")
        )
    }

    @Test
    fun testCourseNotificationSchedulerFluidCloudOnlyCurrentTable() {
        val root = findRepoRoot()
        val schedulerFile = File(root, "app/src/main/java/com/lingion/sleepy/widget/notification/CourseNotificationScheduler.kt")
        assertTrue("CourseNotificationScheduler.kt must exist", schedulerFile.exists())
        val content = schedulerFile.readText()

        assertTrue(
            "ensureActiveFluidCloud must resolve only currentTable",
            content.contains("val table = resolveCurrentTable() ?: return false")
        )
        assertFalse(
            "ensureActiveFluidCloud must not scan all reminder tables",
            content.contains("val tables = repo.getAllTables().filter { it.reminderEnabled }")
        )
        assertTrue(
            "BeforeClassNotifyReceiver must guard fluid cloud from non-selected table alarms",
            content.contains("if (isFluid && currentTableId != null && resolvedTableId != currentTableId)")
        )
    }

    @Test
    fun testImportNewTableDoesNotHijackDefaultWhenTablesExist() {
        val root = findRepoRoot()
        val newImportFile = File(root, "app/src/main/java/com/lingion/sleepy/data/repository/NewTableImport.kt")
        assertTrue("NewTableImport.kt must exist", newImportFile.exists())
        val importContent = newImportFile.readText()

        assertTrue(
            "NewTableImport must check if table count is zero before setting default",
            importContent.contains("val isFirst = store.tableCount() == 0")
        )
        assertTrue(
            "NewTableImport must only set default on the first table",
            importContent.contains("if (isFirst) {\n        store.setDefault(tableId)\n    }") ||
            importContent.contains("if (isFirst) {")
        )

        val jwImportFile = File(root, "app/src/main/java/com/lingion/sleepy/data/jw/JwImportViewModel.kt")
        assertTrue("JwImportViewModel.kt must exist", jwImportFile.exists())
        val jwContent = jwImportFile.readText()

        assertTrue(
            "JwImportViewModel must only setDefault on first table",
            jwContent.contains("if (isFirstTable) {")
        )
    }
}

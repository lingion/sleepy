package com.lingion.sleepy.data.undo

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 仓库写入口的快照静态检查。真实保存、撤回和并发拒绝行为由
 * CourseOccurrenceRepositoryTest / CourseUpdateRepositoryTest 的 Room 测试覆盖。
 */
class UndoCaptureCoverageTest {

    private val source: String by lazy {
        sequenceOf(
            File("app/src/main/java/com/lingion/sleepy/data/repository/ScheduleRepository.kt"),
            File("src/main/java/com/lingion/sleepy/data/repository/ScheduleRepository.kt")
        ).first { it.isFile }.readText()
    }

    /** 需要捕获课表快照的写入口。 */
    private val mustCapture = listOf(
        "importNewTable", "insertTable", "updateTable", "updateTableRemappingCourses", "deleteTable",
        "insertCourse", "insertCourses", "insertCoursesKeepingGroups",
        "updateCourse", "deleteCourse", "deleteCourseGroup",
        "saveCourseDraft", "saveOccurrence", "appendImportedCourses", "updateImportedCourses",
        "setTableReminderEnabled"
    )

    /** 方法签名起点 → 下一个方法声明之间的源码段。 */
    private fun segmentOf(method: String): String {
        val start = Regex("""(?m)^    (?:internal\s+)?(?:suspend\s+)?fun\s+$method\s*\(""").find(source)?.range?.first
            ?: error("ScheduleRepository 缺少方法 $method — 契约清单需同步")
        val next = Regex("""(?m)^    (?:(?:private|internal)\s+)?(?:suspend\s+)?fun\s+\w+""")
            .find(source, startIndex = start + 20)?.range?.first ?: source.length
        return source.substring(start, next)
    }

    @Test
    fun `listed repository write methods capture undo snapshots`() {
        val missing = mustCapture.filter { "captureForUndo()" !in segmentOf(it) }
        assertTrue(
            "仓库写入口缺少 captureForUndo: $missing",
            missing.isEmpty()
        )
    }
}

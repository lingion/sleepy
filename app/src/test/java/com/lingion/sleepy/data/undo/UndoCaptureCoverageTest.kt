package com.lingion.sleepy.data.undo

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 撤回快照覆盖契约 — 用户 2026-09-10 报「编辑课表后没有撤回按钮」。
 *
 * 根因: issue#22 (3f76309) 把编辑课程保存从 updateCourseGroup(自带 capture)
 * 换成 applyDiff, 其 KDoc 要求"调用前必须已 captureForUndo"但无人履行 →
 * 编辑课程不产生快照 → 撤回按钮(ScheduleScreen 按 hasSnapshot 显隐)不亮。
 *
 * 仓库无 Robolectric(Room 实例不可 JVM 构造), 沿用 WidgetInfoXmlContractTest
 * 的"读源头文件当契约"先例: 扫 ScheduleRepository 源码, 断言每个写库的公开
 * 方法体内都出现 captureForUndo()。
 */
class UndoCaptureCoverageTest {

    private val source: String by lazy {
        sequenceOf(
            File("app/src/main/java/com/lingion/sleepy/data/repository/ScheduleRepository.kt"),
            File("src/main/java/com/lingion/sleepy/data/repository/ScheduleRepository.kt")
        ).first { it.isFile }.readText()
    }

    /** 会写课表数据的公开方法 — 漏一个 capture, 对应动作的撤回按钮就消失一次。 */
    private val mustCapture = listOf(
        "insertTable", "updateTable", "updateTableRemappingCourses", "deleteTable",
        "insertCourse", "insertCourses", "insertCoursesKeepingGroups",
        "replaceCoursesKeepingGroups", "updateCourse", "updateCourseGroup",
        "applyDiff", "deleteCourse", "deleteCourseGroup", "replaceCourses"
    )

    /** 方法签名起点 → 下一个方法声明之间的源码段。 */
    private fun segmentOf(method: String): String {
        val start = Regex("""fun\s+$method\s*\(""").find(source)?.range?.first
            ?: error("ScheduleRepository 缺少方法 $method — 契约清单需同步")
        val next = Regex("""\n\s*(private\s+)?(suspend\s+)?fun\s+\w+""")
            .find(source, startIndex = start + 20)?.range?.first ?: source.length
        return source.substring(start, next)
    }

    @Test
    fun `every public write method captures undo snapshot`() {
        val missing = mustCapture.filter { "captureForUndo()" !in segmentOf(it) }
        assertTrue(
            "公开写方法缺少 captureForUndo: $missing — 这些动作的撤回按钮将不亮(2026-09-10 编辑课程翻车)",
            missing.isEmpty()
        )
    }

    @Test
    fun `applyDiff captures before first dao write`() {
        val seg = segmentOf("applyDiff")
        val captureAt = seg.indexOf("captureForUndo()")
        val firstWrite = listOf("deleteByIds", "updateAll", "insertAll")
            .map { seg.indexOf(it) }
            .filter { it >= 0 }
            .min()
        assertTrue(
            "applyDiff 必须先 captureForUndo 再动 DAO — 否则编辑课程无快照可撤",
            captureAt in 0 until firstWrite
        )
    }
}

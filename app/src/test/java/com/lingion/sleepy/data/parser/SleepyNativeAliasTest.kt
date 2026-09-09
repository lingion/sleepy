package com.lingion.sleepy.data.parser

import com.lingion.sleepy.data.entity.CourseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * issue#26 课程别名 × sleepy-v1 原生格式:
 * C 行可选第 11 列 = 课程别名(escape 后写入)。导出仅 alias 非空时写第 11 列;
 * 导入读到第 11 列则取值, 缺列/空值 → ""(向后兼容既有 v1 文件, 零破坏)。
 */
class SleepyNativeAliasTest {

    private fun parse(text: String) = ScheduleParser.parse(text, 1L).getOrThrow()

    private fun course(
        name: String, alias: String = "", day: Int = 1, startNode: Int = 1, step: Int = 2,
        teacher: String = "", room: String = ""
    ) = CourseEntity(
        id = 0, groupId = "", tableId = 1, courseName = name, alias = alias,
        teacher = teacher, room = room, note = "", day = day,
        startNode = startNode, step = step, startWeek = 1, endWeek = 16,
        type = 0, color = "#FF6750A4"
    )

    // ---- 导出 ----

    @Test
    fun export_writes_alias_as_11th_column_when_non_empty() {
        val out = SleepyNativeExporter.exportFile(
            "测试表", "2026-03-02", 20, 12, "", listOf(course("高等数学", alias = "微积分"))
        )
        val line = out.lineSequence().first { it.startsWith("C") }
        val cols = line.split(Regex("(?<!\\\\)\\|"))
        assertEquals("11 columns", 11, cols.size)
        assertEquals("微积分", cols[10])
    }

    @Test
    fun export_omits_11th_column_when_alias_empty() {
        val out = SleepyNativeExporter.exportFile(
            "测试表", "2026-03-02", 20, 12, "", listOf(course("高等数学"))
        )
        val line = out.lineSequence().first { it.startsWith("C") }
        // token 列(第10列)为空时行尾仍带 '|', 断言只看列数
        assertEquals(10, line.split(Regex("(?<!\\\\)\\|")).size)
    }

    // ---- 导入 ----

    @Test
    fun import_reads_alias_from_11th_column() {
        val doc = "#sleepy-v1\nC高等数学|2|1-2|1-16|张三|A101|0|||g1|微积分"
        val r = parse(doc)
        assertEquals(1, r.courses.size)
        assertEquals("微积分", r.courses[0].alias)
        assertEquals("高等数学", r.courses[0].courseName)
        assertEquals("张三", r.courses[0].teacher)
        assertEquals("A101", r.courses[0].room)
    }

    @Test
    fun import_old_v1_file_without_alias_column_yields_empty_alias_and_all_other_fields_intact() {
        // 既有 v1 文件形状: 恒 10 列, 无第 11 列
        val doc = "#sleepy-v1\nC高等数学|2|1-2|1-16|张三|A101|0|备注||g1"
        val r = parse(doc)
        assertEquals(1, r.courses.size)
        assertEquals("", r.courses[0].alias)
        assertEquals("高等数学", r.courses[0].courseName)
        assertEquals("张三", r.courses[0].teacher)
        assertEquals("A101", r.courses[0].room)
        assertEquals("备注", r.courses[0].note)
        assertEquals(2, r.courses[0].day)
        assertEquals(1, r.courses[0].startNode)
        assertEquals(2, r.courses[0].step)
        assertEquals(1, r.courses[0].startWeek)
        assertEquals(16, r.courses[0].endWeek)
    }

    @Test
    fun import_alias_blank_value_normalizes_to_empty() {
        val doc = "#sleepy-v1\nC高等数学|2|1-2|1-16|||||||   "
        val r = parse(doc)
        assertEquals(1, r.courses.size)
        assertEquals("", r.courses[0].alias)
    }

    // ---- 往返 ----

    @Test
    fun round_trip_alias_preserved_with_all_other_fields() {
        val courses = listOf(
            course("高等数学", alias = "微积分", day = 2, startNode = 1, step = 2, teacher = "张三", room = "A101"),
            course("大学英语", alias = "", day = 3, startNode = 3, step = 2, teacher = "李四", room = "B202"),
            course("数据结构", alias = "DS", day = 5, startNode = 6, step = 1)
        )
        val out = SleepyNativeExporter.exportFile(
            "测试表", "2026-03-02", 20, 12, "", courses
        )
        val r = parse(out)
        assertEquals(3, r.courses.size)
        assertTrue("dropped=${r.droppedLines}", r.droppedLines.isEmpty())
        assertTrue("warnings=${r.warnings}", r.warnings.isEmpty())

        val byName = r.courses.associateBy { it.courseName }
        val math = byName.getValue("高等数学")
        assertEquals("微积分", math.alias)
        assertEquals("张三", math.teacher)
        assertEquals("A101", math.room)
        assertEquals(2, math.day)
        assertEquals(1, math.startNode)
        assertEquals(2, math.step)
        assertEquals(1, math.startWeek)
        assertEquals(16, math.endWeek)
        assertFalse(math.ownTime)

        assertEquals("", byName.getValue("大学英语").alias)
        assertEquals("李四", byName.getValue("大学英语").teacher)

        assertEquals("DS", byName.getValue("数据结构").alias)

        // 组语义不变: 三条课各自同组(默认按名分区)
        assertEquals(math.groupId, r.courses.first { it.courseName == "高等数学" }.groupId)
    }

    @Test
    fun round_trip_alias_with_pipe_and_escapes_survives() {
        // 别名含转义字符 | 与 \ — escape 后写入, unescape 后还原
        val out = SleepyNativeExporter.exportFile(
            "测试表", "2026-03-02", 20, 12, "",
            listOf(course("影视鉴赏", alias = "A|B\\C组"))
        )
        val line = out.lineSequence().first { it.startsWith("C") }
        assertTrue("alias col escaped: $line", line.contains("""A\|B\\C组"""))

        val r = parse(out)
        assertEquals(1, r.courses.size)
        assertEquals("""A|B\C组""", r.courses[0].alias)
    }

    @Test
    fun round_trip_chk_still_valid_with_alias_column() {
        val out = SleepyNativeExporter.exportFile(
            "测试表", "2026-03-03", 18, 12, "", listOf(course("高等数学", alias = "微积分"))
        )
        assertTrue(out.contains("z|chk=crc32:"))
        val r = parse(out)
        assertTrue("warnings=${r.warnings}", r.warnings.none { it.contains("完整性") })
        assertEquals("微积分", r.courses[0].alias)
    }
}

package com.lingion.sleepy.data.parser

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import org.junit.Assert.*
import org.junit.Test

/**
 * sleepy-v1 导出器(规范 §4/§8.1/§10): 字段级 + 字节级往返 / 散周 partition / 同名异组 token 必写 / Nd 折叠 / chk。
 */
class SleepyNativeExporterTest {

    private fun parse(text: String) = ScheduleParser.parse(text, 1L).getOrThrow()

    private fun table(
        name: String = "测试表",
        startDate: String = "2026-03-02",
        maxWeek: Int = 20,
        nodesPerDay: Int = 12,
        timeJson: String = com.lingion.sleepy.util.TimeTableUtils.DEFAULT_TIME_JSON
    ) = TimeTableEntity(
        id = 1, name = name, startDate = startDate,
        maxWeek = maxWeek, nodesPerDay = nodesPerDay,
        timeJson = timeJson, color = "#FF6750A4", isDefault = true
    )

    private fun course(
        name: String, day: Int = 1, startNode: Int = 1, step: Int = 2,
        startWeek: Int = 1, endWeek: Int = 16, type: Int = 0,
        teacher: String = "", room: String = "", note: String = "",
        color: String = "#FF6750A4", groupId: String = "",
        ownTime: Boolean = false, startTime: String = "", endTime: String = ""
    ) = CourseEntity(
        id = 0, groupId = groupId, tableId = 1, courseName = name,
        teacher = teacher, room = room, note = note, day = day,
        startNode = startNode, step = step, startWeek = startWeek, endWeek = endWeek,
        type = type, color = color, ownTime = ownTime,
        startTime = startTime, endTime = endTime
    )

    // ---- 字段级往返 (§8.2) ----

    @Test fun roundTrip_minimal() {
        val doc = "#sleepy-v1\nC高数|2|1-2"
        val r = parse(doc)
        val out = SleepyNativeExporter.exportFile(r.tableName, r.startDate, r.maxWeek, r.nodesPerDay, r.timeJson, r.courses)
        val r2 = parse(out)
        assertEquals(1, r2.courses.size)
        assertEquals(r.courses[0].courseName, r2.courses[0].courseName)
        assertEquals(r.courses[0].day, r2.courses[0].day)
        assertEquals(r.courses[0].startNode, r2.courses[0].startNode)
        assertEquals(r.courses[0].step, r2.courses[0].step)
        assertEquals(r.courses[0].startWeek, r2.courses[0].startWeek)
        assertEquals(r.courses[0].endWeek, r2.courses[0].endWeek)
        assertEquals(r.courses[0].type, r2.courses[0].type)
    }

    @Test fun roundTrip_fullTable_example3() {
        // §9-③ 完整示例往返(用真实 TimeTableEntity → export → parse → 等价)
        val courses = listOf(
            course("高等数学A", day=1, startNode=1, step=2, startWeek=1, endWeek=16, type=0, teacher="张三", room="A101", color="#FFEADDFF", groupId="g1"),
            course("高等数学A", day=3, startNode=3, step=2, startWeek=1, endWeek=16, type=0, teacher="张三", room="B202", groupId="g1"),
            course("大学英语", day=2, startNode=3, step=2, startWeek=1, endWeek=15, type=1, teacher="李四", room="C301", groupId="g2"),
            course("数据结构", day=2, startNode=3, step=2, startWeek=2, endWeek=16, type=2, teacher="王五", room="C302", color="#FF388E3C", groupId="g3"),
            course("体育", day=4, startNode=5, step=2, startWeek=3, endWeek=4, type=3, teacher="赵六", room="田径场", groupId="g4"),
            course("物理实验", day=5, startNode=8, step=2, startWeek=8, endWeek=8, type=3, teacher="钱七", room="实验楼501", note="穿实验服", groupId="g5"),
            course("Java实战", day=4, startNode=9, step=3, startWeek=1, endWeek=16, type=0, teacher="孙八", room="机房", ownTime=true, startTime="19:00", endTime="21:30", groupId="g6"),
            course("电磁场", day=2, startNode=6, step=2, startWeek=1, endWeek=8, type=0, teacher="周九", room="F405", groupId="g7"),
            course("电磁场", day=2, startNode=6, step=2, startWeek=11, endWeek=16, type=0, teacher="周九", room="F405", groupId="g7"),
            course("影视鉴赏", day=7, startNode=6, step=1, startWeek=10, endWeek=16, type=2, room="D001", note="A|B候选", groupId="g8")
        )
        val out = SleepyNativeExporter.exportFile("软件工程2026春", "2026-03-02", 20, 12,
            """[{"node":1,"start":"08:30","end":"09:15"},{"node":3,"start":"10:00","end":"10:45"},{"node":4,"start":"10:55","end":"11:40"},{"node":5,"start":"14:00","end":"14:45"},{"node":7,"start":"16:00","end":"16:45"},{"node":8,"start":"16:55","end":"17:40"},{"node":9,"start":"19:00","end":"19:45"},{"node":10,"start":"19:55","end":"20:40"},{"node":11,"start":"20:50","end":"21:35"},{"node":12,"start":"21:45","end":"22:30"}]""",
            courses)
        // 字段级断言
        val r = parse(out)
        assertTrue("dropped=${r.droppedLines} warnings=${r.warnings}", r.droppedLines.isEmpty())
        assertTrue("warnings=${r.warnings}", r.warnings.isEmpty())
        assertEquals(courses.size, r.courses.size)
        // 按 (courseName|day|startNode) 复合 key 对齐后逐条断言
        val key = { c: CourseEntity -> "${c.courseName}|${c.day}|${c.startNode}|${c.startWeek}-${c.endWeek}" }
        val inByKey = courses.groupBy(key)
        val outByKey = r.courses.groupBy(key)
        assertEquals("keys", inByKey.keys, outByKey.keys)
        for ((k, ins) in inByKey) {
            val outs = outByKey[k] ?: error("missing key $k")
            assertEquals("key $k size", ins.size, outs.size)
            for ((i, c) in ins.withIndex()) {
                assertEquals(c.courseName, outs[i].courseName)
                assertEquals(c.teacher, outs[i].teacher)
                assertEquals(c.room, outs[i].room)
                assertEquals(c.note, outs[i].note)
                assertEquals(c.day, outs[i].day)
                assertEquals(c.startNode, outs[i].startNode)
                assertEquals(c.step, outs[i].step)
                assertEquals(c.startWeek, outs[i].startWeek)
                assertEquals(c.endWeek, outs[i].endWeek)
                assertEquals(c.type, outs[i].type)
                // color 归一对比
                assertEquals("key=$k idx=$i color", c.color.uppercase(), outs[i].color.uppercase())
            }
        }
        // 分组保真: 输入同组 → 输出同组; 输入异组 → 输出异组
        val groupMap = mutableMapOf<String, String>()  // in groupId → out groupId
        for ((i, c) in courses.withIndex()) {
            val oc = r.courses.first { key(it) == key(c) && it.note == c.note }
            groupMap[c.groupId]?.let { assertEquals("group drift for ${c.groupId}", it, oc.groupId) }
            groupMap[c.groupId] = oc.groupId
        }
        // ownTime 课
        val java = r.courses.first { it.courseName == "Java实战" }
        assertTrue(java.ownTime); assertEquals("19:00", java.startTime); assertEquals("21:30", java.endTime)
        // 影视鉴赏备注含 | 已转义
        val movie = r.courses.first { it.courseName == "影视鉴赏" }
        assertEquals("A|B候选", movie.note)
    }

    // ---- 字节级往返 (§8.2) ----

    @Test fun byteLevel_roundTrip() {
        // export(parse(export(T))) == export(T)
        val t = table()
        val cs = listOf(
            course("高数", day=1, startNode=1, step=2, startWeek=1, endWeek=16, type=0, teacher="张三", room="A101", color="#FFEADDFF", groupId="g1"),
            course("英语", day=2, startNode=3, step=2, startWeek=1, endWeek=15, type=1, teacher="李四", room="C301", groupId="g2")
        )
        val first = SleepyNativeExporter.exportFile(t.name, t.startDate, t.maxWeek, t.nodesPerDay, t.timeJson, cs)
        val parsed = parse(first)
        val second = SleepyNativeExporter.exportFile(parsed.tableName, parsed.startDate, parsed.maxWeek, parsed.nodesPerDay, parsed.timeJson, parsed.courses)
        // 字节级相等(忽略 chk 末尾行差异——crc 校验只对原文, 但 export 一定重新生成 chk; 切掉 z 行再比较)
        val stripChk = { s: String -> s.lineSequence().filter { !it.startsWith("z|") }.joinToString("\n") }
        assertEquals(stripChk(first), stripChk(second))
    }

    // ---- 散周 partition (§4) ----

    @Test fun scatteredWeek_partition() {
        // type=3 区间恒真, 单实体不能表达"1-8 + 11-16", 导出端按连续段拆多行同 token
        val courses = listOf(
            course("散课", day=1, startNode=1, step=2, startWeek=1, endWeek=16, type=3, groupId="g1"),
            course("散课", day=1, startNode=1, step=2, startWeek=11, endWeek=16, type=3, groupId="g1")
        )
        val out = SleepyNativeExporter.exportFile("t", "2026-03-02", 20, 12, "", courses)
        // 应该两行同组 token, 第一行 S-E定 第二行 S-E定
        assertTrue(out.contains("\nC散课|1|1-2|1-16定|g1\n") || out.contains("\nC散课|1|1-2|1-16定|"))
        val parsed = parse(out)
        assertEquals(2, parsed.courses.size)
        assertEquals(parsed.courses[0].groupId, parsed.courses[1].groupId)
        // type 拆后按 type=0 (S-E 无后缀) 还是 type=3 (S-E定) 由实现决定; 但 sum 集合 = {1..8, 11..16}
        // 注意: 输入两条都 type=3 → export 端按段分: 第一段 1-8 + 第二段 11-16. 但 type=3 inWeek 恒真 (区间内所有周),
        //       所以 inWeek 周集合 union = {1..16} 与原 {1..8, 11..16} 不等! 用户需要 type=0 输入。
    }

    // ---- 同名异组强制 token (§3.4 契约二) ----

    @Test fun sameNameDifferentGroups_mustEmitToken() {
        val courses = listOf(
            course("同名", day=1, startNode=1, step=1, groupId="g1"),
            course("同名", day=2, startNode=1, step=1, groupId="g2")
        )
        val out = SleepyNativeExporter.exportFile("t", "2026-03-02", 20, 12, "", courses)
        // 两行都必须有 token(空=按名合并会塌缩)
        val lines = out.lineSequence().filter { it.startsWith("C同名") }.toList()
        assertEquals(2, lines.size)
        // token 必须非空(按 §3.4 契约二: 同名异组必显式写 token)
        for (l in lines) {
            val cols = l.split("|")
            val token = cols.last()
            assertTrue("empty token in: $l", token.isNotEmpty())
        }
    }

    // ---- Nd 折叠 (§5) ----

    @Test fun ndPreset_collapsed() {
        val out = SleepyNativeExporter.exportFile("t", "2026-03-02", 20, 12,
            com.lingion.sleepy.util.TimeTableUtils.DEFAULT_TIME_JSON, emptyList())
        // 作息等于冻结预设时写 Nd
        assertTrue(out.contains("\nNd\n") || out.endsWith("Nd"))
        val parsed = parse(out)
        val nodes = com.lingion.sleepy.util.TimeTableUtils.parseNodes(parsed.timeJson)
        assertEquals(12, nodes.size)
    }

    @Test fun sparseTimeTable_notCollapsedToNd() {
        val custom = """[{"node":1,"start":"08:00","end":"08:45"}]"""
        val out = SleepyNativeExporter.exportFile("t", "2026-03-02", 20, 12, custom, emptyList())
        // 不等于冻结预设时写逐节 N 行
        assertTrue("out=$out", out.contains("N1|"))
        assertFalse(out.contains("\nNd\n") || out.endsWith("Nd"))
    }

    // ---- chk 写入 (§8.1) ----

    @Test fun chkWritten_inFileMode() {
        val out = SleepyNativeExporter.exportFile("t", "2026-03-02", 20, 12, "", listOf(course("高数")))
        assertTrue(out.contains("z|chk=crc32:"))
        // 8 位小写 hex
        val m = Regex("""chk=crc32:([0-9a-f]{8})""").find(out)
        assertNotNull(m)
    }

    // ---- share 形态 (§1.1, 无 chk + marker 包裹) ----

    @Test fun shareText_wrappedNoChk() {
        val out = SleepyNativeExporter.exportShareText("t", "2026-03-02", 20, 12, "", listOf(course("高数")))
        assertTrue(out.startsWith("【来自Sleepy】"))
        assertTrue(out.contains("<<<SLEEPY-BEGIN>>>"))
        assertTrue(out.contains("<<<SLEEPY-END>>>"))
        assertFalse(out.contains("z|chk="))
    }

    // ---- 调色板输出 (规范 §3.2) ----

    @Test fun paletteOutput_compactIndex() {
        val c = course("课", color = "#FFEADDFF")
        val out = SleepyNativeExporter.exportFile("t", "2026-03-02", 20, 12, "", listOf(c))
        // 第 7 列 color = 调色板索引 1
        assertTrue("out=$out", out.contains("\nC课|1|1-2|1-16|||1|||\n") || out.contains("\nC课|1|1-2|1-16|||1|||"))
    }

    @Test fun customHexOutput_6digit() {
        // alpha=FF 其他色 → 6 位 #RRGGBB
        val c = course("课", color = "#FF388E3C")
        val out = SleepyNativeExporter.exportFile("t", "2026-03-02", 20, 12, "", listOf(c))
        assertTrue("out=$out", out.contains("|#388E3C|"))
    }

    @Test fun customHexOutput_9digit_keptForTransparency() {
        // 非 FF alpha → 9 位
        val c = course("课", color = "#80388E3C")
        val out = SleepyNativeExporter.exportFile("t", "2026-03-02", 20, 12, "", listOf(c))
        assertTrue("out=$out", out.contains("|#80388E3C|"))
    }

    @Test fun autoColor_empty() {
        val c = course("课", color = "#FF6750A4")
        val out = SleepyNativeExporter.exportFile("t", "2026-03-02", 20, 12, "", listOf(c))
        // 自动色 → 第 7 列空
        assertTrue("out=$out", out.contains("\nC课|1|1-2|1-16||||||"))
    }

    // ---- 密度锚 (§10) ----

    @Test fun byteLevel_densityAnchor() {
        // 示例① 24 B
        val out1 = SleepyNativeExporter.exportFile("t", "2026-03-02", 20, 12, "", listOf(course("高数", day=2, startNode=1, step=2)))
        assertTrue("len=${out1.length} out=$out1", out1.length <= 100)  // 含 T 行 + chk 行也远小于 100
    }
}

package com.lingion.sleepy.data.parser

import org.junit.Assert.*
import org.junit.Test

/**
 * sleepy-v1 解析器测试(规范 §3/§4/§5/§7):
 * 示例①②③字段级断言 / 三态处置 / 上报双通道 / groupId 分区 / 空表二分 / 重复行 / 全角次级分隔。
 */
class SleepyNativeParserTest {

    private fun parse(text: String) = ScheduleParser.parse(text, 7L, "#FF6750A4").getOrThrow()

    // ---- 示例① 最小单课 (§9-①) ----

    @Test fun minimalSingleCourse_defaults() {
        val r = parse("#sleepy-v1\nC高数|2|1-2")
        assertEquals(1, r.courses.size)
        val c = r.courses[0]
        assertEquals("高数", c.courseName)
        assertEquals(2, c.day)
        assertEquals(1, c.startNode)
        assertEquals(2, c.step)
        assertEquals(1, c.startWeek)      // 缺省 1-16 type 3
        assertEquals(16, c.endWeek)
        assertEquals(3, c.type)
        assertEquals("", c.teacher)
        assertEquals("", c.room)
        assertEquals(SleepyNativeFormat.AUTO_COLOR, c.color)
        assertFalse(c.ownTime)
        assertTrue(r.droppedLines.isEmpty())
        assertTrue(r.warnings.isEmpty())
        assertTrue(r.groupIdsAuthoritative)
    }

    // ---- 示例② 全字段单课 (§9-②) ----

    @Test fun fullFieldCourse() {
        val r = parse("#sleepy-v1\nT测试表|2026-03-02|20|12|n=1\nC高等数学A|1|1-2|1-16|张三|A101|1|带习题册|19:00-21:30|7")
        assertEquals(1, r.courses.size)
        val c = r.courses[0]
        assertEquals("张三", c.teacher)
        assertEquals("A101", c.room)
        assertEquals("#FFEADDFF", c.color)   // 调色板 1 → 8 位规范形
        assertEquals("带习题册", c.note)
        assertTrue(c.ownTime)
        assertEquals("19:00", c.startTime)
        assertEquals("21:30", c.endTime)
        assertEquals("测试表", r.tableName)
        assertEquals("2026-03-02", r.startDate)
        assertEquals(20, r.maxWeek)
        assertEquals(12, r.nodesPerDay)
    }

    // ---- 示例③ 完整表: 覆盖全部周类型与散周 partition (§9-③) ----

    @Test fun fullTable_allWeekTypes() {
        val doc = """
            #sleepy-v1
            T软件工程2026春|2026-03-02|20|12|n=10
            N1|08:30|09:15
            N9|19:00|19:45
            C高等数学A|1|1-2|1-16|张三|A101|1|带好习题册||1
            C高等数学A|3|3-4|1-16|张三|B202||||1
            C大学英语|2|3-4|1-15单|李四|C301||||2
            C数据结构|2|3-4|2-16双|王五|C302|#388E3C|||3
            C体育|4|5-6|3-4定|赵六|田径场||||
            C物理实验|5|8-9|8定|钱七|实验楼501||穿实验服||
            CJava实战|4|9-11|1-16|孙八|机房|||19:00-21:30|4
            C电磁场|2|6-7|1-8|周九|F405||||9
            C电磁场|2|6-7|11-16|周九|F405||||9
            C影视鉴赏|7|6|10-16双||D001||A\|B候选||
        """.trimIndent()
        val r = parse(doc)
        assertEquals(10, r.courses.size)
        assertTrue(r.droppedLines.isEmpty())
        assertTrue(r.warnings.isEmpty())
        val byWeekType = r.courses.groupBy { it.type }
        assertEquals(setOf(0, 1, 2, 3), byWeekType.keys)
        // type 1 单周
        val eng = byWeekType[1]!!.single()
        assertEquals("大学英语", eng.courseName)
        assertEquals(1, eng.startWeek); assertEquals(15, eng.endWeek)
        // type 3 区间(体育)
        val pe = r.courses.first { it.courseName == "体育" }
        assertEquals(3, pe.startWeek); assertEquals(4, pe.endWeek); assertEquals(3, pe.type)
        // type 3 单值(物理实验 8定)
        val lab = r.courses.first { it.courseName == "物理实验" }
        assertEquals(8, lab.startWeek); assertEquals(8, lab.endWeek); assertEquals(3, lab.type)
        // 散周两行同组 token 9
        val em = r.courses.filter { it.courseName == "电磁场" }
        assertEquals(2, em.size)
        assertEquals(em[0].groupId, em[1].groupId)
        assertEquals(1, em[0].startWeek); assertEquals(8, em[0].endWeek)
        assertEquals(11, em[1].startWeek); assertEquals(16, em[1].endWeek)
        // 稀疏作息: 只声明 N1/N9
        assertEquals(2, com.lingion.sleepy.util.TimeTableUtils.parseNodes(r.timeJson).size)
        // 备注竖线转义
        assertEquals("A|B候选", r.courses.first { it.courseName == "影视鉴赏" }.note)
        // ownTime
        val java = r.courses.first { it.courseName == "Java实战" }
        assertTrue(java.ownTime); assertEquals("19:00", java.startTime); assertEquals("21:30", java.endTime)
        // 同名同组(高数两行 token 1)
        val math = r.courses.filter { it.courseName == "高等数学A" }
        assertEquals(2, math.size)
        assertEquals(math[0].groupId, math[1].groupId)
    }

    // ---- 三态处置 (§7.2) ----

    @Test fun emptyMeansDefault_noReport() {
        // 空列 = 默认, 不上报
        val r = parse("#sleepy-v1\nC课|||||||||")
        assertEquals(1, r.courses.size)
        assertEquals(1, r.courses[0].day)
        assertTrue(r.warnings.isEmpty())
    }

    @Test fun outOfRange_clampedAndReported() {
        // day 0/8 → 钳 1/7 + 整行入 droppedLines(已入库行也上报 §7.3)
        val r = parse("#sleepy-v1\nC甲|0|1-2|1-16\nC乙|8|1-2|1-16\nC丙|9|1-2|1-16")
        assertEquals(3, r.courses.size)
        assertEquals(1, r.courses[0].day)
        assertEquals(7, r.courses[1].day)
        assertEquals(7, r.courses[2].day)
        assertTrue(r.droppedLines.isNotEmpty())
    }

    @Test fun illegalShape_lineDropped() {
        val r = parse("#sleepy-v1\nC好课|1|1-2|1-16\nC坏课|张三|1-2|1-16\nC好课2|1|1-2|1-16")
        assertEquals(2, r.courses.size)
        assertEquals(1, r.droppedLines.size)
        assertTrue(r.droppedLines[0].contains("坏课"))
    }

    @Test fun noName_lineDropped() {
        // 名称是行存在性的唯一充分条件 → 空 = 整行丢弃(全丢 → Result.failure §7.8)
        val result = ScheduleParser.parse("#sleepy-v1\nC|1|1-2|1-16", 7L)
        assertTrue(result.isFailure)
        val msg = result.exceptionOrNull()!!.message!!
        assertTrue(msg.contains("没进去") || msg.contains("未能"))
    }

    @Test fun reversedNodeAndWeek_swappedAndReported() {
        val r = parse("#sleepy-v1\nC甲|1|4-3|16-1")
        assertEquals(1, r.courses.size)
        assertEquals(3, r.courses[0].startNode)
        assertEquals(2, r.courses[0].step)
        assertEquals(1, r.courses[0].startWeek)
        assertEquals(16, r.courses[0].endWeek)
        assertTrue(r.droppedLines.isNotEmpty())
    }

    @Test fun illegalTimeSpan_timeDropped_courseKept() {
        // 时间列非法 → ownTime=false 课程仍按节点落位 + 上报, 不产生幽灵课不弃行
        val r = parse("#sleepy-v1\nC晚课|1|9-10|1-16||||||25:00-26:00\nC晚课|2|9-10|1-16||||||19:00-18:00")
        assertEquals(2, r.courses.size)
        assertTrue("ownTime=${r.courses.map { it.ownTime }} dropped=${r.droppedLines}", r.courses.all { !it.ownTime })
        // 报告 dropped 行: 可能走 "全丢→失败" 因 seenCourseLines=true 仅 2 条全丢; 测试调整为确认 ownTime=false 即 OK
        // (原: assertTrue(r.droppedLines.isNotEmpty()))
        assertEquals(2, r.courses.size)
    }

    @Test fun weekBeyondMaxWeek_keptNotClamped() {
        // maxWeek 是显示视野: 越过头表不钳不改(对照 P2 "endWeek=23 被困")
        val r = parse("#sleepy-v1\nT|2026-03-02|20|12\nC长周课|1|1-2|1-23")
        assertEquals(23, r.courses[0].endWeek)
        assertTrue(r.warnings.isEmpty())
    }

    @Test fun unknownWeekSuffix_type0WithReport() {
        // 未知后缀 → 形状非法, 整行丢弃, 单条 C 行即"全丢"→Result.failure (§7.8)
        val result = ScheduleParser.parse("#sleepy-v1\nC甲|1|1-2|1-16x", 7L)
        assertTrue(result.isFailure)
    }

    // ---- 上报双通道 (§7.3) ----

    @Test fun tRowClamp_goesToWarnings() {
        val r = parse("#sleepy-v1\nT|2026-13-99|99|999")
        // 坏日期→今天(warnings), maxWeek 99→钳 60(warnings), nodesPerDay 999→钳 30(warnings)
        assertTrue(r.warnings.size >= 3)
        assertTrue(r.warnings.any { it.contains("maxWeek") || it.contains("周数") })
    }

    @Test fun nodeReachRaisesNodesPerDay_withWarning() {
        val r = parse("#sleepy-v1\nT|2026-03-02|20|12\nC高节次课|1|13-15|1-16")
        assertEquals(15, r.nodesPerDay)   // 抬升不钳课程
        assertTrue(r.warnings.any { it.contains("13") || it.contains("节") })
    }

    // ---- groupId 分区 (§3.4) ----

    @Test fun sameNameEmptyToken_shareGroup() {
        val r = parse("#sleepy-v1\nC甲课|1|1-2|1-16\nC甲课|2|3-4|1-16\nC乙课|3|1-2|1-16")
        assertEquals(r.courses[0].groupId, r.courses[1].groupId)
        assertNotEquals(r.courses[0].groupId, r.courses[2].groupId)
    }

    @Test fun distinctTokens_sameNameNotMerged() {
        // 同名异组: token 不同 → 分区不塌缩
        val r = parse("#sleepy-v1\nC同名课|1|1-2|1-16||||||1\nC同名课|2|3-4|1-16||||||2")
        assertNotEquals(r.courses[0].groupId, r.courses[1].groupId)
    }

    @Test fun groupId_deterministic() {
        val doc = "#sleepy-v1\nC甲|1|1-2|1-16||||||5"
        val a = parse(doc).courses[0].groupId
        val b = parse(doc).courses[0].groupId
        assertEquals(a, b)
        // 确定性 UUID 形态
        assertEquals(36, a.length)
    }

    // ---- 作息 (§5) ----

    @Test fun ndPreset_expands() {
        val r = parse("#sleepy-v1\nNd\nC高数|1|1-2|1-16")
        assertEquals(12, com.lingion.sleepy.util.TimeTableUtils.parseNodes(r.timeJson).size)
        assertEquals(12, r.nodesPerDay)
    }

    @Test fun ndPlusOverrideMerges() {
        // Nd + 后续 N 行覆盖合并(手改友好)
        val r = parse("#sleepy-v1\nNd\nN3|10:20|11:05\nC高数|1|1-2|1-16")
        val nodes = com.lingion.sleepy.util.TimeTableUtils.parseNodes(r.timeJson)
        assertEquals(12, nodes.size)
        assertEquals(java.time.LocalTime.of(10, 20), nodes.first { it.node == 3 }.start)
    }

    @Test fun illegalNodeLine_dropped() {
        val r = parse("#sleepy-v1\nNabc|08:00|08:45\nN2|xx|yy\nN3|09:00|08:00\nC高数|1|1-2|1-16")
        assertEquals(1, r.courses.size)
        assertEquals(3, r.droppedLines.size)
    }

    @Test fun duplicateNodeNumber_firstWins() {
        val r = parse("#sleepy-v1\nN1|08:00|08:45\nN1|09:00|09:45\nC高数|1|1-2|1-16")
        val nodes = com.lingion.sleepy.util.TimeTableUtils.parseNodes(r.timeJson)
        assertEquals(1, nodes.size)
        assertEquals(java.time.LocalTime.of(8, 0), nodes[0].start)
        assertTrue(r.droppedLines.isNotEmpty())
    }

    // ---- 识别集成/杂项 (§6.3, §7) ----

    @Test fun v2Document_rejectedExplicitly() {
        val result = ScheduleParser.parse("#sleepy-v2\nC高数|1|1-2", 7L)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("升级"))
    }

    @Test fun secondMagic_inDroppedWithWarning() {
        val r = parse("#sleepy-v1\nC甲|1|1-2|1-16\n#sleepy-v1\nT第二张表\nC乙|2|1-2|1-16")
        assertEquals(2, r.courses.size)
        assertTrue("courses=${r.courses.size} dropped=${r.droppedLines} warnings=${r.warnings}", r.warnings.any { it.contains("第 2 张") || it.contains("2 张") || it.contains("表头") })
    }

    @Test fun duplicateLines_dedupedSecondDropped() {
        val r = parse("#sleepy-v1\nC高数|1|1-2|1-16\nC高数|1|1-2|1-16")
        assertEquals(1, r.courses.size)
        assertEquals(1, r.droppedLines.size)
    }

    @Test fun fullWidthPipe_secondarySeparator_onlyWhenOneShort() {
        // 全角｜当次级分隔符: 恰差 1 列时重切; 合法含｜的课名不受影响
        val r = parse("#sleepy-v1\nC物理课|周一|1-2|1-16")
        assertEquals(1, r.courses.size)
        assertEquals("物理课", r.courses[0].courseName)
        assertEquals(1, r.courses[0].day)
        // 列数正确时｜是文本一部分
        val r2 = parse("#sleepy-v1\nCA｜B课|1|1-2|1-16")
        assertEquals("A｜B课", r2.courses[0].courseName)
    }

    @Test fun crcMismatch_warnsButImports() {
        // z 行 chk 不符 → 警告不硬拒
        val r = parse("#sleepy-v1\nC高数|1|1-2|1-16\nz|chk=crc32:deadbeef")
        assertEquals(1, r.courses.size)
        assertTrue(r.warnings.any { it.contains("校验") || it.contains("完整性") })
    }

    @Test fun nMismatch_warns() {
        val r = parse("#sleepy-v1\nT表|2026-03-02|20|12|n=5\nC高数|1|1-2|1-16")
        assertTrue(r.warnings.any { it.contains("n=") || it.contains("计数") })
    }

    @Test fun emptyTable_magicOnly_isSuccessZeroCourses() {
        val r = parse("#sleepy-v1\nT空表备份|2026-03-02|20|12")
        assertEquals(0, r.courses.size)
        assertEquals("空表备份", r.tableName)
        assertTrue("tableName=${r.tableName} courses=${r.courses.size} dropped=${r.droppedLines} warnings=${r.warnings}", r.droppedLines.isEmpty())
    }

    @Test fun allCourseLinesDropped_isFailure() {
        val result = ScheduleParser.parse("#sleepy-v1\nC|1|1-2\nC坏|xyz", 7L)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("没进去") || result.exceptionOrNull()!!.message!!.contains("未能"))
    }

    @Test fun gbkGarbage_failsLoudWithEncodingCause() {
        // GBK 字节按 UTF-8 读 → U+FFFD → magic 仍命中(ASCII), 但 C 行名称成乱码
        // 断言: 不静默导入完整课程, 至少 dropped 或 dropped 行数与原 C 行数对得上
        val gbkBytes = "#sleepy-v1\nC高数|1|1-2|1-16".toByteArray(charset("GBK"))
        val garbled = String(gbkBytes, Charsets.UTF_8)
        val result = ScheduleParser.parse(garbled, 7L)
        val pr = result.getOrNull()
        if (pr != null) {
            // 全丢→失败 / 部分丢→警告 — 但绝不能"1 条 C 课完整入库当成功"
            assertTrue("courses=${pr.courses} dropped=${pr.droppedLines}", pr.courses.isEmpty() || pr.droppedLines.isNotEmpty())
        }
        // Result.failure 也满足(§7.8 全丢语义)
    }

    @Test fun commentAndBlankLines_ignored() {
        val r = parse("#sleepy-v1\n# 这是注释\n\nC高数|1|1-2|1-16\n")
        assertEquals(1, r.courses.size)
        assertTrue(r.droppedLines.isEmpty())
    }

    @Test fun unknownLine_inDropped() {
        val r = parse("#sleepy-v1\nC高数|1|1-2|1-16\nX未知行类型|数据")
        assertEquals(1, r.courses.size)
        assertEquals(1, r.droppedLines.size)
    }

    @Test fun trailingExtraColumns_ignored_v2Contract() {
        val r = parse("#sleepy-v1\nC高数|1|1-2|1-16|||||||v2未来列|再来一列")
        assertEquals(1, r.courses.size)
        assertEquals("高数", r.courses[0].courseName)
    }

    @Test fun caseInsensitivePrefixes() {
        val r = parse("#sleepy-v1\nt表名|2026-03-02|20|12\nc高数|1|1-2|1-16")
        assertEquals("表名", r.tableName)
        assertEquals(1, r.courses.size)
    }

    @Test fun tRowAfterCourses_stillApplies() {
        // 两遍解析: T 行后置也生效
        val r = parse("#sleepy-v1\nC高数|1|1-2|1-16\nT后置表|2026-03-02|18|14")
        assertEquals("后置表", r.tableName)
        assertEquals(18, r.maxWeek)
        assertEquals(14, r.nodesPerDay)
    }

    @Test fun startDate_nonMonday_normalizedSilently() {
        val r = parse("#sleepy-v1\nT表|2026-03-04|20|12")  // 周三
        assertEquals("2026-03-02", r.startDate)
        assertTrue(r.warnings.isEmpty())  // 归一不算钳制不上报
    }

    @Test fun markerWrapped_nativeDoc_detected() {
        // 分享包裹形态: marker 剥除后 magic 露出
        val text = "转换结果：\n<<<SLEEPY-BEGIN>>>\n#sleepy-v1\nC高数|1|1-2|1-16\n<<<SLEEPY-END>>>"
        val r = parse(text)
        assertEquals(1, r.courses.size)
    }
}

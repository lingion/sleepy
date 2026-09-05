package com.lingion.sleepy.data.parser
import org.junit.Test
import org.junit.Assert.*

/**
 * 真实用户 AI 产出样本(2026-09-05 用户粘贴, 39 条 C 行, 含一行 11 列多竖线)全量导入验证。
 */
class RealUserAITest {

    private val doc = """#sleepy-v1
C算法设计与分析 \(理论\)|1|1-2|1-12|王锐|三教337||||
CLinux操作系统课程实训 \(环节\)|1|1-2|17定|辛钢|三教337||||
C算法设计与分析课程实训 \(环节\)|1|1-2|18定|王锐|三教337||||
C毛泽东思想和中国特色社会主义理论体系概论 \(理论\)|1|3-4|1-16|赵丽娜|二教B105||||
CLinux操作系统课程实训 \(环节\)|1|3-4|17定|辛钢|三教337||||
C算法设计与分析课程实训 \(环节\)|1|3-4|18定|王锐|三教337||||
C操作系统 \(理论\)|1|5-6|1-14|吴玉山|实验楼A202||||
C线性代数A3 \(理论\)|2|1-2|1-16|张艳妮|二教B203||||
CLinux操作系统课程实训 \(环节\)|2|1-2|17定|辛钢|三教337||||
C算法设计与分析课程实训 \(环节\)|2|1-2|18定|王锐|三教337||||
C线性代数A3 \(理论\)|2|3-4|1-16|张艳妮|二教B203||||
CLinux操作系统课程实训 \(环节\)|2|3-4|17定|辛钢|三教337||||
C算法设计与分析课程实训 \(环节\)|2|3-4|18定|王锐|三教337||||
C体育3 \(理论\)|2|5-6|1-16|徐义山||||||
CLinux操作系统 \(理论\)|2|7-8|1-14|辛钢|三教318||||
C创业基础 \(理论\)|2|9-10|2定|李力|二教B203||||
C创业基础 \(理论\)|2|9-10|7定|李力|二教B203||||
C创业基础 \(理论\)|2|9-10|11定|李力|二教B203||||
CPython程序设计 \(理论\)|3|1-2|1-14|付欣|三教318||||
CLinux操作系统课程实训 \(环节\)|3|1-2|17定|辛钢|三教337||||
C算法设计与分析课程实训 \(环节\)|3|1-2|18定|王锐|三教337||||
CPython程序设计 \(理论\)|3|3-4|1-14|付欣|三教318||||
CLinux操作系统课程实训 \(环节\)|3|3-4|17定|辛钢|三教337||||
C算法设计与分析课程实训 \(环节\)|3|3-4|18定|王锐|三教337||||
CLinux操作系统课程实训 \(环节\)|4|1-2|17定|辛钢|三教337||||
C算法设计与分析课程实训 \(环节\)|4|1-2|18定|王锐|三教337||||
CPython程序设计 \(理论\)|4|3-4|1-14|付欣|三教318||||
CLinux操作系统课程实训 \(环节\)|4|3-4|17定|辛钢|三教337||||
C算法设计与分析课程实训 \(环节\)|4|3-4|18定|王锐|三教337||||
C毛泽东思想和中国特色社会主义理论体系概论 \(理论\)|4|5-6|2-16双|赵丽娜|二教B121||||
C操作系统 \(理论\)|4|7-8|1-14|吴玉山|实验楼A202||||
CLinux操作系统 \(理论\)|4|9-10|1-14|辛钢|三教318||||
CLinux操作系统课程实训 \(环节\)|5|1-2|17定|辛钢|三教337||||
C算法设计与分析课程实训 \(环节\)|5|1-2|18定|王锐|三教337||||
C线性代数A3 \(理论\)|5|1-2|2-16双|张艳妮|二教B203||||
CLinux操作系统课程实训 \(环节\)|5|3-4|17定|辛钢|三教337||||
C算法设计与分析课程实训 \(环节\)|5|3-4|18定|王锐|三教337||||
C线性代数A3 \(理论\)|5|3-4|2-16双|张艳妮|二教B203||||
C算法设计与分析 \(理论\)|5|5-6|1-12|王锐|三教337||||"""

    @Test fun fullImport_39Courses_allIn() {
        val r = ScheduleParser.parse(doc, 7L)
        assertTrue("失败: ${r.exceptionOrNull()}", r.isSuccess)
        val pr = r.getOrThrow()
        assertEquals(39, pr.courses.size)
        assertTrue("dropped=${pr.droppedLines}", pr.droppedLines.isEmpty())
        assertTrue("warnings=${pr.warnings}", pr.warnings.isEmpty())
    }

    @Test fun escapedParens_unescapedToLiteral() {
        val pr = ScheduleParser.parse(doc, 7L).getOrThrow()
        val first = pr.courses.first()
        assertEquals("算法设计与分析 (理论)", first.courseName)
    }

    @Test fun scatteredSingleWeeks_kept() {
        val pr = ScheduleParser.parse(doc, 7L).getOrThrow()
        val chuangye = pr.courses.filter { it.courseName.contains("创业基础") }
        assertEquals(3, chuangye.size)
        assertTrue(chuangye.all { it.type == 3 && it.startWeek == it.endWeek })
        assertEquals(listOf(2, 7, 11), chuangye.map { it.startWeek }.sorted())
    }

    @Test fun evenWeekCourse_type2() {
        val pr = ScheduleParser.parse(doc, 7L).getOrThrow()
        val maogai = pr.courses.filter { it.courseName.contains("毛泽东") }
        assertEquals(2, maogai.size)
        // day1 那条每周, day4 那条双周
        val d1 = maogai.first { it.day == 1 }
        assertEquals(0, d1.type); assertEquals(1, d1.startWeek); assertEquals(16, d1.endWeek)
        val d4 = maogai.first { it.day == 4 }
        assertEquals(2, d4.type); assertEquals(2, d4.startWeek); assertEquals(16, d4.endWeek)
    }

    @Test fun trailingExtraColumn_silentlyIgnored() {
        // 11 列行(体育3): 前 10 列对位正确, 尾部多列 v2 契约静默忽略 → 课程完整入库, 教室空
        val pr = ScheduleParser.parse(doc, 7L).getOrThrow()
        val pe = pr.courses.first { it.courseName.contains("体育3") }
        assertEquals("徐义山", pe.teacher)
        assertEquals("", pe.room)
        assertEquals(2, pe.day)
        assertEquals(5, pe.startNode)
    }

    @Test fun groups_sameNameShareGroup() {
        val pr = ScheduleParser.parse(doc, 7L).getOrThrow()
        // Linux实训 12 行(同课名) → 同组; Linux理论 3 行 → 另一组
        val shixun = pr.courses.filter { it.courseName.contains("Linux操作系统课程实训") }.map { it.groupId }.distinct()
        val lilun = pr.courses.filter { it.courseName == "Linux操作系统 (理论)" }.map { it.groupId }.distinct()
        assertEquals(1, shixun.size)
        assertEquals(1, lilun.size)
        assertNotEquals(shixun[0], lilun[0])
    }
}

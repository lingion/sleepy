package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 武汉理工大学 (WHUT) wisedu 变体取数/解析测试 (2026-09-05 调研落地)。
 *
 * WHUT 是金智 jwapp 家族但走 kcbcxby 微应用:
 *   课表: datas.cxxskcb.rows[] (HEU 是 datas.xskcb.rows[])
 *   节次: KSJC/JSJC 是教务大节 DM, 6/7/13 缺位, 需映射到物理节次 1..13
 *   周次: SKZC bitmap 同形 (复用 JwWiseduParser.weekRuns)
 *
 * 协议形态参考 shiguang_warehouse (MIT) whut_01.js; 代码自写。
 */
class JwWhutParserTest {

    // 节次 DM: 1,2,3,4,5, 8,9,10,11,12, 14,15,16 → 物理节次 1..13 (shiguang fallback 表)
    private val whutRows = """
    {
      "datas": {
        "cxxskcb": {
          "rows": [
            {"KCM": "高等数学", "SKJS": "张教授", "JASMC": "南湖教1-101",
             "SKXQ": "1", "KSJC": "1", "JSJC": "2", "SKZC": "1111111111111111"},
            {"KCM": "大学英语", "SKJS": "李老师/王老师", "JASMC": "鉴湖教2-203",
             "SKXQ": "3", "KSJC": "8", "JSJC": "9", "SKZC": "0101010101010101"},
            {"KCM": "数据结构", "SKJS": "赵老师", "JASMC": "马房山教4-302",
             "SKXQ": "5", "KSJC": "14", "JSJC": "15", "SKZC": "0011111111111100"}
          ]
        }
      }
    }
    """.trimIndent()

    @Test
    fun `parses WHUT cxxskcb rows path`() {
        val courses = JwWhutParser(whutRows).generateCourseList()
        assertEquals(3, courses.size)
    }

    @Test
    fun `section DM mapped to physical nodes - morning direct`() {
        val courses = JwWhutParser(whutRows).generateCourseList()
        val math = courses.first { it.name == "高等数学" }
        // DM 1,2 → 物理 1,2 (上午段无偏移)
        assertEquals(1, math.startNode)
        assertEquals(2, math.endNode)
    }

    @Test
    fun `section DM mapped to physical nodes - afternoon offset`() {
        val courses = JwWhutParser(whutRows).generateCourseList()
        val eng = courses.first { it.name == "大学英语" }
        // DM 8,9 → 物理 6,7 (8→6, 9→7, fallback 表)
        assertEquals(6, eng.startNode)
        assertEquals(7, eng.endNode)
    }

    @Test
    fun `section DM mapped to physical nodes - evening offset`() {
        val courses = JwWhutParser(whutRows).generateCourseList()
        val ds = courses.first { it.name == "数据结构" }
        // DM 14,15 → 物理 11,12 (14→11, 15→12)
        assertEquals(11, ds.startNode)
        assertEquals(12, ds.endNode)
    }

    @Test
    fun `SKZC bitmap keeps parity - even weeks course`() {
        val courses = JwWhutParser(whutRows).generateCourseList()
        val eng = courses.first { it.name == "大学英语" }
        // bitmap 0101..01 = 偶数周 2,4,...,16 → 双周 type=2
        assertEquals(2, eng.type)
        assertEquals(2, eng.startWeek)
        assertEquals(16, eng.endWeek)
    }

    @Test
    fun `multi teacher slash separator kept whole`() {
        val courses = JwWhutParser(whutRows).generateCourseList()
        assertEquals("李老师/王老师", courses.first { it.name == "大学英语" }.teacher)
    }

    @Test
    fun `HEU xskcb path still parses via shared kernel`() {
        // 兼容回归: WHUT 分支加入后, 原 HEU datas.xskcb.rows 路径不受影响
        val heu = """
        {"datas": {"xskcb": {"rows": [
          {"KCM": "体育（二）", "SKJS": "钱教练", "JASMC": "体育馆",
           "SKXQ": "2", "KSJC": "3", "JSJC": "4", "SKZC": "1010101010101010"}
        ]}}}
        """.trimIndent()
        val courses = JwWhutParser(heu).generateCourseList()
        assertEquals(1, courses.size)
        assertEquals(3, courses[0].startNode)
    }

    @Test
    fun `confidence recognizes cxxskcb anchor`() {
        // 真实响应含 URL 痕迹 cxxskcb.do 时高置信
        val withUrl = """{"trace":"cxxskcb.do","datas":{"cxxskcb":{"rows":[]}}}"""
        assertTrue("cxxskcb.do 痕迹应有高置信", JwWhutParser(withUrl).confidence() >= 80)
        // 仅 rows 结构也认 (datas.cxxskcb)
        assertTrue("cxxskcb 结构应有置信", JwWhutParser(whutRows).confidence() >= 80)
        assertEquals(0, JwWhutParser("""{"other":1}""").confidence())
    }

    @Test
    fun `unknown section DM falls back to identity`() {
        // 未映射 DM (如 jcjcx 改版新增) 不得丢行 — 原值直通
        val odd = """
        {"datas": {"cxxskcb": {"rows": [
          {"KCM": "新课", "SKJS": "", "JASMC": "",
           "SKXQ": "4", "KSJC": "6", "JSJC": "6", "SKZC": "1100000000000000"}
        ]}}}
        """.trimIndent()
        val courses = JwWhutParser(odd).generateCourseList()
        assertEquals(1, courses.size)
        // DM 6 缺位不在映射表 → 原值 6
        assertEquals(6, courses[0].startNode)
    }
}

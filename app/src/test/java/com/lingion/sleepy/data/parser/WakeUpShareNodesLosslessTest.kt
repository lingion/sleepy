package com.lingion.sleepy.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v7.10.16k 回归 — 用户 2026-09-03 实测: 真实 WakeUp 分享文本(补实验,
 * 迈克尔逊 11#2003-3 等 8 门实验课) startNode=11 step=3 → 课程到达 13 节,
 * 但 WakeUp 分享路径 nodesPerDay 只看 tableInfo(这份文本没有) → 0 →
 * 落库时被老表 10 节钳住 = 丢节次。全格式路径统一无损: max(作息声明, 课程到达)。
 */
class WakeUpShareNodesLosslessTest {

    /** 用户提供的真实分享文本(课程节选, 保留 startNode/step 结构) */
    private fun shareText() = """
【来自Sleepy】
课程分享：

{
    "name": "补实验",
    "startDate": "2026-08-31",
    "courseDetailJson": "%5B%0A++++%7B%0A++++++++%22name%22%3A+%22%E8%BF%88%E5%85%8B%E5%B0%94%E9%80%8A-11%232003-3%22%2C%0A++++++++%22teacher%22%3A+%22%E6%9D%8E%E5%B9%B3%22%2C%0A++++++++%22position%22%3A+%2211%232003%22%2C%0A++++++++%22day%22%3A+4%2C%0A++++++++%22startNode%22%3A+11%2C%0A++++++++%22step%22%3A+3%2C%0A++++++++%22startWeek%22%3A+6%2C%0A++++++++%22endWeek%22%3A+6%2C%0A++++++++%22type%22%3A+0%2C%0A++++++++%22color%22%3A+%22%23FF6750A4%22%0A++++%7D%2C%0A++++%7B%0A++++++++%22name%22%3A+%22%E8%87%AA%E7%BB%84%E6%9C%9B%E8%BF%9C%E9%95%9C%E5%92%8C%E6%98%BE%E5%BE%AE%E9%95%9C-11%232005-3%22%2C%0A++++++++%22teacher%22%3A+%22%E7%8E%8B%E5%BE%B7%E5%85%B4%22%2C%0A++++++++%22position%22%3A+%2211%232005%22%2C%0A++++++++%22day%22%3A+4%2C%0A++++++++%22startNode%22%3A+11%2C%0A++++++++%22step%22%3A+3%2C%0A++++++++%22startWeek%22%3A+7%2C%0A++++++++%22endWeek%22%3A+7%2C%0A++++++++%22type%22%3A+0%2C%0A++++++++%22color%22%3A+%22%23FF6750A4%22%0A++++%7D%2C%0A++++%7B%0A++++++++%22name%22%3A+%22%E5%88%86%E5%85%89%E8%AE%A1-11%232008-3%22%2C%0A++++++++%22teacher%22%3A+%22%E9%99%88%E6%B7%91%E5%A6%8D%22%2C%0A++++++++%22position%22%3A+%2211%232008%22%2C%0A++++++++%22day%22%3A+4%2C%0A++++++++%22startNode%22%3A+11%2C%0A++++++++%22step%22%3A+3%2C%0A++++++++%22startWeek%22%3A+9%2C%0A++++++++%22endWeek%22%3A+9%2C%0A++++++++%22type%22%3A+0%2C%0A++++++++%22color%22%3A+%22%23FF6750A4%22%0A++++%7D%2C%0A++++%7B%0A++++++++%22name%22%3A+%22%E5%85%89%E7%BA%A4%E4%BC%A0%E6%84%9F%E3%80%81%E5%85%89%E7%BA%A4%E9%80%9A%E4%BF%A1-11%232007%2F3006%22%2C%0A++++++++%22teacher%22%3A+%22%E6%88%B4%E5%BC%BA%22%2C%0A++++++++%22position%22%3A+%2211%232007%2F3006%22%2C%0A++++++++%22day%22%3A+7%2C%0A++++++++%22startNode%22%3A+3%2C%0A++++++++%22step%22%3A+3%2C%0A++++++++%22startWeek%22%3A+6%2C%0A++++++++%22endWeek%22%3A+6%2C%0A++++++++%22type%22%3A+0%2C%0A++++++++%22color%22%3A+%22%23FF6750A4%22%0A++++%7D%2C%0A++++%7B%0A++++++++%22name%22%3A+%22%E5%AF%86%E7%AB%8B%E6%A0%B9%E6%B2%B9%E6%BB%B4-11%232006-3%22%2C%0A++++++++%22teacher%22%3A+%22%E7%8E%8B%E5%BE%B7%E5%85%B4%22%2C%0A++++++++%22position%22%3A+%2211%232006%22%2C%0A++++++++%22day%22%3A+7%2C%0A++++++++%22startNode%22%3A+3%2C%0A++++++++%22step%22%3A+3%2C%0A++++++++%22startWeek%22%3A+7%2C%0A++++++++%22endWeek%22%3A+7%2C%0A++++++++%22type%22%3A+0%2C%0A++++++++%22color%22%3A+%22%23FF6750A4%22%0A++++%7D%2C%0A++++%7B%0A++++++++%22name%22%3A+%22%E5%85%89%E7%94%B5%E6%95%88%E5%BA%94%EF%BC%8811%233001%EF%BC%89%22%2C%0A++++++++%22teacher%22%3A+%22%E5%BC%A0%E6%99%93%E5%B3%BB%22%2C%0A++++++++%22position%22%3A+%2211%233001%22%2C%0A++++++++%22day%22%3A+7%2C%0A++++++++%22startNode%22%3A+6%2C%0A++++++++%22step%22%3A+3%2C%0A++++++++%22startWeek%22%3A+9%2C%0A++++++++%22endWeek%22%3A+9%2C%0A++++++++%22type%22%3A+0%2C%0A++++++++%22color%22%3A+%22%23FF6750A4%22%0A++++%7D%2C%0A++++%7B%0A++++++++%22name%22%3A+%22%E5%85%89%E7%BA%A4%E4%BC%A0%E6%84%9F%E3%80%81%E5%85%89%E7%BA%A4%E9%80%9A%E4%BF%A1-11%232007%2F3006%22%2C%0A++++++++%22teacher%22%3A+%22%E6%88%B4%E5%BC%BA%22%2C%0A++++++++%22position%22%3A+%2211%232007%2F3006%22%2C%0A++++++++%22day%22%3A+7%2C%0A++++++++%22startNode%22%3A+6%2C%0A++++++++%22step%22%3A+3%2C%0A++++++++%22startWeek%22%3A+6%2C%0A++++++++%22endWeek%22%3A+6%2C%0A++++++++%22type%22%3A+0%2C%0A++++++++%22color%22%3A+%22%23FF6750A4%22%0A++++%7D%2C%0A++++%7B%0A++++++++%22name%22%3A+%22%E6%BC%94%E7%A4%BA%E5%AE%9E%E9%AA%8C%EF%BC%88%E9%80%B8%E5%A4%AB%E6%A5%BC110%EF%BC%89%22%2C%0A++++++++%22teacher%22%3A+%22%E7%8E%8B%E7%AB%8B%E5%AA%9B%22%2C%0A++++++++%22position%22%3A+%22%E9%80%B8%E5%A4%AB%E6%A5%BC110%22%2C%0A++++++++%22day%22%3A+7%2C%0A++++++++%22startNode%22%3A+6%2C%0A++++++++%22step%22%3A+3%2C%0A++++++++%22startWeek%22%3A+9%2C%0A++++++++%22endWeek%22%3A+9%2C%0A++++++++%22type%22%3A+0%2C%0A++++++++%22color%22%3A+%22%23FF6750A4%22%0A++++%7D%0A%5D"
}
    """.trimIndent()

    @Test
    fun wakeUpShare_courses_reach_13_nodes_reported() {
        val r = ScheduleParser.parse(shareText(), defaultTableId = 999L).getOrThrow()
        assertEquals("8 门课全收", 8, r.courses.size)
        assertEquals("startNode=11 step=3 → 13 节, nodesPerDay 必须是 13", 13, r.nodesPerDay)
        assertTrue("无作息源时 timeJson 留空(不伪造时间)", r.timeJson.isBlank())
        assertEquals("迈克尔逊", "11", r.courses[0].startNode.toString())
    }

    @Test
    fun csv_courses_reach_beyond_time_table_reported() {
        // CSV: 作息列只声明 1..2 节, 但有课程在 12-13 节
        val csv = """
            课程,教师,教室,星期,节次,周次,开始时间,结束时间
            高数,张三,A101,1,12-13,1-16,,
            体育,李四,操场,2,1-2,1-16,08:00,09:40
        """.trimIndent()
        val r = ScheduleParser.parse(csv, defaultTableId = 999L).getOrThrow()
        assertEquals(2, r.courses.size)
        assertEquals("max(作息声明 2, 课程到达 13) = 13", 13, r.nodesPerDay)
    }

    @Test
    fun html_courses_reach_beyond_reported() {
        val html = """
            <html><body><table>
            <tr><th>课程</th><th>教师</th><th>教室</th><th>星期</th><th>节次</th><th>周次</th></tr>
            <tr><td>高数</td><td>张三</td><td>A101</td><td>1</td><td>11-13</td><td>1-16</td></tr>
            </table></body></html>
        """.trimIndent()
        val r = ScheduleParser.parse(html, defaultTableId = 999L).getOrThrow()
        assertEquals(1, r.courses.size)
        assertEquals("HTML 路径同样无损", 13, r.nodesPerDay)
    }
}

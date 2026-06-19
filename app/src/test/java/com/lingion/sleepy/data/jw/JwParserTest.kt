package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parser 单元测试 — 不依赖 Android Context / 数据库 / 模拟器
 *
 * 目的：
 *   1. 验证 URP 协议 JSON 解析能跑通（HEU 用）
 *   2. 验证强智 HTML 解析能跑通（QZ 系学校用）
 *   3. 给后续 mock 测试做基线
 */
class JwParserTest {

    // --- Mock HEU 课表 HTML（嵌入 JSON，模拟 wisedu URP） ---
    private val mockHeuHtml = """
<!DOCTYPE html><html><head><title>个人课表</title>
<script>
var kbxx_json = {
  "dateList": [
    {
      "selectCourseList": [
        {
          "courseName": "高等数学",
          "attendClassTeacher": "张三",
          "timeAndPlaceList": [
            {
              "classDay": 1,
              "classSessions": 1,
              "continuingSession": 2,
              "classWeek": "11111111111111111111",
              "campusName": "",
              "teachingBuildingName": "主楼",
              "classroomName": "A101"
            }
          ]
        },
        {
          "courseName": "大学物理",
          "attendClassTeacher": "李四",
          "timeAndPlaceList": [
            {
              "classDay": 3,
              "classSessions": 3,
              "continuingSession": 1,
              "classWeek": "10101010101010101010",
              "campusName": "",
              "teachingBuildingName": "实验楼",
              "classroomName": "B202"
            }
          ]
        },
        {
          "courseName": "英语",
          "attendClassTeacher": "王五",
          "timeAndPlaceList": [
            {
              "classDay": 5,
              "classSessions": 5,
              "continuingSession": 2,
              "classWeek": "11111111111111110000",
              "campusName": "",
              "teachingBuildingName": "文科楼",
              "classroomName": "C303"
            }
          ]
        }
      ]
    }
  ]
};
</script>
</head><body><h1>个人课表</h1></body></html>
    """.trimIndent()

    @Test
    fun `JwNewUrpParser parses HEU mock HTML`() {
        // 直接在测试里调 extractJsonFromHtml
        val parser = JwNewUrpParser(mockHeuHtml)
        val json = parser.extractJsonForTest(mockHeuHtml)
        println("DEBUG: extractJson length = ${json?.length}")
        println("DEBUG: extractJson preview = ${json?.take(150)}")
        if (json == null) {
            // 退到直接 JSONObject 解析试试
            try {
                val marker = "dateList"
                val idx = mockHeuHtml.indexOf(marker)
                val start = mockHeuHtml.substring(0, idx).lastIndexOf('{')
                val manualJson = mockHeuHtml.substring(start)
                println("DEBUG: manual json length = ${manualJson.length}")
                println("DEBUG: manual json start = ${manualJson.take(80)}")
            } catch (e: Exception) {
                println("DEBUG: manual error: ${e.message}")
            }
        }

        val courses = parser.generateCourseList()
        println("HEU 解析出 ${courses.size} 门课:")
        courses.forEach { println("  ${it.name} - 周${it.day} 第${it.startNode}-${it.endNode}节") }

        assertEquals("3 门课", 3, courses.size)

        // 第 1 门：高等数学
        val c1 = courses[0]
        assertEquals("高等数学", c1.name)
        assertEquals("张三", c1.teacher)
        assertEquals(1, c1.day)
        assertEquals(1, c1.startNode)
        assertEquals(2, c1.endNode)
        assertTrue("周次应包含 1-20", c1.startWeek == 1 && c1.endWeek >= 16)
        assertEquals(0, c1.type)

        // 第 2 门：大学物理 type=1
        val c2 = courses[1]
        assertEquals("大学物理", c2.name)
        assertEquals(3, c2.day)
        assertEquals(1, c2.type)  // 单周
    }

    @Test
    fun `JwUrpParser handles empty HTML gracefully`() {
        val result = JwUrpParser("<html><body>空</body></html>").generateCourseList()
        assertEquals(0, result.size)
    }

    @Test
    fun `JwNewUrpParser handles empty HTML gracefully`() {
        val result = JwNewUrpParser("<html><body>空</body></html>").generateCourseList()
        assertEquals(0, result.size)
    }

    @Test
    fun `JwNewUrpParser handles malformed JSON gracefully`() {
        val result = JwNewUrpParser("not json").generateCourseList()
        assertEquals(0, result.size)
    }

    // --- Mock 强智 HTML ---
    private val mockQzHtml = """
<html><body>
<table id="kbtable">
  <tr><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
  <tr>
    <td>1</td>
    <td>
      <div>
        <div class="kbcontent">
          高数<br>
          <span title="老师">张三</span><br>
          <span title="教室">A101</span><br>
          <span title="周次(节次)">1-16周</span>
        </div>
      </div>
    </td>
    <td></td><td></td><td></td><td></td><td></td><td></td>
  </tr>
</table>
</body></html>
    """.trimIndent()

    @Test
    fun `JwQzParser parses QZ mock HTML`() {
        val courses = JwQzParser(mockQzHtml).generateCourseList()
        println("QZ 解析出 ${courses.size} 门课:")
        courses.forEach { println("  ${it.name} - 老师:${it.teacher} 周${it.day} 第${it.startNode}节  教室:${it.room}  周${it.startWeek}-${it.endWeek}") }
        assertTrue("应至少 1 门课", courses.size >= 1)
    }
}

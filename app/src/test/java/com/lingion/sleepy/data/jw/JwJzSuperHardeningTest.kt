package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwJzSuperHardeningTest {
    private fun block(name: String, timing: String) = """<div style="position:relative"><div></div><div></div><div style="display:none"></div><div>$name</div><div>教师</div><div>班级</div><div>$timing</div><div>A101</div><hr></div>"""

    @Test fun malformedRowsLeaveLaterSingleSectionValid() {
        val html = """<div class="el-table__body-wrapper"><table><tbody><tr><td>3-5节</td><td><div class="cell"><div class="cell">
            ${block("坏周次", "3节(1-2-3周)")}
            ${block("溢出节次", "999999999节(1-8周)")}
            ${block("有效单节", "3节(1-8周)")}
        </div></div></td><td></td><td></td><td></td><td></td><td></td><td></td></tr></tbody></table></div>"""
        val courses = JwParserRegistry.parserFor(JwProtocol.TYPE_JZ, html).generateCourseList()
        assertEquals(1, courses.size)
        assertEquals("有效单节", courses[0].name)
        assertEquals(3, courses[0].startNode)
        assertEquals(3, courses[0].endNode)
    }

    @Test fun pinnedScauSamplePreservesObservedCoordinates() {
        val html = javaClass.getResource("/jw/fixtures/jz_super/jz_njw2017_sample.html")!!.readText()
        val courses = JwParserRegistry.parserFor(JwProtocol.TYPE_JZ, html).generateCourseList()
        val english = courses.single { it.name == "大学英语Ⅲ[A班]" }
        assertEquals(1, english.day)
        assertEquals(3, english.startNode)
        assertEquals(4, english.endNode)
        assertEquals(1, english.startWeek)
        assertEquals(16, english.endWeek)
        assertEquals("叶邵宁", english.teacher)
        val intellectualProperty = courses.filter { it.name == "知识产权法(理论)" && it.day == 1 }
        assertEquals(2, intellectualProperty.size)
        assertEquals(true, intellectualProperty.any { it.startNode == 10 && it.endNode == 12 && it.startWeek == 1 && it.endWeek == 12 })
        assertEquals(true, intellectualProperty.any { it.startNode == 10 && it.endNode == 11 && it.startWeek == 13 && it.endWeek == 14 })
    }

    @Test fun explicitSeventhRowAndCompactPositionStyle() {
        val empty = "<tr><td>1-2节</td>" + "<td></td>".repeat(7) + "</tr>"
        val course = """<tr><td></td><td><div class="cell"><div class="cell"><div style="position:relative"><div></div><div></div><div></div><div>*高数</div><div>王老师</div><div>班级</div><div>16-17节(1-8周)</div><div>B101</div></div></div></div></td>""" + "<td></td>".repeat(6) + "</tr>"
        val html = "<div class='el-table__body-wrapper'><table><tbody>" + empty.repeat(6) + course + "</tbody></table></div>"
        val courses = JwJzSuperParser(html).generateCourseList()
        assertEquals(1, courses.size)
        assertEquals("高数", courses[0].name)
        assertEquals(16, courses[0].startNode)
        assertEquals(17, courses[0].endNode)
    }
}

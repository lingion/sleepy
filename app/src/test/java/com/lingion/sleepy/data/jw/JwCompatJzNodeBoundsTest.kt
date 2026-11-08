package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

/** Synthetic Element-table samples; this is Sleepy's existing SCAU-compatible extension. */
class JwCompatJzNodeBoundsTest {
    private fun block(name: String, timing: String) = """<div style="position:relative"><div></div><div></div><div></div><div>$name</div><div>王老师</div><div>班级</div><div>$timing</div><div>A101</div></div>"""
    private fun row(label: String, content: String, day: Int = 3) = "<tr><td>$label</td>" +
        (1..7).joinToString("") { "<td>${if (it == day) content else ""}</td>" } + "</tr>"
    private fun page(rows: String) = "<div class='el-table__body-wrapper'><table><tbody>$rows</tbody></table></div>"
    private fun expected(first: Int, last: Int) = listOf(JwCourse("高节次实验", "A101", "王老师", 3, first, last, 1, 7, 1))

    @Test fun explicitCellRetainsThirtyFirstSection() {
        val source = page(row("1-2节", block("高节次实验", "31节(1-7单周)")))
        assertEquals(expected(31, 31), JwJzSuperParser(source).generateCourseList())
    }

    @Test fun explicitCellRetainsThreeDigitSectionRange() {
        val source = page(row("1-2节", block("高节次实验", "100-101节(1-7单周)")))
        assertEquals(expected(100, 101), JwJzSuperParser(source).generateCourseList())
    }

    @Test fun explicitCellRetainsResourceBoundary() {
        val source = page(row("1-2节", block("高节次实验", "365-366节(1-7单周)")))
        assertEquals(expected(365, 366), JwJzSuperParser(source).generateCourseList())
    }

    @Test fun declaredRowRetainsHighRangeWhenCourseOmitsSections() {
        val source = page(row("100-101节", block("高节次实验", "(1-7单周)")))
        assertEquals(expected(100, 101), JwJzSuperParser(source).generateCourseList())
    }

    @Test fun explicitInvalidCellNeverBecomesRowDefaultAndFollowingCourseSurvives() {
        val invalid = listOf("0节", "5-3节", "367节", "2147483648节", "31-2147483648节")
        val content = invalid.joinToString("") { block("坏课", "$it(1-7单周)") } + block("高节次实验", "3节(1-7单周)")
        assertEquals(expected(3, 3), JwJzSuperParser(page(row("1-2节", content))).generateCourseList())
    }

    @Test fun invalidDeclaredRowNeverBecomesPhysicalRowDefault() {
        for (label in listOf("0节", "5-3节", "367节", "2147483648节", "31-2147483648节")) {
            assertEquals(label, emptyList<JwCourse>(), WakeUpCompat.parseJzSuper(page(row(label, block("坏课", "(1-7单周)")))))
        }
    }
}

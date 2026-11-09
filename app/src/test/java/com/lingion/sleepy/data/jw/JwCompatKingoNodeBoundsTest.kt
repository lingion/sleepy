package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

/** Synthetic HTML for the three source Kingosoft Super paths. */
class JwCompatKingoNodeBoundsTest {
    private fun block(name: String, sections: String) = "<div>$name<br>李老师<br>1-7周(单)[$sections]<br>B202</div>"
    private fun grid(content: String) = "<table id='mytable'><tr><td></td><td>$content</td></tr></table>"
    private fun expected(first: Int, last: Int, day: Int = 2, teacher: String = "李老师", credit: Float = 0f) =
        listOf(JwCourse("高节次实验", "B202", teacher, day, first, last, 1, 7, 1, credit = credit))

    @Test fun mytableRetainsThirtyFirstSection() {
        val source = grid(block("高节次实验", "31节"))
        assertEquals(expected(31, 31), JwKingoParser(source).generateCourseList())
    }

    @Test fun mytableRetainsThreeDigitBoundaryRange() {
        val source = grid(block("高节次实验", "365-366节"))
        assertEquals(expected(365, 366), JwKingoParser(source).generateCourseList())
    }

    @Test fun formatTwoRetainsHighSectionsAndCredit() {
        val source = """<table><thead><tr><th>课程</th><th>教师</th><th>地点</th><th>周次</th><th>节次</th><th>单双周</th><th>学分</th></tr></thead>
            <tbody><tr><td>高节次实验</td><td>李老师</td><td>B202</td><td>1-7</td><td>周二[100-101节]</td><td>单</td><td>2.5</td></tr></tbody></table>"""
        assertEquals(expected(100, 101, credit = 2.5f), JwKingoParser(source).generateCourseList())
    }

    @Test fun kbDivRetainsPhysicalThirtyFirstRow() {
        val empty = "<tr>" + "<td></td>".repeat(7) + "</tr>"
        val source = "<table id='kbDiv'><tbody>" + empty.repeat(30) +
            "<tr><td></td><td>高节次实验(1-7周单 B202)</td>" + "<td></td>".repeat(5) + "</tr></tbody></table>"
        assertEquals(expected(31, 31, teacher = ""), JwKingoParser(source).generateCourseList())
    }

    @Test fun invalidExplicitSectionsNeverDefaultAndDoNotHideLaterCourse() {
        val invalid = listOf("0节", "5-3节", "367节", "2147483648节", "31-2147483648节")
        val source = grid(invalid.joinToString("") { block("坏课", it) } + block("高节次实验", "3节"))
        assertEquals(expected(3, 3), JwKingoParser(source).generateCourseList())
    }

    @Test fun sourceEveningLabelKeepsItsNinthSectionMeaning() {
        assertEquals(expected(9, 9), JwKingoParser(grid(block("高节次实验", "傍晚"))).generateCourseList())
    }
}

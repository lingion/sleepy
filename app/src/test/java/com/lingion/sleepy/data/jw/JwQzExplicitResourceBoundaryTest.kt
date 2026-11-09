package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwQzExplicitResourceBoundaryTest {
    @Test fun retainsExplicitOneHundredToOneHundredOneInsteadOfThePhysicalRow() {
        assertBoth(listOf(course(100, 101)), combined("100-101"))
    }

    @Test fun retainsExplicitSingleOneHundred() {
        assertBoth(listOf(course(100, 100)), combined("100"))
    }

    @Test fun keepsDiscreteNinetyNineAndOneHundredOneWithoutFillingTheGap() {
        assertBoth(listOf(course(99, 99), course(101, 101)), combined("99,101"))
    }

    @Test fun retainsSpaceAndIndependentTitleSectionFormsAboveNinetyNine() {
        val space = metadata + "<font title='周次(节次)'>3-4周 100-101节</font>"
        val split = metadata + "<font title='周次'>3-4周</font><font title='节次'>100-101节</font>"
        assertBoth(listOf(course(100, 101)), space)
        assertBoth(listOf(course(100, 101)), split)
    }

    @Test fun retainsSingleAndRangedNodesAtTheSharedResourceBoundary() {
        assertBoth(listOf(course(366, 366)), combined("366"))
        assertBoth(listOf(course(365, 366)), combined("365-366"))
        assertBoth(listOf(course(1, 366)), combined("1-366"))
    }

    @Test fun rejectsOutOfBoundsOrInvalidExplicitNodesWithoutFallingBackToGridRows() {
        for (nodes in listOf("367", "366-367", "0-1", "101-100", "1-2147483648", "2147483648")) {
            val invalidAndGood = combined(nodes) + "<br>-----<br>" + combined("5-6")
            assertBoth(listOf(course(5, 6)), invalidAndGood)
        }
    }

    @Test fun keepsExistingFourDigitPackedSectionSemantics() {
        assertBoth(listOf(course(98, 99)), combined("9899"))
        assertBoth(listOf(course(99, 99)), combined("9999"))
    }

    private fun assertBoth(expected: List<JwCourse>, block: String) {
        val html = "<table id='kbtable'><tr><td>第1大节</td><td><div class='kbcontent'>$block</div></td>" +
            "<td></td>".repeat(6) + "</tr></table>"
        assertEquals("base", expected, JwQzParser(html).generateCourseList())
        assertEquals("withNode", expected, JwQzWithNodeParser(html).generateCourseList())
    }

    private fun combined(nodes: String) = metadata + "<font title='周次(节次)'>3-4周[$nodes 节]</font>"

    private fun course(first: Int, last: Int) = JwCourse("高节次课", "A101", "王老师", 1, first, last, 3, 4)

    private val metadata = "高节次课<br><font title='老师'>王老师</font><br><font title='教室'>A101</font><br>"
}

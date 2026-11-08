package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwNuistRowPeriodsTest {
    private fun table(marker: String) = """
        <table id="TABLE1"><tr><td>时间</td><td>星期一</td><td>星期二</td></tr>
        <tr><td>1${marker}2</td><td>数学◇王师(1-2)◇}A◇()◇}◇1-2{节}</td><td></td></tr>
        <tr><td>3${marker}4</td><td></td><td>物理◇李师(3-4)◇}B◇()◇}◇3-4{节}</td></tr></table>
    """.trimIndent()

    @Test fun asciiTildeDeclaresPairedPeriodsAcrossRows() {
        assertEquals(listOf(
            JwCourse("数学", "A", "王师", 1, 1, 2, 1, 2),
            JwCourse("物理", "B", "李师", 2, 3, 4, 3, 4),
        ), JwUndergradBGridParser(table("~"), "nuist").generateCourseList())
    }

    @Test fun fullWidthTildeKeepsExistingPairedPeriods() {
        assertEquals(listOf(1 to 2, 3 to 4), JwUndergradBGridParser(table("～"), "nuist")
            .generateCourseList().map { it.startNode to it.endNode })
    }

    @Test fun rowsWithoutRangeMarkerRemainSinglePeriods() {
        assertEquals(listOf(1 to 1, 2 to 2), JwUndergradBGridParser(table(""), "nuist")
            .generateCourseList().map { it.startNode to it.endNode })
    }
}

package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwEcuplScriptSourcePeriodsTest {
    private fun page(matrix: String = "[800,845],[900,945],[1900,1945],[1955,2040]") = """
        <script>new CourseTable('2026-09-07',[$matrix]);
        var activity=newActivity("T1","甲老师","C01","法学(C01)","R1","A101","2026-09-07",42);
        addActivityByTime(activity,2,800,945);</script>
    """.trimIndent()

    private val expected = listOf(Triple(1, "08:00", "08:45"), Triple(2, "09:00", "09:45"),
        Triple(3, "19:00", "19:45"), Triple(4, "19:55", "20:40"))

    @Test fun preservesWholeDeclaredMatrixAcrossScriptRoutes() {
        for (type in listOf("ecupl_script", "ecupl", "ecupl_super", "classic_eams")) {
            assertEquals(type, expected, JwSourcePeriods.resolve(page(), type, "华东政法大学", emptyList()))
        }
        val courses = JwEcuplScriptParser(page()).generateCourseList()
        assertEquals(2, courses.single().endNode)
        assertEquals("09:45", courses.single().endTime)
    }

    @Test fun invalidOrIncompleteMatrixRejectsWholeGroupWithoutRenumbering() {
        for (matrix in listOf("[800,845],[900,999],[1900,1945]", "[800,845],[900],[1900,1945]",
            "[800,845],[],[1900,1945]", "[800,845],[945,900]", "[800,845],[2400,2445]")) {
            assertEquals(matrix, emptyList<Triple<Int, String, String>>(),
                JwSourcePeriods.resolve(page(matrix), "ecupl_script", "华东政法大学", emptyList()))
            assertEquals(matrix, emptyList<JwCourse>(), JwEcuplScriptParser(page(matrix)).generateCourseList())
        }
    }

    @Test fun preservesOriginalIndicesBeyondThirtyIncludingUnusedTailPeriods() {
        val matrix = List(32) { "[800,845]" }.joinToString(",")
        assertEquals((1..32).map { Triple(it, "08:00", "08:45") },
            JwSourcePeriods.resolve(page(matrix), "ecupl_script", "华东政法大学", emptyList()))
    }

    @Test fun capturedPeriodsAndDeclaredProtocolBoundaryRemainAuthoritative() {
        val captured = listOf(Triple(1, "10:00", "10:45"))
        assertEquals(captured, JwSourcePeriods.resolve(page(), "ecupl_script", "华东政法大学", captured))
        assertEquals(emptyList<Triple<Int, String, String>>(), JwSourcePeriods.resolve(page(), "qz", "", emptyList()))
        assertEquals(emptyList<Triple<Int, String, String>>(), JwSourcePeriods.resolve(page().replace("2026-09-07", "2026-02-30"), "ecupl_script", "", emptyList()))
        assertEquals(emptyList<Triple<Int, String, String>>(), JwSourcePeriods.resolve(page().substringAfter("<script>").substringBefore("</script>"), "ecupl_script", "", emptyList()))
    }
}

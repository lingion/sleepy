package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

/** Synthetic protocol fixtures. Expected occurrences come from the index assignments themselves. */
class JwClassicEamsOccurrenceTest {
    private fun page(indices: String, name: String = "\"离散数学\""): String = """
        <table id="manualArrangeCourseTable"></table><script>
        var unitCount = 12;
        activity = new TaskActivity("1","教师甲","C01",$name,"R01","教室甲","0101",null);
        $indices
        table0.marshalTable(2,1,20);
        </script>
    """.trimIndent()

    private fun occurrences(source: String): Set<List<Int>> =
        JwClassicEamsParser(source).generateCourseList().flatMap { course ->
            (course.startWeek..course.endWeek).flatMap { week ->
                (course.startNode..course.endNode).map { node -> listOf(week, course.day, node) }
            }
        }.toSet()

    @Test fun `one activity retains every weekday and disconnected period`() {
        val source = page("""
            index = 0*unitCount+0;
            index = 0*unitCount+1;
            index = 0*unitCount+4;
            index = 0*unitCount+5;
            index = 2*unitCount+2;
            index = 2*unitCount+3;
        """)
        val expected = listOf(1, 3).flatMap { week ->
            listOf(1 to 1, 1 to 2, 1 to 5, 1 to 6, 3 to 3, 3 to 4)
                .map { (day, node) -> listOf(week, day, node) }
        }.toSet()
        assertEquals(expected, occurrences(source))
    }

    @Test fun `single quoted comma and parentheses remain inside course name`() {
        val courses = JwClassicEamsParser(page("index = 1*unitCount+2;", "'概率论,下(实验)'"))
            .generateCourseList()
        assertEquals(2, courses.size)
        assertEquals(setOf("概率论,下(实验)"), courses.map { it.name }.toSet())
        assertEquals(setOf("教师甲"), courses.map { it.teacher }.toSet())
        assertEquals(setOf("教室甲"), courses.map { it.room }.toSet())
    }

    @Test fun `duplicate assignments do not duplicate occurrences or bridge gaps`() {
        val source = page("index = 25; index = 25; index = 28;")
        assertEquals(setOf(listOf(1, 3, 2), listOf(1, 3, 5), listOf(3, 3, 2), listOf(3, 3, 5)), occurrences(source))
    }

    @Test fun `missing teacher does not discard a scheduled class`() {
        val source = page("index = 0*unitCount+0;").replace("\"教师甲\"", "\"\"")
        val courses = JwClassicEamsParser(source).generateCourseList()
        assertEquals(2, courses.size)
        assertEquals(setOf(""), courses.map { it.teacher }.toSet())
    }

    @Test fun `each course name expression uses its own preceding declaration`() {
        val source = """<script>var unitCount = 12;
            var courseNameLessonNo = "课程甲";
            activity = new TaskActivity("1","教师甲","C1",this.courseNameLessonNo,"R1","教室甲","01",null);
            index = 0*unitCount+0;
            var courseNameLessonNo = "课程乙";
            activity = new TaskActivity("2","教师乙","C2",this.courseNameLessonNo,"R2","教室乙","001",null);
            index = 1*unitCount+2;
            table0.marshalTable(2,1,20);</script>"""
        assertEquals(listOf(
            JwCourse("课程甲", "教室甲", "教师甲", 1, 1, 1, 1, 1),
            JwCourse("课程乙", "教室乙", "教师乙", 2, 3, 3, 2, 2)
        ), JwClassicEamsParser(source).generateCourseList())
    }
}

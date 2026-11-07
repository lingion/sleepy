package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwClassicEamsInputIntegrityTest {
    private fun page(bitmap: String = "0111", unitCount: Int = 12, indices: String = "index=0*unitCount+0;") = """
        <script>var unitCount=$unitCount;
        activity=new TaskActivity("T1","甲","C1","数学","R1","A101","$bitmap");
        $indices table0.marshalTable(2,1,20);</script>
    """

    @Test fun `corrupt binary week strings cannot yield partial believable weeks`() {
        for (bitmap in listOf("01x1", "01 1", "1-16", "111111111111111111111118")) {
            assertEquals(bitmap, emptyList<JwCourse>(), JwClassicEamsParser(page(bitmap)).generateCourseList())
        }
    }

    @Test fun `zero unit count and invalid coordinates cannot enter the import draft`() {
        assertEquals(emptyList<JwCourse>(), JwClassicEamsParser(page(unitCount=0, indices="index=1;")).generateCourseList())
        assertEquals(emptyList<JwCourse>(), JwClassicEamsParser(page(indices="index=7*unitCount+0; index=0*unitCount+12;")).generateCourseList())
    }

    @Test fun `teacher array resolves beyond arbitrary text window and supports quoted keys`() {
        val source = page().replace("activity=new", "var actTeachers=[{'id':1,'name':'甲'},{\"name\":\"乙\"}];\n/*" + "注".repeat(2600) + "*/\nactivity=new")
            .replace("\"T1\",\"甲\"", "actTeacherId.join(','),actTeacherName.join(',')")
        assertEquals((1..3).map { JwCourse("数学", "A101", "甲/乙", 1, 1, 1, it, it) },
            JwClassicEamsParser(source).generateCourseList())
    }

    @Test fun `explicit numeric multiplier is evaluated before decoding linear position`() {
        assertEquals((1..3).map { JwCourse("数学", "A101", "甲", 2, 3, 3, it, it) },
            JwClassicEamsParser(page(indices="index=1*14+0;")).generateCourseList())
    }
}

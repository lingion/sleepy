package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwKingosoftOccurrenceTest {
    private fun lesson(nodes: String = "3-4", room: String = "A", credit: String = "2.5") =
        """{"kcmc":"实验","skdd":"$room","rkjs":"张/李","jcxx":"$nodes","xf":"$credit"}"""

    private fun assertRoutes(source: String, expected: List<JwCourse>) {
        assertEquals(expected, JwParserRegistry.parserFor("kingosoft", source).generateCourseList())
        assertEquals(expected, JwParserRegistry.selectBest(source, "kingosoft").first)
        assertEquals(expected, JwImportViewModel.parseHtmlStatic(source, "kingosoft").first)
    }

    @Test fun `null and empty week buckets keep their original week positions`() {
        val l = lesson()
        val source = """[null,"",{"week7":[$l,$l]},null,{"week7":[$l]}]"""
        assertRoutes(source, listOf(JwCourse("实验", "A", "张/李", 7, 3, 4, 3, 5, 1, credit = 2.5f)))
    }

    @Test fun `section and room changes never cross product with weeks`() {
        val source = """[{"week1":[${lesson()}],"week2":[${lesson("7")}]},{"week1":[${lesson("5-6", "B")}]},{"week1":[${lesson()}]}]"""
        assertRoutes(source, listOf(
            JwCourse("实验", "A", "张/李", 1, 3, 4, 1, 3, 1, credit = 2.5f),
            JwCourse("实验", "A", "张/李", 2, 7, 7, 1, 1, credit = 2.5f),
            JwCourse("实验", "B", "张/李", 1, 5, 6, 2, 2, credit = 2.5f),
        ))
    }

    @Test fun `invalid sections never manufacture a period`() {
        for (nodes in listOf("", "0", "-1", "5-2", "1,3", "1-x", "1-2-5"))
            assertRoutes("""[{"week1":[${lesson(nodes)}]}]""", emptyList())
    }

    @Test fun `non finite credits cannot enter course entities`() {
        for (credit in listOf("NaN", "Infinity", "-1", "invalid"))
            assertRoutes("""[{"week1":[${lesson(credit = credit)}]}]""",
                listOf(JwCourse("实验", "A", "张/李", 1, 3, 4, 1, 1)))
    }
}

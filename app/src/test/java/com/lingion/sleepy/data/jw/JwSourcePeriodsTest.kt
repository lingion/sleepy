package com.lingion.sleepy.data.jw

import org.junit.Assert.*
import org.junit.Test

class JwSourcePeriodsTest {
    private fun resolve(name: String, className: String) = JwSourcePeriods.resolve(
        """[{"skbjmc":"$className"}]""", "fstvc", name, emptyList())

    @Test fun `FSTVC staggered mornings are restricted to exact school and declared class`() {
        val first = resolve("福州软件职业技术学院", "2026软件技术一班")
        val second = resolve("福州软件职业技术学院", "2026艺术设计一班")
        assertEquals(12, first.size)
        assertEquals(Triple(1,"08:25","09:10"), first.first())
        assertEquals(Triple(4,"11:00","11:45"), first[3])
        assertEquals(Triple(1,"08:45","09:30"), second.first())
        assertEquals(Triple(4,"11:20","12:05"), second[3])
        assertEquals(first.drop(4), second.drop(4))
        assertEquals(Triple(12,"21:15","22:00"), first.last())
        assertTrue(resolve("福州职业技术学院", "软件技术一班").isEmpty())
        assertTrue(resolve("福州软件职业技术学院", "").isEmpty())
        val mixed = """[{"skbjmc":"软件技术一班"},{"skbjmc":"艺术设计一班"}]"""
        assertTrue(JwSourcePeriods.resolve(mixed,"fstvc","福州软件职业技术学院",emptyList()).isEmpty())
    }

    @Test fun `GDBH source has ten clock periods even when course data uses twelve`() {
        val periods = JwSourcePeriods.resolve("{}","gdbh","桂林电子科技大学北海校区",emptyList())
        assertEquals((1..10).toList(), periods.map { it.first })
        assertEquals(Triple(1,"08:25","09:10"),periods.first())
        assertEquals(Triple(10,"20:35","21:20"),periods.last())
    }

    @Test fun `captured current period table always takes precedence over APK defaults`() {
        val actual = listOf(Triple(1,"09:00","09:45"),Triple(2,"10:00","10:45"))
        for (type in listOf("gdbh","fstvc","unknown")) {
            assertEquals(actual,JwSourcePeriods.resolve("[]",type,"福州软件职业技术学院",actual))
        }
        assertTrue(JwSourcePeriods.resolve("[]","unknown","",emptyList()).isEmpty())
    }
    @Test fun `WIST defaults require its school identity and preserve all twelve clocks`() {
        val expected = listOf("08:10-08:55", "09:05-09:50", "10:10-10:55", "11:05-11:50",
            "12:30-13:10", "13:20-14:00", "14:00-14:45", "14:55-15:40", "16:00-16:45",
            "16:55-17:40", "19:00-19:45", "19:55-20:40").mapIndexed { i,t -> Triple(i+1,t.substringBefore('-'),t.substringAfter('-')) }
        assertEquals(expected,JwSourcePeriods.resolve("","wist","武汉船舶职业技术学院（主校区）",emptyList()))
        assertTrue(JwSourcePeriods.resolve("","wist","其他学校",emptyList()).isEmpty())
        val captured = listOf(Triple(1,"07:00","07:45"))
        assertEquals(captured,JwSourcePeriods.resolve("","wist","武汉船舶职业技术学院",captured))
    }

}

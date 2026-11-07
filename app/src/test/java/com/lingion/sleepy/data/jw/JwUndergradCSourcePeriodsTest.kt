package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JwUndergradCSourcePeriodsTest {
    private fun resolve(type: String) = JwSourcePeriods.resolve("", type, "", emptyList())
    @Test fun `captured periods override every source default`() {
        val captured = listOf(Triple(1,"07:00","07:45"))
        for(type in listOf("lngd","yzzy","thu")) assertEquals(captured,JwSourcePeriods.resolve("",type,"",captured))
    }
    @Test fun `lngd all twelve explicit source periods`() {
        assertEquals(listOf(Triple(1,"08:20","09:05"),Triple(2,"09:15","10:00"),Triple(3,"10:10","10:55"),Triple(4,"11:05","11:50"),
            Triple(5,"13:20","14:05"),Triple(6,"14:15","15:00"),Triple(7,"15:10","15:55"),Triple(8,"16:05","16:50"),
            Triple(9,"17:15","18:00"),Triple(10,"18:10","18:55"),Triple(11,"19:05","19:50"),Triple(12,"20:00","20:45")),resolve("lngd"))
    }
    @Test fun `yzzy thirteen periods keep afternoon boundary and evening gap`() {
        assertEquals(listOf(Triple(1,"08:00","08:40"),Triple(2,"08:50","09:30"),Triple(3,"09:40","10:20"),Triple(4,"10:30","11:10"),
            Triple(5,"11:20","12:00"),Triple(6,"12:50","13:30"),Triple(7,"13:40","14:20"),Triple(8,"14:30","15:10"),
            Triple(9,"15:20","16:00"),Triple(10,"16:10","16:50"),Triple(11,"17:00","17:40"),Triple(12,"19:30","20:10"),Triple(13,"20:20","21:00")),resolve("yzzy"))
    }
    @Test fun `thu fourteen source periods and foreign types remain empty`() {
        assertEquals(listOf(Triple(1,"08:00","08:45"),Triple(2,"08:50","09:35"),Triple(3,"09:50","10:35"),Triple(4,"10:40","11:25"),
            Triple(5,"11:30","12:15"),Triple(6,"13:30","14:15"),Triple(7,"14:20","15:05"),Triple(8,"15:20","16:05"),
            Triple(9,"16:10","16:55"),Triple(10,"17:05","17:50"),Triple(11,"17:55","18:40"),Triple(12,"19:20","20:05"),
            Triple(13,"20:10","20:55"),Triple(14,"21:00","21:45")),resolve("thu"))
        assertTrue(resolve("unknown").isEmpty())
    }
}

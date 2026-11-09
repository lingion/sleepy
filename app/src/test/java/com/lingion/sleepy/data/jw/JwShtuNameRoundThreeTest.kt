package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwShtuNameRoundThreeTest {
    @Test fun `ambiguous class digits preserve label while separately delimited class suffix can be removed`() {
        val names=listOf("统计2班","数学分析21班","数学分析2 1班","数学分析2（12班）","123")
        val html="<table id='div-table'><tr><td>节次</td><td>周一</td></tr>"+
            names.mapIndexed { index,name -> "<tr><td>${index+1}</td><td>$name<br>王<br>A<br>第5周</td></tr>" }.joinToString("")+"</table>"
        val actual=JwPostgradSchoolParser(html,"shtu_post").generateCourseList()
        assertEquals(listOf(JwCourse("统计2班","A","王",1,1,1,5,5),JwCourse("数学分析21班","A","王",1,2,2,5,5),
            JwCourse("数学分析2","A","王",1,3,3,5,5),JwCourse("数学分析2","A","王",1,4,4,5,5),JwCourse("123","A","王",1,5,5,5,5)),actual)
        val occurrences=actual.flatMap { c -> (c.startWeek..c.endWeek).flatMap { week ->
            (c.startNode..c.endNode).map { node -> "${c.name}|${c.teacher}|${c.room}|${c.day}|$node|$week" }
        } }.sorted()
        assertEquals(listOf("统计2班|王|A|1|1|5","数学分析21班|王|A|1|2|5","数学分析2|王|A|1|3|5","数学分析2|王|A|1|4|5","123|王|A|1|5|5").sorted(),occurrences)
    }
}

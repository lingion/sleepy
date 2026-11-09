package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

/** Synthetic cases from WenqiOfficial/SHOU-URP-AiSchedule at c0a330f3.
 * parser.js reads coureName on each timeAndPlaceList item and the teacher on its parent.
 */
class JwNewUrpSourceAliasTest {
    private fun parse(courses: String): List<JwCourse> =
        JwNewUrpParser("""{"dateList":[{"selectCourseList":[$courses]}]}""").generateCourseList()

    @Test fun `nested source names keep each arrangement and sparse weeks`() {
        val source = """{"attendClassTeacher":" 张／李 ","timeAndPlaceList":[
            {"coureName":" 海洋实验 ","classDay":"2","classSessions":"7","continuingSession":"2","classWeek":"10101","campusName":"临港","teachingBuildingName":"实验楼","classroomName":"301"},
            {"coureName":" 海洋讨论 ","classDay":7,"classSessions":3,"continuingSession":1,"classWeek":"0100101","campusName":"军工路","teachingBuildingName":"A楼","classroomName":"201"}
        ]}"""
        assertEquals(listOf(
            JwCourse("海洋实验", "临港实验楼301", "张／李", 2, 7, 8, 1, 5, 1),
            JwCourse("海洋讨论", "军工路A楼201", "张／李", 7, 3, 3, 2, 2),
            JwCourse("海洋讨论", "军工路A楼201", "张／李", 7, 3, 3, 5, 7, 1),
        ), parse(source))
    }

    @Test fun `existing top level name has priority over nested source name`() {
        val source = """{"courseName":" 顶层名称 ","attendClassTeacher":"教师甲","timeAndPlaceList":[
            {"coureName":"另一名称","classDay":3,"classSessions":11,"continuingSession":3,"classWeek":"010101","campusName":"东","teachingBuildingName":"B","classroomName":"502"}
        ]}"""
        assertEquals(listOf(JwCourse("顶层名称", "东B502", "教师甲", 3, 11, 13, 2, 6, 2)), parse(source))
    }

    @Test fun `blank top level name falls back separately without leaking between siblings`() {
        val source = """{"courseName":" ","attendClassTeacher":"教师乙","timeAndPlaceList":[
            {"coureName":"实验","classDay":1,"classSessions":1,"continuingSession":1,"classWeek":"1","classroomName":"A"},
            {"classDay":2,"classSessions":3,"continuingSession":1,"classWeek":"1","classroomName":"B"},
            {"coureName":" ","classDay":3,"classSessions":5,"continuingSession":1,"classWeek":"1","classroomName":"C"},
            {"coureName":"实验","classDay":4,"classSessions":7,"continuingSession":1,"classWeek":"01","classroomName":"D"}
        ]}"""
        assertEquals(listOf(
            JwCourse("实验", "A", "教师乙", 1, 1, 1, 1, 1),
            JwCourse("实验", "D", "教师乙", 4, 7, 7, 2, 2),
        ), parse(source))
    }

    @Test fun `nested name never rescues invalid explicit coordinates`() {
        for (bad in listOf(
            "\"classDay\":0,\"classSessions\":3,\"continuingSession\":2,\"classWeek\":\"101\"",
            "\"classDay\":1,\"classSessions\":0,\"continuingSession\":2,\"classWeek\":\"101\"",
            "\"classDay\":1,\"classSessions\":3,\"continuingSession\":0,\"classWeek\":\"101\"",
            "\"classDay\":1,\"classSessions\":3,\"continuingSession\":\"bad\",\"classWeek\":\"101\"",
            "\"classDay\":1,\"classSessions\":3,\"continuingSession\":2,\"classWeek\":\"10x\""
        )) {
            assertEquals(bad, emptyList<JwCourse>(), parse("""{"timeAndPlaceList":[{"coureName":"坏安排",$bad}]}"""))
        }
    }
}

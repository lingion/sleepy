package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FourSchoolProtocolContractTest {
    @Test
    fun `school URLs route to their dedicated protocols`() {
        assertEquals(JwProtocol.TYPE_CLASSIC_EAMS,
            JwImportViewModel.detectProtocolFromUrlForTest("https://tam.nwupl.edu.cn/eams/courseTableForStd.action"))
        assertEquals(JwProtocol.TYPE_CLASSIC_EAMS,
            JwImportViewModel.detectProtocolFromUrlForTest("https://lxjw.lixin.edu.cn/edu/lesson/std/timetable!courseTable.action"))
        assertEquals(JwProtocol.TYPE_KUST,
            JwImportViewModel.detectProtocolFromUrlForTest("https://i.kust.edu.cn/"))
        assertEquals(JwProtocol.TYPE_NUIT,
            JwImportViewModel.detectProtocolFromUrlForTest("http://jw.nuit.edu.cn/jwapp/sys/homeapp/home/index.html"))
    }

    @Test
    fun `captured JSON fingerprints do not fall through to unrelated parsers`() {
        assertEquals(JwProtocol.TYPE_NUIT,
            JwImportViewModel.detectProtocolFromHtmlForTest("{\"courseName\":\"Physics\",\"classDateAndPlace\":\"2-8周/星期一/第1节-第2节\"}"))
        assertEquals(JwProtocol.TYPE_KUST,
            JwImportViewModel.detectProtocolFromHtmlForTest("{\"data\":{\"resultsJsonArr\":[],\"zs\":\"1\"}}"))
    }

    @Test
    fun `new protocols are registered and represented consistently`() {
        assertTrue(JwProtocol.ALL_TYPES.contains(JwProtocol.TYPE_KUST))
        assertTrue(JwProtocol.ALL_TYPES.contains(JwProtocol.TYPE_NUIT))
        assertEquals("昆明理工大学门户", JwProtocol.displayName(JwProtocol.TYPE_KUST))
        assertEquals("广东东软学院教务", JwProtocol.displayName(JwProtocol.TYPE_NUIT))
        assertEquals(0, JwParserRegistry.parserFor(JwProtocol.TYPE_KUST, "{}").generateCourseList().size)
        assertEquals(0, JwParserRegistry.parserFor(JwProtocol.TYPE_NUIT, "{}").generateCourseList().size)
    }
}

package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JwEcuplRenamedScriptIdentifiersTest {
    private fun sample(path: String) = requireNotNull(javaClass.classLoader?.getResource(path)).readText()

    @Test fun `renamed activity binds only its own clock slots`() {
        val source = sample("jw/ecupl_script_sample.html")
        val expected = JwEcuplScriptParser(source).generateCourseList()
        assertTrue(expected.isNotEmpty())
        val changed = source.replace(Regex("""\bactivity\b"""), "教学活动")
            .replace("addActivityByTime(教学活动,1,800,940);",
                "addActivityByTime(历史教学活动,7,800,940);addActivityByTime(教学活动,1,800,940);")
        assertEquals(expected, JwEcuplScriptParser(changed).generateCourseList())
    }

    @Test fun `declaration-only binding accepts Unicode names`() {
        val source = sample("jw/ecupl_script_sample.html")
        val expected = JwEcuplScriptParser(source).generateCourseList()
        val changed = source.replace("var activity;", "let 当前活动;")
            .replace("addActivityByTime(activity,", "addActivityByTime(当前活动,")
        assertEquals(expected, JwEcuplScriptParser(changed).generateCourseList())
    }
}

package com.lingion.sleepy.data.jw

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** The collector explicitly lists every requested week; incomplete envelopes must not masquerade as full schedules. */
class JwCfNewCollectorIntegrityTest {
    private fun envelope(): JSONObject {
        val row = JSONObject("""{"kcmc":"实验","teaxms":"张","jxcdmc":"A","xq":"3","ps":"1","pe":"2","zc":"1"}""")
        return JSONObject().put("sleepyCfNtss", true).put("requestedWeeks", JSONArray(listOf(1,25)))
            .put("weeks", JSONArray(listOf(JSONObject().put("week",1).put("rows",JSONArray(listOf(row))),
                JSONObject().put("week",25).put("rows",JSONArray()))))
    }

    @Test fun `all declared weeks are required including empty final weeks`() {
        val complete = envelope()
        assertEquals(listOf(JwCourse("实验","A","张",3,1,2,1,1)),JwCfNewParser(complete.toString()).generateCourseList())
        complete.getJSONArray("weeks").remove(1)
        assertTrue(runCatching { JwCfNewParser(complete.toString()).generateCourseList() }.isFailure)
    }

    @Test fun `malformed duplicate unexpected and missing week buckets reject the entire capture`() {
        val invalid = listOf(
            envelope().apply { getJSONArray("weeks").getJSONObject(1).put("week",1) },
            envelope().apply { getJSONArray("weeks").getJSONObject(1).put("week",24) },
            envelope().apply { getJSONArray("weeks").getJSONObject(1).put("week",25.5) },
            envelope().apply { getJSONArray("weeks").getJSONObject(1).remove("rows") },
            envelope().apply { getJSONArray("weeks").getJSONObject(1).put("rows",JSONObject()) },
            envelope().put("requestedWeeks",JSONArray(listOf(1,1,25))),
            envelope().put("requestedWeeks",JSONArray(listOf(1,25.5))),
            envelope().put("requestedWeeks",JSONArray(listOf(1,367))),
        )
        for (source in invalid) {
            assertTrue(source.toString(),runCatching { JwCfNewParser(source.toString()).generateCourseList() }.isFailure)
            assertTrue(JwParserRegistry.selectBest(source.toString(), "cf_new").first.isEmpty())
        }
    }
}

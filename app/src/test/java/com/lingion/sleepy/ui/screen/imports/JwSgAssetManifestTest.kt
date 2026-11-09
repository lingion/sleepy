package com.lingion.sleepy.ui.screen.imports

import com.lingion.sleepy.testutil.projectSourceFile
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class JwSgAssetManifestTest {
    @Test fun `all declarations remain reachable in source order`() {
        val text = projectSourceFile("app/src/main/assets/third_party/shiguang/manifest.json").readText()
        val schools = JSONObject(text).getJSONArray("schools")
        val parsed = JwSgAssetBridge.parseManifest(text)
        val expectedCount = (0 until schools.length()).sumOf { schools.getJSONObject(it).getJSONArray("adapters").length() }
        assertEquals(expectedCount, parsed.values.sumOf { it.size })
        val ujs = parsed.getValue("江苏大学")
        assertTrue(ujs.size > 1)
        assertTrue(ujs.any { it.adapterId.contains("YJS", true) || it.adapterName.contains("研究生") })
    }

    @Test fun `same script with different login URL is not deduplicated`() {
        val parsed = JwSgAssetBridge.parseManifest("""{"schools":[{"sgName":"学校Ａ","adapters":[
          {"adapterId":"one","adapterName":"本科","jsPath":"same.js","importUrl":"https://example.edu/a"},
          {"adapterId":"two","adapterName":"研究生","jsPath":"same.js","importUrl":"https://example.edu/b"}
        ]}]}""")
        val entries = parsed.getValue("学校A")
        assertEquals(listOf("one","two"), entries.map { it.adapterId })
        assertEquals(listOf("https://example.edu/a","https://example.edu/b"), entries.map { it.importUrl })
    }

    @Test fun `saved entry is revalidated and obsolete IDs never select a replacement`() {
        val entry = JwSgAssetBridge.SchoolEntry("学校", "same.js", "https://example.edu/a", "one", "本科", "BACHELOR", "school")
        val second = entry.copy(adapterId = "two", adapterName = "研究生")
        assertEquals(entry, JwSgAssetBridge.restoreChoice(listOf(entry), ""))
        assertNull(JwSgAssetBridge.restoreChoice(listOf(entry, second), ""))
        assertEquals(second, JwSgAssetBridge.restoreChoice(listOf(entry, second), "two"))
        assertNull(JwSgAssetBridge.restoreChoice(listOf(entry), "obsolete"))
        assertNull(JwSgAssetBridge.restoreChoice(listOf(entry, entry), "one"))
    }
}

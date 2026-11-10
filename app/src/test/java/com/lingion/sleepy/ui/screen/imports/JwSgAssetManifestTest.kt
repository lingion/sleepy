package com.lingion.sleepy.ui.screen.imports

import com.lingion.sleepy.testutil.projectSourceFile
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class JwSgAssetManifestTest {

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

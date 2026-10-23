package com.lingion.sleepy.ui.screen.edit

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class OccurrenceStringsTest {
    @Test fun allSupportedLocalesTranslateEveryOccurrenceStringWithMatchingPlaceholders() {
        val resources = sequenceOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
        fun strings(locale: String): Map<String, String> {
            val file = File(resources, "$locale/occurrence_strings.xml")
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
            val entries = (0 until nodes.length).map { index ->
                val node = nodes.item(index)
                node.attributes.getNamedItem("name").nodeValue to node.textContent
            }
            assertEquals("$locale has duplicate resource names", entries.size, entries.map { it.first }.toSet().size)
            return entries.toMap()
        }
        val base = strings("values")
        assertTrue(base.isNotEmpty())
        val placeholder = Regex("%[1-9][0-9]*\\\$[sd]")
        for (locale in listOf("values-zh-rCN", "values-zh-rTW", "values-en", "values-ja", "values-es")) {
            val translated = strings(locale)
            assertEquals("$locale has missing or extra keys", base.keys, translated.keys)
            for ((key, value) in translated) {
                assertTrue("$locale/$key is blank", value.isNotBlank())
                assertEquals("$locale/$key has incompatible placeholders",
                    placeholder.findAll(base.getValue(key)).map { it.value }.sorted().toList(),
                    placeholder.findAll(value).map { it.value }.sorted().toList())
            }
        }
    }
}

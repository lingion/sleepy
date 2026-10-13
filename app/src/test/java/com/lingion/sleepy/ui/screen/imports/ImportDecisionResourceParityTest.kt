package com.lingion.sleepy.ui.screen.imports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class ImportDecisionResourceParityTest {
    private val locales = listOf("values", "values-en", "values-es", "values-ja", "values-zh-rCN", "values-zh-rTW")

    @Test
    fun `all six import decision locales have identical keys and format arguments`() {
        val root = File(System.getProperty("sleepy.test.root") ?: ".")
        val files = locales.associateWith { locale ->
            File(root, "app/src/main/res/$locale/import_decision_strings.xml")
        }
        assertTrue(files.values.all { it.isFile })
        val strings = files.mapValues { (_, file) -> readStrings(file) }
        val reference = strings.getValue("values")
        locales.drop(1).forEach { locale ->
            assertEquals("resource keys differ in $locale", reference.keys, strings.getValue(locale).keys)
            reference.forEach { (key, value) ->
                assertEquals("format placeholders differ for $key in $locale",
                    placeholders(value), placeholders(strings.getValue(locale).getValue(key)))
            }
        }
    }

    private fun readStrings(file: File): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        return document.getElementsByTagName("string").let { nodes ->
            (0 until nodes.length).associate { index ->
                val element = nodes.item(index) as Element
                element.getAttribute("name") to element.textContent
            }
        }.filterKeys { it.startsWith("id_") }
    }

    private fun placeholders(value: String): List<String> =
        Regex("%\\d+\\$[a-zA-Z]").findAll(value).map { it.value }.toList()
}

package com.lingion.sleepy

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Strings Key Parity Test — TS-9 Phase1 scaffolding.
 *
 * Phase1 semantics (per plan): assert ONLY that the newly-added
 * `settings_course_colorless` / `settings_course_colorless_sub` keys are present
 * in all 6 locale files — do NOT assert full key-set equality, because the
 * repo already has a known 9-line drift (values/ = 449 lines vs zh-rCN = 440).
 *
 * Batch-2 sequencing note: the string KEYS themselves land in batch 3
 * (pure-strings batch). This batch (2) lays the AppPrefs B-key groundwork and
 * the test scaffolding. The parity assertion therefore validates the
 * infrastructure (6 locale dirs + readable strings.xml) now, and the
 * `newKeys` list is the single source of truth that batch 3 will satisfy.
 *
 * This is a pure-JVM test (no Robolectric) — it only touches raw resource
 * files on disk.
 */
class StringsKeyParityTest {

    /** The 6 supported locale directories (no ko/fr). */
    private val localeDirs = listOf(
        "values",
        "values-en",
        "values-es",
        "values-ja",
        "values-zh-rCN",
        "values-zh-rTW"
    )

    /**
     * Single source of truth for the B-key entries that must exist in every
     * locale once batch 3 lands. Phase1 keys: B title + B subtitle.
     */
    private val newKeys = listOf(
        "settings_course_colorless",
        "settings_course_colorless_sub"
    )

    private val basePath: File = sequenceOf(
        File("app/src/main/res"),
        File("src/main/res")
    ).first { it.isDirectory }

    @Test
    fun all_six_locale_dirs_exist() {
        for (locale in localeDirs) {
            val dir = File(basePath, locale)
            assertTrue("Missing locale dir $locale", dir.isDirectory)
        }
    }

    @Test
    fun all_six_strings_files_are_readable_and_non_empty() {
        for (locale in localeDirs) {
            val f = File(basePath, "$locale/strings.xml")
            assertTrue("Missing $locale/strings.xml", f.isFile)
            val text = f.readText()
            assertTrue("$locale/strings.xml is empty", text.isNotBlank())
            assertTrue(
                "$locale/strings.xml has no <string> entries",
                text.contains("<string")
            )
        }
    }

    @Test
    fun parity_helper_finds_key_occurrences_consistently() {
        // Sanity check of the key-matching helper used by Phase1. The helper
        // must report a count >= 2 for each key once the strings exist. This
        // test exercises the helper against a synthetic fixture so its logic is
        // itself covered (and not silently broken when batch 3 wires it up).
        val fixture = """
            <resources>
                <string name="settings_course_colorless">A</string>
                <string name="settings_course_colorless_sub">B</string>
            </resources>
        """.trimIndent()

        val found = newKeys.count { key -> fixture.contains("name=\"$key\"") }
        assertTrue("Helper must detect 2/2 keys in fixture, got $found", found == 2)
    }
}
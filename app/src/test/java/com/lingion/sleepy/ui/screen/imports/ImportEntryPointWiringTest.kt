package com.lingion.sleepy.ui.screen.imports

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ImportEntryPointWiringTest {
    private fun source(name: String): String {
        val root = File(System.getProperty("sleepy.test.root", "."))
        return File(root, "app/src/main/java/com/lingion/sleepy/ui/screen/imports/$name.kt").readText()
    }

    @Test fun `file and academic imports use the same decision dialog`() {
        val file = source("ImportSheet")
        val academic = source("JwImportActivity")
        assertTrue(file.contains("ImportDecisionDialog("))
        assertTrue(academic.contains("ImportDecisionDialog("))
        assertFalse(file.contains("applyImportPreview("))
        assertFalse(academic.contains("applyImportPreview("))
        assertFalse(academic.contains("jwViewModel.importAsNewTable("))
    }

    @Test fun `academic draft records configuration but retains the original source periods`() {
        val academic = source("JwImportActivity")
        assertTrue(academic.contains("decisionConfigJson = decisionConfiguration"))
        assertTrue(academic.contains("decodeImportDecisionConfiguration(snapshot.decisionConfigJson)"))
        assertTrue(academic.contains("periods = configRows.map"))
        assertTrue(academic.contains("onConfigurationChange ="))
        assertTrue(academic.contains("onApplyingChange = { isApplying = it }"))
    }

    @Test fun `pure period import stays separate and only completes on success`() {
        val file = source("ImportSheet")
        assertTrue(file.contains("currentPreview.parseResult.courses.isEmpty()"))
        assertTrue(file.contains("currentPreview.parseResult.periodTable != null"))
        assertTrue(file.contains("val applied = applyPurePeriodImport("))
        assertTrue(file.contains("if (applied)"))
    }

    @Test fun `returning from a file preview retains its configuration for reopening`() {
        val file = source("ImportSheet")
        assertTrue(file.contains("onDismiss = { decisionVisible = false }"))
        assertTrue(file.contains("initialConfiguration = decisionConfiguration"))
        assertTrue(file.contains("onConfigurationChange = { decisionConfiguration = it }"))
        assertTrue(file.contains("inputText == lastPreviewText"))
    }
}

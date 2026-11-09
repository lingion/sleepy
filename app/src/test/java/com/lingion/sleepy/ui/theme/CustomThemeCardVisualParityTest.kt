package com.lingion.sleepy.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source order complements real-card geometry: edit stays in the first row. */
class CustomThemeCardVisualParityTest {
    private val source by lazy {
        sequenceOf("app/src/main/java", "src/main/java")
            .map { java.io.File(it, "com/lingion/sleepy/ui/screen/mine/AppearanceScreen.kt") }
            .first { it.isFile }.readText()
    }
    private fun body(name: String, end: String): String {
        assertTrue("real $name declaration must exist", source.contains("fun $name("))
        return source.substringAfter("fun $name(").substringBefore(end)
    }
    private val custom get() = body("CustomThemeCard", "/**" )
    private val firstRow get() = custom.substringBefore("Spacer(Modifier.height(12.dp))")

    @Test fun edit_stays_after_swatches_in_first_row() {
        assertTrue(firstRow.indexOf("IconButton(") > firstRow.lastIndexOf("ColorSwatch("))
        assertTrue(firstRow.contains("onClick = onEdit"))
        assertTrue(firstRow.contains("Modifier.weight(1f)"))
        assertFalse(custom.substringAfter("Spacer(Modifier.height(12.dp))").contains("IconButton("))
    }

    @Test fun edit_uses_shared_glass_disc_and_keeps_classic_container_color() {
        assertTrue(source.contains("GlassIconButton as IconButton"))
        assertTrue(firstRow.contains("containerColor = colors.surfaceContainerHighest"))
        assertTrue(firstRow.contains("Icons.Outlined.Edit"))
        assertFalse(firstRow.contains("noRippleGlassClickable"))
    }

    @Test fun preset_and_custom_keep_matching_rows_and_spacing() {
        val preset = body("PresetThemeCard", "private fun ColorSwatch")
        for (needle in listOf(
            "Modifier.padding(16.dp)", "Arrangement.spacedBy(8.dp)",
            "Arrangement.spacedBy(4.dp)", "minHeight = GlassControlLayout.touchTarget",
            "Spacer(Modifier.height(12.dp))", "maxLines = 1", "Modifier.size(20.dp)",
        )) {
            assertTrue("preset: $needle", preset.contains(needle))
            assertTrue("custom: $needle", custom.contains(needle))
        }
        assertTrue(preset.contains("Spacer(Modifier.size(GlassControlLayout.touchTarget))"))
    }
}

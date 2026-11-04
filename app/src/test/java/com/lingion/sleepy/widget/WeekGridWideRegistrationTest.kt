package com.lingion.sleepy.widget

import com.lingion.sleepy.ui.screen.widget.WidgetEditWideGridSection
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class WeekGridWideRegistrationTest {
    @Test fun newEntryIsImmediatelyAfterExistingGridGroupAndBeforeOtherWidgets() {
        val names = ALL_WIDGET_VARIANTS.map { it.receiverClass.simpleName }
        assertEquals(listOf("WeekGridWidgetProvider", "WeekGridSmallWidgetProvider", "WeekGridWideWidgetProvider", "TodayWidgetReceiver"), names.take(4))
        assertEquals(1, names.count { it == "WeekGridWideWidgetProvider" })
        val entry = ALL_WIDGET_VARIANTS.single { it.receiverClass.simpleName == "WeekGridWideWidgetProvider" }
        assertEquals(300, entry.widthDp)
        assertEquals(160, entry.heightDp)
        assertTrue(WidgetUpdater.remoteViewsReceiverClasses.contains(entry.receiverClass))
    }

    @Test fun editorSectionOnlyAppliesToExplicitNewReceiver() {
        assertTrue(WidgetEditWideGridSection.appliesTo("WeekGridWideWidgetProvider"))
        for (name in listOf(null, "WeekGridWidgetProvider", "WeekGridSmallWidgetProvider", "TodayWideWidgetReceiver"))
            assertFalse(WidgetEditWideGridSection.appliesTo(name))
    }

    @Test fun deletionReceiverKeepsBroadcastAliveUntilRearmCompletes() {
        val root = if (File("src/main").isDirectory) File("src/main") else File("app/src/main")
        val source = File(root, "java/com/lingion/sleepy/widget/WeekGridWideWidgetProvider.kt").readText()
        assertTrue(source.contains("AppWidgetManager.ACTION_APPWIDGET_DELETED"))
        assertTrue(source.contains("pending?.finish()"))
        val scheduler = File(root, "java/com/lingion/sleepy/widget/WidgetBoundaryScheduler.kt").readText()
        assertTrue(scheduler.contains("@Synchronized"))
    }

    @Test fun metadataDeclaresExplicitFourByTwoAndPreservesOldDimensions() {
        val root = if (File("src/main/AndroidManifest.xml").exists()) File("src/main") else File("app/src/main")
        val xml = File(root, "res/xml/week_grid_wide_widget_info.xml").readText()
        assertTrue(xml.contains("android:targetCellWidth=\"4\""))
        assertTrue(xml.contains("android:targetCellHeight=\"2\""))
        assertTrue(xml.contains("android:previewLayout=\"@layout/widget_preview_weekgrid_wide\""))
        assertTrue(File(root, "AndroidManifest.xml").readText().contains(".widget.WeekGridWideWidgetProvider"))
        assertTrue(File(root, "res/xml/week_grid_small_widget_info.xml").readText().contains("android:minWidth=\"110dp\""))
        assertTrue(File(root, "res/xml/week_grid_widget_info.xml").readText().contains("android:minHeight=\"250dp\""))
    }
}

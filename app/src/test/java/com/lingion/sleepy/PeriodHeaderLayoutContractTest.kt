package com.lingion.sleepy

import com.lingion.sleepy.util.AppPrefs
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PeriodHeaderLayoutContractTest {
    @Test
    fun persistence_contract_exposes_only_legacy_and_three_line() {
        val source = File("src/main/java/com/lingion/sleepy/util/AppPrefs.kt").readText()
        assertTrue(source.contains("value == \"legacy\" || value == \"three_line\""))
        assertTrue(source.contains("\"three_line\", \"vertical\" -> \"three_line\""))
        assertTrue(source.contains("KEY_PERIOD_HEADER_LAYOUT"))
        assertTrue(AppPrefs.KEY_PERIOD_HEADER_LAYOUT == "period_header_layout")
    }

    @Test
    fun app_and_widget_use_the_same_global_layout_key() {
        val app = File("src/main/java/com/lingion/sleepy/ui/component/CourseTableView.kt").readText()
        val widget = File("src/main/java/com/lingion/sleepy/widget/WeekGridWidgetProvider.kt").readText()
        assertTrue(app.contains("AppPrefs.getPeriodHeaderLayout(context)"))
        assertTrue(widget.contains("AppPrefs.getPeriodHeaderLayout(context)"))
        assertTrue(app.contains("layout = headerLayout"))
        assertTrue(widget.contains("widgetHeaderLayout == \"three_line\""))
    }
}

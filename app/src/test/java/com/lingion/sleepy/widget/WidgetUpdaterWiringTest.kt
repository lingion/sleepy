package com.lingion.sleepy.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ensures data-change broadcasts cover every regular and small widget provider. */
class WidgetUpdaterWiringTest {
    @Test
    fun `refresh receiver list contains all widget variants`() {
        val receivers = WidgetUpdater.remoteViewsReceiverClasses
        val expected = listOf(
            TodayWidgetReceiver::class.java,
            TodaySmallWidgetReceiver::class.java,
            TwoDayWidgetReceiver::class.java,
            TwoDaySmallWidgetReceiver::class.java,
            WeekListWidgetReceiver::class.java,
            WeekListSmallWidgetReceiver::class.java,
            WeekViewWidgetReceiver::class.java,
            WeekViewSmallWidgetReceiver::class.java,
            WeekGridWidgetProvider::class.java,
            WeekGridSmallWidgetProvider::class.java
        )

        assertEquals("all 10 widget providers are registered", 10, receivers.size)
        expected.forEach { receiver ->
            assertTrue("missing ${receiver.simpleName}", receiver in receivers)
        }
    }
}

package com.lingion.sleepy.widget

import android.appwidget.AppWidgetProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R7 wiring test: widget removal must clean the per-widget binding.
 *
 * Every widget receiver — including the five `*SmallWidgetReceiver` shells
 * that inherit everything — must override [AppWidgetProvider.onDeleted]
 * somewhere in its own class hierarchy (never just inherit the no-op from
 * AppWidgetProvider), and the override must live in one of the five known
 * base receiver classes that call [WidgetBindingStore.remove].
 *
 * The call itself can't be asserted without Robolectric (explicitly banned
 * by the repo); the body of each base's onDeleted is `for (id in ids)
 * WidgetBindingStore.remove(context, id)` and was verified by direct code
 * audit of the five base classes. What this test locks is the wiring:
 * no receiver class may silently lose the override through refactoring.
 */
class WidgetBindingCleanupWiringTest {

    /** Known base classes whose onDeleted calls WidgetBindingStore.remove. */
    private val cleanupBases: Set<Class<out AppWidgetProvider>> = setOf(
        WeekGridWidgetProvider::class.java,
        TodayWidgetReceiver::class.java,
        WeekListWidgetReceiver::class.java,
        WeekViewWidgetReceiver::class.java,
        TwoDayWidgetReceiver::class.java
    )

    /**
     * The class in [clazz]'s hierarchy that declares onDeleted, stopping
     * before AppWidgetProvider. Null = would inherit the framework no-op.
     */
    private fun onDeletedOwner(clazz: Class<out AppWidgetProvider>): Class<*>? {
        var c: Class<*>? = clazz
        while (c != null && c != AppWidgetProvider::class.java) {
            c.declaredMethods.firstOrNull {
                it.name == "onDeleted" && it.parameterTypes.size == 2
            }?.let { return c }
            c = c.superclass
        }
        return null
    }

    @Test
    fun `every variant receiver overrides onDeleted outside the framework`() {
        for (variant in ALL_WIDGET_VARIANTS) {
            val owner = onDeletedOwner(variant.receiverClass)
            assertNotNull(
                "${variant.receiverClass.simpleName} inherits the framework no-op " +
                    "onDeleted — removing the widget would leave a stale binding (R7)",
                owner
            )
            assertTrue(
                "${variant.receiverClass.simpleName} overrides onDeleted in " +
                    "${owner?.simpleName}, which is not one of the known cleanup bases",
                onDeletedOwner(variant.receiverClass) in cleanupBases
            )
        }
    }

    @Test
    fun `each cleanup base declares its own onDeleted override`() {
        for (base in cleanupBases) {
            assertEquals(
                "${base.simpleName} must declare onDeleted directly",
                base,
                onDeletedOwner(base)
            )
        }
    }

    @Test
    fun `all ten variants are covered by the five cleanup bases`() {
        // 10 receivers ÷ 2 (base + small shell per layout) = 5 bases
        assertEquals(5, cleanupBases.size)
        assertEquals(10, ALL_WIDGET_VARIANTS.size)
    }
}
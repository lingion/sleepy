package com.lingion.sleepy.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StartViewTodayPrefTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("sleepy_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun startViewAcceptsFullCardsAndTodayAndEmitsFlow() = runBlocking {
        assertEquals("cards", AppPrefs.getStartView(context))

        AppPrefs.setStartView(context, "today")
        assertEquals("today", AppPrefs.getStartView(context))
        assertEquals("today", AppPrefs.startViewFlow(context).first())

        AppPrefs.setStartView(context, "full")
        assertEquals("full", AppPrefs.getStartView(context))
    }
}

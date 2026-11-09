package com.lingion.sleepy.util

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class HolidayMemorySnapshotTest {
    @Test fun snapshotOnlyExposesAlreadyLoadedYearsIncludingEmptyYears() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("sleepy_prefs", Context.MODE_PRIVATE)
        val year = 2043
        prefs.edit()
            .putBoolean("holiday_entries_${year}_loaded", true)
            .putString("holiday_entries_$year", """[{"date":"2043-01-01","name":"New Year","type":"public_holiday"}]""")
            .putBoolean("holiday_entries_${year + 1}_loaded", true)
            .putString("holiday_entries_${year + 1}", "[]")
            .commit()

        // A first-frame peek must not read/parse even an available disk cache.
        assertNull(HolidayManager.peekYearEntries(year))
        assertNull(HolidayManager.peekYearEntries(year + 1))
        val entries = HolidayManager.getYearEntries(context, year)
        assertEquals(LocalDate.of(year, 1, 1), entries.single().date)
        assertSame(entries, HolidayManager.peekYearEntries(year))
        HolidayManager.getYearEntries(context, year + 1)
        assertEquals(emptyList<HolidayEntry>(), HolidayManager.peekYearEntries(year + 1))
        assertNull(HolidayManager.peekYearEntries(year + 2))
    }
}

package com.lingion.sleepy.ui.component

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lingion.sleepy.MealBreakPreviewFixture
import com.lingion.sleepy.ui.theme.SleepyThemeProvider
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.TimeTableUtils
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/** Captures CardsGridView itself on-device so the long-break layout can be visually reviewed. */
@RunWith(AndroidJUnit4::class)
class CourseTableMealBreakRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun exportsActualComposeTimetableWithMealBreakSeparators() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val oldSeparators = AppPrefs.isGridShowSeparators(context)
        val oldLongBreakSpacing = AppPrefs.isGridLongBreakSpacing(context)
        AppPrefs.setGridShowSeparators(context, true)
        AppPrefs.setGridLongBreakSpacing(context, true)

        val timeJson = MealBreakPreviewFixture.timeJson
        val courses = MealBreakPreviewFixture.courses

        try {
            composeRule.setContent {
                SleepyThemeProvider {
                    CardsGridView(
                        courses = courses,
                        timeSlots = TimeTableUtils.timeSlotsFor(timeJson),
                        visibleDays = setOf(1, 2, 3, 4, 5),
                        onCourseClick = {},
                        modifier = Modifier.fillMaxSize(),
                        timeJson = timeJson
                    )
                }
            }
            composeRule.waitForIdle()
            val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
            try {
                val preview = File(context.cacheDir, "sleepy-course-table-meal-break.png")
                FileOutputStream(preview).use { output ->
                    assertTrue("Compose preview PNG must be written",
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                }
                assertTrue("Compose render must have a visible viewport", bitmap.width > 0 && bitmap.height > 0)
                println("COURSE_TABLE_MEAL_BREAK_PREVIEW=${preview.absolutePath}")
            } finally {
                bitmap.recycle()
            }
        } finally {
            AppPrefs.setGridShowSeparators(context, oldSeparators)
            AppPrefs.setGridLongBreakSpacing(context, oldLongBreakSpacing)
        }
    }

}

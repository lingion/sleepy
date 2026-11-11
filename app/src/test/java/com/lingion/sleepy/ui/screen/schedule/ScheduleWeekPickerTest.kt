package com.lingion.sleepy.ui.screen.schedule

import android.view.Gravity
import android.view.WindowManager
import android.view.inspector.WindowInspector
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-w360dp-h640dp-mdpi")
class ScheduleWeekPickerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun materialPickerUsesCenteredWindowAndSelectsWeek() = checkPicker(UiStyle.Classic)
    @Test fun glassPickerUsesCenteredWindowAndSelectsWeek() = checkPicker(UiStyle.LiquidGlass)

    private fun checkPicker(style: UiStyle) {
        var selected = 0
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides style) {
                    ScheduleWeekPicker(2, 60, {}, { selected = it })
                }
            }
        }
        compose.onNodeWithTag("schedule_week_dialog").assertIsDisplayed()
        compose.onNodeWithTag("schedule_week#2").assertIsSelected()
        compose.runOnIdle {
            val window = WindowInspector.getGlobalWindowViews()
                .first { it.javaClass.simpleName == "DecorView" &&
                    it.findViewById<android.view.View>(android.R.id.content)
                        ?.let { content -> content is android.view.ViewGroup &&
                            (0 until content.childCount).any { content.getChildAt(it).javaClass.simpleName == "DialogLayout" } } == true }
            val params = window.layoutParams as WindowManager.LayoutParams
            assertEquals(Gravity.CENTER, params.gravity and (Gravity.HORIZONTAL_GRAVITY_MASK or Gravity.VERTICAL_GRAVITY_MASK))
        }
        compose.onNodeWithTag("schedule_week#60").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(60, selected) }
    }
}

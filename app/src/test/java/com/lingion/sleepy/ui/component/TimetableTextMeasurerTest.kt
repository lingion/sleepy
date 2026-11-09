package com.lingion.sleepy.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.lingion.sleepy.MainTabContent
import com.lingion.sleepy.Tab
import com.lingion.sleepy.ui.theme.SleepyThemeProvider
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class TimetableTextMeasurerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun headerMeasurementsSurviveTabDisposalButFollowDisplayConfiguration() {
        val tab = mutableStateOf(Tab.Schedule)
        val density = mutableStateOf(Density(2f, 1f))
        val direction = mutableStateOf(LayoutDirection.Ltr)
        var measured: TextLayoutResult? = null
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides density.value, LocalLayoutDirection provides direction.value) {
                SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                    Box(Modifier.size(240.dp, 320.dp)) {
                        MainTabContent(tab.value) { current ->
                            if (current == Tab.Schedule) {
                                val measurer = rememberTimetableTextMeasurer()
                                measured = measurer.measure("08:00", TextStyle(fontSize = 12.sp))
                                // More than the default eight entries: a normal timetable has
                                // many distinct times/labels, reused by both geometry and cells.
                                repeat(48) { measurer.measure("Header $it", TextStyle(fontSize = 12.sp)) }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val original = measured!!.multiParagraph
        compose.runOnIdle { tab.value = Tab.Today }
        compose.waitForIdle()
        compose.runOnIdle { tab.value = Tab.Schedule }
        compose.waitForIdle()
        assertSame("Returning must reuse the measured paragraph", original, measured.multiParagraph)

        compose.runOnIdle { density.value = Density(2f, 1.3f) }
        compose.waitForIdle()
        val larger = measured.multiParagraph
        assertNotSame("Font scale changes must not reuse old geometry", original, larger)
        compose.runOnIdle { direction.value = LayoutDirection.Rtl }
        compose.waitForIdle()
        assertNotSame("Layout direction changes must not reuse old geometry", larger, measured.multiParagraph)
    }
}

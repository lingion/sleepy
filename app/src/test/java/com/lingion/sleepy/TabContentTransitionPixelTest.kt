package com.lingion.sleepy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.nav.RightHalfTabContent
import com.lingion.sleepy.ui.theme.SleepyThemeProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Runs the production containers with sparse pages: each marker sits in the other page's gap. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-port-xhdpi")
class TabContentTransitionPixelTest {
    @get:Rule val compose = createComposeRule()

    @Test fun mainTabsLight() = verifyFrames(rightPane = false, dark = false)
    @Test fun mainTabsDark() = verifyFrames(rightPane = false, dark = true)
    @Test fun rightPaneLight() = verifyFrames(rightPane = true, dark = false)
    @Test fun rightPaneDark() = verifyFrames(rightPane = true, dark = true)

    private fun verifyFrames(rightPane: Boolean, dark: Boolean) {
        val first = if (rightPane) Tab.Today else Tab.Schedule
        val second = if (rightPane) Tab.Manage else Tab.Today
        val current = mutableStateOf(first)
        var background = Color.Unspecified
        compose.setContent {
            SleepyThemeProvider(darkTheme = dark, themeKey = "default") {
                background = MaterialTheme.colorScheme.background
                // A third color catches missing coverage independently of either marker.
                Box(Modifier.size(240.dp, 320.dp).background(Color.Blue).testTag("viewport")) {
                    val page: @Composable (Tab) -> Unit = { tab ->
                        Canvas(Modifier.fillMaxSize().testTag(tab.name)) {
                            drawRect(
                                color = if (tab == first) Color.Red else Color.Green,
                                topLeft = Offset(size.width * (if (tab == first) .15f else .65f), size.height * .3f),
                                size = Size(size.width * .2f, size.height * .4f),
                            )
                        }
                    }
                    if (rightPane) RightHalfTabContent(current.value, page)
                    else MainTabContent(current.value, page)
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false

        fun assertPixels(target: Tab) {
            val pixels = compose.onNodeWithTag("viewport").captureToImage().toPixelMap()
            val markerX = if (target == first) .25f else .75f
            val blankX = if (target == first) .75f else .25f
            val marker = if (target == first) Color.Red else Color.Green
            // Sample patches well inside both scaled pages, never their exposed edges.
            for (dx in -3..3) for (dy in -3..3) {
                val y = pixels.height / 2 + dy
                assertEquals("Target marker must be visible ($target)", marker.toArgb(),
                    pixels[(pixels.width * markerX).toInt() + dx, y].toArgb())
                assertEquals("Outgoing marker must not bleed into $target's blank area", background.toArgb(),
                    pixels[(pixels.width * blankX).toInt() + dx, y].toArgb())
            }
        }

        fun switchAndSample(target: Tab) {
            compose.runOnIdle { current.value = target }
            repeat(2) {
                pumpFrames(2)
                // Both pages must still be composed: a settled-only screenshot is insufficient.
                compose.onNodeWithTag(first.name).assertExists()
                compose.onNodeWithTag(second.name).assertExists()
                assertPixels(target)
            }
        }

        assertPixels(first)
        switchAndSample(second)
        settle()
        assertPixels(second)
        switchAndSample(first)
        settle()
        assertPixels(first)
        // Reverse before the outgoing page is disposed, in both directions.
        switchAndSample(second)
        switchAndSample(first)
        switchAndSample(second)
        settle()
        assertPixels(second)
    }

    @Test fun mainTabScrollStateSurvivesDisposalAndReturn() {
        val current = mutableStateOf(Tab.Schedule)
        val mounted = mutableSetOf<Tab>()
        val scrollStates = mutableMapOf<Tab, ScrollState>()
        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                val holder = rememberSaveableStateHolder()
                Box(Modifier.size(240.dp, 320.dp)) {
                    MainTabContent(current.value) { tab ->
                        holder.SaveableStateProvider(tab.name) {
                            val scroll = rememberScrollState()
                            DisposableEffect(tab, scroll) {
                                mounted.add(tab)
                                scrollStates[tab] = scroll
                                onDispose { mounted.remove(tab) }
                            }
                            Box(Modifier.fillMaxSize().verticalScroll(scroll)) {
                                Box(Modifier.height(1600.dp))
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { scrollStates.getValue(Tab.Schedule).dispatchRawDelta(180f) }
        compose.runOnIdle { current.value = Tab.Today }
        settle()
        compose.runOnIdle {
            assertFalse(mounted.contains(Tab.Schedule))
            scrollStates.getValue(Tab.Today).dispatchRawDelta(260f)
            current.value = Tab.Schedule
        }
        settle()
        compose.runOnIdle {
            assertEquals(180, scrollStates.getValue(Tab.Schedule).value)
            assertFalse(mounted.contains(Tab.Today))
            current.value = Tab.Today
        }
        settle()
        compose.runOnIdle {
            assertTrue(mounted.contains(Tab.Today))
            assertEquals(260, scrollStates.getValue(Tab.Today).value)
            current.value = Tab.Schedule
        }
        pumpFrames(4)
        compose.runOnIdle { current.value = Tab.Today }
        pumpFrames(4)
        compose.runOnIdle {
            assertEquals(180, scrollStates.getValue(Tab.Schedule).value)
            assertEquals(260, scrollStates.getValue(Tab.Today).value)
        }
    }

    private fun pumpFrames(count: Int) {
        repeat(count) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
    }

    private fun settle() {
        pumpFrames(3)
        compose.mainClock.advanceTimeBy(2000)
        compose.waitForIdle()
    }
}

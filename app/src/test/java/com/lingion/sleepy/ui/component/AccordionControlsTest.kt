package com.lingion.sleepy.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.ui.component.liquidglass.GlassHost
import com.lingion.sleepy.ui.component.liquidglass.GlassIconButton
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-mdpi")
class AccordionControlsTest {
    @get:Rule val compose = createComposeRule()

    @Composable
    private fun Fixture(style: UiStyle = UiStyle.Classic, content: @Composable () -> Unit) {
        MaterialExpressiveTheme {
            CompositionLocalProvider(LocalUiStyle provides style) {
                GlassHost(enabled = style == UiStyle.LiquidGlass) { content() }
            }
        }
    }

    @Test
    fun headerHasOneAccessibleToggleAndReportsTheCurrentState() {
        var expanded by mutableStateOf(false)
        var collapsedLabel = ""
        var expandedLabel = ""
        compose.setContent {
            Fixture {
                collapsedLabel = LocalContext.current.getString(R.string.accordion_collapsed)
                expandedLabel = LocalContext.current.getString(R.string.accordion_expanded)
                AccordionHeader(expanded, { expanded = !expanded }, Modifier.testTag("header")) {
                    Text("Section")
                }
            }
        }
        compose.onAllNodes(hasClickAction()).assertCountEquals(1)
        compose.onNodeWithTag("header").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, collapsedLabel),
        ).performClick()
        compose.onNodeWithTag("header").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, expandedLabel),
        )
        assertTrue(expanded)
    }

    @Test
    fun independentDeleteActionDoesNotAlsoToggleItsHeader() {
        var expanded by mutableStateOf(true)
        var removals = 0
        compose.setContent {
            Fixture {
                AccordionHeader(
                    expanded = expanded,
                    onToggle = { expanded = !expanded },
                    actions = {
                        GlassIconButton({ removals++ }, Modifier.testTag("delete")) { Text("X") }
                    },
                ) { Text("Section") }
            }
        }
        compose.onAllNodes(hasClickAction()).assertCountEquals(2)
        compose.onNodeWithTag("delete").performClick()
        assertEquals(1, removals)
        assertTrue(expanded)
    }

    @Test
    fun bothStylesSkipTheInternalArrowButKeepStandaloneButtonsInFocusOrder() {
        var style by mutableStateOf(UiStyle.Classic)
        val headerFocus = FocusRequester()
        lateinit var focus: FocusManager
        lateinit var inputMode: InputModeManager
        compose.setContent {
            Fixture(style) {
                focus = LocalFocusManager.current
                inputMode = LocalInputModeManager.current
                Column {
                    AccordionHeader(
                        expanded = false,
                        onToggle = {},
                        modifier = Modifier.focusRequester(headerFocus).testTag("header"),
                        actions = {
                            GlassIconButton({}, Modifier.testTag("delete")) { Text("X") }
                        },
                    ) { Text("Section") }
                    AccordionIconButton(false, {}, Modifier.testTag("standalone"))
                }
            }
        }
        // Foundation clickables intentionally reject keyboard focus while in touch input mode.
        compose.runOnIdle { assertTrue(inputMode.requestInputMode(InputMode.Keyboard)) }
        listOf(UiStyle.Classic, UiStyle.LiquidGlass).forEach { current ->
            compose.runOnIdle { style = current }
            compose.runOnIdle { headerFocus.requestFocus() }
            compose.onNodeWithTag("header").assertIsFocused()
            compose.runOnIdle { assertTrue(focus.moveFocus(FocusDirection.Next)) }
            compose.onNodeWithTag("delete").assertIsFocused()
            compose.runOnIdle { assertTrue(focus.moveFocus(FocusDirection.Next)) }
            compose.onNodeWithTag("standalone").assertIsFocused()
        }
    }

    @Test fun materialCardBodyTapLeavesTheSectionOpen() = cardBodyTapLeavesTheSectionOpen(UiStyle.Classic)

    @Test fun glassCardBodyTapLeavesTheSectionOpen() = cardBodyTapLeavesTheSectionOpen(UiStyle.LiquidGlass)

    private fun cardBodyTapLeavesTheSectionOpen(style: UiStyle) {
        var expanded by mutableStateOf(true)
        compose.setContent {
            Fixture(style) {
                SettingsCard("Section", expanded, { expanded = !expanded }) {
                    Text("Body", Modifier.testTag("body"))
                }
            }
        }
        compose.onNodeWithTag("body").performTouchInput { click() }
        assertTrue("Only the header is a collapse target", expanded)
        compose.onNodeWithText("Section").performClick()
        compose.onNodeWithTag("body").assertDoesNotExist()
    }

    @Test
    fun bothStylesKeepExitingContentAndAllowAnImmediateReversal() {
        var expanded by mutableStateOf(true)
        compose.setContent {
            Column {
                listOf(UiStyle.Classic, UiStyle.LiquidGlass).forEach { style ->
                    Fixture(style) {
                        AccordionContent(expanded) {
                            Box(Modifier.size(100.dp, 80.dp).testTag("body-$style"))
                        }
                    }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { expanded = false }
        advanceFrames(64)
        listOf(UiStyle.Classic, UiStyle.LiquidGlass).forEach {
            compose.onNodeWithTag("body-$it").assertExists()
        }
        compose.runOnIdle { expanded = true }
        advanceFrames(1000)
        listOf(UiStyle.Classic, UiStyle.LiquidGlass).forEach {
            compose.onNodeWithTag("body-$it").assertExists()
        }
        compose.runOnIdle { expanded = false }
        advanceFrames(1000)
        listOf(UiStyle.Classic, UiStyle.LiquidGlass).forEach {
            compose.onNodeWithTag("body-$it").assertDoesNotExist()
        }
    }

    private fun advanceFrames(milliseconds: Int) {
        // Size transitions need the Android host to measure/layout between animation frames.
        repeat((milliseconds + 15) / 16) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
    }
}

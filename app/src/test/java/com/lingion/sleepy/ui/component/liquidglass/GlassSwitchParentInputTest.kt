package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real parent-toggleable ownership, including the reminder screen's pending disable. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GlassSwitchParentInputTest {
    @get:Rule val compose = createComposeRule()
    private val checked = mutableStateOf(false)
    private val pendingTarget = mutableStateOf<Boolean?>(null)
    private val changes = mutableListOf<Boolean>()
    private lateinit var rowInteractions: MutableInteractionSource

    private fun show(writeDelayMillis: Long = 0L, acceptWrite: Boolean = true, enabled: Boolean = true,
        scrollableParent: Boolean = false) {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalUiStyle provides UiStyle.LiquidGlass,
                    LocalDockInputTimeNanos provides { compose.mainClock.currentTime * 1_000_000L },
                ) {
                    GlassHost {
                        val interactions = remember { MutableInteractionSource() }
                        rowInteractions = interactions
                        val pending = pendingTarget.value != null
                        LaunchedEffect(pendingTarget.value) {
                            val target = pendingTarget.value ?: return@LaunchedEffect
                            delay(writeDelayMillis)
                            if (acceptWrite) {
                                checked.value = target
                                // The database can emit before pending clears.
                                delay(32L)
                            }
                            pendingTarget.value = null
                        }
                        @Composable fun ToggleRow() { Row(
                            Modifier.testTag("row").toggleable(
                                value = checked.value,
                                enabled = enabled && !pending,
                                role = Role.Switch,
                                interactionSource = interactions,
                                indication = null,
                                onValueChange = { target ->
                                    changes += target
                                    if (writeDelayMillis == 0L) checked.value = target
                                    else pendingTarget.value = target
                                },
                            ),
                        ) {
                            // A press here belongs to the parent, outside the switch hit box.
                            Box(Modifier.width(40.dp).height(48.dp))
                            GlassSwitch(
                                checked = checked.value,
                                onCheckedChange = null,
                                enabled = enabled && !pending,
                                interactionSource = interactions,
                                modifier = Modifier.testTag("switch"),
                                thumbContent = { Box(Modifier.fillMaxSize().testTag("thumb")) },
                            )
                        } }
                        if (scrollableParent) LazyColumn { item { ToggleRow() } }
                        else ToggleRow()
                    }
                }
            }
        }
    }

    @Test fun parentHeldDownLiftsBeforeAnyCheckedValueChanges() {
        show()
        compose.mainClock.autoAdvance = false
        val row = compose.onNodeWithTag("row")
        val thumb = compose.onNodeWithTag("thumb", useUnmergedTree = true)
        val rest = thumb.fetchSemanticsNode().boundsInRoot
        row.performTouchInput { down(Offset(8.dp.toPx(), centerY)) }
        compose.mainClock.advanceTimeBy(240L)
        val held = thumb.fetchSemanticsNode().boundsInRoot
        row.assertIsOff()
        assertEquals(emptyList<Boolean>(), changes)
        assertTrue("the row's DOWN must lift its child lens before UP: rest=$rest held=$held", held.width > 55f && held.height > 34f)
        assertEquals("a stationary hold must not select or travel", rest.center.x, held.center.x, 1f)

        row.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1_000L)
        row.assertIsOn()
        val settled = thumb.fetchSemanticsNode().boundsInRoot
        assertEquals(rest.width, settled.width, 1f)
        assertTrue(settled.center.x > rest.center.x + 20f)
        assertEquals(listOf(true), changes)
        compose.onNodeWithTag("switch", useUnmergedTree = true)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        compose.onAllNodes(hasClickAction()).assertCountEquals(1)
    }

    @Test fun parentTapKeepsItsLiftAcrossPendingDisableAndDelayedControlledEcho() {
        show(writeDelayMillis = 120L)
        compose.mainClock.autoAdvance = false
        val row = compose.onNodeWithTag("row")
        val thumb = compose.onNodeWithTag("thumb", useUnmergedTree = true)
        val rest = thumb.fetchSemanticsNode().boundsInRoot
        row.performTouchInput { click(Offset(8.dp.toPx(), centerY)) }
        var widestBeforeEcho = rest.width
        var tallestBeforeEcho = rest.height
        repeat(6) {
            compose.mainClock.advanceTimeByFrame()
            val bounds = thumb.fetchSemanticsNode().boundsInRoot
            widestBeforeEcho = maxOf(widestBeforeEcho, bounds.width)
            tallestBeforeEcho = maxOf(tallestBeforeEcho, bounds.height)
        }
        row.assertIsOff().assertIsNotEnabled()
        assertEquals(listOf(true), changes)
        assertTrue(
            "accepted parent tap must start its lens before the delayed database echo: $widestBeforeEcho x $tallestBeforeEcho",
            widestBeforeEcho > 55f && tallestBeforeEcho > 34f,
        )
        compose.mainClock.advanceTimeBy(48L)
        row.assertIsOn().assertIsNotEnabled()
        compose.mainClock.advanceTimeBy(1_000L)
        row.assertIsOn()
        val settled = thumb.fetchSemanticsNode().boundsInRoot
        assertEquals(rest.width, settled.width, 1f)
        assertTrue(settled.center.x > rest.center.x + 20f)
        assertEquals(listOf(true), changes)
    }

    @Test fun cancelledParentPressLowersWithoutCommitting() {
        show()
        compose.mainClock.autoAdvance = false
        val row = compose.onNodeWithTag("row")
        val thumb = compose.onNodeWithTag("thumb", useUnmergedTree = true)
        val rest = thumb.fetchSemanticsNode().boundsInRoot
        row.performTouchInput { down(Offset(8.dp.toPx(), centerY)) }
        compose.mainClock.advanceTimeBy(240L)
        val held = thumb.fetchSemanticsNode().boundsInRoot
        assertTrue("cancel regression must start from a raised parent lens: $held", held.width > 55f && held.height > 34f)
        row.performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(1_000L)
        val settled = thumb.fetchSemanticsNode().boundsInRoot
        row.assertIsOff()
        assertEquals(rest.width, settled.width, 1f)
        assertEquals(rest.center.x, settled.center.x, 1f)
        assertEquals(emptyList<Boolean>(), changes)
    }

    @Test fun failedParentSaveRollsBackAfterPendingClears() {
        show(writeDelayMillis = 120L, acceptWrite = false)
        compose.mainClock.autoAdvance = false
        val row = compose.onNodeWithTag("row")
        val thumb = compose.onNodeWithTag("thumb", useUnmergedTree = true)
        val rest = thumb.fetchSemanticsNode().boundsInRoot
        row.performTouchInput { click(Offset(8.dp.toPx(), centerY)) }
        compose.mainClock.advanceTimeBy(96L)
        row.assertIsOff().assertIsNotEnabled()
        val pending = thumb.fetchSemanticsNode().boundsInRoot
        assertTrue("an accepted tap must rise before its save fails: $pending", pending.width > 55f && pending.height > 34f)
        compose.mainClock.advanceTimeBy(1_000L)
        row.assertIsOff().assertIsEnabled()
        val settled = thumb.fetchSemanticsNode().boundsInRoot
        assertEquals(rest.width, settled.width, 1f)
        assertEquals(rest.center.x, settled.center.x, 1f)
        assertEquals(listOf(true), changes)
    }

    @Test fun disabledParentRejectsTouchAndForeignPressWithoutLifting() {
        show(enabled = false)
        compose.mainClock.autoAdvance = false
        val row = compose.onNodeWithTag("row")
        val thumb = compose.onNodeWithTag("thumb", useUnmergedTree = true)
        val rest = thumb.fetchSemanticsNode().boundsInRoot
        row.assertIsNotEnabled().performTouchInput { click(Offset(8.dp.toPx(), centerY)) }
        val foreignPress = PressInteraction.Press(Offset.Zero)
        compose.runOnIdle { assertTrue(rowInteractions.tryEmit(foreignPress)) }
        compose.mainClock.advanceTimeBy(240L)
        val held = thumb.fetchSemanticsNode().boundsInRoot
        assertEquals(rest.width, held.width, 1f)
        assertEquals(rest.height, held.height, 1f)
        assertEquals(rest.center.x, held.center.x, 1f)
        compose.runOnIdle { assertTrue(rowInteractions.tryEmit(PressInteraction.Release(foreignPress))) }
        compose.mainClock.advanceTimeBy(1_000L)
        row.assertIsOff().assertIsNotEnabled()
        assertEquals(emptyList<Boolean>(), changes)
    }

    @Test fun lazyColumnShortTapLiftsDespiteSynchronousPendingDisable() {
        show(writeDelayMillis = 120L, scrollableParent = true)
        compose.mainClock.autoAdvance = false
        val row = compose.onNodeWithTag("row")
        val thumb = compose.onNodeWithTag("thumb", useUnmergedTree = true)
        val rest = thumb.fetchSemanticsNode().boundsInRoot
        row.performTouchInput {
            down(Offset(8.dp.toPx(), centerY))
            advanceEventTime(16L)
            up()
        }
        var widest = rest.width
        var tallest = rest.height
        repeat(6) {
            compose.mainClock.advanceTimeByFrame()
            val bounds = thumb.fetchSemanticsNode().boundsInRoot
            widest = maxOf(widest, bounds.width)
            tallest = maxOf(tallest, bounds.height)
        }
        row.assertIsOff().assertIsNotEnabled()
        assertEquals(listOf(true), changes)
        assertTrue("a scroll parent's delayed Press/Release must not swallow its accepted tap: $widest x $tallest",
            widest > 55f && tallest > 34f)
        compose.mainClock.advanceTimeBy(1_000L)
        row.assertIsOn().assertIsEnabled()
        val settled = thumb.fetchSemanticsNode().boundsInRoot
        assertEquals(rest.width, settled.width, 1f)
        assertTrue(settled.center.x > rest.center.x + 20f)
        assertEquals(listOf(true), changes)
    }
}

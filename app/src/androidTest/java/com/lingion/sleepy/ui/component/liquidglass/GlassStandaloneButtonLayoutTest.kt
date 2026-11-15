package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.up
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real-host layout / pointer coverage. Carrier bounds do not prove optical parity with iOS. */
@RunWith(AndroidJUnit4::class)
class GlassStandaloneButtonLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun glassBodyStaysSeparateFromDefaultAndFixedHeightTouchTargets() = checkButtons(UiStyle.LiquidGlass)

    @Test fun materialKeepsAccessibleTargetsAndDisabledPointerBehavior() = checkButtons(UiStyle.Classic)

    @Test fun tallButtonTouchPaddingKeepsItsLensLiftedUntilPointerRelease() {
        var label: LayoutCoordinates? = null
        var calls = 0
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass) {
                    GlassHost(modifier = Modifier.size(320.dp, 240.dp), background = Color.White) {
                        Column(Modifier.padding(20.dp)) {
                            GlassButton(onClick = { calls++ },
                                modifier = Modifier.width(240.dp).height(56.dp).testTag("tall")) {
                                Text("Continue", Modifier.onGloballyPositioned { label = it })
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        fun ink() = checkNotNull(label).boundsInRoot()
        val rest = ink()
        compose.mainClock.autoAdvance = false
        val target = compose.onNodeWithTag("tall")
        target.performTouchInput { down(Offset(centerX, 1f)) }
        repeat(24) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        val held = ink()
        assertTrue("Touch padding must lift the same lens as its visible body", held.height > rest.height + 1f)
        target.performTouchInput { moveTo(Offset(centerX + 30f, -24f)) }
        repeat(24) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        val dragged = ink()
        assertTrue("A held pointer stays lifted after leaving the activation bounds", dragged.height > rest.height + 1f)
        assertTrue("Touch-padding drag must pull the lens with the held pointer", dragged.center.x > held.center.x + 1f)
        target.performTouchInput { up() }
        repeat(80) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        assertEquals(rest.height, ink().height, 1f)
        compose.runOnIdle { assertEquals("Dragging out cancels activation", 0, calls) }
        compose.mainClock.autoAdvance = true
    }

    private fun checkButtons(style: UiStyle) {
        var calls = 0
        var density = 0f
        val bodyBounds = mutableMapOf<String, Rect>()
        compose.setContent {
            val deviceDensity = LocalDensity.current
            MaterialTheme {
                CompositionLocalProvider(
                    LocalUiStyle provides style,
                    // Explicit fixture setting; the product still supports larger fonts.
                    LocalDensity provides Density(deviceDensity.density, fontScale = 1f),
                ) {
                    SideEffect { density = deviceDensity.density }
                    GlassHost(modifier = Modifier.size(320.dp, 240.dp), background = Color.White) {
                        Column(
                            modifier = Modifier.padding(20.dp).width(280.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            GlassTextButton(onClick = { calls++ }, modifier = Modifier.testTag("regular")) {
                                Text("Glass", Modifier.onGloballyPositioned {
                                    bodyBounds["regular"] = contentParentCarrier(it)
                                })
                            }
                            GlassButton(
                                onClick = { calls += 10 },
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("wide"),
                            ) {
                                Text("Continue", Modifier.onGloballyPositioned {
                                    bodyBounds["wide"] = contentParentCarrier(it)
                                })
                            }
                            GlassButton(onClick = { calls += 100 }, enabled = false,
                                modifier = Modifier.testTag("disabled")) { Text("Disabled") }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        listOf("regular", "wide", "disabled").forEach { tag ->
            if (style == UiStyle.LiquidGlass) compose.onNodeWithTag(tag).assertHeightIsAtLeast(48.dp)
        }
        compose.onNodeWithTag("wide").assertWidthIsEqualTo(280.dp).assertHeightIsEqualTo(48.dp)
        compose.onNodeWithTag("disabled").assertIsNotEnabled()
        if (style == UiStyle.LiquidGlass) {
            listOf("regular", "wide").forEach { tag ->
                val outer = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
                val body = checkNotNull(bodyBounds[tag])
                assertEquals("$tag: resting body uses the AX-anchored height target", 33.5f * density, body.height, 1f)
                assertTrue("$tag: touch padding must remain outside the material carrier", body.height < outer.height - 8f * density)
                assertEquals("$tag: body stays vertically centered in its hit target", outer.center.y, body.center.y, 1f)
                if (tag == "wide") {
                    assertEquals("fillMaxWidth must reach the material carrier", outer.width, body.width, 1f)
                }
            }
        }
        // The top pixel is in the vertical touch padding, outside the resting
        // glass body. A semantics-only performClick would not cover this path.
        compose.onNodeWithTag("regular").performTouchInput { click(Offset(centerX, 1f)) }
        compose.onNodeWithTag("wide").performTouchInput { click(Offset(centerX, 1f)) }
        compose.onNodeWithTag("disabled").performTouchInput { click() }
        compose.runOnIdle { assertEquals("disabled pointer input must not activate", 11, calls) }
    }

    private fun contentParentCarrier(content: LayoutCoordinates): Rect {
        // The label's parent is the actual content Row. Walk that Row's own
        // modifier coordinates, stopping before the surrounding activation
        // Layout. This observes the body carrier without a production testTag.
        var carrier = checkNotNull(content.parentLayoutCoordinates)
        val owner = checkNotNull(carrier.parentLayoutCoordinates)
        while (true) {
            val parent = carrier.parentCoordinates ?: break
            if (parent === owner) break
            carrier = parent
        }
        return carrier.boundsInRoot()
    }
}

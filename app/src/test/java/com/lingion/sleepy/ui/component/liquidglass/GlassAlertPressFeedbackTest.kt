package com.lingion.sleepy.ui.component.liquidglass

import android.view.View
import android.os.Binder
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Material still owns the actual press and click; the Alert observes that source. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w600dp-h800dp-mdpi")
class GlassAlertPressFeedbackTest {
    @get:Rule val compose = createComposeRule()

    @Test fun defaultMaterialSourceLiftsAllSlotsAndTheMaterialWithoutDelayingActivation() {
        val fixture = Fixture()
        show(fixture)
        val pane = pane(fixture)
        val slots = fixture.slotTags.associateWith(::bounds)
        assertEquals(320f, pane.width, .01f)

        compose.onNodeWithTag("confirm").performTouchInput { down(center) }
        settle()
        val held = pane(fixture)
        assertEquals(1.0125f, held.width / pane.width, .00005f)
        assertEquals(1.0125f, held.height / pane.height, .00005f)
        assertRectNear(scaled(pane, pane.center, 1.0125f), held)
        slots.forEach { (tag, rest) -> assertRectNear(scaled(rest, pane.center, 1.0125f), bounds(tag)) }
        assertWindowContains(fixture, held = true)
        assertEquals(0, fixture.confirmations)

        // Recomposition must not replace the Material source or lose its held state.
        compose.runOnIdle { fixture.bodyText.value = "Updated message" }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(held.width, pane(fixture).width, .02f)
        compose.onNodeWithTag("confirm").performTouchInput { up() }
        compose.runOnIdle { assertEquals(1, fixture.confirmations) }
        assertTrue("activation occurs while the pane still has its held scale", pane(fixture).width > pane.width * 1.01f)
        settle()
        assertRectNear(pane, pane(fixture))

        // Actual pointer cancellation resets feedback and must not activate.
        compose.onNodeWithTag("cancel").performTouchInput { down(center) }
        settle()
        assertTrue(pane(fixture).width > pane.width)
        compose.onNodeWithTag("cancel").performTouchInput { cancel() }
        settle()
        assertRectNear(pane, pane(fixture))
        assertEquals(0, fixture.cancellations)
    }

    @Test fun releaseOrCancellationOfOneActionKeepsOtherPressesHeld() {
        val fixture = Fixture()
        val cancelSource = MutableInteractionSource()
        val confirmSource = MutableInteractionSource()
        fixture.cancelSource.value = cancelSource
        fixture.confirmSource.value = confirmSource
        show(fixture)
        val rest = pane(fixture)
        val cancelPress = PressInteraction.Press(Offset.Zero)
        val confirmPress = PressInteraction.Press(Offset.Zero)
        emit(cancelSource, cancelPress)
        emit(confirmSource, confirmPress)
        settle()
        assertEquals(rest.width * 1.0125f, pane(fixture).width, .02f)
        emit(cancelSource, PressInteraction.Release(cancelPress))
        settle()
        assertEquals("another action is still held", rest.width * 1.0125f, pane(fixture).width, .02f)

        // More than one outstanding token on the same source also remains held.
        val secondPress = PressInteraction.Press(Offset(1f, 1f))
        emit(confirmSource, secondPress)
        emit(confirmSource, PressInteraction.Cancel(confirmPress))
        settle()
        assertEquals(rest.width * 1.0125f, pane(fixture).width, .02f)
        emit(confirmSource, PressInteraction.Release(secondPress))
        settle()
        assertRectNear(rest, pane(fixture))
        assertEquals(0, fixture.confirmations)
        assertEquals(0, fixture.cancellations)
    }

    @Test fun disablingReplacingAndDisposingAnActionRetireItsExternalSourceRegistration() {
        val fixture = Fixture()
        val firstSource = MutableInteractionSource()
        val nextSource = MutableInteractionSource()
        fixture.cancelSource.value = firstSource
        show(fixture)
        val rest = pane(fixture)
        emit(firstSource, PressInteraction.Press(Offset.Zero))
        settle()
        assertTrue(pane(fixture).width > rest.width)
        compose.runOnIdle { fixture.cancelEnabled.value = false }
        settle()
        assertRectNear(rest, pane(fixture))
        emit(firstSource, PressInteraction.Press(Offset.Zero))
        settle()
        assertRectNear(rest, pane(fixture))
        compose.runOnIdle { fixture.cancelEnabled.value = true }
        settle()
        assertRectNear(rest, pane(fixture))

        emit(firstSource, PressInteraction.Press(Offset.Zero))
        settle()
        compose.runOnIdle { fixture.cancelSource.value = nextSource }
        settle()
        assertRectNear(rest, pane(fixture))
        emit(firstSource, PressInteraction.Press(Offset.Zero))
        settle()
        assertRectNear(rest, pane(fixture))
        emit(nextSource, PressInteraction.Press(Offset.Zero))
        settle()
        assertTrue(pane(fixture).width > rest.width)
        compose.runOnIdle { fixture.showCancel.value = false }
        settle()
        assertRectNear(rest, pane(fixture))
        compose.runOnIdle { fixture.showCancel.value = true }
        settle()
        assertRectNear(rest, pane(fixture))
        assertEquals(0, fixture.cancellations)
    }

    @Test fun removingOneActionDoesNotRetireAnotherActionsOutstandingPress() {
        val fixture = Fixture()
        val first = MutableInteractionSource()
        val second = MutableInteractionSource()
        fixture.cancelSource.value = first
        fixture.confirmSource.value = second
        show(fixture)
        val restWidth = pane(fixture).width
        val secondPress = PressInteraction.Press(Offset.Zero)
        emit(first, PressInteraction.Press(Offset.Zero))
        emit(second, secondPress)
        settle()
        compose.runOnIdle { fixture.showCancel.value = false }
        settle()
        assertEquals(restWidth * 1.0125f, pane(fixture).width, .02f)
        emit(second, PressInteraction.Cancel(secondPress))
        settle()
        assertEquals(restWidth, pane(fixture).width, .02f)
    }

    @Test fun bodyButtonsDoNotDriveTheSharedAlertPressAndExplicitColorsRemainUnchanged() {
        val fixture = Fixture(bodyButton = true, explicitColors = true, paneModifier = Modifier.width(304.dp))
        show(fixture)
        val rest = pane(fixture)
        assertEquals(304f, rest.width, .01f)
        val colors = fixture.observedColors.toMap()
        assertEquals(Color.Cyan, colors["title"])
        assertEquals(Color.Yellow, colors["body"])
        assertEquals(Color.Green, colors["action"])
        compose.onNodeWithTag("body-button").performTouchInput { down(center) }
        settle()
        assertRectNear(rest, pane(fixture))
        compose.onNodeWithTag("body-button").performTouchInput { cancel() }
        settle()
        compose.onNodeWithTag("confirm").performTouchInput { down(center) }
        settle()
        assertEquals(rest.width * 1.0125f, pane(fixture).width, .02f)
        assertEquals(colors, fixture.observedColors)
        compose.onNodeWithTag("confirm").performTouchInput { cancel() }
        settle()
        assertRectNear(rest, pane(fixture))
    }

    @Test fun classicAlertNeverAcquiresTheGlassParentScale() {
        val fixture = Fixture(style = UiStyle.Classic)
        show(fixture)
        val tags = listOf("alert") + fixture.slotTags
        val rest = tags.associateWith(::bounds)
        assertFalse(fixture.material != null)
        compose.onNodeWithTag("confirm").performTouchInput { down(center) }
        settle()
        assertEquals(rest, tags.associateWith(::bounds))
        compose.onNodeWithTag("confirm").performTouchInput { up() }
        compose.runOnIdle { assertEquals(1, fixture.confirmations) }
        settle()
        assertEquals(rest, tags.associateWith(::bounds))
    }

    @Test
    @Config(sdk = [35], qualifiers = "w320dp-h480dp-mdpi")
    fun narrowDialogKeepsItsScaledPaneInsideRealWindowSpace() {
        val fixture = Fixture()
        show(fixture)
        val rest = pane(fixture)
        assertTrue(rest.width <= 320f)
        assertTrue(rest.width > 200f)
        assertWindowContains(fixture, held = false)
        compose.onNodeWithTag("confirm").performTouchInput { down(center) }
        settle()
        assertEquals(rest.width * 1.0125f, pane(fixture).width, .02f)
        assertWindowContains(fixture, held = true)
        compose.onNodeWithTag("confirm").performTouchInput { cancel() }
        settle()
    }

    @Test fun opticalWindowPolicyPreservesAllOtherCallerProperties() {
        val token = Binder()
        val requested = DialogProperties(
            dismissOnBackPress = false, dismissOnClickOutside = false,
            securePolicy = SecureFlagPolicy.SecureOn, usePlatformDefaultWidth = true,
            decorFitsSystemWindows = false, windowTitle = "Caller alert",
            windowType = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL, windowToken = token,
        )
        val actual = nativeGlassAlertDialogProperties(requested)
        assertFalse(actual.usePlatformDefaultWidth)
        assertEquals(requested.dismissOnBackPress, actual.dismissOnBackPress)
        assertEquals(requested.dismissOnClickOutside, actual.dismissOnClickOutside)
        assertEquals(requested.securePolicy, actual.securePolicy)
        assertEquals(requested.decorFitsSystemWindows, actual.decorFitsSystemWindows)
        assertEquals(requested.windowTitle, actual.windowTitle)
        assertEquals(requested.windowType, actual.windowType)
        assertSame(token, actual.windowToken)
        val optedOut = DialogProperties(usePlatformDefaultWidth = false)
        assertSame(optedOut, nativeGlassAlertDialogProperties(optedOut))
    }

    @Test fun callerCustomWidthAndPlatformWidthOptOutRemainEffective() {
        val fixture = Fixture(
            paneModifier = Modifier.width(400.dp),
            properties = DialogProperties(usePlatformDefaultWidth = false),
        )
        show(fixture)
        val rest = pane(fixture)
        assertEquals(400f, rest.width, .01f)
        assertWindowContains(fixture, held = false)
        compose.onNodeWithTag("confirm").performTouchInput { down(center) }
        settle()
        assertEquals(405f, pane(fixture).width, .02f)
        assertWindowContains(fixture, held = true)
        compose.onNodeWithTag("confirm").performTouchInput { cancel() }
        settle()
    }

    private fun show(fixture: Fixture) {
        compose.setContent { fixture.Content() }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    private fun settle() {
        // SubcomposeLayout can apply action changes during the Android measure pass.
        // Drain that work before advancing the animation's controlled clock.
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1600)
        compose.waitForIdle()
    }

    private fun emit(source: MutableInteractionSource, interaction: Interaction) {
        compose.runOnIdle { assertTrue(source.tryEmit(interaction)) }
        compose.waitForIdle()
    }

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    /** Read un-clipped transformed corners, so a too-small Window cannot hide a regression. */
    private fun pane(fixture: Fixture): Rect = compose.runOnIdle {
        transformedBounds(checkNotNull(checkNotNull(fixture.material).coordinates), window = false)
    }

    private fun transformedBounds(coordinates: LayoutCoordinates, window: Boolean): Rect {
        val origin = if (window) coordinates.localToWindow(Offset.Zero) else coordinates.localToRoot(Offset.Zero)
        val endPoint = Offset(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())
        val end = if (window) coordinates.localToWindow(endPoint) else coordinates.localToRoot(endPoint)
        return Rect(origin.x, origin.y, end.x, end.y)
    }

    private fun assertWindowContains(fixture: Fixture, held: Boolean) {
        compose.runOnIdle {
            val actual = transformedBounds(checkNotNull(checkNotNull(fixture.material).coordinates), window = true)
            val view = checkNotNull(fixture.windowView)
            val message = "${if (held) "held" else "rest"} optical pane needs transparent drawing room inside its actual Window"
            assertTrue(message, actual.left >= .5f)
            assertTrue(message, actual.top >= .5f)
            assertTrue(message, actual.right <= view.width - .5f)
            assertTrue(message, actual.bottom <= view.height - .5f)
        }
    }

    private fun scaled(rect: Rect, center: Offset, scale: Float) = Rect(
        center.x + (rect.left - center.x) * scale,
        center.y + (rect.top - center.y) * scale,
        center.x + (rect.right - center.x) * scale,
        center.y + (rect.bottom - center.y) * scale,
    )

    private fun assertRectNear(expected: Rect, actual: Rect) {
        assertEquals("left", expected.left, actual.left, .08f)
        assertEquals("top", expected.top, actual.top, .08f)
        assertEquals("right", expected.right, actual.right, .08f)
        assertEquals("bottom", expected.bottom, actual.bottom, .08f)
    }

    private class Fixture(
        private val style: UiStyle = UiStyle.LiquidGlass,
        private val bodyButton: Boolean = false,
        private val explicitColors: Boolean = false,
        private val paneModifier: Modifier = Modifier,
        private val properties: DialogProperties = DialogProperties(),
    ) {
        val cancelEnabled = mutableStateOf(true)
        val showCancel = mutableStateOf(true)
        val bodyText = mutableStateOf("Message")
        val cancelSource = mutableStateOf<MutableInteractionSource?>(null)
        val confirmSource = mutableStateOf<MutableInteractionSource?>(null)
        var material: GlassMaterialScope? = null
        var windowView: View? = null
        var confirmations = 0
        var cancellations = 0
        val observedColors = mutableMapOf<String, Color>()
        val slotTags = listOf("icon", "title", "body", "cancel", "cancel-label", "confirm", "confirm-label")

        @Composable
        fun Content() {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides style) {
                    GlassHost {
                        GlassAlertDialog(
                            onDismissRequest = {}, modifier = paneModifier.testTag("alert"),
                            properties = properties,
                            shape = if (explicitColors) RoundedCornerShape(18.dp) else null,
                            containerColor = if (explicitColors) Color.Magenta else Color.Unspecified,
                            titleContentColor = if (explicitColors) Color.Cyan else Color.Unspecified,
                            textContentColor = if (explicitColors) Color.Yellow else Color.Unspecified,
                            icon = { Box(Modifier.size(20.dp).background(Color.Cyan).testTag("icon")) },
                            title = {
                                material = LocalGlassMaterialBackdrop.current as? GlassMaterialScope
                                windowView = LocalView.current.rootView
                                observedColors["title"] = LocalContentColor.current
                                Text("Title", Modifier.fillMaxWidth().testTag("title"))
                            },
                            text = {
                                observedColors["body"] = LocalContentColor.current
                                Column {
                                    Text(bodyText.value, Modifier.fillMaxWidth().testTag("body"))
                                    if (bodyButton) GlassTextButton({}, Modifier.testTag("body-button")) { Text("Body control") }
                                }
                            },
                            dismissButton = {
                                if (showCancel.value) GlassTextButton(
                                    { cancellations++ }, Modifier.testTag("cancel"),
                                    enabled = cancelEnabled.value, interactionSource = cancelSource.value,
                                ) { Text("Cancel", Modifier.testTag("cancel-label")) }
                            },
                            confirmButton = {
                                GlassTextButton(
                                    { confirmations++ }, Modifier.testTag("confirm"),
                                    interactionSource = confirmSource.value,
                                    colors = if (explicitColors) ButtonDefaults.textButtonColors(
                                        containerColor = Color.Red, contentColor = Color.Green,
                                    ) else null,
                                ) {
                                    observedColors["action"] = LocalContentColor.current
                                    Text("Confirm", Modifier.testTag("confirm-label"))
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

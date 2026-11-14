package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GlassSliderTimestampInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val value = mutableFloatStateOf(.25f)
    private val receiptNanos = AtomicLong(0)
    private val changes = mutableListOf<Float>()
    private var finishes = 0
    private lateinit var uiScope: CoroutineScope

    private fun show(acceptChanges: Boolean = true) {
        compose.setContent {
            uiScope = rememberCoroutineScope()
            MaterialTheme {
                CompositionLocalProvider(
                    LocalUiStyle provides UiStyle.LiquidGlass,
                    LocalDockInputTimeNanos provides receiptNanos::get,
                ) {
                    GlassHost {
                        Box(Modifier.padding(32.dp)) {
                            GlassSlider(value.floatValue, {
                                changes += it
                                if (acceptChanges) value.floatValue = it
                            }, Modifier.width(280.dp).testTag("slider"),
                                onValueChangeFinished = { finishes++ })
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    private fun advanceObservedFrame(): Long {
        val observed = AtomicLong(Long.MIN_VALUE)
        compose.runOnIdle {
            uiScope.launch(start = CoroutineStart.UNDISPATCHED) {
                withFrameNanos { observed.set(it) }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertTrue("The reference needs an actual animation frame timestamp", observed.get() != Long.MIN_VALUE)
        return observed.get() / 1_000_000L
    }

    @Test fun pointerMovesBetweenFramesPreserveBothReceiptSegments() {
        show()
        val slider = compose.onNodeWithTag("slider")
        val rest = compose.onNodeWithTag("glass-slider-end").getUnclippedBoundsInRoot()
        val sliderBounds = slider.getUnclippedBoundsInRoot()
        val thumbPixels = compose.onNodeWithTag("glass-slider-end").fetchSemanticsNode().boundsInRoot
        val sliderPixels = slider.fetchSemanticsNode().boundsInRoot
        val downX = thumbPixels.center.x - sliderPixels.left
        receiptNanos.set((compose.mainClock.currentTime + 1) * 1_000_000L)
        slider.performTouchInput { down(Offset(downX, centerY)) }
        // Waking snapshotFlow and registering its frame callback can span more
        // than one test frame. Observe the held lens before declaring an origin.
        var began = false
        var origin = 0L
        for (ignored in 0 until 8) {
            val frameTime = advanceObservedFrame()
            val held = compose.onNodeWithTag("glass-slider-end").getUnclippedBoundsInRoot()
            if ((held.right - held.left).value > (rest.right - rest.left).value + .1f) {
                began = true
                origin = frameTime
                break
            }
        }
        assertTrue("DOWN must have reached the animated lens before measuring MOVE intervals", began)

        receiptNanos.set((origin + 2) * 1_000_000L)
        slider.performTouchInput { moveTo(Offset(width * .55f, centerY), 2) }
        receiptNanos.set((origin + 14) * 1_000_000L)
        slider.performTouchInput { moveTo(Offset(width * .9f, centerY), 12) }
        assertEquals(2, changes.size)
        val expected = SpringState(.25f)
        expected.tickDelta(.002)
        expected.springTo(changes[0], FlClashSprings.FollowSpring)
        expected.tickDelta(.012)
        expected.springTo(changes[1], FlClashSprings.FollowSpring)
        // Touch injection advances the scheduler by its event delays. Its final
        // time can lie after the last delivered animation frame; integrate only
        // through the frame actually observed by the control.
        val endFrame = advanceObservedFrame()
        assertTrue("Both MOVE receipts must be eligible for this frame", endFrame >= origin + 14)
        expected.tickDelta((endFrame - origin - 14) / 1000.0)

        val thumb = compose.onNodeWithTag("glass-slider-end").getUnclippedBoundsInRoot()
        val center = ((thumb.left + thumb.right) / 2 - sliderBounds.left).value
        val inset = ((rest.right - rest.left) / 2).value
        val travel = (sliderBounds.right - sliderBounds.left).value - 2 * inset
        assertEquals("The lens follows each delivered target for its own receipt interval",
            inset + travel * expected.value, center, 1f)
        receiptNanos.set((compose.mainClock.currentTime + 1) * 1_000_000L)
        slider.performTouchInput { up() }
        compose.runOnIdle { assertEquals(1, finishes) }
    }

    @Test fun aParentRefusingAPublishedDragValueCancelsThePointerBeforeAnotherMoveOrFinish() {
        show(acceptChanges = false)
        val slider = compose.onNodeWithTag("slider")
        receiptNanos.set((compose.mainClock.currentTime + 1) * 1_000_000L)
        slider.performTouchInput { down(Offset(width * .25f, centerY)) }
        compose.mainClock.advanceTimeByFrame()
        receiptNanos.set((compose.mainClock.currentTime + 1) * 1_000_000L)
        slider.performTouchInput { moveTo(Offset(width * .65f, centerY), 16) }
        compose.mainClock.advanceTimeByFrame()
        assertTrue(changes.isNotEmpty())
        val publishedBeforeRefusal = changes.size

        receiptNanos.set((compose.mainClock.currentTime + 1) * 1_000_000L)
        slider.performTouchInput {
            moveTo(Offset(width * .9f, centerY), 16)
            up()
        }

        compose.runOnIdle {
            assertEquals("A refused continuous request ends its input epoch", publishedBeforeRefusal, changes.size)
            assertEquals(.25f, value.floatValue, 0f)
            assertEquals("A refused pointer gesture cannot finish later", 0, finishes)
        }
    }
}

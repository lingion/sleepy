package com.lingion.sleepy.ui.component.liquidglass

import org.junit.Assert.assertEquals
import org.junit.Test

class GlassSliderDragMappingTest {
    private fun mapping(
        downX: Float = 104f,
        downValue: Float = .25f,
        width: Float = 335f,
        span: Float = 1f,
        slop: Float = 10f,
        rtl: Boolean = false,
    ) = GlassSliderDragMapping(downX, downValue, width, span, slop, rtl)

    @Test fun nativeSlowForwardUsesFullControlWidthAfterTheMeasuredSlop() {
        val drag = mapping()
        assertEquals(.25f, drag.valueAt(104f), 0f)
        assertEquals(.25f, drag.valueAt(113f), 0f)
        assertEquals(.25f, drag.valueAt(114f), 0f)
        assertEquals(.2559701502f, drag.valueAt(116f), .0000001f)
        assertEquals(.3126865625f, drag.valueAt(135f), .0000001f)
        assertEquals(.7694029808f, drag.valueAt(288f), .0000001f)
    }

    @Test fun nativeFastReverseStartsFromTheActualPriorValueAndEndsAtQuarterWhileHeld() {
        val drag = mapping(downX = 288f, downValue = .7694029808f)
        assertEquals(.7694029808f, drag.valueAt(288f), 0f)
        assertEquals(.7694029808f, drag.valueAt(278f), 0f)
        assertEquals(.7201492786f, drag.valueAt(261.5f), .0000001f)
        assertEquals(.6738805771f, drag.valueAt(246f), .0000001f)
        assertEquals(.25f, drag.valueAt(104f), .0000001f)
        // Holding the last point must not manufacture the native post-up drift.
        repeat(20) { assertEquals(.25f, drag.valueAt(104f), .0000001f) }
    }

    @Test fun activationConsumesSlopOnceAndKeepsTheAnchorWhenDirectionChanges() {
        val drag = mapping()
        assertEquals(.25f, drag.valueAt(95f), 0f)
        assertEquals(.25f, drag.valueAt(113f), 0f)
        assertEquals(.25f + 20f / 335f, drag.valueAt(134f), .0000001f)
        assertEquals(.25f, drag.valueAt(114f), .0000001f)
        assertEquals(.25f - 10f / 335f, drag.valueAt(104f), .0000001f)
        assertEquals(.25f - 30f / 335f, drag.valueAt(84f), .0000001f)
    }

    @Test fun rightToLeftMirrorsValueDirectionButKeepsPhysicalActivationDirection() {
        val drag = mapping(downX = 288f, rtl = true)
        assertEquals(.25f, drag.valueAt(278f), 0f)
        assertEquals(.7694029808f, drag.valueAt(104f), .0000001f)
    }

    @Test fun densityScalingPreservesTheMeasuredPointMapping() {
        val drag = mapping(downX = 208f, width = 670f, slop = 20f)
        assertEquals(.25f, drag.valueAt(228f), 0f)
        assertEquals(.7694029808f, drag.valueAt(576f), .0000001f)
    }

    @Test fun nonUnitRangesRetainUnsnappedMotionForTheExistingDiscreteSnapper() {
        val drag = mapping(downX = 100f, downValue = 5f, width = 280f, span = 9f)
        assertEquals(5f, drag.valueAt(109f), 0f)
        assertEquals(5f + 1f / 280f * 9f, drag.valueAt(111f), .0000001f)
        assertEquals(5f + 20f / 280f * 9f, drag.valueAt(130f), .0000001f)
    }
}

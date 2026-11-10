package com.lingion.sleepy.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test

class EdgePagingContractTest {
    @Test
    fun edgeDirectionUsesBothPointerAndCardWithoutTriggeringAtRest() {
        for ((center, expected) in listOf(5f to -1, 20f to 0, 340f to 0, 355f to 1)) {
            assertEquals(expected, floatEdgeDirection(center, center, 180f, 360f))
        }
        assertEquals(1, floatEdgeDirection(335f, 355f, 180f, 360f))
        assertEquals(-1, floatEdgeDirection(25f, 5f, 180f, 360f))
        assertEquals(0, floatEdgeDirection(355f, 355f, 355f, 360f))
    }
}

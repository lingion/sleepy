package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiquidGlassExactContourPathTest {
    @Test fun actualPathReaderPreservesMixedLinesAndRejectsUnsupportedOrNonradialGeometry() {
        fun rectangle(reverse: Boolean = false, closed: Boolean = true) = Path().apply {
            moveTo(0f, 0f)
            if (reverse) { lineTo(0f, 50f); lineTo(100f, 50f); lineTo(100f, 0f) }
            else { lineTo(100f, 0f); lineTo(100f, 50f); lineTo(0f, 50f) }
            if (closed) close()
        }
        val exact = checkNotNull(LiquidGlassExactContour.fromPath(rectangle()))
        val packed = checkNotNull(exact.pack(1f))
        assertEquals(4f, packed[4], 0f)
        assertEquals(2f, packed[6], 0f)
        assertEquals(15f, packed[7], 0f)
        assertNull(LiquidGlassExactContour.fromPath(rectangle(reverse = true)))
        assertNull(LiquidGlassExactContour.fromPath(rectangle(closed = false)))
        assertNull(LiquidGlassExactContour.fromPath(Path().apply {
            addPath(rectangle()); addPath(rectangle().apply { translate(Offset(120f, 0f)) })
        }))
        assertNull(LiquidGlassExactContour.fromPath(Path().apply {
            moveTo(0f, 0f); quadraticBezierTo(50f, -10f, 100f, 0f)
            lineTo(100f, 50f); lineTo(0f, 50f); close()
        }))
        // AsConic must reject this actual conic, never silently approximate it into quadratics.
        assertNull(LiquidGlassExactContour.fromPath(Path().apply { addOval(Rect(0f, 0f, 100f, 50f)) }))
        fun polygon(vertices: Int) = Path().apply {
            repeat(vertices) { index ->
                val angle = index * 2.0 * Math.PI / vertices
                val x = (50.0 + 45.0 * kotlin.math.cos(angle)).toFloat()
                val y = (50.0 + 45.0 * kotlin.math.sin(angle)).toFloat()
                if (index == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        val fullCapacity = checkNotNull(LiquidGlassExactContour.fromPath(polygon(17))).pack(1f)!!
        assertEquals(17f, fullCapacity[4], 0f)
        assertEquals(131071f, fullCapacity[7], 0f)
        assertNull(LiquidGlassExactContour.fromPath(polygon(18)))
    }
}

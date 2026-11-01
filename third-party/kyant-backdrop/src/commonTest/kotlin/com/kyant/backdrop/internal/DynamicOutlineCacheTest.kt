package com.kyant.backdrop.internal

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals

class DynamicOutlineCacheTest {
    @Test fun fixedHostObservesShapeStateAndRetainsUnchangedOutline() {
        val edge = mutableStateOf(10f)
        var recordings = 0
        val dynamicShape = object : Shape {
            override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
                recordings++
                return Outline.Rectangle(Rect(0f, 0f, edge.value * density.density, size.height))
            }
        }
        val provider = ShapeProvider({ dynamicShape })
        fun outline(density: Float = 1f) = provider.shape.createOutline(
            Size(100f, 100f), LayoutDirection.Ltr, Density(density)
        ) as Outline.Rectangle

        assertEquals(10f, outline().rect.right)
        assertEquals(10f, outline().rect.right)
        assertEquals(1, recordings)
        edge.value = 30f
        assertEquals(30f, outline().rect.right)
        assertEquals(2, recordings)
        assertEquals(60f, outline(2f).rect.right)
        assertEquals(3, recordings)
    }
}
